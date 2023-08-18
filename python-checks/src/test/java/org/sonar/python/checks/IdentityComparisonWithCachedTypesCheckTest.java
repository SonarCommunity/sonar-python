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

import org.junit.jupiter.api.Test;
import org.sonar.python.checks.quickfix.PythonQuickFixVerifier;
import org.sonar.python.checks.utils.PythonCheckVerifier;

class IdentityComparisonWithCachedTypesCheckTest {

  @Test
  void test() {
    PythonCheckVerifier.verify(
      "src/test/resources/checks/identityComparisonWithCachedTypes.py",
      new IdentityComparisonWithCachedTypesCheck());
  }

  @Test
  void testIsReplacementQuickfix() {
    var check = new IdentityComparisonWithCachedTypesCheck();
    String codeWithIssue = "def literal_comparison(param):\n" +
      "    3000 is param";
    String codeFixed = "def literal_comparison(param):\n" +
      "    3000 == param";
    PythonQuickFixVerifier.verify(check, codeWithIssue, codeFixed);
    PythonQuickFixVerifier.verifyQuickFixMessages(check, codeWithIssue, IdentityComparisonWithCachedTypesCheck.IS_QUICK_FIX_MESSAGE);
  }

  @Test
  void testIsNotReplacementQuickfix() {
    var check = new IdentityComparisonWithCachedTypesCheck();
    String codeWithIssue = "def literal_comparison(param):\n" +
      "    3000 is not param";
    String codeFixed = "def literal_comparison(param):\n" +
      "    3000 != param";
    PythonQuickFixVerifier.verify(check, codeWithIssue, codeFixed);
    PythonQuickFixVerifier.verifyQuickFixMessages(check, codeWithIssue, IdentityComparisonWithCachedTypesCheck.IS_NOT_QUICK_FIX_MESSAGE);
  }

}
