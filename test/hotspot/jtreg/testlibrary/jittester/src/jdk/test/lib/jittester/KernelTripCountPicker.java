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

import jdk.test.lib.jittester.utils.PseudoRandom;

public final class KernelTripCountPicker {
    private static final Range[] RANGES = {
            new Range(1, 1, 5),
            new Range(2, 3, 5),
            new Range(4, 7, 10),
            new Range(8, 15, 20),
            new Range(16, 31, 25),
            new Range(32, 63, 25),
            new Range(64, 127, 8),
            new Range(128, 255, 2),
    };
    private static final int TOTAL_WEIGHT = totalWeight();

    public static int pick() {
        int selected = PseudoRandom.randomNotNegative(TOTAL_WEIGHT);
        int threshold = 0;
        for (Range range : RANGES) {
            threshold += range.weight;
            if (selected < threshold) {
                return range.pick();
            }
        }
        return RANGES[RANGES.length - 1].pick();
    }

    private static int totalWeight() {
        int total = 0;
        for (Range range : RANGES) {
            total += range.weight;
        }
        return total;
    }

    private record Range(int min, int max, int weight) {
        int pick() {
            return min + PseudoRandom.randomNotNegative(max - min + 1);
        }
    }
}
