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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import jdk.test.lib.jittester.GenerationState;
import jdk.test.lib.jittester.ProductionParams;
import jdk.test.lib.jittester.SymbolTable;
import jdk.test.lib.jittester.TypeList;
import jdk.test.lib.jittester.classes.ClassDefinitionBlock;
import jdk.test.lib.jittester.morph.MorphContext;
import jdk.test.lib.jittester.morph.MorphLegResult;
import jdk.test.lib.jittester.morph.MorphLegTarget;
import jdk.test.lib.jittester.morph.MorphTemplate;

public class TypeDeclarationMorphTests {
    public static void main(String[] args) throws Exception {
        Method memberFunctionsLimit = IRNodeBuilder.class.getDeclaredMethod("getMemberFunctionsLimit");
        memberFunctionsLimit.setAccessible(true);
        for (boolean interfaces : new boolean[] {true, false}) {
            for (int recordWeight : new int[] {0, 100}) {
                for (int balance : new int[] {0, 100}) {
                    for (int limit : new int[] {1, 10}) {
                        checkBuilderBudget(memberFunctionsLimit, interfaces, recordWeight, balance, limit);
                    }
                }
            }
        }
        System.out.println("TypeDeclarationMorphTests passed");
    }

    private static void checkBuilderBudget(Method getter, boolean interfaces, int recordWeight,
            int balance, int limit) throws Exception {
        ProductionParams.initializeFromCmdline(new String[] {
                "--seed=777", "--disable-external-symbols", "--disable-inheritance",
                "--disable-functions", "--disable-interfaces=" + !interfaces,
                "--class-record-weight=" + recordWeight, "--identity-value-class-balance=" + balance,
                "--min-cfg-depth=0", "--max-cfg-depth=100", "--morph-template-leg-weight=100"});
        GenerationState.initializeFlowParamsFromProductionParams();
        SymbolTable.removeAll();
        TypeList.removeAll();
        BudgetCheckingTemplate template = new BudgetCheckingTemplate(getter, limit,
                "interfaces=" + interfaces + ", recordWeight=" + recordWeight + ", balance=" + balance);
        GenerationState.setCurrentMorphContext(new MorphContext(List.of(template)));
        try {
            new ClassDefinitionBlockFactory("Test_TypeLeg", 10, limit, 2, 1, 1, 0).produce();
            require(template.checks > 0 && template.legs > 0,
                    "Did not exercise both applicability and production: " + template.configuration);
        } finally {
            GenerationState.setCurrentMorphContext(MorphContext.EMPTY);
        }
    }

    private static final class BudgetCheckingTemplate implements MorphTemplate {
        private final Method getter;
        private final int expectedLimit;
        private final String configuration;
        private int checks;
        private int legs;

        private BudgetCheckingTemplate(Method getter, int expectedLimit, String configuration) {
            this.getter = getter;
            this.expectedLimit = expectedLimit;
            this.configuration = configuration;
        }

        @Override
        public long id() { return 1; }

        @Override
        public int nextLeg() { return 0; }

        @Override
        public double legWeightMultiplier() {
            // Make type-leg production dominate ordinary types for these fixed-seed checks.
            return 1e12;
        }

        @Override
        public boolean canProduceLeg(MorphLegTarget target, IRNodeBuilder builder) {
            if (target != MorphLegTarget.TYPE_DECLARATION) {
                return false;
            }
            checkBudget(builder);
            checks++;
            return true;
        }

        @Override
        public MorphLegResult produceLeg(int leg, IRNodeBuilder builder) {
            checkBudget(builder);
            legs++;
            return new MorphLegResult(new ClassDefinitionBlock(new ArrayList<>(), 0), this, false);
        }

        private void checkBudget(IRNodeBuilder builder) {
            try {
                int actual = (Integer) getter.invoke(builder);
                require(actual == expectedLimit, "Type-leg method budget: expected " + expectedLimit
                        + ", got " + actual + " (" + configuration + ")");
            } catch (ReflectiveOperationException ex) {
                throw new AssertionError(ex);
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
