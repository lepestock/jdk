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

import jdk.test.lib.jittester.BinaryOperator;
import jdk.test.lib.jittester.Block;
import jdk.test.lib.jittester.LiteralInitializer;
import jdk.test.lib.jittester.GenerationState;
import jdk.test.lib.jittester.LocalVariable;
import jdk.test.lib.jittester.Nothing;
import jdk.test.lib.jittester.OperatorKind;
import jdk.test.lib.jittester.ProductionFailedException;
import jdk.test.lib.jittester.Statement;
import jdk.test.lib.jittester.Type;
import jdk.test.lib.jittester.TypeList;
import jdk.test.lib.jittester.UnaryOperator;
import jdk.test.lib.jittester.VariableInfo;
import jdk.test.lib.jittester.SymbolTable;
import jdk.test.lib.jittester.loops.CounterInitializer;
import jdk.test.lib.jittester.loops.CounterManipulator;
import jdk.test.lib.jittester.loops.For;
import jdk.test.lib.jittester.loops.Loop;
import jdk.test.lib.jittester.loops.LoopingCondition;
import jdk.test.lib.jittester.types.TypeKlass;
import jdk.test.lib.jittester.utils.PseudoRandom;

/**
 * Initial array-kernel factory scaffold.
 * It emits a canonical for-loop shape and marks kernel body blocks for source-level probing.
 */
class ArrayKernelLoopFactory extends SafeFactory<For> {
    private final TypeKlass ownerClass;
    private final Type returnType;
    private final long complexityLimit;
    private final int statementLimit;
    private final int operatorLimit;
    private final int level;
    private final boolean canHaveReturn;

    ArrayKernelLoopFactory(TypeKlass ownerClass, Type returnType, long complexityLimit,
                           int statementLimit, int operatorLimit, int level, boolean canHaveReturn) {
        this.ownerClass = ownerClass;
        this.returnType = returnType;
        this.complexityLimit = complexityLimit;
        this.statementLimit = statementLimit;
        this.operatorLimit = operatorLimit;
        this.level = level;
        this.canHaveReturn = canHaveReturn;
    }

    @Override
    protected For sproduce() throws ProductionFailedException {
        if (statementLimit <= 0 || complexityLimit <= 0) {
            throw new ProductionFailedException();
        }
        IRNodeBuilder builder = new IRNodeBuilder()
                .setOwnerKlass(ownerClass)
                .setResultType(returnType)
                .setComplexityLimit(complexityLimit)
                .setStatementLimit(statementLimit)
                .setOperatorLimit(operatorLimit)
                .setLevel(level)
                .setSubBlock(true)
                .setCanHaveBreaks(false)
                .setCanHaveContinues(false)
                .setCanHaveReturn(canHaveReturn)
                .setCanHaveThrow(false);

        Type counterType = pickCounterType();
        int thisLoopIterLimit = clampTripCount(counterType, GenerationState.preferredIntCollectionSize());
        boolean reverse = PseudoRandom.randomBoolean();
        Loop loop = new Loop();
        loop.initialization = createCounterInitializer(counterType, reverse ? thisLoopIterLimit - 1 : 0);
        LocalVariable counter = new LocalVariable(loop.initialization.getVariableInfo());
        String iterationVariable = counter.getVariableInfo().name;
        loop.condition = createLoopCondition(counter, counterType, thisLoopIterLimit, reverse);
        Statement headerInit = createCounterHeaderInitializer(counter, counterType, reverse ? thisLoopIterLimit - 1 : 0);
        Statement headerUpdate = createCounterHeaderUpdate(counter, reverse);
        loop.manipulator = new CounterManipulator(new Statement(new Nothing(), false));

        SymbolTable.push();
        try {
            Block header = BlockFactory.produceEmptyBlock(ownerClass, returnType, Math.max(0, level - 1));
            Statement statement1 = headerInit;
            Statement statement2 = headerUpdate;
            Block body1 = builder
                    .setComplexityLimit(Math.max(1L, complexityLimit / 2))
                    .setStatementLimit(Math.max(1, statementLimit / 2))
                    .setLevel(level)
                    .setSubBlock(true)
                    .setCanHaveBreaks(true)
                    .setCanHaveContinues(false)
                    .setCanHaveReturn(false)
                    .setCanHaveThrow(false)
                    .withArrayKernelVariable(iterationVariable)
                    .withInArrayKernel(true)
                    .produceBlock();
            Block body2 = BlockFactory.produceEmptyBlock(ownerClass, returnType, level);
            Block body3 = BlockFactory.produceEmptyBlock(ownerClass, returnType, level);
            return new For(level, loop, thisLoopIterLimit, header, statement1, statement2, body1, body2, body3);
        } finally {
            SymbolTable.pop();
        }
    }

    private static Type pickCounterType() {
        Type[] candidates = {TypeList.INT, TypeList.SHORT, TypeList.BYTE};
        return candidates[PseudoRandom.randomNotNegative(candidates.length)];
    }

    private static int clampTripCount(Type counterType, int preferredIntCount) {
        int preferred = Math.max(1, preferredIntCount);
        if (counterType.equals(TypeList.BYTE)) {
            return Math.min(preferred, 120);
        }
        if (counterType.equals(TypeList.SHORT)) {
            return Math.min(preferred, 30_000);
        }
        return preferred;
    }

    private CounterInitializer createCounterInitializer(Type counterType, int initValue) {
        String resultName = "var_" + SymbolTable.getNextVariableNumber();
        VariableInfo varInfo = new VariableInfo(resultName, ownerClass, counterType,
                VariableInfo.LOCAL | VariableInfo.INITIALIZED);
        SymbolTable.add(varInfo);
        return new CounterInitializer(varInfo, new LiteralInitializer(castLiteral(initValue, counterType), counterType));
    }

    private static LoopingCondition createLoopCondition(LocalVariable counter, Type counterType,
                                                        int tripCount, boolean reverse) {
        OperatorKind comparison = reverse ? OperatorKind.GE : OperatorKind.LT;
        int limit = reverse ? 0 : tripCount;
        return new LoopingCondition(new BinaryOperator(comparison, TypeList.BOOLEAN, counter,
                new LiteralInitializer(castLiteral(limit, counterType), counterType)));
    }

    private static Statement createCounterHeaderInitializer(LocalVariable counter, Type counterType, int initValue) {
        return new Statement(new BinaryOperator(OperatorKind.ASSIGN, counterType, counter,
                new LiteralInitializer(castLiteral(initValue, counterType), counterType)), false);
    }

    private static Statement createCounterHeaderUpdate(LocalVariable counter, boolean reverse) {
        OperatorKind op = reverse ? OperatorKind.POST_DEC : OperatorKind.POST_INC;
        return new Statement(new UnaryOperator(op, counter), false);
    }

    private static Object castLiteral(int value, Type type) {
        if (type.equals(TypeList.BYTE)) {
            return (byte) value;
        }
        if (type.equals(TypeList.SHORT)) {
            return (short) value;
        }
        return value;
    }
}
