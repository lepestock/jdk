/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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
import jdk.test.lib.jittester.Block;
import jdk.test.lib.jittester.IRNode;
import jdk.test.lib.jittester.OperatorKind;
import jdk.test.lib.jittester.ProductionFailedException;
import jdk.test.lib.jittester.Statement;
import jdk.test.lib.jittester.StaticMemberVariable;
import jdk.test.lib.jittester.Symbol;
import jdk.test.lib.jittester.SymbolTable;
import jdk.test.lib.jittester.TypeList;
import jdk.test.lib.jittester.VariableInfo;
import jdk.test.lib.jittester.collections.CollectionInitializer;
import jdk.test.lib.jittester.functions.StaticConstructorDefinition;
import jdk.test.lib.jittester.types.TypeArray;
import jdk.test.lib.jittester.types.TypeKlass;
import jdk.test.lib.jittester.utils.PseudoRandom;

class StaticConstructorDefinitionFactory extends Factory<StaticConstructorDefinition> {
    private final long complexityLimit;
    private final int statementLimit;
    private final int operatorLimit;
    private final int level;
    private final TypeKlass ownerClass;

    StaticConstructorDefinitionFactory(TypeKlass ownerClass, long complexityLimit,
            int statementLimit, int operatorLimit, int level) {
        this.ownerClass = ownerClass;
        this.complexityLimit = complexityLimit;
        this.statementLimit = statementLimit;
        this.operatorLimit = operatorLimit;
        this.level = level;
    }

    @Override
    public StaticConstructorDefinition produce() throws ProductionFailedException {
        SymbolTable.push();
        IRNode body;
        try {
            SymbolTable.remove(SymbolTable.get("this", VariableInfo.class));
            long complLimit = (long) (PseudoRandom.random() * complexityLimit);
            ThisVariableControl.pushForbidThis();
            try {
                body = new IRNodeBuilder()
                        .setOwnerKlass(ownerClass)
                        .setResultType(TypeList.VOID)
                        .withComplexityLimit(complLimit)
                        .withStatementLimit(statementLimit)
                        .withOperatorLimit(operatorLimit)
                        .setLevel(level)
                        .setSubBlock(true)
                        .setCanHaveBreaks(false)
                        .setCanHaveContinues(false)
                        .setCanHaveReturn(false)
                        .getBlockFactory()
                        .produce();
            } finally {
                ThisVariableControl.popForbidThis();
            }
        } finally {
            SymbolTable.pop();
        }
        return new StaticConstructorDefinition(prependStaticCollectionInitializers(body));
    }

    private IRNode prependStaticCollectionInitializers(IRNode originalBody) {
        if (!(originalBody instanceof Block blockBody)) {
            return originalBody;
        }
        List<IRNode> prelude = buildStaticCollectionInitializers();
        if (prelude.isEmpty()) {
            return originalBody;
        }
        List<IRNode> merged = new ArrayList<>(prelude.size() + blockBody.getChildren().size());
        merged.addAll(prelude);
        merged.addAll(blockBody.getChildren());
        return new Block(ownerClass, TypeList.VOID, merged, blockBody.getLevel(), blockBody.getBlockGene());
    }

    private List<IRNode> buildStaticCollectionInitializers() {
        List<IRNode> statements = new ArrayList<>();
        for (Symbol symbol : SymbolTable.getAllCombined(ownerClass, VariableInfo.class)) {
            VariableInfo variableInfo = (VariableInfo) symbol;
            if (!needsStaticCollectionInitializer(variableInfo)) {
                continue;
            }
            try {
                statements.add(createStaticCollectionInitializerStatement(variableInfo));
            } catch (ProductionFailedException ignored) {
                // Keep generation resilient: if initializer creation fails, keep previous behavior.
            }
        }
        return statements;
    }

    private static boolean needsStaticCollectionInitializer(VariableInfo variableInfo) {
        return variableInfo.isStatic()
                && variableInfo.type instanceof TypeArray
                && (variableInfo.flags & VariableInfo.INITIALIZED) == 0;
    }

    private Statement createStaticCollectionInitializerStatement(VariableInfo variableInfo)
            throws ProductionFailedException {
        TypeArray arrayType = (TypeArray) variableInfo.type;
        IRNodeBuilder initBuilder = new IRNodeBuilder()
                .setOwnerKlass(ownerClass)
                .withComplexityLimit(Math.max(1L, complexityLimit / 8))
                .withOperatorLimit(Math.max(1, operatorLimit / 8))
                .setResultType(arrayType)
                .setExceptionSafe(true)
                .setNoConsts(false);
        CollectionInitializer initializer = initBuilder.getCollectionInitializerFactory().produce();
        variableInfo.flags |= VariableInfo.INITIALIZED;
        StaticMemberVariable target = new StaticMemberVariable(ownerClass, variableInfo);
        return new Statement(new BinaryOperator(OperatorKind.ASSIGN, arrayType, target, initializer), true);
    }
}
