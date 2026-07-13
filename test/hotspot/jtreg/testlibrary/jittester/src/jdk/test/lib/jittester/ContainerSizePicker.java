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

public final class ContainerSizePicker {
    private static final int[] BYTE_LENGTH_ANCHORS = {
            0, 1, 2, 3, 4, 7, 8, 15, 16, 17, 31, 32, 33, 63, 64, 65,
            127, 128, 129, 255, 256, 257, 511, 512
    };
    private static final int SMALL_BYTE_ANCHOR_THRESHOLD = 64;
    private static final int SMALL_ANCHOR_BIAS_PERCENT = 85;

    private ContainerSizePicker() {
    }

    public static int pickIntContainerSize() {
        return Math.max(1, pickByteAnchor() / 4);
    }

    private static int pickByteAnchor() {
        // FIXME: temporary dev/testing bias; retune after array-utilization work stabilizes.
        boolean preferSmall = PseudoRandom.randomNotNegative(100) < SMALL_ANCHOR_BIAS_PERCENT;
        if (preferSmall) {
            int[] small = new int[BYTE_LENGTH_ANCHORS.length];
            int count = 0;
            for (int anchor : BYTE_LENGTH_ANCHORS) {
                if (anchor <= SMALL_BYTE_ANCHOR_THRESHOLD) {
                    small[count++] = anchor;
                }
            }
            if (count > 0) {
                return small[PseudoRandom.randomNotNegative(count)];
            }
        }
        return BYTE_LENGTH_ANCHORS[PseudoRandom.randomNotNegative(BYTE_LENGTH_ANCHORS.length)];
    }
}
