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
package org.sonar.python.tree;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.sonar.plugins.python.api.symbols.AmbiguousSymbol;
import org.sonar.plugins.python.api.symbols.ClassSymbol;
import org.sonar.plugins.python.api.symbols.Symbol;
import org.sonar.plugins.python.api.tree.ArgList;
import org.sonar.plugins.python.api.tree.Argument;
import org.sonar.plugins.python.api.tree.CallExpression;
import org.sonar.plugins.python.api.tree.Expression;
import org.sonar.plugins.python.api.tree.QualifiedExpression;
import org.sonar.plugins.python.api.tree.SubscriptionExpression;
import org.sonar.plugins.python.api.tree.Token;
import org.sonar.plugins.python.api.tree.Tree;
import org.sonar.plugins.python.api.tree.TreeVisitor;
import org.sonar.plugins.python.api.types.InferredType;
import org.sonar.python.semantic.ClassSymbolImpl;
import org.sonar.python.semantic.FunctionSymbolImpl;
import org.sonar.python.types.DeclaredType;
import org.sonar.python.types.HasTypeDependencies;
import org.sonar.python.types.InferredTypes;
import org.sonar.python.types.v2.ClassType;
import org.sonar.python.types.v2.FunctionType;
import org.sonar.python.types.v2.ObjectType;
import org.sonar.python.types.v2.PythonType;
import org.sonar.python.types.v2.TypeSource;
import org.sonar.python.types.v2.UnionType;

import static org.sonar.plugins.python.api.symbols.Symbol.Kind.CLASS;
import static org.sonar.plugins.python.api.tree.Tree.Kind.SUBSCRIPTION;
import static org.sonar.python.tree.TreeUtils.getSymbolFromTree;

public class CallExpressionImpl extends PyTree implements CallExpression, HasTypeDependencies {
  private final Expression callee;
  private final ArgList argumentList;
  private final Token leftPar;
  private final Token rightPar;

  public CallExpressionImpl(Expression callee, @Nullable ArgList argumentList, Token leftPar, Token rightPar) {
    this.callee = callee;
    this.argumentList = argumentList;
    this.leftPar = leftPar;
    this.rightPar = rightPar;
  }

  @Override
  public Expression callee() {
    return callee;
  }

  @Override
  public ArgList argumentList() {
    return argumentList;
  }

  @Override
  public List<Argument> arguments() {
    return argumentList != null ? argumentList.arguments() : Collections.emptyList();
  }

  @Override
  public Token leftPar() {
    return leftPar;
  }

  @Override
  public Token rightPar() {
    return rightPar;
  }

  @Override
  public Kind getKind() {
    return Kind.CALL_EXPR;
  }

  @Override
  public void accept(TreeVisitor visitor) {
    visitor.visitCallExpression(this);
  }

  @Override
  public List<Tree> computeChildren() {
    return Stream.of(callee, leftPar, argumentList, rightPar).filter(Objects::nonNull).toList();
  }

  @Override
  public InferredType type() {
    Symbol calleeSymbol = calleeSymbol();
    if (calleeSymbol != null) {
      InferredType type = getType(calleeSymbol);
      if (type.equals(InferredTypes.anyType()) && callee.is(Kind.QUALIFIED_EXPR)) {
        return getDeclaredType(callee);
      }
      return type;
    }
    if (callee.is(SUBSCRIPTION)) {
      return getSymbolFromTree(((SubscriptionExpression) callee).object())
        .filter(CallExpressionImpl::supportsGenerics)
        .map(InferredTypes::runtimeType)
        .orElse(InferredTypes.anyType());
    }
    return InferredTypes.anyType();
  }

  private static boolean supportsGenerics(Symbol symbol) {
    switch (symbol.kind()) {
      case CLASS:
        return ((ClassSymbolImpl) symbol).supportsGenerics();
      case AMBIGUOUS:
        return ((AmbiguousSymbol) symbol).alternatives().stream().allMatch(CallExpressionImpl::supportsGenerics);
      default:
        return false;
    }
  }

  private static InferredType getDeclaredType(Expression callee) {
    QualifiedExpression qualifiedCallee = (QualifiedExpression) callee;
    InferredType qualifierType = qualifiedCallee.qualifier().type();
    if (qualifierType instanceof DeclaredType declaredType) {
      Set<Optional<Symbol>> resolvedMembers = declaredType.alternativeTypeSymbols().stream()
        .filter(s -> s.is(CLASS))
        .map(ClassSymbol.class::cast)
        .map(t -> t.resolveMember(qualifiedCallee.name().name()))
        .filter(Optional::isPresent)
        .collect(Collectors.toSet());

      if (resolvedMembers.size() == 1) {
        return resolvedMembers.iterator().next()
          .map(CallExpressionImpl::getType)
          .map(DeclaredType::fromInferredType)
          .orElse(InferredTypes.anyType());
      }
    }
    return InferredTypes.anyType();
  }

  private static InferredType getType(Symbol symbol) {
    if (symbol.is(CLASS)) {
      ClassSymbol classSymbol = (ClassSymbol) symbol;
      if ("typing.NamedTuple".equals(classSymbol.fullyQualifiedName())) {
        // Calling typing.NamedTuple actually returns a "type" object
        return InferredTypes.TYPE;
      }
      return InferredTypes.runtimeType(classSymbol);
    }
    if (symbol.is(Symbol.Kind.FUNCTION)) {
      FunctionSymbolImpl functionSymbol = (FunctionSymbolImpl) symbol;
      return functionSymbol.declaredReturnType();
    }
    if (symbol.is(Symbol.Kind.AMBIGUOUS)) {
      Collection<Symbol> alternatives = ((AmbiguousSymbol) symbol).alternatives();
      return InferredTypes.union(alternatives.stream().map(CallExpressionImpl::getType));
    }
    return InferredTypes.anyOrUnknownClassType(symbol);
  }

  @Override
  public List<Expression> typeDependencies() {
    return Collections.singletonList(callee);
  }

  @Override
  public PythonType typeV2() {
    TypeSource typeSource = computeTypeSource();
    PythonType pythonType = returnTypeOfCall(callee().typeV2());
    return pythonType != PythonType.UNKNOWN ? new ObjectType(pythonType, typeSource) : PythonType.UNKNOWN;
  }

  static PythonType returnTypeOfCall(PythonType calleeType) {
    if (calleeType instanceof ClassType classType) {
      return classType;
    }
    if (calleeType instanceof FunctionType functionType) {
      PythonType returnType = functionType.returnType();
      if (returnType.equals(PythonType.UNKNOWN)) {
        return PythonType.UNKNOWN;
      }
      return returnType;
    }
    if (calleeType instanceof UnionType unionType) {
      Set<PythonType> types = new HashSet<>();
      for (PythonType candidate : unionType.candidates()) {
        if (candidate instanceof ClassType classType) {
          types.add(classType);
        } else if (candidate instanceof FunctionType functionType) {
          types.add(functionType.returnType());
        } else {
          return PythonType.UNKNOWN;
        }
      }
      return UnionType.or(types);
    }
    if (calleeType instanceof ObjectType objectType) {
      Optional<PythonType> pythonType = objectType.resolveMember("__call__");
      return pythonType.map(CallExpressionImpl::returnTypeOfCall).orElse(PythonType.UNKNOWN);
    }
    return PythonType.UNKNOWN;
  }

  TypeSource computeTypeSource() {
    if (callee() instanceof QualifiedExpression qualifiedExpression) {
      return qualifiedExpression.qualifier().typeV2().typeSource();
    }
    return callee().typeV2().typeSource();
  }
}
