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

import java.util.Arrays;

/**
 * Small long-array container for sparse statement overrides.
 *
 * NOTE: {@code values} and {@code size} are intentionally public for fast traversal.
 * External mutation is unsafe and can break invariants.
 */
public final class LongSmallSet {
    public static final long NOT_OVERRIDDEN = Long.MIN_VALUE;

    /**
     * Dense positional storage of overrides, with NOT_OVERRIDDEN sentinel for gaps.
     */
    public final long[] values;

    /**
     * Number of valid positions in {@code values}.
     */
    public final int size;

    public LongSmallSet() {
        this.values = new long[0];
        this.size = 0;
    }

    public LongSmallSet(long[] values) {
        int end = values.length;
        while (end > 0 && values[end - 1] == NOT_OVERRIDDEN) {
            end--;
        }
        this.values = Arrays.copyOf(values, end);
        this.size = end;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public int overriddenCount() {
        int count = 0;
        for (int i = 0; i < size; i++) {
            if (values[i] != NOT_OVERRIDDEN) {
                count++;
            }
        }
        return count;
    }

    public boolean hasOverrides() {
        return overriddenCount() > 0;
    }

    public Long getNullable(int index) {
        if (index < 0 || index >= size) {
            return null;
        }
        long value = values[index];
        return value == NOT_OVERRIDDEN ? null : value;
    }
}

