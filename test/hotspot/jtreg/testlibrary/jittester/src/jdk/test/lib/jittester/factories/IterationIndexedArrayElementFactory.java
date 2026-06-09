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
import jdk.test.lib.jittester.GenerationState;
import jdk.test.lib.jittester.IRNode;
import jdk.test.lib.jittester.LocalVariable;
import jdk.test.lib.jittester.ProductionFailedException;
import jdk.test.lib.jittester.StaticMemberVariable;
import jdk.test.lib.jittester.Symbol;
import jdk.test.lib.jittester.SymbolTable;
import jdk.test.lib.jittester.Type;
import jdk.test.lib.jittester.VariableInfo;
import jdk.test.lib.jittester.arrays.ArrayElement;
import jdk.test.lib.jittester.types.TypeArray;
import jdk.test.lib.jittester.types.TypeKlass;
import jdk.test.lib.jittester.utils.PseudoRandom;

/**
 * Produces array element accesses of shape array[iterationVariable].
 * The array base is intentionally simple (local/static variable) to keep this
 * deterministic and avoid extra factory complexity in kernel-map contexts.
 */
class IterationIndexedArrayElementFactory extends SafeFactory<ArrayElement> {
    private final TypeKlass ownerClass;
    private final Type elementType;

    IterationIndexedArrayElementFactory(TypeKlass ownerClass, Type elementType) {
        this.ownerClass = ownerClass;
        this.elementType = elementType;
    }

    @Override
    protected ArrayElement sproduce() throws ProductionFailedException {
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

        TypeArray arrayType = new TypeArray(elementType, 1);
        List<Symbol> symbols = new ArrayList<>(SymbolTable.get(arrayType, VariableInfo.class));
        ArrayList<IRNode> arrayCandidates = new ArrayList<>();
        ArrayList<VariableInfo> arrayCandidateInfos = new ArrayList<>();
        for (Symbol symbol : symbols) {
            if (!(symbol instanceof VariableInfo varInfo)) {
                continue;
            }
            if ((varInfo.flags & VariableInfo.INITIALIZED) == 0) {
                continue;
            }
            if (varInfo.getArrayLength().isEmpty()) {
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
        // Array candidates here carry known generation-time lengths. Kernel loops keep iterator in-range.
        indexes.add(new LocalVariable(iterationInfo));
        return new ArrayElement(baseArray, indexes);
    }
}
