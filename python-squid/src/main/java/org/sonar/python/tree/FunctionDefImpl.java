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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonar.python.api.tree.Decorator;
import org.sonar.python.api.tree.FunctionDef;
import org.sonar.python.api.tree.Name;
import org.sonar.python.api.tree.ParameterList;
import org.sonar.python.api.tree.StatementList;
import org.sonar.python.api.tree.Token;
import org.sonar.python.api.tree.Tree;
import org.sonar.python.api.tree.TreeVisitor;
import org.sonar.python.api.tree.TypeAnnotation;
import org.sonar.python.semantic.Symbol;

public class FunctionDefImpl extends PyTree implements FunctionDef {

  private final List<Decorator> decorators;
  private final Token asyncKeyword;
  private final Token defKeyword;
  private final Name name;
  private final Token leftPar;
  private final ParameterList parameters;
  private final Token rightPar;
  private final TypeAnnotation returnType;
  private final Token colon;
  private final Token newLine;
  private final Token indent;
  private final StatementList body;
  private final Token dedent;

  private final boolean isMethodDefinition;
  private final Token docstring;
  private Set<Symbol> symbols = new HashSet<>();

  public FunctionDefImpl(List<Decorator> decorators, @Nullable Token asyncKeyword, Token defKeyword, Name name,
                         Token leftPar, @Nullable ParameterList parameters, Token rightPar, @Nullable TypeAnnotation returnType,
                         Token colon, @Nullable Token newLine, @Nullable Token indent, StatementList body, @Nullable Token dedent,
                         boolean isMethodDefinition, @Nullable Token docstring) {
    this.decorators = decorators;
    this.asyncKeyword = asyncKeyword;
    this.defKeyword = defKeyword;
    this.name = name;
    this.leftPar = leftPar;
    this.parameters = parameters;
    this.rightPar = rightPar;
    this.returnType = returnType;
    this.colon = colon;
    this.newLine = newLine;
    this.indent = indent;
    this.body = body;
    this.dedent = dedent;
    this.isMethodDefinition = isMethodDefinition;
    this.docstring = docstring;
  }

  @Override
  public List<Decorator> decorators() {
    return decorators;
  }

  @Override
  public Token defKeyword() {
    return defKeyword;
  }

  @CheckForNull
  @Override
  public Token asyncKeyword() {
    return asyncKeyword;
  }

  @Override
  public Name name() {
    return name;
  }

  @Override
  public Token leftPar() {
    return leftPar;
  }

  @CheckForNull
  @Override
  public ParameterList parameters() {
    return parameters;
  }

  @Override
  public Token rightPar() {
    return rightPar;
  }

  @CheckForNull
  @Override
  public TypeAnnotation returnTypeAnnotation() {
    return returnType;
  }

  @Override
  public Token colon() {
    return colon;
  }

  @Override
  public StatementList body() {
    return body;
  }

  @Override
  public boolean isMethodDefinition() {
    return isMethodDefinition;
  }

  @CheckForNull
  @Override
  public Token docstring() {
    return docstring;
  }

  @Override
  public Set<Symbol> localVariables() {
    return symbols;
  }

  public void addLocalVariableSymbol(Symbol symbol) {
    symbols.add(symbol);
  }

  @Override
  public Kind getKind() {
    return Kind.FUNCDEF;
  }

  @Override
  public void accept(TreeVisitor visitor) {
    visitor.visitFunctionDef(this);
  }

  @Override
  public List<Tree> childs() {
    return Stream.of(decorators, Arrays.asList(asyncKeyword, defKeyword, name, leftPar, parameters, rightPar, returnType, colon, newLine, indent, docstring, body, dedent))
      .flatMap(List::stream).filter(Objects::nonNull).collect(Collectors.toList());
  }
}
