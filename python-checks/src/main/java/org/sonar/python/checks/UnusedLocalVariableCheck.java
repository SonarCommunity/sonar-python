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

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.sonar.check.Rule;
import org.sonar.check.RuleProperty;
import org.sonar.plugins.python.api.PythonSubscriptionCheck;
import org.sonar.plugins.python.api.SubscriptionContext;
import org.sonar.plugins.python.api.quickfix.PythonQuickFix;
import org.sonar.plugins.python.api.symbols.Symbol;
import org.sonar.plugins.python.api.symbols.Usage;
import org.sonar.plugins.python.api.tree.AnnotatedAssignment;
import org.sonar.plugins.python.api.tree.ComprehensionExpression;
import org.sonar.plugins.python.api.tree.DictCompExpression;
import org.sonar.plugins.python.api.tree.ExpressionList;
import org.sonar.plugins.python.api.tree.ForStatement;
import org.sonar.plugins.python.api.tree.FunctionDef;
import org.sonar.plugins.python.api.tree.Tree;
import org.sonar.plugins.python.api.tree.Tree.Kind;
import org.sonar.python.quickfix.TextEditUtils;
import org.sonar.python.tree.TreeUtils;

@Rule(key = "S1481")
public class UnusedLocalVariableCheck extends PythonSubscriptionCheck {

  private static final String DEFAULT = "(_[a-zA-Z0-9_]*|dummy|unused|ignored)";
  private static final String MESSAGE = "Remove the unused local variable \"%s\".";
  private static final String SEQUENCE_UNPACKING_MESSAGE = "Replace unused local variable \"%s\" with \"_\".";
  private static final String QUICK_FIX_MESSAGE = "Replace with \"_\"";
  private static final String SECONDARY_MESSAGE = "Assignment to unused local variable \"%s\".";

  @RuleProperty(
    key = "regex",
    description = "Regular expression used to identify variable name to ignore.",
    defaultValue = DEFAULT)
  public String format = DEFAULT;
  private Pattern pattern;

  @Override
  public void initialize(Context context) {
    pattern = Pattern.compile(format);
    context.registerSyntaxNodeConsumer(Kind.FUNCDEF, ctx -> checkLocalVars(ctx, ctx.syntaxNode(), ((FunctionDef) ctx.syntaxNode()).localVariables()));
    context.registerSyntaxNodeConsumer(Kind.DICT_COMPREHENSION, ctx -> checkLocalVars(ctx, ctx.syntaxNode(), ((DictCompExpression) ctx.syntaxNode()).localVariables()));
    context.registerSyntaxNodeConsumer(Kind.LIST_COMPREHENSION, ctx -> checkLocalVars(ctx, ctx.syntaxNode(), ((ComprehensionExpression) ctx.syntaxNode()).localVariables()));
    context.registerSyntaxNodeConsumer(Kind.SET_COMPREHENSION, ctx -> checkLocalVars(ctx, ctx.syntaxNode(), ((ComprehensionExpression) ctx.syntaxNode()).localVariables()));
    context.registerSyntaxNodeConsumer(Kind.GENERATOR_EXPR, ctx -> checkLocalVars(ctx, ctx.syntaxNode(), ((ComprehensionExpression) ctx.syntaxNode()).localVariables()));
  }

  private void checkLocalVars(SubscriptionContext ctx, Tree functionTree, Set<Symbol> symbols) {
    // https://docs.python.org/3/library/functions.html#locals
    if (CheckUtils.containsCallToLocalsFunction(functionTree)) {
      return;
    }
    symbols.stream()
      .filter(s -> !pattern.matcher(s.name()).matches())
      .filter(UnusedLocalVariableCheck::hasOnlyBindingUsages)
      .forEach(symbol -> {
        var usages = symbol.usages().stream()
          .filter(usage -> usage.tree().parent() == null || !usage.tree().parent().is(Kind.PARAMETER))
          .filter(usage -> !isTupleDeclaration(usage))
          .filter(usage -> usage.kind() != Usage.Kind.FUNC_DECLARATION)
          .filter(usage -> usage.kind() != Usage.Kind.CLASS_DECLARATION)
          .collect(Collectors.toList());

        if (!usages.isEmpty()) {
          var firstUsage = usages.get(0);
          var issue = createIssue(ctx, symbol, firstUsage);

          usages.stream().skip(1)
            .forEach(usage -> issue.secondary(usage.tree(), String.format(SECONDARY_MESSAGE, symbol.name())));
        }
      });
  }

  public PreciseIssue createIssue(SubscriptionContext ctx, Symbol symbol, Usage usage) {
    if (isSequenceUnpacking(usage)) {
      var quickFix = PythonQuickFix.newQuickFix(QUICK_FIX_MESSAGE, TextEditUtils.replace(usage.tree(), "_"));
      var issue = ctx.addIssue(usage.tree(), String.format(SEQUENCE_UNPACKING_MESSAGE, symbol.name()));
      issue.addQuickFix(quickFix);
      return issue;
    } else {
      return ctx.addIssue(usage.tree(), String.format(MESSAGE, symbol.name()));
    }
  }

  private static boolean hasOnlyBindingUsages(Symbol symbol) {
    List<Usage> usages = symbol.usages();
    if (isOnlyTypeAnnotation(usages)) {
      return false;
    }
    return usages.stream().noneMatch(usage -> usage.kind() == Usage.Kind.IMPORT)
      && usages.stream().allMatch(Usage::isBindingUsage);
  }

  private static boolean isOnlyTypeAnnotation(List<Usage> usages) {
    return usages.size() == 1 && usages.get(0).isBindingUsage() &&
      TreeUtils.firstAncestor(usages.get(0).tree(), t -> t.is(Kind.ANNOTATED_ASSIGNMENT) && ((AnnotatedAssignment) t).assignedValue() == null) != null;
  }

  private static boolean isTupleDeclaration(Usage usage) {
    var tree = usage.tree();
    return !isSequenceUnpacking(usage) && TreeUtils.firstAncestor(tree, t -> t.is(Kind.TUPLE)
      || (t.is(Kind.EXPRESSION_LIST) && ((ExpressionList) t).expressions().size() > 1)
      || (t.is(Kind.FOR_STMT) && ((ForStatement) t).expressions().size() > 1 && ((ForStatement) t).expressions().contains(tree))) != null;
  }

  private static boolean isSequenceUnpacking(Usage usage) {
    return Optional.of(usage)
      .filter(u -> u.kind() == Usage.Kind.ASSIGNMENT_LHS)
      .map(Usage::tree)
      .map(tree -> TreeUtils.firstAncestorOfKind(tree, Kind.EXPRESSION_LIST))
      .map(ExpressionList.class::cast)
      .filter(list -> list.expressions().size() > 1)
      .isPresent();
  }
}
