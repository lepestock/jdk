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

package jdk.test.lib.jittester.factories;

import java.util.List;
import jdk.test.lib.jittester.GenerationState;
import jdk.test.lib.jittester.ProductionParams;
import jdk.test.lib.jittester.SymbolTable;
import jdk.test.lib.jittester.TypeList;
import jdk.test.lib.jittester.VariableInfo;
import jdk.test.lib.jittester.classes.RecordKlass;
import jdk.test.lib.jittester.functions.FunctionInfo;
import jdk.test.lib.jittester.utils.PseudoRandom;

public class RecordKlassTests {
    public static void main(String[] args) throws Exception {
        testFunctionControl(true);
        testFunctionControl(false);
        System.out.println("RecordKlassTests passed");
    }

    private static void testFunctionControl(boolean disabled) throws Exception {
        ProductionParams.initializeFromCmdline(new String[] {
                "--disable-external-symbols", "--disable-inheritance",
                "--disable-functions=" + disabled, "--arrays-disable=true",
                "--lock-elimination-morph-template-probability=0", "--seed=12345"});
        GenerationState.initializeFlowParamsFromProductionParams();
        boolean generatedMethodSeen = false;
        boolean componentSeen = false;
        for (int i = 0; i < 20; i++) {
            int seed = 7 + 104729 * i;
            SymbolTable.removeAll();
            TypeList.removeAll();
            PseudoRandom.setCurrentSeed(seed);
            RecordKlass record = new RecordKlassFactory("Test_Record_" + seed,
                    10, 2, 1, 2, 0).produce();
            List<FunctionInfo> functions = SymbolTable
                    .getAllCombined(record.getThisKlass(), FunctionInfo.class).stream()
                    .map(FunctionInfo.class::cast).toList();
            boolean hasGeneratedMethod = functions.stream()
                    .anyMatch(function -> function.generatedRank != FunctionInfo.GENERATED_RANK_NONE);
            require(!disabled || !hasGeneratedMethod,
                    "Record generated methods with --disable-functions at seed " + seed);
            generatedMethodSeen |= hasGeneratedMethod;
            require(functions.stream().anyMatch(function -> function.isConstructor()
                            && function.argTypes.size() == record.getComponents().size()),
                    "Record lost its canonical constructor at seed " + seed);
            for (VariableInfo component : record.getComponents()) {
                componentSeen = true;
                require(functions.stream().anyMatch(function -> function.name.equals(component.name)
                                && function.type.equals(component.type) && function.argTypes.size() == 1),
                        "Record lost accessor " + component.name + " at seed " + seed);
            }
        }
        require(componentSeen, "Did not exercise records with components");
        require(disabled || generatedMethodSeen, "Did not exercise enabled record method generation");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
