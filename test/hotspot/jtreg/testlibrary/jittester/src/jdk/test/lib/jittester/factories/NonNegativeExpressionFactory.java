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
import jdk.test.lib.jittester.IRNode;
import jdk.test.lib.jittester.Literal;
import jdk.test.lib.jittester.OperatorKind;
import jdk.test.lib.jittester.ProductionFailedException;
import jdk.test.lib.jittester.Type;
import jdk.test.lib.jittester.TypeList;
import jdk.test.lib.jittester.types.TypeKlass;
import jdk.test.lib.jittester.utils.PseudoRandom;
import jdk.test.lib.jittester.utils.TypeBoxingUtil;

/**
 * Produces an expression suitable for method arguments that require a
 * non-negative integral value, such as exact power exponents.
 *
 * <p>The constraint is a boundary property of the whole argument expression, so
 * the factory delegates to normal expression production first and then guards
 * the completed value. This keeps expression variety and evaluates the raw
 * expression once while guaranteeing {@code >= 0} for integral types. Low
 * bounds are preferred because exact power exponents are much more useful when
 * they do not immediately overflow.</p>
 */
class NonNegativeExpressionFactory extends Factory<IRNode> {
    private static final int LOW_MASK_PERCENT = 75;
    private static final int UNSIGNED_SHIFT_PERCENT = 20;
    private static final int[] LOW_INT_NONNEGATIVE_MASKS = {
            1, 1, 3, 3, 7, 7, 15, 15, 31, 31, 63, 127, 255
    };
    private static final long[] LOW_LONG_NONNEGATIVE_MASKS = {
            1L, 1L, 3L, 3L, 7L, 7L, 15L, 15L, 31L, 31L, 63L, 127L, 255L
    };
    private static final int[] INT_UNSIGNED_SHIFTS = {
            24, 25, 26, 27, 28, 29, 30, 1
    };
    private static final int[] LONG_UNSIGNED_SHIFTS = {
            56, 57, 58, 59, 60, 61, 62, 1
    };

    private final long complexityLimit;
    private final int operatorLimit;
    private final TypeKlass ownerClass;
    private final Type resultType;
    private final boolean exceptionSafe;
    private final boolean noconsts;

    NonNegativeExpressionFactory(long complexityLimit, int operatorLimit, TypeKlass ownerClass,
            Type resultType, boolean exceptionSafe, boolean noconsts) {
        this.complexityLimit = complexityLimit;
        this.operatorLimit = operatorLimit;
        this.ownerClass = ownerClass;
        this.resultType = resultType;
        this.exceptionSafe = exceptionSafe;
        this.noconsts = noconsts;
    }

    @Override
    public IRNode produce() throws ProductionFailedException {
        IRNode raw = new IRNodeBuilder()
                .setComplexityLimit(complexityLimit)
                .setOperatorLimit(Math.max(0, operatorLimit - 1))
                .setOwnerKlass(ownerClass)
                .setResultType(resultType)
                .setExceptionSafe(exceptionSafe)
                .setNoConsts(noconsts)
                .getExpressionFactory()
                .produce();
        if (!isIntegral(raw.getResultType())) {
            return raw;
        }
        Type guardType = guardType(raw.getResultType());
        return guardNonNegative(raw, guardType);
    }

    static boolean canApplyTo(Type type) {
        return isIntegral(type);
    }

    private static boolean isIntegral(Type type) {
        Type primitiveType = TypeBoxingUtil.toPrimitiveType(type);
        return primitiveType != null
                && TypeList.isBuiltInInt(primitiveType)
                && !primitiveType.equals(TypeList.BOOLEAN);
    }

    private static Type guardType(Type type) {
        return TypeList.LONG.equals(TypeBoxingUtil.toPrimitiveType(type)) ? TypeList.LONG : TypeList.INT;
    }

    private static IRNode guardNonNegative(IRNode raw, Type guardType) {
        int choice = PseudoRandom.randomNotNegative(100);
        if (choice < LOW_MASK_PERCENT) {
            return new BinaryOperator(OperatorKind.BIT_AND, guardType, raw, lowMask(guardType));
        }
        if (choice < LOW_MASK_PERCENT + UNSIGNED_SHIFT_PERCENT) {
            return new BinaryOperator(OperatorKind.SAR, guardType, raw, unsignedShiftAmount(guardType));
        }
        return new BinaryOperator(OperatorKind.BIT_AND, guardType, raw, signBitMask(guardType));
    }

    private static Literal lowMask(Type guardType) {
        if (guardType.equals(TypeList.LONG)) {
            long mask = LOW_LONG_NONNEGATIVE_MASKS[PseudoRandom.randomNotNegative(LOW_LONG_NONNEGATIVE_MASKS.length)];
            return new Literal(mask, TypeList.LONG);
        }
        int mask = LOW_INT_NONNEGATIVE_MASKS[PseudoRandom.randomNotNegative(LOW_INT_NONNEGATIVE_MASKS.length)];
        return new Literal(mask, TypeList.INT);
    }

    private static Literal unsignedShiftAmount(Type guardType) {
        int[] shifts = guardType.equals(TypeList.LONG) ? LONG_UNSIGNED_SHIFTS : INT_UNSIGNED_SHIFTS;
        return new Literal(shifts[PseudoRandom.randomNotNegative(shifts.length)], TypeList.INT);
    }

    private static Literal signBitMask(Type guardType) {
        return guardType.equals(TypeList.LONG)
                ? new Literal(Long.MAX_VALUE, TypeList.LONG)
                : new Literal(Integer.MAX_VALUE, TypeList.INT);
    }
}
