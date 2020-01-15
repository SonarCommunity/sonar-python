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
package org.sonar.python.semantic;

import com.google.common.base.Functions;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.sonar.plugins.python.api.symbols.Symbol;
import org.sonar.plugins.python.api.symbols.Usage;
import org.sonar.plugins.python.api.tree.CallExpression;
import org.sonar.plugins.python.api.tree.FileInput;
import org.sonar.plugins.python.api.tree.FunctionDef;
import org.sonar.plugins.python.api.tree.ImportFrom;
import org.sonar.plugins.python.api.tree.Tree;
import org.sonar.python.PythonTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonar.python.PythonTestUtils.parse;

public class ProjectLevelSymbolTableTest {

  private Map<String, Symbol> getSymbolByName(FileInput fileInput) {
    return fileInput.globalVariables().stream().collect(Collectors.toMap(Symbol::name, Functions.identity()));
  }

  @Test
  public void wildcard_import() {
    List<Symbol> modSymbols = Arrays.asList(new SymbolImpl("a", "mod.a"), new SymbolImpl("b", "mod.b"));
    Map<String, Set<Symbol>> globalSymbols = Collections.singletonMap("mod", new HashSet<>(modSymbols));
    FileInput tree = parse(
      new SymbolTableBuilder("my_package", "my_module.py", globalSymbols),
      "from mod import *",
      "print(a)"
    );
    assertThat(tree.globalVariables()).extracting(Symbol::name).containsExactlyInAnyOrder("a", "b");
    Symbol a = getSymbolByName(tree).get("a");
    assertThat(a.fullyQualifiedName()).isEqualTo("mod.a");
    assertThat(a.usages()).extracting(Usage::kind).containsExactlyInAnyOrder(Usage.Kind.OTHER);

    Symbol b = getSymbolByName(tree).get("b");
    assertThat(b.fullyQualifiedName()).isEqualTo("mod.b");
    assertThat(b.usages()).isEmpty();
  }

  @Test
  public void unresolved_wildcard_import() {
    FileInput tree = parse(
      new SymbolTableBuilder("my_package", "my_module.py", Collections.emptyMap()),
      "from external import *",
      "print(a)"
    );
    ImportFrom importFrom = ((ImportFrom) PythonTestUtils.getAllDescendant(tree, t -> t.is(Tree.Kind.IMPORT_FROM)).get(0));
    assertThat(importFrom.hasUnresolvedWildcardImport()).isTrue();
  }

  @Test
  public void function_symbol() {
    FunctionDef functionDef = (FunctionDef) parse("def fn(p1, p2): pass").statements().statements().get(0);
    FunctionSymbolImpl fnSymbol = new FunctionSymbolImpl(functionDef, "mod.fn");
    List<Symbol> modSymbols = Collections.singletonList(fnSymbol);
    Map<String, Set<Symbol>> globalSymbols = Collections.singletonMap("mod", new HashSet<>(modSymbols));
    FileInput tree = parse(
      new SymbolTableBuilder("my_package", "my_module.py", globalSymbols),
      "from mod import fn",
      "fn(1, 2)"
    );
    CallExpression callExpression = PythonTestUtils.getFirstChild(tree, t -> t.is(Tree.Kind.CALL_EXPR));
    Symbol importedFnSymbol = callExpression.calleeSymbol();
    assertThat(importedFnSymbol).isNotEqualTo(fnSymbol);
    assertThat(importedFnSymbol.kind()).isEqualTo(Symbol.Kind.FUNCTION);
    assertThat(fnSymbol.usages()).isEmpty();
    assertThat(importedFnSymbol.usages()).extracting(Usage::kind).containsExactlyInAnyOrder(Usage.Kind.IMPORT, Usage.Kind.OTHER);

    tree = parse(
      new SymbolTableBuilder("my_package", "my_module.py", globalSymbols),
      "import mod",
      "mod.fn(1, 2)"
    );
    callExpression = PythonTestUtils.getFirstChild(tree, t -> t.is(Tree.Kind.CALL_EXPR));
    importedFnSymbol = callExpression.calleeSymbol();
    assertThat(importedFnSymbol).isNotEqualTo(fnSymbol);
    assertThat(importedFnSymbol.kind()).isEqualTo(Symbol.Kind.FUNCTION);
    assertThat(fnSymbol.usages()).isEmpty();
    assertThat(importedFnSymbol.usages()).extracting(Usage::kind).containsExactlyInAnyOrder(Usage.Kind.OTHER);

    tree = parse(
      new SymbolTableBuilder("my_package", "my_module.py", globalSymbols),
      "import mod as mod1",
      "mod1.fn(1, 2)"
    );
    callExpression = PythonTestUtils.getFirstChild(tree, t -> t.is(Tree.Kind.CALL_EXPR));
    importedFnSymbol = callExpression.calleeSymbol();
    assertThat(importedFnSymbol.kind()).isEqualTo(Symbol.Kind.FUNCTION);
    assertThat(importedFnSymbol.fullyQualifiedName()).isEqualTo("mod.fn");
    assertThat(importedFnSymbol.usages()).extracting(Usage::kind).containsExactlyInAnyOrder(Usage.Kind.OTHER);
  }

  @Test
  public void import_already_existing_symbol() {
    FunctionDef functionDef = (FunctionDef) parse("def fn(p1, p2): pass").statements().statements().get(0);
    FunctionSymbolImpl fnSymbol = new FunctionSymbolImpl(functionDef, "mod.fn");
    List<Symbol> modSymbols = Collections.singletonList(fnSymbol);
    Map<String, Set<Symbol>> globalSymbols = Collections.singletonMap("mod", new HashSet<>(modSymbols));
    FileInput tree = parse(
      new SymbolTableBuilder("my_package", "my_module.py", globalSymbols),
      "fn = 42",
      "from mod import fn"
    );
    assertThat(tree.globalVariables()).hasSize(1);
    Symbol importedFnSymbol = tree.globalVariables().iterator().next();
    assertThat(importedFnSymbol.kind()).isEqualTo(Symbol.Kind.OTHER);
    assertThat(importedFnSymbol.name()).isEqualTo("fn");
    assertThat(importedFnSymbol.fullyQualifiedName()).isEqualTo(null);

    tree = parse(
      new SymbolTableBuilder("my_package", "my_module.py", globalSymbols),
      "mod = 42",
      "import mod"
    );
    assertThat(tree.globalVariables()).hasSize(1);
    Symbol modSymbol = tree.globalVariables().iterator().next();
    assertThat(modSymbol.kind()).isEqualTo(Symbol.Kind.OTHER);
    assertThat(modSymbol.name()).isEqualTo("mod");
    assertThat(modSymbol.fullyQualifiedName()).isEqualTo(null);
  }

  @Test
  public void other_imported_symbol() {
    SymbolImpl xSymbol = new SymbolImpl("x", "mod.x");
    List<Symbol> modSymbols = Collections.singletonList(xSymbol);
    Map<String, Set<Symbol>> globalSymbols = Collections.singletonMap("mod", new HashSet<>(modSymbols));
    FileInput tree = parse(
      new SymbolTableBuilder("my_package", "my_module.py", globalSymbols),
      "from mod import x"
    );
    Symbol importedXSymbol = tree.globalVariables().iterator().next();
    assertThat(importedXSymbol.name()).isEqualTo("x");
    assertThat(importedXSymbol.kind()).isEqualTo(Symbol.Kind.OTHER);
    assertThat(importedXSymbol.fullyQualifiedName()).isEqualTo("mod.x");
    assertThat(importedXSymbol.usages()).hasSize(1);
    assertThat(xSymbol).isNotEqualTo(importedXSymbol);
    assertThat(xSymbol.usages()).isEmpty();
  }

  @Test
  public void aliased_imported_symbols() {
    SymbolImpl xSymbol = new SymbolImpl("x", "mod.x");
    List<Symbol> modSymbols = Collections.singletonList(xSymbol);
    Map<String, Set<Symbol>> globalSymbols = Collections.singletonMap("mod", new HashSet<>(modSymbols));
    FileInput tree = parse(
      new SymbolTableBuilder("my_package", "my_module.py", globalSymbols),
      "from mod import x as y"
    );
    Symbol importedYSymbol = tree.globalVariables().iterator().next();
    assertThat(importedYSymbol.name()).isEqualTo("y");

    tree = parse(
      new SymbolTableBuilder("my_package", "my_module.py", globalSymbols),
      "import mod as mod1"
    );
    Symbol importedModSymbol = tree.globalVariables().iterator().next();
    assertThat(importedModSymbol.name()).isEqualTo("mod1");
  }
}
