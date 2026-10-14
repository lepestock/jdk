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

import java.lang.reflect.Method;
import jdk.test.lib.jittester.FlowParams;
import jdk.test.lib.jittester.Formatter;
import jdk.test.lib.jittester.GenerationState;
import jdk.test.lib.jittester.IRNode;
import jdk.test.lib.jittester.MethodArgumentConstraint;
import jdk.test.lib.jittester.MethodResultWrapper;
import jdk.test.lib.jittester.ProductionParams;
import jdk.test.lib.jittester.Symbol;
import jdk.test.lib.jittester.SymbolTable;
import jdk.test.lib.jittester.TypeList;
import jdk.test.lib.jittester.Declaration;
import jdk.test.lib.jittester.VariableInfo;
import jdk.test.lib.jittester.classes.ClassDefinitionBlock;
import jdk.test.lib.jittester.classes.Klass;
import jdk.test.lib.jittester.functions.FunctionInfo;
import jdk.test.lib.jittester.morph.InlineTypeFlatFieldMorphTemplate;
import jdk.test.lib.jittester.morph.MorphLegResult;
import jdk.test.lib.jittester.morph.MorphLegTarget;
import jdk.test.lib.jittester.morph.MorphTemplate;
import jdk.test.lib.jittester.types.TypeKlass;
import jdk.test.lib.jittester.utils.PseudoRandom;
import jdk.test.lib.jittester.visitors.JavaCodeVisitor;

public class FlatFieldMorphTests {
    public static void main(String[] args) throws Exception {
        ProductionParams.initializeFromCmdline(new String[] {
                "--disable-external-symbols", "true", "--disable-interfaces", "true",
                "--disable-inheritance", "true", "--member-functions-limit", "2",
                "--statement-limit", "2", "--operator-limit", "2", "--data-member-limit", "2",
                "--morph-template-inline-type-flat-field-probability", "100"});
        GenerationState.setCurrentMainClassName("Test_FlatField");
        SymbolTable.removeAll();
        SymbolTable.add(new FunctionInfo(TypeList.OBJECT.getName(), TypeList.OBJECT, TypeList.OBJECT,
                0, Symbol.PUBLIC));
        SymbolTable.add(new FunctionInfo(TypeList.STRING.getName(), TypeList.STRING, TypeList.STRING,
                0, Symbol.PUBLIC));
        testGeneratedClassesAndLegs();
        testStaticSinkFromAnotherClass();
        testFloatingPointConstraints();
        testSinkFieldStaticSetting(false);
        testSinkFieldStaticSetting(true);
        System.out.println("FlatFieldMorphTests passed");
    }

    private static void testSinkFieldStaticSetting(boolean disableStatic) throws Exception {
        ProductionParams.initializeFromCmdline(new String[] {
                "--disable-external-symbols=true", "--disable-static=" + disableStatic});
        GenerationState.Checkpoint checkpoint = GenerationState.checkpoint();
        try {
            TypeKlass payload = new TypeKlass("Test_FlatField_SinkPayload");
            payload.setValueKlass();
            TypeKlass owner = new TypeKlass("Test_FlatField_SinkOwner");
            VariableInfo flatField = new VariableInfo("flat", owner, payload,
                    VariableInfo.INITIALIZED | VariableInfo.NULL_RESTRICTED);
            SymbolTable.add(new FunctionInfo(payload.getName(), payload, payload, 0, Symbol.PUBLIC));
            InlineTypeFlatFieldMorphTemplate template = new InlineTypeFlatFieldMorphTemplate(
                    1L, 2, true, 1, payload, owner, flatField, null);
            IRNodeBuilder builder = new IRNodeBuilder().setOwnerKlass(owner).setLevel(1);
            boolean staticSeen = false;
            boolean instanceSeen = false;
            for (int seed = 0; seed < 20; seed++) {
                PseudoRandom.setCurrentSeed(seed * 104729L);
                MorphLegResult result = template.produceLeg(MorphLegTarget.FIELD_DECLARATION, 2, builder);
                VariableInfo sink = ((InlineTypeFlatFieldMorphTemplate) result.updatedTemplate()).sink();
                String source = result.node().accept(new JavaCodeVisitor());
                require(!disableStatic || !sink.isStatic(),
                        "MTFF generated a static sink with --disable-static=true: " + source);
                require(source.contains("static ") == sink.isStatic(), "Sink flags and source disagree");
                require((sink.flags & VariableInfo.INITIALIZED) != 0
                                && source.contains(" = new " + payload.getName() + "("),
                        "Sink must retain its initializer: " + source);
                staticSeen |= sink.isStatic();
                instanceSeen |= !sink.isStatic();
            }
            require(instanceSeen && (disableStatic || staticSeen),
                    "Exercise both sink kinds when static generation is allowed");
        } finally {
            GenerationState.rollbackTo(checkpoint);
        }
    }

    private static void testStaticSinkFromAnotherClass() throws Exception {
        GenerationState.Checkpoint checkpoint = GenerationState.checkpoint();
        try {
            TypeKlass payload = new TypeKlass("Test_FlatField_CrossOwnerPayload");
            payload.setValueKlass();
            TypeKlass holder = new TypeKlass("Test_FlatField_CrossOwnerHolder");
            TypeKlass sinkOwner = new TypeKlass("Test_FlatField_CrossOwnerSink");
            TypeKlass useOwner = new TypeKlass("Test_FlatField_CrossOwnerUse");
            VariableInfo flatField = new VariableInfo("flat", holder, payload,
                    VariableInfo.INITIALIZED | VariableInfo.NULL_RESTRICTED);
            VariableInfo sink = new VariableInfo("crossOwnerSink", sinkOwner, payload,
                    VariableInfo.STATIC | VariableInfo.INITIALIZED);
            SymbolTable.add(new FunctionInfo(holder.getName(), holder, holder, 0, Symbol.PUBLIC));
            SymbolTable.add(flatField);
            SymbolTable.add(sink);
            GenerationState.setCurrentFlowParams(GenerationState.currentFlowParams()
                    .withCodeContext(FlowParams.CodeContext.TEST).advance());
            InlineTypeFlatFieldMorphTemplate template = new InlineTypeFlatFieldMorphTemplate(
                    1L, 3, true, 1, payload, holder, flatField, sink);
            IRNodeBuilder builder = new IRNodeBuilder().setOwnerKlass(useOwner).setLevel(1)
                    .withOperatorLimit(2);
            boolean sinkUsed = false;
            for (int seed = 0; seed < 200 && !sinkUsed; seed++) {
                PseudoRandom.setCurrentSeed(seed);
                String source = template.produceLeg(MorphLegTarget.BLOCK, 3, builder)
                        .node().accept(new JavaCodeVisitor());
                if (source.contains(sink.name)) {
                    sinkUsed = true;
                    require(source.contains(sinkOwner.getName() + "." + sink.name),
                            "a static sink used from another class must be qualified: " + source);
                }
            }
            require(sinkUsed, "exercise a cross-owner static sink destination");
        } finally {
            GenerationState.rollbackTo(checkpoint);
        }
    }

    private static void testGeneratedClassesAndLegs() throws Exception {
        Method createTemplate = InlineTypeFlatFieldMorphTemplate.class.getDeclaredMethod("createGentestScoped");
        createTemplate.setAccessible(true);
        boolean loadSeen = false;
        boolean storeSeen = false;
        boolean recordSeen = false;
        boolean sinkSeen = false;
        boolean localSeen = false;
        boolean valueHolderSeen = false;
        for (int i = 0; i < 40; i++) {
            GenerationState.Checkpoint checkpoint = GenerationState.checkpoint();
            String stage = "types";
            try {
                PseudoRandom.setCurrentSeed(100 + i);
                MorphTemplate template = (MorphTemplate) createTemplate.invoke(null);
                IRNodeBuilder builder = new IRNodeBuilder().setName("Test_FlatField_Value_" + i).setLevel(0);
                MorphLegResult types = template.produceLeg(MorphLegTarget.TYPE_DECLARATION, 0, builder);
                String source = types.node().accept(new JavaCodeVisitor());
                assertNoHashHelpers(source);
                require(types.node() instanceof ClassDefinitionBlock, "type leg must produce class IR");
                for (IRNode node : types.node().getChildren()) {
                    require(node instanceof Klass, "payloads must use the ordinary class IR");
                    recordSeen |= ((Klass) node).getThisKlass().isRecord();
                }
                require(!source.contains("@jdk.internal.vm.annotation.NullRestricted"),
                        "the type leg must leave flat-field placement to an ordinary class");
                template = types.updatedTemplate();
                stage = "fields";
                boolean load = template.creationDiagnostic().contains("kind=load-value");
                TypeKlass owner = new TypeKlass("Test_FlatField_Holder_" + i);
                if (load && (i & 3) == 0) {
                    owner.setValueKlass();
                }
                SymbolTable.add(new FunctionInfo(owner.getName(), owner, owner, 0, Symbol.PUBLIC));
                builder.setOwnerKlass(owner).setExceptionSafe(false);
                MorphLegResult fields = template.produceLeg(MorphLegTarget.FIELD_DECLARATION, 1, builder);
                require(fields.node() instanceof Declaration, "the flat field is one ordinary declaration");
                String fieldSource = fields.node().accept(new JavaCodeVisitor());
                require(fieldSource.contains("@jdk.internal.vm.annotation.NullRestricted"),
                        "field leg must introduce a null-restricted field");
                if (owner.isValueKlass()) {
                    require(fieldSource.contains("final"), "value-class flat fields must be final");
                    valueHolderSeen = true;
                }
                template = fields.updatedTemplate();
                if (load && (i & 1) == 0) {
                    TypeKlass sinkOwner = new TypeKlass("Test_FlatField_Sink_" + i);
                    SymbolTable.add(new FunctionInfo(sinkOwner.getName(), sinkOwner, sinkOwner, 0, Symbol.PUBLIC));
                    builder.setOwnerKlass(sinkOwner);
                    MorphLegResult sink = template.produceLeg(MorphLegTarget.FIELD_DECLARATION,
                            template.nextLeg(), builder);
                    String sinkSource = sink.node().accept(new JavaCodeVisitor());
                    require(sinkSource.contains(" = ") && !sinkSource.contains("= null"),
                            "sink field must be initialized before ordinary code can read it");
                    template = sink.updatedTemplate();
                    sinkSeen = true;
                }
                GenerationState.setCurrentFlowParams(GenerationState.currentFlowParams()
                        .withCodeContext(FlowParams.CodeContext.TEST).advance());
                stage = "use";
                IRNode leg = template.produceLeg(MorphLegTarget.BLOCK, template.nextLeg(), builder).node();
                String use = leg.accept(new JavaCodeVisitor());
                assertNoHashHelpers(use);
                require(!(leg instanceof jdk.test.lib.jittester.RawJavaStatement), "use leg must use expression IR");
                localSeen |= leg instanceof jdk.test.lib.jittester.StatementSequence;
                loadSeen |= load;
                storeSeen |= !load;
            } catch (jdk.test.lib.jittester.ProductionFailedException ex) {
                throw new AssertionError("Failed " + stage + " for seed " + (100 + i), ex);
            } finally {
                GenerationState.rollbackTo(checkpoint);
            }
        }
        require(loadSeen && storeSeen && recordSeen && sinkSeen && localSeen && valueHolderSeen,
                "exercise stores, loads, sinks, local destinations, value hosts, and value records");
    }

    private static void testFloatingPointConstraints() throws Exception {
        TypeKlass owner = new TypeKlass("Test_FlatField_NaN");
        TypeKlass doubles = new TypeKlass("java.lang.Double");
        FunctionInfo bits = new FunctionInfo("doubleToRawLongBits", doubles, TypeList.LONG, 0,
                Symbol.PUBLIC | Symbol.STATIC,
                VariableInfo.symbolArgument("value", doubles, TypeList.DOUBLE,
                        VariableInfo.LOCAL | VariableInfo.INITIALIZED));
        bits.setArgumentConstraint(0, MethodArgumentConstraint.NAN_NORMALIZED);
        SymbolTable.add(bits);
        String call = Formatter.format(new FunctionFactory(2, owner, TypeList.LONG, false,
                function -> function.name.equals(bits.name) && function.owner.equals(doubles)).produce());
        require(call.contains("normalizeNaN("), "raw-bit conversions must retain the common NaN guard");

        TypeKlass math = new TypeKlass("java.lang.Math");
        FunctionInfo sqrt = new FunctionInfo("sqrt", math, TypeList.DOUBLE, 0,
                Symbol.PUBLIC | Symbol.STATIC,
                VariableInfo.symbolArgument("value", math, TypeList.DOUBLE,
                        VariableInfo.LOCAL | VariableInfo.INITIALIZED));
        sqrt.setResultWrapper(MethodResultWrapper.NORMALIZE_NAN);
        SymbolTable.add(sqrt);
        GenerationState.setCurrentFlowParams(GenerationState.currentFlowParams().withNormalizeNaN(true).advance());
        call = Formatter.format(new FunctionFactory(2, owner, TypeList.DOUBLE, false,
                function -> function.name.equals(sqrt.name) && function.owner.equals(math)).produce());
        require(call.contains("normalizeNaN("), "result wrappers must retain the caller's normalization mode");
    }

    private static void assertNoHashHelpers(String source) {
        require(!source.contains("hashCode(") && !source.contains("HashCode(")
                        && !source.contains("__jtt_"), "MTFF must not inject hash-based or fixed helper methods");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
