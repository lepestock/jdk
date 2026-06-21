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
import jdk.test.lib.jittester.ProductionFailedException;
import jdk.test.lib.jittester.Type;
import jdk.test.lib.jittester.collections.CollectionInitializer;
import jdk.test.lib.jittester.types.TypeArray;
import jdk.test.lib.jittester.types.TypeKlass;

class CollectionInitializerFactory extends SafeFactory<CollectionInitializer> {
    private final long complexityLimit;
    private final int operatorLimit;
    private final TypeKlass ownerClass;
    private final Type resultType;
    private final boolean exceptionSafe;
    private final boolean noConsts;

    CollectionInitializerFactory(long complexityLimit, int operatorLimit, TypeKlass ownerClass,
            Type resultType, boolean exceptionSafe, boolean noConsts) {
        this.complexityLimit = complexityLimit;
        this.operatorLimit = operatorLimit;
        this.ownerClass = ownerClass;
        this.resultType = resultType;
        this.exceptionSafe = exceptionSafe;
        this.noConsts = noConsts;
    }

    @Override
    protected CollectionInitializer sproduce() throws ProductionFailedException {
        if (!(resultType instanceof TypeArray arrayType) || arrayType.dimensions != 1) {
            throw new ProductionFailedException();
        }
        if (!TypeArray.isElementTypeAllowed(arrayType.type)) {
            throw new ProductionFailedException();
        }
        Type elementType = arrayType.type;
        int elementCount = chooseElementCount(elementType);
        long perElemComplexity = Math.max(1L, complexityLimit / Math.max(1, elementCount));
        int perElemOps = Math.max(1, operatorLimit / Math.max(1, elementCount));
        IRNodeBuilder elementBuilder = new IRNodeBuilder()
                .setOwnerKlass(ownerClass)
                .withComplexityLimit(perElemComplexity)
                .withOperatorLimit(perElemOps)
                .setResultType(elementType)
                .setExceptionSafe(exceptionSafe)
                .setNoConsts(noConsts);
        List<IRNode> elements = new ArrayList<>(elementCount);
        for (int i = 0; i < elementCount; i++) {
            elements.add(produceElement(elementBuilder));
        }
        return new CollectionInitializer(arrayType, elements, arrayType.getStorageKind());
    }

    private int chooseElementCount(Type elementType) {
        // FIXME: temporary simplification for collection-alignment experiments:
        // treat all arrays as int-typed for sizing and use one generation-wide fixed int count.
        return GenerationState.preferredIntCollectionSize();
    }

    private IRNode produceElement(IRNodeBuilder elementBuilder) throws ProductionFailedException {
        try {
            return elementBuilder.getLiteralFactory().produce();
        } catch (ProductionFailedException ignored) {
            return elementBuilder.getLimitedExpressionFactory().produce();
        }
    }
}
