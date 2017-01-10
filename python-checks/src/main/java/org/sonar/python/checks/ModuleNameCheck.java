/*
 * SonarQube Python Plugin
 * Copyright (C) 2011-2017 SonarSource SA
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

import com.sonar.sslr.api.AstNode;
import com.sonar.sslr.api.Grammar;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.sonar.check.Priority;
import org.sonar.check.Rule;
import org.sonar.check.RuleProperty;
import org.sonar.squidbridge.annotations.SqaleConstantRemediation;
import org.sonar.squidbridge.checks.SquidCheck;

@Rule(
    key = ModuleNameCheck.CHECK_KEY,
    priority = Priority.MINOR,
    name = "Module names should comply with a naming convention",
    tags = Tags.CONVENTION
)
@SqaleConstantRemediation("10min")
public class ModuleNameCheck extends SquidCheck<Grammar> {

  public static final String CHECK_KEY = "S1578";
  private static final String DEFAULT = "(([a-z_][a-z0-9_]*)|([A-Z][a-zA-Z0-9]+))$";
  private static final String MESSAGE = "Rename this module to match this regular expression: \"%s\".";

  @RuleProperty(
    key = "format",
    defaultValue = "" + DEFAULT)
  public String format = DEFAULT;
  private Pattern pattern = null;

  @Override
  public void init() {
    pattern = Pattern.compile(format);
  }

  @Override
  public void visitFile(@Nullable AstNode astNode) {
    String fileName = getContext().getFile().getName();
    int dotIndex = fileName.lastIndexOf('.');
    if (dotIndex > 0) {
      String moduleName = fileName.substring(0, dotIndex);
      if (!pattern.matcher(moduleName).matches()) {
        getContext().createFileViolation(this, String.format(MESSAGE, format));
      }
    }
  }

}
