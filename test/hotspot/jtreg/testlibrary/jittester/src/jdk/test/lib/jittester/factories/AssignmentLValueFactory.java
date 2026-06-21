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

import java.util.ArrayList;
import jdk.test.lib.jittester.GenerationState;
import jdk.test.lib.jittester.IRNode;
import jdk.test.lib.jittester.Literal;
import jdk.test.lib.jittester.LocalVariable;
import jdk.test.lib.jittester.ProductionFailedException;
import jdk.test.lib.jittester.ProductionParams;
import jdk.test.lib.jittester.Rule;
import jdk.test.lib.jittester.StaticMemberVariable;
import jdk.test.lib.jittester.Symbol;
import jdk.test.lib.jittester.SymbolTable;
import jdk.test.lib.jittester.Type;
import jdk.test.lib.jittester.TypeList;
import jdk.test.lib.jittester.VariableInfo;
import jdk.test.lib.jittester.collections.CollectionElement;
import jdk.test.lib.jittester.collections.IndexedStorageKind;
import jdk.test.lib.jittester.types.TypeArray;
import jdk.test.lib.jittester.types.TypeKlass;
import jdk.test.lib.jittester.utils.PseudoRandom;

class AssignmentLValueFactory extends Factory<IRNode> {
    private static final int LVALUE_PICK_RETRIES = 16;
    private static final double ARRAY_ELEMENT_LVALUE_WEIGHT = 3.0;

    private final long complexityLimit;
    private final int operatorLimit;
    private final TypeKlass ownerClass;
    private final Type resultType;
    private final boolean exceptionSafe;
    private final boolean noconsts;

    AssignmentLValueFactory(long complexityLimit, int operatorLimit, TypeKlass ownerClass,
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
        Rule<IRNode> rule = new Rule<>("assignment_lvalue");
        ReadOnlyLocalLValueFactory variableFactory = variableLValueFactory(
                complexityLimit, operatorLimit, ownerClass, resultType, exceptionSafe, noconsts);
        if (variableFactory.hasCandidates(v -> true)) {
            rule.add("variable_lvalue", variableFactory);
        }
        CollectionElementLValueFactory arrayElementFactory = new CollectionElementLValueFactory(ownerClass, resultType);
        if (arrayElementFactory.hasCandidates()) {
            rule.add("array_element_lvalue", arrayElementFactory, ARRAY_ELEMENT_LVALUE_WEIGHT);
        }
        if (rule.size() == 0) {
            throw new ProductionFailedException();
        }
        return rule.produce();
    }

    static boolean hasCandidates(long complexityLimit, int operatorLimit, TypeKlass ownerClass,
            Type resultType, boolean exceptionSafe, boolean noconsts) {
        return variableLValueFactory(complexityLimit, operatorLimit, ownerClass,
                resultType, exceptionSafe, noconsts).hasCandidates(v -> true)
                || new CollectionElementLValueFactory(ownerClass, resultType).hasCandidates();
    }

    private static ReadOnlyLocalLValueFactory variableLValueFactory(long complexityLimit, int operatorLimit,
            TypeKlass ownerClass, Type resultType, boolean exceptionSafe, boolean noconsts) {
        return new ReadOnlyLocalLValueFactory(new IRNodeBuilder()
                .withComplexityLimit(Math.max(1L, complexityLimit))
                .withOperatorLimit(Math.max(1, operatorLimit))
                .setOwnerKlass(ownerClass)
                .setResultType(resultType)
                .setExceptionSafe(exceptionSafe)
                .setNoConsts(noconsts)
                .setIsConstant(false)
                .setIsInitialized(true)
                .getVariableFactory(),
                LVALUE_PICK_RETRIES);
    }

    private static final class CollectionElementLValueFactory extends SafeFactory<IRNode> {
        private final TypeKlass ownerClass;
        private final Type elementType;

        CollectionElementLValueFactory(TypeKlass ownerClass, Type elementType) {
            this.ownerClass = ownerClass;
            this.elementType = elementType;
        }

        @Override
        protected IRNode sproduce() throws ProductionFailedException {
            ArrayList<VariableInfo> candidates = candidates();
            if (candidates.isEmpty()) {
                throw new ProductionFailedException();
            }
            VariableInfo arrayInfo = candidates.get(PseudoRandom.randomNotNegative(candidates.size()));
            IRNode baseArray = arrayInfo.isLocal()
                    ? new LocalVariable(arrayInfo)
                    : new StaticMemberVariable(ownerClass, arrayInfo);
            ArrayList<IRNode> indexes = new ArrayList<>(1);
            indexes.add(indexExpression(arrayInfo));
            return new CollectionElement(baseArray, indexes);
        }

        boolean hasCandidates() {
            return !candidates().isEmpty();
        }

        private ArrayList<VariableInfo> candidates() {
            ArrayList<VariableInfo> result = new ArrayList<>();
            if (!ProductionParams.arrayKernelCollectionElementLValues.value()
                    || !GenerationState.currentFlowParams().inArrayKernel()
                    || !TypeArray.isElementTypeAllowed(elementType)) {
                return result;
            }
            for (Symbol symbol : SymbolTable.get(new TypeArray(elementType, 1), VariableInfo.class)) {
                if (!(symbol instanceof VariableInfo varInfo)) {
                    continue;
                }
                if (varInfo.type instanceof TypeArray arrayType
                        && arrayType.getStorageKind() != IndexedStorageKind.ARRAY) {
                    continue;
                }
                if ((varInfo.flags & VariableInfo.INITIALIZED) == 0) {
                    continue;
                }
                if (varInfo.getArrayLength().isEmpty()) {
                    continue;
                }
                if (varInfo.isLocal() || varInfo.isStatic()) {
                    result.add(varInfo);
                }
            }
            return result;
        }

        private IRNode indexExpression(VariableInfo arrayInfo) {
            String iterationVariable = GenerationState.currentFlowParams().iterationVariable();
            Symbol iterationSymbol = iterationVariable == null
                    ? null
                    : SymbolTable.get(iterationVariable, VariableInfo.class);
            if (iterationSymbol instanceof VariableInfo iterationInfo && PseudoRandom.randomBoolean()) {
                return new LocalVariable(iterationInfo);
            }
            int arrayLength = arrayInfo.getArrayLength().orElse(GenerationState.preferredIntCollectionSize());
            return new Literal(PseudoRandom.randomNotNegative(arrayLength), TypeList.INT);
        }
    }
}
