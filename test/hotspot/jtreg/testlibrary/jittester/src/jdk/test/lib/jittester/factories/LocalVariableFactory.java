/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk.test.lib.jittester.factories;

import java.util.ArrayList;

import jdk.test.lib.jittester.LocalVariable;
import jdk.test.lib.jittester.ProductionFailedException;
import jdk.test.lib.jittester.Symbol;
import jdk.test.lib.jittester.SymbolTable;
import jdk.test.lib.jittester.Type;
import jdk.test.lib.jittester.VariableInfo;
import jdk.test.lib.jittester.utils.Genome;
import jdk.test.lib.jittester.utils.PseudoRandom;
import jdk.test.lib.jittester.Logger;

class LocalVariableFactory extends Factory<LocalVariable> {
    private static final String MAGNET_CHANNEL = "use.local.variable";
    private final Type type;
    private final int flags;

    LocalVariableFactory(Type type, int flags) {
        this.type = type;
        this.flags = flags;
    }

    @Override
    public LocalVariable produce() throws ProductionFailedException {
        Logger.log(PseudoRandom.getCurrentSeed() == 132795661563799L,
                "LocalVariableFactory :type");
        // Get the variables of the requested type from SymbolTable
        ArrayList<Symbol> allVariables = new ArrayList<>(SymbolTable.get(type, VariableInfo.class));
        if (!allVariables.isEmpty()) {
            ArrayList<Symbol> eligible = new ArrayList<>();
            for (Symbol symbol : allVariables) {
                VariableInfo varInfo = (VariableInfo) symbol;
                if (ThisVariableControl.isThisForbidden() && "this".equals(varInfo.name)) {
                    continue;
                }
                if ((varInfo.flags & VariableInfo.FINAL) == (flags & VariableInfo.FINAL)
                        && (varInfo.flags & VariableInfo.INITIALIZED) == (flags & VariableInfo.INITIALIZED)
                        && (varInfo.flags & VariableInfo.LOCAL) > 0) {
                    eligible.add(varInfo);
                }
            }
            if (eligible.isEmpty()) {
                throw new ProductionFailedException();
            }
            VariableInfo selected;
            if (Genome.isReplayActive()) {
                long replayTargetGene = SymbolTable.consumeMagnetTargetGene(MAGNET_CHANNEL);
                selected = (VariableInfo) SymbolTable.attractByMagnet(eligible, replayTargetGene, MAGNET_CHANNEL);
            } else {
                // Replay selects directly by recorded magnet target and does not consume shuffle RNG events.
                // Keep this shuffle out of genome event stream.
                PseudoRandom.shuffleSilent(eligible);
                selected = (VariableInfo) eligible.get(0);
                SymbolTable.recordMagnetTargetGene(MAGNET_CHANNEL, selected.getMagnetismGeneId());
            }
            return new LocalVariable(selected);
        }
        throw new ProductionFailedException();
    }
}
