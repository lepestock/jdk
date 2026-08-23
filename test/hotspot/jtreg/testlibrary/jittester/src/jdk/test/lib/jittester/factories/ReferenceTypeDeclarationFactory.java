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
 * You should have received a copy of the GNU General Public License
 * version 2 along with this work; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk.test.lib.jittester.factories;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import jdk.test.lib.jittester.Declaration;
import jdk.test.lib.jittester.ProductionFailedException;
import jdk.test.lib.jittester.Type;
import jdk.test.lib.jittester.TypeList;
import jdk.test.lib.jittester.types.TypeKlass;
import jdk.test.lib.jittester.utils.PseudoRandom;

class ReferenceTypeDeclarationFactory extends Factory<Declaration> {
    private final TypeKlass ownerClass;
    private final long complexityLimit;
    private final int operatorLimit;
    private final boolean isLocal;
    private final boolean exceptionSafe;
    private final boolean isConstant;
    private final Predicate<TypeKlass> typeFilter;

    ReferenceTypeDeclarationFactory(TypeKlass ownerClass, long complexityLimit,
            int operatorLimit, boolean isLocal, boolean exceptionSafe, boolean isConstant,
            Predicate<TypeKlass> typeFilter) {
        this.ownerClass = ownerClass;
        this.complexityLimit = complexityLimit;
        this.operatorLimit = operatorLimit;
        this.isLocal = isLocal;
        this.exceptionSafe = exceptionSafe;
        this.isConstant = isConstant;
        this.typeFilter = typeFilter;
    }

    @Override
    public Declaration produce() throws ProductionFailedException {
        List<Type> candidates = declarationTypeCandidates();
        if (candidates.isEmpty()) {
            throw new ProductionFailedException();
        }

        List<Integer> candidateIndexes = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            candidateIndexes.add(i);
        }
        PseudoRandom.shuffle(candidateIndexes);
        moveStringFallbackLast(candidateIndexes, candidates);
        for (int candidateIndex : candidateIndexes) {
            try {
                return produceDeclaration(candidates.get(candidateIndex));
            } catch (ProductionFailedException e) {
                // Try the next reference type candidate.
            }
        }
        throw new ProductionFailedException();
    }

    private Declaration produceDeclaration(Type type) throws ProductionFailedException {
        return new DeclarationFactory(ownerClass, complexityLimit, operatorLimit, isLocal,
                exceptionSafe, isConstant, type, true).produce();
    }

    private List<Type> declarationTypeCandidates() {
        ArrayList<Type> candidates = new ArrayList<>();
        for (Type type : TypeList.getAll()) {
            if (isReferenceDeclarationTypeCandidate(type)) {
                candidates.add(type);
            }
        }
        if (!candidates.contains(TypeList.STRING) && typeFilter.test(TypeList.STRING)) {
            candidates.add(TypeList.STRING);
        }
        return candidates;
    }

    private boolean isReferenceDeclarationTypeCandidate(Type type) {
        if (!(type instanceof TypeKlass typeKlass)) {
            return false;
        }
        return !typeKlass.isValueKlass()
                && !typeKlass.isInterface()
                && !typeKlass.isAbstract()
                && typeFilter.test(typeKlass);
    }

    private static void moveStringFallbackLast(List<Integer> candidateIndexes, List<Type> candidates) {
        int stringIndex = candidates.indexOf(TypeList.STRING);
        if (stringIndex < 0) {
            return;
        }
        candidateIndexes.remove(Integer.valueOf(stringIndex));
        candidateIndexes.add(stringIndex);
    }
}
