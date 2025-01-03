/*
 * SonarQube Python Plugin
 * Copyright (C) 2011-2025 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource SA.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the Sonar Source-Available License for more details.
 *
 * You should have received a copy of the Sonar Source-Available License
 * along with this program; if not, see https://sonarsource.com/license/ssal/
 */
package org.sonar.python.tree;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.sonar.plugins.python.api.tree.Statement;
import org.sonar.plugins.python.api.tree.StatementList;
import org.sonar.plugins.python.api.tree.Tree;
import org.sonar.plugins.python.api.tree.TreeVisitor;

public class StatementListImpl extends PyTree implements StatementList {

  private List<Statement> statements;

  public StatementListImpl(List<Statement> statements) {
    this.statements = statements;
  }

  @Override
  public List<Statement> statements() {
    return statements;
  }

  @Override
  public void accept(TreeVisitor visitor) {
    visitor.visitStatementList(this);
  }

  @Override
  public Kind getKind() {
    return Kind.STATEMENT_LIST;
  }

  @Override
  public List<Tree> computeChildren() {
    return Stream.of(statements).flatMap(List::stream).filter(Objects::nonNull).collect(Collectors.toList());
  }

}
