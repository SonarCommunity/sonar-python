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

import com.google.common.collect.ImmutableSet;
import com.sonar.sslr.api.AstNode;
import com.sonar.sslr.api.AstNodeType;
import java.util.Set;
import org.sonar.check.Priority;
import org.sonar.check.Rule;
import org.sonar.python.PythonCheck;
import org.sonar.python.api.PythonGrammar;
import org.sonar.squidbridge.annotations.ActivatedByDefault;
import org.sonar.squidbridge.annotations.SqaleConstantRemediation;

@Rule(
    key = ExecStatementUsageCheck.CHECK_KEY,
    priority = Priority.MAJOR,
    name = "The \"exec\" statement should not be used",
    tags = Tags.OBSOLETE
)
@SqaleConstantRemediation("10min")
@ActivatedByDefault
public class ExecStatementUsageCheck extends PythonCheck {
  public static final String CHECK_KEY = "ExecStatementUsage";
  @Override
  public Set<AstNodeType> subscribedKinds() {
    return ImmutableSet.of(PythonGrammar.EXEC_STMT);
  }

  @Override
  public void visitNode(AstNode astNode) {
    addIssue(astNode.getFirstChild(), "Do not use exec statement.");
  }

}
