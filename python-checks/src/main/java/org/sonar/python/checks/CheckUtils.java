/*
 * SonarQube Python Plugin
 * Copyright (C) 2011-2022 SonarSource SA
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
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonar.plugins.python.api.tree.ArgList;
import org.sonar.plugins.python.api.tree.Argument;
import org.sonar.plugins.python.api.tree.CallExpression;
import org.sonar.plugins.python.api.tree.ClassDef;
import org.sonar.plugins.python.api.tree.Expression;
import org.sonar.plugins.python.api.tree.Name;
import org.sonar.plugins.python.api.tree.Tree;
import org.sonar.python.api.PythonTokenType;
import org.sonar.python.tree.TreeUtils;

public class CheckUtils {

  private CheckUtils() {

  }

  public static boolean areEquivalent(@Nullable Tree leftTree, @Nullable Tree rightTree) {
    if (leftTree == rightTree) {
      return true;
    }
    if (leftTree == null || rightTree == null) {
      return false;
    }
    if (leftTree.getKind() != rightTree.getKind() || leftTree.children().size() != rightTree.children().size()) {
      return false;
    }
    if (leftTree.children().isEmpty() && rightTree.children().isEmpty()) {
      return areLeavesEquivalent(leftTree, rightTree);
    }

    List<Tree> children1 = leftTree.children();
    List<Tree> children2 = rightTree.children();
    for (int i = 0; i < children1.size(); i++) {
      if (!areEquivalent(children1.get(i), children2.get(i))) {
        return false;
      }
    }
    return true;
  }

  private static boolean areLeavesEquivalent(Tree leftLeaf, Tree rightLeaf) {
    if (leftLeaf.firstToken() == null && rightLeaf.firstToken() == null) {
      return true;
    }
    return leftLeaf.firstToken().type().equals(PythonTokenType.INDENT) || leftLeaf.firstToken().type().equals(PythonTokenType.DEDENT) ||
      leftLeaf.firstToken().value().equals(rightLeaf.firstToken().value());
  }

  @CheckForNull
  public static ClassDef getParentClassDef(Tree tree) {
    Tree current = tree.parent();
    while (current != null) {
      if (current.is(Tree.Kind.CLASSDEF)) {
        return (ClassDef) current;
      } else if (current.is(Tree.Kind.FUNCDEF, Tree.Kind.LAMBDA)) {
        return null;
      }
      current = current.parent();
    }
    return null;
  }

  public static boolean classHasInheritance(ClassDef classDef) {
    ArgList argList = classDef.args();
    if (argList == null) {
      return false;
    }
    List<Argument> arguments = argList.arguments();
    if (arguments.isEmpty()) {
      return false;
    }
    return arguments.size() != 1 || !"object".equals(arguments.get(0).firstToken().value());
  }

  public static boolean containsCallToLocalsFunction(Tree tree) {
    return TreeUtils.hasDescendant(tree, t -> t.is(Tree.Kind.CALL_EXPR) && calleeHasNameLocals(((CallExpression) t)));
  }

  private static boolean calleeHasNameLocals(CallExpression callExpression) {
    Expression callee = callExpression.callee();
    return callee.is(Tree.Kind.NAME) && "locals".equals(((Name) callee).name());
  }
}
