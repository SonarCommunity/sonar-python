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
import java.util.regex.Pattern;
import org.sonar.check.Priority;
import org.sonar.check.Rule;
import org.sonar.check.RuleProperty;
import org.sonar.python.api.PythonGrammar;
import org.sonar.squidbridge.annotations.ActivatedByDefault;
import org.sonar.squidbridge.annotations.SqaleConstantRemediation;
import org.sonar.squidbridge.checks.SquidCheck;

@Rule(
    key = ClassNameCheck.CHECK_KEY,
    priority = Priority.MINOR,
    name = "Class names should comply with a naming convention",
    tags = Tags.CONVENTION
)
@SqaleConstantRemediation("5min")
@ActivatedByDefault
public class ClassNameCheck extends SquidCheck<Grammar> {

  public static final String CHECK_KEY = "S101";
  private static final String DEFAULT = "^[A-Z_][a-zA-Z0-9]+$";

  @RuleProperty(
    key = "format",
    defaultValue = "" + DEFAULT)
  public String format = DEFAULT;
  private Pattern pattern = null;

  @Override
  public void init() {
    pattern = Pattern.compile(format);
    subscribeTo(PythonGrammar.CLASSDEF);
  }

  @Override
  public void visitNode(AstNode astNode) {
    String className = astNode.getFirstChild(PythonGrammar.CLASSNAME).getTokenValue();
    if (!pattern.matcher(className).matches()) {
      getContext().createLineViolation(this,
        "Rename class \"{0}\" to match the regular expression {1}.", astNode, className, format);
    }
  }

}
