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
import java.util.List;
import jdk.test.lib.jittester.BinaryOperator;
import jdk.test.lib.jittester.CastOperator;
import jdk.test.lib.jittester.GenerationState;
import jdk.test.lib.jittester.IRNode;
import jdk.test.lib.jittester.Literal;
import jdk.test.lib.jittester.LocalVariable;
import jdk.test.lib.jittester.OperatorKind;
import jdk.test.lib.jittester.ProductionFailedException;
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
import jdk.test.lib.jittester.utils.TypeBoxingUtil;

/**
 * Produces array element accesses of shape array[iterationVariable].
 * The array base is intentionally simple (local/static variable) to keep this
 * deterministic and avoid extra factory complexity in kernel-map contexts.
 */
class IterationIndexedCollectionElementFactory extends SafeFactory<IRNode> {
    private final TypeKlass ownerClass;
    private final Type elementType;
    private final boolean assignmentCompatible;
    private final boolean arrayOnly;
    private final boolean nullRestrictedOnly;
    private final int iterationOffset;

    IterationIndexedCollectionElementFactory(TypeKlass ownerClass, Type elementType) {
        this(ownerClass, elementType, false, false);
    }

    IterationIndexedCollectionElementFactory(TypeKlass ownerClass, Type elementType,
            boolean assignmentCompatible) {
        this(ownerClass, elementType, assignmentCompatible, false);
    }

    IterationIndexedCollectionElementFactory(TypeKlass ownerClass, Type elementType,
            boolean assignmentCompatible, boolean arrayOnly) {
        this(ownerClass, elementType, assignmentCompatible, arrayOnly, 0);
    }

    IterationIndexedCollectionElementFactory(TypeKlass ownerClass, Type elementType,
            boolean assignmentCompatible, boolean arrayOnly, int iterationOffset) {
        this(ownerClass, elementType, assignmentCompatible, arrayOnly, false, iterationOffset);
    }

    IterationIndexedCollectionElementFactory(TypeKlass ownerClass, Type elementType,
            boolean assignmentCompatible, boolean arrayOnly, boolean nullRestrictedOnly, int iterationOffset) {
        this.ownerClass = ownerClass;
        this.elementType = elementType;
        this.assignmentCompatible = assignmentCompatible;
        this.arrayOnly = arrayOnly;
        this.nullRestrictedOnly = nullRestrictedOnly;
        this.iterationOffset = iterationOffset;
    }

    @Override
    protected IRNode sproduce() throws ProductionFailedException {
        String iterationVariable = GenerationState.currentFlowParams().iterationVariable();
        if (iterationVariable == null || iterationVariable.isBlank()) {
            throw new ProductionFailedException();
        }
        Symbol iterationSymbol = SymbolTable.get(iterationVariable, VariableInfo.class);
        if (!(iterationSymbol instanceof VariableInfo iterationInfo)) {
            throw new ProductionFailedException();
        }
        if (!TypeArray.isElementTypeAllowed(elementType)) {
            throw new ProductionFailedException();
        }

        ArrayList<IRNode> arrayCandidates = new ArrayList<>();
        ArrayList<VariableInfo> arrayCandidateInfos = new ArrayList<>();
        for (Symbol symbol : candidateSymbols()) {
            if (!(symbol instanceof VariableInfo varInfo)) {
                continue;
            }
            if ((varInfo.flags & VariableInfo.INITIALIZED) == 0) {
                continue;
            }
            if (varInfo.getArrayLength().isEmpty()) {
                continue;
            }
            if (!canUseIterationIndex(varInfo, iterationOffset)) {
                continue;
            }
            if (varInfo.isLocal()) {
                arrayCandidates.add(new LocalVariable(varInfo));
                arrayCandidateInfos.add(varInfo);
            } else if (varInfo.isStatic()) {
                arrayCandidates.add(new StaticMemberVariable(ownerClass, varInfo));
                arrayCandidateInfos.add(varInfo);
            }
        }
        if (arrayCandidates.isEmpty()) {
            throw new ProductionFailedException();
        }
        int selected = PseudoRandom.randomNotNegative(arrayCandidates.size());
        IRNode baseArray = arrayCandidates.get(selected);
        VariableInfo baseArrayInfo = arrayCandidateInfos.get(selected);
        if (baseArrayInfo.getArrayLength().isEmpty()) {
            throw new ProductionFailedException();
        }
        ArrayList<IRNode> indexes = new ArrayList<>(1);
        indexes.add(indexExpression(iterationInfo));
        CollectionElement element = new CollectionElement(baseArray, indexes, storageKind(baseArrayInfo));
        if (element.getResultType().equals(elementType)) {
            return element;
        }
        return new CastOperator(elementType, element);
    }

    boolean hasCandidates() {
        String iterationVariable = GenerationState.currentFlowParams().iterationVariable();
        if (iterationVariable == null || iterationVariable.isBlank()) {
            return false;
        }
        Symbol iterationSymbol = SymbolTable.get(iterationVariable, VariableInfo.class);
        if (!(iterationSymbol instanceof VariableInfo)) {
            return false;
        }
        if (!TypeArray.isElementTypeAllowed(elementType)) {
            return false;
        }
        for (Symbol symbol : candidateSymbols()) {
            if (!(symbol instanceof VariableInfo varInfo)) {
                continue;
            }
            if ((varInfo.flags & VariableInfo.INITIALIZED) == 0) {
                continue;
            }
            if (varInfo.getArrayLength().isEmpty()) {
                continue;
            }
            if (!canUseIterationIndex(varInfo, iterationOffset)) {
                continue;
            }
            if (varInfo.isLocal() || varInfo.isStatic()) {
                return true;
            }
        }
        return false;
    }

    private List<Symbol> candidateSymbols() {
        if (!assignmentCompatible) {
            ArrayList<Symbol> result = new ArrayList<>();
            Iterable<Symbol> symbols = nullRestrictedOnly
                    ? SymbolTable.getAllCombined(VariableInfo.class)
                    : SymbolTable.get(new TypeArray(elementType, 1), VariableInfo.class);
            for (Symbol symbol : symbols) {
                if (!(symbol instanceof VariableInfo varInfo)) {
                    continue;
                }
                if (!(varInfo.type instanceof TypeArray arrayType)) {
                    continue;
                }
                if (arrayOnly && arrayType.getStorageKind() != IndexedStorageKind.ARRAY) {
                    continue;
                }
                if (nullRestrictedOnly
                        && (!arrayType.isNullRestricted()
                                || arrayType.dimensions != 1
                                || !arrayType.type.equals(elementType))) {
                    continue;
                }
                result.add(symbol);
            }
            return result;
        }
        ArrayList<Symbol> result = new ArrayList<>();
        for (Symbol symbol : SymbolTable.getAllCombined(VariableInfo.class)) {
            if (!(symbol instanceof VariableInfo varInfo)) {
                continue;
            }
            if (!(varInfo.type instanceof TypeArray arrayType)) {
                continue;
            }
            if (arrayOnly && arrayType.getStorageKind() != IndexedStorageKind.ARRAY) {
                continue;
            }
            if (nullRestrictedOnly && !arrayType.isNullRestricted()) {
                continue;
            }
            if (arrayType.dimensions != 1) {
                continue;
            }
            if (!TypeArray.isElementTypeAllowed(arrayType.type)) {
                continue;
            }
            if (TypeBoxingUtil.isAssignmentCompatibleWithBoxing(arrayType.type, elementType)) {
                result.add(varInfo);
            }
        }
        return result;
    }

    private IRNode indexExpression(VariableInfo iterationInfo) {
        IRNode index = new LocalVariable(iterationInfo);
        if (iterationOffset == 0) {
            return index;
        }
        if (iterationOffset < 0) {
            return new BinaryOperator(OperatorKind.SUB, TypeList.INT, index,
                    new Literal(-iterationOffset, TypeList.INT));
        }
        return new BinaryOperator(OperatorKind.ADD, TypeList.INT, index,
                new Literal(iterationOffset, TypeList.INT));
    }

    private static boolean canUseIterationIndex(VariableInfo varInfo, int offset) {
        int iterationStart = GenerationState.currentFlowParams().arrayKernelIterationStart();
        int iterationLimit = GenerationState.currentFlowParams().arrayKernelIterationLimit();
        if (iterationStart + offset < 0) {
            return false;
        }
        return iterationLimit <= 0 || varInfo.getArrayLength().orElse(0) >= iterationLimit + offset;
    }

    private static IndexedStorageKind storageKind(VariableInfo variableInfo) {
        return variableInfo.type instanceof TypeArray arrayType
                ? arrayType.getStorageKind()
                : IndexedStorageKind.ARRAY;
    }
}
