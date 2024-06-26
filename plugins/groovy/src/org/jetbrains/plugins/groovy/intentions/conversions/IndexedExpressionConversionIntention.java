/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.intentions.conversions;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.intentions.base.GrPsiUpdateIntention;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;

public final class IndexedExpressionConversionIntention extends GrPsiUpdateIntention {

  @Override
  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new IndexedExpressionConversionPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull ActionContext context, @NotNull ModPsiUpdater updater) {
    final GrIndexProperty arrayIndexExpression = (GrIndexProperty)element;

    final GrArgumentList argList = (GrArgumentList)arrayIndexExpression.getLastChild();

    assert argList != null;
    final GrExpression[] arguments = argList.getExpressionArguments();

    final PsiElement parent = element.getParent();
    final GrExpression arrayExpression = arrayIndexExpression.getInvokedExpression();
    if (!(parent instanceof GrAssignmentExpression assignmentExpression)) {
      rewriteAsGetAt(arrayIndexExpression, arrayExpression, arguments[0]);
      return;
    }
    final GrExpression rhs = assignmentExpression.getRValue();
    if (rhs.equals(element)) {
      rewriteAsGetAt(arrayIndexExpression, arrayExpression, arguments[0]);
    }
    else {
      rewriteAsSetAt(assignmentExpression, arrayExpression, arguments[0], rhs);
    }
  }

  private static void rewriteAsGetAt(GrIndexProperty arrayIndexExpression, GrExpression arrayExpression, GrExpression argument)
    throws IncorrectOperationException {
    PsiImplUtil.replaceExpression(arrayExpression.getText() + ".getAt(" + argument.getText() + ')', arrayIndexExpression);
  }

  private static void rewriteAsSetAt(GrAssignmentExpression assignment,
                                     GrExpression arrayExpression,
                                     GrExpression argument,
                                     GrExpression value) throws IncorrectOperationException {
    PsiImplUtil.replaceExpression(arrayExpression.getText() + ".putAt(" + argument.getText() + ", " + value.getText() + ')', assignment);
  }
}
