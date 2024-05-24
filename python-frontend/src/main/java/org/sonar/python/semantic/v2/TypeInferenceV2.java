/*
 * SonarQube Python Plugin
 * Copyright (C) 2011-2024 SonarSource SA
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
package org.sonar.python.semantic.v2;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.sonar.plugins.python.api.PythonFile;
import org.sonar.plugins.python.api.cfg.ControlFlowGraph;
import org.sonar.plugins.python.api.tree.BaseTreeVisitor;
import org.sonar.plugins.python.api.tree.FileInput;
import org.sonar.plugins.python.api.tree.FunctionDef;
import org.sonar.plugins.python.api.tree.Name;
import org.sonar.plugins.python.api.tree.Parameter;
import org.sonar.plugins.python.api.tree.StatementList;
import org.sonar.plugins.python.api.tree.Tree;
import org.sonar.python.semantic.v2.types.FlowSensitiveTypeInference;
import org.sonar.python.semantic.v2.types.Propagation;
import org.sonar.python.semantic.v2.types.PropagationVisitor;
import org.sonar.python.semantic.v2.types.TrivialTypeInferenceVisitor;
import org.sonar.python.semantic.v2.types.TryStatementVisitor;
import org.sonar.python.tree.TreeUtils;
import org.sonar.python.types.v2.PythonType;

public class TypeInferenceV2 {

  private final ProjectLevelTypeTable projectLevelTypeTable;
  private final SymbolTable symbolTable;
  private final PythonFile pythonFile;

  public TypeInferenceV2(ProjectLevelTypeTable projectLevelTypeTable, PythonFile pythonFile, SymbolTable symbolTable) {
    this.projectLevelTypeTable = projectLevelTypeTable;
    this.symbolTable = symbolTable;
    this.pythonFile = pythonFile;
  }

  public void inferTypes(FileInput fileInput) {
    TrivialTypeInferenceVisitor trivialTypeInferenceVisitor = new TrivialTypeInferenceVisitor(projectLevelTypeTable, pythonFile);
    fileInput.accept(trivialTypeInferenceVisitor);

    inferTypesAndMemberAccessSymbols(fileInput);

    fileInput.accept(new BaseTreeVisitor() {
      @Override
      public void visitFunctionDef(FunctionDef funcDef) {
        super.visitFunctionDef(funcDef);
        inferTypesAndMemberAccessSymbols(funcDef);
      }
    });
  }


  private void inferTypesAndMemberAccessSymbols(FileInput fileInput) {
    StatementList statements = fileInput.statements();
    if (statements == null) {
      return;
    }
    var moduleSymbols = symbolTable.getSymbolsByRootTree(fileInput);

    inferTypesAndMemberAccessSymbols(
      fileInput,
      statements,
      moduleSymbols,
      Collections.emptySet(),
      () -> ControlFlowGraph.build(fileInput, pythonFile)
    );
  }

  private void inferTypesAndMemberAccessSymbols(FunctionDef functionDef) {
    Set<Name> parameterNames = TreeUtils.nonTupleParameters(functionDef).stream()
      .map(Parameter::name)
      .collect(Collectors.toSet());
    Set<SymbolV2> localVariables = symbolTable.getSymbolsByRootTree(functionDef);
    inferTypesAndMemberAccessSymbols(
      functionDef,
      functionDef.body(),
      localVariables,
      parameterNames,
      () -> ControlFlowGraph.build(functionDef, pythonFile)
    );
  }


  private static void inferTypesAndMemberAccessSymbols(Tree scopeTree,
    StatementList statements,
    Set<SymbolV2> declaredVariables,
    Set<Name> annotatedParameterNames,
    Supplier<ControlFlowGraph> controlFlowGraphSupplier
  ) {
    PropagationVisitor propagationVisitor = new PropagationVisitor();
    scopeTree.accept(propagationVisitor);
    Set<Name> assignedNames = propagationVisitor.propagationsByLhs().values().stream()
      .flatMap(Collection::stream)
      .map(Propagation::lhsName)
      .collect(Collectors.toSet());

    TryStatementVisitor tryStatementVisitor = new TryStatementVisitor();
    statements.accept(tryStatementVisitor);
    if (tryStatementVisitor.hasTryStatement()) {
      // CFG doesn't model precisely try-except statements. Hence we fallback to AST based type inference
      propagationVisitor.processPropagations(getTrackedVars(declaredVariables, assignedNames));
    } else {
      ControlFlowGraph cfg = controlFlowGraphSupplier.get();
      if (cfg == null) {
        return;
      }
      assignedNames.addAll(annotatedParameterNames);
      flowSensitiveTypeInference(cfg, getTrackedVars(declaredVariables, assignedNames), propagationVisitor);
    }
  }

  private static void flowSensitiveTypeInference(ControlFlowGraph cfg, Set<SymbolV2> trackedVars, PropagationVisitor propagationVisitor) {
    // TODO: infer parameter type based on type hint or default value assignement
    var parameterTypes = trackedVars
      .stream()
      .filter(s -> s.usages().stream().map(UsageV2::kind).anyMatch(UsageV2.Kind.PARAMETER::equals))
      .collect(Collectors.toMap(SymbolV2::name, v -> PythonType.UNKNOWN));

    FlowSensitiveTypeInference flowSensitiveTypeInference = new FlowSensitiveTypeInference(
      trackedVars,
      propagationVisitor.assignmentsByAssignmentStatement(),
      propagationVisitor.definitionsByDefinitionStatement(),
      parameterTypes);

    flowSensitiveTypeInference.compute(cfg);
    flowSensitiveTypeInference.compute(cfg);
  }

  private static Set<SymbolV2> getTrackedVars(Set<SymbolV2> localVariables, Set<Name> assignedNames) {
    Set<SymbolV2> trackedVars = new HashSet<>();
    for (SymbolV2 variable : localVariables) {
      boolean hasMissingBindingUsage = variable.usages().stream()
        .filter(UsageV2::isBindingUsage)
        .anyMatch(u -> !assignedNames.contains(u.tree()));
      boolean isGlobal = variable.usages().stream().anyMatch(v -> v.kind().equals(UsageV2.Kind.GLOBAL_DECLARATION));
      if (!hasMissingBindingUsage && !isGlobal) {
        trackedVars.add(variable);
      }
    }
    return trackedVars;
  }

}
