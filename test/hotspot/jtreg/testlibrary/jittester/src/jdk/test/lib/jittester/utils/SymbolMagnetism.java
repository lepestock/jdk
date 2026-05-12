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
import java.util.Comparator;
import java.util.List;
import jdk.test.lib.jittester.ProductionParams;
import jdk.test.lib.jittester.Symbol;

/**
 * Generic helpers for magnet-biased symbol selection.
 */
public final class SymbolMagnetism {
    private static final double DISTANCE_DECAY = 0.125;
    private static final double EXACT_MATCH_WEIGHT = 1_000_000.0;
    private static final double MIN_WEIGHT = 1e-15;

    private SymbolMagnetism() {
    }

    private static boolean isStrictExactMode() {
        return ProductionParams.magnetismLevel != null
                && ProductionParams.magnetismLevel.value() <= 0;
    }

    public static long unsignedDistance(long a, long b) {
        return Long.compareUnsigned(a, b) >= 0 ? a - b : b - a;
    }

    public static double magnetWeight(long candidateGeneId, long targetGeneId) {
        long distance = unsignedDistance(candidateGeneId, targetGeneId);
        if (distance == 0L) {
            return EXACT_MATCH_WEIGHT;
        }
        int bucket = 64 - Long.numberOfLeadingZeros(distance);
        double weight = Math.pow(DISTANCE_DECAY, bucket);
        return Math.max(MIN_WEIGHT, weight);
    }

    public static <T extends Symbol> int pickMagneticIndex(List<T> candidates, long targetGeneId) {
        if (candidates.isEmpty()) {
            return -1;
        }
        if (isStrictExactMode()) {
            for (int i = 0; i < candidates.size(); i++) {
                if (candidates.get(i).getMagnetismGeneId() == targetGeneId) {
                    return i;
                }
            }
            // Deterministic fallback in strict mode: no stochastic draw.
            return 0;
        }
        double totalWeight = 0.0;
        for (T candidate : candidates) {
            totalWeight += magnetWeight(candidate.getMagnetismGeneId(), targetGeneId);
        }
        if (totalWeight <= 0.0) {
            return PseudoRandom.randomNotNegative(candidates.size());
        }
        double draw = PseudoRandom.random() * totalWeight;
        double prefix = 0.0;
        for (int i = 0; i < candidates.size(); i++) {
            prefix += magnetWeight(candidates.get(i).getMagnetismGeneId(), targetGeneId);
            if (draw <= prefix) {
                return i;
            }
        }
        return candidates.size() - 1;
    }

    public static <T extends Symbol> List<T> orderByMagneticPreference(List<T> candidates, long targetGeneId) {
        if (isStrictExactMode()) {
            ArrayList<T> ordered = new ArrayList<>(candidates);
            ordered.sort(new Comparator<T>() {
                @Override
                public int compare(T left, T right) {
                    long l = left.getMagnetismGeneId();
                    long r = right.getMagnetismGeneId();
                    boolean le = l == targetGeneId;
                    boolean re = r == targetGeneId;
                    if (le != re) {
                        return le ? -1 : 1;
                    }
                    long ld = unsignedDistance(l, targetGeneId);
                    long rd = unsignedDistance(r, targetGeneId);
                    int byDist = Long.compareUnsigned(ld, rd);
                    if (byDist != 0) {
                        return byDist;
                    }
                    return Long.compareUnsigned(l, r);
                }
            });
            return ordered;
        }
        ArrayList<T> remaining = new ArrayList<>(candidates);
        ArrayList<T> ordered = new ArrayList<>(candidates.size());
        while (!remaining.isEmpty()) {
            int picked = pickMagneticIndex(remaining, targetGeneId);
            if (picked < 0) {
                break;
            }
            ordered.add(remaining.remove(picked));
        }
        return ordered;
    }
}
