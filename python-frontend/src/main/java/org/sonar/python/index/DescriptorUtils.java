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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.sonar.plugins.python.api.symbols.AmbiguousSymbol;
import org.sonar.plugins.python.api.symbols.ClassSymbol;
import org.sonar.plugins.python.api.symbols.FunctionSymbol;
import org.sonar.plugins.python.api.symbols.Symbol;
import org.sonar.plugins.python.api.types.InferredType;
import org.sonar.python.semantic.AmbiguousSymbolImpl;
import org.sonar.python.semantic.ClassSymbolImpl;
import org.sonar.python.semantic.FunctionSymbolImpl;
import org.sonar.python.semantic.ProjectLevelSymbolTable;
import org.sonar.python.semantic.SymbolImpl;
import org.sonar.python.types.DeclaredType;
import org.sonar.python.types.InferredTypes;

import static org.sonar.python.semantic.SymbolUtils.typeshedSymbolWithFQN;
import static org.sonar.python.types.InferredTypes.anyType;

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
      parameter.isKeywordOnly(),
      parameter.isPositionalOnly(),
      parameter.isPositionalVariadic(),
      parameter.isKeywordVariadic(),
      parameter.location()
    )).collect(Collectors.toList());
  }

  public static Symbol symbolFromDescriptor(Descriptor descriptor, ProjectLevelSymbolTable projectLevelSymbolTable,
                                            @Nullable String localSymbolName, Map<String, Symbol> createdSymbols) {
    // The symbol generated from the descriptor will not have the descriptor name if an alias (localSymbolName) is defined
    if (createdSymbols.containsKey(descriptor.fullyQualifiedName())) {
      return createdSymbols.get(descriptor.fullyQualifiedName());
    }
    String symbolName = localSymbolName != null ? localSymbolName : descriptor.name();
    switch (descriptor.kind()) {
      case CLASS:
        return createClassSymbol(descriptor, projectLevelSymbolTable, createdSymbols, symbolName);
      case FUNCTION:
        return createFunctionSymbol((FunctionDescriptor) descriptor, projectLevelSymbolTable, createdSymbols, symbolName);
      case VARIABLE:
        return new SymbolImpl(symbolName, descriptor.fullyQualifiedName());
      case AMBIGUOUS:
        Set<Symbol> alternatives = ((AmbiguousDescriptor) descriptor).alternatives().stream()
          .map(a -> DescriptorUtils.symbolFromDescriptor(a, projectLevelSymbolTable, symbolName, createdSymbols))
          .collect(Collectors.toSet());
        return new AmbiguousSymbolImpl(symbolName, descriptor.fullyQualifiedName(), alternatives);
      default:
        throw new IllegalStateException(String.format("Error while creating a Symbol from a Descriptor: Unexpected descriptor kind: %s", descriptor.kind()));
    }
  }

  private static ClassSymbolImpl createClassSymbol(Descriptor descriptor, ProjectLevelSymbolTable projectLevelSymbolTable, Map<String, Symbol> createdSymbols, String symbolName) {
    ClassDescriptor classDescriptor = (ClassDescriptor) descriptor;
    ClassSymbolImpl classSymbol = new ClassSymbolImpl((ClassDescriptor) descriptor, symbolName);
    createdSymbols.put(descriptor.fullyQualifiedName(), classSymbol);
    addSuperClasses(classSymbol, classDescriptor, projectLevelSymbolTable, createdSymbols);
    addMembers(classSymbol, classDescriptor, projectLevelSymbolTable, createdSymbols);
    return classSymbol;
  }

  private static void addMembers(ClassSymbolImpl classSymbol, ClassDescriptor classDescriptor,
                                 ProjectLevelSymbolTable projectLevelSymbolTable, Map<String, Symbol> createdSymbols) {
    classSymbol.addMembers(classDescriptor.members().stream()
      .map(memberFqn -> DescriptorUtils.symbolFromDescriptor(memberFqn, projectLevelSymbolTable, null, createdSymbols))
      .map(member -> {
        if (member instanceof FunctionSymbolImpl) {
          ((FunctionSymbolImpl) member).setOwner(classSymbol);
        }
        return member;
      })
      .collect(Collectors.toList()));
  }

  private static void addSuperClasses(ClassSymbolImpl classSymbol, ClassDescriptor classDescriptor,
                                      ProjectLevelSymbolTable projectLevelSymbolTable, Map<String, Symbol> createdSymbols) {
    classDescriptor.superClasses().stream()
      .map(superClassFqn -> {
          if (createdSymbols.containsKey(superClassFqn)) {
            return createdSymbols.get(superClassFqn);
          }
          Symbol symbol = projectLevelSymbolTable.getSymbol(superClassFqn, null, createdSymbols);
          symbol = symbol != null ? symbol : typeshedSymbolWithFQN(superClassFqn);
          createdSymbols.put(superClassFqn, symbol);
          return symbol;
        }
      )
      .forEach(classSymbol::addSuperClass);
  }

  private static FunctionSymbolImpl createFunctionSymbol(FunctionDescriptor functionDescriptor, ProjectLevelSymbolTable projectLevelSymbolTable,
                                                         Map<String, Symbol> createdSymbols, String symbolName) {
    FunctionSymbolImpl functionSymbol = new FunctionSymbolImpl(functionDescriptor, symbolName);
    addParameters(functionSymbol, functionDescriptor, projectLevelSymbolTable, createdSymbols);
    return functionSymbol;
  }

  private static void addParameters(FunctionSymbolImpl functionSymbol, FunctionDescriptor functionDescriptor,
                                    ProjectLevelSymbolTable projectLevelSymbolTable, Map<String, Symbol> createdSymbols) {
    functionDescriptor.parameters().stream().map(parameterDescriptor -> {
      FunctionSymbolImpl.ParameterImpl parameter = new FunctionSymbolImpl.ParameterImpl(parameterDescriptor);
      setParameterType(parameter, parameterDescriptor.annotatedType(), projectLevelSymbolTable, createdSymbols);
      return parameter;
    }).forEach(functionSymbol::addParameter);
  }

  private static void setParameterType(FunctionSymbolImpl.ParameterImpl parameter, String annotatedType,
                                       ProjectLevelSymbolTable projectLevelSymbolTable, Map<String, Symbol> createdSymbols) {
    InferredType declaredType;
    if (parameter.isKeywordVariadic()) {
      declaredType = InferredTypes.DICT;
    } else if (parameter.isPositionalVariadic()) {
      declaredType = InferredTypes.TUPLE;
    } else {
      Symbol existingSymbol = createdSymbols.get(annotatedType);
      Symbol typeSymbol = existingSymbol != null ? existingSymbol : projectLevelSymbolTable.getSymbol(annotatedType, null, createdSymbols);
      String annotatedTypeName = parameter.annotatedTypeName();
      if (typeSymbol == null && annotatedTypeName != null) {
        typeSymbol = typeshedSymbolWithFQN(annotatedTypeName);
      }
      declaredType = typeSymbol == null ? anyType() : new DeclaredType(typeSymbol, Collections.emptyList());
    }
    parameter.setDeclaredType(declaredType);
  }
}
