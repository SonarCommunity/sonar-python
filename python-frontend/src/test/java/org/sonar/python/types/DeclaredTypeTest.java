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
package org.sonar.python.types;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.sonar.plugins.python.api.symbols.AmbiguousSymbol;
import org.sonar.plugins.python.api.symbols.ClassSymbol;
import org.sonar.plugins.python.api.symbols.Symbol;
import org.sonar.plugins.python.api.tree.ClassDef;
import org.sonar.plugins.python.api.tree.FileInput;
import org.sonar.plugins.python.api.tree.Statement;
import org.sonar.python.semantic.AmbiguousSymbolImpl;
import org.sonar.python.semantic.ClassSymbolImpl;
import org.sonar.python.semantic.SymbolImpl;
import org.sonar.python.semantic.SymbolTableBuilder;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonar.python.PythonTestUtils.lastExpression;
import static org.sonar.python.PythonTestUtils.parse;
import static org.sonar.python.PythonTestUtils.pythonFile;
import static org.sonar.python.types.DeclaredType.fromInferredType;
import static org.sonar.python.types.InferredTypes.DECL_INT;
import static org.sonar.python.types.InferredTypes.DECL_LIST;
import static org.sonar.python.types.InferredTypes.INT;
import static org.sonar.python.types.InferredTypes.STR;
import static org.sonar.python.types.InferredTypes.anyType;
import static org.sonar.python.types.InferredTypes.or;

public class DeclaredTypeTest {

  private final ClassSymbolImpl a = new ClassSymbolImpl("a", "a");
  private final ClassSymbolImpl b = new ClassSymbolImpl("b", "b");
  private final ClassSymbolImpl c = new ClassSymbolImpl("c", "c");

  @Test
  public void isIdentityComparableWith() {
    DeclaredType aType = new DeclaredType(a);
    DeclaredType bType = new DeclaredType(b);
    DeclaredType cType = new DeclaredType(c);

    assertThat(aType.isIdentityComparableWith(bType)).isTrue();
    assertThat(aType.isIdentityComparableWith(aType)).isTrue();
    assertThat(aType.isIdentityComparableWith(new RuntimeType(a))).isTrue();

    assertThat(aType.isIdentityComparableWith(AnyType.ANY)).isTrue();

    assertThat(aType.isIdentityComparableWith(or(aType, bType))).isTrue();
    assertThat(aType.isIdentityComparableWith(or(cType, bType))).isTrue();
  }

  @Test
  public void member() {
    ClassSymbolImpl x = new ClassSymbolImpl("x", "x");
    SymbolImpl foo = new SymbolImpl("foo", null);
    x.addMembers(singletonList(foo));
    assertThat(new DeclaredType(x).canHaveMember("foo")).isTrue();
    assertThat(new DeclaredType(x).canHaveMember("bar")).isTrue();
    assertThat(new DeclaredType(x).resolveMember("foo")).isEmpty();
    assertThat(new DeclaredType(x).resolveMember("bar")).isEmpty();

    ClassSymbol classSymbol = lastClassSymbol(
      "class C:",
      "  def foo(): ..."
    );
    DeclaredType declaredType = new DeclaredType(classSymbol);
    assertThat(declaredType.declaresMember("foo")).isTrue();
    assertThat(declaredType.declaresMember("bar")).isFalse();

    DeclaredType emptyUnion = new DeclaredType(new SymbolImpl("Union", "typing.Union"));
    assertThat(emptyUnion.declaresMember("foo")).isTrue();

    classSymbol = lastClassSymbol(
      "class Base:",
      "  def bar(): ...",
      "class C(Base):",
      "  def foo(): ..."
    );
    declaredType = new DeclaredType(classSymbol);
    assertThat(declaredType.declaresMember("foo")).isTrue();
    assertThat(declaredType.declaresMember("bar")).isTrue();
    assertThat(declaredType.declaresMember("other")).isFalse();

    assertThat(new DeclaredType(new SymbolImpl("x", "foo.x")).declaresMember("member")).isTrue();
  }

  @Test
  public void test_toString() {
    assertThat(new DeclaredType(a)).hasToString("DeclaredType(a)");
    assertThat(new DeclaredType(a, Collections.singletonList(new DeclaredType(b)))).hasToString("DeclaredType(a[b])");
    assertThat(new DeclaredType(a, Arrays.asList(new DeclaredType(b), new DeclaredType(c)))).hasToString("DeclaredType(a[b, c])");
  }

  @Test
  public void test_canOnlyBe() {
    assertThat(new DeclaredType(a).canOnlyBe("a")).isFalse();
    assertThat(new DeclaredType(b).canOnlyBe("a")).isFalse();
  }

  @Test
  public void test_canBeOrExtend() {
    ClassSymbolImpl x = new ClassSymbolImpl("x", "x");
    assertThat(new DeclaredType(x).canBeOrExtend("x")).isTrue();
    assertThat(new DeclaredType(x).canBeOrExtend("y")).isTrue();
  }

  @Test
  public void test_isCompatibleWith() {
    ClassSymbol x1 = lastClassSymbol(
      "class X1:",
      "  def foo(): ..."
    );
    ClassSymbol x2 = lastClassSymbol(
      "class X2:",
      "  def bar(): ..."
    );
    ((ClassSymbolImpl) x2).addSuperClass(x1);

    assertThat(new DeclaredType(x2).isCompatibleWith(new DeclaredType(x1))).isTrue();
    assertThat(new DeclaredType(x1).isCompatibleWith(new DeclaredType(x1))).isTrue();
    assertThat(new DeclaredType(x1).isCompatibleWith(new DeclaredType(x2))).isFalse();
    assertThat(new DeclaredType(x1).isCompatibleWith(INT)).isFalse();
    DeclaredType emptyUnion = new DeclaredType(new SymbolImpl("Union", "typing.Union"));
    assertThat(emptyUnion.isCompatibleWith(INT)).isTrue();
  }

  @Test
  public void test_mustBeOrExtend() {
    ClassSymbolImpl x1 = new ClassSymbolImpl("x1", "x1");
    ClassSymbolImpl x2 = new ClassSymbolImpl("x2", "x2");
    x2.addSuperClass(x1);

    DeclaredType typeX1 = new DeclaredType(x1);
    DeclaredType typeX2 = new DeclaredType(x2);

    assertThat(typeX1.mustBeOrExtend("x1")).isTrue();
    assertThat(typeX1.mustBeOrExtend("x2")).isFalse();

    assertThat(typeX2.mustBeOrExtend("x1")).isTrue();
    assertThat(typeX2.mustBeOrExtend("x2")).isTrue();

    ClassSymbolImpl otherX1 = new ClassSymbolImpl("x1", "x1");
    Set<Symbol> symbols = new HashSet<>(Arrays.asList(x1, otherX1));
    AmbiguousSymbol ambiguousSymbol = new AmbiguousSymbolImpl("x1", "x1", symbols);
    DeclaredType typeAmbiguousX1 = new DeclaredType(ambiguousSymbol);
    assertThat(typeAmbiguousX1.mustBeOrExtend("x1")).isTrue();
    assertThat(typeAmbiguousX1.mustBeOrExtend("other")).isFalse();

    DeclaredType declaredType = new DeclaredType(new SymbolImpl("C", "foo.C"));
    assertThat(declaredType.mustBeOrExtend("other")).isTrue();
    assertThat(declaredType.mustBeOrExtend("foo.C")).isTrue();
    assertThat(declaredType.mustBeOrExtend("C")).isTrue();
  }

  @Test
  public void test_getClass() {
    ClassSymbolImpl x1 = new ClassSymbolImpl("x1", "x1");
    assertThat(new DeclaredType(x1).getTypeClass()).isEqualTo(x1);
  }

  @Test
  public void test_equals() {
    DeclaredType aType = new DeclaredType(a);
    assertThat(aType)
      .isEqualTo(aType)
      .isEqualTo(new DeclaredType(a))
      .isNotEqualTo(new DeclaredType(b))
      .isNotEqualTo(a)
      .isNotEqualTo(null)
      .isNotEqualTo(new DeclaredType(a, Arrays.asList(new DeclaredType(b), new DeclaredType(c))))
      .isEqualTo(new DeclaredType(new SymbolImpl("a", "a")))
      .isNotEqualTo(new DeclaredType(new SymbolImpl("a", "b")));

    DeclaredType x = new DeclaredType(new ClassSymbolImpl("X", null));
    DeclaredType y = new DeclaredType(new ClassSymbolImpl("Y", null));
    assertThat(x).isNotEqualTo(y);
  }

  @Test
  public void test_hashCode() {
    DeclaredType aType = new DeclaredType(a);
    assertThat(aType.hashCode()).isEqualTo(new DeclaredType(a).hashCode());
    assertThat(aType.hashCode()).isNotEqualTo(new DeclaredType(b).hashCode());
    assertThat(aType.hashCode()).isNotEqualTo(new DeclaredType(a, Arrays.asList(new DeclaredType(b), new DeclaredType(c))).hashCode());

    DeclaredType x = new DeclaredType(new ClassSymbolImpl("X", null));
    DeclaredType y = new DeclaredType(new ClassSymbolImpl("Y", null));
    assertThat(x.hashCode()).isNotEqualTo(y.hashCode());
  }

  @Test
  public void test_fromInferredType() {
    assertThat(fromInferredType(anyType())).isEqualTo(anyType());
    assertThat(fromInferredType(INT)).isEqualTo(DECL_INT);
    assertThat(fromInferredType(DECL_INT)).isEqualTo(DECL_INT);
    assertThat(fromInferredType(or(INT, STR))).isEqualTo(anyType());
  }

  @Test
  public void test_resolveDeclaredMember() {
    ClassSymbolImpl typeClassX = new ClassSymbolImpl("x", "x");
    SymbolImpl fooX = new SymbolImpl("foo", "x.foo");
    typeClassX.addMembers(Collections.singletonList(fooX));
    DeclaredType declaredTypeX = new DeclaredType(typeClassX);
    assertThat(declaredTypeX.resolveDeclaredMember("foo")).contains(fooX);
    assertThat(declaredTypeX.resolveDeclaredMember("bar")).isEmpty();

    ClassSymbolImpl typeClassY = new ClassSymbolImpl("y", "y");
    SymbolImpl fooY = new SymbolImpl("foo", "y.foo");
    typeClassY.addMembers(Collections.singletonList(fooY));
    DeclaredType declaredTypeY = new DeclaredType(typeClassY);
    DeclaredType union = new DeclaredType(new SymbolImpl("Union", "typing.Union"), Arrays.asList(declaredTypeX, declaredTypeY));
    assertThat(union.resolveDeclaredMember("foo")).isEmpty();
    assertThat(union.resolveDeclaredMember("bar")).isEmpty();

    DeclaredType unresolved = new DeclaredType(new SymbolImpl("unresolved", "unresolved"));
    union = new DeclaredType(new SymbolImpl("Union", "typing.Union"), Arrays.asList(declaredTypeX, unresolved));
    assertThat(union.resolveDeclaredMember("foo")).isEmpty();
  }

  @Test
  public void test_hasUnresolvedHierarchy() {
    ClassSymbolImpl typeClassX = new ClassSymbolImpl("x", "x");
    DeclaredType declaredTypeX = new DeclaredType(typeClassX);
    assertThat(declaredTypeX.hasUnresolvedHierarchy()).isFalse();
    DeclaredType union = new DeclaredType(new SymbolImpl("Union", "typing.Union"));
    assertThat(union.hasUnresolvedHierarchy()).isTrue();
    DeclaredType unresolved = new DeclaredType(new SymbolImpl("unresolved", "unresolved"));
    union = new DeclaredType(new SymbolImpl("Union", "typing.Union"), Arrays.asList(declaredTypeX, unresolved));
    assertThat(union.hasUnresolvedHierarchy()).isTrue();
  }

  @Test
  public void test_generic_collections() {
    assertThat(lastExpression(
      "def f(x: list):",
      "  x"
    ).type()).isEqualTo(DECL_LIST);

    assertThat(((DeclaredType) lastExpression(
      "def f(x: list[int]):",
      "  x"
    ).type()).alternativeTypeSymbols()).extracting(Symbol::fullyQualifiedName).containsExactly("list");
  }

  private static ClassSymbol lastClassSymbol(String... code) {
    FileInput fileInput = parse(new SymbolTableBuilder("my_package", pythonFile("my_module.py")), code);
    List<Statement> statements = fileInput.statements().statements();
    ClassDef classDef = (ClassDef) statements.get(statements.size() - 1);
    return (ClassSymbol) classDef.name().symbol();
  }
}
