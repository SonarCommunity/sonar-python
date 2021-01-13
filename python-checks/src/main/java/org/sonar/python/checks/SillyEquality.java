/*
 * SonarQube Python Plugin
 * Copyright (C) 2011-2021 SonarSource SA
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

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import javax.annotation.CheckForNull;
import org.sonar.plugins.python.api.PythonSubscriptionCheck;
import org.sonar.plugins.python.api.SubscriptionContext;
import org.sonar.plugins.python.api.tree.BinaryExpression;
import org.sonar.plugins.python.api.tree.Expression;
import org.sonar.plugins.python.api.tree.Tree;
import org.sonar.plugins.python.api.types.BuiltinTypes;
import org.sonar.plugins.python.api.types.InferredType;

public abstract class SillyEquality extends PythonSubscriptionCheck {

  private static final HashSet<String> CONSIDERED_OPERATORS = new HashSet<>(Arrays.asList("==", "!="));
  protected static final HashMap<String, String> BUILTINS_TYPE_CATEGORY = new HashMap<>();

  private static final String NUMBER = "number";

  static {
    BUILTINS_TYPE_CATEGORY.put(BuiltinTypes.STR, BuiltinTypes.STR);
    BUILTINS_TYPE_CATEGORY.put(BuiltinTypes.INT, NUMBER);
    BUILTINS_TYPE_CATEGORY.put(BuiltinTypes.FLOAT, NUMBER);
    BUILTINS_TYPE_CATEGORY.put(BuiltinTypes.COMPLEX, NUMBER);
    BUILTINS_TYPE_CATEGORY.put(BuiltinTypes.BOOL, NUMBER);
    BUILTINS_TYPE_CATEGORY.put(BuiltinTypes.LIST, BuiltinTypes.LIST);
    BUILTINS_TYPE_CATEGORY.put(BuiltinTypes.SET, BuiltinTypes.SET);
    BUILTINS_TYPE_CATEGORY.put("frozenset", BuiltinTypes.SET);
    BUILTINS_TYPE_CATEGORY.put(BuiltinTypes.DICT, BuiltinTypes.DICT);
    BUILTINS_TYPE_CATEGORY.put(BuiltinTypes.TUPLE, BuiltinTypes.TUPLE);
  }

  @Override
  public void initialize(Context context) {
    context.registerSyntaxNodeConsumer(Tree.Kind.COMPARISON, ctx -> {
      BinaryExpression binaryExpression = (BinaryExpression) ctx.syntaxNode();
      String operator = binaryExpression.operator().value();
      if (!CONSIDERED_OPERATORS.contains(operator)) {
        return;
      }
      checkIncompatibleTypes(ctx, binaryExpression);
    });
  }

  private void checkIncompatibleTypes(SubscriptionContext ctx, BinaryExpression binaryExpression) {
    Expression left = binaryExpression.leftOperand();
    Expression right = binaryExpression.rightOperand();
    InferredType leftType = left.type();
    InferredType rightType = right.type();

    if (areIdentityComparableOrNone(leftType, rightType)) {
      return;
    }

    String leftCategory = builtinTypeCategory(leftType);
    String rightCategory = builtinTypeCategory(rightType);
    boolean leftCanImplementEqOrNe = canImplementEqOrNe(left);
    boolean rightCanImplementEqOrNe = canImplementEqOrNe(right);

    if ((leftCategory != null && leftCategory.equals(rightCategory))) {
      return;
    }

    if ((!leftCanImplementEqOrNe && !rightCanImplementEqOrNe)
      || (leftCategory != null && rightCategory != null)
      || (leftCategory != null && !rightCanImplementEqOrNe)
      || (rightCategory != null && !leftCanImplementEqOrNe)) {

      raiseIssue(ctx, binaryExpression, binaryExpression.operator().value());
    }
  }

  private void raiseIssue(SubscriptionContext ctx, BinaryExpression binaryExpression, String operator) {
    String result = operator.equals("==") ? "False" : "True";
    ctx.addIssue(binaryExpression.operator(), message(result));
  }


  abstract boolean areIdentityComparableOrNone(InferredType leftType, InferredType rightType);

  abstract boolean canImplementEqOrNe(Expression expression);

  @CheckForNull
  abstract String builtinTypeCategory(InferredType inferredType);

  abstract String message(String result);
}
