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
import jdk.test.lib.jittester.BinaryOperator;
import jdk.test.lib.jittester.GenerationState;
import jdk.test.lib.jittester.IRNode;
import jdk.test.lib.jittester.Literal;
import jdk.test.lib.jittester.OperatorKind;
import jdk.test.lib.jittester.ProductionFailedException;
import jdk.test.lib.jittester.ProductionParams;
import jdk.test.lib.jittester.Type;
import jdk.test.lib.jittester.TypeList;
import jdk.test.lib.jittester.collections.CollectionCreation;
import jdk.test.lib.jittester.collections.CollectionElement;
import jdk.test.lib.jittester.collections.CollectionExtraction;
import jdk.test.lib.jittester.collections.IndexedStorageKind;
import jdk.test.lib.jittester.types.TypeArray;
import jdk.test.lib.jittester.types.TypeKlass;
import jdk.test.lib.jittester.utils.Genome;
import jdk.test.lib.jittester.utils.PseudoRandom;

class CollectionElementFactory extends SafeFactory<CollectionElement> {
    private final long complexityLimit;
    private final int operatorLimit;
    private final Type resultType;
    private final TypeKlass ownerClass;
    private final boolean exceptionSafe;
    private final boolean noconsts;

    CollectionElementFactory(long complexityLimit, int operatorLimit, TypeKlass ownerClass,
            Type resultType, boolean exceptionSafe, boolean noconsts) {
        this.complexityLimit = complexityLimit;
        this.operatorLimit = operatorLimit;
        this.ownerClass = ownerClass;
        this.resultType = resultType;
        this.exceptionSafe = exceptionSafe;
        this.noconsts = noconsts;
    }

    @Override
    protected CollectionElement sproduce() throws ProductionFailedException {
        if (resultType instanceof TypeArray) {
            throw new ProductionFailedException();
        }
        if (!TypeArray.isElementTypeAllowed(resultType)) {
            throw new ProductionFailedException();
        }
        long arrayComplexityLimit = (long) (complexityLimit * 0.5 * PseudoRandom.random());
        int arrayOperatorLimit = (int) (operatorLimit * 0.5 * PseudoRandom.random());
        int dimensionsCount = PseudoRandom.randomNotZero(ProductionParams.dimensionsLimit.value());
        long complexityPerDimension = (long) ((complexityLimit - arrayComplexityLimit)
                * PseudoRandom.random()) / dimensionsCount;
        int operatorLimitPerDimension = (int) ((operatorLimit - arrayOperatorLimit - dimensionsCount)
                * PseudoRandom.random()) / dimensionsCount;
        IRNodeBuilder builder = new IRNodeBuilder().setOwnerKlass(ownerClass)
                .setExceptionSafe(exceptionSafe)
                .setNoConsts(noconsts);
        IRNode arrayReturningExpression = builder
                .withComplexityLimit(arrayComplexityLimit)
                .withOperatorLimit(arrayOperatorLimit)
                .setResultType(new TypeArray(resultType, dimensionsCount))
                .getExpressionFactory()
                .produce();
        Factory<IRNode> expressionFactory = builder
                .withComplexityLimit(complexityPerDimension)
                .withOperatorLimit(operatorLimitPerDimension)
                .setResultType(TypeList.BYTE)
                .getExpressionFactory();
        double chanceExpression = ProductionParams.chanceExpressionIndex.value() / 100.;
        ArrayList<IRNode> perDimensionExpressions = new ArrayList<>(dimensionsCount);
        int preferredSize = GenerationState.preferredIntCollectionSize();
        Literal shiftByOne = new Literal(1, TypeList.INT);
        Literal preferredSizeLiteral = new Literal(preferredSize, TypeList.INT);
        for (int i = 0; i < dimensionsCount; i++) {
            if (PseudoRandom.randomBoolean(chanceExpression)) {
                IRNode rawIndex = expressionFactory.produce();
                IRNode nonNegative = new BinaryOperator(OperatorKind.SAR, TypeList.INT, rawIndex, shiftByOne);
                IRNode bounded = new BinaryOperator(OperatorKind.MOD, TypeList.INT, nonNegative, preferredSizeLiteral);
                perDimensionExpressions.add(bounded);
            } else {
                byte dimLimit = 0;
                if (arrayReturningExpression instanceof CollectionCreation) {
                    CollectionCreation arrayCreation = (CollectionCreation) arrayReturningExpression;
                    dimLimit = arrayCreation.getDimensionSize(i);
                } else if (arrayReturningExpression instanceof CollectionExtraction) {
                    CollectionExtraction arrayExtraction = (CollectionExtraction) arrayReturningExpression;
                    if (i < arrayExtraction.getDimsNumber())
                        dimLimit = arrayExtraction.getDim(i);
                }
                int boundedLimit = dimLimit > 0 ? dimLimit : preferredSize;
                perDimensionExpressions.add(new Literal((byte)PseudoRandom.randomNotNegative(boundedLimit), TypeList.BYTE));
            }
        }
        CollectionElement produced = new CollectionElement(arrayReturningExpression, perDimensionExpressions,
                storageKind(arrayReturningExpression));
        Long expressionScopeSeed = Genome.getCurrentExpressionScopeSeed();
        if (expressionScopeSeed != null) {
            produced.setExpressionGeneSeed(expressionScopeSeed);
        }
        return produced;
    }

    private static IndexedStorageKind storageKind(IRNode node) {
        return node.getResultType() instanceof TypeArray arrayType
                ? arrayType.getStorageKind()
                : IndexedStorageKind.ARRAY;
    }
}
