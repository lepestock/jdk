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
import jdk.test.lib.jittester.KernelTripCountPicker;
import jdk.test.lib.jittester.LocalVariable;
import jdk.test.lib.jittester.Nothing;
import jdk.test.lib.jittester.OperatorKind;
import jdk.test.lib.jittester.ProductionFailedException;
import jdk.test.lib.jittester.ProductionParams;
import jdk.test.lib.jittester.Statement;
import jdk.test.lib.jittester.Type;
import jdk.test.lib.jittester.TypeList;
import jdk.test.lib.jittester.UnaryOperator;
import jdk.test.lib.jittester.VariableInfo;
import jdk.test.lib.jittester.SymbolTable;
import jdk.test.lib.jittester.diagnostics.SourceDiagnostics;
import jdk.test.lib.jittester.loops.CounterInitializer;
import jdk.test.lib.jittester.loops.CounterManipulator;
import jdk.test.lib.jittester.loops.For;
import jdk.test.lib.jittester.loops.Loop;
import jdk.test.lib.jittester.loops.LoopingCondition;
import jdk.test.lib.jittester.morph.LoopIntrinsificationMorphTemplate;
import jdk.test.lib.jittester.types.TypeKlass;
import jdk.test.lib.jittester.utils.PseudoRandom;

/**
 * Emits canonical array-kernel for-loop shapes.
 */
class ArrayKernelLoopFactory extends SafeFactory<For> {
    private static final int KERNEL_ARRAY_ELEMENT_EXPRESSION_WEIGHT_PERCENT = 10;
    private static final int KERNEL_ARRAY_EXTRACTION_EXPRESSION_WEIGHT_PERCENT = 10;

    private final TypeKlass ownerClass;
    private final Type returnType;
    private final int statementLimit;
    private final int operatorLimit;
    private final int level;
    private final boolean canHaveReturn;

    ArrayKernelLoopFactory(TypeKlass ownerClass, Type returnType,
                           int statementLimit, int operatorLimit, int level, boolean canHaveReturn) {
        this.ownerClass = ownerClass;
        this.returnType = returnType;
        this.statementLimit = statementLimit;
        this.operatorLimit = operatorLimit;
        this.level = level;
        this.canHaveReturn = canHaveReturn;
    }

    @Override
    protected For sproduce() throws ProductionFailedException {
        if (statementLimit <= 0) {
            throw new ProductionFailedException();
        }
        IRNodeBuilder builder = new IRNodeBuilder()
                .setOwnerKlass(ownerClass)
                .setResultType(returnType)
                .withStatementLimit(statementLimit)
                .withOperatorLimit(operatorLimit)
                .setLevel(level)
                .setSubBlock(true)
                .setCanHaveBreaks(false)
                .setCanHaveContinues(false)
                .setCanHaveReturn(canHaveReturn)
                .setCanHaveThrow(false);

        boolean preferLoopIntrinsificationShape = preferLoopIntrinsificationShape();
        Type counterType = pickCounterType(preferLoopIntrinsificationShape);
        int iterationCount = clampTripCount(counterType, KernelTripCountPicker.pick());
        boolean reverse = !counterType.equals(TypeList.CHAR) && pickReverse(preferLoopIntrinsificationShape);
        if (isLoopIntrinsificationEnabled() && !reverse
                && (counterType.equals(TypeList.INT) || counterType.equals(TypeList.SHORT))) {
            iterationCount = Math.max(iterationCount, 128);
        }
        boolean negativeOffsetCompatibleRange = preferLoopIntrinsificationShape && !reverse
                && LoopIntrinsificationMorphTemplate.shouldUseNegativeOffsetCompatibleRange();
        int iterationStart = negativeOffsetCompatibleRange
                ? LoopIntrinsificationMorphTemplate.maxNegativeOffset()
                : reverse ? iterationCount - 1 : 0;
        int iterationLimit = reverse ? 0 : iterationStart + iterationCount;
        Loop loop = new Loop();
        loop.initialization = createCounterInitializer(counterType, iterationStart);
        LocalVariable counter = new LocalVariable(loop.initialization.getVariableInfo());
        String iterationVariable = counter.getVariableInfo().name;
        loop.condition = createLoopCondition(counter, counterType, iterationLimit, reverse);
        Statement headerInit = createCounterHeaderInitializer(counter, counterType, iterationStart);
        Statement headerUpdate = createCounterHeaderUpdate(counter, reverse);
        loop.manipulator = new CounterManipulator(new Statement(new Nothing(), false));

        SymbolTable.push();
        try {
            Block header = BlockFactory.produceEmptyBlock(ownerClass, returnType, Math.max(0, level - 1));
            Statement statement1 = headerInit;
            Statement statement2 = headerUpdate;
            int kernelBodyStatementLimit = (int) scaleLimit(statementLimit,
                    ProductionParams.arrayKernelBodyStatementPercent.value());
            Block body1 = builder
                    .withStatementLimit(kernelBodyStatementLimit)
                    .setLevel(level)
                    .setSubBlock(true)
                    .setCanHaveBreaks(true)
                    .setCanHaveContinues(false)
                    .setCanHaveReturn(false)
                    .setCanHaveThrow(false)
                    .withArrayKernelVariable(iterationVariable)
                    .withArrayKernelVariableType(counterType)
                    .withArrayKernelIterationStart(iterationStart)
                    .withArrayKernelIterationLimit(iterationLimit)
                    .withInArrayKernel(true)
                    .withArrayKernelForward(!reverse)
                    // Generic array expression roots tend to collapse kernel RHS into simple loads.
                    .withCollectionElementExpressionWeightPercent(KERNEL_ARRAY_ELEMENT_EXPRESSION_WEIGHT_PERCENT)
                    .withCollectionExtractionExpressionWeightPercent(KERNEL_ARRAY_EXTRACTION_EXPRESSION_WEIGHT_PERCENT)
                    .withMoreReadOnlyVars(iterationVariable)
                    .withMoreIterationVariables(iterationVariable)
                    .withEnteredLoop(iterationCount)
                    .produceBlock();
            if (body1.getChildren().isEmpty() && !shouldKeepEmptyBody()) {
                throw new ProductionFailedException();
            }
            Block body2 = BlockFactory.produceEmptyBlock(ownerClass, returnType, level);
            Block body3 = BlockFactory.produceEmptyBlock(ownerClass, returnType, level);
            For result = new For(level, loop, iterationCount, header, statement1, statement2, body1, body2, body3);
            attachDiagnostic(result, counterType, !reverse, iterationStart, iterationLimit, iterationCount);
            return result;
        } finally {
            SymbolTable.pop();
        }
    }

    private static void attachDiagnostic(For node, Type counterType, boolean forward,
            int start, int limit, int tripCount) {
        if (ProductionParams.debugMorphSourceDiagnostics.value()) {
            SourceDiagnostics.attach(node, "ArrayKernel created counterType="
                    + counterType.getName() + " forward=" + forward
                    + " start=" + start + " limit=" + limit + " tripCount=" + tripCount);
        }
    }

    private static Type pickCounterType(boolean preferLoopIntrinsificationShape) {
        Type[] candidates = preferLoopIntrinsificationShape
                ? new Type[] {TypeList.INT, TypeList.SHORT}
                : new Type[] {TypeList.INT, TypeList.SHORT, TypeList.BYTE, TypeList.CHAR};
        return candidates[PseudoRandom.randomNotNegative(candidates.length)];
    }

    private static boolean pickReverse(boolean preferLoopIntrinsificationShape) {
        return !preferLoopIntrinsificationShape && PseudoRandom.randomBoolean();
    }

    private static int clampTripCount(Type counterType, int preferredIntCount) {
        int preferred = Math.max(1, preferredIntCount);
        if (counterType.equals(TypeList.BYTE)) {
            return Math.min(preferred, 120);
        }
        if (counterType.equals(TypeList.SHORT)) {
            return Math.min(preferred, 30_000);
        }
        if (counterType.equals(TypeList.CHAR)) {
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
        if (type.equals(TypeList.CHAR)) {
            return (char) value;
        }
        return value;
    }

    private static long scaleLimit(long base, int percent) {
        int clampedPercent = Math.max(1, percent);
        long scaled = (long) Math.ceil(base * (clampedPercent / 100.0));
        return Math.max(1L, scaled);
    }

    private static boolean isLoopIntrinsificationEnabled() {
        return ProductionParams.morphTemplateLoopIntrinsificationProbability.value() > 0;
    }

    private static boolean preferLoopIntrinsificationShape() {
        if (!isLoopIntrinsificationEnabled()) {
            return false;
        }
        return LoopIntrinsificationMorphTemplate.shouldPreferViableArrayKernelShape();
    }

    private static boolean shouldKeepEmptyBody() {
        double probability = Math.max(0, Math.min(100,
                ProductionParams.arrayKernelEmptyBodyKeepProbability.value())) / 100.0;
        return probability > 0.0 && PseudoRandom.randomBoolean(probability);
    }
}
