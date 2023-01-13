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

import java.util.Optional;
import org.sonar.check.Rule;
import org.sonar.plugins.python.api.PythonSubscriptionCheck;
import org.sonar.plugins.python.api.SubscriptionContext;
import org.sonar.plugins.python.api.tree.AssignmentStatement;
import org.sonar.plugins.python.api.tree.CallExpression;
import org.sonar.plugins.python.api.tree.Expression;
import org.sonar.plugins.python.api.tree.RegularArgument;
import org.sonar.python.types.InferredTypes;

import static org.sonar.plugins.python.api.tree.Tree.Kind.ASSIGNMENT_STMT;
import static org.sonar.plugins.python.api.tree.Tree.Kind.CALL_EXPR;
import static org.sonar.plugins.python.api.tree.Tree.Kind.REGULAR_ARGUMENT;

@Rule(key = "S3699")
public class UseOfEmptyReturnValueCheck extends PythonSubscriptionCheck {

  private static final String MESSAGE = "Remove this use of the output from \"%s\"; \"%s\" doesn’t return anything.";

  @Override
  public void initialize(Context context) {
    context.registerSyntaxNodeConsumer(ASSIGNMENT_STMT, ctx -> checkReturnValue(((AssignmentStatement) ctx.syntaxNode()).assignedValue(), ctx));
    context.registerSyntaxNodeConsumer(REGULAR_ARGUMENT, ctx -> checkReturnValue(((RegularArgument) ctx.syntaxNode()).expression(), ctx));
  }

  private static void checkReturnValue(Expression expression, SubscriptionContext ctx) {
    if (expression.is(CALL_EXPR) && expression.type().equals(InferredTypes.NONE)) {
      CallExpression callExpression = (CallExpression) expression;
      Optional.ofNullable(callExpression.calleeSymbol())
        .ifPresent(symbol -> ctx.addIssue(expression, String.format(MESSAGE, symbol.name(), symbol.name())));
    }
  }
}
