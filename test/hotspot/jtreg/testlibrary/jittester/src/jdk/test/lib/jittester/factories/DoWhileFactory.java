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

import jdk.test.lib.jittester.Block;
import jdk.test.lib.jittester.FlowParams;
import jdk.test.lib.jittester.GenerationState;
import jdk.test.lib.jittester.Literal;
import jdk.test.lib.jittester.LocalVariable;
import jdk.test.lib.jittester.ProductionFailedException;
import jdk.test.lib.jittester.SymbolTable;
import jdk.test.lib.jittester.Type;
import jdk.test.lib.jittester.TypeList;
import jdk.test.lib.jittester.loops.DoWhile;
import jdk.test.lib.jittester.loops.Loop;
import jdk.test.lib.jittester.types.TypeKlass;
import jdk.test.lib.jittester.utils.PseudoRandom;
import jdk.test.lib.jittester.Logger;
import jdk.test.lib.jittester.Formatter;
import jdk.test.lib.jittester.loops.LoopingCondition;

public class DoWhileFactory extends SafeFactory<DoWhile> {
    private final Loop loop;
    private final long complexityLimit;
    private final int statementLimit;
    private final int operatorLimit;
    private boolean canHaveReturn = false;
    private final TypeKlass ownerClass;
    private final int level;
    private final Type returnType;
    private long thisLoopIterLimit;
    public static long SEED;

    DoWhileFactory(TypeKlass ownerClass, Type returnType, long complexityLimit, int statementLimit,
            int operatorLimit, int level, boolean canHaveReturn) {
        loop = new Loop();
        this.ownerClass = ownerClass;
        this.returnType = returnType;
        this.complexityLimit = complexityLimit;
        this.statementLimit = statementLimit;
        this.operatorLimit = operatorLimit;
        this.level = level;
        this.canHaveReturn = canHaveReturn;
        thisLoopIterLimit = 0;
    }

    @Override
    protected DoWhile sproduce() throws ProductionFailedException {
        SEED = PseudoRandom.getCurrentSeed();
        if (statementLimit > 0 && complexityLimit > 0) {
            long complexity = complexityLimit;
            // Loop header parameters
            long headerComplLimit = (long) (0.005 * complexity * PseudoRandom.random());
            complexity -= headerComplLimit;
            int headerStatementLimit = PseudoRandom.randomNotZero((int) (statementLimit / 3.0));
            // Loop body parameters
            thisLoopIterLimit = (long) (0.0001 * complexity * PseudoRandom.random());
            if (thisLoopIterLimit > Integer.MAX_VALUE || thisLoopIterLimit == 0) {
                throw new ProductionFailedException();
            }
            complexity = thisLoopIterLimit > 0 ? complexity / thisLoopIterLimit : 0;
            long condComplLimit = (long) (complexity * PseudoRandom.random());
            complexity -= condComplLimit;
            long body1ComplLimit = (long) (complexity * PseudoRandom.random());
            complexity -= body1ComplLimit;
            int body1StatementLimit = PseudoRandom.randomNotZero((int) (statementLimit / 3.0));
            long body2ComplLimit = (long) (complexity * PseudoRandom.random());
            complexity -= body2ComplLimit;
            int body2StatementLimit = PseudoRandom.randomNotZero((int) (statementLimit / 3.0));
            // Production
            IRNodeBuilder builder = new IRNodeBuilder()
                    .setOwnerKlass(ownerClass)
                    .setResultType(returnType)
                    .withOperatorLimit(operatorLimit);
            loop.initialization = builder.getCounterInitializerFactory(0).produce();
            Block header;
            try {
                header = builder.withComplexityLimit(headerComplLimit)
                        .withStatementLimit(headerStatementLimit)
                        .setLevel(level - 1)
                        .setSubBlock(true)
                        .setCanHaveBreaks(false)
                        .setCanHaveContinues(false)
                        .setCanHaveReturn(false)
                        .produceBlock();
            } catch (ProductionFailedException e) {
                header = BlockFactory.produceEmptyBlock(ownerClass, returnType, level - 1);
            }
            // getChildren().set(DoWhile.DoWhilePart.HEADER.ordinal(), header);
            LocalVariable counter = new LocalVariable(loop.initialization.getVariableInfo());
            String iterationVariable = counter.getVariableInfo().name;
            Literal limiter = new Literal((int) thisLoopIterLimit, TypeList.INT);
            Factory<LoopingCondition> lcFactory = builder.withComplexityLimit(condComplLimit)
                    .setLocalVariable(counter)
                    .getLoopingConditionFactory(limiter);
            if (false && SEED == 99649021304063L) {
                Logger.enableTrace();
                Logger.trace("DoWhileFactory.sproduce" +
                        " :loop-initializer " + Formatter.format(loop.initialization) +
                        " :parsed " + loop.initialization.getChild(0));
                Literal init = (Literal)(loop.initialization.getChild(0));
                Logger.trace("DoWhileFactory.sproduce" +
                        " :loop-initializer-type " + init.value.getClass() +
                        " :loop-initializer-value " + init.value);
            }
            Block body1;
            Block body2;
            SymbolTable.push();
            FlowParams previousFlowParams = GenerationState.currentFlowParams();
            GenerationState.setCurrentFlowParams(previousFlowParams
                    .withMoreIterationVariables(iterationVariable)
                    .advance());
            try {
                loop.condition = lcFactory.produce();
                Logger.disableTrace();
                try {
                    body1 = builder.withComplexityLimit(body1ComplLimit)
                            .withStatementLimit(body1StatementLimit)
                            .setLevel(level)
                            .setSubBlock(true)
                            .setCanHaveBreaks(true)
                            .setCanHaveContinues(false)
                            .setCanHaveReturn(false)
                            .withMoreReadOnlyVars(iterationVariable)
                            .withMoreIterationVariables(iterationVariable)
                            .produceBlock();
                } catch (ProductionFailedException e) {
                    body1 = BlockFactory.produceEmptyBlock(ownerClass, returnType, level - 1);
                }
                // getChildren().set(DoWhile.DoWhilePart.BODY1.ordinal(), body1);
                loop.manipulator = builder.setLocalVariable(counter)
                                          .getCounterManipulatorFactory()
                                          .calculateDirection((Literal)(loop.initialization.getChild(0)), limiter)
                                          .produce();
                try {
                    body2 = builder.withComplexityLimit(body2ComplLimit)
                            .withStatementLimit(body2StatementLimit)
                            .setLevel(level)
                            .setSubBlock(true)
                            .setCanHaveBreaks(true)
                            .setCanHaveContinues(false)
                            .setCanHaveReturn(canHaveReturn)
                            .withMoreReadOnlyVars(iterationVariable)
                            .withMoreIterationVariables(iterationVariable)
                            .produceBlock();
                } catch (ProductionFailedException e) {
                    body2 = BlockFactory.produceEmptyBlock(ownerClass, returnType, level - 1);
                }
            } finally {
                GenerationState.setCurrentFlowParams(previousFlowParams);
                SymbolTable.pop();
            }
            // getChildren().set(DoWhile.DoWhilePart.BODY2.ordinal(), body2);
            return new DoWhile(level, loop, thisLoopIterLimit, header, body1, body2);
        }
        throw new ProductionFailedException();
    }
}
