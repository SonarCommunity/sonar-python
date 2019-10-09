/*
 * SonarQube Python Plugin
 * Copyright (C) 2011-2019 SonarSource SA
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
package org.sonar.python.tree;

import com.sonar.sslr.api.AstNode;
import com.sonar.sslr.api.Grammar;
import com.sonar.sslr.impl.Parser;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.sonar.python.PythonConfiguration;
import org.sonar.python.api.tree.ClassDef;
import org.sonar.python.api.tree.FileInput;
import org.sonar.python.api.tree.FunctionDef;
import org.sonar.python.api.tree.PassStatement;
import org.sonar.python.api.tree.Statement;
import org.sonar.python.api.tree.Tree;
import org.sonar.python.api.tree.Tree.Kind;
import org.sonar.python.api.tree.WhileStatement;
import org.sonar.python.parser.PythonParser;

import static org.assertj.core.api.Assertions.assertThat;

public class TreeUtilsTest {

  @Test
  public void first_ancestor_of_kind() {
    String code = "" +
      "class A:\n" +
      "  def foo(): pass";
    FileInput root = parse(code);
    assertThat(TreeUtils.firstAncestorOfKind(root, Kind.CLASSDEF)).isNull();
    ClassDef classDef = (ClassDef) root.statements().statements().get(0);
    assertThat(TreeUtils.firstAncestorOfKind(classDef, Kind.FILE_INPUT, Kind.CLASSDEF)).isEqualTo(root);
    FunctionDef funcDef = (FunctionDef) classDef.body().statements().get(0);
    assertThat(TreeUtils.firstAncestorOfKind(funcDef, Kind.FILE_INPUT)).isEqualTo(root);
    assertThat(TreeUtils.firstAncestorOfKind(funcDef, Kind.CLASSDEF)).isEqualTo(classDef);

    code = "" +
      "while True:\n" +
      "  while True:\n" +
      "    pass";
    WhileStatement outerWhile = (WhileStatement) parse(code).statements().statements().get(0);
    WhileStatement innerWhile = (WhileStatement) outerWhile.body().statements().get(0);
    PassStatement passStatement = (PassStatement) innerWhile.body().statements().get(0);
    assertThat(TreeUtils.firstAncestorOfKind(passStatement, Kind.WHILE_STMT)).isEqualTo(innerWhile);
  }

  @Test
  public void first_ancestor() {
    String code = "" +
      "def outer():\n" +
      "  def inner():\n" +
      "    pass";
    FileInput root = parse(code);
    FunctionDef outerFunction = (FunctionDef) root.statements().statements().get(0);
    FunctionDef innerFunction = (FunctionDef) outerFunction.body().statements().get(0);
    Statement passStatement = innerFunction.body().statements().get(0);
    assertThat(TreeUtils.firstAncestor(passStatement, TreeUtilsTest::isOuterFunction)).isEqualTo(outerFunction);
  }

  private static boolean isOuterFunction(Tree tree) {
    return tree.is(Kind.FUNCDEF) && ((FunctionDef) tree).name().name().equals("outer");
  }

  private FileInput parse(String content) {
    Parser<Grammar> parser = PythonParser.create(new PythonConfiguration(StandardCharsets.UTF_8));
    AstNode astNode = parser.parse(content);
    FileInput parse = new PythonTreeMaker().fileInput(astNode);
    return parse;
  }
}
