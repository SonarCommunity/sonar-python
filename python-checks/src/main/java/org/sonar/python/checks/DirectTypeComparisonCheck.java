/*
 * SonarQube Python Plugin
 * Copyright (C) 2011-2023 SonarSource SA
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
import org.sonar.plugins.python.api.tree.BinaryExpression;
import org.sonar.plugins.python.api.tree.CallExpression;
import org.sonar.plugins.python.api.tree.Expression;
import org.sonar.python.tree.TreeUtils;

import static org.sonar.plugins.python.api.symbols.Symbol.Kind.CLASS;
import static org.sonar.plugins.python.api.tree.Tree.Kind.CALL_EXPR;
import static org.sonar.plugins.python.api.tree.Tree.Kind.COMPARISON;

@Rule(key = "S6660")
public class DirectTypeComparisonCheck extends PythonSubscriptionCheck {

  @Override
  public void initialize(Context context) {
    context.registerSyntaxNodeConsumer(COMPARISON, ctx -> checkBinaryExpression(ctx, ((BinaryExpression) ctx.syntaxNode())));
  }

  private static void checkBinaryExpression(SubscriptionContext ctx, BinaryExpression binaryExpression) {
    if (isDirectTypeComparison(binaryExpression.leftOperand(), binaryExpression.rightOperand())) {
      ctx.addIssue(binaryExpression.operator(), "Use the `isinstance()` function here.");
    }
  }

  private static boolean isDirectTypeComparison(Expression lhs, Expression rhs) {
    return (isTypeBuiltinCall(lhs) && TreeUtils.getSymbolFromTree(rhs).filter(s -> s.is(CLASS)).isPresent())
      || (isTypeBuiltinCall(rhs) && TreeUtils.getSymbolFromTree(lhs).filter(s -> s.is(CLASS)).isPresent());
  }

  private static boolean isTypeBuiltinCall(Expression expression) {
    if (!expression.is(CALL_EXPR)) return false;
    Symbol calleeSymbol = ((CallExpression) expression).calleeSymbol();
    return calleeSymbol != null && "type".equals(calleeSymbol.fullyQualifiedName());
  }
}
