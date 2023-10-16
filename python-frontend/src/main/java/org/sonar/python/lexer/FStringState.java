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
package org.sonar.python.lexer;


public class FStringState {

  Character quote;
  int nestingLevel;
  int numberOfQuotes;
  boolean isInFString = true;

  public enum Mode{
    REGULAR_MODE,
    FSTRING_MODE,
    FORMAT_SPECIFIER_MODE
  }
  
  private Mode tokenizerMode;

  public FStringState(Mode mode, int nestingLevel) {
    this.tokenizerMode = mode;
    this.nestingLevel = nestingLevel;
  }

  public FStringState(Mode mode, int nestingLevel, boolean isInFString) {
    this.tokenizerMode = mode;
    this.nestingLevel = nestingLevel;
    this.isInFString = isInFString;
  }
  public Character getQuote() {
    return quote;
  }

  public void setQuote(Character quote) {
    this.quote = quote;
  }

  public Mode getTokenizerMode() {
    return tokenizerMode;
  }

  public int getNestingLevel() {
    return nestingLevel;
  }

  public int getNumberOfQuotes() {
    return numberOfQuotes;
  }

  public void setNumberOfQuotes(int numberOfQuotes) {
    this.numberOfQuotes = numberOfQuotes;
  }
}
