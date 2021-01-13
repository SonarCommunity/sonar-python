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
package org.sonar.python.cfg;


import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.mockito.Mockito;
import org.sonar.plugins.python.api.cfg.CfgBlock;
import org.sonar.plugins.python.api.cfg.ControlFlowGraph;
import org.sonar.plugins.python.api.tree.FileInput;
import org.sonar.plugins.python.api.tree.FunctionDef;
import org.sonar.plugins.python.api.PythonFile;
import org.sonar.python.PythonTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class CfgUtilsTest {

  private PythonFile file = Mockito.mock(PythonFile.class, "file1.py");

  @Test
  public void unreachableBlocks_empty() {
    ControlFlowGraph cfg = cfg(
      "x = 10"
    );
    assertThat(CfgUtils.unreachableBlocks(cfg)).isEmpty();
  }

  @Test
  public void unreachableBlocks_one() {
    ControlFlowGraph cfg = cfg(
      "x = 10",
      "return",
      "y = 42"
    );
    assertThat(CfgUtils.unreachableBlocks(cfg)).containsExactlyInAnyOrder(cfg.start().syntacticSuccessor());
  }

  @Test
  public void unreachableBlocks_conditional() {
    ControlFlowGraph cfg = cfg(
      "x = 10",
      "return",
      "if x > 9:",
      "  print(x)",
      "y = 42"
    );

    Set<CfgBlock> unreachableBlocks = new HashSet<>();
    CfgBlock syntacticSuccessor = cfg.start().syntacticSuccessor();
    unreachableBlocks.add(syntacticSuccessor);
    unreachableBlocks.addAll(syntacticSuccessor.successors());
    assertThat(CfgUtils.unreachableBlocks(cfg)).isEqualTo(unreachableBlocks);
  }



  private ControlFlowGraph cfg(String... lines) {
    FileInput fileInput = PythonTestUtils.parse("def wrapper():", Arrays.stream(lines).map(s -> "  " + s).collect(Collectors.joining("\n")));
    FunctionDef fun = (FunctionDef) fileInput.statements().statements().get(0);
    return ControlFlowGraph.build(fun, file);
  }
}
