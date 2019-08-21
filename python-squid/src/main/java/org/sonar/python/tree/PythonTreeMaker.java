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

import com.sonar.sslr.api.AstNode;
import com.sonar.sslr.api.GenericTokenType;
import com.sonar.sslr.api.Token;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.sonar.python.api.PythonGrammar;
import org.sonar.python.api.PythonKeyword;
import org.sonar.python.api.tree.PyArgListTree;
import org.sonar.python.api.PythonPunctuator;
import org.sonar.python.api.tree.PyAliasedNameTree;
import org.sonar.python.api.tree.PyAssertStatementTree;
import org.sonar.python.api.tree.PyBreakStatementTree;
import org.sonar.python.api.tree.PyClassDefTree;
import org.sonar.python.api.tree.PyContinueStatementTree;
import org.sonar.python.api.tree.PyDelStatementTree;
import org.sonar.python.api.tree.PyDottedNameTree;
import org.sonar.python.api.tree.PyElseStatementTree;
import org.sonar.python.api.tree.PyExecStatementTree;
import org.sonar.python.api.tree.PyExpressionTree;
import org.sonar.python.api.tree.PyFileInputTree;
import org.sonar.python.api.tree.PyFunctionDefTree;
import org.sonar.python.api.tree.PyIfStatementTree;
import org.sonar.python.api.tree.PyImportFromTree;
import org.sonar.python.api.tree.PyImportNameTree;
import org.sonar.python.api.tree.PyImportStatementTree;
import org.sonar.python.api.tree.PyNameTree;
import org.sonar.python.api.tree.PyPassStatementTree;
import org.sonar.python.api.tree.PyPrintStatementTree;
import org.sonar.python.api.tree.PyRaiseStatementTree;
import org.sonar.python.api.tree.PyReturnStatementTree;
import org.sonar.python.api.tree.PyStatementTree;
import org.sonar.python.api.tree.PyTypedArgListTree;
import org.sonar.python.api.tree.PyYieldExpressionTree;
import org.sonar.python.api.tree.PyYieldStatementTree;

public class PythonTreeMaker {

  public PyFileInputTree fileInput(AstNode astNode) {
    List<PyStatementTree> statements = getStatements(astNode).stream().map(this::statement).collect(Collectors.toList());
    return new PyFileInputTreeImpl(astNode, statements);
  }

  private PyStatementTree statement(AstNode astNode) {
    if (astNode.is(PythonGrammar.IF_STMT)) {
      return ifStatement(astNode);
    }
    if (astNode.is(PythonGrammar.PASS_STMT)) {
      return passStatement(astNode);
    }
    if (astNode.is(PythonGrammar.PRINT_STMT)) {
      return printStatement(astNode);
    }
    if (astNode.is(PythonGrammar.EXEC_STMT)) {
      return execStatement(astNode);
    }
    if (astNode.is(PythonGrammar.ASSERT_STMT)) {
      return assertStatement(astNode);
    }
    if (astNode.is(PythonGrammar.PASS_STMT)) {
      return passStatement(astNode);
    }
    if (astNode.is(PythonGrammar.DEL_STMT)) {
      return delStatement(astNode);
    }
    if (astNode.is(PythonGrammar.RETURN_STMT)) {
      return returnStatement(astNode);
    }
    if (astNode.is(PythonGrammar.YIELD_STMT)) {
      return yieldStatement(astNode);
    }
    if (astNode.is(PythonGrammar.RAISE_STMT)) {
      return raiseStatement(astNode);
    }
    if (astNode.is(PythonGrammar.BREAK_STMT)) {
      return breakStatement(astNode);
    }
    if (astNode.is(PythonGrammar.CONTINUE_STMT)) {
      return continueStatement(astNode);
    }
    if (astNode.is(PythonGrammar.FUNCDEF)) {
      return funcDefStatement(astNode);
    }
    if (astNode.is(PythonGrammar.CLASSDEF)) {
      return classDefStatement(astNode);
    }
    if (astNode.is(PythonGrammar.IMPORT_STMT)) {
      return importStatement(astNode);
    }
    // throw new IllegalStateException("Statement not translated to strongly typed AST");
    return null;
  }

  private List<PyStatementTree> getStatementsFromSuite(AstNode astNode) {
    if (astNode.is(PythonGrammar.SUITE)) {
      List<AstNode> statements = getStatements(astNode);
      if (statements.isEmpty()) {
        AstNode stmtListNode = astNode.getFirstChild(PythonGrammar.STMT_LIST);
        return stmtListNode.getChildren(PythonGrammar.SIMPLE_STMT).stream()
          .map(AstNode::getFirstChild)
          .map(this::statement)
          .collect(Collectors.toList());
      }
      return statements.stream().map(this::statement)
        .collect(Collectors.toList());
    }
    return Collections.emptyList();
  }

  private List<AstNode> getStatements(AstNode astNode) {
    List<AstNode> statements = astNode.getChildren(PythonGrammar.STATEMENT);
    return statements.stream().flatMap(stmt -> {
      if (stmt.hasDirectChildren(PythonGrammar.STMT_LIST)) {
        AstNode stmtListNode = stmt.getFirstChild(PythonGrammar.STMT_LIST);
        return stmtListNode.getChildren(PythonGrammar.SIMPLE_STMT).stream()
          .map(AstNode::getFirstChild);
      }
      return stmt.getChildren(PythonGrammar.COMPOUND_STMT).stream()
        .map(AstNode::getFirstChild);
    }).collect(Collectors.toList());
  }

  // Simple statements

  public PyPrintStatementTree printStatement(AstNode astNode) {
    List<PyExpressionTree> expressions = astNode.getChildren(PythonGrammar.TEST).stream().map(this::expression).collect(Collectors.toList());
    return new PyPrintStatementTreeImpl(astNode, astNode.getTokens().get(0), expressions);
  }

  public PyExecStatementTree execStatement(AstNode astNode) {
    PyExpressionTree expression = expression(astNode.getFirstChild(PythonGrammar.EXPR));
    List<PyExpressionTree> expressions = astNode.getChildren(PythonGrammar.TEST).stream().map(this::expression).collect(Collectors.toList());
    if (expressions.isEmpty()) {
      return new PyExecStatementTreeImpl(astNode, astNode.getTokens().get(0), expression);
    }
    return new PyExecStatementTreeImpl(astNode, astNode.getTokens().get(0), expression, expressions.get(0), expressions.size() == 2 ? expressions.get(1) : null);
  }

  public PyAssertStatementTree assertStatement(AstNode astNode) {
    List<PyExpressionTree> expressions = astNode.getChildren(PythonGrammar.TEST).stream().map(this::expression).collect(Collectors.toList());
    return new PyAssertStatementTreeImpl(astNode, astNode.getTokens().get(0), expressions);
  }

  public PyPassStatementTree passStatement(AstNode astNode) {
    return new PyPassStatementTreeImpl(astNode, astNode.getTokens().get(0));
  }

  public PyDelStatementTree delStatement(AstNode astNode) {
    AstNode exprListNode = astNode.getFirstChild(PythonGrammar.EXPRLIST);
    List<PyExpressionTree> expressionTrees = exprListNode.getChildren(PythonGrammar.EXPR, PythonGrammar.STAR_EXPR).stream()
      .map(this::expression)
      .collect(Collectors.toList());
    return new PyDelStatementTreeImpl(astNode, astNode.getTokens().get(0), expressionTrees);
  }

  public PyReturnStatementTree returnStatement(AstNode astNode) {
    AstNode testListNode = astNode.getFirstChild(PythonGrammar.TESTLIST);
    List<PyExpressionTree> expressionTrees = Collections.emptyList();
    if (testListNode != null) {
      expressionTrees = testListNode.getChildren(PythonGrammar.TEST).stream()
        .map(this::expression)
        .collect(Collectors.toList());
    }
    return new PyReturnStatementTreeImpl(astNode, astNode.getTokens().get(0), expressionTrees);
  }

  public PyYieldStatementTree yieldStatement(AstNode astNode) {
    return new PyYieldStatementTreeImpl(astNode, yieldExpression(astNode.getFirstChild(PythonGrammar.YIELD_EXPR)));
  }

  public PyYieldExpressionTree yieldExpression(AstNode astNode) {
    Token yieldKeyword = astNode.getFirstChild(PythonKeyword.YIELD).getToken();
    AstNode nodeContainingExpression = astNode;
    AstNode fromKeyword = astNode.getFirstChild(PythonKeyword.FROM);
    if (fromKeyword == null) {
      nodeContainingExpression = astNode.getFirstChild(PythonGrammar.TESTLIST);
    }
    List<PyExpressionTree> expressionTrees = Collections.emptyList();
    if (nodeContainingExpression != null) {
      expressionTrees = nodeContainingExpression.getChildren(PythonGrammar.TEST).stream()
        .map(this::expression)
        .collect(Collectors.toList());
    }
    return new PyYieldExpressionTreeImpl(astNode, yieldKeyword, fromKeyword == null ? null : fromKeyword.getToken(), expressionTrees);
  }

  public PyRaiseStatementTree raiseStatement(AstNode astNode) {
    AstNode fromKeyword = astNode.getFirstChild(PythonKeyword.FROM);
    List<AstNode> expressions = new ArrayList<>();
    AstNode fromExpression = null;
    if (fromKeyword != null) {
      expressions.add(astNode.getFirstChild(PythonGrammar.TEST));
      fromExpression = astNode.getLastChild(PythonGrammar.TEST);
    } else {
      expressions = astNode.getChildren(PythonGrammar.TEST);
    }
    List<PyExpressionTree> expressionTrees = expressions.stream()
      .map(this::expression)
      .collect(Collectors.toList());
    return new PyRaiseStatementTreeImpl(astNode, astNode.getFirstChild(PythonKeyword.RAISE).getToken(),
      expressionTrees, fromKeyword == null ? null : fromKeyword.getToken(), fromExpression == null ? null : expression(fromExpression));
  }

  public PyBreakStatementTree breakStatement(AstNode astNode) {
    return new PyBreakStatementTreeImpl(astNode, astNode.getToken());
  }

  public PyContinueStatementTree continueStatement(AstNode astNode) {
    return new PyContinueStatementTreeImpl(astNode, astNode.getToken());
  }

  public PyImportStatementTree importStatement(AstNode astNode) {
    AstNode importStmt = astNode.getFirstChild();
    if (importStmt.is(PythonGrammar.IMPORT_NAME)) {
      return importName(importStmt);
    }
    return importFromStatement(importStmt);
  }

  private PyImportNameTree importName(AstNode astNode) {
    Token importKeyword = astNode.getFirstChild(PythonKeyword.IMPORT).getToken();
    List<PyAliasedNameTree> aliasedNames = astNode
      .getFirstChild(PythonGrammar.DOTTED_AS_NAMES)
      .getChildren(PythonGrammar.DOTTED_AS_NAME).stream()
      .map(this::aliasedName)
      .collect(Collectors.toList());
    return new PyImportNameTreeImpl(astNode, importKeyword, aliasedNames);
  }

  public PyImportFromTree importFromStatement(AstNode astNode) {
    Token importKeyword = astNode.getFirstChild(PythonKeyword.IMPORT).getToken();
    Token fromKeyword = astNode.getFirstChild(PythonKeyword.FROM).getToken();
    List<Token> dottedPrefixForModule = astNode.getChildren(PythonPunctuator.DOT).stream()
      .map(AstNode::getToken)
      .collect(Collectors.toList());
    AstNode moduleNode = astNode.getFirstChild(PythonGrammar.DOTTED_NAME);
    PyDottedNameTree moduleName = null;
    if (moduleNode != null) {
      moduleName = dottedName(moduleNode);
    }
    AstNode importAsnames = astNode.getFirstChild(PythonGrammar.IMPORT_AS_NAMES);
    List<PyAliasedNameTree> aliasedImportNames = null;
    boolean isWildcardImport = true;
    if (importAsnames != null) {
      aliasedImportNames = importAsnames.getChildren(PythonGrammar.IMPORT_AS_NAME).stream()
        .map(this::aliasedName)
        .collect(Collectors.toList());
      isWildcardImport = false;
    }
    return new PyImportFromTreeImpl(astNode, fromKeyword, dottedPrefixForModule, moduleName, importKeyword, aliasedImportNames, isWildcardImport);
  }

  private PyAliasedNameTree aliasedName(AstNode astNode) {
    AstNode asKeyword = astNode.getFirstChild(PythonKeyword.AS);
    PyDottedNameTree dottedName;
    if (astNode.is(PythonGrammar.DOTTED_AS_NAME)) {
      dottedName = dottedName(astNode.getFirstChild(PythonGrammar.DOTTED_NAME));
    } else {
      // astNode is IMPORT_AS_NAME
      AstNode importedName = astNode.getFirstChild(PythonGrammar.NAME);
      dottedName = new PyDottedNameTreeImpl(astNode, Collections.singletonList(name(importedName)));
    }
    if (asKeyword == null) {
      return new PyAliasedNameTreeImpl(astNode, null, dottedName, null);
    }
    return new PyAliasedNameTreeImpl(astNode, asKeyword.getToken(), dottedName, name(astNode.getLastChild(PythonGrammar.NAME)));
  }

  private PyDottedNameTree dottedName(AstNode astNode) {
    List<PyNameTree> names = astNode
      .getChildren(PythonGrammar.NAME).stream()
      .map(this::name)
      .collect(Collectors.toList());
    return new PyDottedNameTreeImpl(astNode, names);
  }
  // Compound statements

  public PyIfStatementTree ifStatement(AstNode astNode) {
    Token ifToken = astNode.getTokens().get(0);
    AstNode condition = astNode.getFirstChild(PythonGrammar.TEST);
    AstNode suite = astNode.getFirstChild(PythonGrammar.SUITE);
    List<PyStatementTree> statements = getStatementsFromSuite(suite);
    AstNode elseSuite = astNode.getLastChild(PythonGrammar.SUITE);
    PyElseStatementTree elseStatement = null;
    if (elseSuite.getPreviousSibling().getPreviousSibling().is(PythonKeyword.ELSE)) {
      elseStatement = elseStatement(elseSuite);
    }
    List<PyIfStatementTree> elifBranches = astNode.getChildren(PythonKeyword.ELIF).stream()
      .map(this::elifStatement)
      .collect(Collectors.toList());

    return new PyIfStatementTreeImpl(
      astNode, ifToken, expression(condition), statements, elifBranches, elseStatement);
  }

  private PyIfStatementTree elifStatement(AstNode astNode) {
    Token elifToken = astNode.getToken();
    AstNode suite = astNode.getNextSibling().getNextSibling().getNextSibling();
    AstNode condition = astNode.getNextSibling();
    List<PyStatementTree> statements = getStatementsFromSuite(suite);
    return new PyIfStatementTreeImpl(
      astNode, elifToken, expression(condition), statements);
  }

  private PyElseStatementTree elseStatement(AstNode astNode) {
    Token elseToken = astNode.getPreviousSibling().getPreviousSibling().getToken();
    List<PyStatementTree> statements = getStatementsFromSuite(astNode);
    return new PyElseStatementTreeImpl(astNode, elseToken, statements);
  }

  PyExpressionTree expression(AstNode astNode) {
    if (astNode.is(PythonGrammar.YIELD_EXPR)) {
      return yieldExpression(astNode);
    }
    return new PyExpressionTreeImpl(astNode);
  }

  public PyFunctionDefTree funcDefStatement(AstNode astNode) {
    // TODO decorators
    PyNameTree name = name(astNode.getFirstChild(PythonGrammar.FUNCNAME).getFirstChild(PythonGrammar.NAME));
    // TODO argList
    PyTypedArgListTree typedArgs = null;
    List<PyStatementTree> body = getStatementsFromSuite(astNode.getFirstChild(PythonGrammar.SUITE));
    return new PyFunctionDefTreeImpl(astNode, name, typedArgs, body);
  }

  public PyClassDefTree classDefStatement(AstNode astNode) {
    // TODO decorators
    PyNameTree name = name(astNode.getFirstChild(PythonGrammar.CLASSNAME).getFirstChild(PythonGrammar.NAME));
    // TODO argList
    PyArgListTree args = null;
    List<PyStatementTree> body = getStatementsFromSuite(astNode.getFirstChild(PythonGrammar.SUITE));
    return new PyClassDefTreeImpl(astNode, name, args, body);
  }

  private PyNameTree name(AstNode astNode) {
    return new PyNameTreeImpl(astNode, astNode.getFirstChild(GenericTokenType.IDENTIFIER).getTokenOriginalValue());
  }
}
