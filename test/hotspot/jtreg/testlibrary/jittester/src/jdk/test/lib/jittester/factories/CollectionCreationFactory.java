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
import jdk.test.lib.jittester.Literal;
import jdk.test.lib.jittester.ProductionFailedException;
import jdk.test.lib.jittester.ProductionParams;
import jdk.test.lib.jittester.Type;
import jdk.test.lib.jittester.TypeList;
import jdk.test.lib.jittester.VariableDeclaration;
import jdk.test.lib.jittester.collections.CollectionCreation;
import jdk.test.lib.jittester.collections.IndexedStorageKind;
import jdk.test.lib.jittester.types.TypeArray;
import jdk.test.lib.jittester.types.TypeKlass;
import jdk.test.lib.jittester.utils.PseudoRandom;

class CollectionCreationFactory extends SafeFactory<CollectionCreation> {
    private final Type resultType;
    private final boolean exceptionSafe;
    private final boolean noconsts;
    private final TypeKlass ownerClass;

    CollectionCreationFactory(int operatorLimit,
            TypeKlass ownerClass, Type resultType, boolean exceptionSafe, boolean noconsts) {
        this.ownerClass = ownerClass;
        this.resultType = resultType;
        this.exceptionSafe = exceptionSafe;
        this.noconsts = noconsts;
    }

    @Override
    protected CollectionCreation sproduce() throws ProductionFailedException {
        if (resultType instanceof TypeArray) {
            TypeArray arrayResultType = (TypeArray) resultType;
            if (arrayResultType.type.equals(TypeList.VOID)) {
                arrayResultType = arrayResultType.produce();
            } else if (!TypeArray.isElementTypeAllowed(arrayResultType.type)) {
                throw new ProductionFailedException();
            }
            arrayResultType = withSelectedStorageKind(arrayResultType);
            IndexedStorageKind storageKind = arrayResultType.getStorageKind();
            IRNodeBuilder builder = new IRNodeBuilder()
                    .setOwnerKlass(ownerClass)
                    .setResultType(TypeList.BYTE)
                    .setExceptionSafe(exceptionSafe)
                    .setNoConsts(noconsts);
            ArrayList<IRNode> dims = new ArrayList<>(arrayResultType.dimensions);
            VariableDeclaration var = builder
                    .setOwnerKlass(ownerClass)
                    .setResultType(arrayResultType)
                    .setIsLocal(true)
                    .setIsStatic(false)
                    .getVariableDeclarationFactory()
                    .produce();
            int variableSize = var.getVariableInfo().getArrayLength()
                    .orElseThrow(ProductionFailedException::new);
            for (int i = 0; i < arrayResultType.dimensions; i++) {
                dims.add(new Literal(variableSize, TypeList.INT));
            }
            return new CollectionCreation(var, arrayResultType, dims, storageKind);
        }
        throw new ProductionFailedException();
    }

    static TypeArray withSelectedStorageKind(TypeArray arrayType) {
        IndexedStorageKind storageKind = selectStorageKind(arrayType);
        return storageKind == arrayType.getStorageKind()
                ? arrayType
                : new TypeArray(arrayType.type, arrayType.dimensions, storageKind);
    }

    static IndexedStorageKind selectStorageKind(TypeArray arrayType) {
        if (arrayType.getStorageKind() != IndexedStorageKind.ARRAY) {
            return arrayType.getStorageKind();
        }
        if (arrayType.dimensions != 1) {
            return IndexedStorageKind.ARRAY;
        }
        int percent = Math.max(0, Math.min(100, ProductionParams.listStoragePercent.value()));
        return PseudoRandom.randomBoolean(percent / 100.0)
                ? IndexedStorageKind.LIST
                : IndexedStorageKind.ARRAY;
    }
}
