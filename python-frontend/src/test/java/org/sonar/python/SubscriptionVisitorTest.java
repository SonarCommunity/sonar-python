/*
 * SonarQube Python Plugin
 * Copyright (C) 2011-2022 SonarSource SA
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
package org.sonar.python;

import java.util.Collections;
import java.util.regex.Pattern;
import org.junit.Test;
import org.sonar.plugins.python.api.PythonSubscriptionCheck;
import org.sonar.plugins.python.api.PythonVisitorContext;
import org.sonar.plugins.python.api.tree.FileInput;
import org.sonar.plugins.python.api.tree.StringElement;
import org.sonar.plugins.python.api.tree.Tree;
import org.sonar.python.regex.RegexContext;
import org.sonarsource.analyzer.commons.regex.RegexParseResult;
import org.sonarsource.analyzer.commons.regex.ast.FlagSet;

import static org.assertj.core.api.Assertions.assertThat;

public class SubscriptionVisitorTest {

  @Test
  public void test_regex_cache() {
    PythonSubscriptionCheck check = new PythonSubscriptionCheck() {
      @Override
      public void initialize(Context context) {
        context.registerSyntaxNodeConsumer(Tree.Kind.STRING_ELEMENT, ctx -> {
          StringElement stringElement = (StringElement)ctx.syntaxNode();
          RegexContext regexCtx = (RegexContext) ctx;
          RegexParseResult resultWithNoFlags = regexCtx.regexForStringElement(stringElement, new FlagSet());
          RegexParseResult resultWithFlags = regexCtx.regexForStringElement(stringElement, new FlagSet(Pattern.MULTILINE));

          assertThat(resultWithNoFlags).isNotSameAs(resultWithFlags);
          // When we retrieve them again, it will be the same instance retrieved from the cache.
          assertThat(resultWithNoFlags).isSameAs(regexCtx.regexForStringElement(stringElement, new FlagSet()));
          assertThat(resultWithFlags).isSameAs(regexCtx.regexForStringElement(stringElement, new FlagSet(Pattern.MULTILINE)));
        });
      }
    };

    FileInput fileInput = PythonTestUtils.parse("'.*'");
    PythonVisitorContext context = new PythonVisitorContext(fileInput, PythonTestUtils.pythonFile("file"), null, null);
    SubscriptionVisitor.analyze(Collections.singleton(check), context);
  }
}