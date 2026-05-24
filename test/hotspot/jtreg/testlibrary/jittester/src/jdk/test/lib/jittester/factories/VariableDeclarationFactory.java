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

import jdk.test.lib.jittester.ProductionFailedException;
import jdk.test.lib.jittester.ProductionParams;
import jdk.test.lib.jittester.SymbolTable;
import jdk.test.lib.jittester.Type;
import jdk.test.lib.jittester.TypeList;
import jdk.test.lib.jittester.VariableDeclaration;
import jdk.test.lib.jittester.VariableInfo;
import jdk.test.lib.jittester.types.TypeKlass;
import jdk.test.lib.jittester.types.TypeArray;
import jdk.test.lib.jittester.utils.PseudoRandom;

class VariableDeclarationFactory extends Factory<VariableDeclaration> {
    private static final double CLASS_FIELD_ARRAY_TYPE_BASE_PROBABILITY = 0.10;
    private final boolean isStatic;
    private final boolean isLocal;
    private final TypeKlass ownerClass;
    private Type resultType;

    VariableDeclarationFactory(TypeKlass ownerClass, boolean isStatic, boolean isLocal, Type resultType) {
        this.ownerClass = ownerClass;
        this.isStatic = isStatic;
        this.isLocal = isLocal;
        this.resultType = resultType;
    }

    @Override
    public VariableDeclaration produce() throws ProductionFailedException {
        if (resultType.equals(TypeList.VOID)) {
            ArrayList<Type> types = new ArrayList<>(TypeList.getAll());
            if (types.isEmpty()) {
                throw new ProductionFailedException();
            }
            resultType = TypeSelectionUtil.pickPreferredOrAnyType(ownerClass, types);
            resultType = maybeWrapClassFieldTypeAsArray(resultType);
        }
        String resultName = "var_" + SymbolTable.getNextVariableNumber();
        int flags = VariableInfo.NONE;
        if (isStatic) {
            flags |= VariableInfo.STATIC;
        }
        if (isLocal) {
            flags |= VariableInfo.LOCAL;
        }
        VariableInfo varInfo = new VariableInfo(resultName, ownerClass, resultType, flags);
        SymbolTable.add(varInfo);
        return new VariableDeclaration(varInfo);
    }

    private Type maybeWrapClassFieldTypeAsArray(Type selectedType) {
        if (isLocal || ProductionParams.disableArrays.value()) {
            return selectedType;
        }
        if (selectedType instanceof TypeArray || selectedType.equals(TypeList.VOID)) {
            return selectedType;
        }
        double bonusScale = 1.0
                + Math.max(0, ProductionParams.arrayFieldDefinitionWeightBonus.value()) / 100.0;
        double probability = Math.max(0.0, Math.min(1.0,
                CLASS_FIELD_ARRAY_TYPE_BASE_PROBABILITY * bonusScale));
        if (!PseudoRandom.randomBoolean(probability)) {
            return selectedType;
        }
        // Keep field arrays 1D for now: this path is tuned for stable initialization/use,
        // and multi-dimensional declarations here tend to produce brittle behavior.
        return new TypeArray(selectedType, 1);
    }
}
