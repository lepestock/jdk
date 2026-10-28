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

import jdk.test.lib.jittester.utils.Genome;

public class MorphLegWeightTests {
    public static void main(String[] args) {
        try {
            ProductionParams.initializeFromCmdline(new String[] {"--morph-template-leg-weight=250"});
            FlowParams flow = GenerationState.currentFlowParams();
            check(flow.mtLegWeight() == 2.5, "Global weight was capped at 1");
            check(flow.withMtLegWeight(3).advance().mtLegWeight() == 3, "Scoped weight was capped");
            FlowParams both = flow.withMtParameters(2, 4).advance();
            check(both.mtCreationProbability() == 1 && both.mtLegWeight() == 4,
                    "Probability/weight semantics were mixed");
            System.out.println("MorphLegWeightTests passed");
        } finally {
            Genome.close();
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
