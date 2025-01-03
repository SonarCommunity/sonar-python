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
package org.sonar.plugins.python.api.tree;

/**
 * Star Pattern
 * Only used inside sequence patterns
 *
 * <pre>
 *   case [x, y, *others]:
 *     ...
 * </pre>
 *
 * See https://docs.python.org/3/reference/compound_stmts.html#grammar-token-python-grammar-star_pattern
 */
public interface StarPattern extends Pattern {

  Token starToken();

  /**
   * Return value can only be either {@link CapturePattern} or {@link WildcardPattern}
   */
  Pattern pattern();
}
