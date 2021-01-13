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
package org.sonar.plugins.python.cpd;

import com.sonar.sslr.api.GenericTokenType;
import com.sonar.sslr.api.TokenType;
import java.util.List;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.cpd.NewCpdTokens;
import org.sonar.plugins.python.api.PythonVisitorContext;
import org.sonar.python.TokenLocation;
import org.sonar.python.api.PythonTokenType;
import org.sonar.plugins.python.api.tree.Token;
import org.sonar.plugins.python.api.tree.Tree;
import org.sonar.python.tree.TreeUtils;

public class PythonCpdAnalyzer {

  private final SensorContext context;

  public PythonCpdAnalyzer(SensorContext context) {
    this.context = context;
  }

  public void pushCpdTokens(InputFile inputFile, PythonVisitorContext visitorContext) {
    Tree root = visitorContext.rootTree();
    if (root != null) {
      NewCpdTokens cpdTokens = context.newCpdTokens().onFile(inputFile);
      List<Token> tokens = TreeUtils.tokens(root);
      for (int i = 0; i < tokens.size(); i++) {
        Token token = tokens.get(i);
        TokenType currentTokenType = token.type();
        TokenType nextTokenType = i + 1 < tokens.size() ? tokens.get(i + 1).type() : GenericTokenType.EOF;
        // INDENT/DEDENT could not be completely ignored during CPD see https://docs.python.org/3/reference/lexical_analysis.html#indentation
        // Just taking into account DEDENT is enough, but because the DEDENT token has an empty value, it's the
        // preceding new line which is added in its place to create a difference
        if (isNewLineWithIndentationChange(currentTokenType, nextTokenType) || !isIgnoredType(currentTokenType)) {
          TokenLocation location = new TokenLocation(token);
          cpdTokens.addToken(location.startLine(), location.startLineOffset(), location.endLine(), location.endLineOffset(), token.value());
        }
      }
      cpdTokens.save();
    }
  }

  private static boolean isNewLineWithIndentationChange(TokenType currentTokenType, TokenType nextTokenType) {
    return currentTokenType.equals(PythonTokenType.NEWLINE) && nextTokenType.equals(PythonTokenType.DEDENT);
  }

  private static boolean isIgnoredType(TokenType type) {
    return type.equals(PythonTokenType.NEWLINE) ||
      type.equals(PythonTokenType.DEDENT) ||
      type.equals(PythonTokenType.INDENT) ||
      type.equals(GenericTokenType.EOF);
  }

}
