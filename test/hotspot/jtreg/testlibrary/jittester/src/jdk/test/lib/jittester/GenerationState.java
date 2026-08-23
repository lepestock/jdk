/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib.jittester;

import jdk.test.lib.jittester.morph.MorphContext;
import jdk.test.lib.jittester.utils.PseudoRandom;

/**
 * Aggregates mutable generation state carriers and provides atomic checkpoint/rollback.
 * Current scope: {@link SymbolTable}, {@link TypeList}, {@link ScopeGuards},
 * {@link FlowParams}, and {@link MorphContext}.
 */
public final class GenerationState {
    private static FlowParams currentFlowParams;
    private static MorphContext currentMorphContext = MorphContext.EMPTY;
    private static String currentMainClassName;

    private GenerationState() {
    }

    public static void initializeFlowParamsFromProductionParams() {
        currentFlowParams = FlowParams.fromProductionParams();
        currentMorphContext = MorphContext.EMPTY;
    }

    public static FlowParams currentFlowParams() {
        if (currentFlowParams == null) {
            initializeFlowParamsFromProductionParams();
        }
        return currentFlowParams;
    }

    public static void setCurrentFlowParams(FlowParams flowParams) {
        if (flowParams == null) {
            throw new IllegalArgumentException("GenerationState flow params must not be null");
        }
        currentFlowParams = flowParams;
    }

    public static MorphContext currentMorphContext() {
        return currentMorphContext;
    }

    public static void setCurrentMorphContext(MorphContext morphContext) {
        if (morphContext == null) {
            throw new IllegalArgumentException("GenerationState morph context must not be null");
        }
        currentMorphContext = morphContext;
    }

    public static void setCurrentMainClassName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("GenerationState main class name must not be blank");
        }
        currentMainClassName = name;
    }

    public static String currentMainClassName() {
        if (currentMainClassName == null || currentMainClassName.isBlank()) {
            throw new IllegalStateException("GenerationState main class name is not initialized");
        }
        return currentMainClassName;
    }

    public static Checkpoint checkpoint() {
        return new Checkpoint(
                SymbolTable.checkpoint(),
                TypeList.checkpoint(),
                ScopeGuards.checkpoint(),
                currentFlowParams(),
                currentMorphContext());
    }

    public static void rollbackTo(Checkpoint checkpoint) {
        if (checkpoint == null) {
            throw new IllegalArgumentException("GenerationState checkpoint must not be null");
        }
        SymbolTable.rollbackToCheckpoint(checkpoint.symbolCheckpoint());
        TypeList.rollbackToCheckpoint(checkpoint.typeCheckpoint());
        ScopeGuards.rollbackToCheckpoint(checkpoint.scopeGuardsCheckpoint);
        currentFlowParams = checkpoint.flowParamsCheckpoint;
        currentMorphContext = checkpoint.morphContextCheckpoint;
    }

    public static String dumpSnapshot() {
        return dumpSnapshot("<none>");
    }

    public static String dumpSnapshot(String requesterGene) {
        StringBuilder sb = new StringBuilder();
        sb.append("GenerationState{requesterGene=")
                .append(requesterGene == null ? "<none>" : requesterGene)
                .append(", rngSeed=")
                .append(PseudoRandom.getCurrentSeed())
                .append("}\n");
        sb.append("--- FlowParams ---\n");
        sb.append(currentFlowParams().dumpSnapshot()).append('\n');
        sb.append("--- MorphContext ---\n");
        sb.append(currentMorphContext()).append('\n');
        sb.append("--- SymbolTable ---\n");
        sb.append(SymbolTable.dumpSnapshot(Integer.MAX_VALUE, Integer.MAX_VALUE));
        sb.append("--- TypeList ---\n");
        sb.append(TypeList.dumpSnapshot(Integer.MAX_VALUE));
        return sb.toString();
    }

    public static final class Checkpoint {
        private final int symbolCheckpoint;
        private final int typeCheckpoint;
        private final ScopeGuards.Checkpoint scopeGuardsCheckpoint;
        private final FlowParams flowParamsCheckpoint;
        private final MorphContext morphContextCheckpoint;

        private Checkpoint(int symbolCheckpoint,
                           int typeCheckpoint,
                           ScopeGuards.Checkpoint scopeGuardsCheckpoint,
                           FlowParams flowParamsCheckpoint,
                           MorphContext morphContextCheckpoint) {
            this.symbolCheckpoint = symbolCheckpoint;
            this.typeCheckpoint = typeCheckpoint;
            this.scopeGuardsCheckpoint = scopeGuardsCheckpoint;
            this.flowParamsCheckpoint = flowParamsCheckpoint;
            this.morphContextCheckpoint = morphContextCheckpoint;
        }

        public int symbolCheckpoint() {
            return symbolCheckpoint;
        }

        public int typeCheckpoint() {
            return typeCheckpoint;
        }
    }

}
