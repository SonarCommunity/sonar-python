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
package org.sonar.python.index;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.sonar.plugins.python.api.symbols.AmbiguousSymbol;
import org.sonar.plugins.python.api.symbols.ClassSymbol;
import org.sonar.plugins.python.api.symbols.FunctionSymbol;
import org.sonar.plugins.python.api.symbols.Symbol;
import org.sonar.python.semantic.AmbiguousSymbolImpl;
import org.sonar.python.semantic.ClassSymbolImpl;
import org.sonar.python.semantic.FunctionSymbolImpl;
import org.sonar.python.semantic.ProjectLevelSymbolTable;
import org.sonar.python.semantic.SymbolImpl;

public class DescriptorUtils {

  private DescriptorUtils() {}

  public static Descriptor descriptor(Symbol symbol) {
    switch (symbol.kind()) {
      case FUNCTION:
        return functionDescriptor(((FunctionSymbol) symbol));
      case CLASS:
        return classDescriptor((ClassSymbol) symbol);
      case AMBIGUOUS:
        return ambiguousDescriptor((AmbiguousSymbol) symbol);
      default:
        return new VariableDescriptor(symbol.name(), symbol.fullyQualifiedName(), symbol.annotatedTypeName());
    }
  }

  private static ClassDescriptor classDescriptor(ClassSymbol classSymbol) {
    ClassDescriptor.ClassDescriptorBuilder classDescriptor = new ClassDescriptor.ClassDescriptorBuilder()
      .withName(classSymbol.name())
      .withFullyQualifiedName(classSymbol.fullyQualifiedName())
      .withMembers(classSymbol.declaredMembers().stream().map(DescriptorUtils::descriptor).collect(Collectors.toList()))
      .withSuperClasses(classSymbol.superClasses().stream().map(Symbol::fullyQualifiedName).filter(Objects::nonNull).collect(Collectors.toList()))
      .withDefinitionLocation(classSymbol.definitionLocation())
      .withHasMetaClass(((ClassSymbolImpl) classSymbol).hasMetaClass())
      .withHasSuperClassWithoutDescriptor(((ClassSymbolImpl) classSymbol).hasSuperClassWithoutSymbol() ||
        // Setting hasSuperClassWithoutDescriptor if a parent has a null FQN as it would be impossible to retrieve it without one, even if the parent exists.
        classSymbol.superClasses().stream().anyMatch(s -> s.fullyQualifiedName() == null))
      .withMetaclassFQN(((ClassSymbolImpl) classSymbol).metaclassFQN())
      .withHasDecorators(classSymbol.hasDecorators())
      .withSupportsGenerics(((ClassSymbolImpl) classSymbol).supportsGenerics());

    return classDescriptor.build();
  }

  private static FunctionDescriptor functionDescriptor(FunctionSymbol functionSymbol) {
    return new FunctionDescriptor.FunctionDescriptorBuilder()
      .withName(functionSymbol.name())
      .withFullyQualifiedName(functionSymbol.fullyQualifiedName())
      .withParameters(parameters(functionSymbol.parameters()))
      .withHasDecorators(functionSymbol.hasDecorators())
      .withDecorators(functionSymbol.decorators())
      .withIsAsynchronous(functionSymbol.isAsynchronous())
      .withIsInstanceMethod(functionSymbol.isInstanceMethod())
      .withAnnotatedReturnTypeName(functionSymbol.annotatedReturnTypeName())
      .withDefinitionLocation(functionSymbol.definitionLocation())
      .build();
  }

  private static AmbiguousDescriptor ambiguousDescriptor(AmbiguousSymbol ambiguousSymbol) {
    return ambiguousDescriptor(ambiguousSymbol, null);
  }

  public static AmbiguousDescriptor ambiguousDescriptor(AmbiguousSymbol ambiguousSymbol, @Nullable String overriddenFQN) {
    String fullyQualifiedName = overriddenFQN != null ? overriddenFQN : ambiguousSymbol.fullyQualifiedName();
    Set<Descriptor> alternatives = ambiguousSymbol.alternatives().stream()
      .map(DescriptorUtils::descriptor)
      .collect(Collectors.toSet());
    return new AmbiguousDescriptor(ambiguousSymbol.name(), fullyQualifiedName, alternatives);
  }

  private static List<FunctionDescriptor.Parameter> parameters(List<FunctionSymbol.Parameter> parameters) {
    return parameters.stream().map(parameter -> new FunctionDescriptor.Parameter(
      parameter.name(),
      ((FunctionSymbolImpl.ParameterImpl) parameter).annotatedTypeName(),
      parameter.hasDefaultValue(),
      parameter.isVariadic(),
      parameter.isKeywordOnly(),
      parameter.isPositionalOnly(),
      parameter.location()
    )).collect(Collectors.toList());
  }

  public static Symbol symbolFromDescriptor(Descriptor descriptor, ProjectLevelSymbolTable projectLevelSymbolTable) {
    return symbolFromDescriptor(descriptor, projectLevelSymbolTable, null);
  }

  public static Symbol symbolFromDescriptor(Descriptor descriptor, ProjectLevelSymbolTable projectLevelSymbolTable, @Nullable String localSymbolName) {
    // The symbol generated from the descriptor will not have the descriptor name if an alias (localSymbolName) is defined
    String symbolName = localSymbolName != null ? localSymbolName : descriptor.name();
    switch (descriptor.kind()) {
      case CLASS:
        return new ClassSymbolImpl((ClassDescriptor) descriptor, projectLevelSymbolTable, symbolName);
      case FUNCTION:
        return new FunctionSymbolImpl((FunctionDescriptor) descriptor, projectLevelSymbolTable, symbolName);
      case VARIABLE:
        return new SymbolImpl(symbolName, descriptor.fullyQualifiedName());
      case AMBIGUOUS:
        Set<Symbol> alternatives = ((AmbiguousDescriptor) descriptor).alternatives().stream()
          .map(a -> DescriptorUtils.symbolFromDescriptor(a, projectLevelSymbolTable, symbolName))
          .collect(Collectors.toSet());
        return new AmbiguousSymbolImpl(symbolName, descriptor.fullyQualifiedName(), alternatives);
      default:
        throw new IllegalStateException(String.format("Error while creating a Symbol from a Descriptor: Unexpected descriptor kind: %s", descriptor.kind()));
    }
  }
}
