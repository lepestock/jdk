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
import java.util.HashSet;
import java.util.List;

import jdk.test.lib.jittester.BuiltInType;
import jdk.test.lib.jittester.IRNode;
import jdk.test.lib.jittester.Literal;
import jdk.test.lib.jittester.Nothing;
import jdk.test.lib.jittester.ProductionFailedException;
import jdk.test.lib.jittester.ProductionParams;
import jdk.test.lib.jittester.Rule;
import jdk.test.lib.jittester.Switch;
import jdk.test.lib.jittester.Type;
import jdk.test.lib.jittester.TypeList;
import jdk.test.lib.jittester.types.TypeKlass;
import jdk.test.lib.jittester.utils.DepthProbabilityTaper;
import jdk.test.lib.jittester.utils.PseudoRandom;

class SwitchFactory extends SafeFactory<Switch> {

    private final int statementLimit;
    private final int operatorLimit;
    private final boolean canHaveReturn;
    private final TypeKlass ownerClass;
    private final int level;

    SwitchFactory(TypeKlass ownerClass, int statementLimit,
            int operatorLimit, int level, boolean canHaveReturn) {
        this.ownerClass = ownerClass;
        this.statementLimit = statementLimit;
        this.operatorLimit = operatorLimit;
        this.level = level;
        this.canHaveReturn = canHaveReturn;
    }

    @Override
    protected Switch sproduce() throws ProductionFailedException {
        if (statementLimit > 0) {
            List<Type> switchTypes = new ArrayList<>();
            switchTypes.add(TypeList.CHAR);
            switchTypes.add(TypeList.BYTE);
            switchTypes.add(TypeList.SHORT);
            switchTypes.add(TypeList.INT);
            PseudoRandom.shuffle(switchTypes);
            IRNodeBuilder builder = new IRNodeBuilder()
                    .setOwnerKlass(ownerClass)
                    .withOperatorLimit(operatorLimit)
                    .setSubBlock(false)
                    .setCanHaveBreaks(true)
                    .setCanHaveContinues(false)
                    .setCanHaveReturn(canHaveReturn);
            Rule<Switch> switchRule = new Rule<>("switch");
            for (Type type : switchTypes) {
                switchRule.add(type.getName(), new Factory<Switch>() {
                    @Override
                    public Switch produce() throws ProductionFailedException {
                        return produceSwitchForType(builder, type);
                    }
                });
            }
            return switchRule.produce();
        }
        throw new ProductionFailedException();
    }

    private Switch produceSwitchForType(IRNodeBuilder builder, Type type) throws ProductionFailedException {
        List<IRNode> caseConsts = new ArrayList<>();
        List<IRNode> caseBlocks = new ArrayList<>();
        int accumulatedStatements = 0;
        int currentStatementsLimit = 0;
        boolean noConstsForSwitchExpr = shouldDisallowConstsByDepth(level + 1);
        IRNode switchExp = builder
                .setResultType(type)
                .setExceptionSafe(false)
                .setNoConsts(noConstsForSwitchExpr)
                .getLimitedExpressionFactory()
                .produce();
        List<Type> caseTypes = buildCompatibleCaseTypes((BuiltInType) type);
        if (PseudoRandom.randomBoolean()) { // "default"
            currentStatementsLimit = (int) (PseudoRandom.random()
                    * (statementLimit - accumulatedStatements));
            caseConsts.add(new Nothing());
            caseBlocks.add(builder
                    .withStatementLimit(currentStatementsLimit)
                    .setLevel(level + 1)
                    .setCanHaveReturn(false)
                    .setCanHaveBreaks(false)
                    .produceBlock());
            builder.setCanHaveBreaks(true)
                    .setCanHaveReturn(canHaveReturn);
            accumulatedStatements += currentStatementsLimit;
        }
        HashSet<Integer> cases = new HashSet<>();
        while (accumulatedStatements < statementLimit) { // "case"s
            currentStatementsLimit = (int) (PseudoRandom.random()
                    * (statementLimit - accumulatedStatements));
            PseudoRandom.shuffle(caseTypes);
            produceUniqueCaseLiteral(builder, caseTypes, cases, caseConsts);
            Rule<IRNode> rule = new Rule<>("case_block");
            int caseStatementLimit = currentStatementsLimit;
            rule.add("block", new Factory<IRNode>() {
                @Override
                public IRNode produce() throws ProductionFailedException {
                    return builder
                            .withStatementLimit(caseStatementLimit)
                            .setLevel(level)
                            .setCanHaveReturn(false)
                            .setCanHaveBreaks(false)
                            .produceBlock();
                }
            });
            builder.setCanHaveBreaks(true)
                    .setCanHaveReturn(canHaveReturn);
            rule.add("nothing", builder.getNothingFactory());
            IRNode choiceResult = rule.produce();
            caseBlocks.add(choiceResult);
            if (choiceResult instanceof Nothing) {
                accumulatedStatements++;
            } else {
                accumulatedStatements += currentStatementsLimit;
            }
        }
        PseudoRandom.shuffle(caseConsts);
        List<IRNode> accum = new ArrayList<>();
        int caseBlockIdx = 1 + caseConsts.size();
        accum.add(switchExp);
        for (int i = 1; i < caseBlockIdx; ++i) {
            accum.add(caseConsts.get(i - 1));
        }
        for (int i = caseBlockIdx; i < 1 + caseConsts.size() + caseBlocks.size(); ++i) {
            accum.add(caseBlocks.get(i - caseBlockIdx));
        }
        return new Switch(level, accum, caseBlockIdx);
    }

    private void produceUniqueCaseLiteral(IRNodeBuilder builder, List<Type> caseTypes,
            HashSet<Integer> cases, List<IRNode> caseConsts) throws ProductionFailedException {
        for (int tryCount = 0; tryCount < 10; tryCount++) {
            Literal literal = builder.setResultType(caseTypes.get(0))
                    .getLiteralFactory().produce();
            int value = 0;
            if (literal.value instanceof Integer) {
                value = (Integer) literal.value;
            }
            if (literal.value instanceof Short) {
                value = (Short) literal.value;
            }
            if (literal.value instanceof Byte) {
                value = (Byte) literal.value;
            }
            if (literal.value instanceof Character) {
                value = (Character) literal.value;
            }
            if (!cases.contains(value)) {
                cases.add(value);
                caseConsts.add(literal);
                return;
            }
        }
        throw new ProductionFailedException();
    }

    private static boolean shouldDisallowConstsByDepth(int depth) {
        double base = Math.max(0.0, Math.min(1.0, ProductionParams.constBiasBasePercent.value() / 100.0));
        int halfDepth = Math.max(1, ProductionParams.constBiasHalfDepth.value());
        double noConstsProbability = DepthProbabilityTaper.decayingAsymptote(depth, base, halfDepth);
        return PseudoRandom.randomBoolean(noConstsProbability);
    }

    private static List<Type> buildCompatibleCaseTypes(BuiltInType switchType) {
        List<Type> caseTypes = new ArrayList<>();
        if (switchType.equals(TypeList.CHAR)) {
            caseTypes.add(TypeList.CHAR);
        } else if (switchType.equals(TypeList.BYTE)) {
            caseTypes.add(TypeList.BYTE);
        } else if (switchType.equals(TypeList.SHORT)) {
            caseTypes.add(TypeList.BYTE);
            caseTypes.add(TypeList.SHORT);
        } else if (switchType.equals(TypeList.INT)) {
            caseTypes.add(TypeList.BYTE);
            caseTypes.add(TypeList.SHORT);
            caseTypes.add(TypeList.CHAR);
            caseTypes.add(TypeList.INT);
        } else {
            throw new IllegalArgumentException("Unsupported switch type: " + switchType.getName());
        }
        return caseTypes;
    }
}
