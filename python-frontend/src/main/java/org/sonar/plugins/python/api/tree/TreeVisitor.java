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
package org.sonar.plugins.python.api.tree;

import org.sonar.python.tree.DictCompExpressionImpl;

public interface TreeVisitor {

  void visitFileInput(FileInput fileInput);

  void visitStatementList(StatementList statementList);

  void visitIfStatement(IfStatement ifStatement);

  void visitElseClause(ElseClause elseClause);

  void visitExecStatement(ExecStatement execStatement);

  void visitAssertStatement(AssertStatement assertStatement);

  void visitDelStatement(DelStatement delStatement);

  void visitPassStatement(PassStatement passStatement);

  void visitPrintStatement(PrintStatement printStatement);

  void visitReturnStatement(ReturnStatement returnStatement);

  void visitYieldStatement(YieldStatement yieldStatement);

  void visitYieldExpression(YieldExpression yieldExpression);

  void visitRaiseStatement(RaiseStatement raiseStatement);

  void visitBreakStatement(BreakStatement breakStatement);

  void visitContinueStatement(ContinueStatement continueStatement);

  void visitFunctionDef(FunctionDef functionDef);

  void visitName(Name name);

  void visitClassDef(ClassDef classDef);

  void visitAliasedName(AliasedName aliasedName);

  void visitDottedName(DottedName dottedName);

  void visitImportFrom(ImportFrom importFrom);

  void visitImportName(ImportName importName);

  void visitForStatement(ForStatement forStatement);

  void visitGlobalStatement(GlobalStatement globalStatement);

  void visitNonlocalStatement(NonlocalStatement nonlocalStatement);

  void visitWhileStatement(WhileStatement whileStatement);

  void visitExpressionStatement(ExpressionStatement expressionStatement);

  void visitTryStatement(TryStatement tryStatement);

  void visitFinallyClause(FinallyClause finallyClause);

  void visitExceptClause(ExceptClause exceptClause);

  void visitWithStatement(WithStatement withStatement);

  void visitWithItem(WithItem withItem);

  void visitQualifiedExpression(QualifiedExpression qualifiedExpression);

  void visitCallExpression(CallExpression callExpression);

  void visitRegularArgument(RegularArgument regularArgument);

  void visitAssignmentStatement(AssignmentStatement assignmentStatement);

  void visitAssignmentExpression(AssignmentExpression assignmentExpression);

  void visitExpressionList(ExpressionList expressionList);

  void visitBinaryExpression(BinaryExpression binaryExpression);

  void visitLambda(LambdaExpression lambdaExpression);

  void visitArgumentList(ArgList argList);

  void visitParameterList(ParameterList parameterList);

  void visitTupleParameter(TupleParameter tupleParameter);

  void visitParameter(Parameter parameter);

  void visitTypeAnnotation(TypeAnnotation typeAnnotation);

  void visitNumericLiteral(NumericLiteral numericLiteral);

  void visitListLiteral(ListLiteral listLiteral);

  void visitUnaryExpression(UnaryExpression unaryExpression);

  void visitStringLiteral(StringLiteral stringLiteral);

  void visitStringElement(StringElement stringElement);

  void visitUnpackingExpression(UnpackingExpression unpackingExpression);

  void visitAwaitExpression(AwaitExpression awaitExpression);

  void visitSliceExpression(SliceExpression sliceExpression);

  void visitSliceList(SliceList sliceList);

  void visitSliceItem(SliceItem sliceItem);

  void visitSubscriptionExpression(SubscriptionExpression subscriptionExpression);

  void visitParenthesizedExpression(ParenthesizedExpression parenthesizedExpression);

  void visitTuple(Tuple tuple);

  void visitConditionalExpression(ConditionalExpression conditionalExpression);

  void visitPyListOrSetCompExpression(ComprehensionExpression comprehensionExpression);

  void visitComprehensionFor(ComprehensionFor comprehensionFor);

  void visitComprehensionIf(ComprehensionIf comprehensionIf);

  void visitDictionaryLiteral(DictionaryLiteral dictionaryLiteral);

  void visitSetLiteral(SetLiteral setLiteral);

  void visitKeyValuePair(KeyValuePair keyValuePair);

  void visitDictCompExpression(DictCompExpressionImpl dictCompExpression);

  void visitCompoundAssignment(CompoundAssignmentStatement compoundAssignmentStatement);

  void visitAnnotatedAssignment(AnnotatedAssignment annotatedAssignment);

  void visitNone(NoneExpression noneExpression);

  void visitRepr(ReprExpression reprExpression);

  void visitEllipsis(EllipsisExpression ellipsisExpression);

  void visitDecorator(Decorator decorator);

  void visitToken(Token token);

  void visitFormattedExpression(FormattedExpression formattedExpression);

  void visitFormatSpecifier(FormatSpecifier formatSpecifier);
}
