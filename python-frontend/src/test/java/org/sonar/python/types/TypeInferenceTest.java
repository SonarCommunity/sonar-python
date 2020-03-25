/*
 * SonarQube Python Plugin
 * Copyright (C) 2011-2020 SonarSource SA
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
package org.sonar.python.types;

import org.junit.Test;
import org.sonar.plugins.python.api.symbols.ClassSymbol;
import org.sonar.plugins.python.api.tree.CallExpression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonar.python.PythonTestUtils.lastExpression;
import static org.sonar.python.PythonTestUtils.lastExpressionInFunction;
import static org.sonar.python.types.InferredTypes.BOOL;
import static org.sonar.python.types.InferredTypes.COMPLEX;
import static org.sonar.python.types.InferredTypes.DICT;
import static org.sonar.python.types.InferredTypes.FLOAT;
import static org.sonar.python.types.InferredTypes.INT;
import static org.sonar.python.types.InferredTypes.LIST;
import static org.sonar.python.types.InferredTypes.NONE;
import static org.sonar.python.types.InferredTypes.SET;
import static org.sonar.python.types.InferredTypes.STR;
import static org.sonar.python.types.InferredTypes.TUPLE;
import static org.sonar.python.types.InferredTypes.anyType;
import static org.sonar.python.types.InferredTypes.or;
import static org.sonar.python.types.InferredTypes.runtimeType;

//import static org.sonar.python.types.InferredTypes.BYTES;

public class TypeInferenceTest {

  @Test
  public void unknown_expression_type() {
    assertThat(lastExpression("a.b").type()).isEqualTo(anyType());
    assertThat(lastExpression("a[0]").type()).isEqualTo(anyType());
  }

  @Test
  public void call_expression() {
    assertThat(lastExpression(
      "f()").type()).isEqualTo(anyType());
    assertThat(lastExpression(
      "def f(): pass",
      "f()").type()).isEqualTo(anyType());

    CallExpression expression = (CallExpression) lastExpression(
      "class A: pass",
      "A()");
    assertThat(expression.calleeSymbol().fullyQualifiedName()).isEqualTo("mod1.A");
    assertThat(expression.type()).isEqualTo(runtimeType((ClassSymbol) expression.calleeSymbol()));
  }

  @Test
  public void variable_outside_function() {
    assertThat(lastExpression("a = 42; a").type()).isEqualTo(anyType());
  }

  @Test
  public void parameter() {
    assertThat(lastExpression(
      "def f(p):",
      "  p = 42",
      "  p").type()).isEqualTo(anyType());

    assertThat(lastExpression(
      "def f(p):",
      "  a = p",
      "  if cond:",
      "    a = 42",
      "  a").type()).isEqualTo(anyType());
  }

  @Test
  public void local_variable() {
    assertThat(lastExpressionInFunction(
      "a = 42",
      "a").type()).isEqualTo(INT);
  }

  @Test
  public void global_variable() {
    assertThat(lastExpressionInFunction(
      "global a",
      "a = 42",
      "a").type()).isEqualTo(anyType());
  }

  @Test
  public void simple_propagation_between_variables() {
    assertThat(lastExpressionInFunction(
      "a = ''",
      "b = a",
      "c = b",
      "c").type()).isEqualTo(STR);
  }

  @Test
  public void variable_read_appearing_before_initialization() {
    assertThat(lastExpressionInFunction(
      "for i in range(3):",
      "  if i > 0: a = b",
      "  else:     b = 1",
      "a").type()).isEqualTo(INT);
  }

  @Test
  public void cycle_between_variables_with_initialization() {
    assertThat(lastExpressionInFunction(
      "for i in range(3):",
      "  if i > 1:  b = a",
      "  elif i==1: a = b",
      "  else:      b = 1",
      "a").type()).isEqualTo(INT);
  }

  @Test
  public void unresolvable_cycle_between_variables() {
    assertThat(lastExpressionInFunction(
      "if cond: a = b",
      "else:    b = a",
      "c = 1",
      "a").type()).isEqualTo(anyType());
  }

  @Test
  public void unsupported_assignment() {
    assertThat(lastExpressionInFunction(
      "(a, b) = foo()",
      "a").type()).isEqualTo(anyType());

    assertThat(lastExpressionInFunction(
      "(a, b) = foo()",
      "a = ''",
      "a").type()).isEqualTo(anyType());

    assertThat(lastExpressionInFunction(
      "(a, b) = foo()",
      "a = ''",
      "c = a",
      "c").type()).isEqualTo(anyType());

    assertThat(lastExpressionInFunction(
      "c = 42",
      "if cond: a, b = foo()",
      "else:    a = c",
      "d = a",
      "d").type()).isEqualTo(anyType());
  }

  @Test
  public void propagate_return_type_to_variable() {
    assertThat(lastExpressionInFunction(
      "a = 'abc'.capitalize()",
      "a").type()).isEqualTo(STR);

    assertThat(lastExpressionInFunction(
      "for i in range(3):",
      "  if i > 0: b = a.capitalize()",
      "  else:     a = 'abc'",
      "b").type()).isEqualTo(STR);

    assertThat(lastExpressionInFunction(
      "for i in range(3):",
      "  if i > 0: b = a.capitalize().upper()",
      "  else:     a = 'abc'",
      "b").type()).isEqualTo(STR);

    assertThat(lastExpressionInFunction(
      "if cond:  a = 'abc'",
      "else:     a = x.foo()",
      "b = a.capitalize()",
      "b").type()).isEqualTo(anyType());

    assertThat(lastExpressionInFunction(
      "global a",
      "for i in range(3):",
      "  if i > 0: b = a.capitalize()",
      "  else:     a = 'abc'",
      "b").type()).isEqualTo(anyType());
  }

  @Test
  public void multiple_types() {
    assertThat(lastExpressionInFunction(
      "if cond: a = ''",
      "else:    a = 42",
      "a").type()).isEqualTo(or(STR, INT));
  }

  @Test
  public void numeric_literals() {
    assertThat(lastExpression("42").type()).isEqualTo(INT);
    assertThat(lastExpression("42_3").type()).isEqualTo(INT);
    assertThat(lastExpression("0b101").type()).isEqualTo(INT);
    assertThat(lastExpression("0x1F").type()).isEqualTo(INT);
    assertThat(lastExpression("42.0").type()).isEqualTo(FLOAT);
    assertThat(lastExpression("1e100").type()).isEqualTo(FLOAT);
    assertThat(lastExpression("1E100").type()).isEqualTo(FLOAT);
    assertThat(lastExpression("42j").type()).isEqualTo(COMPLEX);
    assertThat(lastExpression("42.0j").type()).isEqualTo(COMPLEX);
  }

  @Test
  public void string_literals() {
    assertThat(lastExpression("'hello world'").type()).isEqualTo(STR);
    assertThat(lastExpression("f'hello world'").type()).isEqualTo(STR);
    assertThat(lastExpression("'hello' 'world'").type()).isEqualTo(STR);

    assertThat(lastExpression("b'hello'").type()).isEqualTo(anyType());
    assertThat(lastExpression("rb'hello'").type()).isEqualTo(anyType());

    // this throws a 'SyntaxError: cannot mix bytes and nonbytes literals'
    assertThat(lastExpression("b'hello' 'world'").type()).isEqualTo(STR);
  }

  @Test
  public void list_literals() {
    assertThat(lastExpression("[]").type()).isEqualTo(LIST);
    assertThat(lastExpression("[42]").type()).isEqualTo(LIST);

    assertThat(lastExpression("[x for x in range(0, 100)]").type()).isEqualTo(LIST);
  }

  @Test
  public void dict_literals() {
    assertThat(lastExpression("{}").type()).isEqualTo(DICT);
    assertThat(lastExpression("{'x' : 1, 'y' : 2}").type()).isEqualTo(DICT);

    assertThat(lastExpression("{ k:v for (k, v) in foo }").type()).isEqualTo(DICT);
  }

  @Test
  public void set_literals() {
    assertThat(lastExpression("{1, 2, 3}").type()).isEqualTo(SET);

    assertThat(lastExpression("{ v for v in foo }").type()).isEqualTo(SET);
  }

  @Test
  public void generator_literal() {
    assertThat(lastExpression("(v for v in foo)").type()).isEqualTo(anyType());
  }

  @Test
  public void tuple_literal() {
    assertThat(lastExpression("()").type()).isEqualTo(TUPLE);
    assertThat(lastExpression("(1, 2)").type()).isEqualTo(TUPLE);
  }

  @Test
  public void none_type() {
    assertThat(lastExpression("None").type()).isEqualTo(NONE);
  }

  @Test
  public void true_false_literal() {
    assertThat(lastExpression("True").type()).isEqualTo(BOOL);
    assertThat(lastExpression("False").type()).isEqualTo(BOOL);
  }

  @Test
  public void builtin_function_types() {
    assertThat(lastExpression("all([1, 2, 3])").type()).isEqualTo(BOOL);
    assertThat(lastExpression("round(42)").type()).isEqualTo(AnyType.ANY);
    assertThat(lastExpression("range(42)").type()).isEqualTo(AnyType.ANY);
    assertThat(lastExpression("getattr(42)").type()).isEqualTo(AnyType.ANY);
  }

  @Test
  public void builtin_method_types() {
    assertThat(lastExpression("'abc'.capitalize()").type()).isEqualTo(STR);
    assertThat(lastExpression("list().copy()").type()).isEqualTo(LIST);
  }

  @Test
  public void conditional_expressions() {
    assertThat(lastExpression("42 if '' else 43").type()).isEqualTo(INT);
    assertThat(lastExpression("42 if cond else ''").type()).isEqualTo(or(INT, STR));
    assertThat(lastExpression("42 if '' else xxx").type()).isEqualTo(anyType());
    assertThat(lastExpression("42 if cond1 else True if cond2 else ''").type()).isEqualTo(or(or(INT, STR), BOOL));

    assertThat(lastExpressionInFunction(
      "for i in range(3):",
      "  if   i > 1: c = b",
      "  elif i > 0: b = 42 if cond else a",
      "  else:       a = ''",
      "c").type()).isEqualTo(or(INT, STR));

    assertThat(lastExpressionInFunction(
      "c = 42 if '' else c",
      "c").type()).isEqualTo(anyType());
  }
}
