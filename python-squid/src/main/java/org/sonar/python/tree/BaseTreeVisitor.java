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

import java.util.List;
import javax.annotation.Nullable;
import org.sonar.python.api.tree.PyAliasedNameTree;
import org.sonar.python.api.tree.PyArgumentTree;
import org.sonar.python.api.tree.PyAssertStatementTree;
import org.sonar.python.api.tree.PyAssignmentStatementTree;
import org.sonar.python.api.tree.PyAtomTree;
import org.sonar.python.api.tree.PyBreakStatementTree;
import org.sonar.python.api.tree.PyCallExpressionTree;
import org.sonar.python.api.tree.PyClassDefTree;
import org.sonar.python.api.tree.PyContinueStatementTree;
import org.sonar.python.api.tree.PyDelStatementTree;
import org.sonar.python.api.tree.PyDottedNameTree;
import org.sonar.python.api.tree.PyElseStatementTree;
import org.sonar.python.api.tree.PyExceptClauseTree;
import org.sonar.python.api.tree.PyExecStatementTree;
import org.sonar.python.api.tree.PyExpressionStatementTree;
import org.sonar.python.api.tree.PyFileInputTree;
import org.sonar.python.api.tree.PyFinallyClauseTree;
import org.sonar.python.api.tree.PyForStatementTree;
import org.sonar.python.api.tree.PyFunctionDefTree;
import org.sonar.python.api.tree.PyGlobalStatementTree;
import org.sonar.python.api.tree.PyIfStatementTree;
import org.sonar.python.api.tree.PyImportFromTree;
import org.sonar.python.api.tree.PyImportNameTree;
import org.sonar.python.api.tree.PyNameTree;
import org.sonar.python.api.tree.PyNonlocalStatementTree;
import org.sonar.python.api.tree.PyPassStatementTree;
import org.sonar.python.api.tree.PyPrintStatementTree;
import org.sonar.python.api.tree.PyQualifiedExpressionTree;
import org.sonar.python.api.tree.PyRaiseStatementTree;
import org.sonar.python.api.tree.PyReturnStatementTree;
import org.sonar.python.api.tree.PyStatementListTree;
import org.sonar.python.api.tree.PyTreeVisitor;
import org.sonar.python.api.tree.PyTryStatementTree;
import org.sonar.python.api.tree.PyWhileStatementTree;
import org.sonar.python.api.tree.PyWithItemTree;
import org.sonar.python.api.tree.PyWithStatementTree;
import org.sonar.python.api.tree.PyYieldExpressionTree;
import org.sonar.python.api.tree.PyYieldStatementTree;
import org.sonar.python.api.tree.Tree;

/**
 * Default implementation of {@link org.sonar.python.api.tree.PyTreeVisitor}.
 */
public class BaseTreeVisitor implements PyTreeVisitor {

  protected void scan(@Nullable Tree tree) {
    if (tree != null) {
      tree.accept(this);
    }
  }

  protected void scan(List<? extends Tree> trees) {
    if (trees != null) {
      for (Tree tree : trees) {
        scan(tree);
      }
    }
  }

  @Override
  public void visitFileInput(PyFileInputTree pyFileInputTree) {
    scan(pyFileInputTree.statements());
  }

  @Override
  public void visitStatementList(PyStatementListTree pyStatementListTree) {
    scan(pyStatementListTree.statements());
  }

  @Override
  public void visitIfStatement(PyIfStatementTree pyIfStatementTree) {
    scan(pyIfStatementTree.condition());
    scan(pyIfStatementTree.body());
    scan(pyIfStatementTree.elifBranches());
    scan(pyIfStatementTree.elseBranch());
  }

  @Override
  public void visitElseStatement(PyElseStatementTree pyElseStatementTree) {
    scan(pyElseStatementTree.body());
  }

  @Override
  public void visitExecStatement(PyExecStatementTree pyExecStatementTree) {
    scan(pyExecStatementTree.expression());
    scan(pyExecStatementTree.globalsExpression());
    scan(pyExecStatementTree.localsExpression());
  }

  @Override
  public void visitAssertStatement(PyAssertStatementTree pyAssertStatementTree) {
    scan(pyAssertStatementTree.expressions());
  }

  @Override
  public void visitDelStatement(PyDelStatementTree pyDelStatementTree) {
    scan(pyDelStatementTree.expressions());
  }

  @Override
  public void visitPassStatement(PyPassStatementTree pyPassStatementTree) {
    // nothing to visit for pass statement
  }

  @Override
  public void visitPrintStatement(PyPrintStatementTree pyPrintStatementTree) {
    scan(pyPrintStatementTree.expressions());
  }

  @Override
  public void visitReturnStatement(PyReturnStatementTree pyReturnStatementTree) {
    scan(pyReturnStatementTree.expressions());
  }

  @Override
  public void visitYieldStatement(PyYieldStatementTree pyYieldStatementTree) {
    scan(pyYieldStatementTree.yieldExpression());
  }

  @Override
  public void visitYieldExpression(PyYieldExpressionTree pyYieldExpressionTree) {
    scan(pyYieldExpressionTree.expressions());
  }

  @Override
  public void visitRaiseStatement(PyRaiseStatementTree pyRaiseStatementTree) {
    scan(pyRaiseStatementTree.expressions());
    scan(pyRaiseStatementTree.fromExpression());
  }

  @Override
  public void visitBreakStatement(PyBreakStatementTree pyBreakStatementTree) {
    // nothing to visit for break statement
  }

  @Override
  public void visitContinueStatement(PyContinueStatementTree pyContinueStatementTree) {
    // nothing to visit for continue statement
  }

  @Override
  public void visitFunctionDef(PyFunctionDefTree pyFunctionDefTree) {
    scan(pyFunctionDefTree.decorators());
    scan(pyFunctionDefTree.name());
    scan(pyFunctionDefTree.typedArgs());
    scan(pyFunctionDefTree.annotationReturn());
    scan(pyFunctionDefTree.body());
  }

  @Override
  public void visitName(PyNameTree pyNameTree) {
    // nothing to scan on a name
  }

  @Override
  public void visitClassDef(PyClassDefTree pyClassDefTree) {
    scan(pyClassDefTree.name());
    scan(pyClassDefTree.args());
    scan(pyClassDefTree.body());
  }

  @Override
  public void visitAliasedName(PyAliasedNameTree pyAliasedNameTree) {
    scan(pyAliasedNameTree.dottedName());
    scan(pyAliasedNameTree.alias());
  }

  @Override
  public void visitDottedName(PyDottedNameTree pyDottedNameTree) {
    scan(pyDottedNameTree.names());
  }

  @Override
  public void visitImportFrom(PyImportFromTree pyImportFromTree) {
    scan(pyImportFromTree.module());
    scan(pyImportFromTree.importedNames());
  }

  @Override
  public void visitForStatement(PyForStatementTree pyForStatementTree) {
    scan(pyForStatementTree.expressions());
    scan(pyForStatementTree.testExpressions());
    scan(pyForStatementTree.body());
    scan(pyForStatementTree.elseBody());
  }

  @Override
  public void visitImportName(PyImportNameTree pyImportNameTree) {
    scan(pyImportNameTree.modules());
  }

  @Override
  public void visitGlobalStatement(PyGlobalStatementTree pyGlobalStatementTree) {
    scan(pyGlobalStatementTree.variables());
  }

  @Override
  public void visitNonlocalStatement(PyNonlocalStatementTree pyNonlocalStatementTree) {
    scan(pyNonlocalStatementTree.variables());
  }

  @Override
  public void visitWhileStatement(PyWhileStatementTree pyWhileStatementTree) {
    scan(pyWhileStatementTree.condition());
    scan(pyWhileStatementTree.body());
    scan(pyWhileStatementTree.elseBody());
  }

  @Override
  public void visitExpressionStatement(PyExpressionStatementTree pyExpressionStatementTree) {
    scan(pyExpressionStatementTree.expressions());
  }

  @Override
  public void visitTryStatement(PyTryStatementTree pyTryStatementTree) {
    scan(pyTryStatementTree.body());
    scan(pyTryStatementTree.exceptClauses());
    scan(pyTryStatementTree.finallyClause());
    scan(pyTryStatementTree.elseClause());
  }

  @Override
  public void visitFinallyClause(PyFinallyClauseTree pyFinallyClauseTree) {
    scan(pyFinallyClauseTree.body());
  }

  @Override
  public void visitExceptClause(PyExceptClauseTree pyExceptClauseTree) {
    scan(pyExceptClauseTree.exception());
    scan(pyExceptClauseTree.exceptionInstance());
    scan(pyExceptClauseTree.body());
  }

  @Override
  public void visitWithStatement(PyWithStatementTree pyWithStatementTree) {
    scan(pyWithStatementTree.withItems());
    scan(pyWithStatementTree.statements());
  }

  @Override
  public void visitWithItem(PyWithItemTree pyWithItemTree) {
    scan(pyWithItemTree.test());
    scan(pyWithItemTree.expression());
  }

  @Override
  public void visitQualifiedExpression(PyQualifiedExpressionTree pyQualifiedExpressionTree) {
    scan(pyQualifiedExpressionTree.qualifier());
    scan(pyQualifiedExpressionTree.name());
  }

  @Override
  public void visitCallExpression(PyCallExpressionTree pyCallExpressionTree) {
    scan(pyCallExpressionTree.callee());
    scan(pyCallExpressionTree.arguments());
  }

  @Override
  public void visitArgument(PyArgumentTree pyArgumentTree) {
    scan(pyArgumentTree.keywordArgument());
    scan(pyArgumentTree.expression());
  }

  @Override
  public void visitAssignmentStatement(PyAssignmentStatementTree pyAssignmentStatementTree) {
    scan(pyAssignmentStatementTree.lhsExpressions());
    scan(pyAssignmentStatementTree.assignedValues());
  }

  @Override
  public void visitExpressionList(PyExpressionListTreeImpl pyExpressionListTree) {
    scan(pyExpressionListTree.expressions());
  }

  @Override
  public void visitAtom(PyAtomTree pyAtomTree) {
    scan(pyAtomTree.atom());
  }
}
