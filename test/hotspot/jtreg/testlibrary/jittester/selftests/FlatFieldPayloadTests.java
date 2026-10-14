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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import jdk.test.lib.jittester.GenerationState;
import jdk.test.lib.jittester.ProductionParams;
import jdk.test.lib.jittester.Symbol;
import jdk.test.lib.jittester.SymbolTable;
import jdk.test.lib.jittester.Type;
import jdk.test.lib.jittester.TypeList;
import jdk.test.lib.jittester.VariableInfo;
import jdk.test.lib.jittester.classes.ClassField;
import jdk.test.lib.jittester.classes.Klass;
import jdk.test.lib.jittester.collections.IndexedStorageKind;
import jdk.test.lib.jittester.functions.FunctionInfo;
import jdk.test.lib.jittester.morph.InlineTypeFlatFieldMorphTemplate;
import jdk.test.lib.jittester.morph.MorphClassLabels;
import jdk.test.lib.jittester.types.TypeArray;
import jdk.test.lib.jittester.types.TypeKlass;
import jdk.test.lib.jittester.utils.PseudoRandom;
import jdk.test.lib.jittester.visitors.JavaCodeVisitor;

public class FlatFieldPayloadTests {
    private static int nextType;

    public static void main(String[] args) throws Exception {
        ProductionParams.initializeFromCmdline(new String[] {
                "--disable-external-symbols=true", "--disable-interfaces=true",
                "--disable-inheritance=true", "--disable-functions=true",
                "--disable-expr-in-init=true", "--member-functions-limit=0",
                "--statement-limit=1", "--operator-limit=1", "--debug-type-labels"});
        GenerationState.setCurrentMainClassName("Test_PayloadSizes");
        SymbolTable.removeAll();
        SymbolTable.add(new FunctionInfo(TypeList.OBJECT.getName(), TypeList.OBJECT,
                TypeList.OBJECT, 0, Symbol.PUBLIC));
        testSmallNestedPayloadSelection();
        testPrimitiveLabels();
        testReferencesAndNestedValues();
        testInheritanceAndNonInstanceFields();
        testReferenceCycles();
        System.out.println("FlatFieldPayloadTests passed");
    }

    private static void testSmallNestedPayloadSelection() throws Exception {
        GenerationState.Checkpoint checkpoint = GenerationState.checkpoint();
        try {
            TypeKlass one = metadataValue("OneByte");
            field(one, "value", TypeList.BYTE, 0);
            TypeKlass two = metadataValue("TwoByteRecord");
            two.setRecord();
            field(two, "value", TypeList.SHORT, 0);
            TypeKlass three = metadataValue("ThreeByteComposition");
            field(three, "first", one, VariableInfo.NULL_RESTRICTED);
            field(three, "second", two, VariableInfo.NULL_RESTRICTED);
            TypeKlass empty = metadataValue("Empty");
            TypeKlass large = metadataValue("NineBytes");
            field(large, "first", TypeList.LONG, 0);
            field(large, "second", TypeList.BYTE, 0);
            for (TypeKlass type : List.of(one, two, three, empty, large)) {
                SymbolTable.add(new FunctionInfo(type.getName(), type, type, 0, Symbol.PUBLIC));
            }

            Method payloadFields = InlineTypeFlatFieldMorphTemplate.class.getDeclaredMethod("payloadFields");
            payloadFields.setAccessible(true);
            boolean[] smallInlineSeen = new boolean[4];
            boolean nullableSeen = false;
            boolean optionalInlineSeen = false;
            for (int seed = 0; seed < 20_000; seed++) {
                PseudoRandom.setCurrentSeed(seed * 104729L);
                int remaining = 8;
                for (Object item : (List<?>) payloadFields.invoke(null)) {
                    ClassField component = (ClassField) item;
                    int bytes = MorphClassLabels.fieldBytes(component.type(), component.nullRestricted());
                    if (bytes <= 0 || bytes > remaining) {
                        throw new AssertionError("Invalid component size " + bytes + " for budget " + remaining);
                    }
                    if (List.of(one, two, three).contains(component.type())) {
                        if (component.nullRestricted()) {
                            if (remaining < MorphClassLabels.fieldBytes(component.type(), false)) {
                                smallInlineSeen[bytes] = true;
                            } else {
                                optionalInlineSeen = true;
                            }
                        } else {
                            nullableSeen = true;
                        }
                    }
                    remaining -= bytes;
                }
            }
            for (int bytes = 1; bytes <= 3; bytes++) {
                if (!smallInlineSeen[bytes]) {
                    throw new AssertionError("Never selected a " + bytes
                            + "-byte nested payload when its reference would not fit");
                }
            }
            if (!nullableSeen || !optionalInlineSeen) {
                throw new AssertionError("Both nullable and inline components must remain selectable");
            }
        } finally {
            GenerationState.rollbackTo(checkpoint);
        }
    }

    private static void testPrimitiveLabels() throws Exception {
        Type[] types = {TypeList.LONG, TypeList.DOUBLE, TypeList.INT, TypeList.FLOAT,
                TypeList.SHORT, TypeList.CHAR, TypeList.BOOLEAN, TypeList.BYTE};
        int[] sizes = {8, 8, 4, 4, 2, 2, 1, 1};
        for (int i = 0; i < types.length; i++) {
            List<ClassField> fields = List.of(new ClassField(types[i], false));
            expectLabel(makeValue(false, fields), sizes[i]);
            expectLabel(makeValue(true, fields), sizes[i]);
        }
        expectLabel(makeValue(false, List.of()), 0);
        expectLabel(makeValue(true, List.of()), 0);
    }

    private static void testReferencesAndNestedValues() throws Exception {
        TypeKlass pair = makeValue(true, List.of(new ClassField(TypeList.BYTE, false),
                new ClassField(TypeList.SHORT, false)));
        expectLabel(pair, 3);
        TypeKlass middle = makeValue(false, List.of(new ClassField(pair, true),
                new ClassField(pair, true), new ClassField(TypeList.CHAR, false)));
        expectLabel(middle, 8);
        TypeKlass outer = makeValue(false, List.of(new ClassField(middle, true),
                new ClassField(pair, true), new ClassField(TypeList.LONG, false)));
        expectLabel(outer, 19);
        // Nullable value components remain references, including in value records.
        expectLabel(makeValue(true, List.of(new ClassField(outer, false))), 4);

        TypeKlass identity = new TypeKlass("Test_IdentityReference");
        TypeKlass record = new TypeKlass("Test_IdentityRecordReference");
        record.setRecord();
        List<Type> references = new ArrayList<>(List.of(identity, record, TypeList.OBJECT,
                TypeList.STRING, new TypeKlass("java.util.ArrayList"),
                new TypeKlass("Test_EnumReference"), new TypeArray(TypeList.LONG, 3),
                new TypeArray(pair, 1, IndexedStorageKind.ARRAY, true),
                new TypeArray(pair, 1, IndexedStorageKind.LIST)));
        for (String boxed : List.of("Boolean", "Byte", "Short", "Character",
                "Integer", "Long", "Float", "Double")) {
            references.add(new TypeKlass("java.lang." + boxed));
        }
        for (Type reference : references) {
            expectLabel(makeValue(true, List.of(new ClassField(reference, false))), 4);
        }
        expectLabel(makeValue(true, List.of(new ClassField(TypeList.BYTE, false),
                new ClassField(identity, false))), 5);
    }

    private static void testInheritanceAndNonInstanceFields() {
        TypeKlass parent = metadataValue("Parent");
        parent.setAbstract();
        field(parent, "privateShort", TypeList.SHORT, VariableInfo.PRIVATE);
        field(parent, "shared", TypeList.BYTE, 0);
        field(parent, "staticLong", TypeList.LONG, VariableInfo.STATIC);

        TypeKlass child = metadataValue("Child");
        child.setParent(parent);
        field(child, "shared", TypeList.BYTE, VariableInfo.INHERITED);
        field(child, "ownLong", TypeList.LONG, 0);
        field(child, "localLong", TypeList.LONG, VariableInfo.LOCAL);
        label(child);
        expectLabel(child, 11);

        TypeKlass grandchild = metadataValue("Grandchild");
        grandchild.setParent(child);
        field(grandchild, "shared", TypeList.BYTE, VariableInfo.INHERITED);
        field(grandchild, "ownLong", TypeList.LONG, VariableInfo.INHERITED);
        label(grandchild);
        expectLabel(grandchild, 11);

        TypeKlass ignored = metadataValue("Ignored");
        field(ignored, "staticLong", TypeList.LONG, VariableInfo.STATIC);
        field(ignored, "localLong", TypeList.LONG, VariableInfo.LOCAL);
        label(ignored);
        expectLabel(ignored, 0);
    }

    private static TypeKlass makeValue(boolean record, List<ClassField> fields) throws Exception {
        PseudoRandom.setCurrentSeed(100 + nextType);
        IRNodeBuilder builder = new IRNodeBuilder().setName("Test_Payload_" + nextType++)
                .setLevel(0).setMemberFunctionsLimit(0).setMemberFunctionsArgLimit(0)
                .withStatementLimit(1).withOperatorLimit(1);
        Klass declaration = record ? builder.getValueRecordKlassFactory(fields).produce()
                : builder.getValueKlassFactory(fields).produce();
        TypeKlass type = declaration.getThisKlass();
        String comment = "// Type labels: " + String.join(", ", type.getLabels()) + "\n";
        if (!declaration.accept(new JavaCodeVisitor()).startsWith(comment)) {
            throw new AssertionError("Missing payload labels on " + type.getName());
        }
        return type;
    }

    private static void testReferenceCycles() {
        TypeKlass selfReference = metadataValue("SelfReference");
        field(selfReference, "next", selfReference, 0);
        label(selfReference);
        expectLabel(selfReference, 4);

        TypeKlass first = metadataValue("First");
        TypeKlass second = metadataValue("Second");
        field(first, "next", second, VariableInfo.NULL_RESTRICTED);
        field(second, "next", first, VariableInfo.NULL_RESTRICTED);
        try {
            MorphClassLabels.payloadBytes(first);
            throw new AssertionError("Accepted an impossible recursive inline payload");
        } catch (IllegalArgumentException expected) {
            if (!expected.getMessage().contains("Recursive inline payload")) {
                throw new AssertionError(expected);
            }
        }
    }

    private static TypeKlass metadataValue(String suffix) {
        TypeKlass type = new TypeKlass("Test_PayloadMetadata_" + suffix);
        type.setValueKlass();
        TypeList.add(type);
        return type;
    }

    private static void field(TypeKlass owner, String name, Type type, int flags) {
        SymbolTable.add(new VariableInfo(name, owner, type, flags | VariableInfo.INITIALIZED));
    }

    private static void label(TypeKlass type) {
        new ValueKlassFactory(type.getName(), 0, 0, 1, 1, 0).finalizeClassFlags(type);
    }

    private static void expectLabel(TypeKlass type, int bytes) {
        String expected = "mtff-payload-" + bytes;
        if (!type.getLabels().equals(Set.of(expected))) {
            throw new AssertionError(type.getName() + ": expected " + expected + ", got " + type.getLabels());
        }
    }
}
