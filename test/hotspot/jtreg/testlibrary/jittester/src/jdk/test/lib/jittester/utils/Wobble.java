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

import java.util.Locale;
import java.util.Random;

/** Per-gentest variation for nonnegative counts and relative weights. */
public final class Wobble {
    private Wobble() {}

    public enum Level {
        STABLE(0, 0, 0),
        SLIGHTLY_WOBBLY(0.05, 0.45, 0.05),
        WOBBLY(0.10, 1.0, 0.20),
        VERY_WOBBLY(0.20, 1.5, 0.35);

        private final double tightSigma;
        private final double broadSigma;
        private final double broadProbability;

        Level(double tightSigma, double broadSigma, double broadProbability) {
            this.tightSigma = tightSigma;
            this.broadSigma = broadSigma;
            this.broadProbability = broadProbability;
        }
    }

    public record Specification(int baseline, Level level) {
        public static Specification parse(String text, int minimum) {
            String[] parts = text.split(":", -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Expected <integer>:<wobbliness-class>: " + text);
            }
            Level level = switch (parts[1]) {
                case "stable" -> Level.STABLE;
                case "slightly_wobbly" -> Level.SLIGHTLY_WOBBLY;
                case "wobbly" -> Level.WOBBLY;
                case "very_wobbly" -> Level.VERY_WOBBLY;
                default -> throw new IllegalArgumentException("Unknown wobbliness class: " + parts[1]);
            };
            int baseline = Integer.parseInt(parts[0]);
            if (baseline < minimum) {
                throw new IllegalArgumentException("Wobbly baseline must be >= " + minimum + ": " + text);
            }
            return new Specification(baseline, level);
        }

        @Override
        public String toString() {
            return baseline + ":" + level.name().toLowerCase(Locale.ROOT);
        }

        public int sample(long gentestSeed, String optionName, int minimum) {
            if (baseline < minimum) {
                throw new IllegalArgumentException("Baseline is below the parameter minimum");
            }
            if (level == Level.STABLE || baseline == minimum) {
                return baseline;
            }
            // Independent named streams do not consume structural RNG or depend on option order.
            long hash = 0xcbf29ce484222325L;
            for (int i = 0; i < optionName.length(); i++) {
                hash = (hash ^ optionName.charAt(i)) * 0x100000001b3L;
            }
            Random random = new Random(mix(gentestSeed ^ hash ^ 0x574f42424c453031L));
            double sigma = random.nextDouble() < level.broadProbability ? level.broadSigma : level.tightSigma;
            // Each log-normal component has mean 1; shifting by minimum preserves valid counts.
            double value = minimum + (baseline - (double) minimum)
                    * StrictMath.exp(sigma * random.nextGaussian() - sigma * sigma / 2);
            if (value >= Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
            int lower = (int) value;
            return lower + (random.nextDouble() < value - lower ? 1 : 0);
        }
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
}
