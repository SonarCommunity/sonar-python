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
package org.sonar.python.checks.hotspots;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.sonar.check.Rule;
import org.sonar.plugins.python.api.PythonSubscriptionCheck;
import org.sonar.plugins.python.api.SubscriptionContext;
import org.sonar.plugins.python.api.symbols.Symbol;
import org.sonar.plugins.python.api.tree.ArgList;
import org.sonar.plugins.python.api.tree.Argument;
import org.sonar.plugins.python.api.tree.AssignmentStatement;
import org.sonar.plugins.python.api.tree.CallExpression;
import org.sonar.plugins.python.api.tree.ClassDef;
import org.sonar.plugins.python.api.tree.Expression;
import org.sonar.plugins.python.api.tree.HasSymbol;
import org.sonar.plugins.python.api.tree.Name;
import org.sonar.plugins.python.api.tree.QualifiedExpression;
import org.sonar.plugins.python.api.tree.RegularArgument;
import org.sonar.plugins.python.api.tree.StringElement;
import org.sonar.plugins.python.api.tree.Tree;
import org.sonar.python.checks.Expressions;
import org.sonar.python.checks.cdk.ClearTextProtocolsCheckPart;
import org.sonar.python.tree.TreeUtils;

@Rule(key = "S5332")
public class ClearTextProtocolsCheck extends PythonSubscriptionCheck {
  private static final List<String> SENSITIVE_PROTOCOLS = Arrays.asList("http://", "ftp://", "telnet://");
  private static final Pattern LOOPBACK = Pattern.compile("localhost|127(?:\\.[0-9]+){0,2}\\.[0-9]+$|^(?:0*\\:)*?:?0*1", Pattern.CASE_INSENSITIVE);
  private static final Map<String, String> ALTERNATIVES = new HashMap<>();
  private static final String SENSITIVE_HTTP_SERVER_CALL = "socketserver.BaseServer.serve_forever";
  private static final Set<String> SENSITIVE_HTTP_SERVER_CLASSES = Set.of("HTTPServer", "ThreadingHTTPServer");

  static {
    ALTERNATIVES.put("http", "https");
    ALTERNATIVES.put("ftp", "sftp, scp or ftps");
    ALTERNATIVES.put("telnet", "ssh");
  }

  @Override
  public void initialize(Context context) {
    context.registerSyntaxNodeConsumer(Tree.Kind.STRING_ELEMENT, ctx -> {
      Tree node = ctx.syntaxNode();
      String value = Expressions.unescape((StringElement) node);
      unsafeProtocol(value)
        // cleanup slashes
        .map(protocol -> protocol.substring(0, protocol.length() - 3))
        .ifPresent(protocol -> ctx.addIssue(node, message(protocol)));
    });
    context.registerSyntaxNodeConsumer(Tree.Kind.CALL_EXPR, ctx -> {
      CallExpression callExpression = (CallExpression) ctx.syntaxNode();
      Optional.ofNullable(callExpression.calleeSymbol())
        .map(Symbol::fullyQualifiedName)
        .flatMap(ClearTextProtocolsCheck::isUnsafeLib)
        .ifPresent(protocol -> ctx.addIssue(callExpression, message(protocol)));
    });

    context.registerSyntaxNodeConsumer(Tree.Kind.ASSIGNMENT_STMT, ctx -> handleAssignmentStatement((AssignmentStatement) ctx.syntaxNode(), ctx));

    context.registerSyntaxNodeConsumer(Tree.Kind.QUALIFIED_EXPR, ClearTextProtocolsCheck::checkServerCallFromSuper);
    new ClearTextProtocolsCheckPart().initialize(context);
  }

  private static void checkServerCallFromSuper(SubscriptionContext ctx) {
    QualifiedExpression qualifiedExpression = (QualifiedExpression) ctx.syntaxNode();
    Optional.of(qualifiedExpression)
      .filter(qe -> isServeForverCall(qe) && isCallToSensitiveSuperClass(qe))
      .ifPresent(qe -> ctx.addIssue(qe, message("http")));
  }

  private static boolean isServeForverCall(QualifiedExpression expression) {
    return "serve_forever".equals(expression.name().name());
  }

  private static boolean isCallToSensitiveSuperClass(QualifiedExpression expression) {
    return Optional.of(expression.qualifier())
      .flatMap(TreeUtils.toOptionalInstanceOfMapper(CallExpression.class))
      .map(CallExpression::callee)
      .flatMap(TreeUtils.toOptionalInstanceOfMapper(Name.class))
      .map(Name::name)
      .filter("super"::equals)
      .filter(name -> isExtendingSensitiveClass(expression))
      .isPresent();
  }


  private static boolean isExtendingSensitiveClass(QualifiedExpression expression) {
    return Optional.ofNullable(TreeUtils.firstAncestorOfKind(expression, Tree.Kind.CLASSDEF))
      .map(ClassDef.class::cast)
      .map(ClassDef::args)
      .map(ArgList::arguments)
      .map(ClearTextProtocolsCheck::getClassNameFromArgument)
      .map(arguments -> arguments.anyMatch(SENSITIVE_HTTP_SERVER_CLASSES::contains))
      .orElse(false);
  }

  public static Stream<String> getClassNameFromArgument(List<Argument> arguments) {
    return arguments.stream()
      .map(TreeUtils.toInstanceOfMapper(RegularArgument.class))
      .filter(Objects::nonNull).map(RegularArgument::expression)
      .map(TreeUtils::nameFromExpression).filter(Objects::nonNull);
  }

  private static void handleAssignmentStatement(AssignmentStatement assignmentStatement, SubscriptionContext ctx) {
    if (assignmentStatement.lhsExpressions().size() > 1) {
      // avoid potential FPs
      return;
    }
    Expression lhs = assignmentStatement.lhsExpressions().get(0).expressions().get(0);
    if (lhs instanceof HasSymbol) {
      Symbol symbol = ((HasSymbol) lhs).symbol();
      if (symbol == null) {
        return;
      }
      if (lhs.type().canOnlyBe("smtplib.SMTP")) {
        boolean usesEncryption = symbol.usages().stream().anyMatch(u -> {
          Tree tree = TreeUtils.firstAncestorOfKind(u.tree(), Tree.Kind.CALL_EXPR);
          if (tree != null) {
            Symbol calleeSymbol = ((CallExpression) tree).calleeSymbol();
            return calleeSymbol != null && "smtplib.SMTP.starttls".equals(calleeSymbol.fullyQualifiedName());
          }
          return false;
        });
        if (!usesEncryption) {
          ctx.addIssue(assignmentStatement.assignedValue(), "Make sure STARTTLS is used to upgrade to a secure connection using SSL/TLS.");
        }
      }
    }
  }

  private static Optional<String> unsafeProtocol(String literalValue) {
    for (String protocol : SENSITIVE_PROTOCOLS) {
      if (literalValue.startsWith(protocol)) {
        try {
          URI uri = new URI(literalValue);
          String host = uri.getHost();
          if (host == null) {
            // handle ipv6 loopback
            host = uri.getAuthority();
          }
          if (host == null || LOOPBACK.matcher(host).matches()) {
            return Optional.empty();
          }
        } catch (URISyntaxException e) {
          // not parseable uri, try to find loopback in the substring without protocol, this handles case of url formatted as string
          if (LOOPBACK.matcher(literalValue.substring(protocol.length())).find()) {
            return Optional.empty();
          }
        }
        return Optional.of(protocol);
      }
    }
    return Optional.empty();
  }

  private static Optional<String> isUnsafeLib(String qualifiedName) {
    if ("telnetlib.Telnet".equals(qualifiedName)) {
      return Optional.of("telnet");
    }
    if ("ftplib.FTP".equals(qualifiedName)) {
      return Optional.of("ftp");
    }
    if (SENSITIVE_HTTP_SERVER_CALL.equals(qualifiedName)) {
      return Optional.of("http");
    }
    return Optional.empty();
  }

  private static String message(String protocol) {
    return "Using " + protocol + " protocol is insecure. Use " + ALTERNATIVES.get(protocol) + " instead";
  }
}
