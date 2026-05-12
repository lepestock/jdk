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

import jdk.test.lib.jittester.BinaryOperator;
import jdk.test.lib.jittester.IRNode;
import jdk.test.lib.jittester.Literal;
import jdk.test.lib.jittester.LocalVariable;
import jdk.test.lib.jittester.OperatorKind;
import jdk.test.lib.jittester.ProductionFailedException;
import jdk.test.lib.jittester.TypeList;
import jdk.test.lib.jittester.loops.LoopingCondition;
import jdk.test.lib.jittester.types.TypeKlass;
import jdk.test.lib.jittester.utils.PseudoRandom;
import jdk.test.lib.jittester.Logger;
import jdk.test.lib.jittester.Formatter;
import jdk.test.lib.jittester.ProductionParams;

class LoopingConditionFactory extends Factory<LoopingCondition> {
    private final LocalVariable counter;
    private final Literal limiter;
    private final int operatorLimit;
    private final long complexityLimit;
    private final TypeKlass ownerClass;

    public static enum Direction { INCREASING, DECREASING, UNKNOWN };
    private Direction direction = Direction.UNKNOWN;

    LoopingConditionFactory(long complexityLimit, int operatorLimit, TypeKlass ownerClass,
            LocalVariable counter, Literal limiter) {
        this.complexityLimit = complexityLimit;
        this.operatorLimit = operatorLimit;
        this.counter = counter;
        this.limiter = limiter;
        this.ownerClass = ownerClass;
    }

    public void setDirection(Direction direction) {
        this.direction = direction;
    }

    @Override
    public LoopingCondition produce() throws ProductionFailedException {
        long SEED = PseudoRandom.getCurrentSeed();
        IRNode leftExpression = null;
        IRNode rightExpression = null;
        Factory<IRNode> exprFactory = new IRNodeBuilder()
                .setResultType(TypeList.BOOLEAN)
                .setComplexityLimit((complexityLimit - 1) / 2)
                .setOperatorLimit((operatorLimit - 1) / 2)
                .setOwnerKlass(ownerClass)
                .setExceptionSafe(false)
                .setNoConsts(false)
                .getLimitedExpressionFactory();
        if (!ProductionParams.complexLoops.value()) {
            // Complex loops have high probability of failing execution, so we
            // give them only a low probability
            if (PseudoRandom.randomBoolean(0.1)) {
                leftExpression = exprFactory.produce();
            }
            if (PseudoRandom.randomBoolean(0.1)) {
                rightExpression = exprFactory.produce();
            }
        }

        Logger.trace("LoopingCondition.produce :expressions-before" +
                " :left " + Formatter.format(leftExpression) +
                " :right " + Formatter.format(rightExpression));
        // Depending on loop counter direction, we should synthesize limiting condition.
        // Example: If the counter is counting forward. Then the looping condition can be:
        // counter < n, counter <= n, n > counter, n >= counter, n - counter > 0, etc..

        // Just as a temporary solution we'll assume that the counter is monotonically increasing.
        // And use counter < n condition to limit the loop.
        // In future we may introduce other equivalent relations as well.
        OperatorKind operatorKind = (direction == Direction.DECREASING) ? OperatorKind.GT : OperatorKind.LT;
        BinaryOperator condition = new BinaryOperator(operatorKind, TypeList.BOOLEAN, counter, limiter);
        Logger.trace("LoopingCondition.produce :condition-before " + Formatter.format(condition));

        //FIXME JNP You need to catch these right or left not equal null.
        //How is it possible that they generate boolean expressions for loops?!!!
        condition = (rightExpression != null) ? new BinaryOperator(OperatorKind.AND, TypeList.BOOLEAN, condition,
                rightExpression) : condition;
        condition = (leftExpression != null) ? new BinaryOperator(OperatorKind.AND, TypeList.BOOLEAN, leftExpression,
                condition) : condition;
        LoopingCondition result = new LoopingCondition(condition);
        Logger.trace("LoopintConditionFactory.produce :result " + Formatter.format(condition));
        return result;
    }
}
