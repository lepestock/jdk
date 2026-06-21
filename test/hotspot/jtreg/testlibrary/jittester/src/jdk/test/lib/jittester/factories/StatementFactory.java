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
import java.util.List;
import jdk.test.lib.jittester.GenerationState;
import jdk.test.lib.jittester.IRNode;
import jdk.test.lib.jittester.ProductionFailedException;
import jdk.test.lib.jittester.ProductionParams;
import jdk.test.lib.jittester.Rule;
import jdk.test.lib.jittester.Statement;
import jdk.test.lib.jittester.Symbol;
import jdk.test.lib.jittester.SymbolTable;
import jdk.test.lib.jittester.Type;
import jdk.test.lib.jittester.TypeList;
import jdk.test.lib.jittester.VariableInfo;
import jdk.test.lib.jittester.types.TypeArray;
import jdk.test.lib.jittester.types.TypeKlass;
import jdk.test.lib.jittester.utils.PseudoRandom;

class StatementFactory extends Factory<Statement> {
    private static final double NUMERIC_RESULT_TYPE_PREFERENCE = 0.90;
    private final Rule<IRNode> rule;
    private final boolean needSemicolon;

    StatementFactory(long complexityLimit, int operatorLimit,
            TypeKlass ownerClass, boolean exceptionSafe,
            boolean noconsts, boolean needSemicolon ){
        this.needSemicolon = needSemicolon;
        rule = new Rule<>("statement");
        IRNodeBuilder builder = new IRNodeBuilder()
                .withComplexityLimit(complexityLimit)
                .withOperatorLimit(operatorLimit)
                .setOwnerKlass(ownerClass)
                .setExceptionSafe(exceptionSafe)
                .setNoConsts(noconsts)
                .setResultType(pickStatementResultType());
        double arrayWeight = 1.0
                + Math.max(0, ProductionParams.arrayProductionWeightBonus.value()) / 100.0;
        rule.add("array_creation", builder.getCollectionCreationFactory(), arrayWeight);
        rule.add("assignment", builder.getAssignmentOperatorFactory());
//        rule.add("function", builder.getFunctionFactory(), 0.1);
    }

    private static Type pickStatementResultType() {
        if (GenerationState.currentFlowParams().inArrayKernel()) {
            Type arrayElementType = pickKernelCollectionElementType();
            if (arrayElementType != null) {
                return arrayElementType;
            }
        }
        if (PseudoRandom.randomBoolean(NUMERIC_RESULT_TYPE_PREFERENCE)) {
            List<Type> numericPreferred = new ArrayList<>();
            numericPreferred.add(TypeList.INT);
            numericPreferred.add(TypeList.LONG);
            numericPreferred.add(TypeList.FLOAT);
            numericPreferred.add(TypeList.DOUBLE);
            return PseudoRandom.randomElement(numericPreferred);
        }
        return PseudoRandom.randomElement(TypeList.getAll());
    }

    private static Type pickKernelCollectionElementType() {
        ArrayList<Type> candidates = new ArrayList<>();
        for (Symbol symbol : SymbolTable.getAllCombined(VariableInfo.class)) {
            if (!(symbol instanceof VariableInfo varInfo)) {
                continue;
            }
            if (!(varInfo.type instanceof TypeArray arrayType)) {
                continue;
            }
            if (arrayType.dimensions != 1) {
                continue;
            }
            if ((varInfo.flags & VariableInfo.INITIALIZED) == 0) {
                continue;
            }
            if (varInfo.getArrayLength().isEmpty()) {
                continue;
            }
            if (!TypeArray.isElementTypeAllowed(arrayType.type)) {
                continue;
            }
            if (!candidates.contains(arrayType.type)) {
                candidates.add(arrayType.type);
            }
        }
        return candidates.isEmpty() ? null : PseudoRandom.randomElement(candidates);
    }

    @Override
    public Statement produce() throws ProductionFailedException {
        return new Statement(rule.produce(), needSemicolon);
    }
}
