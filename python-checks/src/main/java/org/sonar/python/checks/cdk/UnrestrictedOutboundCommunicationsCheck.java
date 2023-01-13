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
package org.sonar.python.checks.cdk;

import java.util.List;
import org.sonar.check.Rule;

import static org.sonar.python.checks.cdk.CdkPredicate.isTrue;
import static org.sonar.python.checks.cdk.CdkUtils.getArgument;

@Rule(key = "S6463")
public class UnrestrictedOutboundCommunicationsCheck extends AbstractCdkResourceCheck {

  public static final String OMITTING_MESSAGE = "Omitting \"allow_all_outbound\" enables unrestricted outbound communications. Make sure it is safe here.";
  public static final String UNRESTRICTED_MESSAGE = "Make sure that allowing unrestricted outbound communications is safe here.";

  @Override
  protected void registerFqnConsumer() {
    checkFqns(List.of("aws_cdk.aws_ec2.SecurityGroup", "aws_cdk.aws_ec2.SecurityGroup.from_security_group_id"), (subscriptionContext, callExpression) ->
      getArgument(subscriptionContext, callExpression, "allow_all_outbound").ifPresentOrElse(
        argument -> argument.addIssueIf(isTrue(), UNRESTRICTED_MESSAGE),
        () -> subscriptionContext.addIssue(callExpression.callee(), OMITTING_MESSAGE)
      )
    );
  }
}
