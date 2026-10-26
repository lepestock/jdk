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

import java.util.regex.Pattern;
import jdk.test.lib.jittester.GenerationState;
import jdk.test.lib.jittester.ProductionParams;
import jdk.test.lib.jittester.Symbol;
import jdk.test.lib.jittester.SymbolTable;
import jdk.test.lib.jittester.VariableInfo;
import jdk.test.lib.jittester.functions.FunctionInfo;
import jdk.test.lib.jittester.morph.InlineTypeFlatArrayMorphTemplate;
import jdk.test.lib.jittester.morph.MorphLegTarget;
import jdk.test.lib.jittester.morph.MorphTemplate;
import jdk.test.lib.jittester.types.TypeKlass;
import jdk.test.lib.jittester.utils.PseudoRandom;
import jdk.test.lib.jittester.visitors.JavaCodeVisitor;

public class FlatArrayMorphTests {
    private static final Pattern IMPLICIT_RECEIVER = Pattern.compile("(?<![\\w.])(?:this|payload)\\b");

    public static void main(String[] args) throws Exception {
        ProductionParams.initializeFromCmdline(new String[] {
                "--disable-external-symbols", "true", "--operator-limit", "2"});
        GenerationState.initializeFlowParamsFromProductionParams();
        GenerationState.setCurrentMainClassName("Test_FlatArray");
        SymbolTable.removeAll();
        testStaticArrayFieldLeg(0);
        testStaticArrayFieldLeg(1);
        System.out.println("FlatArrayMorphTests passed");
    }

    private static void testStaticArrayFieldLeg(int kind) throws Exception {
        GenerationState.Checkpoint checkpoint = GenerationState.checkpoint();
        try {
            TypeKlass payload = new TypeKlass("Test_FlatArray_Payload");
            payload.setValueKlass();
            TypeKlass owner = new TypeKlass("Test_FlatArray_Holder");
            SymbolTable.add(new FunctionInfo(payload.getName(), payload, payload, 0, Symbol.PUBLIC));
            SymbolTable.add(new VariableInfo("this", owner, owner,
                    VariableInfo.LOCAL | VariableInfo.FINAL | VariableInfo.INITIALIZED));
            SymbolTable.add(new VariableInfo("payload", owner, payload, VariableInfo.INITIALIZED));
            for (int seed = 200; seed < 220; seed++) {
                PseudoRandom.setCurrentSeed(seed);
                MorphTemplate template = new InlineTypeFlatArrayMorphTemplate(seed, 0, kind,
                        payload, null, null, 0, 0, false);
                String source = template.produceLeg(MorphLegTarget.FIELD_DECLARATION, 0,
                        new IRNodeBuilder().setOwnerKlass(owner).setLevel(1))
                        .node().accept(new JavaCodeVisitor());
                if (IMPLICIT_RECEIVER.matcher(source).find()) {
                    throw new AssertionError("Static MTFA initializer borrows implicit this, kind="
                            + kind + ", seed=" + seed + ":\n" + source);
                }
                if (ThisVariableControl.isThisForbidden()) {
                    throw new AssertionError("Static initializer guard leaked into the surrounding scope");
                }
            }
        } finally {
            GenerationState.rollbackTo(checkpoint);
        }
    }
}
