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
package org.sonar.python.tree;

import org.junit.Test;
import org.sonar.plugins.python.api.tree.AsPattern;
import org.sonar.plugins.python.api.tree.CapturePattern;
import org.sonar.plugins.python.api.tree.CaseBlock;
import org.sonar.plugins.python.api.tree.Expression;
import org.sonar.plugins.python.api.tree.Guard;
import org.sonar.plugins.python.api.tree.ListLiteral;
import org.sonar.plugins.python.api.tree.LiteralPattern;
import org.sonar.plugins.python.api.tree.MatchStatement;
import org.sonar.plugins.python.api.tree.Pattern;
import org.sonar.plugins.python.api.tree.Tree;
import org.sonar.plugins.python.api.tree.Tuple;
import org.sonar.python.api.PythonGrammar;
import org.sonar.python.parser.RuleTest;

import static org.assertj.core.api.Assertions.assertThat;

public class PythonTreeMakerMatchStatementTest extends RuleTest {

  private final PythonTreeMaker treeMaker = new PythonTreeMaker();

  @Test
  public void match_statement() {
    setRootRule(PythonGrammar.MATCH_STMT);
    MatchStatement matchStatement = parse("match command:\n  case 42:...\n", treeMaker::matchStatement);
    assertThat(matchStatement.getKind()).isEqualTo(Tree.Kind.MATCH_STMT);
    assertThat(matchStatement.matchKeyword().value()).isEqualTo("match");
    assertThat(matchStatement.subjectExpression().getKind()).isEqualTo(Tree.Kind.NAME);
    assertThat(matchStatement.caseBlocks()).hasSize(1);
    assertThat(matchStatement.children()).extracting(Tree::getKind)
      .containsExactly(Tree.Kind.TOKEN, Tree.Kind.NAME, Tree.Kind.TOKEN, Tree.Kind.TOKEN, Tree.Kind.TOKEN, Tree.Kind.CASE_BLOCK, Tree.Kind.TOKEN);

    CaseBlock caseBlock = matchStatement.caseBlocks().get(0);
    assertThat(caseBlock.caseKeyword().value()).isEqualTo("case");
    assertThat(caseBlock.guard()).isNull();
    assertThat(caseBlock.body().statements()).extracting(Tree::getKind).containsExactly(Tree.Kind.EXPRESSION_STMT);
    assertThat(caseBlock.children()).extracting(Tree::getKind)
      .containsExactly(Tree.Kind.TOKEN, Tree.Kind.LITERAL_PATTERN, Tree.Kind.TOKEN, Tree.Kind.STATEMENT_LIST);

    Pattern pattern = caseBlock.pattern();
    assertThat(pattern.getKind()).isEqualTo(Tree.Kind.LITERAL_PATTERN);
    LiteralPattern literalPattern = (LiteralPattern) pattern;
    assertThat(literalPattern.literalKind()).isEqualTo(LiteralPattern.LiteralKind.NUMBER);
    assertThat(literalPattern.children()).extracting(Tree::getKind)
      .containsExactly(Tree.Kind.TOKEN);
  }

  @Test
  public void match_statement_tuple_subject() {
    setRootRule(PythonGrammar.MATCH_STMT);
    MatchStatement matchStatement = parse("match (x, y):\n  case 42:...\n", treeMaker::matchStatement);
    Expression subjectExpression = matchStatement.subjectExpression();
    assertThat(subjectExpression.getKind()).isEqualTo(Tree.Kind.TUPLE);
    assertThat(((Tuple) subjectExpression).elements()).hasSize(2);

    matchStatement = parse("match x, y:\n  case 42:...\n", treeMaker::matchStatement);
    subjectExpression = matchStatement.subjectExpression();
    assertThat(subjectExpression.getKind()).isEqualTo(Tree.Kind.TUPLE);
    assertThat(((Tuple) subjectExpression).elements()).hasSize(2);

    matchStatement = parse("match x,:\n  case 42:...\n", treeMaker::matchStatement);
    subjectExpression = matchStatement.subjectExpression();
    assertThat(subjectExpression.getKind()).isEqualTo(Tree.Kind.TUPLE);
    assertThat(((Tuple) subjectExpression).elements()).hasSize(1);

    matchStatement = parse("match (x):\n  case 42:...\n", treeMaker::matchStatement);
    subjectExpression = matchStatement.subjectExpression();
    assertThat(subjectExpression.getKind()).isEqualTo(Tree.Kind.PARENTHESIZED);
  }

  @Test
  public void match_statement_list_subject() {
    setRootRule(PythonGrammar.MATCH_STMT);
    MatchStatement matchStatement = parse("match [x, y]:\n  case 42:...\n", treeMaker::matchStatement);
    Expression subjectExpression = matchStatement.subjectExpression();
    assertThat(subjectExpression.getKind()).isEqualTo(Tree.Kind.LIST_LITERAL);
    assertThat(((ListLiteral) subjectExpression).elements().expressions()).hasSize(2);
  }

  @Test
  public void case_block_with_guard() {
    setRootRule(PythonGrammar.CASE_BLOCK);
    CaseBlock caseBlock = parse("case 42 if x is None: ...", treeMaker::caseBlock);
    Guard guard = caseBlock.guard();
    assertThat(guard).isNotNull();
    assertThat(guard.ifKeyword().value()).isEqualTo("if");
    assertThat(guard.condition().getKind()).isEqualTo(Tree.Kind.IS);
    assertThat(guard.children()).extracting(Tree::getKind)
      .containsExactly(Tree.Kind.TOKEN, Tree.Kind.IS);
  }

  @Test
  public void literal_patterns() {
    setRootRule(PythonGrammar.CASE_BLOCK);
    CaseBlock caseBlock = parse("case \"foo\": ...", treeMaker::caseBlock);
    LiteralPattern literalPattern = (LiteralPattern) caseBlock.pattern();
    assertThat(literalPattern.literalKind()).isEqualTo(LiteralPattern.LiteralKind.STRING);
    assertThat(literalPattern.valueAsString()).isEqualTo("\"foo\"");

    caseBlock = parse("case \"foo\" \"bar\": ...", treeMaker::caseBlock);
    literalPattern = (LiteralPattern) caseBlock.pattern();
    assertThat(literalPattern.literalKind()).isEqualTo(LiteralPattern.LiteralKind.STRING);
    assertThat(literalPattern.valueAsString()).isEqualTo("\"foo\"\"bar\"");

    caseBlock = parse("case -42: ...", treeMaker::caseBlock);
    literalPattern = (LiteralPattern) caseBlock.pattern();
    assertThat(literalPattern.literalKind()).isEqualTo(LiteralPattern.LiteralKind.NUMBER);
    assertThat(literalPattern.valueAsString()).isEqualTo("-42");

    caseBlock = parse("case 3 + 5j: ...", treeMaker::caseBlock);
    literalPattern = (LiteralPattern) caseBlock.pattern();
    assertThat(literalPattern.literalKind()).isEqualTo(LiteralPattern.LiteralKind.NUMBER);
    assertThat(literalPattern.valueAsString()).isEqualTo("3+5j");

    caseBlock = parse("case None: ...", treeMaker::caseBlock);
    literalPattern = (LiteralPattern) caseBlock.pattern();
    assertThat(literalPattern.literalKind()).isEqualTo(LiteralPattern.LiteralKind.NONE);
    assertThat(literalPattern.valueAsString()).isEqualTo("None");

    caseBlock = parse("case True: ...", treeMaker::caseBlock);
    literalPattern = (LiteralPattern) caseBlock.pattern();
    assertThat(literalPattern.literalKind()).isEqualTo(LiteralPattern.LiteralKind.BOOLEAN);
    assertThat(literalPattern.valueAsString()).isEqualTo("True");

    caseBlock = parse("case False: ...", treeMaker::caseBlock);
    literalPattern = (LiteralPattern) caseBlock.pattern();
    assertThat(literalPattern.literalKind()).isEqualTo(LiteralPattern.LiteralKind.BOOLEAN);
    assertThat(literalPattern.valueAsString()).isEqualTo("False");
  }

  @Test
  public void as_pattern() {
    setRootRule(PythonGrammar.CASE_BLOCK);
    CaseBlock caseBlock = parse("case \"foo\" as x: ...", treeMaker::caseBlock);
    AsPattern asPattern = (AsPattern) caseBlock.pattern();
    assertThat(asPattern.pattern()).isInstanceOf(LiteralPattern.class);
    assertThat(asPattern.alias().name()).isEqualTo("x");
    assertThat(asPattern.children()).extracting(Tree::getKind).containsExactly(Tree.Kind.LITERAL_PATTERN, Tree.Kind.TOKEN, Tree.Kind.NAME);

    caseBlock = parse("case value as x: ...", treeMaker::caseBlock);
    asPattern = (AsPattern) caseBlock.pattern();
    assertThat(asPattern.pattern().getKind()).isEqualTo(Tree.Kind.CAPTURE_PATTERN);
  }

  @Test
  public void capture_pattern() {
    setRootRule(PythonGrammar.CASE_BLOCK);
    CaseBlock caseBlock = parse("case x: ...", treeMaker::caseBlock);
    CapturePattern capturePattern = (CapturePattern) caseBlock.pattern();
    assertThat(capturePattern.name().name()).isEqualTo("x");
    assertThat(capturePattern.children()).containsExactly(capturePattern.name());
  }
}
