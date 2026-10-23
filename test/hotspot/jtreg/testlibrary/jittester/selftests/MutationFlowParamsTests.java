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

import java.nio.file.Files;
import java.nio.file.Path;
import jdk.test.lib.jittester.utils.Genome;

public class MutationFlowParamsTests {
    public static void main(String[] args) throws Exception {
        Path work = Files.createTempDirectory("jittester-mutation-flow-");
        try {
            String founder = work.resolve("founder").toString();
            ProductionParams.initializeFromCmdline(new String[] {
                    "--statement-limit=4", "--operator-limit=8", "--seed=12345",
                    "--genome-record", founder});
            exerciseScopes(false);
            Genome.close();

            String mutant = work.resolve("mutant").toString();
            ProductionParams.initializeFromCmdline(new String[] {
                    "--statement-limit=4", "--operator-limit=8",
                    "--genome-replay", founder, "--genome-record", mutant,
                    "--genome-mutation-target", "B200", "--genome-mutation-seed", "201"});
            exerciseScopes(false);
            Genome.close();

            String replay = work.resolve("replay").toString();
            ProductionParams.initializeFromCmdline(new String[] {
                    "--statement-limit=4", "--operator-limit=8",
                    "--genome-replay", mutant, "--genome-record", replay});
            exerciseScopes(false);
            Genome.close();
            for (String suffix : new String[] {".genome", ".blocks"}) {
                check(Files.mismatch(Path.of(mutant + suffix), Path.of(replay + suffix)) == -1,
                        "Mutant replay changed " + suffix);
            }

            // Explicit overrides must still apply locally and be restored on exit.
            ProductionParams.initializeFromCmdline(new String[] {
                    "--statement-limit=4", "--operator-limit=8",
                    "--genome-replay", founder, "--genome-mutation-target", "B200",
                    "--genome-mutation-seed", "201", "--override", "statement-limit", "60",
                    "--override", "operator-limit", "9"});
            exerciseScopes(true);
            System.out.println("MutationFlowParamsTests passed: " + work);
        } finally {
            Genome.close();
        }
    }

    private static void exerciseScopes(boolean overridden) {
        FlowParams initial = GenerationState.currentFlowParams();
        FlowParams parent = initial.withLimits(6, 7).advance();
        enterBlock(100, parent);
        check(GenerationState.currentFlowParams() == parent, "Parent context was replaced");

        FlowParams target = parent.withLimits(2, 3).advance();
        enterBlock(200, target);
        FlowParams active = GenerationState.currentFlowParams();
        check(active.statementLimit() == (overridden ? 60 : 2),
                "Mutation changed the inherited statement limit without an override: " + active.statementLimit());
        check(active.operatorLimit() == (overridden ? 9 : 3),
                "Mutation changed the inherited operator limit without an override: " + active.operatorLimit());
        check(overridden || active == target, "Mutation introduced an extra FlowParams advance");

        long nested = Genome.startScope('B', 300);
        Genome.recordCurrentScopeGene('B', nested);
        check(GenerationState.currentFlowParams() == active, "Nested scope lost the inherited context");
        Genome.endBlock();
        Genome.endBlock();
        check(GenerationState.currentFlowParams() == parent, "Mutation leaked its context to the parent");
        check(ProductionParams.statementLimit.value() == 4 && ProductionParams.operatorLimit.value() == 8,
                "Mutation leaked production parameter overrides");

        FlowParams sibling = parent.withLimits(1, 2).advance();
        enterBlock(400, sibling);
        check(GenerationState.currentFlowParams() == sibling, "Mutation changed a sibling context");
        Genome.endBlock();
        Genome.endBlock();
        check(GenerationState.currentFlowParams() == initial, "Parent scope did not restore its context");
    }

    private static void enterBlock(long seed, FlowParams flowParams) {
        long selected = Genome.startBlock(seed, flowParams);
        Genome.recordCurrentScopeGene('B', selected);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
