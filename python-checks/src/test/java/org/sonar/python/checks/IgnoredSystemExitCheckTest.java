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

class IgnoredSystemExitCheckTest {

  @Test
  void test() {
    PythonCheckVerifier.verify("src/test/resources/checks/ignoredSystemExit.py", new IgnoredSystemExitCheck());
  }

  @Test
  void quickFixTest() {
    var before = "try:\n" +
      "    open(\"foo.txt\", \"r\")\n" +
      "except SystemExit:\n" +
      "    pass";
    var after = "try:\n" +
      "    open(\"foo.txt\", \"r\")\n" +
      "except SystemExit:\n" +
      "    raise";
    IgnoredSystemExitCheck check = new IgnoredSystemExitCheck();
    PythonQuickFixVerifier.verify(check, before, after);
    PythonQuickFixVerifier.verifyQuickFixMessages(check, before, IgnoredSystemExitCheck.QUICK_FIX_MESSAGE);
  }

  @Test
  void addRaiseQuickFixTest() {
    var before = "def bar():\n" +
      "    try:\n" +
      "        foo()\n" +
      "    except SystemExit:\n" +
      "        pass\n" +
      "        a = 10\n" +
      "        print(a)";
    var after = "def bar():\n" +
      "    try:\n" +
      "        foo()\n" +
      "    except SystemExit:\n" +
      "        pass\n" +
      "        a = 10\n" +
      "        print(a)\n" +
      "        raise";
    IgnoredSystemExitCheck check = new IgnoredSystemExitCheck();
    PythonQuickFixVerifier.verify(check, before, after);
    PythonQuickFixVerifier.verifyQuickFixMessages(check, before, IgnoredSystemExitCheck.QUICK_FIX_MESSAGE);
  }

  @Test
  void replacePassWithRaiseQuickFixTest() {
    var before = "def bar():\n" +
      "    try:\n" +
      "        foo()\n" +
      "    except SystemExit:\n" +
      "        a = 10\n" +
      "        print(a)\n" +
      "        pass";
    var after = "def bar():\n" +
      "    try:\n" +
      "        foo()\n" +
      "    except SystemExit:\n" +
      "        a = 10\n" +
      "        print(a)\n" +
      "        raise";
    IgnoredSystemExitCheck check = new IgnoredSystemExitCheck();
    PythonQuickFixVerifier.verify(check, before, after);
    PythonQuickFixVerifier.verifyQuickFixMessages(check, before, IgnoredSystemExitCheck.QUICK_FIX_MESSAGE);
  }

  @Test
  void replaceEllipsisWithRaiseQuickFixTest() {
    var before = "def bar():\n" +
      "    try:\n" +
      "        foo()\n" +
      "    except SystemExit:\n" +
      "        a = 10\n" +
      "        print(a)\n" +
      "        ...";
    var after = "def bar():\n" +
      "    try:\n" +
      "        foo()\n" +
      "    except SystemExit:\n" +
      "        a = 10\n" +
      "        print(a)\n" +
      "        raise";
    IgnoredSystemExitCheck check = new IgnoredSystemExitCheck();
    PythonQuickFixVerifier.verify(check, before, after);
    PythonQuickFixVerifier.verifyQuickFixMessages(check, before, IgnoredSystemExitCheck.QUICK_FIX_MESSAGE);
  }

  @Test
  void baseExceptionQuickFixTest() {
    var before = "def bar():\n" +
      "    try:\n" +
      "        foo()\n" +
      "    except BaseException:\n" +
      "        a = 10\n" +
      "        print(a)\n" +
      "        ...";
    var after = "def bar():\n" +
      "    try:\n" +
      "        foo()\n" +
      "    except BaseException:\n" +
      "        a = 10\n" +
      "        print(a)\n" +
      "        raise";
    IgnoredSystemExitCheck check = new IgnoredSystemExitCheck();
    PythonQuickFixVerifier.verify(check, before, after);
    PythonQuickFixVerifier.verifyQuickFixMessages(check, before, IgnoredSystemExitCheck.QUICK_FIX_MESSAGE);
  }

  @Test
  void bareExceptionQuickFixTest() {
    var before = "def bar():\n" +
      "    try:\n" +
      "        foo()\n" +
      "    except:\n" +
      "        a = 10\n" +
      "        print(a)\n" +
      "        ...";
    var after = "def bar():\n" +
      "    try:\n" +
      "        foo()\n" +
      "    except:\n" +
      "        a = 10\n" +
      "        print(a)\n" +
      "        raise";
    IgnoredSystemExitCheck check = new IgnoredSystemExitCheck();
    PythonQuickFixVerifier.verify(check, before, after);
    PythonQuickFixVerifier.verifyQuickFixMessages(check, before, IgnoredSystemExitCheck.QUICK_FIX_MESSAGE);
  }

}
