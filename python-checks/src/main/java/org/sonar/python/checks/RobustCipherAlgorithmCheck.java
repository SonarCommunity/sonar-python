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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.CheckForNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.check.Rule;
import org.sonar.plugins.python.api.PythonSubscriptionCheck;
import org.sonar.plugins.python.api.SubscriptionContext;
import org.sonar.plugins.python.api.symbols.Symbol;
import org.sonar.plugins.python.api.tree.CallExpression;
import org.sonar.plugins.python.api.tree.Expression;
import org.sonar.plugins.python.api.tree.Name;
import org.sonar.plugins.python.api.tree.RegularArgument;
import org.sonar.plugins.python.api.tree.Tree;
import org.sonar.python.tree.StringLiteralImpl;
import org.sonar.python.tree.TreeUtils;

import static java.util.Arrays.asList;

// https://jira.sonarsource.com/browse/RSPEC-5547 (general)
// https://jira.sonarsource.com/browse/RSPEC-5552 (python-specific)
@Rule(key = "S5547")
public class RobustCipherAlgorithmCheck extends PythonSubscriptionCheck {

  private static final Logger LOG = LoggerFactory.getLogger(RobustCipherAlgorithmCheck.class);

  private static final String MESSAGE = "Use a strong cipher algorithm.";
  private static final HashSet<String> sensitiveCalleeFqns = new HashSet<>();

  private static final Set<String> INSECURE_CIPHERS = Set.of(
    "NULL",
    "RC2",
    "RC4",
    "DES",
    "3DES",
    "MD5",
    "SHA"
  );

  public static final String SSL_SET_CIPHERS_FQN = "ssl.SSLContext.set_ciphers";

  static {
    // `pycryptodomex`, `pycryptodome`, and `pycrypto` all share the same names of the algorithms,
    // moreover, `pycryptodome` is drop-in replacement for `pycrypto`, thus they share same name ("Crypto").
    for (String libraryName : asList("Cryptodome", "Crypto")) {
      for (String vulnerableMethodName : asList("DES", "DES3", "ARC2", "ARC4", "Blowfish")) {
        sensitiveCalleeFqns.add(String.format("%s.Cipher.%s.new", libraryName, vulnerableMethodName));
      }
    }


    // Idea is listed under "Weak Algorithms" in pyca/cryptography documentation
    // https://cryptography.io/en/latest/hazmat/primitives/symmetric-encryption/\
    // #cryptography.hazmat.primitives.ciphers.algorithms.IDEA
    // pyca (pyca/cryptography)
    for (String methodName : asList("TripleDES", "Blowfish", "ARC4", "IDEA")) {
      sensitiveCalleeFqns.add(String.format("cryptography.hazmat.primitives.ciphers.algorithms.%s", methodName));
    }

    // pydes
    sensitiveCalleeFqns.add("pyDes.des");
    sensitiveCalleeFqns.add("pyDes.triple_des");
//    sensitiveCalleeFqns.add(SSL_SET_CIPHERS_FQN);
  }

  @Override
  public void initialize(Context context) {
    context.registerSyntaxNodeConsumer(Tree.Kind.CALL_EXPR, subscriptionContext -> {
      CallExpression callExpr = (CallExpression) subscriptionContext.syntaxNode();
      Optional.ofNullable(callExpr)
        .map(CallExpression::calleeSymbol)
        .map(Symbol::fullyQualifiedName)
        .filter(fqn -> sensitiveCalleeFqns.contains(fqn) || SSL_SET_CIPHERS_FQN.equals(fqn))
        .ifPresent(str -> addMethodSpecificIssue(subscriptionContext, callExpr, str));
    });
  }

  private static void addMethodSpecificIssue(
    SubscriptionContext subscriptionContext,
    CallExpression callExpression,
    String fullyQualifiedName
  ) {
    if (!SSL_SET_CIPHERS_FQN.equals(fullyQualifiedName) || argumentSpecifiesRiskyAlgorithm(callExpression)) {
      subscriptionContext.addIssue(callExpression.callee(), MESSAGE);
    }
  }

  private static boolean argumentSpecifiesRiskyAlgorithm(CallExpression callExpression) {
    return Optional.of(callExpression.arguments())
      .filter(list -> list.size() == 1)
      .map(list -> list.get(0))
      .flatMap(TreeUtils.toOptionalInstanceOfMapper(RegularArgument.class))
      .map(RegularArgument::expression)
      .map(RobustCipherAlgorithmCheck::unpackArgument)
      .filter(RobustCipherAlgorithmCheck::containsInsecureCipher)
      .isPresent();
  }

  @CheckForNull
  private static String unpackArgument(@CheckForNull Expression expression) {
    if (expression == null) {
      return null;
    } else if (expression.is(Tree.Kind.STRING_LITERAL)) {
      LOG.info(((StringLiteralImpl) expression).trimmedQuotesValue());
      return ((StringLiteralImpl) expression).trimmedQuotesValue();
    } else if (expression.is(Tree.Kind.NAME)) {
      return unpackArgument(Expressions.singleAssignedValue((Name) expression));
    } else {
      return null;
    }
  }

  private static boolean containsInsecureCipher(String ciphers) {
    return Stream.of(ciphers)
      .flatMap(str -> Arrays.stream(str.split(":")))
      .flatMap(str -> Arrays.stream(str.split("-")))
      .anyMatch(INSECURE_CIPHERS::contains);
  }


}
