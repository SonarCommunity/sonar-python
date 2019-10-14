/*
 * SonarQube Python Plugin
 * Copyright (C) 2011-2019 SonarSource SA
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

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.sonar.plugins.python.api.cfg.CfgBlock;
import org.sonar.python.api.tree.Tree;

public abstract class PythonCfgBlock implements CfgBlock {

  private final LinkedList<Tree> elements = new LinkedList<>();
  private final Set<CfgBlock> predecessors = new HashSet<>();

  @Override
  public Set<CfgBlock> predecessors() {
    return predecessors;
  }

  @Override
  public List<Tree> elements() {
    return elements;
  }

  public void addElement(Tree tree) {
    elements.addFirst(tree);
  }

  public boolean isEmptyBlock() {
    return elements.isEmpty() && successors().size() == 1;
  }

  PythonCfgBlock firstNonEmptySuccessor() {
    PythonCfgBlock block = this;
    Set<CfgBlock> skippedBlocks = new HashSet<>();
    while (block.isEmptyBlock()) {
      PythonCfgBlock next = (PythonCfgBlock) block.successors().iterator().next();
      if (skippedBlocks.add(next)) {
        block = next;
      } else {
        return block;
      }
    }
    return block;
  }

  /**
   * Replace successors based on a replacement map.
   * This method is used when we remove empty blocks:
   * we have to replace empty successors in the remaining blocks by non-empty successors.
   */
  abstract void replaceSuccessors(Map<PythonCfgBlock, PythonCfgBlock> replacements);

  @Override
  public String toString() {
    if (elements.isEmpty()) {
      return "empty";
    }
    return elements.stream().map(elem -> elem.getKind().toString()).collect(Collectors.joining(";"));
  }

  void addPredecessor(PythonCfgBlock block) {
    predecessors.add(block);
  }
}
