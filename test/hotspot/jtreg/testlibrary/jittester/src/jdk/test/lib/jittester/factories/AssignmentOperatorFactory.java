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

import jdk.test.lib.jittester.IRNode;
import jdk.test.lib.jittester.Operator;
import jdk.test.lib.jittester.OperatorKind;
import jdk.test.lib.jittester.ProductionFailedException;
import jdk.test.lib.jittester.Rule;
import jdk.test.lib.jittester.SymbolTable;
import jdk.test.lib.jittester.Type;
import jdk.test.lib.jittester.TypeList;
import jdk.test.lib.jittester.types.TypeKlass;
import jdk.test.lib.jittester.utils.PseudoRandom;
import jdk.test.lib.jittester.utils.TypeBoxingUtil;

class AssignmentOperatorFactory extends Factory<Operator> {
    private static final double COMPOUND_DIVISION_LIKE_WEIGHT = 0.05;
    private final int operatorLimit;
    private final long complexityLimit;
    private final Type resultType;
    private final boolean exceptionSafe;
    private final boolean noconsts;
    private final TypeKlass ownerClass;

    private Rule<Operator> fillRule(Type resultType) throws ProductionFailedException {
        Rule<Operator> rule = new Rule<>("assignment");
        IRNodeBuilder builder = new IRNodeBuilder()
                .setComplexityLimit(complexityLimit)
                .setOperatorLimit(operatorLimit)
                .setOwnerKlass(ownerClass)
                .setResultType(resultType)
                .setExceptionSafe(exceptionSafe)
                .setNoConsts(noconsts);
        rule.add("simple_assign", builder.setOperatorKind(OperatorKind.ASSIGN).getBinaryOperatorFactory());
        boolean hasVariableLValue = hasInitializedWritableVariableLValue(resultType);
        if (hasVariableLValue && supportsCompoundArithmetic(resultType)) {
            rule.add("compound_add", builder.setOperatorKind(OperatorKind.COMPOUND_ADD).getBinaryOperatorFactory());
            rule.add("compound_sub", builder.setOperatorKind(OperatorKind.COMPOUND_SUB).getBinaryOperatorFactory());
            rule.add("compound_mul", builder.setOperatorKind(OperatorKind.COMPOUND_MUL).getBinaryOperatorFactory());
            if (!exceptionSafe) {
                // Keep division-like compound assignments available but rare; denominator
                // guarding handles most generated cases, while a small raw tail remains.
                rule.add("compound_div", builder.setOperatorKind(OperatorKind.COMPOUND_DIV).getBinaryOperatorFactory(),
                        COMPOUND_DIVISION_LIKE_WEIGHT);
                rule.add("compound_mod", builder.setOperatorKind(OperatorKind.COMPOUND_MOD).getBinaryOperatorFactory(),
                        COMPOUND_DIVISION_LIKE_WEIGHT);
            }
        }
        if (hasVariableLValue && supportsCompoundBitwise(resultType)) {
            rule.add("compound_and", builder.setOperatorKind(OperatorKind.COMPOUND_AND).getBinaryOperatorFactory());
            rule.add("compound_or", builder.setOperatorKind(OperatorKind.COMPOUND_OR).getBinaryOperatorFactory());
            rule.add("compound_xor", builder.setOperatorKind(OperatorKind.COMPOUND_XOR).getBinaryOperatorFactory());
        }
        if (hasVariableLValue && supportsShiftOrIncDec(resultType)) {
            rule.add("compound_shr", builder.setOperatorKind(OperatorKind.COMPOUND_SHR).getBinaryOperatorFactory());
            rule.add("compound_sar", builder.setOperatorKind(OperatorKind.COMPOUND_SAR).getBinaryOperatorFactory());
            rule.add("compound_shl", builder.setOperatorKind(OperatorKind.COMPOUND_SHL).getBinaryOperatorFactory());

            rule.add("prefix_inc", builder.setOperatorKind(OperatorKind.PRE_INC).getUnaryOperatorFactory());
            rule.add("prefix_dec", builder.setOperatorKind(OperatorKind.PRE_DEC).getUnaryOperatorFactory());
            rule.add("postfix_inc", builder.setOperatorKind(OperatorKind.POST_INC).getUnaryOperatorFactory());
            rule.add("postfix_dec", builder.setOperatorKind(OperatorKind.POST_DEC).getUnaryOperatorFactory());
        }
        return rule;
    }

    private boolean hasInitializedWritableVariableLValue(Type type) {
        Factory<? extends IRNode> variableFactory = new IRNodeBuilder()
                .setComplexityLimit(complexityLimit)
                .setOperatorLimit(operatorLimit)
                .setOwnerKlass(ownerClass)
                .setResultType(type)
                .setExceptionSafe(exceptionSafe)
                .setNoConsts(noconsts)
                .setIsConstant(false)
                .setIsInitialized(true)
                .getVariableFactory();
        return new ReadOnlyLocalLValueFactory(variableFactory, 1).hasCandidates(v -> true);
    }

    AssignmentOperatorFactory(long complexityLimit, int operatorLimit, TypeKlass ownerClass,
            Type resultType, boolean exceptionSafe, boolean noconsts) {
        this.ownerClass = ownerClass;
        this.complexityLimit = complexityLimit;
        this.operatorLimit = operatorLimit;
        this.resultType = resultType;
        this.exceptionSafe = exceptionSafe;
        this.noconsts = noconsts;
    }

    @Override
    public Operator produce() throws ProductionFailedException {
        if (resultType == null) { // if no result type is given - choose any.
            ArrayList<Type> allTypes = new ArrayList<>(TypeList.getAll());
            PseudoRandom.shuffle(allTypes);
            for (Type type : allTypes) {
                int symbolCheckpoint = SymbolTable.checkpoint();
                SymbolTable.push();
                try {
                    Operator result =  fillRule(type).produce();
                    SymbolTable.merge();
                    return result;
                } catch (ProductionFailedException e) {
                    SymbolTable.rollbackToCheckpoint(symbolCheckpoint);
                } catch (RuntimeException e) {
                    SymbolTable.rollbackToCheckpoint(symbolCheckpoint);
                    throw e;
                }
            }
        } else {
            return fillRule(resultType).produce();
        }
        throw new ProductionFailedException();
    }

    private static boolean supportsCompoundArithmetic(Type type) {
        return TypeBoxingUtil.isPrimitiveNumeric(type);
    }

    private static boolean supportsCompoundBitwise(Type type) {
        return TypeList.isBuiltInInt(type);
    }

    private static boolean supportsShiftOrIncDec(Type type) {
        return TypeList.isBuiltInInt(type) && !type.equals(TypeList.BOOLEAN);
    }
}
