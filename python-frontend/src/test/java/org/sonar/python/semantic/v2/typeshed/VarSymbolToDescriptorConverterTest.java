/*
 * SonarQube Python Plugin
 * Copyright (C) 2011-2024 SonarSource SA
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
package org.sonar.python.semantic.v2.typeshed;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.sonar.python.index.VariableDescriptor;
import org.sonar.python.types.protobuf.SymbolsProtos;

class VarSymbolToDescriptorConverterTest {

  @Test
  void test() {
    var symbol = SymbolsProtos.VarSymbol.newBuilder()
      .setName("something")
      .setFullyQualifiedName("module.something")
      .setTypeAnnotation(SymbolsProtos.Type.newBuilder()
        .setFullyQualifiedName("module.something_else")
        .build())
      .build();
    var converter = new VarSymbolToDescriptorConverter();

    var descriptor = (VariableDescriptor) converter.convert(symbol);
    Assertions.assertThat(descriptor.name()).isEqualTo("something");
    Assertions.assertThat(descriptor.fullyQualifiedName()).isEqualTo("module.something");
    Assertions.assertThat(descriptor.annotatedType()).isEqualTo("module.something_else");
  }

  @Test
  void builtinVarTest() {
    var symbol = SymbolsProtos.VarSymbol.newBuilder()
      .setName("int")
      .setFullyQualifiedName("builtins.int")
      .setTypeAnnotation(SymbolsProtos.Type.newBuilder()
        .setFullyQualifiedName("builtins.int")
        .build())
      .build();
    var converter = new VarSymbolToDescriptorConverter();

    var descriptor = (VariableDescriptor) converter.convert(symbol);
    Assertions.assertThat(descriptor.name()).isEqualTo("int");
    Assertions.assertThat(descriptor.fullyQualifiedName()).isEqualTo("int");
    Assertions.assertThat(descriptor.annotatedType()).isEqualTo("int");
  }

  @Test
  void test_typed_dict_exception() {
    var converter = new VarSymbolToDescriptorConverter();
    var symbol = SymbolsProtos.VarSymbol.newBuilder()
      .setName("TypedDict")
      .setFullyQualifiedName("typing.TypedDict")
      .setTypeAnnotation(SymbolsProtos.Type.newBuilder()
        .setFullyQualifiedName("something")
        .build())
      .build();

    var descriptor = (VariableDescriptor) converter.convert(symbol);
    Assertions.assertThat(descriptor.name()).isEqualTo("TypedDict");
    Assertions.assertThat(descriptor.fullyQualifiedName()).isEqualTo("typing.TypedDict");
    Assertions.assertThat(descriptor.annotatedType()).isNull();

    symbol = SymbolsProtos.VarSymbol.newBuilder()
      .setName("TypedDict")
      .setFullyQualifiedName("typing_extensions.TypedDict")
      .setTypeAnnotation(SymbolsProtos.Type.newBuilder()
        .setFullyQualifiedName("something")
        .build())
      .build();

    descriptor = (VariableDescriptor) converter.convert(symbol);
    Assertions.assertThat(descriptor.name()).isEqualTo("TypedDict");
    Assertions.assertThat(descriptor.fullyQualifiedName()).isEqualTo("typing_extensions.TypedDict");
    Assertions.assertThat(descriptor.annotatedType()).isNull();

    symbol = SymbolsProtos.VarSymbol.newBuilder()
      .setName("TypedDict")
      .setFullyQualifiedName("unrelated.TypedDict")
      .setTypeAnnotation(SymbolsProtos.Type.newBuilder()
        .setFullyQualifiedName("something")
        .build())
      .build();

    descriptor = (VariableDescriptor) converter.convert(symbol);
    Assertions.assertThat(descriptor.name()).isEqualTo("TypedDict");
    Assertions.assertThat(descriptor.fullyQualifiedName()).isEqualTo("unrelated.TypedDict");
    Assertions.assertThat(descriptor.annotatedType()).isEqualTo("something");
  }

}