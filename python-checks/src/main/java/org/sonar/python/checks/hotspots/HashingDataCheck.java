/*
 * SonarQube Python Plugin
 * Copyright (C) 2011-2021 SonarSource SA
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

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.sonar.check.Rule;
import org.sonar.plugins.python.api.SubscriptionContext;
import org.sonar.plugins.python.api.tree.ArgList;
import org.sonar.plugins.python.api.tree.AssignmentStatement;
import org.sonar.plugins.python.api.tree.CallExpression;
import org.sonar.plugins.python.api.tree.ClassDef;
import org.sonar.plugins.python.api.tree.Expression;
import org.sonar.plugins.python.api.tree.ExpressionList;
import org.sonar.plugins.python.api.tree.HasSymbol;
import org.sonar.plugins.python.api.tree.Name;
import org.sonar.plugins.python.api.tree.QualifiedExpression;
import org.sonar.plugins.python.api.tree.RegularArgument;
import org.sonar.plugins.python.api.tree.Tree;
import org.sonar.python.checks.AbstractCallExpressionCheck;
import org.sonar.python.checks.Expressions;
import org.sonar.plugins.python.api.symbols.Symbol;

@Rule(key = HashingDataCheck.CHECK_KEY)
public class HashingDataCheck extends AbstractCallExpressionCheck {
  public static final String CHECK_KEY = "S4790";
  private static final String MESSAGE = "Make sure that hashing data is safe here.";
  private static final Set<String> questionableFunctions = immutableSet(
    "hashlib.new",
    "cryptography.hazmat.primitives.hashes.Hash",
    "cryptography.hazmat.primitives.hashes.MD5",
    "cryptography.hazmat.primitives.hashes.SHA1",
    "django.contrib.auth.hashers.make_password",
    "werkzeug.security.generate_password_hash",
    // https://github.com/Legrandin/pycryptodome
    "Cryptodome.Hash.MD2.new",
    "Cryptodome.Hash.MD4.new",
    "Cryptodome.Hash.MD5.new",
    "Cryptodome.Hash.SHA.new",
    "Cryptodome.Hash.SHA224.new",
    "Cryptodome.Hash.SHA256.new",
    "Cryptodome.Hash.SHA384.new",
    "Cryptodome.Hash.SHA512.new",
    "Cryptodome.Hash.HMAC.new",
    // https://github.com/dlitz/pycrypto
    "Crypto.Hash.MD2.new",
    "Crypto.Hash.MD4.new",
    "Crypto.Hash.MD5.new",
    "Crypto.Hash.SHA.new",
    "Crypto.Hash.SHA224.new",
    "Crypto.Hash.SHA256.new",
    "Crypto.Hash.SHA384.new",
    "Crypto.Hash.SHA512.new",
    "Crypto.Hash.HMAC.new"
    );
  private static final Set<String> questionableHashlibAlgorithm = Stream.of(
    "blake2b", "blake2s", "md5", "pbkdf2_hmac", "sha1", "sha224",
    "sha256", "sha384", "sha3_224", "sha3_256", "sha3_384", "sha3_512",
    "sha512", "shake_128", "shake_256", "scrypt")
    .map(hasher -> "hashlib." + hasher)
    .collect(Collectors.toSet());

  private static final Set<String> questionablePasslibAlgorithm = Stream.of(
    "apr_md5_crypt", "argon2", "atlassian_pbkdf2_sha1", "bcrypt",
    "bcrypt_sha256", "bigcrypt", "bsd_nthash", "bsdi_crypt",
    "cisco_asa", "cisco_pix", "cisco_type7", "crypt16",
    "cta_pbkdf2_sha1", "des_crypt", "django_argon2", "django_bcrypt",
    "django_bcrypt_sha256", "django_des_crypt", "django_disabled",
    "django_pbkdf2_sha1", "django_pbkdf2_sha256", "django_salted_md5",
    "django_salted_sha1", "dlitz_pbkdf2_sha1", "fshp", "grub_pbkdf2_sha512",
    "hex_md4", "hex_md5", "hex_sha1", "hex_sha256", "hex_sha512",
    "htdigest", "ldap_bcrypt", "ldap_bsdi_crypt", "ldap_des_crypt", "ldap_hex_md5",
    "ldap_hex_sha1", "ldap_md5", "ldap_md5_crypt", "ldap_pbkdf2_sha1",
    "ldap_pbkdf2_sha256", "ldap_pbkdf2_sha512", "ldap_plaintext", "ldap_salted_md5",
    "ldap_salted_sha1", "ldap_sha1", "ldap_sha1_crypt", "ldap_sha256_crypt",
    "ldap_sha512_crypt", "lmhash", "md5_crypt", "msdcc", "msdcc2",
    "mssql2000", "mssql2005", "mysql323", "mysql41", "nthash", "oracle10",
    "oracle11", "pbkdf2_sha1", "pbkdf2_sha256", "pbkdf2_sha512", "phpass", "plaintext",
    "postgres_md5", "roundup_plaintext", "scram", "scrypt", "sha1_crypt", "sha256_crypt",
    "sha512_crypt", "sun_md5_crypt", "unix_disabled", "unix_fallback")
    .map(hasher -> "passlib.hash." + hasher)
    .collect(Collectors.toSet());


  private static final Set<String> questionableDjangoHashers = Stream.of(
    "PBKDF2PasswordHasher", "PBKDF2SHA1PasswordHasher", "Argon2PasswordHasher",
    "BCryptSHA256PasswordHasher", "BasePasswordHasher", "BCryptPasswordHasher", "SHA1PasswordHasher", "MD5PasswordHasher",
    "UnsaltedSHA1PasswordHasher", "UnsaltedMD5PasswordHasher", "CryptPasswordHasher")
    .map(hasher -> "django.contrib.auth.hashers." + hasher)
    .collect(Collectors.toSet());

  @Override
  public void initialize(Context context) {
    super.initialize(context);
    context.registerSyntaxNodeConsumer(Tree.Kind.ASSIGNMENT_STMT, HashingDataCheck::checkOverwriteDjangoHashers);
    context.registerSyntaxNodeConsumer(Tree.Kind.CLASSDEF, HashingDataCheck::checkCreatingCustomHasher);
    context.registerSyntaxNodeConsumer(Tree.Kind.NAME, HashingDataCheck::checkQuestionableHashingAlgorithm);
  }

  /**
   * `make_password(password, salt, hasher)` function is sensitive when it's used with a specific
   * hasher name or salt.
   * No issue should be raised when only the password is provided.
   * <p>
   * make_password(password, salt=salt)  # Sensitive
   * make_password(password, hasher=hasher)  # Sensitive
   * make_password(password, salt=salt, hasher=hasher)  # Sensitive
   * make_password(password)  # OK
   */
  @Override
  protected boolean isException(CallExpression callExpression) {
    return isDjangoMakePasswordFunctionWithoutSaltAndHasher(callExpression);
  }

  private static boolean isDjangoMakePasswordFunctionWithoutSaltAndHasher(CallExpression callExpression) {
    return callExpression.calleeSymbol() != null
      && "django.contrib.auth.hashers.make_password".equals(callExpression.calleeSymbol().fullyQualifiedName())
      && callExpression.arguments().size() == 1;
  }

  private static void checkOverwriteDjangoHashers(SubscriptionContext ctx) {
    AssignmentStatement assignmentStatementTree = (AssignmentStatement) ctx.syntaxNode();

    if (isOverwritingDjangoHashers(assignmentStatementTree.lhsExpressions())) {
      ctx.addIssue(assignmentStatementTree, MESSAGE);
      return;
    }

    // checks for `PASSWORD_HASHERS = []` in a global_settings.py file
    if (ctx.pythonFile().fileName().equals("global_settings.py") &&
      assignmentStatementTree.lhsExpressions().stream()
        .flatMap(pelt -> pelt.expressions().stream())
        .anyMatch(expression -> expression.firstToken().value().equals("PASSWORD_HASHERS"))) {
      ctx.addIssue(assignmentStatementTree, MESSAGE);
    }
  }

  /**
   * checks for `settings.PASSWORD_HASHERS = value`
   */
  private static boolean isOverwritingDjangoHashers(List<ExpressionList> lhsExpressions) {
    for (ExpressionList expr : lhsExpressions) {
      for (Expression expression : expr.expressions()) {
        Expression baseExpr = Expressions.removeParentheses(expression);
        if (baseExpr.is(Tree.Kind.QUALIFIED_EXPR)) {
          QualifiedExpression qualifiedExpression = (QualifiedExpression) baseExpr;
          if (qualifiedExpression.symbol() != null
            && "django.conf.settings.PASSWORD_HASHERS".equals(qualifiedExpression.symbol().fullyQualifiedName())) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private static void checkQuestionableHashingAlgorithm(SubscriptionContext ctx) {
    Name name = (Name) ctx.syntaxNode();
    if (isWithinImport(name)) {
      return;
    }
    String fullyQualifiedName = name.symbol() != null ? name.symbol().fullyQualifiedName() : "";
    if (questionableHashlibAlgorithm.contains(fullyQualifiedName) || questionablePasslibAlgorithm.contains(fullyQualifiedName)) {
      ctx.addIssue(name, MESSAGE);
    }
  }

  private static String getQualifiedName(Expression node) {
    if (node instanceof HasSymbol) {
      Symbol symbol = ((HasSymbol) node).symbol();
      return symbol != null ? symbol.fullyQualifiedName() : "";
    }
    return "";
  }

  private static void checkCreatingCustomHasher(SubscriptionContext ctx) {
    ClassDef classDef = (ClassDef) ctx.syntaxNode();
    ArgList argList = classDef.args();
    if (argList != null) {
      argList.arguments()
        .stream()
        .filter(arg -> arg.is(Tree.Kind.REGULAR_ARGUMENT))
        .map(RegularArgument.class::cast)
        .filter(arg -> questionableDjangoHashers.contains(getQualifiedName(arg.expression())))
        .forEach(arg -> ctx.addIssue(arg, MESSAGE));
    }
  }

  @Override
  protected Set<String> functionsToCheck() {
    return questionableFunctions;
  }

  @Override
  protected String message() {
    return MESSAGE;
  }
}
