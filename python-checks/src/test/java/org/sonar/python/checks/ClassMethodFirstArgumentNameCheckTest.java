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
package org.sonar.python.checks;

import org.junit.Test;
import org.sonar.python.checks.quickfix.PythonQuickFixVerifier;
import org.sonar.python.checks.utils.PythonCheckVerifier;

import static org.sonar.python.checks.utils.CodeTestUtils.code;

public class ClassMethodFirstArgumentNameCheckTest {

  @Test
  public void testRule() {
    PythonCheckVerifier.verify("src/test/resources/checks/classMethodFirstArgumentNameCheck.py", new ClassMethodFirstArgumentNameCheck());
  }

  @Test
  public void testQuickFix() {
    String codeWithIssue = code(
      "class A():",
      "    @classmethod",
      "    def long_function_name(bob, alice):",
      "        print(bob)");
    String codeFixed1 = code(
      "class A():",
      "    @classmethod",
      "    def long_function_name(cls, bob, alice):",
      "        print(bob)");
    String codeFixed2 = code(
      "class A():",
      "    @classmethod",
      "    def long_function_name(cls, alice):",
      "        print(cls)");

    PythonQuickFixVerifier.verify(new ClassMethodFirstArgumentNameCheck(), codeWithIssue, codeFixed1, codeFixed2);
  }

  @Test
  public void testQuickFixMultiline() {
    String codeWithIssue = code(
      "class A():",
      "    @classmethod",
      "    def long_function_name(bob,",
      "            alice):",
      "        print(bob)");
    String codeFixed1 = code(
      "class A():",
      "    @classmethod",
      "    def long_function_name(cls,",
      "            bob,",
      "            alice):",
      "        print(bob)");
    String codeFixed2 = code(
      "class A():",
      "    @classmethod",
      "    def long_function_name(cls,",
      "            alice):",
      "        print(cls)");

    PythonQuickFixVerifier.verify(new ClassMethodFirstArgumentNameCheck(), codeWithIssue, codeFixed1, codeFixed2);
  }

  @Test
  public void testQuickFixMultiline2() {
    String codeWithIssue = code(
      "class A():",
      "    @classmethod",
      "    def long_function_name(",
      "            bob,",
      "            alice):",
      "        print(bob)");
    String codeFixed1 = code(
      "class A():",
      "    @classmethod",
      "    def long_function_name(",
      "            cls,",
      "            bob,",
      "            alice):",
      "        print(bob)");
    String codeFixed2 = code(
      "class A():",
      "    @classmethod",
      "    def long_function_name(",
      "            cls,",
      "            alice):",
      "        print(cls)");

    PythonQuickFixVerifier.verify(new ClassMethodFirstArgumentNameCheck(), codeWithIssue, codeFixed1, codeFixed2);
  }

  @Test
  public void testQuickFixMultiline3() {
    String codeWithIssue = code(
      "class A():",
      "    @classmethod",
      "    def long_function_name(",
      "            bob, alice",
      "    ):",
      "        print(bob)");
    String codeFixed1 = code(
      "class A():",
      "    @classmethod",
      "    def long_function_name(",
      "            cls, bob, alice",
      "    ):",
      "        print(bob)");
    String codeFixed2 = code(
      "class A():",
      "    @classmethod",
      "    def long_function_name(",
      "            cls, alice",
      "    ):",
      "        print(cls)");

    PythonQuickFixVerifier.verify(new ClassMethodFirstArgumentNameCheck(), codeWithIssue, codeFixed1, codeFixed2);
  }

  @Test
  public void testQuickFixMultiline4() {
    String codeWithIssue = code(
      "class A():",
      "    @classmethod",
      "    def long_function_name(",
      "            bob",
      "    ):",
      "        print(bob)");
    String codeFixed1 = code(
      "class A():",
      "    @classmethod",
      "    def long_function_name(",
      "            cls, bob",
      "    ):",
      "        print(bob)");
    String codeFixed2 = code(
      "class A():",
      "    @classmethod",
      "    def long_function_name(",
      "            cls",
      "    ):",
      "        print(cls)");

    PythonQuickFixVerifier.verify(new ClassMethodFirstArgumentNameCheck(), codeWithIssue, codeFixed1, codeFixed2);
  }

  @Test
  public void testQuickFixMultilineCustomClassParameterNames() {
    ClassMethodFirstArgumentNameCheck check = new ClassMethodFirstArgumentNameCheck();
    check.classParameterNames = "xxx";
    String codeWithIssue = code(
      "class A():",
      "    @classmethod",
      "    def long_function_name(",
      "            bob, alice",
      "    ):",
      "        print(bob)");
    String codeFixed1 = code(
      "class A():",
      "    @classmethod",
      "    def long_function_name(",
      "            xxx, bob, alice",
      "    ):",
      "        print(bob)");
    String codeFixed2 = code(
      "class A():",
      "    @classmethod",
      "    def long_function_name(",
      "            xxx, alice",
      "    ):",
      "        print(xxx)");

    PythonQuickFixVerifier.verify(check, codeWithIssue, codeFixed1, codeFixed2);
  }

  @Test
  public void testQuickFixMultilineEmptyClassParameterNames() {
    ClassMethodFirstArgumentNameCheck check = new ClassMethodFirstArgumentNameCheck();
    check.classParameterNames = "";
    String codeWithIssue = code(
      "class A():",
      "    @classmethod",
      "    def long_function_name(",
      "            bob, alice",
      "    ):",
      "        print(bob)");
    String codeFixed1 = code(
      "class A():",
      "    @classmethod",
      "    def long_function_name(",
      "            cls, bob, alice",
      "    ):",
      "        print(bob)");
    String codeFixed2 = code(
      "class A():",
      "    @classmethod",
      "    def long_function_name(",
      "            cls, alice",
      "    ):",
      "        print(cls)");

    PythonQuickFixVerifier.verify(check, codeWithIssue, codeFixed1, codeFixed2);
  }

  @Test
  public void testQuickFixDescriptions() {
    ClassMethodFirstArgumentNameCheck check = new ClassMethodFirstArgumentNameCheck();
    check.classParameterNames = "xxx";
    String codeWithIssue = code(
      "class A():",
      "    @classmethod",
      "    def long_function_name(",
      "            bob, alice",
      "    ):",
      "        print(bob)");

    PythonQuickFixVerifier.verifyQuickFixMessages(check, codeWithIssue,
      "Add 'xxx' as the first argument.",
      "Rename 'bob' to 'xxx'");
  }
}
