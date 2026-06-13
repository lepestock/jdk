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
import jdk.test.lib.jittester.MethodArgumentConstraint;
import jdk.test.lib.jittester.OperatorKind;
import jdk.test.lib.jittester.ProductionFailedException;
import jdk.test.lib.jittester.Type;
import jdk.test.lib.jittester.TypeList;
import jdk.test.lib.jittester.types.TypeKlass;
import jdk.test.lib.jittester.utils.PseudoRandom;
import jdk.test.lib.jittester.utils.TypeBoxingUtil;

/**
 * Produces integral expressions for method arguments with a value-domain
 * constraint that is narrower than the Java type itself.
 *
 * <p>The constraint is a boundary property of the whole argument expression.
 * The factory therefore delegates to normal expression production first and
 * then wraps the completed expression. This preserves expression diversity and
 * evaluates the raw expression once. A global, very small raw path is kept so
 * tests can still occasionally exercise the exceptional method behavior.</p>
 */
class ConstrainedIntegralExpressionFactory extends Factory<IRNode> {
    private static final int LOW_MASK_PERCENT = 75;
    private static final int UNSIGNED_SHIFT_PERCENT = 20;
    private static final int SMALL_MOD_PERCENT = 70;
    private static final int INT_RANGE_SHIFT_PERCENT = 70;

    private static final int[] NONNEGATIVE_MASKS = {
            1, 1, 3, 3, 7, 7, 15, 15, 31, 31, 63, 127, 255
    };
    private static final long[] LONG_NONNEGATIVE_MASKS = {
            1L, 1L, 3L, 3L, 7L, 7L, 15L, 15L, 31L, 31L, 63L, 127L, 255L
    };
    private static final int[] SMALL_NONNEGATIVE_MASKS = {
            1, 1, 1, 3, 3, 3, 7
    };
    private static final long[] LONG_SMALL_NONNEGATIVE_MASKS = {
            1L, 1L, 1L, 3L, 3L, 3L, 7L
    };
    private static final int[] INT_UNSIGNED_SHIFTS = {
            24, 25, 26, 27, 28, 29, 30, 1
    };
    private static final int[] LONG_UNSIGNED_SHIFTS = {
            56, 57, 58, 59, 60, 61, 62, 1
    };
    private static final int[] SMALL_INT_MODULI = {
            9, 9, 17, 17, 33
    };
    private static final long[] SMALL_LONG_MODULI = {
            9L, 9L, 17L, 17L, 33L
    };
    private static final int[] INT_RANGE_SHIFTS = {
            32, 33, 34, 35, 36, 40, 48
    };

    private final long complexityLimit;
    private final int operatorLimit;
    private final TypeKlass ownerClass;
    private final Type resultType;
    private final boolean exceptionSafe;
    private final boolean noconsts;
    private final MethodArgumentConstraint constraint;

    ConstrainedIntegralExpressionFactory(long complexityLimit, int operatorLimit, TypeKlass ownerClass,
            Type resultType, boolean exceptionSafe, boolean noconsts, MethodArgumentConstraint constraint) {
        this.complexityLimit = complexityLimit;
        this.operatorLimit = operatorLimit;
        this.ownerClass = ownerClass;
        this.resultType = resultType;
        this.exceptionSafe = exceptionSafe;
        this.noconsts = noconsts;
        this.constraint = constraint;
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
        if (ExpressionGuards.shouldSkipGuard() || !isIntegral(raw.getResultType())) {
            return raw;
        }
        Type guardType = guardType(raw.getResultType());
        return switch (constraint) {
            case NONNEGATIVE -> guardNonNegative(raw, guardType, false);
            case SMALL_NONNEGATIVE -> guardNonNegative(raw, guardType, true);
            case NONMIN -> guardNonMin(raw, guardType);
            case NONMAX -> guardNonMax(raw, guardType);
            case INT_RANGE -> guardIntRange(raw, guardType);
            case SMALL_INTEGRAL -> guardSmallIntegral(raw, guardType);
            default -> raw;
        };
    }

    static boolean canApplyTo(Type type, MethodArgumentConstraint constraint) {
        return switch (constraint) {
            case NONNEGATIVE, SMALL_NONNEGATIVE, NONMIN, NONMAX, INT_RANGE, SMALL_INTEGRAL -> isIntegral(type);
            default -> false;
        };
    }

    private static IRNode guardNonNegative(IRNode raw, Type guardType, boolean small) {
        int choice = PseudoRandom.randomNotNegative(100);
        int shiftPercent = small ? 10 : UNSIGNED_SHIFT_PERCENT;
        if (choice < (small ? 90 : LOW_MASK_PERCENT)) {
            return new BinaryOperator(OperatorKind.BIT_AND, guardType, raw, nonNegativeMask(guardType, small));
        }
        if (choice < (small ? 90 : LOW_MASK_PERCENT) + shiftPercent) {
            return new BinaryOperator(OperatorKind.SAR, guardType, raw, unsignedShiftAmount(guardType, small));
        }
        return new BinaryOperator(OperatorKind.BIT_AND, guardType, raw, signBitMask(guardType));
    }

    private static IRNode guardNonMin(IRNode raw, Type guardType) {
        return new BinaryOperator(OperatorKind.BIT_OR, guardType, raw, literal(guardType, 1, 1L));
    }

    private static IRNode guardNonMax(IRNode raw, Type guardType) {
        return new BinaryOperator(OperatorKind.BIT_AND, guardType, raw, literal(guardType, -2, -2L));
    }

    private static IRNode guardIntRange(IRNode raw, Type guardType) {
        if (!guardType.equals(TypeList.LONG)) {
            return raw;
        }
        if (PseudoRandom.randomNotNegative(100) < INT_RANGE_SHIFT_PERCENT) {
            int shift = INT_RANGE_SHIFTS[PseudoRandom.randomNotNegative(INT_RANGE_SHIFTS.length)];
            return new BinaryOperator(OperatorKind.SHR, TypeList.LONG, raw, new Literal(shift, TypeList.INT));
        }
        return new BinaryOperator(OperatorKind.BIT_AND, TypeList.LONG, raw,
                new Literal((long) Integer.MAX_VALUE, TypeList.LONG));
    }

    private static IRNode guardSmallIntegral(IRNode raw, Type guardType) {
        if (PseudoRandom.randomNotNegative(100) < SMALL_MOD_PERCENT) {
            return new BinaryOperator(OperatorKind.MOD, guardType, raw, smallModulus(guardType));
        }
        return new BinaryOperator(OperatorKind.BIT_AND, guardType, raw, nonNegativeMask(guardType, true));
    }

    private static Literal nonNegativeMask(Type guardType, boolean small) {
        if (guardType.equals(TypeList.LONG)) {
            long[] masks = small ? LONG_SMALL_NONNEGATIVE_MASKS : LONG_NONNEGATIVE_MASKS;
            return new Literal(masks[PseudoRandom.randomNotNegative(masks.length)], TypeList.LONG);
        }
        int[] masks = small ? SMALL_NONNEGATIVE_MASKS : NONNEGATIVE_MASKS;
        return new Literal(masks[PseudoRandom.randomNotNegative(masks.length)], TypeList.INT);
    }

    private static Literal smallModulus(Type guardType) {
        if (guardType.equals(TypeList.LONG)) {
            long modulus = SMALL_LONG_MODULI[PseudoRandom.randomNotNegative(SMALL_LONG_MODULI.length)];
            return new Literal(modulus, TypeList.LONG);
        }
        int modulus = SMALL_INT_MODULI[PseudoRandom.randomNotNegative(SMALL_INT_MODULI.length)];
        return new Literal(modulus, TypeList.INT);
    }

    private static Literal unsignedShiftAmount(Type guardType, boolean small) {
        int[] shifts = guardType.equals(TypeList.LONG) ? LONG_UNSIGNED_SHIFTS : INT_UNSIGNED_SHIFTS;
        int shift = shifts[PseudoRandom.randomNotNegative(shifts.length)];
        if (small && shift == 1) {
            shift = guardType.equals(TypeList.LONG) ? 60 : 28;
        }
        return new Literal(shift, TypeList.INT);
    }

    private static Literal signBitMask(Type guardType) {
        return guardType.equals(TypeList.LONG)
                ? new Literal(Long.MAX_VALUE, TypeList.LONG)
                : new Literal(Integer.MAX_VALUE, TypeList.INT);
    }

    private static Literal literal(Type guardType, int intValue, long longValue) {
        return guardType.equals(TypeList.LONG)
                ? new Literal(longValue, TypeList.LONG)
                : new Literal(intValue, TypeList.INT);
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
}
