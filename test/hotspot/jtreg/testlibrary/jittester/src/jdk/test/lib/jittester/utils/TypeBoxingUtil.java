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

package jdk.test.lib.jittester.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jdk.test.lib.jittester.BuiltInType;
import jdk.test.lib.jittester.Type;
import jdk.test.lib.jittester.TypeList;

public final class TypeBoxingUtil {
    private static final Map<String, String> PRIMITIVE_TO_WRAPPER;
    private static final Map<String, String> WRAPPER_TO_PRIMITIVE;

    static {
        Map<String, String> p2w = new HashMap<>();
        p2w.put("byte", "java.lang.Byte");
        p2w.put("short", "java.lang.Short");
        p2w.put("char", "java.lang.Character");
        p2w.put("int", "java.lang.Integer");
        p2w.put("long", "java.lang.Long");
        p2w.put("float", "java.lang.Float");
        p2w.put("double", "java.lang.Double");
        p2w.put("boolean", "java.lang.Boolean");
        PRIMITIVE_TO_WRAPPER = Collections.unmodifiableMap(p2w);

        Map<String, String> w2p = new HashMap<>();
        for (Map.Entry<String, String> entry : p2w.entrySet()) {
            w2p.put(entry.getValue(), entry.getKey());
        }
        WRAPPER_TO_PRIMITIVE = Collections.unmodifiableMap(w2p);
    }

    private TypeBoxingUtil() {
    }

    public static boolean isWrapperType(Type type) {
        return type != null && WRAPPER_TO_PRIMITIVE.containsKey(type.getName());
    }

    public static boolean isPrimitiveNumeric(Type type) {
        return type != null
                && TypeList.isBuiltIn(type)
                && !type.equals(TypeList.VOID)
                && !type.equals(TypeList.BOOLEAN);
    }

    public static boolean isNumericPrimitiveOrWrapper(Type type) {
        return isPrimitiveNumeric(type) || (isWrapperType(type)
                && isPrimitiveNumeric(toPrimitiveType(type)));
    }

    public static boolean isArithmeticResultType(Type type) {
        Type primitiveType = toPrimitiveType(type);
        if (!isPrimitiveNumeric(primitiveType)) {
            return false;
        }
        BuiltInType builtInType = (BuiltInType) primitiveType;
        return builtInType.equals(TypeList.INT) || builtInType.isMoreCapaciousThan(TypeList.INT);
    }

    public static Type toPrimitiveType(Type type) {
        if (type == null) {
            return null;
        }
        if (TypeList.isBuiltIn(type)) {
            return type;
        }
        String primitiveName = WRAPPER_TO_PRIMITIVE.get(type.getName());
        return primitiveName == null ? null : TypeList.find(primitiveName);
    }

    public static Type toWrapperType(Type type) {
        if (type == null) {
            return null;
        }
        if (isWrapperType(type)) {
            return type;
        }
        String wrapperName = PRIMITIVE_TO_WRAPPER.get(type.getName());
        return wrapperName == null ? null : TypeList.find(wrapperName);
    }

    public static boolean isAssignmentCompatibleWithBoxing(Type from, Type to) {
        if (from == null || to == null) {
            return false;
        }
        if (from.canImplicitlyCastTo(to)) {
            return true;
        }

        boolean fromWrapper = isWrapperType(from);
        boolean toWrapper = isWrapperType(to);
        Type fromPrimitive = toPrimitiveType(from);
        Type toPrimitive = toPrimitiveType(to);

        if (fromWrapper && !toWrapper) {
            // unboxing + primitive widening
            return fromPrimitive != null && toPrimitive != null
                    && fromPrimitive.canImplicitlyCastTo(toPrimitive);
        }
        if (!fromWrapper && toWrapper) {
            // boxing (exact primitive-wrapper pair)
            Type expectedWrapper = toWrapperType(from);
            return expectedWrapper != null && expectedWrapper.equals(to);
        }
        return false;
    }

    public static Collection<Type> getAssignmentCompatibleWithBoxing(Collection<Type> types, Type targetType) {
        List<Type> result = new ArrayList<>();
        for (Type type : types) {
            if (isAssignmentCompatibleWithBoxing(type, targetType)) {
                result.add(type);
            }
        }
        return result;
    }

    public static List<Type> getArithmeticOperandTypesForResult(Type resultType) {
        Type primitiveResultType = toPrimitiveType(resultType);
        if (!isPrimitiveNumeric(primitiveResultType)) {
            return Collections.emptyList();
        }
        Collection<Type> primitiveOperands = TypeUtil.getImplicitlyCastable(TypeList.getBuiltIn(), primitiveResultType);
        List<Type> result = new ArrayList<>(primitiveOperands);
        for (Type primitiveType : primitiveOperands) {
            Type wrapperType = toWrapperType(primitiveType);
            if (wrapperType != null && !result.contains(wrapperType)) {
                result.add(wrapperType);
            }
        }
        return result;
    }

    public static List<Type> getCompoundAssignmentRightOperandTypes(Type leftType) {
        Type primitiveLeftType = toPrimitiveType(leftType);
        if (!isPrimitiveNumeric(primitiveLeftType)) {
            return Collections.emptyList();
        }
        Collection<Type> primitiveCandidates = TypeUtil.getExplicitlyCastable(TypeList.getBuiltIn(), primitiveLeftType);
        List<Type> result = new ArrayList<>(primitiveCandidates);
        for (Type primitiveType : primitiveCandidates) {
            Type wrapperType = toWrapperType(primitiveType);
            if (wrapperType != null && !result.contains(wrapperType)) {
                result.add(wrapperType);
            }
        }
        return result;
    }
}
