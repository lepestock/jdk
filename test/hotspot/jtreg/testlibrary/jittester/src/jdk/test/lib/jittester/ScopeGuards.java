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

package jdk.test.lib.jittester;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Generic scoped guard storage.
 *
 * <p>Guards have stacked semantics: nested {@code push} calls for the same guard key
 * increase depth, and each {@code pop} removes one layer. Callers may use this class
 * as a true stack for nested scopes, or as a simple on/off guard with single push/pop.
 */
public final class ScopeGuards {
    private static final Map<Object, Integer> depths = new HashMap<>();

    private ScopeGuards() {
    }

    public static void push(Object guard) {
        Object key = Objects.requireNonNull(guard);
        depths.merge(key, 1, Integer::sum);
    }

    public static void pop(Object guard) {
        Object key = Objects.requireNonNull(guard);
        Integer depth = depths.get(key);
        if (depth == null || depth <= 1) {
            depths.remove(key);
        } else {
            depths.put(key, depth - 1);
        }
    }

    public static boolean isSet(Object guard) {
        Object key = Objects.requireNonNull(guard);
        return depths.getOrDefault(key, 0) > 0;
    }
}
