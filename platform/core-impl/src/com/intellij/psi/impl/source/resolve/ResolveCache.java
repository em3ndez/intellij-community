// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.resolve;

import com.intellij.model.Symbol;
import com.intellij.model.psi.PsiSymbolReference;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.psi.*;
import com.intellij.psi.impl.AnyPsiChangeListener;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.IdempotenceChecker;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ConcurrentWeakKeySoftValueHashMap;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.ReferenceQueue;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class ResolveCache implements Disposable {
  private final AtomicReferenceArray<Map<?, ?>> myMaps = new AtomicReferenceArray<>(8); //boolean isPhysical * boolean incompleteCode * boolean isPoly

  public static ResolveCache getInstance(@NotNull Project project) {
    ProgressIndicatorProvider.checkCanceled(); // We hope this method is being called often enough to cancel daemon processes smoothly
    return project.getService(ResolveCache.class);
  }

  public ResolveCache(@NotNull Project project) {
    clearCacheOnPsiChange(project.getMessageBus());
    LowMemoryWatcher.register(() -> onLowMemory(), this);
  }

  private void clearCacheOnPsiChange(@NotNull MessageBus bus) {
    bus.connect().subscribe(PsiManagerImpl.ANY_PSI_CHANGE_TOPIC, new AnyPsiChangeListener() {
      @Override
      public void beforePsiChanged(boolean isPhysical) {
        clearCache(isPhysical);
      }
    });
  }

  private void onLowMemory() {
    clearArray(myMaps, true);
  }

  @Override
  public void dispose() {
  }

  @FunctionalInterface
  public interface AbstractResolver<TRef extends PsiReference, TResult> {
    TResult resolve(@NotNull TRef ref, boolean incompleteCode);
  }

  /**
   * Resolver which returns an array of possible resolved variants instead of just one
   */
  @FunctionalInterface
  public interface PolyVariantResolver<T extends PsiPolyVariantReference> extends AbstractResolver<T,ResolveResult[]> {
    @Override
    ResolveResult @NotNull [] resolve(@NotNull T t, boolean incompleteCode);
  }

  /**
   * Poly variant resolver with additional containingFile parameter, which helps to avoid costly tree up traversal
   */
  @FunctionalInterface
  public interface PolyVariantContextResolver<T extends PsiPolyVariantReference> {
    ResolveResult @NotNull [] resolve(@NotNull T ref, @NotNull PsiFile containingFile, boolean incompleteCode);
  }

  /**
   * Resolver specialized to resolve PsiReference to PsiElement
   */
  @FunctionalInterface
  public interface Resolver extends AbstractResolver<PsiReference, PsiElement> {
  }

  private static @NotNull <K,V> Map<K, V> createWeakMap() {
    //noinspection deprecation
    return new ConcurrentWeakKeySoftValueHashMap<K, V>(100, 0.75f, Runtime.getRuntime().availableProcessors()) {
      @Override
      protected @NotNull ValueReference<K, V> createValueReference(@NotNull V value, @NotNull ReferenceQueue<? super V> queue) {
        ValueReference<K, V> result;
        if (value == NULL_RESULT || value instanceof Object[] && ((Object[])value).length == 0) {
          // no use in creating SoftReference to null
          result = createStrongReference(value);
        }
        else {
          result = super.createValueReference(value, queue);
        }
        return result;
      }

      @Override
      public V get(@NotNull Object key) {
        V v = super.get(key);
        return v == NULL_RESULT ? null : v;
      }

      @Override
      public V put(K key, V value) {
        // no use in creating SoftReference to null
        //noinspection unchecked
        V toStore = value == null ? (V)NULL_RESULT : value;
        return super.put(key, toStore);
      }

      @Override
      public boolean equals(Object obj) {
        // The map instance is used as the recursion prevention key.
        // Each instance is determined by several flags: physical, incomplete, poly;
        // Each instance is unique, so we don't need to store flags to check equality.
        return this == obj;
      }

      @Override
      public int hashCode() {
        return System.identityHashCode(this);
      }
    };
  }

  public void clearCache(boolean isPhysical) {
    clearArray(myMaps, isPhysical);
  }

  private static void clearArray(@NotNull AtomicReferenceArray<?> array, boolean clearPhysical) {
    for (int i = clearPhysical ? 0 : 4; i < array.length(); i++) {
      array.set(i, null);
    }
  }

  @SuppressWarnings("LambdaUnfriendlyMethodOverload")
  public <T extends PsiPolyVariantReference> ResolveResult @NotNull [] resolveWithCaching(@NotNull T ref,
                                                                                          @NotNull PolyVariantResolver<T> resolver,
                                                                                          boolean needToPreventRecursion,
                                                                                          boolean incompleteCode) {
    return resolveWithCaching(ref, resolver, needToPreventRecursion, incompleteCode, ref.getElement().getContainingFile());
  }

  /**
   * Optimization: do not (re)compute containing file.
   * When the containing file is known, please prefer this method over {@link #resolveWithCaching(PsiPolyVariantReference, PolyVariantResolver, boolean, boolean)}
   */
  public <T extends PsiPolyVariantReference> ResolveResult @NotNull [] resolveWithCaching(@NotNull T ref,
                                                                                          @NotNull PolyVariantResolver<T> resolver,
                                                                                          boolean needToPreventRecursion,
                                                                                          boolean incompleteCode,
                                                                                          @NotNull PsiFile containingFile) {
    boolean isPhysical = containingFile.isPhysical();
    ProgressIndicatorProvider.checkCanceled();
    if (isPhysical) {
      ApplicationManager.getApplication().assertReadAccessAllowed();
    }
    int index = getIndex(isPhysical, incompleteCode, true);
    ResolveResult[] result = resolve(ref, getMap(index), needToPreventRecursion,
                                     () -> ((AbstractResolver<? super @NotNull T, ResolveResult[]>)resolver).resolve(ref, incompleteCode));
    return result == null ? ResolveResult.EMPTY_ARRAY : result;
  }

  public <T extends PsiPolyVariantReference> ResolveResult @NotNull [] resolveWithCaching(@NotNull T ref,
                                                                                          @NotNull PolyVariantContextResolver<T> resolver,
                                                                                          boolean needToPreventRecursion,
                                                                                          boolean incompleteCode,
                                                                                          @NotNull PsiFile containingFile) {
    ProgressIndicatorProvider.checkCanceled();
    ApplicationManager.getApplication().assertReadAccessAllowed();

    boolean isPhysical = containingFile.isPhysical();
    int index = getIndex(isPhysical, incompleteCode, true);
    Map<T, ResolveResult[]> map = getMap(index);
    ResolveResult[] results = resolve(ref, map, needToPreventRecursion, () -> resolver.resolve(ref, containingFile, incompleteCode));
    return results == null ? ResolveResult.EMPTY_ARRAY : results;
  }

  @FunctionalInterface
  @ApiStatus.Experimental
  public interface PsiSymbolReferenceResolver<R extends @NotNull PsiSymbolReference> {
    @NotNull Collection<? extends @NotNull Symbol> resolve(@NotNull R reference);
  }

  @ApiStatus.Experimental
  public <R extends @NotNull PsiSymbolReference> @NotNull Collection<? extends @NotNull Symbol> resolveWithCaching(@NotNull R ref, @NotNull PsiSymbolReferenceResolver<? super R> resolver) {
    return resolveWithCaching(ref, true, resolver);
  }

  @ApiStatus.Experimental
  public <R extends @NotNull PsiSymbolReference>
  @NotNull Collection<? extends @NotNull Symbol> resolveWithCaching(@NotNull R ref, boolean preventRecursion, @NotNull PsiSymbolReferenceResolver<? super R> resolver) {
    ProgressIndicatorProvider.checkCanceled();
    ApplicationManager.getApplication().assertReadAccessAllowed();
    boolean isPhysical = ref.getElement().isPhysical();
    int index = getIndex(isPhysical, false, true);
    Collection<? extends Symbol> results = resolve(ref, getMap(index), preventRecursion, () -> resolver.resolve(ref));
    return results == null ? Collections.emptyList() : results;
  }

  private static <TRef, TResult> @Nullable TResult resolve(@NotNull TRef ref, @NotNull Map<TRef, TResult> cache, boolean preventRecursion, @NotNull Computable<? extends TResult> resolver) {
    TResult cachedResult = cache.get(ref);
    if (cachedResult != null) {
      if (IdempotenceChecker.areRandomChecksEnabled()) {
        IdempotenceChecker.applyForRandomCheck(cachedResult, ref, loggingResolver(ref, resolver));
      }
      return cachedResult;
    }

    RecursionGuard.StackStamp stamp = RecursionManager.markStack();
    Computable<TResult> loggingResolver = loggingResolver(ref, resolver);
    TResult result = preventRecursion
                     ? RecursionManager.doPreventingRecursion(Pair.create(ref, cache), true, loggingResolver)
                     : loggingResolver.get();
    if (result instanceof ResolveResult) {
      ensureValidPsi((ResolveResult)result);
    }
    else if (result instanceof ResolveResult[]) {
      ensureValidResults((ResolveResult[])result);
    }
    else if (result instanceof PsiElement) {
      PsiUtilCore.ensureValid((PsiElement)result);
    }
    if (stamp.mayCacheNow()) {
      cache(ref, cache, result, loggingResolver);
    }
    return result;
  }

  private static @NotNull <R> Computable<R> loggingResolver(@NotNull Object ref, @NotNull Computable<? extends R> resolver) {
    return () -> {
      if (IdempotenceChecker.isLoggingEnabled()) {
        IdempotenceChecker.logTrace("Resolving " + ref + " of " + ref.getClass());
      }
      return resolver.get();
    };
  }

  private static void ensureValidResults(@NotNull ResolveResult @NotNull [] result) {
    for (ResolveResult resolveResult : result) {
      ensureValidPsi(resolveResult);
    }
  }

  private static void ensureValidPsi(@NotNull ResolveResult resolveResult) {
    PsiElement element = resolveResult.getElement();
    if (element != null) {
      PsiUtilCore.ensureValid(element);
    }
  }

  // null means not cached
  public <T extends PsiPolyVariantReference> ResolveResult @Nullable [] getCachedResults(@NotNull T ref, boolean isPhysical, boolean incompleteCode, boolean isPoly) {
    Map<T, ResolveResult[]> map = getMap(getIndex(isPhysical, incompleteCode, isPoly));
    return map.get(ref);
  }

  @SuppressWarnings("LambdaUnfriendlyMethodOverload")
  public @Nullable <TRef extends PsiReference, TResult> TResult resolveWithCaching(@NotNull TRef ref,
                                                                                   @NotNull AbstractResolver<TRef, TResult> resolver,
                                                                                   boolean needToPreventRecursion,
                                                                                   boolean incompleteCode) {
    boolean isPhysical = ref.getElement().isPhysical();
    ProgressIndicatorProvider.checkCanceled();
    if (isPhysical) {
      ApplicationManager.getApplication().assertReadAccessAllowed();
    }
    int index = getIndex(isPhysical, incompleteCode, false);
    return resolve(ref, getMap(index), needToPreventRecursion, () -> resolver.resolve(ref, incompleteCode));
  }

  private <TRef, TResult> @NotNull Map<TRef, TResult> getMap(int index) {
    //noinspection unchecked
    AtomicReferenceArray<Map<TRef, TResult>> array = (AtomicReferenceArray<Map<TRef,TResult>>)(AtomicReferenceArray<?>)myMaps;
    Map<TRef, TResult> map = array.get(index);
    if (map == null) {
      map = array.updateAndGet(index, oldMap -> oldMap == null ? createWeakMap() : oldMap);
    }
    return map;
  }

  private static int getIndex(boolean isPhysical, boolean incompleteCode, boolean isPoly) {
    return (isPhysical ? 0 : 4) + (incompleteCode ? 0 : 2) + (isPoly ? 0 : 1);
  }

  private static final Object NULL_RESULT = ObjectUtils.sentinel("ResolveCache.NULL_RESULT");

  private static <TRef, TResult> void cache(@NotNull TRef ref, @NotNull Map<? super TRef, TResult> map, TResult result, @NotNull Computable<? extends TResult> doResolve) {
    // optimization: less contention
    TResult cached = map.get(ref);
    if (cached != null) {
      if (cached == result) {
        return;
      }
      IdempotenceChecker.checkEquivalence(cached, result, ref.getClass(), doResolve);
    }
    map.put(ref, result);
  }

  private static @NotNull <K, V> StrongValueReference<K, V> createStrongReference(@NotNull V value) {
    //noinspection unchecked
    return value == NULL_RESULT ? (StrongValueReference<K, V>)NULL_VALUE_REFERENCE
           : value == ResolveResult.EMPTY_ARRAY ? (StrongValueReference<K, V>)EMPTY_RESOLVE_RESULT
           : new StrongValueReference<>(value);
  }

  private static final StrongValueReference<?, ?> NULL_VALUE_REFERENCE = new StrongValueReference<>(NULL_RESULT);
  private static final StrongValueReference<?, ?> EMPTY_RESOLVE_RESULT = new StrongValueReference<>(ResolveResult.EMPTY_ARRAY);

  @SuppressWarnings("deprecation")
  private static class StrongValueReference<K, V> implements ConcurrentWeakKeySoftValueHashMap.ValueReference<K, V> {
    private final V myValue;

    StrongValueReference(@NotNull V value) {
      myValue = value;
    }

    @Override
    public @NotNull ConcurrentWeakKeySoftValueHashMap.KeyReference<K, V> getKeyReference() {
      throw new UnsupportedOperationException(); // will never GC so this method will never be called so no implementation is necessary
    }

    @Override
    public @NotNull V get() {
      return myValue;
    }
  }
}
