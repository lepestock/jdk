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
import java.util.LinkedList;
import java.util.List;
import jdk.test.lib.jittester.IRNode;
import jdk.test.lib.jittester.ProductionFailedException;
import jdk.test.lib.jittester.ProductionParams;
import jdk.test.lib.jittester.Rule;
import jdk.test.lib.jittester.Symbol;
import jdk.test.lib.jittester.SymbolTable;
import jdk.test.lib.jittester.Type;
import jdk.test.lib.jittester.BuiltInType;
import jdk.test.lib.jittester.TypeList;
import jdk.test.lib.jittester.VariableInfo;
import jdk.test.lib.jittester.VariableInitialization;
import jdk.test.lib.jittester.types.TypeArray;
import jdk.test.lib.jittester.types.TypeKlass;
import jdk.test.lib.jittester.utils.DepthProbabilityTaper;
import jdk.test.lib.jittester.utils.GenomeChoice;
import jdk.test.lib.jittester.utils.PseudoRandom;

class VariableInitializationFactory extends SafeFactory<VariableInitialization> {
    private static final double NUMERIC_INIT_TYPE_PREFERENCE = 0.85;
    private static final double FIELD_ARRAY_INIT_BASE_PROBABILITY = 0.08;
    private final int operatorLimit;
    private final long complexityLimit;
    private final boolean constant;
    private final boolean isStatic;
    private final boolean isLocal;
    private final boolean exceptionSafe;
    private final TypeKlass ownerClass;

    VariableInitializationFactory(TypeKlass ownerClass, boolean constant, boolean isStatic,
            boolean isLocal, long complexityLimit, int operatorLimit, boolean exceptionSafe) {
        this.ownerClass = ownerClass;
        this.constant = constant;
        this.isStatic = isStatic;
        this.isLocal = isLocal;
        this.complexityLimit = complexityLimit;
        this.operatorLimit = operatorLimit;
        this.exceptionSafe = exceptionSafe;
    }

    @Override
    protected VariableInitialization sproduce() throws ProductionFailedException {
        Type resultType = pickInitializationType();
        int effectiveOperatorLimit = Math.max(1, operatorLimit);
        long effectiveComplexityLimit = Math.max(1, complexityLimit);
        int scopeDepth = Math.max(1, SymbolTable.getScopeDepth());
        boolean noConstsForInitExpr = shouldDisallowConstsByDepth(scopeDepth);
        IRNodeBuilder b = new IRNodeBuilder().withComplexityLimit(effectiveComplexityLimit)
                .withOperatorLimit(effectiveOperatorLimit)
                .setOwnerKlass(ownerClass)
                .setResultType(resultType)
                .setExceptionSafe(exceptionSafe)
                .setNoConsts(false);
        Symbol thisSymbol = null;
        boolean forbidThisScope = !isLocal;
        if (isStatic) {
            thisSymbol = SymbolTable.get("this", VariableInfo.class);
            SymbolTable.remove(thisSymbol);
        }
        IRNode init;
        try {
            if (forbidThisScope) {
                ThisVariableControl.pushForbidThis();
            }
            try {
                if (!ProductionParams.disableExprInInit.value()) {
                    // Prefer non-literal initializer expressions; fall back to literal when expression fails.
                    try {
                        if (resultType instanceof TypeArray) {
                            init = new IRNodeBuilder().withComplexityLimit(effectiveComplexityLimit)
                                    .withOperatorLimit(effectiveOperatorLimit)
                                    .setOwnerKlass(ownerClass)
                                    .setResultType(resultType)
                                    .setExceptionSafe(exceptionSafe)
                                    .setNoConsts(noConstsForInitExpr)
                                    .getCollectionInitializerFactory()
                                    .produce();
                        } else {
                        IRNodeBuilder exprBuilder = new IRNodeBuilder().withComplexityLimit(effectiveComplexityLimit)
                                .withOperatorLimit(effectiveOperatorLimit)
                                .setOwnerKlass(ownerClass)
                                .setResultType(resultType)
                                .setExceptionSafe(exceptionSafe)
                                .setNoConsts(noConstsForInitExpr);
                        if (isArithmeticFriendly(resultType)) {
                            init = exprBuilder.getArithmeticOperatorFactory().produce();
                        } else {
                            init = exprBuilder.getLimitedExpressionFactory().produce();
                        }
                        }
                    } catch (ProductionFailedException ignored) {
                        try {
                            init = new IRNodeBuilder().withComplexityLimit(effectiveComplexityLimit)
                                    .withOperatorLimit(effectiveOperatorLimit)
                                    .setOwnerKlass(ownerClass)
                                    .setResultType(resultType)
                                    .setExceptionSafe(exceptionSafe)
                                    .setNoConsts(noConstsForInitExpr)
                                    .getLimitedExpressionFactory()
                                    .produce();
                        } catch (ProductionFailedException ignoredAgain) {
                            init = b.getLiteralFactory().produce();
                        } catch (RuntimeException e) {
                            throw e;
                        }
                    } catch (RuntimeException e) {
                        throw e;
                    }
                } else {
                    init = b.getLiteralFactory().produce();
                }
            } finally {
                if (forbidThisScope) {
                    ThisVariableControl.popForbidThis();
                }
            }
        } finally {
            if (isStatic) {
                SymbolTable.add(thisSymbol);
            }
        }
        String resultName = "var_" + SymbolTable.getNextVariableNumber();
        int flags = VariableInfo.INITIALIZED;
        if (constant) {
            flags |= VariableInfo.FINAL;
        }
        if (isStatic) {
            flags |= VariableInfo.STATIC;
        }
        if (isLocal) {
            flags |= VariableInfo.LOCAL;
        }
        VariableInfo varInfo = new VariableInfo(resultName, ownerClass, resultType, flags);
        SymbolTable.add(varInfo);
        return new VariableInitialization(varInfo, init);
    }

    private static boolean isArithmeticFriendly(Type resultType) {
        if (!TypeList.isBuiltIn(resultType)) {
            return false;
        }
        BuiltInType bt = (BuiltInType) resultType;
        return bt.equals(TypeList.INT) || bt.equals(TypeList.LONG)
                || bt.equals(TypeList.FLOAT) || bt.equals(TypeList.DOUBLE)
                || bt.equals(TypeList.SHORT) || bt.equals(TypeList.BYTE)
                || bt.equals(TypeList.CHAR);
    }

    private static boolean shouldDisallowConstsByDepth(int depth) {
        double base = Math.max(0.0, Math.min(1.0, ProductionParams.constBiasBasePercent.value() / 100.0));
        int halfDepth = Math.max(1, ProductionParams.constBiasHalfDepth.value());
        double noConstsProbability = DepthProbabilityTaper.decayingAsymptote(depth, base, halfDepth);
        boolean noConstsLive = PseudoRandom.randomSilent() < noConstsProbability;
        return GenomeChoice.bool(noConstsLive);
    }

    private Type pickInitializationType() throws ProductionFailedException {
        if (!isLocal && !ProductionParams.disableArrays.value()
                && PseudoRandom.randomBoolean(fieldArrayInitProbability())) {
            return CollectionCreationFactory.withSelectedStorageKind(new TypeArray(pickCollectionElementType(), 1));
        }
        if (PseudoRandom.randomBoolean(NUMERIC_INIT_TYPE_PREFERENCE)) {
            List<Type> numericPreferred = new ArrayList<>();
            numericPreferred.add(TypeList.INT);
            numericPreferred.add(TypeList.SHORT);
            numericPreferred.add(TypeList.BYTE);
            numericPreferred.add(TypeList.CHAR);
            numericPreferred.add(TypeList.LONG);
            numericPreferred.add(TypeList.FLOAT);
            numericPreferred.add(TypeList.DOUBLE);
            return PseudoRandom.randomElement(numericPreferred);
        }
        LinkedList<Type> types = new LinkedList<>(TypeList.getAll());
        if (types.isEmpty()) {
            throw new ProductionFailedException();
        }
        return TypeSelectionUtil.pickPreferredOrAnyType(ownerClass, types);
    }

    private static double fieldArrayInitProbability() {
        double bonusScale = 1.0
                + Math.max(0, ProductionParams.arrayFieldDefinitionWeightBonus.value()) / 100.0;
        return Math.max(0.0, Math.min(1.0, FIELD_ARRAY_INIT_BASE_PROBABILITY * bonusScale));
    }

    private Type pickCollectionElementType() throws ProductionFailedException {
        LinkedList<Type> types = new LinkedList<>(TypeArray.filterAllowedElementTypes(TypeList.getAll()));
        if (types.isEmpty()) {
            throw new ProductionFailedException();
        }
        return TypeSelectionUtil.pickPreferredOrAnyType(ownerClass, types);
    }
}
