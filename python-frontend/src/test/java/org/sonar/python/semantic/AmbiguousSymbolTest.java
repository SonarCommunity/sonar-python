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
package org.sonar.python.semantic;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.Test;
import org.sonar.plugins.python.api.symbols.AmbiguousSymbol;
import org.sonar.plugins.python.api.symbols.ClassSymbol;
import org.sonar.plugins.python.api.symbols.Symbol;
import org.sonar.plugins.python.api.tree.FileInput;
import org.sonar.python.PythonTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.sonar.python.PythonTestUtils.parse;

public class AmbiguousSymbolTest {

  @Test
  public void overloaded_functions() {
    Symbol fn = symbols(
      "from typing import overload",
      "@overload",
      "def fn(a, b): ...",
      "@overload",
      "def fn(a): ..."
    ).get("fn");
    assertThat(fn.kind()).isEqualTo(Symbol.Kind.AMBIGUOUS);
    assertThat(fn.name()).isEqualTo("fn");
    assertThat(fn.fullyQualifiedName()).isEqualTo("foo.fn");

    AmbiguousSymbol overloadedFn = (AmbiguousSymbol) fn;
    assertThat(overloadedFn.alternatives()).extracting(Symbol::kind).containsExactly(Symbol.Kind.FUNCTION, Symbol.Kind.FUNCTION);
  }

  @Test
  public void remove_usages() {
    Symbol fn = symbols(
      "from typing import overload",
      "@overload",
      "def fn(a, b): ...",
      "@overload",
      "def fn(a): ..."
    ).get("fn");
    ((SymbolImpl) fn).removeUsages();
    assertThat(fn.usages()).isEmpty();
    assertThat(((AmbiguousSymbolImpl) fn).alternatives()).allMatch(symbol -> symbol.usages().isEmpty());
  }

  @Test
  public void redefined_class() {
    Symbol a = symbols(
      "class A:",
      "  def meth(self): ...",
      "A = 42"
    ).get("A");

    assertThat(a.kind()).isEqualTo(Symbol.Kind.AMBIGUOUS);
    assertThat(a.name()).isEqualTo("A");
    assertThat(a.fullyQualifiedName()).isNull();

    Set<Symbol> symbols = ((AmbiguousSymbol) a).alternatives();
    assertThat(symbols).extracting(Symbol::kind).containsExactlyInAnyOrder(Symbol.Kind.CLASS, Symbol.Kind.OTHER);
    ClassSymbol classSymbol = (ClassSymbol) symbols.stream().filter(symbol -> symbol.kind() == Symbol.Kind.CLASS).findFirst().get();
    assertThat(classSymbol.declaredMembers()).extracting(Symbol::name).containsExactly("meth");
  }

  @Test
  public void redefined_class_member() {
    ClassSymbol a = ((ClassSymbol) symbols(
      "class A:",
      "  _foo = 42",
      "  _foo = 43",
      "  def meth(self, a, b, c): ...",
      "  def meth(self): ..."
    ).get("A"));

    assertThat(a.declaredMembers()).extracting(Symbol::kind).containsExactlyInAnyOrder(Symbol.Kind.AMBIGUOUS, Symbol.Kind.OTHER);
  }

  @Test
  public void global_ambiguous_symbol() {
    Symbol x = symbols(
      "def x(): ...",
      "def foo():",
      "  global x",
      "  x = 42"
    ).get("x");
    assertThat(x.kind()).isEqualTo(Symbol.Kind.AMBIGUOUS);
    assertThat(((AmbiguousSymbol) x).alternatives()).extracting(Symbol::kind).containsExactlyInAnyOrder(Symbol.Kind.FUNCTION, Symbol.Kind.OTHER);
  }

  @Test
  public void not_ambiguous_symbols() {
    Symbol x = symbols(
      "x = 42",
      "x = 43"
    ).get("x");
    assertThat(x.kind()).isEqualTo(Symbol.Kind.OTHER);
  }

  @Test(expected = IllegalArgumentException.class)
  public void empty_ambiguous_symbol_creation() {
    AmbiguousSymbolImpl.create(Collections.emptySet());
  }

  @Test(expected = IllegalArgumentException.class)
  public void singleton_ambiguous_symbol_creation() {
    AmbiguousSymbolImpl.create(Collections.singleton(new SymbolImpl("foo", null)));
  }

  @Test(expected = IllegalArgumentException.class)
  public void ambiguous_symbol_creation_different_name_different_fqn() {
    SymbolImpl foo = new SymbolImpl("foo", "mod.foo");
    SymbolImpl bar = new SymbolImpl("bar", "mod.bar");
    AmbiguousSymbolImpl.create(new HashSet<>(Arrays.asList(foo, bar)));
  }

  @Test
  public void ambiguous_symbol_creation_different_name_same_fqn() {
    SymbolImpl foo = new SymbolImpl("foo", "mod.bar");
    SymbolImpl bar = new SymbolImpl("bar", "mod.bar");
    AmbiguousSymbol ambiguousSymbol = AmbiguousSymbolImpl.create(new HashSet<>(Arrays.asList(foo, bar)));
    assertThat(ambiguousSymbol.fullyQualifiedName()).isEqualTo("mod.bar");
    assertThat(ambiguousSymbol.name()).isEmpty();
  }

  @Test
  public void ambiguous_symbol_creation_different_fqn() {
    SymbolImpl foo = new SymbolImpl("foo", "mod1.foo");
    SymbolImpl otherFoo = new SymbolImpl("foo", "mod2.foo");
    AmbiguousSymbol ambiguousSymbol = AmbiguousSymbolImpl.create(new HashSet<>(Arrays.asList(foo, otherFoo)));
    assertThat(ambiguousSymbol.fullyQualifiedName()).isNull();
    assertThat(ambiguousSymbol.name()).isEqualTo("foo");
    assertThat(ambiguousSymbol.alternatives()).containsExactlyInAnyOrder(foo, otherFoo);
  }

  @Test
  public void aliased_import() {
    Symbol symbol = symbols(
      "try:",
      "  from gettext import gettext as _",
      "except ImportError:",
      "  def _(s): return s"
    ).get("_");
    assertThat(symbol.name()).isEqualTo("_");
    assertThat(symbol.is(Symbol.Kind.AMBIGUOUS)).isTrue();
    assertThat(((AmbiguousSymbol) symbol).alternatives()).extracting(Symbol::kind).containsExactlyInAnyOrder(Symbol.Kind.OTHER, Symbol.Kind.FUNCTION);
  }

  @Test
  public void copy_without_usages() {
    SymbolImpl foo = new SymbolImpl("foo", "mod1.foo");
    SymbolImpl otherFoo = new SymbolImpl("foo", "mod2.foo");
    AmbiguousSymbol ambiguousSymbol = AmbiguousSymbolImpl.create(new HashSet<>(Arrays.asList(foo, otherFoo)));
    AmbiguousSymbolImpl copy = ((AmbiguousSymbolImpl) ambiguousSymbol).copyWithoutUsages();
    assertThat(copy.is(Symbol.Kind.AMBIGUOUS)).isTrue();
    assertThat(copy.usages()).isEmpty();
    assertThat(copy).isNotEqualTo(ambiguousSymbol);
    assertThat(copy.alternatives()).doesNotContain(foo, otherFoo);
    assertThat(copy.alternatives()).extracting(Symbol::name, Symbol::fullyQualifiedName).containsExactlyInAnyOrder(tuple("foo", "mod1.foo"), tuple("foo", "mod2.foo"));
  }

  private Map<String, Symbol> symbols(String... code) {
    FileInput fileInput = parse(new SymbolTableBuilder("", PythonTestUtils.pythonFile("foo")), code);
    return fileInput.globalVariables().stream().collect(Collectors.toMap(Symbol::name, Function.identity()));
  }
}
