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
package org.sonar.python.semantic.v2.types;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.sonar.plugins.python.api.tree.AnnotatedAssignment;
import org.sonar.plugins.python.api.tree.AssignmentStatement;
import org.sonar.plugins.python.api.tree.CompoundAssignmentStatement;
import org.sonar.plugins.python.api.tree.Expression;
import org.sonar.plugins.python.api.tree.FunctionDef;
import org.sonar.plugins.python.api.tree.Name;
import org.sonar.plugins.python.api.tree.Parameter;
import org.sonar.plugins.python.api.tree.Statement;
import org.sonar.plugins.python.api.tree.Tree;
import org.sonar.python.cfg.fixpoint.ForwardAnalysis;
import org.sonar.python.cfg.fixpoint.ProgramState;
import org.sonar.python.semantic.v2.SymbolV2;
import org.sonar.python.types.v2.PythonType;

public class FlowSensitiveTypeInference extends ForwardAnalysis {
  private final Set<SymbolV2> trackedVars;
  private final Map<Statement, Assignment> assignmentsByAssignmentStatement;
  private final Map<Statement, Definition> definitionsByDefinitionStatement;
  private final Map<String, PythonType> parameterTypesByName;

  public FlowSensitiveTypeInference(
    Set<SymbolV2> trackedVars,
    Map<Statement, Assignment> assignmentsByAssignmentStatement,
    Map<Statement, Definition> definitionsByDefinitionStatement,
    Map<String, PythonType> parameterTypesByName
  ) {
    this.trackedVars = trackedVars;
    this.assignmentsByAssignmentStatement = assignmentsByAssignmentStatement;
    this.definitionsByDefinitionStatement = definitionsByDefinitionStatement;
    this.parameterTypesByName = parameterTypesByName;
  }

  @Override
  public ProgramState initialState() {
    TypeInferenceProgramState initialState = new TypeInferenceProgramState();
    for (SymbolV2 variable : trackedVars) {
      initialState.setTypes(variable, Set.of());
    }
    return initialState;
  }

  @Override
  public void updateProgramState(Tree element, ProgramState programState) {
    TypeInferenceProgramState state = (TypeInferenceProgramState) programState;
    if (element instanceof AssignmentStatement assignment) {
      // update rhs
      updateTree(assignment.assignedValue(), state);
      handleAssignment(assignment, state);
      // update lhs
      assignment.lhsExpressions().forEach(lhs -> updateTree(lhs, state));
    } else if (element instanceof CompoundAssignmentStatement) {
      // Assumption: compound assignments don't change types
      updateTree(element, state);
    } else if (element instanceof AnnotatedAssignment assignment) {
      var assignedValue = assignment.assignedValue();
      if (assignedValue != null) {
        handleAssignment(assignment, state);
        updateTree(assignedValue, state);
        // update lhs
        updateTree(assignment.variable(), state);
      }
    } else if (element instanceof FunctionDef functionDef) {
      handleDefinition(functionDef, state);
    } else if (element instanceof Parameter parameter) {
      handleParameter(parameter, state);
    } else {
      // Here we should run "isinstance" visitor when we handle declared types, to avoid FPs when type guard checks are made
      updateTree(element, state);
    }
  }

  private void handleParameter(Parameter parameter, TypeInferenceProgramState state) {
    var name = parameter.name();

    if (name == null || !trackedVars.contains(name.symbolV2())) {
      return;
    }

    var type = parameterTypesByName.getOrDefault(name.name(), PythonType.UNKNOWN);
    state.setTypes(name.symbolV2(), new HashSet<>(Set.of(type)));
    updateTree(name, state);
  }

  private static void updateTree(Tree tree, TypeInferenceProgramState state) {
    tree.accept(new ProgramStateTypeInferenceVisitor(state));
  }

  private void handleAssignment(Statement assignmentStatement, TypeInferenceProgramState programState) {
    Optional.ofNullable(assignmentsByAssignmentStatement.get(assignmentStatement))
      .ifPresent(assignment -> {
        if (trackedVars.contains(assignment.lhsSymbol())) {
          Expression rhs = assignment.rhs();
          // strong update
          if (rhs instanceof Name rhsName && trackedVars.contains(rhsName.symbolV2())) {
            SymbolV2 rhsSymbol = rhsName.symbolV2();
            programState.setTypes(assignment.lhsSymbol(), programState.getTypes(rhsSymbol));
          } else {
            programState.setTypes(assignment.lhsSymbol(), Set.of(rhs.typeV2()));
          }
        }
      });
  }

  private void handleDefinition(Statement definitionStatement, TypeInferenceProgramState programState) {
    Optional.ofNullable(definitionsByDefinitionStatement.get(definitionStatement))
      .ifPresent(definition -> {
        SymbolV2 symbol = definition.lhsSymbol();
        if (trackedVars.contains(symbol)) {
          programState.setTypes(symbol, Set.of(definition.lhsName.typeV2()));
        }
      });
  }
}
