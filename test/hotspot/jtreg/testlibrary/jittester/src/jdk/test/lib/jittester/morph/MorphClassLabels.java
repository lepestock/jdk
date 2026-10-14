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

package jdk.test.lib.jittester.morph;

import java.util.HashMap;
import java.util.Map;
import jdk.test.lib.jittester.Symbol;
import jdk.test.lib.jittester.SymbolTable;
import jdk.test.lib.jittester.Type;
import jdk.test.lib.jittester.TypeList;
import jdk.test.lib.jittester.VariableInfo;
import jdk.test.lib.jittester.types.TypeKlass;

public final class MorphClassLabels {
    public static final String MTFF_PAYLOAD_PREFIX = "mtff-payload-";
    private static final int REFERENCE_BYTES = 4;

    public static String mtffPayload(TypeKlass type) {
        return MTFF_PAYLOAD_PREFIX + payloadBytes(type);
    }

    /**
     * Logical instance payload bytes for the MTFF budget, not a VM object size.
     * Null-restricted values are expanded recursively, including value-record
     * components and inherited fields. Other reference fields use compressed-oop
     * width, regardless of their referents' contents (boxed values, arrays, etc.).
     * Object headers, alignment padding and VM-specific flattening are excluded.
     */
    public static int payloadBytes(TypeKlass type) {
        return payloadBytes(type, new HashMap<>());
    }

    public static int fieldBytes(Type type, boolean nullRestricted) {
        return fieldBytes(type, nullRestricted, new HashMap<>());
    }

    private static int payloadBytes(TypeKlass type, Map<TypeKlass, Integer> sizes) {
        if (!type.isValueKlass()) {
            throw new IllegalArgumentException("Inline payload requires a value class: " + type.getName());
        }
        // The in-progress marker catches impossible recursive inline layouts;
        // completed sizes also avoid re-expanding shared nested payloads.
        Integer known = sizes.putIfAbsent(type, -1);
        if (known != null) {
            if (known < 0) {
                throw new IllegalArgumentException("Recursive inline payload: " + type.getName());
            }
            return known;
        }
        TypeKlass parent = type.getParent();
        int size = parent != null && parent.isValueKlass() ? payloadBytes(parent, sizes) : 0;
        for (Symbol symbol : SymbolTable.getAllCombined(type, VariableInfo.class)) {
            VariableInfo field = (VariableInfo) symbol;
            // Inherited symbols are copies for name lookup; count their original
            // declarations through the parent, including its private fields.
            if (!field.isStatic() && !field.isLocal() && (field.flags & VariableInfo.INHERITED) == 0) {
                size = Math.addExact(size, fieldBytes(field.type, field.isNullRestricted(), sizes));
            }
        }
        sizes.put(type, size);
        return size;
    }

    private static int fieldBytes(Type type, boolean nullRestricted, Map<TypeKlass, Integer> sizes) {
        if (nullRestricted) {
            if (!(type instanceof TypeKlass klass)) {
                throw new IllegalArgumentException("Null-restricted field requires a value class: " + type);
            }
            return payloadBytes(klass, sizes);
        }
        if (type.equals(TypeList.BOOLEAN) || type.equals(TypeList.BYTE)) {
            return 1;
        }
        if (type.equals(TypeList.SHORT) || type.equals(TypeList.CHAR)) {
            return 2;
        }
        if (type.equals(TypeList.INT) || type.equals(TypeList.FLOAT)) {
            return 4;
        }
        if (type.equals(TypeList.LONG) || type.equals(TypeList.DOUBLE)) {
            return 8;
        }
        if (type instanceof TypeKlass) {
            return REFERENCE_BYTES;
        }
        throw new IllegalArgumentException("Unsupported payload field type: " + type);
    }

    private MorphClassLabels() {
    }
}
