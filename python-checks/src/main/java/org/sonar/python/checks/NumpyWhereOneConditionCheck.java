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

import java.util.List;
import java.util.Optional;
import org.sonar.check.Rule;
import org.sonar.plugins.python.api.PythonSubscriptionCheck;
import org.sonar.plugins.python.api.SubscriptionContext;
import org.sonar.plugins.python.api.symbols.Symbol;
import org.sonar.plugins.python.api.tree.Argument;
import org.sonar.plugins.python.api.tree.CallExpression;
import org.sonar.plugins.python.api.tree.Name;
import org.sonar.plugins.python.api.tree.RegularArgument;
import org.sonar.plugins.python.api.tree.Tree;

@Rule(key = "S6729")
public class NumpyWhereOneConditionCheck extends PythonSubscriptionCheck {
  @Override
  public void initialize(Context context) {
    context.registerSyntaxNodeConsumer(Tree.Kind.CALL_EXPR, NumpyWhereOneConditionCheck::checkNumpyWhereCall);
  }

  private static void checkNumpyWhereCall(SubscriptionContext ctx) {
    CallExpression ce = (CallExpression) ctx.syntaxNode();
    Symbol symbol = ce.calleeSymbol();
    if (symbol != null && "numpy.where".equals(symbol.fullyQualifiedName()) && hasOneParameter(ce)) {
      ctx.addIssue(ce, "Use \"np.nonzero\" when only the condition parameter is provided to \"np.where\".");
    }
  }

  private static boolean hasOneParameter(CallExpression ce) {
    List<Argument> argList = ce.arguments();
    if (argList.size() != 1 || argList.get(0).is(Tree.Kind.UNPACKING_EXPR)) {
      return false;
    }
    RegularArgument regArg = (RegularArgument) argList.get(0);
    Name keywordArgument = regArg.keywordArgument();
    if (keywordArgument == null) {
      return true;
    }
    return Optional.ofNullable(keywordArgument.name()).filter(name -> "condition".equals(keywordArgument.name())).isPresent();
  }
}
