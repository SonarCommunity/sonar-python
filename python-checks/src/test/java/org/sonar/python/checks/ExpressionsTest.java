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
package org.sonar.python.checks;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.sonar.plugins.python.api.tree.BaseTreeVisitor;
import org.sonar.plugins.python.api.tree.Expression;
import org.sonar.plugins.python.api.tree.FileInput;
import org.sonar.plugins.python.api.tree.Name;
import org.sonar.plugins.python.api.tree.StringElement;
import org.sonar.plugins.python.api.tree.StringLiteral;
import org.sonar.plugins.python.api.tree.Tree;
import org.sonar.plugins.python.api.tree.Tree.Kind;
import org.sonar.python.parser.PythonParser;
import org.sonar.python.semantic.SymbolTableBuilder;
import org.sonar.python.tree.PythonTreeMaker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonar.python.checks.Expressions.isFalsy;
import static org.sonar.python.checks.Expressions.removeParentheses;
import static org.sonar.python.checks.Expressions.unescape;
import static org.sonar.python.checks.Expressions.unescapeString;

public class ExpressionsTest {

  private PythonParser parser = PythonParser.create();

  @Test
  public void falsy() {
    assertThat(isFalsy(null)).isFalse();

    assertThat(isFalsy(exp("True"))).isFalse();
    assertThat(isFalsy(exp("False"))).isTrue();
    assertThat(isFalsy(exp("x"))).isFalse();
    assertThat(isFalsy(exp("None"))).isTrue();

    assertThat(isFalsy(exp("''"))).isTrue();
    assertThat(isFalsy(exp("'\\\n'"))).isTrue();
    assertThat(isFalsy(exp("'x'"))).isFalse();
    assertThat(isFalsy(exp("' '"))).isFalse();
    assertThat(isFalsy(exp("''"))).isTrue();
    assertThat(isFalsy(exp("\"\""))).isTrue();
    assertThat(isFalsy(exp("'' 'x'"))).isFalse();
    assertThat(isFalsy(exp("'' ''"))).isTrue();

    assertThat(isFalsy(exp("1"))).isFalse();
    assertThat(isFalsy(exp("0"))).isTrue();
    assertThat(isFalsy(exp("0.0"))).isTrue();
    assertThat(isFalsy(exp("0j"))).isTrue();
    assertThat(isFalsy(exp("3.14"))).isFalse();

    assertThat(isFalsy(exp("[x]"))).isFalse();
    assertThat(isFalsy(exp("[]"))).isTrue();
    assertThat(isFalsy(exp("(x)"))).isFalse();
    assertThat(isFalsy(exp("()"))).isTrue();
    assertThat(isFalsy(exp("{x:y}"))).isFalse();
    assertThat(isFalsy(exp("{x}"))).isFalse();
    assertThat(isFalsy(exp("{}"))).isTrue();

    assertThat(isFalsy(exp("x.y"))).isFalse();
  }

  @Test
  public void remove_parentheses() {
    assertThat(removeParentheses(exp("42")).getKind()).isEqualTo(Kind.NUMERIC_LITERAL);
    assertThat(removeParentheses(exp("(42)")).getKind()).isEqualTo(Kind.NUMERIC_LITERAL);
    assertThat(removeParentheses(exp("((42))")).getKind()).isEqualTo(Kind.NUMERIC_LITERAL);
    assertThat(removeParentheses(exp("((a))")).getKind()).isEqualTo(Kind.NAME);
    assertThat(removeParentheses(null)).isNull();
  }

  @Test
  public void unescape_string_literal() {
    assertThat(unescape(stringLiteral("''"))).isEqualTo("");
    assertThat(unescape(stringLiteral("'' ''"))).isEqualTo("");
    assertThat(unescape(stringLiteral("'abc' 'def'"))).isEqualTo("abcdef");
    assertThat(unescape(stringLiteral("'\\u0061' r'\\u0061' f'{value}'"))).isEqualTo("a\\u0061{value}");
  }

  @Test
  public void unescape_string_element_using_different_prefix() {
    assertThat(unescape(stringElement("''"))).isEqualTo("");
    assertThat(unescape(stringElement("b''"))).isEqualTo("");
    assertThat(unescape(stringElement("'a'"))).isEqualTo("a");
    assertThat(unescape(stringElement("u'a\\tc'"))).isEqualTo("a\tc");
    assertThat(unescape(stringElement("r'a\\tc'"))).isEqualTo("a\\tc");
    assertThat(unescape(stringElement("fR'a\\tc'"))).isEqualTo("a\\tc");
    assertThat(unescape(stringElement("f'name:\\n{name}'"))).isEqualTo("name:\n{name}");
    assertThat(unescape(stringElement("F'{name}'"))).isEqualTo("{name}");
    assertThat(unescape(stringElement("u'abc'"))).isEqualTo("abc");
    assertThat(unescape(stringElement("b'\\x48'"))).isEqualTo("H");
    assertThat(unescape(stringElement("br'\\x48'"))).isEqualTo("\\x48");
    assertThat(unescape(stringElement("rb'\\x48'"))).isEqualTo("\\x48");
  }

  @Test
  public void unescape_string_element_containing_ignored_line_beak() {
    // linux
    assertThat(unescape(stringElement("'a\\\nb'"))).isEqualTo("ab");
    // windows
    assertThat(unescape(stringElement("'a\\\r\nb'"))).isEqualTo("ab");
    // mac
    assertThat(unescape(stringElement("'a\\\r'"))).isEqualTo("a");
    assertThat(unescape(stringElement("'a\\\rb'"))).isEqualTo("ab");
  }

  @Test
  public void unescape_string_element_backslash_and_quotes() {
    assertThat(unescape(stringElement("'\\\\'"))).isEqualTo("\\");
    assertThat(unescape(stringElement("'0\\\\'"))).isEqualTo("0\\");
    assertThat(unescape(stringElement("'\\\\0'"))).isEqualTo("\\0");
    assertThat(unescape(stringElement("'\\'\\\"\\''"))).isEqualTo("'\"'");
  }

  @Test
  public void unescape_string_element_named_escape_sequences() {
    assertThat(unescape(stringElement("'\\a'"))).isEqualTo("\u0007");
    assertThat(unescape(stringElement("'\\b'"))).isEqualTo("\u0008");
    assertThat(unescape(stringElement("'\\f'"))).isEqualTo("\u000C");
    assertThat(unescape(stringElement("'\\n'"))).isEqualTo("\n");
    assertThat(unescape(stringElement("'\\r'"))).isEqualTo("\r");
    assertThat(unescape(stringElement("'\\t'"))).isEqualTo("\t");
    assertThat(unescape(stringElement("'\\v'"))).isEqualTo("\u000B");
  }

  @Test
  public void unescape_string_element_python_string_with_x_u_U_escape_sequences() {
    assertThat(unescape(stringElement("'\\x48\\u0065\\U0000006c\\x6c\\x6f\\x20\\x57\\x6f\\x72\\x6c\\x64\\x21'")))
      .isEqualTo("Hello World!");
  }

  @Test
  public void unescape_string_element_python_bytes_with_x_u_U_escape_sequences() {
    assertThat(unescape(stringElement("b'\\x48\\u0065\\U0000006c\\x6c'"))).isEqualTo("H\\u0065\\U0000006cl");
  }

  @Test
  public void unescape_string_element_various_octal_length() {
    assertThat(unescape(stringElement("'\\0'"))).isEqualTo("\u0000");
    assertThat(unescape(stringElement("'\\1'"))).isEqualTo("\u0001");
    assertThat(unescape(stringElement("'\\7'"))).isEqualTo("\u0007");
    assertThat(unescape(stringElement("'\\10'"))).isEqualTo("\u0008");
    assertThat(unescape(stringElement("'a\\777b'"))).isEqualTo("a\u01FFb");
    assertThat(unescape(stringElement("'a\\7777b'"))).isEqualTo("a\u01FF7b");
    assertThat(unescape(stringElement("'a\\78b'"))).isEqualTo("a\u00078b");
    assertThat(unescape(stringElement("'\\o12'"))).isEqualTo("\n");
    assertThat(unescape(stringElement("'\\o5 \\o6'"))).isEqualTo("\u0005 \u0006");
  }

  @Test
  public void unescape_string_element_unsupported() {
    assertThat(unescape(stringElement("'\\N{LATIN CAPITAL LETTER A}'"))).isEqualTo("\\N{LATIN CAPITAL LETTER A}");
  }

  @Test
  public void unescape_string_element_invalid_escape_sequences() {
    assertThat(unescapeString("\\", true)).isEqualTo("\\");
    assertThat(unescape(stringElement("'\\o'"))).isEqualTo("\\o");
    assertThat(unescape(stringElement("'\\z'"))).isEqualTo("\\z");
    assertThat(unescape(stringElement("'\\X00'"))).isEqualTo("\\X00");
    assertThat(unescape(stringElement("'\\x0'"))).isEqualTo("\\x0");
    assertThat(unescape(stringElement("'\\xZZ\\x04'"))).isEqualTo("\\xZZ\u0004");
    assertThat(unescape(stringElement("'\\u000'"))).isEqualTo("\\u000");
    assertThat(unescape(stringElement("'\\U0000000'"))).isEqualTo("\\U0000000");

    // Python error: f-string expression part cannot include a backslash
    assertThat(unescape(stringElement("f'name:\\n{na\\\nme}'"))).isEqualTo("name:\n{name}");
  }

  private StringElement stringElement(String source) {
    return stringLiteral(source).stringElements().get(0);
  }

  private StringLiteral stringLiteral(String source) {
    return (StringLiteral) exp(source);
  }

  private Expression exp(String code) {
    return exp(parse(code));
  }

  private Expression exp(Tree tree) {
    if (tree instanceof Expression) {
      return (Expression) tree;
    }
    for (Tree child : tree.children()) {
      Expression exp = exp(child);
      if (exp != null) {
        return exp;
      }
    }
    return null;
  }

  private FileInput parse(String code) {
    return new PythonTreeMaker().fileInput(parser.parse(code));
  }

  @Test
  public void singleAssignedValue() {
    assertThat(lastNameValue("x = 42; x").getKind()).isEqualTo(Kind.NUMERIC_LITERAL);
    assertThat(lastNameValue("x = ''; x").getKind()).isEqualTo(Kind.STRING_LITERAL);
    assertThat(lastNameValue("(x, y) = (42, 43); x")).isNull();
    assertThat(lastNameValue("x = 42; import x; x")).isNull();
    assertThat(lastNameValue("x = 42; x = 43; x")).isNull();
    assertThat(lastNameValue("x = 42; y")).isNull();
  }

  private Expression lastNameValue(String code) {
    FileInput root = parse(code);
    new SymbolTableBuilder(null).visitFileInput(root);
    NameVisitor nameVisitor = new NameVisitor();
    root.accept(nameVisitor);
    List<Name> names = nameVisitor.names;
    return Expressions.singleAssignedValue(names.get(names.size() - 1));
  }

  private static class NameVisitor extends BaseTreeVisitor {
    private List<Name> names = new ArrayList<>();

    @Override
    public void visitName(Name pyNameTree) {
      names.add(pyNameTree);
    }
  }

}
