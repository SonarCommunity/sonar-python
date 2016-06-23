/*
 * SonarQube Python Plugin
 * Copyright (C) 2011-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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

import com.sonar.sslr.api.AstNode;
import com.sonar.sslr.api.Grammar;
import org.sonar.check.Priority;
import org.sonar.check.Rule;
import org.sonar.python.api.PythonTokenType;
import org.sonar.squidbridge.annotations.ActivatedByDefault;
import org.sonar.squidbridge.annotations.SqaleConstantRemediation;
import org.sonar.squidbridge.checks.SquidCheck;

@Rule(
    key = LongIntegerWithLowercaseSuffixUsageCheck.CHECK_KEY,
    priority = Priority.MINOR,
    name = "Long suffix \"L\" should be upper case",
    tags = Tags.CONVENTION
)
@SqaleConstantRemediation("2min")
@ActivatedByDefault
public class LongIntegerWithLowercaseSuffixUsageCheck extends SquidCheck<Grammar> {

  public static final String CHECK_KEY = "LongIntegerWithLowercaseSuffixUsage";

  @Override
  public void init() {
    subscribeTo(PythonTokenType.NUMBER);
  }

  @Override
  public void visitNode(AstNode astNode) {
    String value = astNode.getTokenValue();
    if (value.charAt(value.length() - 1) == 'l') {
      getContext().createLineViolation(this, "Replace suffix in long integers from lower case \"l\" to upper case \"L\".", astNode);
    }
  }

}
