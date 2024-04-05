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
package org.sonar.python.types.v2;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.sonar.plugins.python.api.PythonFile;
import org.sonar.plugins.python.api.tree.BaseTreeVisitor;
import org.sonar.plugins.python.api.tree.CallExpression;
import org.sonar.plugins.python.api.tree.ClassDef;
import org.sonar.plugins.python.api.tree.FileInput;
import org.sonar.plugins.python.api.tree.FunctionDef;
import org.sonar.plugins.python.api.tree.Name;
import org.sonar.plugins.python.api.tree.QualifiedExpression;
import org.sonar.plugins.python.api.tree.Tree;
import org.sonar.python.tree.NameImpl;
import org.sonar.python.tree.TreeUtils;
import org.sonar.python.types.pytype.PyTypeTable;
import org.sonar.python.types.v2.converter.PyTypeConverter;

public class TypesTableBuilder extends BaseTreeVisitor {

  private final PyTypeTable pyTypeTable;
  private final TypesTable typesTable;
  private final String filePath;
  private final Deque<PythonType> typesStack;
  private boolean isFunctionDefName;
  private boolean isClassDefName;
  private boolean isClassArgName;

  public TypesTableBuilder(PyTypeTable pyTypeTable, TypesTable typesTable, PythonFile pythonFile) {
    this.pyTypeTable = pyTypeTable;
    this.typesTable = typesTable;
    this.filePath = getFilePath(pythonFile);
    this.isFunctionDefName = false;
    this.isClassDefName = false;
    this.isClassArgName = false;
    this.typesStack = new ArrayDeque<>();
  }

  private String getFilePath(PythonFile pythonFile) {
    if (pythonFile.key().indexOf(':') != -1) {
      return pythonFile.key().substring(pythonFile.key().indexOf(':') + 1);
    } else {
      return pythonFile.key();
    }
  }

  public void annotate(FileInput fileInput) {
    fileInput.accept(this);
  }

  @Override
  public void visitClassDef(ClassDef classDef) {
    scan(classDef.decorators());
    isClassDefName = true;
    scan(classDef.name());
    isClassDefName = false;
    scan(classDef.typeParams());
    isClassArgName = true;
    scan(classDef.args());
    isClassArgName = false;
    scan(classDef.body());
    typesStack.poll();
  }

  @Override
  public void visitFunctionDef(FunctionDef functionDef) {
    scan(functionDef.decorators());
    isFunctionDefName = true;
    scan(functionDef.name());
    isFunctionDefName = false;
    scan(functionDef.typeParams());
    scan(functionDef.parameters());
    scan(functionDef.returnTypeAnnotation());
    scan(functionDef.body());
  }

  @Override
  public void visitCallExpression(CallExpression callExpression) {
    super.visitCallExpression(callExpression);
  }

  @Override
  public void visitQualifiedExpression(QualifiedExpression qualifiedExpression) {
    scan(qualifiedExpression.qualifier());
    var qualifiedExpressionName = qualifiedExpression.name();

    var qualifierType = qualifiedExpression.qualifier().pythonType();
    Optional.of(qualifierType)
      .filter(ObjectType.class::isInstance)
      .map(ObjectType.class::cast)
      .map(ObjectType::type)
      .filter(ClassType.class::isInstance)
      .map(ClassType.class::cast)
      .map(ClassType::members)
      .stream()
      .flatMap(Collection::stream)
      .filter(member -> Objects.equals(qualifiedExpressionName.name(), member.name()))
      .findFirst()
      .ifPresent(member -> {
        if (qualifiedExpressionName instanceof NameImpl name) {
          name.pythonType(new ObjectType(member.type(), new ArrayList<>()));
        }
      });
  }

  @Override
  public void visitName(Name name) {
    PythonType type;
    if (isClassDefName) {
      type = getTypeForClassDefName(filePath, name);
      typesStack.add(type);
    } else if (isFunctionDefName) {
      type = getTypeForFunctionDefName(filePath, name);
    } else if (isClassArgName) {
      type = getTypeForClassDefName(filePath, name);
      if (!typesStack.isEmpty() && typesStack.pop() instanceof ClassType classType) {
        classType.superClasses().add(type);
      }
    } else {
      type = getTypeForName(filePath, name);
    }
    if (name instanceof NameImpl ni) {
      ni.pythonType(type);
    }
    super.visitName(name);
  }

  private PythonType getTypeForClassDefName(String fileName, Name name) {
    var classType = typesTable.addType(new ClassType(name.name()));
    return classType;
  }

  private PythonType getTypeForName(String fileName, Name name) {
    return pyTypeTable.getVariableTypeFor(fileName, name)
      .map(pyTypeInfo -> {
        var pythonType = PyTypeConverter.convert(typesTable, pyTypeInfo);
        pythonType = typesTable.addType(pythonType);
        return (PythonType) new ObjectType(pythonType, List.of());
      }).orElse(PythonType.UNKNOWN);
  }

  private PythonType getTypeForFunctionDefName(String fileName, Name name) {
    return Optional.of(name)
      .map(Tree::parent)
      .map(Tree::parent)
      .map(Tree::parent)
      .flatMap(TreeUtils.toOptionalInstanceOfMapper(ClassDef.class))
      .map(classDef -> {
        var classType = (ClassType) classDef.name().pythonType();
        return pyTypeTable.getMethodTypeFor(fileName, name)
          .map(pyTypeInfo -> {
            var pythonType = (FunctionType) PyTypeConverter.convert(typesTable, pyTypeInfo, false);
            classType.members().add(new Member(pythonType.name(), pythonType));
            return (PythonType) pythonType;
          }).orElse(PythonType.UNKNOWN);
      }).orElseGet(() -> {
        return pyTypeTable.getFunctionTypeFor(fileName, name)
          .map(pyTypeInfo -> {
            var pythonType = PyTypeConverter.convert(typesTable, pyTypeInfo);
            pythonType = typesTable.addType(pythonType);
            return pythonType;
          }).orElse(PythonType.UNKNOWN);
      });

  }
}