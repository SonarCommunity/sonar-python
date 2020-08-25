/*
 * SonarQube Python Plugin
 * Copyright (C) 2011-2020 SonarSource SA
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.sonar.check.Rule;
import org.sonar.plugins.python.api.PythonSubscriptionCheck;
import org.sonar.plugins.python.api.SubscriptionContext;
import org.sonar.plugins.python.api.tree.BaseTreeVisitor;
import org.sonar.plugins.python.api.tree.CallExpression;
import org.sonar.plugins.python.api.tree.FileInput;
import org.sonar.plugins.python.api.tree.ImportFrom;
import org.sonar.plugins.python.api.tree.Name;
import org.sonar.plugins.python.api.tree.Tree;

@Rule(key = "S5953")
public class UnresolvedSymbolsCheck extends PythonSubscriptionCheck {

  @Override
  public void initialize(Context context) {
    context.registerSyntaxNodeConsumer(Tree.Kind.FILE_INPUT, ctx -> {
      FileInput fileInput = (FileInput) ctx.syntaxNode();
      if (importsManipulatedAllProperty(fileInput)) {
        return;
      }
      UnresolvedSymbolsVisitor unresolvedSymbolsVisitor = new UnresolvedSymbolsVisitor();
      fileInput.accept(unresolvedSymbolsVisitor);
      if (!unresolvedSymbolsVisitor.callGlobalsOrLocals && !unresolvedSymbolsVisitor.hasUnresolvedWildcardImport) {
        addNameIssues(unresolvedSymbolsVisitor.nameIssues, ctx);
      }
    });
  }

  private static boolean importsManipulatedAllProperty(FileInput fileInput) {
    return fileInput.globalVariables().stream().anyMatch(s -> s.name().equals("__all__") && s.fullyQualifiedName() != null);
  }

  private static void addNameIssues(Map<String, List<Name>> nameIssues, SubscriptionContext subscriptionContext) {
    nameIssues.forEach((name, list) -> {
      Name first = list.get(0);
      PreciseIssue issue = subscriptionContext.addIssue(first, first.name() + " is not defined. Change its name or define it before using it");
      list.stream().skip(1).forEach(n -> issue.secondary(n, null));
    });
  }

  private static class UnresolvedSymbolsVisitor extends BaseTreeVisitor {

    private boolean hasUnresolvedWildcardImport = false;
    private boolean callGlobalsOrLocals = false;
    private final Map<String, List<Name>> nameIssues = new HashMap<>();

    @Override
    public void visitName(Name name) {
      if (name.isVariable() && name.symbol() == null) {
        nameIssues.computeIfAbsent(name.name(), k -> new ArrayList<>()).add(name);
      }
    }

    @Override
    public void visitImportFrom(ImportFrom importFrom) {
      hasUnresolvedWildcardImport |= importFrom.hasUnresolvedWildcardImport();
      super.visitImportFrom(importFrom);
    }

    @Override
    public void visitCallExpression(CallExpression callExpression) {
      if (callExpression.callee().is(Tree.Kind.NAME)) {
        String name = ((Name) callExpression.callee()).name();
        callGlobalsOrLocals |= name.equals("globals") || name.equals("locals");
      }
      super.visitCallExpression(callExpression);
    }
  }
}
