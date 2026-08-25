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

import jdk.test.lib.jittester.IRNode;
import jdk.test.lib.jittester.OperatorKind;
import jdk.test.lib.jittester.ProductionFailedException;
import jdk.test.lib.jittester.Rule;
import jdk.test.lib.jittester.TypeList;
import jdk.test.lib.jittester.types.TypeKlass;
import jdk.test.lib.jittester.utils.PseudoRandom;

class BooleanConditionFactory extends Factory<IRNode> {
    private static final double VARIABLE_WEIGHT = 6.0;
    private static final double COMPARISON_WEIGHT = 4.0;
    private static final BudgetRange[] BUDGET_RANGES = {
            new BudgetRange(3, 4, 70),
            new BudgetRange(5, 8, 25),
            new BudgetRange(9, 16, 4),
            new BudgetRange(Integer.MAX_VALUE, Integer.MAX_VALUE, 1),
    };
    private static final int TOTAL_BUDGET_WEIGHT = totalBudgetWeight();

    private final Rule<IRNode> rule;
    private final Factory<? extends IRNode> fallbackExpressionFactory;
    private final Factory<? extends IRNode> literalFactory;

    BooleanConditionFactory(int operatorLimit, TypeKlass ownerClass,
            boolean exceptionSafe) throws ProductionFailedException {
        Budget budget = pickBudget(operatorLimit);
        IRNodeBuilder builder = new IRNodeBuilder()
                .withOperatorLimit(budget.operatorLimit)
                .setOwnerKlass(ownerClass)
                .setResultType(TypeList.BOOLEAN)
                .setExceptionSafe(exceptionSafe)
                .setNoConsts(true);
        rule = new Rule<>("boolean_condition");
        fallbackExpressionFactory = builder.getExpressionFactory();
        literalFactory = builder.setNoConsts(false).getLiteralFactory();

        Factory<? extends IRNode> variableFactory = builder
                .setIsConstant(false)
                .setIsInitialized(true)
                .getVariableFactory();
        if (hasVariableCandidates(variableFactory)) {
            rule.add("variable", variableFactory, VARIABLE_WEIGHT);
        }

        addBinary(builder, "gt", OperatorKind.GT, COMPARISON_WEIGHT);
        addBinary(builder, "lt", OperatorKind.LT, COMPARISON_WEIGHT);
        addBinary(builder, "ge", OperatorKind.GE, COMPARISON_WEIGHT);
        addBinary(builder, "le", OperatorKind.LE, COMPARISON_WEIGHT);
        addBinary(builder, "eq", OperatorKind.EQ, COMPARISON_WEIGHT);
        addBinary(builder, "ne", OperatorKind.NE, COMPARISON_WEIGHT);
    }

    private static boolean hasVariableCandidates(Factory<? extends IRNode> factory) {
        return !(factory instanceof VariableCandidateSource source) || source.hasCandidates(v -> true);
    }

    private void addBinary(IRNodeBuilder builder, String name, OperatorKind kind, double weight)
            throws ProductionFailedException {
        rule.add(name, builder.setOperatorKind(kind).getBinaryOperatorFactory(), weight);
    }

    private static Budget pickBudget(int operatorLimit) {
        int selected = PseudoRandom.randomNotNegative(TOTAL_BUDGET_WEIGHT);
        int threshold = 0;
        for (BudgetRange range : BUDGET_RANGES) {
            threshold += range.weight;
            if (selected < threshold) {
                return range.pick(operatorLimit);
            }
        }
        return BUDGET_RANGES[BUDGET_RANGES.length - 1].pick(operatorLimit);
    }

    private static int totalBudgetWeight() {
        int total = 0;
        for (BudgetRange range : BUDGET_RANGES) {
            total += range.weight;
        }
        return total;
    }

    @Override
    public IRNode produce() throws ProductionFailedException {
        try {
            return rule.produce();
        } catch (ProductionFailedException e) {
            try {
                return fallbackExpressionFactory.produce();
            } catch (ProductionFailedException ignored) {
                return literalFactory.produce();
            }
        }
    }

    private record Budget(int operatorLimit) {
    }

    private record BudgetRange(int minOperators, int maxOperators, int weight) {
        Budget pick(int operatorLimit) {
            int pickedOperators = pickInt(minOperators, maxOperators);
            return new Budget(Math.max(1, Math.min(operatorLimit, pickedOperators)));
        }

        private static int pickInt(int min, int max) {
            if (max == Integer.MAX_VALUE) {
                return max;
            }
            return min + PseudoRandom.randomNotNegative(max - min + 1);
        }

    }
}
