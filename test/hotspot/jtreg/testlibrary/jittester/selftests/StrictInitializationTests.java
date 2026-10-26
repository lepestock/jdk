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

import jdk.test.lib.jittester.GenerationState;
import jdk.test.lib.jittester.LocalVariable;
import jdk.test.lib.jittester.NonStaticMemberVariable;
import jdk.test.lib.jittester.ProductionParams;
import jdk.test.lib.jittester.Symbol;
import jdk.test.lib.jittester.SymbolTable;
import jdk.test.lib.jittester.TypeList;
import jdk.test.lib.jittester.VariableDeclaration;
import jdk.test.lib.jittester.VariableInfo;
import jdk.test.lib.jittester.VariableInitialization;
import jdk.test.lib.jittester.functions.FunctionInfo;
import jdk.test.lib.jittester.types.TypeKlass;
import jdk.test.lib.jittester.utils.PseudoRandom;
import jdk.test.lib.jittester.visitors.JavaCodeVisitor;

public class StrictInitializationTests {
    public static void main(String[] args) throws Exception {
        ProductionParams.initializeFromCmdline(new String[] {
                "--disable-external-symbols", "true", "--disable-interfaces", "true",
                "--disable-inheritance", "true", "--member-functions-limit", "2",
                "--statement-limit", "2", "--operator-limit", "2", "--data-member-limit", "2"});
        GenerationState.initializeFlowParamsFromProductionParams();
        GenerationState.setCurrentMainClassName("Test_StrictInit");
        SymbolTable.removeAll();
        SymbolTable.add(new FunctionInfo(TypeList.OBJECT.getName(), TypeList.OBJECT, TypeList.OBJECT,
                0, Symbol.PUBLIC));
        SymbolTable.add(new FunctionInfo(TypeList.STRING.getName(), TypeList.STRING, TypeList.STRING,
                0, Symbol.PUBLIC));
        testInheritedStrictFields();
        testAnnotationsAndReceiver();
        System.out.println("StrictInitializationTests passed");
    }

    private static void testInheritedStrictFields() throws Exception {
        GenerationState.Checkpoint checkpoint = GenerationState.checkpoint();
        try {
            PseudoRandom.setCurrentSeed(300);
            TypeKlass payload = new TypeKlass("Test_StrictInit_Payload");
            payload.setValueKlass();
            TypeKlass owner = new TypeKlass("Test_StrictInit_Holder");
            SymbolTable.add(new FunctionInfo(payload.getName(), payload, payload, 0, Symbol.PUBLIC));
            SymbolTable.add(new VariableInfo("own", owner, payload,
                    VariableInfo.INITIALIZED | VariableInfo.NULL_RESTRICTED));
            SymbolTable.add(new VariableInfo("inherited", owner, payload,
                    VariableInfo.INITIALIZED | VariableInfo.NULL_RESTRICTED | VariableInfo.INHERITED));
            String source = new IRNodeBuilder().setOwnerKlass(owner).setLevel(1).setMemberFunctionsArgLimit(0)
                    .withOperatorLimit(2).withStatementLimit(1).getConstructorDefinitionFactory()
                    .produce().accept(new JavaCodeVisitor());
            int superCall = source.indexOf("super();");
            require(superCall >= 0, "strict initialization needs an explicit superclass call");
            String prologue = source.substring(0, superCall);
            require(prologue.contains("this.own = ") && !prologue.contains("this.inherited = "),
                    "a constructor must initialize only its own strict fields before super");
            require(!ThisVariableControl.isThisForbidden(), "constructor guard must not leak");
        } finally {
            GenerationState.rollbackTo(checkpoint);
        }
    }

    private static void testAnnotationsAndReceiver() {
        TypeKlass payload = new TypeKlass("Test_StrictInit_AnnotationPayload");
        payload.setValueKlass();
        TypeKlass owner = new TypeKlass("Test_StrictInit_AnnotationHolder");
        VariableInfo field = new VariableInfo("field", owner, payload,
                VariableInfo.INITIALIZED | VariableInfo.NULL_RESTRICTED);
        VariableInfo thizz = new VariableInfo("this", owner, owner,
                VariableInfo.LOCAL | VariableInfo.FINAL | VariableInfo.INITIALIZED);
        VariableInfo value = new VariableInfo("value", owner, payload,
                VariableInfo.LOCAL | VariableInfo.INITIALIZED);
        JavaCodeVisitor visitor = new JavaCodeVisitor();
        String prefix = (Boolean.getBoolean("jittester.strict_initialization_markers")
                ? "@jdk.test.lib.helpers.StrictInit " : "")
                + "@jdk.internal.vm.annotation.NullRestricted ";
        require(new VariableDeclaration(field).accept(visitor).startsWith(prefix),
                "null-restricted declarations need the requested annotations");
        require(new VariableInitialization(field, new LocalVariable(value)).accept(visitor).startsWith(prefix),
                "null-restricted initializations need the requested annotations");
        require(new NonStaticMemberVariable(new LocalVariable(thizz), field).accept(visitor).equals("this.field"),
                "early field writes must retain the explicit this receiver");
        VariableInfo ordinary = new VariableInfo("ordinary", owner, payload, VariableInfo.INITIALIZED);
        require(!new VariableDeclaration(ordinary).accept(visitor).contains("@"),
                "ordinary fields must not acquire null-restricted annotations");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
