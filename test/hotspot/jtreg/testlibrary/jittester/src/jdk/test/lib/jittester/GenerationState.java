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

import jdk.test.lib.jittester.utils.PseudoRandom;

/**
 * Aggregates mutable generation state carriers and provides atomic checkpoint/rollback.
 * Current scope: {@link SymbolTable}, {@link TypeList}, {@link ScopeGuards}, and {@link FlowParams}.
 */
public final class GenerationState {
    private static final int[] BYTE_LENGTH_ANCHORS = {
            0, 1, 2, 3, 4, 7, 8, 15, 16, 17, 31, 32, 33, 63, 64, 65,
            127, 128, 129, 255, 256, 257, 511, 512
    };
    private static final int SMALL_BYTE_ANCHOR_THRESHOLD = 64;
    private static final int SMALL_ANCHOR_BIAS_PERCENT = 85;
    private static FlowParams currentFlowParams;
    private static int preferredIntCollectionSize;

    private GenerationState() {
    }

    public static void initializeFlowParamsFromProductionParams() {
        currentFlowParams = FlowParams.fromProductionParams();
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

    public static void initializePreferredIntCollectionSize() {
        preferredIntCollectionSize = Math.max(1, choosePreferredByteAnchor() / 4);
    }

    public static int preferredIntCollectionSize() {
        if (preferredIntCollectionSize < 1) {
            initializePreferredIntCollectionSize();
        }
        return preferredIntCollectionSize;
    }

    public static Checkpoint checkpoint() {
        return new Checkpoint(
                SymbolTable.checkpoint(),
                TypeList.checkpoint(),
                ScopeGuards.checkpoint(),
                currentFlowParams());
    }

    public static void rollbackTo(Checkpoint checkpoint) {
        if (checkpoint == null) {
            throw new IllegalArgumentException("GenerationState checkpoint must not be null");
        }
        SymbolTable.rollbackToCheckpoint(checkpoint.symbolCheckpoint());
        TypeList.rollbackToCheckpoint(checkpoint.typeCheckpoint());
        ScopeGuards.rollbackToCheckpoint(checkpoint.scopeGuardsCheckpoint);
        currentFlowParams = checkpoint.flowParamsCheckpoint;
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
        sb.append("--- Collections ---\n");
        sb.append("preferredIntCollectionSize=")
                .append(preferredIntCollectionSize())
                .append('\n');
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

        private Checkpoint(int symbolCheckpoint,
                           int typeCheckpoint,
                           ScopeGuards.Checkpoint scopeGuardsCheckpoint,
                           FlowParams flowParamsCheckpoint) {
            this.symbolCheckpoint = symbolCheckpoint;
            this.typeCheckpoint = typeCheckpoint;
            this.scopeGuardsCheckpoint = scopeGuardsCheckpoint;
            this.flowParamsCheckpoint = flowParamsCheckpoint;
        }

        public int symbolCheckpoint() {
            return symbolCheckpoint;
        }

        public int typeCheckpoint() {
            return typeCheckpoint;
        }
    }

    private static int choosePreferredByteAnchor() {
        // FIXME: temporary dev/testing bias; retune after array-utilization work stabilizes.
        boolean preferSmall = PseudoRandom.randomNotNegative(100) < SMALL_ANCHOR_BIAS_PERCENT;
        if (preferSmall) {
            int[] small = new int[BYTE_LENGTH_ANCHORS.length];
            int count = 0;
            for (int anchor : BYTE_LENGTH_ANCHORS) {
                if (anchor <= SMALL_BYTE_ANCHOR_THRESHOLD) {
                    small[count++] = anchor;
                }
            }
            if (count > 0) {
                return small[PseudoRandom.randomNotNegative(count)];
            }
        }
        return BYTE_LENGTH_ANCHORS[PseudoRandom.randomNotNegative(BYTE_LENGTH_ANCHORS.length)];
    }
}
