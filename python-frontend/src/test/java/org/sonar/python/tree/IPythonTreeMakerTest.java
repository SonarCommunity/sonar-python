/*
 * SonarQube Python Plugin
 * Copyright (C) 2011-2023 SonarSource SA
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

import com.sonar.sslr.api.RecognitionException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;
import org.sonar.plugins.python.api.tree.AssignmentStatement;
import org.sonar.plugins.python.api.tree.DynamicObjectInfoStatement;
import org.sonar.plugins.python.api.tree.LineMagic;
import org.sonar.plugins.python.api.tree.Statement;
import org.sonar.plugins.python.api.tree.Tree;
import org.sonar.python.api.PythonGrammar;
import org.sonar.python.parser.RuleTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class IPythonTreeMakerTest extends RuleTest {

  private final IPythonTreeMaker treeMaker = new IPythonTreeMaker();

  @Test
  public void emptyFile() {
    var parse = parseIPython("", treeMaker::fileInput);
    assertThat(parse).isNotNull();
    assertThat(parse.statements()).isNull();
  }

  @Test
  public void cellDelimiter() {
    var parse = parseIPython("print(b)\n" +
      "#SONAR_PYTHON_NOTEBOOK_CELL_DELIMITER\nprint(c)", treeMaker::fileInput);
    assertThat(parse).isNotNull();
    List<Statement> statements = parse.statements().statements();
    assertThat(statements).hasSize(2);
    assertThat(statements.get(0).is(Tree.Kind.EXPRESSION_STMT)).isTrue();
    assertThat(statements.get(1).is(Tree.Kind.EXPRESSION_STMT)).isTrue();
  }

  @Test
  public void cellDelimiterAtBeginningOfFile() {
    var parse = parseIPython("#SONAR_PYTHON_NOTEBOOK_CELL_DELIMITER\nprint(b)\n" +
      "print(c)", treeMaker::fileInput);
    assertThat(parse).isNotNull();
    List<Statement> statements = parse.statements().statements();
    assertThat(statements).hasSize(2);
    assertThat(statements.get(0).is(Tree.Kind.EXPRESSION_STMT)).isTrue();
    assertThat(statements.get(1).is(Tree.Kind.EXPRESSION_STMT)).isTrue();
  }

  @Test
  public void cellDelimiterAtEndOfFile() {
    var parse = parseIPython("print(a)\n" +
      "print(b)\n#SONAR_PYTHON_NOTEBOOK_CELL_DELIMITER\n", treeMaker::fileInput);
    List<Statement> statements = parse.statements().statements();
    assertThat(statements).hasSize(2);
    assertThat(statements.get(0).is(Tree.Kind.EXPRESSION_STMT)).isTrue();
    assertThat(statements.get(1).is(Tree.Kind.EXPRESSION_STMT)).isTrue();
  }

  @Test
  public void cellDelimiterAfterCompoundStatement() {
    var parse = parseIPython("if(b):\n" +
      "  print(x)\n" +
      "#SONAR_PYTHON_NOTEBOOK_CELL_DELIMITER\nprint(c)", treeMaker::fileInput);
    List<Statement> statements = parse.statements().statements();
    assertThat(statements).hasSize(2);
    assertThat(statements.get(0).is(Tree.Kind.IF_STMT)).isTrue();
    assertThat(statements.get(1).is(Tree.Kind.EXPRESSION_STMT)).isTrue();
  }

  @Test
  public void cellDelimiterInCompoundStatementShouldFail() {
    assertThatThrownBy(() -> parseIPython("if(b):\n" +
      "  print(x)\n" +
      "#SONAR_PYTHON_NOTEBOOK_CELL_DELIMITER\n" +
      "  print(c)", treeMaker::fileInput)).isInstanceOf(RecognitionException.class);
  }

  @Test
  public void regularCellFollowedByMagicCell() {
    var parse = parseIPython("print(b)\n#SONAR_PYTHON_NOTEBOOK_CELL_DELIMITER\n" +
      "%%hello\n#SONAR_PYTHON_NOTEBOOK_CELL_DELIMITER\nprint(c)", treeMaker::fileInput);
    List<Statement> statements = parse.statements().statements();
    assertThat(statements).hasSize(3);
    assertThat(statements.get(0).is(Tree.Kind.EXPRESSION_STMT)).isTrue();
    assertThat(statements.get(1).is(Tree.Kind.CELL_MAGIC_STATEMENT)).isTrue();
    assertThat(statements.get(2).is(Tree.Kind.EXPRESSION_STMT)).isTrue();
  }

  @Test
  public void cellMagicWithMissingDelimiterStillParsed() {
    var parse = parseIPython("print(b)\n" +
      "%%hello\n#SONAR_PYTHON_NOTEBOOK_CELL_DELIMITER\nprint(c)", treeMaker::fileInput);
    List<Statement> statements = parse.statements().statements();
    assertThat(statements).hasSize(3);
    assertThat(statements.get(0).is(Tree.Kind.EXPRESSION_STMT)).isTrue();
    assertThat(statements.get(1).is(Tree.Kind.CELL_MAGIC_STATEMENT)).isTrue();
    assertThat(statements.get(2).is(Tree.Kind.EXPRESSION_STMT)).isTrue();
  }

  @Test
  public void cellMagicUntilEndOfFile() {
    setRootRule(PythonGrammar.FILE_INPUT);
    var parse = parseIPython("%%hello\n" +
      "print(b)\n", treeMaker::fileInput);
    List<Statement> statements = parse.statements().statements();
    assertThat(statements).hasSize(1);
    assertThat(statements.get(0).is(Tree.Kind.CELL_MAGIC_STATEMENT)).isTrue();
  }

  @Test
  public void dynamicObjectInfo() {
    var file = parseIPython("a = A()\n" +
      "??a.foo\n" +
      "?a.foo\n" +
      "?a.foo?\n" +
      "a.foo?\n" +
      "a.foo??\n" +
      "??a.foo()??\n" +
      "b = a.foo()", treeMaker::fileInput);
    var statementList = findFirstChildWithKind(file, Tree.Kind.STATEMENT_LIST);
    var statements = statementList.children();
    assertThat(statements).hasSize(8);

    assertThat(statements.get(0).getKind()).isEqualTo(Tree.Kind.ASSIGNMENT_STMT);
    checkDynamicObjectInfo(statements.get(1), 2, 0);
    checkDynamicObjectInfo(statements.get(2), 1, 0);
    checkDynamicObjectInfo(statements.get(3), 1, 1);
    checkDynamicObjectInfo(statements.get(4), 0, 1);
    checkDynamicObjectInfo(statements.get(5), 0, 2);
    checkDynamicObjectInfo(statements.get(6), 2, 2);
    assertThat(statements.get(7).getKind()).isEqualTo(Tree.Kind.ASSIGNMENT_STMT);
  }

  private void checkDynamicObjectInfo(Tree dynamicObjectInfo, int questionMarksBefore, int questionMarksAfter) {
    assertThat(dynamicObjectInfo)
      .isNotNull()
      .isInstanceOf(DynamicObjectInfoStatement.class)
      .extracting(Tree::getKind)
      .isEqualTo(Tree.Kind.DYNAMIC_OBJECT_INFO_STATEMENT);

    var children = dynamicObjectInfo.children();
    for (int i = 0; questionMarksBefore > 0 && i < questionMarksBefore; i++) {
      var child = children.get(i);
      assertThat(child.getKind()).isEqualTo(Tree.Kind.TOKEN);
      var tokenValue = child.firstToken().value();
      assertThat(tokenValue).isEqualTo("?");
    }

    for (int i = children.size() - questionMarksAfter; questionMarksAfter > 0 && i < children.size(); i++) {
      var child = children.get(i);
      assertThat(child.getKind()).isEqualTo(Tree.Kind.TOKEN);
      var tokenValue = child.firstToken().value();
      assertThat(tokenValue).isEqualTo("?");
    }
  }

  @Test
  public void lineMagic() {
    var statements = parseIPython("print(b)\n" +
      "a = %alias showPath pwd && ls -a\n", treeMaker::fileInput).statements().statements();
    assertThat(statements).hasSize(2);
    assertThat(statements.get(0).getKind()).isEqualTo(Tree.Kind.EXPRESSION_STMT);
    assertThat(statements.get(1).getKind()).isEqualTo(Tree.Kind.ASSIGNMENT_STMT);
    var lineMagic = findFirstChildWithKind(statements.get(1), Tree.Kind.LINE_MAGIC);
    assertThat(lineMagic)
      .isNotNull()
      .isInstanceOf(LineMagic.class);

    statements = parseIPython("print(b)\n" +
      "a = %timeit foo(b)\n", treeMaker::fileInput).statements().statements();
    assertThat(statements).hasSize(2);
    assertThat(statements.get(0).getKind()).isEqualTo(Tree.Kind.EXPRESSION_STMT);
    assertThat(statements.get(1).getKind()).isEqualTo(Tree.Kind.ASSIGNMENT_STMT);
    lineMagic = findFirstChildWithKind(statements.get(1), Tree.Kind.LINE_MAGIC);
    assertThat(lineMagic)
      .isNotNull()
      .isInstanceOf(LineMagic.class);

    statements = parseIPython("print(b)\n" +
      "a = %timeit foo(b) % 3\n" +
      "print(a)", treeMaker::fileInput).statements().statements();
    assertThat(statements).hasSize(3);
    assertThat(statements.get(0).getKind()).isEqualTo(Tree.Kind.EXPRESSION_STMT);
    assertThat(statements.get(1).getKind()).isEqualTo(Tree.Kind.ASSIGNMENT_STMT);
    assertThat(statements.get(2).getKind()).isEqualTo(Tree.Kind.EXPRESSION_STMT);
    lineMagic = findFirstChildWithKind(statements.get(1), Tree.Kind.LINE_MAGIC);
    assertThat(lineMagic)
      .isNotNull()
      .isInstanceOf(LineMagic.class);
  }

  @Test
  public void lineMagicStatement() {
    var statements = parseIPython("print(b)\n" +
      "%alias showPath pwd && ls -a\n", treeMaker::fileInput).statements().statements();
    assertThat(statements).hasSize(2);
    assertThat(statements.get(0).getKind()).isEqualTo(Tree.Kind.EXPRESSION_STMT);
    assertThat(statements.get(1).getKind()).isEqualTo(Tree.Kind.LINE_MAGIC_STATEMENT);

    var lineMagicStatement = statements.get(1);
    assertThat(lineMagicStatement.children()).hasSize(1);
    var lineMagic = findFirstChildWithKind(lineMagicStatement, Tree.Kind.LINE_MAGIC);
    assertThat(lineMagic).isNotNull();

    statements = parseIPython("print(b)\n" +
      "%timeit a = foo(b) % 3\n" +
      "a %= 2\n" +
      "b = a % foo(b)", treeMaker::fileInput).statements().statements();
    assertThat(statements).hasSize(4);
    assertThat(statements.get(0).getKind()).isEqualTo(Tree.Kind.EXPRESSION_STMT);
    assertThat(statements.get(1).getKind()).isEqualTo(Tree.Kind.LINE_MAGIC_STATEMENT);
    assertThat(statements.get(2).getKind()).isEqualTo(Tree.Kind.COMPOUND_ASSIGNMENT);
    assertThat(statements.get(3).getKind()).isEqualTo(Tree.Kind.ASSIGNMENT_STMT);
    assertThat(findFirstChildWithKind(statements.get(3), Tree.Kind.MODULO)).isNotNull();

    statements = parseIPython("print(b)\n" +
      "%timeit a = foo(b); b = 2\n" +
      "a += b", treeMaker::fileInput).statements().statements();
    assertThat(statements).hasSize(3);
    assertThat(statements.get(0).getKind()).isEqualTo(Tree.Kind.EXPRESSION_STMT);
    assertThat(statements.get(1).getKind()).isEqualTo(Tree.Kind.LINE_MAGIC_STATEMENT);
    assertThat(statements.get(2).getKind()).isEqualTo(Tree.Kind.COMPOUND_ASSIGNMENT);

    statements = parseIPython("print(b)\n" +
      "%timeit a =\\\n" +
      "  foo(b); b = 2\n" +
      "a +=\\\n" +
      "  b", treeMaker::fileInput).statements().statements();
    assertThat(statements).hasSize(3);
    assertThat(statements.get(0).getKind()).isEqualTo(Tree.Kind.EXPRESSION_STMT);
    assertThat(statements.get(1).getKind()).isEqualTo(Tree.Kind.LINE_MAGIC_STATEMENT);
    assertThat(statements.get(2).getKind()).isEqualTo(Tree.Kind.COMPOUND_ASSIGNMENT);
  }

  @Test
  public void systemShellAccess() {
    var statements = parseIPython("print(b)\n" +
      "!pwd \\\n" +
      "  && ls -a | sed 's/^/\\    /'\n" +
      "a =\\\n" +
      "  b", treeMaker::fileInput).statements().statements();
    assertThat(statements).hasSize(3);
    assertThat(statements.get(0).getKind()).isEqualTo(Tree.Kind.EXPRESSION_STMT);
    assertThat(statements.get(1).getKind()).isEqualTo(Tree.Kind.LINE_MAGIC_STATEMENT);
    assertThat(statements.get(2).getKind()).isEqualTo(Tree.Kind.ASSIGNMENT_STMT);

    var lineMagicStatement = statements.get(1);
    assertThat(lineMagicStatement.children()).hasSize(1);
    var lineMagic = findFirstChildWithKind(lineMagicStatement, Tree.Kind.LINE_MAGIC);
    assertThat(lineMagic).isNotNull();

    statements = parseIPython("print(b)\n" +
      "!pwd && ls -a | sed 's/^/\\    /'\n" +
      "a = b", treeMaker::fileInput).statements().statements();
    assertThat(statements).hasSize(3);
    assertThat(statements.get(0).getKind()).isEqualTo(Tree.Kind.EXPRESSION_STMT);
    assertThat(statements.get(1).getKind()).isEqualTo(Tree.Kind.LINE_MAGIC_STATEMENT);
    assertThat(statements.get(2).getKind()).isEqualTo(Tree.Kind.ASSIGNMENT_STMT);

    lineMagicStatement = statements.get(1);
    assertThat(lineMagicStatement.children()).hasSize(1);
    lineMagic = findFirstChildWithKind(lineMagicStatement, Tree.Kind.LINE_MAGIC);
    assertThat(lineMagic).isNotNull();
  }

  @Test
  public void assignmentRhs() {
    var statementList = parseIPython("print(b)\n" +
      "a = yield foo(b)\n" +
      "c = bar(a) + b\n" +
      "d = bar(c) % bar(a)", treeMaker::fileInput).statements();
    assertThat(statementList).isNotNull();

    var assignments = findChildrenWithKind(statementList, Tree.Kind.ASSIGNMENT_STMT)
      .stream().map(AssignmentStatement.class::cast).collect(Collectors.toList());
    assertThat(assignments).hasSize(3);
    assertThat(assignments.get(0).assignedValue().getKind()).isEqualTo(Tree.Kind.YIELD_EXPR);
    assertThat(assignments.get(1).assignedValue().getKind()).isEqualTo(Tree.Kind.PLUS);
    assertThat(assignments.get(2).assignedValue().getKind()).isEqualTo(Tree.Kind.MODULO);
  }

  private List<Tree> findChildrenWithKind(Tree parent, Tree.Kind kind) {
    if (parent.is(kind)) {
      return List.of(parent);
    }
    return parent.children()
      .stream()
      .flatMap(c -> {
        if (c.is(kind)) {
          return Stream.of(c);
        } else {
          return findChildrenWithKind(c, kind).stream();
        }
      })
      .collect(Collectors.toList());
  }

  private Tree findFirstChildWithKind(Tree parent, Tree.Kind kind) {
    return parent.children()
      .stream()
      .map(c -> {
        if (c.is(kind)) {
          return c;
        } else {
          return findFirstChildWithKind(c, kind);
        }
      })
      .filter(Objects::nonNull)
      .findFirst()
      .orElse(null);
  }
}