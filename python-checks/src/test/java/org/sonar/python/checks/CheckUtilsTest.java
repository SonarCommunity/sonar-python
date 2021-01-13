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

import com.sonar.sslr.api.AstNode;
import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.Objects;
import javax.annotation.Nullable;
import org.junit.Test;
import org.sonar.plugins.python.api.tree.ClassDef;
import org.sonar.plugins.python.api.tree.FileInput;
import org.sonar.plugins.python.api.tree.FunctionDef;
import org.sonar.plugins.python.api.tree.Tree;
import org.sonar.python.parser.PythonParser;
import org.sonar.python.tree.ArgListImpl;
import org.sonar.python.tree.PythonTreeMaker;

import static org.assertj.core.api.Assertions.assertThat;

public class CheckUtilsTest {

  @Test
  public void private_constructor() throws Exception {
    Constructor constructor = CheckUtils.class.getDeclaredConstructor();
    assertThat(constructor.isAccessible()).isFalse();
    constructor.setAccessible(true);
    constructor.newInstance();
  }

  @Test
  public void statement_equivalence() {
    assertThat(CheckUtils.areEquivalent(parse("x = x + 1"), parse("x = x + 1"))).isTrue();
    assertThat(CheckUtils.areEquivalent(parse("x = x + 1"), parse("x = x + 1"))).isTrue();
    assertThat(CheckUtils.areEquivalent(parse("x = x + 1"), parse("x = x + 1"))).isTrue();
    assertThat(CheckUtils.areEquivalent(parse("x = x + 1"), parse("x = x + 2"))).isFalse();
    assertThat(CheckUtils.areEquivalent(parse("foo()"), parse("foo()"))).isTrue();
    assertThat(CheckUtils.areEquivalent(parse("foo()"), parse("foo"))).isFalse();
    assertThat(CheckUtils.areEquivalent(parse("foo"), parse("foo()"))).isFalse();
  }

  @Test
  public void comparison_equivalence() {
    assertThat(CheckUtils.areEquivalent(parse("foo is None"), parse("foo is not None"))).isFalse();
    assertThat(CheckUtils.areEquivalent(parse("x < 2"), parse("x > 2"))).isFalse();
    assertThat(CheckUtils.areEquivalent(parse("foo is None"), parse("foo is  None"))).isTrue();
    assertThat(CheckUtils.areEquivalent(parse("x < 1"), parse("x < 1"))).isTrue();
  }

  @Test
  public void tree_equivalence() {
    assertThat(CheckUtils.areEquivalent(new ArgListImpl(Collections.emptyList(), Collections.emptyList()),
      new ArgListImpl(Collections.emptyList(), Collections.emptyList()))).isTrue();
  }

  @Test
  public void null_equivalence() {
    assertThat(CheckUtils.areEquivalent(null, null)).isTrue();
    assertThat(CheckUtils.areEquivalent(null, parse("class clazz(): \n pass"))).isFalse();
    assertThat(CheckUtils.areEquivalent(parse("class clazz(): \n pass"), null)).isFalse();
  }

  @Test
  public void statement_list_equivalence() {
    assertThat(CheckUtils.areEquivalent(parse("foo()\nbar()"), parse("foo()\nbar()"))).isTrue();
    assertThat(CheckUtils.areEquivalent(parse("foo()\n  "), parse("foo()\n"))).isTrue();
    assertThat(CheckUtils.areEquivalent(parse("foo()\n"), parse("foo()\n  "))).isTrue();
    assertThat(CheckUtils.areEquivalent(parse("foo()"), parse("foo()\nfoo()"))).isFalse();
    assertThat(CheckUtils.areEquivalent(parse("foo()\nfoo()"), parse("foo()"))).isFalse();
    assertThat(CheckUtils.areEquivalent(parse("foo()\nbar()"), parse("foo()\nbar"))).isFalse();
  }

  @Test
  public void lambda_equivalence() {
    assertThat(CheckUtils.areEquivalent(parse("x = lambda a : a + 10"), parse("x = lambda a : a + 10"))).isTrue();
    assertThat(CheckUtils.areEquivalent(parse("x = lambda a : a + 10"), parse("x = lambda a : a + 5"))).isFalse();
  }

  @Test
  public void no_parent_class() {
    FileInput file = (FileInput) parse("" +
      "def f():\n" +
      "    pass\n");
    FunctionDef f = descendantFunction(file, "f");
    assertThat(f).isNotNull();
    assertThat(CheckUtils.getParentClassDef(f)).isNull();
  }

  @Test
  public void parent_class() {
    FileInput file = (FileInput) parse("" +
      "class A:\n" +
      "    def f():\n" +
      "        def g():\n" +
      "            pass\n" +
      "        pass\n" +
      "\n" +
      "    if x:\n" +
      "        def h():\n" +
      "            pass\n");
    FunctionDef f = descendantFunction(file, "f");
    FunctionDef g = descendantFunction(file, "g");
    FunctionDef h = descendantFunction(file, "h");
    assertThat(f).isNotNull();
    assertThat(g).isNotNull();
    assertThat(h).isNotNull();

    ClassDef parent = CheckUtils.getParentClassDef(f);
    assertThat(parent).isNotNull();
    assertThat(parent.name().name()).isEqualTo("A");

    parent = CheckUtils.getParentClassDef(g);
    assertThat(parent).isNull();

    parent = CheckUtils.getParentClassDef(h);
    assertThat(parent).isNotNull();
    assertThat(parent.name().name()).isEqualTo("A");
  }

  private Tree parse(String content) {
    PythonParser parser = PythonParser.create();
    AstNode astNode = parser.parse(content);
    FileInput parse = new PythonTreeMaker().fileInput(astNode);
    return parse;
  }

  @Nullable
  private static FunctionDef descendantFunction(Tree tree, String name) {
    if (tree.is(Tree.Kind.FUNCDEF)) {
      FunctionDef functionDef = (FunctionDef) tree;
      if (functionDef.name().name().equals(name)) {
        return functionDef;
      }
    }
    return tree.children().stream()
      .map(child -> descendantFunction(child, name))
      .filter(Objects::nonNull)
      .findFirst().orElse(null);
  }

}
