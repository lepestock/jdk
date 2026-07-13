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

import java.util.Set;
import jdk.test.lib.jittester.ProductionParams;
import jdk.test.lib.jittester.Type;
import jdk.test.lib.jittester.TypeList;
import jdk.test.lib.jittester.types.TypeArray;
import jdk.test.lib.jittester.types.TypeKlass;
import jdk.test.lib.jittester.utils.PseudoRandom;

final class FinalVariablePolicy {
    private static final int ARGUMENT_FINAL_PERCENT = 5;
    private static final int SCALAR_LOCAL_FINAL_PERCENT = 5;
    private static final int SCALAR_FIELD_FINAL_PERCENT = 10;
    private static final int SCALAR_STATIC_FIELD_FINAL_PERCENT = 3;
    private static final int REFERENCE_FINAL_PERCENT = 100;

    private static final Set<String> BOXED_SCALAR_TYPES = Set.of(
            "java.lang.Boolean",
            "java.lang.Byte",
            "java.lang.Character",
            "java.lang.Short",
            "java.lang.Integer",
            "java.lang.Long",
            "java.lang.Float",
            "java.lang.Double");

    static boolean shouldMakeArgumentFinal(Type type) {
        return finalsEnabled() && pick(ARGUMENT_FINAL_PERCENT);
    }

    static boolean shouldMakeInitializedVariableFinal(TypeKlass ownerClass, Type type,
            boolean isLocal, boolean isStatic, boolean requestedFinal) {
        if (isValueClassInstanceField(ownerClass, isLocal, isStatic)) {
            return true;
        }
        if (!requestedFinal || !finalsEnabled()) {
            return false;
        }
        return pick(finalPercent(type, isLocal, isStatic));
    }

    static boolean isValueClassInstanceField(TypeKlass ownerClass, boolean isLocal, boolean isStatic) {
        return ownerClass != null && ownerClass.isValueKlass() && !isLocal && !isStatic;
    }

    private static int finalPercent(Type type, boolean isLocal, boolean isStatic) {
        if (isScalarImmutable(type)) {
            if (isLocal) {
                return SCALAR_LOCAL_FINAL_PERCENT;
            }
            return isStatic ? SCALAR_STATIC_FIELD_FINAL_PERCENT : SCALAR_FIELD_FINAL_PERCENT;
        }
        if (isMutableReference(type)) {
            return REFERENCE_FINAL_PERCENT;
        }
        return REFERENCE_FINAL_PERCENT;
    }

    private static boolean isScalarImmutable(Type type) {
        return TypeList.isBuiltIn(type) && !type.equals(TypeList.VOID)
                || type.equals(TypeList.STRING)
                || BOXED_SCALAR_TYPES.contains(type.getName());
    }

    private static boolean isMutableReference(Type type) {
        return type instanceof TypeArray;
    }

    private static boolean finalsEnabled() {
        return !ProductionParams.disableFinalVariables.value();
    }

    private static boolean pick(int percent) {
        return PseudoRandom.randomNotNegative(100) < percent;
    }
}
