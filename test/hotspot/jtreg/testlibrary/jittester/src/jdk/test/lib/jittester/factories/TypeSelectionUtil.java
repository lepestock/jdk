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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import jdk.test.lib.jittester.Type;
import jdk.test.lib.jittester.TypeList;
import jdk.test.lib.jittester.types.TypeKlass;
import jdk.test.lib.jittester.utils.PseudoRandom;

final class TypeSelectionUtil {
    private static final double PREFER_PRIMITIVE_STRING_AND_OWN_TYPES = 0.92;

    private TypeSelectionUtil() {}

    static Type pickPreferredOrAnyType(TypeKlass ownerClass, Collection<Type> allTypes) {
        List<Type> preferred = preferredTypes(ownerClass, allTypes);
        if (!preferred.isEmpty() && PseudoRandom.randomBoolean(PREFER_PRIMITIVE_STRING_AND_OWN_TYPES)) {
            return PseudoRandom.randomElement(preferred);
        }
        return PseudoRandom.randomElement(new ArrayList<>(allTypes));
    }

    private static List<Type> preferredTypes(TypeKlass ownerClass, Collection<Type> allTypes) {
        List<Type> preferred = new ArrayList<>();
        for (Type t : allTypes) {
            if (t.equals(TypeList.VOID)) {
                continue;
            }
            if (TypeList.isBuiltIn(t) || t.equals(TypeList.STRING)) {
                preferred.add(t);
                continue;
            }
            if (t instanceof TypeKlass tk && isOwnGeneratedOrRelated(ownerClass, tk)) {
                preferred.add(t);
            }
        }
        return preferred;
    }

    private static boolean isOwnGeneratedOrRelated(TypeKlass ownerClass, TypeKlass candidate) {
        String ownerName = ownerClass.getName();
        String candidateName = candidate.getName();
        if (candidateName.equals(ownerName) || candidateName.startsWith(ownerName + "_")) {
            return true;
        }
        // Keep non-JDK types preferred over unrelated java.* classes.
        return !candidateName.startsWith("java.");
    }
}
