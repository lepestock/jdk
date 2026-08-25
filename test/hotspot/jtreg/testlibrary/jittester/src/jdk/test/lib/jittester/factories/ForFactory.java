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
import jdk.test.lib.jittester.IRNode;
import jdk.test.lib.jittester.Literal;
import jdk.test.lib.jittester.LocalVariable;
import jdk.test.lib.jittester.Nothing;
import jdk.test.lib.jittester.ProductionFailedException;
import jdk.test.lib.jittester.Rule;
import jdk.test.lib.jittester.Statement;
import jdk.test.lib.jittester.SymbolTable;
import jdk.test.lib.jittester.Type;
import jdk.test.lib.jittester.TypeList;
import jdk.test.lib.jittester.loops.For;
import jdk.test.lib.jittester.loops.Loop;
import jdk.test.lib.jittester.types.TypeKlass;
import jdk.test.lib.jittester.utils.PseudoRandom;
import jdk.test.lib.jittester.FlowParams;
import jdk.test.lib.jittester.GenerationState;

class ForFactory extends SafeFactory<For> {
    private static final int LOOP_ITERATION_LIMIT = 100;

    private final Loop loop;
    private final int statementLimit;
    private final int operatorLimit;
    private final TypeKlass ownerClass;
    private final Type returnType;
    private final int level;
    private final boolean canHaveReturn;

    ForFactory(TypeKlass ownerClass, Type returnType, int statementLimit,
            int operatorLimit, int level, boolean canHaveReturn) {
        this.ownerClass = ownerClass;
        this.returnType = returnType;
        this.statementLimit = statementLimit;
        this.operatorLimit = operatorLimit;
        this.level = level;
        loop = new Loop();
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
                .withOperatorLimit(operatorLimit)
                .setSemicolon(false)
                .setExceptionSafe(false)
                .setNoConsts(false);
        // Loop header parameters
        int headerStatementLimit = PseudoRandom.randomNotZero((int) (statementLimit / 4.0));
        // Loop body parameters
        long thisLoopIterLimit = PseudoRandom.randomNotZero(LOOP_ITERATION_LIMIT);
        int body1StatementLimit = PseudoRandom.randomNotZero((int) (statementLimit / 4.0));
        int body2StatementLimit = PseudoRandom.randomNotZero((int) (statementLimit / 4.0));
        int body3StatementLimit = PseudoRandom.randomNotZero((int) (statementLimit / 4.0));
        // Production
        loop.initialization = builder.getCounterInitializerFactory(0).produce();
        Block header;
        try {
            header = builder
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
        SymbolTable.push();
        IRNode statement1;
        try {
            Rule<IRNode> rule = new Rule<>("statement1");
            rule.add("assignment", builder.getAssignmentOperatorFactory());
            rule.add("function", builder.getFunctionFactory(), 0.1);
            rule.add("initialization", builder.setIsConstant(false)
                    .setIsStatic(false)
                    .setIsLocal(true)
                    .getVariableInitializationFactory());
            statement1 = rule.produce();
        } catch (ProductionFailedException e) {
            statement1 = new Nothing();
        }
        LocalVariable counter = new LocalVariable(loop.initialization.getVariableInfo());
        String iterationVariable = counter.getVariableInfo().name;
        Literal limiter = new Literal((int) thisLoopIterLimit, TypeList.INT);
        IRNode statement2;
        Block body1;
        Block body2;
        Block body3;
        FlowParams previousFlowParams = GenerationState.currentFlowParams();
        GenerationState.setCurrentFlowParams(previousFlowParams
                .withMoreIterationVariables(iterationVariable)
                .advance());
        try {
            loop.condition = builder
                    .setLocalVariable(counter)
                    .getLoopingConditionFactory(limiter)
                    .produce();
            try {
                statement2 = builder
                        .getAssignmentOperatorFactory().produce();
            } catch (ProductionFailedException e) {
                statement2 = new Nothing();
            }
            try {
                body1 = builder
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
            loop.manipulator = builder.setLocalVariable(counter)
                                      .getCounterManipulatorFactory()
                                      .calculateDirection((Literal)(loop.initialization.getChild(0)), limiter)
                                      .produce();
            try {
                body2 = builder
                        .withStatementLimit(body2StatementLimit)
                        .setLevel(level)
                        .setSubBlock(true)
                        .setCanHaveBreaks(true)
                        .setCanHaveContinues(true)
                        .setCanHaveReturn(false)
                        .withMoreReadOnlyVars(iterationVariable)
                        .withMoreIterationVariables(iterationVariable)
                        .produceBlock();
            } catch (ProductionFailedException e) {
                body2 = BlockFactory.produceEmptyBlock(ownerClass, returnType, level - 1);
            }
            try {
                body3 = builder
                        .withStatementLimit(body3StatementLimit)
                        .setLevel(level)
                        .setSubBlock(true)
                        .setCanHaveBreaks(true)
                        .setCanHaveContinues(false)
                        .setCanHaveReturn(canHaveReturn)
                        .withMoreReadOnlyVars(iterationVariable)
                        .withMoreIterationVariables(iterationVariable)
                        .produceBlock();
            } catch (ProductionFailedException e) {
                body3 = BlockFactory.produceEmptyBlock(ownerClass, returnType, level - 1);
            }
        } finally {
            GenerationState.setCurrentFlowParams(previousFlowParams);
        }
        SymbolTable.pop();
        For result = new For(level, loop, thisLoopIterLimit, header,
                new Statement(statement1, false),
                new Statement(statement2, false),
                body1,
                body2, body3);
        return result;
    }
}
