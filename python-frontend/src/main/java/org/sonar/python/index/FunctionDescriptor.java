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

import java.util.ArrayList;
import java.util.List;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonar.plugins.python.api.LocationInFile;

public class FunctionDescriptor implements Descriptor {

  private final String name;
  @Nullable
  private final String fullyQualifiedName;
  private final List<Parameter> parameters;
  private final boolean isAsynchronous;
  private final boolean isInstanceMethod;
  private final List<String> decorators;
  private final boolean hasDecorators;
  @Nullable
  private final LocationInFile definitionLocation;
  @Nullable
  private final String annotatedReturnTypeName;

  private FunctionDescriptor(String name, @Nullable String fullyQualifiedName, List<Parameter> parameters, boolean isAsynchronous,
    boolean isInstanceMethod, List<String> decorators, boolean hasDecorators, @Nullable LocationInFile definitionLocation, @Nullable String annotatedReturnTypeName) {

    this.name = name;
    this.fullyQualifiedName = fullyQualifiedName;
    this.parameters = parameters;
    this.isAsynchronous = isAsynchronous;
    this.isInstanceMethod = isInstanceMethod;
    this.decorators = decorators;
    this.hasDecorators = hasDecorators;
    this.definitionLocation = definitionLocation;
    this.annotatedReturnTypeName = annotatedReturnTypeName;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public String fullyQualifiedName() {
    return fullyQualifiedName;
  }

  @Override
  public Kind kind() {
    return Kind.FUNCTION;
  }

  public List<Parameter> parameters() {
    return parameters;
  }

  public boolean isAsynchronous() {
    return isAsynchronous;
  }

  public boolean isInstanceMethod() {
    return isInstanceMethod;
  }

  public List<String> decorators() {
    return decorators;
  }

  public boolean hasDecorators() {
    return hasDecorators;
  }

  @CheckForNull
  public LocationInFile definitionLocation() {
    return definitionLocation;
  }

  @CheckForNull
  public String annotatedReturnTypeName() {
    return annotatedReturnTypeName;
  }

  public static class Parameter  {

    private final String name;
    private final String annotatedType;
    private final boolean hasDefaultValue;
    private final boolean isVariadic;
    private final boolean isKeywordOnly;
    private final boolean isPositionalOnly;
    private final LocationInFile location;

    public Parameter(@Nullable String name, @Nullable String annotatedType, boolean hasDefaultValue,
      boolean isVariadic, boolean isKeywordOnly, boolean isPositionalOnly, @Nullable LocationInFile location) {
      this.name = name;
      this.annotatedType = annotatedType;
      this.hasDefaultValue = hasDefaultValue;
      this.isVariadic = isVariadic;
      this.isKeywordOnly = isKeywordOnly;
      this.isPositionalOnly = isPositionalOnly;
      this.location = location;
    }

    @CheckForNull
    public String name() {
      return name;
    }

    public String annotatedType() {
      return annotatedType;
    }

    public boolean hasDefaultValue() {
      return hasDefaultValue;
    }

    public boolean isVariadic() {
      return isVariadic;
    }

    public boolean isKeywordOnly() {
      return isKeywordOnly;
    }

    public boolean isPositionalOnly() {
      return isPositionalOnly;
    }

    @CheckForNull
    public LocationInFile location() {
      return location;
    }
  }

  public static class FunctionDescriptorBuilder {

    private String name;
    private String fullyQualifiedName;
    private List<Parameter> parameters = new ArrayList<>();
    private boolean isAsynchronous = false;
    private boolean isInstanceMethod = false;
    private List<String> decorators = new ArrayList<>();
    private boolean hasDecorators = false;
    private LocationInFile definitionLocation = null;
    private String annotatedReturnTypeName = null;

    public FunctionDescriptorBuilder withName(String name) {
      this.name = name;
      return this;
    }

    public FunctionDescriptorBuilder withFullyQualifiedName(@Nullable String fullyQualifiedName) {
      this.fullyQualifiedName = fullyQualifiedName;
      return this;
    }

    public FunctionDescriptorBuilder withParameters(List<Parameter> parameters) {
      this.parameters = parameters;
      return this;
    }

    public FunctionDescriptorBuilder withIsAsynchronous(boolean isAsynchronous) {
      this.isAsynchronous = isAsynchronous;
      return this;
    }

    public FunctionDescriptorBuilder withIsInstanceMethod(boolean isInstanceMethod) {
      this.isInstanceMethod = isInstanceMethod;
      return this;
    }

    public FunctionDescriptorBuilder withDecorators(List<String> decorators) {
      this.decorators = decorators;
      return this;
    }

    public FunctionDescriptorBuilder withHasDecorators(boolean hasDecorators) {
      this.hasDecorators = hasDecorators;
      return this;
    }

    public FunctionDescriptorBuilder withDefinitionLocation(@Nullable LocationInFile definitionLocation) {
      this.definitionLocation = definitionLocation;
      return this;
    }

    public FunctionDescriptorBuilder withAnnotatedReturnTypeName(@Nullable String annotatedReturnTypeName) {
      this.annotatedReturnTypeName = annotatedReturnTypeName;
      return this;
    }

    public FunctionDescriptor build() {
      return new FunctionDescriptor(name, fullyQualifiedName, parameters, isAsynchronous, isInstanceMethod, decorators,
        hasDecorators, definitionLocation, annotatedReturnTypeName);
    }
  }
}
