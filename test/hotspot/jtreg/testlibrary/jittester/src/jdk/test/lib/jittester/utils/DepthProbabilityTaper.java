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

/**
 * Shared depth-aware probabilistic tapering helpers used by generation heuristics.
 *
 * Why this exists:
 * JitTester uses several "probability changes with depth" rules (e.g. expression
 * recursion stop probability, const-bias decay). Keeping them in one place makes
 * tuning consistent and avoids duplicated formulas drifting apart.
 */
public final class DepthProbabilityTaper {
    private DepthProbabilityTaper() {
    }

    /**
     * Rising asymptote:
     * p(depth) = base + (max - base) * depth / (depth + halfDepth)
     */
    public static double risingAsymptote(int depth, double base, double max, int halfDepth) {
        double normalizedBase = clamp01(base);
        double normalizedMax = Math.max(normalizedBase, clamp01(max));
        int hd = Math.max(1, halfDepth);
        double d = Math.max(1.0, depth);
        double ratio = d / (d + hd);
        return normalizedBase + (normalizedMax - normalizedBase) * ratio;
    }

    /**
     * Bounded S-shaped ramp:
     * p(depth) = floor + (1 - floor) * (6x^5 - 15x^4 + 10x^3),
     * where x = (depth - startDepth) / (fullDepth - startDepth), clamped to [0, 1].
     *
     * Use this when early depths should strongly favor recursive/operator production and
     * late depths should strongly favor terminals. The floor is the minimum terminal-forcing
     * probability before the ramp starts. The start and full depths define the transition
     * interval; a shorter interval makes the switch from operators to terminals steeper.
     * At fullDepth and beyond the result is exactly 1.0, so terminal production is forced.
     */
    public static double smootherStepRamp(int depth, double floor, int startDepth, int fullDepth) {
        double normalizedFloor = clamp01(floor);
        int start = Math.max(1, startDepth);
        int full = Math.max(start + 1, fullDepth);
        if (depth <= start) {
            return normalizedFloor;
        }
        if (depth >= full) {
            return 1.0;
        }
        double x = (depth - start) / (double) (full - start);
        double s = x * x * x * (x * (x * 6.0 - 15.0) + 10.0);
        return normalizedFloor + (1.0 - normalizedFloor) * s;
    }

    /**
     * Decaying asymptote:
     * p(depth) = base * (halfDepth / (depth + halfDepth))
     */
    public static double decayingAsymptote(int depth, double base, int halfDepth) {
        double normalizedBase = clamp01(base);
        int hd = Math.max(1, halfDepth);
        double d = Math.max(1.0, depth);
        return normalizedBase * (hd / (d + hd));
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
