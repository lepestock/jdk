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
 * Produces an expression suitable for the right operand of a division-like
 * operator ({@code /}, {@code %}, {@code /=}, or {@code %=}).
 *
 * <p>Being a denominator is a local parent-child requirement, not a scoped
 * expression-generation mode. In {@code a / (b + c)}, the whole {@code b + c}
 * expression must be non-zero, but the nested operands do not individually
 * need to be non-zero. For that reason this factory first delegates to normal
 * expression generation and then decides whether the completed denominator
 * needs a boundary guard.</p>
 *
 * <p>Floating-point division by zero does not throw in Java, so floating
 * denominators are left unchanged. Integral denominators are usually guarded
 * with {@code raw | smallMask}: it evaluates the generated expression once,
 * preserves array/variable-heavy expression shapes, and guarantees a non-zero
 * value. Small masks are strongly preferred to avoid inflating denominators.
 * A very small raw path is intentionally kept so generated tests still contain
 * rare unguarded division-like operations and can exercise exception behavior.
 * Obvious safe shapes, such as non-zero integral literals, are kept as-is.</p>
 */
class DenominatorExpressionFactory extends Factory<IRNode> {
    private static final int RAW_PROBABILITY_DIVISOR = 5;
    private static final double RARE_NONZERO_MASK_PROBABILITY = 0.10;
    private static final int[] COMMON_INT_NONZERO_MASKS = {
            1, 1, 1, 1, 1, 1, -1, -1, -1, -1, 3, -3
    };
    private static final int[] RARE_INT_NONZERO_MASKS = {
            5, -5, 7, -7, 10, -10, 15, -15, 31, -31
    };
    private static final long[] COMMON_LONG_NONZERO_MASKS = {
            1L, 1L, 1L, 1L, 1L, 1L, -1L, -1L, -1L, -1L, 3L, -3L
    };
    private static final long[] RARE_LONG_NONZERO_MASKS = {
            5L, -5L, 7L, -7L, 10L, -10L, 15L, -15L, 31L, -31L
    };

    private final long complexityLimit;
    private final int operatorLimit;
    private final TypeKlass ownerClass;
    private final Type resultType;
    private final boolean exceptionSafe;
    private final boolean noconsts;

    DenominatorExpressionFactory(long complexityLimit, int operatorLimit, TypeKlass ownerClass,
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
        boolean guardIntegralDenominator = !ExpressionGuards.shouldSkipGuard(RAW_PROBABILITY_DIVISOR);
        int rawOperatorLimit = guardIntegralDenominator ? Math.max(0, operatorLimit - 1) : operatorLimit;
        IRNode raw = new IRNodeBuilder()
                .setComplexityLimit(complexityLimit)
                .setOperatorLimit(rawOperatorLimit)
                .setOwnerKlass(ownerClass)
                .setResultType(resultType)
                .setExceptionSafe(exceptionSafe)
                .setNoConsts(noconsts)
                .getExpressionFactory()
                .produce();
        if (!guardIntegralDenominator || !isIntegral(raw.getResultType()) || isDefinitelyNonZero(raw)) {
            return raw;
        }
        Type guardType = guardType(raw.getResultType());
        return new BinaryOperator(OperatorKind.BIT_OR, guardType, raw, nonZeroMask(guardType));
    }

    static boolean canThrowFor(Type leftType, Type rightType) {
        return isIntegral(leftType) && isIntegral(rightType);
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

    private static Literal nonZeroMask(Type guardType) {
        boolean rareMask = PseudoRandom.randomBoolean(RARE_NONZERO_MASK_PROBABILITY);
        if (guardType.equals(TypeList.LONG)) {
            long[] masks = rareMask ? RARE_LONG_NONZERO_MASKS : COMMON_LONG_NONZERO_MASKS;
            return new Literal(masks[PseudoRandom.randomNotNegative(masks.length)], TypeList.LONG);
        }
        int[] masks = rareMask ? RARE_INT_NONZERO_MASKS : COMMON_INT_NONZERO_MASKS;
        return new Literal(masks[PseudoRandom.randomNotNegative(masks.length)], TypeList.INT);
    }

    private static boolean isDefinitelyNonZero(IRNode node) {
        if (!(node instanceof Literal literal)) {
            return false;
        }
        Object value = literal.getValue();
        if (value instanceof Number number) {
            return number.longValue() != 0L;
        }
        if (value instanceof Character ch) {
            return ch.charValue() != 0;
        }
        return false;
    }
}
