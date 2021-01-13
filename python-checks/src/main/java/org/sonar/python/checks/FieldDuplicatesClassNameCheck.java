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

import java.util.HashSet;
import java.util.Set;
import org.sonar.check.Rule;
import org.sonar.plugins.python.api.PythonSubscriptionCheck;
import org.sonar.plugins.python.api.tree.ClassDef;
import org.sonar.plugins.python.api.tree.Tree;
import org.sonar.plugins.python.api.symbols.Symbol;
import org.sonar.plugins.python.api.symbols.Usage;

@Rule(key = "S1700")
public class FieldDuplicatesClassNameCheck extends PythonSubscriptionCheck {

  private static final String MESSAGE = "Rename field \"%s\"";

  @Override
  public void initialize(Context context) {
    context.registerSyntaxNodeConsumer(Tree.Kind.CLASSDEF, ctx -> {
      ClassDef classDef = (ClassDef) ctx.syntaxNode();
      if (CheckUtils.classHasInheritance(classDef)) {
        return;
      }
      String className = classDef.name().name();
      Set<Symbol> allFields = new HashSet<>(classDef.classFields());
      allFields.addAll(classDef.instanceFields());
      allFields.stream()
        .filter(symbol -> className.equalsIgnoreCase(symbol.name()))
        .filter(symbol -> symbol.usages().stream().noneMatch(usage -> usage.kind() == Usage.Kind.FUNC_DECLARATION))
        .forEach(symbol -> symbol.usages()
          .stream()
          .findFirst()
          .ifPresent(usage -> ctx.addIssue(usage.tree(), String.format(MESSAGE, symbol.name())).secondary(classDef.name(), "Class declaration")));
    });
  }
}
