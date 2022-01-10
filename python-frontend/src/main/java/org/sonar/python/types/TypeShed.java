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
package org.sonar.python.types;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.plugins.python.api.ProjectPythonVersion;
import org.sonar.plugins.python.api.PythonVersionUtils;
import org.sonar.plugins.python.api.symbols.AmbiguousSymbol;
import org.sonar.plugins.python.api.symbols.ClassSymbol;
import org.sonar.plugins.python.api.symbols.Symbol;
import org.sonar.plugins.python.api.types.BuiltinTypes;
import org.sonar.python.semantic.AmbiguousSymbolImpl;
import org.sonar.python.semantic.BuiltinSymbols;
import org.sonar.python.semantic.ClassSymbolImpl;
import org.sonar.python.semantic.FunctionSymbolImpl;
import org.sonar.python.semantic.SymbolImpl;
import org.sonar.python.types.protobuf.SymbolsProtos;
import org.sonar.python.types.protobuf.SymbolsProtos.ModuleSymbol;
import org.sonar.python.types.protobuf.SymbolsProtos.OverloadedFunctionSymbol;

import static org.sonar.plugins.python.api.types.BuiltinTypes.BOOL;
import static org.sonar.plugins.python.api.types.BuiltinTypes.COMPLEX;
import static org.sonar.plugins.python.api.types.BuiltinTypes.DICT;
import static org.sonar.plugins.python.api.types.BuiltinTypes.FLOAT;
import static org.sonar.plugins.python.api.types.BuiltinTypes.INT;
import static org.sonar.plugins.python.api.types.BuiltinTypes.LIST;
import static org.sonar.plugins.python.api.types.BuiltinTypes.NONE_TYPE;
import static org.sonar.plugins.python.api.types.BuiltinTypes.STR;
import static org.sonar.plugins.python.api.types.BuiltinTypes.TUPLE;
import static org.sonar.python.types.TypeShedThirdParties.commonSymbols;
import static org.sonar.python.types.TypeShedThirdParties.getModuleSymbols;

public class TypeShed {

  private static Map<String, Symbol> builtins;
  private static final Map<String, Map<String, Symbol>> typeShedSymbols = new HashMap<>();
  private static final Map<String, Set<Symbol>> builtinGlobalSymbols = new HashMap<>();
  private static final Set<String> modulesInProgress = new HashSet<>();

  private static final String THIRD_PARTY_2AND3 = "typeshed/third_party/2and3/";
  private static final String THIRD_PARTY_2 = "typeshed/third_party/2/";
  private static final String THIRD_PARTY_3 = "typeshed/third_party/3/";
  private static final String CUSTOM_THIRD_PARTY = "custom/";
  private static final String PROTOBUF = "protobuf/";
  private static final String BUILTINS_FQN = "builtins";
  private static final String BUILTINS_PREFIX = BUILTINS_FQN + ".";
  // Those fundamentals builtins symbols need not to be ambiguous for the frontend to work properly
  private static final Set<String> BUILTINS_TO_DISAMBIGUATE = new HashSet<>(
    Arrays.asList(INT, FLOAT, COMPLEX, STR, BuiltinTypes.SET, DICT, LIST, TUPLE, NONE_TYPE, BOOL, "type", "super", "frozenset", "memoryview"));

  static {
    BUILTINS_TO_DISAMBIGUATE.addAll(BuiltinSymbols.EXCEPTIONS);
  }

  private static final Logger LOG = Loggers.get(TypeShed.class);
  private static Set<String> supportedPythonVersions;

  private TypeShed() {
  }

  //================================================================================
  // Public methods
  //================================================================================

  public static Map<String, Symbol> builtinSymbols() {
    if ((TypeShed.builtins == null)) {
      supportedPythonVersions = ProjectPythonVersion.currentVersions().stream().map(PythonVersionUtils.Version::serializedValue).collect(Collectors.toSet());
      Map<String, Symbol> builtins = getSymbolsFromProtobufModule(BUILTINS_FQN);
      builtins.put(NONE_TYPE, new ClassSymbolImpl(NONE_TYPE, NONE_TYPE));
      TypeShed.builtins = Collections.unmodifiableMap(builtins);
      TypeShed.builtinGlobalSymbols.put("", new HashSet<>(builtins.values()));
    }
    return builtins;
  }

  public static ClassSymbol typeShedClass(String fullyQualifiedName) {
    Symbol symbol = builtinSymbols().get(fullyQualifiedName);
    if (symbol == null) {
      throw new IllegalArgumentException("No TypeShed symbol found for name: " + fullyQualifiedName);
    }
    if (symbol.kind() != Symbol.Kind.CLASS) {
      throw new IllegalArgumentException("TypeShed symbol " + fullyQualifiedName + " is not a class");
    }
    return (ClassSymbol) symbol;
  }

  /**
   * Returns map of exported symbols by name for a given module
   */
  public static Map<String, Symbol> symbolsForModule(String moduleName) {
    if (!TypeShed.typeShedSymbols.containsKey(moduleName)) {
      Map<String, Symbol> symbols = searchTypeShedForModule(moduleName);
      typeShedSymbols.put(moduleName, symbols);
      return symbols;
    }
    return TypeShed.typeShedSymbols.get(moduleName);
  }

  @CheckForNull
  public static Symbol symbolWithFQN(String stdLibModuleName, String fullyQualifiedName) {
    Map<String, Symbol> symbols = symbolsForModule(stdLibModuleName);
    // TODO: improve performance - see SONARPY-955
    Symbol symbolByFqn = symbols.values().stream().filter(s -> fullyQualifiedName.equals(s.fullyQualifiedName())).findFirst().orElse(null);
    if (symbolByFqn != null || !fullyQualifiedName.contains(".")) {
      return symbolByFqn;
    }

    // If FQN of the member does not match the pattern of "package_name.file_name.symbol_name"
    // (e.g. it could be declared in package_name.file_name using import) or in case when
    // we have import with an alias (from module import method as alias_method), we retrieve symbol_name out of
    // FQN and try to look up by local symbol name, rather than FQN
    String[] fqnSplittedByDot = fullyQualifiedName.split("\\.");
    String symbolLocalNameFromFqn = fqnSplittedByDot[fqnSplittedByDot.length - 1];
    return symbols.get(symbolLocalNameFromFqn);
  }

  @CheckForNull
  public static Symbol symbolWithFQN(String fullyQualifiedName) {
    Map<String, Symbol> builtinSymbols = builtinSymbols();
    Symbol builtinSymbol = builtinSymbols.get(normalizedFqn(fullyQualifiedName));
    if (builtinSymbol != null) {
      return builtinSymbol;
    }
    String[] fqnSplittedByDot = fullyQualifiedName.split("\\.");
    String moduleName = Arrays.stream(fqnSplittedByDot, 0, fqnSplittedByDot.length - 1).collect(Collectors.joining("."));
    return symbolWithFQN(moduleName, fullyQualifiedName);
  }

  /**
   * Returns stub symbols to be used by SonarSecurity.
   * Ambiguous symbols that only contain class symbols are disambiguated with latest Python version.
   */
  public static Collection<Symbol> stubFilesSymbols() {
    Set<Symbol> symbols = new HashSet<>(TypeShed.builtinSymbols().values());
    for (Map<String, Symbol> symbolsByFqn : typeShedSymbols.values()) {
      for (Symbol symbol : symbolsByFqn.values()) {
        symbols.add(isAmbiguousSymbolOfClasses(symbol) ? disambiguateWithLatestPythonSymbol(((AmbiguousSymbol) symbol).alternatives()) : symbol);
      }
    }
    return symbols;
  }

  public static String normalizedFqn(String fqn) {
    if (fqn.startsWith(BUILTINS_PREFIX)) {
      return fqn.substring(BUILTINS_PREFIX.length());
    }
    return fqn;
  }

  public static String normalizedFqn(String fqn, String moduleName, String localName) {
    return normalizedFqn(fqn, moduleName, localName, null);
  }

  public static String normalizedFqn(String fqn, String moduleName, String localName, @Nullable String containerClassFqn) {
    if (containerClassFqn != null) return containerClassFqn + "." + localName;
    if (fqn.startsWith(moduleName)) return normalizedFqn(fqn);
    return moduleName + "." + localName;
  }

  public static boolean isValidForProjectPythonVersion(List<String> validForPythonVersions) {
    if (validForPythonVersions.isEmpty()) {
      return true;
    }
    HashSet<String> intersection = new HashSet<>(validForPythonVersions);
    intersection.retainAll(supportedPythonVersions);
    return !intersection.isEmpty();
  }

  public static Set<Symbol> symbolsFromProtobufDescriptors(Set<Object> protobufDescriptors, @Nullable String containerClassFqn, String moduleName) {
    Set<Symbol> symbols = new HashSet<>();
    for (Object descriptor : protobufDescriptors) {
      if (descriptor instanceof SymbolsProtos.ClassSymbol) {
        symbols.add(new ClassSymbolImpl(((SymbolsProtos.ClassSymbol) descriptor), moduleName));
      }
      if (descriptor instanceof SymbolsProtos.FunctionSymbol) {
        symbols.add(new FunctionSymbolImpl(((SymbolsProtos.FunctionSymbol) descriptor), containerClassFqn, moduleName));
      }
      if (descriptor instanceof OverloadedFunctionSymbol) {
        if (((OverloadedFunctionSymbol) descriptor).getDefinitionsList().size() < 2) {
          throw new IllegalStateException("Overloaded function symbols should have at least two definitions.");
        }
        symbols.add(fromOverloadedFunction(((OverloadedFunctionSymbol) descriptor), containerClassFqn, moduleName));
      }
      if (descriptor instanceof SymbolsProtos.VarSymbol) {
        SymbolsProtos.VarSymbol varSymbol = (SymbolsProtos.VarSymbol) descriptor;
        SymbolImpl symbol = new SymbolImpl(varSymbol, moduleName);
        if (varSymbol.getIsImportedModule()) {
          Map<String, Symbol> moduleExportedSymbols = symbolsForModule(varSymbol.getFullyQualifiedName());
          moduleExportedSymbols.values().forEach(symbol::addChildSymbol);
        }
        symbols.add(symbol);
      }
    }
    return symbols;
  }


  @CheckForNull
  public static SymbolsProtos.ClassSymbol classDescriptorWithFQN(String fullyQualifiedName) {
    String[] fqnSplitByDot = fullyQualifiedName.split("\\.");
    String symbolLocalNameFromFqn = fqnSplitByDot[fqnSplitByDot.length - 1];
    String moduleName = Arrays.stream(fqnSplitByDot, 0, fqnSplitByDot.length - 1).collect(Collectors.joining("."));
    InputStream resource = TypeShed.class.getResourceAsStream(PROTOBUF + moduleName + ".protobuf");
    if (resource == null) return null;
    ModuleSymbol moduleSymbol = deserializedModule(moduleName, resource);
    if (moduleSymbol == null) return null;
    for (SymbolsProtos.ClassSymbol classSymbol : moduleSymbol.getClassesList()) {
      if (classSymbol.getName().equals(symbolLocalNameFromFqn)) {
        return classSymbol;
      }
    }
    return null;
  }

  //================================================================================
  // Private methods
  //================================================================================

  // used by tests whenever 'sonar.python.version' changes
  static void resetBuiltinSymbols() {
    builtins = null;
    typeShedSymbols.clear();
    builtinSymbols();
  }

  private static Map<String, Symbol> searchTypeShedForModule(String moduleName) {
    if (modulesInProgress.contains(moduleName)) {
      return new HashMap<>();
    }
    modulesInProgress.add(moduleName);
    Map<String, Symbol> customSymbols = getModuleSymbols(moduleName, CUSTOM_THIRD_PARTY, builtinGlobalSymbols);
    if (!customSymbols.isEmpty()) {
      modulesInProgress.remove(moduleName);
      return customSymbols;
    }
    Map<String, Symbol> symbolsFromProtobuf = getSymbolsFromProtobufModule(moduleName);
    if (!symbolsFromProtobuf.isEmpty()) {
      modulesInProgress.remove(moduleName);
      return symbolsFromProtobuf;
    }
    Map<String, Symbol> thirdPartySymbols = getModuleSymbols(moduleName, THIRD_PARTY_2AND3, builtinGlobalSymbols);
    if (thirdPartySymbols.isEmpty()) {
      thirdPartySymbols = commonSymbols(getModuleSymbols(moduleName, THIRD_PARTY_2, builtinGlobalSymbols),
        getModuleSymbols(moduleName, THIRD_PARTY_3, builtinGlobalSymbols), moduleName);
    }
    modulesInProgress.remove(moduleName);
    return thirdPartySymbols;
  }

  /**
   * Some special symbols need NOT to be ambiguous for the frontend to work properly.
   * This method sort ambiguous symbol by python version and returns the one which is valid for
   * the most recent Python version.
   */
  static Symbol disambiguateWithLatestPythonSymbol(Set<Symbol> alternatives) {
    int max = Integer.MIN_VALUE;
    Symbol latestPythonSymbol = null;
    for (Symbol alternative : alternatives) {
      int maxPythonVersionForSymbol = ((SymbolImpl) alternative).validForPythonVersions().stream().mapToInt(Integer::parseInt).max().orElse(max);
      if (maxPythonVersionForSymbol > max) {
        max = maxPythonVersionForSymbol;
        latestPythonSymbol = alternative;
      }
    }
    return latestPythonSymbol;
  }

  private static boolean isAmbiguousSymbolOfClasses(Symbol symbol) {
    if (symbol.is(Symbol.Kind.AMBIGUOUS)) {
      return ((AmbiguousSymbol) symbol).alternatives().stream().allMatch(s -> s.is(Symbol.Kind.CLASS));
    }
    return false;
  }

  private static Map<String, Symbol> getSymbolsFromProtobufModule(String moduleName) {
    InputStream resource = TypeShed.class.getResourceAsStream(PROTOBUF + moduleName + ".protobuf");
    if (resource == null) {
      return Collections.emptyMap();
    }
    return getSymbolsFromProtobufModule(deserializedModule(moduleName, resource));
  }

  @CheckForNull
  static ModuleSymbol deserializedModule(String moduleName, InputStream resource) {
    try {
      return ModuleSymbol.parseFrom(resource);
    } catch (IOException e) {
      LOG.debug("Error while deserializing protobuf for module " + moduleName, e);
      return null;
    }
  }

  static Map<String, Symbol> getSymbolsFromProtobufModule(@Nullable ModuleSymbol moduleSymbol) {
    if (moduleSymbol == null) {
      return Collections.emptyMap();
    }

    // TODO: Use a common proxy interface Descriptor instead of using Object
    Map<String, Set<Object>> descriptorsByName = new HashMap<>();
    moduleSymbol.getClassesList().stream()
      .filter(d -> isValidForProjectPythonVersion(d.getValidForList()))
      .forEach(proto -> descriptorsByName.computeIfAbsent(proto.getName(), d -> new HashSet<>()).add(proto));
    moduleSymbol.getFunctionsList().stream()
      .filter(d -> isValidForProjectPythonVersion(d.getValidForList()))
      .forEach(proto -> descriptorsByName.computeIfAbsent(proto.getName(), d -> new HashSet<>()).add(proto));
    moduleSymbol.getOverloadedFunctionsList().stream()
      .filter(d -> isValidForProjectPythonVersion(d.getValidForList()))
      .forEach(proto -> descriptorsByName.computeIfAbsent(proto.getName(), d -> new HashSet<>()).add(proto));
    moduleSymbol.getVarsList().stream()
      .filter(d -> isValidForProjectPythonVersion(d.getValidForList()))
      .forEach(proto -> descriptorsByName.computeIfAbsent(proto.getName(), d -> new HashSet<>()).add(proto));

    Map<String, Symbol> deserializedSymbols = new HashMap<>();

    for (Map.Entry<String, Set<Object>> entry : descriptorsByName.entrySet()) {
      String name = entry.getKey();
      Set<Symbol> symbols = symbolsFromProtobufDescriptors(entry.getValue(), null, moduleSymbol.getFullyQualifiedName());
      Symbol disambiguatedSymbol = disambiguateSymbolsWithSameName(name, symbols, moduleSymbol.getFullyQualifiedName());
      deserializedSymbols.put(name, disambiguatedSymbol);
    }
    return deserializedSymbols;
  }

  private static Symbol disambiguateSymbolsWithSameName(String name, Set<Symbol> symbols, String moduleFqn) {
    if (symbols.size() > 1) {
      if (haveAllTheSameFqn(symbols) && !isBuiltinToDisambiguate(moduleFqn, name)) {
        return AmbiguousSymbolImpl.create(symbols);
      }
      if (!moduleFqn.equals(BUILTINS_FQN)) {
        String fqns = symbols.stream()
          .map(Symbol::fullyQualifiedName)
          .map(fqn -> fqn == null ? "N/A" : fqn)
          .collect(Collectors.joining(","));
        LOG.debug("Symbol " + name + " has conflicting fully qualified names:" + fqns);
        LOG.debug("It has been disambiguated with its latest Python version available symbol.");
      }
      return disambiguateWithLatestPythonSymbol(symbols);
    }
    return symbols.iterator().next();
  }

  private static boolean isBuiltinToDisambiguate(String moduleFqn, String name) {
    return moduleFqn.equals(BUILTINS_FQN) && BUILTINS_TO_DISAMBIGUATE.contains(name);
  }

  private static boolean haveAllTheSameFqn(Set<Symbol> symbols) {
    String firstFqn = symbols.iterator().next().fullyQualifiedName();
    return firstFqn != null && symbols.stream().map(Symbol::fullyQualifiedName).allMatch(firstFqn::equals);
  }

  private static AmbiguousSymbol fromOverloadedFunction(OverloadedFunctionSymbol overloadedFunctionSymbol, @Nullable String containerClassFqn, String moduleName) {
    Set<Symbol> overloadedSymbols = overloadedFunctionSymbol.getDefinitionsList().stream()
      .map(def -> new FunctionSymbolImpl(def, containerClassFqn, overloadedFunctionSymbol.getValidForList(), moduleName))
      .collect(Collectors.toSet());
    return AmbiguousSymbolImpl.create(overloadedSymbols);
  }
}
