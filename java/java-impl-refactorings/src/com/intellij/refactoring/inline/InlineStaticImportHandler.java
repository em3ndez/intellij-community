/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.refactoring.inline;

import com.intellij.java.JavaBundle;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiImportStaticStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.listeners.RefactoringEventListener;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.psi.util.ImportsUtil.collectReferencesThrough;
import static com.intellij.psi.util.ImportsUtil.replaceAllAndDeleteImport;

public final class InlineStaticImportHandler extends JavaInlineActionHandler {

  public static final String REFACTORING_ID = "refactoring.inline.import";

  @Override
  public boolean canInlineElement(PsiElement element) {
    if (element.getContainingFile() == null) return false;
    return PsiTreeUtil.getParentOfType(element, PsiImportStaticStatement.class) != null;
  }

  @Override
  public void inlineElement(Project project, Editor editor, PsiElement element) {
    final PsiImportStaticStatement staticStatement = PsiTreeUtil.getParentOfType(element, PsiImportStaticStatement.class);
    final List<PsiJavaCodeReferenceElement> referenceElements =
      collectReferencesThrough(element.getContainingFile(), null, staticStatement);

    RefactoringEventData data = new RefactoringEventData();
    data.addElement(element);
    project.getMessageBus().syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC).refactoringStarted(REFACTORING_ID, data);


    WriteCommandAction.writeCommandAction(project).withName(
      JavaBundle.message("action.expand.static.import.text")).run(() -> replaceAllAndDeleteImport(referenceElements, null, staticStatement));
    project.getMessageBus().syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC).refactoringDone(REFACTORING_ID, null);
  }

  @Nullable
  @Override
  public String getActionName(PsiElement element) {
    return JavaBundle.message("action.expand.static.import.text");
  }
}
