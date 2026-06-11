/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.util.Pair;
import jdk.test.lib.jittester.arrays.ArrayElement;
import jdk.test.lib.jittester.BinaryOperator;
import jdk.test.lib.jittester.GenerationState;
import jdk.test.lib.jittester.IRNode;
import jdk.test.lib.jittester.OperatorKind;
import jdk.test.lib.jittester.ProductionFailedException;
import jdk.test.lib.jittester.Rule;
import jdk.test.lib.jittester.Type;
import jdk.test.lib.jittester.TypeList;
import jdk.test.lib.jittester.VariableBase;
import jdk.test.lib.jittester.VariableInfo;
import jdk.test.lib.jittester.diagnostics.ArrayAssignmentDiagnostics;
import jdk.test.lib.jittester.diagnostics.Diagnostics;
import jdk.test.lib.jittester.types.TypeKlass;
import jdk.test.lib.jittester.utils.PseudoRandom;
import jdk.test.lib.jittester.utils.TypeBoxingUtil;

class AssignmentOperatorImplFactory extends BinaryOperatorFactory {
    private static final int LVALUE_PICK_RETRIES = 16;
    private static final int ARRAY_KERNEL_LVALUE_COMPLEXITY_PERCENT = 5;
    private static final int ARRAY_KERNEL_LVALUE_OPERATOR_PERCENT = 10;
    private static final ArrayAssignmentDiagnostics ARRAY_ASSIGNMENT_DIAGNOSTICS =
            Diagnostics.arrayAssignment();

    AssignmentOperatorImplFactory(long complexityLimit, int operatorLimit, TypeKlass ownerClass,
            Type resultType, boolean exceptionSafe, boolean noconsts) {
        super(OperatorKind.ASSIGN, complexityLimit, operatorLimit, ownerClass, resultType, exceptionSafe, noconsts);
    }

    @Override
    protected boolean isApplicable(Type resultType) {
        return true;
    }

    @Override
    protected Pair<Type, Type> generateTypes() {
        if (hasFixedOperandType()) {
            Type fixedType = fixedOr(resultType);
            if (!resultType.equals(fixedType)) {
                throw new IllegalArgumentException();
            }
            return new Pair<>(resultType, fixedType);
        }
        return new Pair<>(resultType, PseudoRandom.randomElement(
                TypeBoxingUtil.getAssignmentCompatibleWithBoxing(TypeList.getAll(), resultType)));
    }

    @Override
    protected BinaryOperator generateProduction(Type leftOperandType, Type rightOperandType)
            throws ProductionFailedException {
        boolean inArrayKernel = GenerationState.currentFlowParams().inArrayKernel();
        long leftComplexityLimit = inArrayKernel
                ? percentageLimit(complexityLimit, ARRAY_KERNEL_LVALUE_COMPLEXITY_PERCENT)
                : (long) (PseudoRandom.random() * complexityLimit);
        long rightComplexityLimit = complexityLimit - leftComplexityLimit;
        int leftOperatorLimit = inArrayKernel
                ? percentageLimit(operatorLimit, ARRAY_KERNEL_LVALUE_OPERATOR_PERCENT)
                : (int) (PseudoRandom.random() * operatorLimit);
        int rightOperatorLimit = operatorLimit - leftOperatorLimit;
        if (leftOperatorLimit <= 0 || rightOperatorLimit <= 0
                || leftComplexityLimit <= 0 || rightComplexityLimit <= 0) {
            throw new ProductionFailedException();
        }
        IRNodeBuilder builder = new IRNodeBuilder().setOwnerKlass((TypeKlass) ownerClass)
                .setExceptionSafe(exceptionSafe)
                .setNoConsts(noconsts)
                .setComplexityLimit(leftComplexityLimit)
                .setOperatorLimit(leftOperatorLimit)
                .setResultType(leftOperandType)
                .setIsConstant(false);
        Rule<IRNode> rule = new Rule<>("assignment");
        if (inArrayKernel) {
            String iterationVariable = GenerationState.currentFlowParams().iterationVariable();
            rule.add("initialized_nonconst_var",
                    new ReadOnlyLocalLValueFactory(
                            new ExcludingLocalVariableLValueFactory(
                                    builder.setIsInitialized(true).getVariableFactory(),
                                    iterationVariable,
                                    LVALUE_PICK_RETRIES),
                            LVALUE_PICK_RETRIES));
            rule.add("uninitialized_nonconst_var",
                    new ReadOnlyLocalLValueFactory(
                            new ExcludingLocalVariableLValueFactory(
                                    builder.setIsInitialized(false).getVariableFactory(),
                                    iterationVariable,
                                    LVALUE_PICK_RETRIES),
                            LVALUE_PICK_RETRIES));
            // Inside array-kernel blocks, array lvalue indices must follow the kernel iterator.
            rule.add("array_element_lvalue",
                    new IterationIndexedArrayElementFactory((TypeKlass) ownerClass, leftOperandType), 5.0);
        } else {
            rule.add("initialized_nonconst_var",
                    new ReadOnlyLocalLValueFactory(builder.setIsInitialized(true).getVariableFactory(),
                            LVALUE_PICK_RETRIES));
            rule.add("uninitialized_nonconst_var",
                    new ReadOnlyLocalLValueFactory(builder.setIsInitialized(false).getVariableFactory(),
                            LVALUE_PICK_RETRIES));
        }
        IRNode leftOperandValue = rule.produce();
        boolean preferIndexedArrayTerminal = inArrayKernel && leftOperandValue instanceof ArrayElement;
        Type effectiveRightOperandType = preferIndexedArrayTerminal ? leftOperandType : rightOperandType;
        ArrayAssignmentDiagnostics.Snapshot diagnosticSnapshot =
                ARRAY_ASSIGNMENT_DIAGNOSTICS.snapshot(leftOperandType, effectiveRightOperandType);
        IRNode rightOperandValue = builder.setComplexityLimit(rightComplexityLimit)
                .setOperatorLimit(rightOperatorLimit)
                .setResultType(effectiveRightOperandType)
                .withPreferIterationIndexedArrayTerminal(preferIndexedArrayTerminal)
                .setFixedOperandType(preferIndexedArrayTerminal ? leftOperandType : null)
                .produceExpression();
        try {
            if (leftOperandValue instanceof VariableBase variableBase
                    && (variableBase.getVariableInfo().flags & VariableInfo.INITIALIZED) == 0) {
                variableBase.getVariableInfo().flags |= VariableInfo.INITIALIZED;
            }
        } catch (Exception e) {
            throw new ProductionFailedException(e.getMessage());
        }
        BinaryOperator result = new BinaryOperator(opKind, resultType, leftOperandValue, rightOperandValue);
        ARRAY_ASSIGNMENT_DIAGNOSTICS.attach(diagnosticSnapshot, result, opKind,
                leftOperandValue, rightOperandValue);
        return result;
    }

    private static long percentageLimit(long limit, int percent) {
        return Math.max(1L, Math.min(limit - 1L, (long) Math.ceil(limit * (percent / 100.0))));
    }

    private static int percentageLimit(int limit, int percent) {
        return Math.max(1, Math.min(limit - 1, (int) Math.ceil(limit * (percent / 100.0))));
    }

    private static final class ExcludingLocalVariableLValueFactory extends Factory<IRNode> {
        private final Factory<? extends IRNode> delegate;
        private final String forbiddenLocalName;
        private final int retries;

        private ExcludingLocalVariableLValueFactory(Factory<? extends IRNode> delegate,
                                                    String forbiddenLocalName,
                                                    int retries) {
            this.delegate = delegate;
            this.forbiddenLocalName = forbiddenLocalName;
            this.retries = retries;
        }

        @Override
        public IRNode produce() throws ProductionFailedException {
            if (forbiddenLocalName == null || forbiddenLocalName.isBlank()) {
                return delegate.produce();
            }
            ProductionFailedException lastFailure = null;
            for (int i = 0; i < retries; i++) {
                try {
                    IRNode candidate = delegate.produce();
                    if (candidate instanceof VariableBase variableBase
                            && variableBase.getVariableInfo().isLocal()
                            && forbiddenLocalName.equals(variableBase.getVariableInfo().name)) {
                        continue;
                    }
                    return candidate;
                } catch (ProductionFailedException e) {
                    lastFailure = e;
                }
            }
            if (lastFailure != null) {
                throw lastFailure;
            }
            throw new ProductionFailedException();
        }
    }
}
