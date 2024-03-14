/*
 * SonarQube Python Plugin
 * Copyright (C) 2011-2024 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.python.checks;

import org.sonar.check.Rule;
import org.sonar.plugins.python.api.PythonSubscriptionCheck;
import org.sonar.plugins.python.api.SubscriptionContext;
import org.sonar.plugins.python.api.symbols.Symbol;
import org.sonar.plugins.python.api.tree.CallExpression;
import org.sonar.plugins.python.api.tree.Tree;
import org.sonar.python.tree.TreeUtils;

@Rule(key = "S6918")
public class NonSingletonTfVariableCheck extends PythonSubscriptionCheck {
  @Override
  public void initialize(Context context) {
    context.registerSyntaxNodeConsumer(Tree.Kind.CALL_EXPR, NonSingletonTfVariableCheck::checkCallExpression);
  }

  private static void checkCallExpression(SubscriptionContext ctx) {
    CallExpression callExpression = (CallExpression) ctx.syntaxNode();
    Symbol symbol = callExpression.calleeSymbol();
    if (symbol == null || !"tensorflow.Variable".equals(symbol.fullyQualifiedName())) {
      return;
    }
    if (!isWithinTensorflowFunction(callExpression)) {
      return;
    }
    if (isUnconditional(callExpression)) {
      ctx.addIssue(callExpression, "Refactor this variable declaration to be a singleton.");
    }
  }

  private static boolean isUnconditional(CallExpression callExpression) {
    Tree firstAncestor = TreeUtils.firstAncestorOfKind(callExpression, Tree.Kind.FUNCDEF, Tree.Kind.IF_STMT, Tree.Kind.CONDITIONAL_EXPR);
    return firstAncestor != null && firstAncestor.is(Tree.Kind.FUNCDEF);
  }

  private static boolean isWithinTensorflowFunction(Tree tree) {
    return TreeUtils.firstAncestor(tree, t -> TreeUtils.isFunctionWithGivenDecoratorFQN(t, "tensorflow.function")) != null;
  }
}
