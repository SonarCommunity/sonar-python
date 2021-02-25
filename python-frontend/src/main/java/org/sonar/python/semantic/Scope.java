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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonar.plugins.python.api.PythonFile;
import org.sonar.plugins.python.api.symbols.AmbiguousSymbol;
import org.sonar.plugins.python.api.symbols.FunctionSymbol;
import org.sonar.plugins.python.api.symbols.Symbol;
import org.sonar.plugins.python.api.symbols.Usage;
import org.sonar.plugins.python.api.tree.ClassDef;
import org.sonar.plugins.python.api.tree.FunctionDef;
import org.sonar.plugins.python.api.tree.ImportFrom;
import org.sonar.plugins.python.api.tree.Name;
import org.sonar.plugins.python.api.tree.Parameter;
import org.sonar.plugins.python.api.tree.Tree;
import org.sonar.python.tree.FunctionDefImpl;
import org.sonar.python.types.TypeShed;

class Scope {

  final Tree rootTree;
  private PythonFile pythonFile;
  private String fullyQualifiedModuleName;
  private final ProjectLevelSymbolTable projectLevelSymbolTable;
  private final Scope parent;
  final Map<String, Symbol> symbolsByName = new HashMap<>();
  private final Set<Symbol> symbols = new HashSet<>();
  final Set<Symbol> builtinSymbols = new HashSet<>();
  private final Set<String> globalNames = new HashSet<>();
  private final Set<String> nonlocalNames = new HashSet<>();
  final Map<String, SymbolImpl> instanceAttributesByName = new HashMap<>();

  Scope(@Nullable Scope parent, Tree rootTree, PythonFile pythonFile, String fullyQualifiedModuleName, ProjectLevelSymbolTable projectLevelSymbolTable) {
    this.parent = parent;
    this.rootTree = rootTree;
    this.pythonFile = pythonFile;
    this.fullyQualifiedModuleName = fullyQualifiedModuleName;
    this.projectLevelSymbolTable = projectLevelSymbolTable;
  }

  Set<Symbol> symbols() {
    return Collections.unmodifiableSet(symbols);
  }

  void createBuiltinSymbol(String name, Map<String, Symbol> typeShedSymbols) {
    SymbolImpl symbol;
    Symbol typeShedSymbol = typeShedSymbols.get(name);
    if (typeShedSymbol != null) {
      symbol = ((SymbolImpl) typeShedSymbol).copyWithoutUsages();
    } else {
      symbol = new SymbolImpl(name, name);
    }
    symbols.add(symbol);
    builtinSymbols.add(symbol);
    symbolsByName.put(name, symbol);
  }

  void createSymbolsFromWildcardImport(Set<Symbol> importedSymbols, ImportFrom importFrom) {
    importedSymbols.forEach(symbol -> {
      Symbol importedSymbol = copySymbol(symbol.name(), symbol);
      if (!isExistingSymbol(importedSymbol.name())) {
        symbols.add(importedSymbol);
        symbolsByName.put(symbol.name(), importedSymbol);
        ((SymbolImpl) importedSymbol).addUsage(importFrom, Usage.Kind.IMPORT);
      } else {
        SymbolImpl originalSymbol = resolve(symbol.name());
        if (originalSymbol != null) {
          resetSymbolInfo(importedSymbol.fullyQualifiedName(), originalSymbol);
          originalSymbol.addUsage(importFrom, Usage.Kind.IMPORT);
        }
      }
    });
  }

  void createSelfParameter(Parameter parameter) {
    Name nameTree = parameter.name();
    if (nameTree == null) {
      return;
    }
    String symbolName = nameTree.name();
    SymbolImpl symbol = new SelfSymbolImpl(symbolName, parent);
    symbols.add(symbol);
    symbolsByName.put(symbolName, symbol);
    symbol.addUsage(nameTree, Usage.Kind.PARAMETER);
  }

  void addFunctionSymbol(FunctionDef functionDef, @Nullable String fullyQualifiedName) {
    String symbolName = functionDef.name().name();
    if (isExistingSymbol(symbolName)) {
      addBindingUsage(functionDef.name(), Usage.Kind.FUNC_DECLARATION, fullyQualifiedName);
    } else {
      FunctionSymbolImpl functionSymbol = new FunctionSymbolImpl(functionDef, fullyQualifiedName, pythonFile);
      functionSymbol.setIsDjangoView(projectLevelSymbolTable.isDjangoView(fullyQualifiedName));
      ((FunctionDefImpl) functionDef).setFunctionSymbol(functionSymbol);
      symbols.add(functionSymbol);
      symbolsByName.put(symbolName, functionSymbol);
      functionSymbol.addUsage(functionDef.name(), Usage.Kind.FUNC_DECLARATION);
    }
  }

  private Symbol copySymbol(String symbolName, Symbol symbol) {
    return copySymbol(symbolName, symbol, new HashSet<>());
  }

  private Symbol copySymbol(String symbolName, Symbol symbol, Set<Symbol> alreadyVisitedSymbols) {
    alreadyVisitedSymbols.add(symbol);
    switch (symbol.kind()) {
      case FUNCTION:
        return new FunctionSymbolImpl(symbolName, (FunctionSymbol) symbol);
      case CLASS:
        return copyClassSymbol(symbolName, (ClassSymbolImpl) symbol, alreadyVisitedSymbols);
      case AMBIGUOUS:
        Set<Symbol> alternativeSymbols = ((AmbiguousSymbol) symbol).alternatives().stream()
          .map(s -> copySymbol(symbolName, s, alreadyVisitedSymbols))
          .collect(Collectors.toSet());
        return new AmbiguousSymbolImpl(symbolName, symbol.fullyQualifiedName(), alternativeSymbols);
      default:
        SymbolImpl copiedSymbol = new SymbolImpl(symbolName, symbol.fullyQualifiedName(), symbol.annotatedTypeName());
        for (Map.Entry<String, Symbol> kv: ((SymbolImpl) symbol).getChildrenSymbolByName().entrySet()) {
          copiedSymbol.addChildSymbol(((SymbolImpl) kv.getValue()).copyWithoutUsages());
        }
        return copiedSymbol;
    }
  }

  private ClassSymbolImpl copyClassSymbol(String symbolName, ClassSymbolImpl originalClassSymbol, Set<Symbol> alreadyVisitedSymbols) {
    // Must use symbolName to preserve import aliases
    ClassSymbolImpl classSymbol = new ClassSymbolImpl(symbolName, originalClassSymbol.fullyQualifiedName(), originalClassSymbol.definitionLocation(),
      originalClassSymbol.hasDecorators(), originalClassSymbol.hasMetaClass(), originalClassSymbol.metaclassFQN(), originalClassSymbol.supportsGenerics());

    for (Symbol originalSymbol : originalClassSymbol.superClasses()) {
      Symbol globalSymbol = projectLevelSymbolTable.getSymbol(originalSymbol.fullyQualifiedName());
      if (globalSymbol != null && globalSymbol.kind() == Symbol.Kind.CLASS) {
        Symbol parentClass = alreadyVisitedSymbols.contains(globalSymbol)
          ? new SymbolImpl(globalSymbol.name(), globalSymbol.fullyQualifiedName())
          : copySymbol(globalSymbol.name(), globalSymbol, alreadyVisitedSymbols);
        classSymbol.addSuperClass(parentClass);
      } else {
        classSymbol.addSuperClass(originalSymbol);
      }
    }
    classSymbol.addMembers(originalClassSymbol
      .declaredMembers().stream()
      .map(m -> ((SymbolImpl) m).copyWithoutUsages())
      .collect(Collectors.toList()));
    if (originalClassSymbol.hasSuperClassWithoutSymbol()) {
      classSymbol.setHasSuperClassWithoutSymbol();
    }
    return classSymbol;
  }

  void addModuleSymbol(Name nameTree, @CheckForNull String fullyQualifiedName) {
    String symbolName = nameTree.name();
    Set<Symbol> moduleExportedSymbols = projectLevelSymbolTable.getSymbolsFromModule(fullyQualifiedName);
    if (moduleExportedSymbols != null && !isExistingSymbol(symbolName)) {
      SymbolImpl moduleSymbol = new SymbolImpl(symbolName, fullyQualifiedName);
      moduleExportedSymbols.forEach(symbol -> moduleSymbol.addChildSymbol(copySymbol(symbol.name(), symbol)));
      this.symbols.add(moduleSymbol);
      symbolsByName.put(symbolName, moduleSymbol);
    } else if (!isExistingSymbol(symbolName) && fullyQualifiedName != null && !fullyQualifiedName.equals(fullyQualifiedModuleName)) {
      Set<Symbol> standardLibrarySymbols = TypeShed.symbolsForModule(fullyQualifiedName);
      if (!standardLibrarySymbols.isEmpty()) {
        SymbolImpl moduleSymbol = new SymbolImpl(symbolName, fullyQualifiedName);
        standardLibrarySymbols.forEach(symbol -> moduleSymbol.addChildSymbol(copySymbol(symbol.name(), symbol)));
        this.symbols.add(moduleSymbol);
        symbolsByName.put(symbolName, moduleSymbol);
      }
    }
    addBindingUsage(nameTree, Usage.Kind.IMPORT, fullyQualifiedName);
  }

  void addImportedSymbol(Name nameTree, @CheckForNull String fullyQualifiedName, String fromModuleName) {
    String symbolName = nameTree.name();
    Symbol globalSymbol = projectLevelSymbolTable.getSymbol(fullyQualifiedName);
    if (globalSymbol == null && fullyQualifiedName != null && !fromModuleName.equals(fullyQualifiedModuleName)) {
      globalSymbol = TypeShed.symbolWithFQN(fromModuleName, fullyQualifiedName);
    }
    if (globalSymbol == null || isExistingSymbol(symbolName)) {
      addBindingUsage(nameTree, Usage.Kind.IMPORT, fullyQualifiedName);
    } else {
      Symbol symbol = copySymbol(symbolName, globalSymbol);
      this.symbols.add(symbol);
      symbolsByName.put(symbolName, symbol);
      ((SymbolImpl) symbol).addUsage(nameTree, Usage.Kind.IMPORT);
    }
  }

  private boolean isExistingSymbol(String symbolName) {
    return symbolsByName.containsKey(symbolName) || globalNames.contains(symbolName) || nonlocalNames.contains(symbolName);
  }

  void addBindingUsage(Name nameTree, Usage.Kind kind, @Nullable String fullyQualifiedName) {
    String symbolName = nameTree.name();
    if (!isExistingSymbol(symbolName)) {
      SymbolImpl symbol = new SymbolImpl(symbolName, fullyQualifiedName);
      symbols.add(symbol);
      symbolsByName.put(symbolName, symbol);
    }
    SymbolImpl symbol = resolve(symbolName);
    if (symbol != null) {
      resetSymbolInfo(fullyQualifiedName, symbol);
      symbol.addUsage(nameTree, kind);
    }
  }

  private static void resetSymbolInfo(@Nullable String fullyQualifiedName, SymbolImpl symbol) {
    if (!Symbol.Kind.OTHER.equals(symbol.kind())) {
      symbol.setKind(Symbol.Kind.OTHER);
    }
    if (fullyQualifiedName != null && !fullyQualifiedName.equals(symbol.fullyQualifiedName)) {
      symbol.fullyQualifiedName = null;
    }
    if (fullyQualifiedName == null && symbol.fullyQualifiedName != null) {
      symbol.fullyQualifiedName = null;
    }
  }

  @CheckForNull
  SymbolImpl resolve(String symbolName) {
    Symbol symbol = symbolsByName.get(symbolName);
    if (parent == null || symbol != null) {
      return (SymbolImpl) symbol;
    }
    if (parent.rootTree.is(Tree.Kind.CLASSDEF)) {
      return parent.parent.resolve(symbolName);
    }
    return parent.resolve(symbolName);
  }

  void addGlobalName(String name) {
    globalNames.add(name);
  }

  void addNonLocalName(String name) {
    nonlocalNames.add(name);
  }

  void addClassSymbol(ClassDef classDef, @Nullable String fullyQualifiedName) {
    String symbolName = classDef.name().name();
    if (isExistingSymbol(symbolName)) {
      addBindingUsage(classDef.name(), Usage.Kind.CLASS_DECLARATION, fullyQualifiedName);
    } else {
      ClassSymbolImpl classSymbol = new ClassSymbolImpl(classDef, fullyQualifiedName, pythonFile);
      symbols.add(classSymbol);
      symbolsByName.put(symbolName, classSymbol);
      classSymbol.addUsage(classDef.name(), Usage.Kind.CLASS_DECLARATION);
    }
  }

  void replaceSymbolWithAmbiguousSymbol(Symbol symbol, AmbiguousSymbol ambiguousSymbol) {
    symbols.remove(symbol);
    symbols.add(ambiguousSymbol);
    symbolsByName.remove(symbol.name());
    symbolsByName.put(symbol.name(), ambiguousSymbol);
  }
}
