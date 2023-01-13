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
import org.sonar.plugins.python.api.PythonCheck;
import org.sonar.python.checks.quickfix.PythonQuickFixVerifier;
import org.sonar.python.checks.utils.PythonCheckVerifier;

public class ModifiedParameterValueCheckTest {

  private final PythonCheck check = new ModifiedParameterValueCheck();
  @Test
  public void test() {
    PythonCheckVerifier.verify("src/test/resources/checks/modifiedParameterValue.py", check);
  }

  @Test
  public void list_modified_quickfix() {
    String codeWithIssue = "" +
      "def list_modified(param=list()):\n" +
      "    param.append('a')";

    String fixedCode = "" +
      "def list_modified(param=None):\n" +
      "    if param is None:\n" +
      "        param = list()\n" +
      "    param.append('a')";

    PythonQuickFixVerifier.verify(check, codeWithIssue, fixedCode);
  }

  @Test
  public void method_with_quickfix() {
    String codeWithIssue = "" +
      "class Foo:\n" +
      "    def list_modified(param=list()):\n" +
      "        param.append('a')";

    String fixedCode = "" +
      "class Foo:\n" +
      "    def list_modified(param=None):\n" +
      "        if param is None:\n" +
      "            param = list()\n" +
      "        param.append('a')";

    PythonQuickFixVerifier.verify(check, codeWithIssue, fixedCode);
  }

  @Test
  public void default_with_spaces_quickfix() {
    String codeWithIssue = "" +
      "def list_modified(param = list()):\n" +
      "    param.append('a')";

    String fixedCode = "" +
      "def list_modified(param = None):\n" +
      "    if param is None:\n" +
      "        param = list()\n" +
      "    param.append('a')";

    PythonQuickFixVerifier.verify(check, codeWithIssue, fixedCode);
  }

  @Test
  public void annotated_parameter_quickfix() {
    String codeWithIssue = "" +
      "def list_modified(param:list=list()):\n" +
      "    param.append('a')";

    String fixedCode = "" +
      "def list_modified(param:list=None):\n" +
      "    if param is None:\n" +
      "        param = list()\n" +
      "    param.append('a')";

    PythonQuickFixVerifier.verify(check, codeWithIssue, fixedCode);
  }

  @Test
  public void annotated_parameter_with_space_quickfix() {
    String codeWithIssue = "" +
      "def list_modified(param: list = list()):\n" +
      "    param.append('a')";

    String fixedCode = "" +
      "def list_modified(param: list = None):\n" +
      "    if param is None:\n" +
      "        param = list()\n" +
      "    param.append('a')";

    PythonQuickFixVerifier.verify(check, codeWithIssue, fixedCode);
  }

  @Test
  public void set_modified_quickfix() {
    String codeWithIssue = "" +
      "def set_modified(param=set()):\n" +
      "    param.add('a')";

    String fixedCode = "" +
      "def set_modified(param=None):\n" +
      "    if param is None:\n" +
      "        param = set()\n" +
      "    param.add('a')";

    PythonQuickFixVerifier.verify(check, codeWithIssue, fixedCode);
  }

  @Test
  public void counter_modified_quickfix() {
    String codeWithIssue = "" +
      "import collections\n" +
      "def counter_modified(param=collections.Counter()):\n" +
      "    param.subtract()";

    String fixedCode = "" +
      "import collections\n" +
      "def counter_modified(param=None):\n" +
      "    if param is None:\n" +
      "        param = collections.Counter()\n" +
      "    param.subtract()";

    PythonQuickFixVerifier.verify(check, codeWithIssue, fixedCode);
  }

  @Test
  public void import_from_quickfix() {
    String codeWithIssue = "" +
      "from collections import Counter\n" +
      "def list_modified(param=Counter()):\n" +
      "    param.subtract()";

    String fixedCode = "" +
      "from collections import Counter\n" +
      "def list_modified(param=None):\n" +
      "    if param is None:\n" +
      "        param = Counter()\n" +
      "    param.subtract()";

    PythonQuickFixVerifier.verify(check, codeWithIssue, fixedCode);
  }

  @Test
  public void literal_dict_quickfix() {
    String codeWithIssue = "def literal_dict(param={}):\n" +
      "    param.pop('a')";
    String fixedCode = "def literal_dict(param=None):\n" +
      "    if param is None:\n" +
      "        param = dict()\n" +
      "    param.pop('a')";

    PythonQuickFixVerifier.verify(check, codeWithIssue, fixedCode);
  }

  @Test
  public void literal_list_quickfix() {
    String codeWithIssue = "def literal_dict(param=[]):\n" +
      "    param.append('a')";
    String fixedCode = "def literal_dict(param=None):\n" +
      "    if param is None:\n" +
      "        param = list()\n" +
      "    param.append('a')";

    PythonQuickFixVerifier.verify(check, codeWithIssue, fixedCode);
  }

  @Test
  public void set_quickfix() {
    String codeWithIssue = "def literal_dict(param=set()):\n" +
      "    param.add('a')";
    String fixedCode = "def literal_dict(param=None):\n" +
      "    if param is None:\n" +
      "        param = set()\n" +
      "    param.add('a')";

    PythonQuickFixVerifier.verify(check, codeWithIssue, fixedCode);
  }

  @Test
  public void no_quickfix_non_empty_literal_dict() {
    String codeWithIssue = "def literal_dict(param={'foo': 'bar'}):\n" +
      "    param.pop('a')";
    PythonQuickFixVerifier.verifyNoQuickFixes(check, codeWithIssue);
  }

  @Test
  public void no_quickfix_non_empty_literal_list() {
    String codeWithIssue = "def literal_dict(param=[100]):\n" +
      "    param.append('a')";
    PythonQuickFixVerifier.verifyNoQuickFixes(check, codeWithIssue);
  }

  @Test
  public void no_quickfix_non_empty_call() {
    String codeWithIssue = "def literal_dict(param=A('foo')):\n" +
      "    param.attr = 'bar'";
    PythonQuickFixVerifier.verifyNoQuickFixes(check, codeWithIssue);
  }

  @Test
  public void no_quickfix_non_empty_set() {
    String codeWithIssue = "def literal_dict(param={'foo'}):\n" +
      "    param.attr = 'bar'";
    PythonQuickFixVerifier.verifyNoQuickFixes(check, codeWithIssue);
  }

}
