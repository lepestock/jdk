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
import java.util.stream.Collectors;

import jdk.test.lib.jittester.IRNode;
import jdk.test.lib.jittester.GenerationState;
import jdk.test.lib.jittester.LocalVariable;
import jdk.test.lib.jittester.NonStaticMemberVariable;
import jdk.test.lib.jittester.ProductionFailedException;
import jdk.test.lib.jittester.Symbol;
import jdk.test.lib.jittester.SymbolTable;
import jdk.test.lib.jittester.Type;
import jdk.test.lib.jittester.VariableInfo;
import jdk.test.lib.jittester.types.TypeKlass;
import jdk.test.lib.jittester.utils.Genome;
import jdk.test.lib.jittester.utils.PseudoRandom;
import jdk.test.lib.jittester.Logger;

class NonStaticMemberVariableFactory extends Factory<NonStaticMemberVariable> {
    private static final String MAGNET_CHANNEL = "use.member.variable";
    private static final boolean DEBUG_REPLAY_FAIL =
            Boolean.getBoolean("jittester.debug.member.replay.fail");
    private static final boolean DEBUG_SELECTION =
            Boolean.getBoolean("jittester.debug.member.selection");
    private final Type type;
    private final int flags;
    private final int operatorLimit;
    private final boolean exceptionSafe;
    private final Type ownerClass;

    NonStaticMemberVariableFactory(int operatorLimit,
            TypeKlass ownerClass, Type type, int flags, boolean exceptionSafe) {
        this.ownerClass = ownerClass;
        this.type = type;
        this.flags = flags;
        this.operatorLimit = operatorLimit;
        this.exceptionSafe = exceptionSafe;
    }

    @Override
    public NonStaticMemberVariable produce() throws ProductionFailedException {
        // Get the variables of the requested type from SymbolTable
        long SEED = PseudoRandom.getCurrentSeed();
        ArrayList<Symbol> variables = new ArrayList<>(SymbolTable.get(type, VariableInfo.class));
        Logger.log(SEED == 194820577109216L,
                ":variables " + variables.stream().map(Symbol::toString).collect(Collectors.joining(", ")));
        if (!variables.isEmpty()) {
            boolean replayMode = Genome.isReplayActive();
            if (replayMode) {
                long replayTargetGene = SymbolTable.consumeMagnetTargetGene(MAGNET_CHANNEL);
                // Previous record attempt for this selection point ended up with no usable variables.
                if (replayTargetGene == -1L) {
                    throw new ProductionFailedException();
                }
                Symbol selected = SymbolTable.attractByMagnet(variables, replayTargetGene, MAGNET_CHANNEL);
                variables = new ArrayList<>();
                variables.add(selected);
            }
            IRNodeBuilder builder = new IRNodeBuilder().withOperatorLimit(operatorLimit)
                    .setOwnerKlass((TypeKlass) ownerClass)
                    .setExceptionSafe(exceptionSafe)
                    .setNoConsts(false);
            for (Symbol symbol : variables) {
                VariableInfo varInfo = (VariableInfo) symbol;
                if ((varInfo.flags & VariableInfo.FINAL) == (flags & VariableInfo.FINAL)
                        && (varInfo.flags & VariableInfo.INITIALIZED) == (flags & VariableInfo.INITIALIZED)
                        && (varInfo.flags & VariableInfo.STATIC) == 0
                        && (varInfo.flags & VariableInfo.LOCAL) == 0
                        && VariableFactory.hasAccessibleReceiver((TypeKlass) ownerClass, varInfo)) {
                    GenerationState.Checkpoint stateCheckpoint = GenerationState.checkpoint();
                    try {
                        if (DEBUG_SELECTION) {
                            System.err.println("[JTDBG][NonStaticMemberVariableFactory] selection mode="
                                    + (replayMode ? "replay" : "record")
                                    + " channel=" + MAGNET_CHANNEL
                                    + " ownerClass=" + ownerClass
                                    + " requestedType=" + type
                                    + " selectedOwner=" + varInfo.owner
                                    + " selectedName=" + varInfo.name
                                    + " selectedMagnet=" + varInfo.getMagnetismGeneId()
                                    + " flags=" + varInfo.flags);
                        }
                        if (!replayMode) {
                            Genome.beginSpeculativeRecord();
                            SymbolTable.recordMagnetTargetSelection(MAGNET_CHANNEL, varInfo.getMagnetismGeneId(),
                                    variables, varInfo);
                        }
                        Logger.log(SEED == 194820577109216L, ":expressionSeed " + PseudoRandom.getCurrentSeed());
                        IRNode object;
                        if (VariableFactory.canUseImplicitThis((TypeKlass) ownerClass, varInfo)) {
                            VariableInfo thisInfo = new VariableInfo("this", varInfo.owner, varInfo.owner,
                                    VariableInfo.FINAL | VariableInfo.LOCAL | VariableInfo.INITIALIZED);
                            object = new LocalVariable(thisInfo);
                        } else {
                            object = builder.setResultType(varInfo.owner)
                                    .getExpressionFactory().produce();
                        }
                        if (!replayMode) {
                            Genome.commitSpeculativeRecord();
                        }
                        return new NonStaticMemberVariable(object, varInfo);
                    } catch (ProductionFailedException e) {
                        GenerationState.rollbackTo(stateCheckpoint);
                        if (!replayMode) {
                            Genome.rollbackSpeculativeRecord();
                        } else {
                            if (DEBUG_REPLAY_FAIL) {
                                System.err.println("[JTDBG][NonStaticMemberVariableFactory] replay failure on selected member"
                                        + " channel=" + MAGNET_CHANNEL
                                        + " ownerClass=" + ownerClass
                                        + " requestedType=" + type
                                        + " selectedOwner=" + varInfo.owner
                                        + " selectedName=" + varInfo.name
                                        + " selectedMagnet=" + varInfo.getMagnetismGeneId()
                                        + " flags=" + varInfo.flags);
                                System.err.print(GenerationState.dumpSnapshot(
                                        "C" + varInfo.getMagnetismGeneId()));
                                e.printStackTrace(System.err);
                            }
                            throw new RuntimeException("Genome broken around magnet gene "
                                    + varInfo.getMagnetismGeneId() + " for channel '" + MAGNET_CHANNEL
                                    + "': selected variable failed in replay", e);
                        }
                    } catch (RuntimeException e) {
                        GenerationState.rollbackTo(stateCheckpoint);
                        if (!replayMode) {
                            Genome.rollbackSpeculativeRecord();
                        }
                        throw e;
                    }
                }
            }
            if (!replayMode) {
                SymbolTable.recordMagnetTargetGene(MAGNET_CHANNEL, -1L);
            }
        }
        throw new ProductionFailedException();
    }
}
