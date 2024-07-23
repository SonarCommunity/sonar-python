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

import javax.annotation.Nullable;
import org.sonar.plugins.python.api.PythonSubscriptionCheck;
import org.sonar.plugins.python.api.SubscriptionContext;
import org.sonar.plugins.python.api.tree.CallExpression;
import org.sonar.plugins.python.api.tree.Tree;
import org.sonar.python.types.v2.PythonType;
import org.sonar.python.types.v2.TriBool;

import static org.sonar.python.tree.TreeUtils.nameFromExpression;

public class NonCallableCalled extends PythonSubscriptionCheck {
  @Override
  public void initialize(Context context) {
    context.registerSyntaxNodeConsumer(Tree.Kind.CALL_EXPR, ctx -> {
      var callExpression = (CallExpression) ctx.syntaxNode();
      var callee = callExpression.callee();
      var calleeType = callee.typeV2();
      if (isNonCallableCall(ctx, calleeType)) {
        String name = nameFromExpression(callee);
        var preciseIssue = ctx.addIssue(callee, message(calleeType, name));
        calleeType.definitionLocation()
          .ifPresent(location -> preciseIssue.secondary(location, "Definition."));
      }
    });
  }

  private static boolean isNonCallableCall(SubscriptionContext ctx, PythonType calleeType) {
    return ctx.typeChecker().typeCheckBuilder().isTypeHintTypeSource().check(calleeType) == TriBool.TRUE
           && ctx.typeChecker().typeCheckBuilder().hasMember("__call__").check(calleeType) == TriBool.FALSE
           && ctx.typeChecker().typeCheckBuilder().isInstanceOf("typing", "Coroutine").check(calleeType) == TriBool.FALSE
           && ctx.typeChecker().typeCheckBuilder().isInstanceOf("typing", "Callable").check(calleeType) == TriBool.FALSE;
  }

  protected static String addTypeName(PythonType type) {
    return type.displayName()
      .map(d -> " has type " + d + " and it")
      .orElse("");
  }

  private static String message(PythonType calleeType, @Nullable String name) {
    if (name != null) {
      return String.format("Fix this call; Previous type checks suggest that \"%s\"%s is not callable.", name, addTypeName(calleeType));
    }
    return String.format("Fix this call; Previous type checks suggest that this expression%s is not callable.", addTypeName(calleeType));
  }

}
