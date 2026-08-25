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
import jdk.test.lib.jittester.If;
import jdk.test.lib.jittester.ProductionFailedException;
import jdk.test.lib.jittester.Type;
import jdk.test.lib.jittester.TypeList;
import jdk.test.lib.jittester.types.TypeKlass;
import jdk.test.lib.jittester.utils.PseudoRandom;

class IfFactory extends SafeFactory<If> {

    protected final int statementLimit;
    protected final int operatorLimit;
    protected final boolean canHaveBreaks;
    protected final boolean canHaveContinues;
    protected final boolean canHaveReturn;
    protected final TypeKlass ownerClass;
    protected final Type returnType;
    protected final int level;

    IfFactory(TypeKlass ownerClass, Type returnType, int statementLimit,
            int operatorLimit, int level, boolean canHaveBreaks, boolean canHaveContinues,
            boolean canHaveReturn) {
        this.ownerClass = ownerClass;
        this.returnType = returnType;
        this.statementLimit = statementLimit;
        this.operatorLimit = operatorLimit;
        this.level = level;
        this.canHaveBreaks = canHaveBreaks;
        this.canHaveContinues = canHaveContinues;
        this.canHaveReturn = canHaveReturn;
    }

    @Override
    public If sproduce() throws ProductionFailedException {
        // resizeUpChildren(If.IfPart.values().length);
        if (statementLimit <= 0) {
            throw new ProductionFailedException();
        }
        IRNodeBuilder builder = new IRNodeBuilder()
                .setOwnerKlass(ownerClass)
                .withOperatorLimit(operatorLimit);
        IRNode condition = builder
                .setResultType(TypeList.BOOLEAN)
                .setExceptionSafe(false)
                .getBooleanConditionFactory()
                .produce();
        // setChild(If.IfPart.CONDITION.ordinal(), condition);
        int ifBlockLimit = 1 + PseudoRandom.randomNotNegative(statementLimit);
        int elseBlockLimit = statementLimit - ifBlockLimit;
        If.IfPart controlDeviation;
        if (elseBlockLimit <= 0) {
            controlDeviation = If.IfPart.THEN;
        } else {
            controlDeviation = PseudoRandom.randomBoolean() ? If.IfPart.THEN : If.IfPart.ELSE;
        }
        Block thenBlock;
        builder.setResultType(returnType)
                .setLevel(level)
                .withStatementLimit(ifBlockLimit);
        if (controlDeviation == If.IfPart.THEN) {
            thenBlock = builder.setSubBlock(false)
                    .setCanHaveBreaks(canHaveBreaks)
                    .setCanHaveContinues(canHaveContinues)
                    .setCanHaveReturn(canHaveReturn)
                    .produceBlock();
        } else {
            thenBlock = builder.setSubBlock(false)
                    .setCanHaveBreaks(false)
                    .setCanHaveContinues(false)
                    .setCanHaveReturn(false)
                    .produceBlock();
        }
        // setChild(If.IfPart.THEN.ordinal(), thenBlock);
        Block elseBlock = null;
        if (elseBlockLimit > 0) {
            builder
                    .withStatementLimit(elseBlockLimit);
            if (controlDeviation == If.IfPart.ELSE) {
                elseBlock = builder.setSubBlock(false)
                    .setCanHaveBreaks(canHaveBreaks)
                    .setCanHaveContinues(canHaveContinues)
                    .setCanHaveReturn(canHaveReturn)
                    .produceBlock();
            } else {
                elseBlock = builder.setSubBlock(false)
                    .setCanHaveBreaks(false)
                    .setCanHaveContinues(false)
                    .setCanHaveReturn(false)
                    .produceBlock();
            }
        }
        // setChild(If.IfPart.ELSE.ordinal(), elseBlock);
        return new If(condition, thenBlock, elseBlock, level);
    }
}
