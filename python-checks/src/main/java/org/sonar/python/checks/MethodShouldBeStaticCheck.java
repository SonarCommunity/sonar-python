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

import java.util.List;
import org.sonar.check.Rule;
import org.sonar.plugins.python.api.PythonSubscriptionCheck;
import org.sonar.plugins.python.api.tree.AnyParameter;
import org.sonar.plugins.python.api.tree.ExpressionStatement;
import org.sonar.plugins.python.api.tree.FunctionDef;
import org.sonar.plugins.python.api.tree.Name;
import org.sonar.plugins.python.api.tree.ParameterList;
import org.sonar.plugins.python.api.tree.Parameter;
import org.sonar.plugins.python.api.tree.RaiseStatement;
import org.sonar.plugins.python.api.tree.Statement;
import org.sonar.plugins.python.api.tree.Tree;
import org.sonar.plugins.python.api.tree.BaseTreeVisitor;
import org.sonar.python.tree.TreeUtils;

import static org.sonar.python.checks.CheckUtils.classHasInheritance;
import static org.sonar.python.checks.CheckUtils.getParentClassDef;

@Rule(key = "S2325")
public class MethodShouldBeStaticCheck extends PythonSubscriptionCheck {

  private static final String MESSAGE = "Make this method static.";

  @Override
  public void initialize(Context context) {
    context.registerSyntaxNodeConsumer(Tree.Kind.FUNCDEF, ctx -> {
      FunctionDef funcDef = (FunctionDef) ctx.syntaxNode();
      if (funcDef.isMethodDefinition()
        && !classHasInheritance(getParentClassDef(funcDef))
        && !isBuiltInMethod(funcDef)
        && !isStatic(funcDef)
        && hasValuableCode(funcDef)
        && !mayRaiseNotImplementedError(funcDef)
        && !isUsingSelfArg(funcDef)
      ) {
        ctx.addIssue(funcDef.name(), MESSAGE);
      }
    });
  }

  private static boolean mayRaiseNotImplementedError(FunctionDef funcDef) {
    RaiseStatementVisitor visitor = new RaiseStatementVisitor();
    funcDef.accept(visitor);
    return visitor.hasNotImplementedError;

  }

  private static boolean hasValuableCode(FunctionDef funcDef) {
    List<Statement> statements = funcDef.body().statements();
    return !statements.stream().allMatch(st -> isStringLiteral(st) || st.is(Tree.Kind.PASS_STMT));
  }

  private static boolean isStringLiteral(Statement st) {
    return st.is(Tree.Kind.EXPRESSION_STMT) && ((ExpressionStatement) st).expressions().stream().allMatch(e -> e.is(Tree.Kind.STRING_LITERAL));
  }

  private static boolean isUsingSelfArg(FunctionDef funcDef) {
    ParameterList parameters = funcDef.parameters();
    if (parameters == null) {
      // if a method has no parameters then it can't be a instance method.
      return true;
    }
    List<AnyParameter> params = parameters.all();
    if (params.isEmpty()) {
      return false;
    }
    if (params.get(0).is(Tree.Kind.TUPLE_PARAMETER)) {
      return false;
    }
    Parameter first = (Parameter) params.get(0);
    Name paramName = first.name();
    if (paramName == null) {
      // star argument should not raise issue
      return true;
    }
    SelfVisitor visitor = new SelfVisitor(paramName.name());
    funcDef.body().accept(visitor);
    return visitor.isUsingSelfArg;
  }

  private static boolean isStatic(FunctionDef funcDef) {
    return funcDef.decorators().stream()
      .map(d -> TreeUtils.decoratorNameFromExpression(d.expression()))
      .anyMatch(n -> "staticmethod".equals(n) || "classmethod".equals(n));
  }

  private static boolean isBuiltInMethod(FunctionDef funcDef) {
    String name = funcDef.name().name();
    String doubleUnderscore = "__";
    return name.startsWith(doubleUnderscore) && name.endsWith(doubleUnderscore);
  }

  private static class RaiseStatementVisitor extends BaseTreeVisitor {
    private int withinRaise = 0;
    boolean hasNotImplementedError = false;

    @Override
    public void visitRaiseStatement(RaiseStatement pyRaiseStatementTree) {
      withinRaise++;
      scan(pyRaiseStatementTree.expressions());
      withinRaise--;
    }

    @Override
    public void visitName(Name pyNameTree) {
      if (withinRaise > 0) {
        hasNotImplementedError |= pyNameTree.name().equals("NotImplementedError");
      }
    }
  }

  private static class SelfVisitor extends BaseTreeVisitor {
    private final String selfName;
    boolean isUsingSelfArg = false;

    SelfVisitor(String selfName) {
      this.selfName = selfName;
    }

    @Override
    public void visitName(Name pyNameTree) {
      isUsingSelfArg |= selfName.equals(pyNameTree.name());
    }
  }
}
