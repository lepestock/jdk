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

import compiler.lib.generators.RandomnessSource;

import static java.lang.Float.float16ToFloat;
import static java.lang.Float.floatToFloat16;

/**
 * A {@link RandomnessSource} backed by jittester's global {@link PseudoRandom}.
 *
 * This keeps all randomness consumption inside the existing jittester RNG stream,
 * so replay behavior stays deterministic with respect to jittester seeds.
 */
public final class PseudoRandomnessSourceAdapter implements RandomnessSource {
    private static void requireRange(boolean ok) {
        if (!ok) {
            throw new IllegalArgumentException("bound must be greater than origin");
        }
    }

    @Override
    public long nextLong() {
        return PseudoRandom.nextLong();
    }

    @Override
    public long nextLong(long lo, long hi) {
        requireRange(lo < hi);
        return PseudoRandom.nextLong(lo, hi);
    }

    @Override
    public int nextInt() {
        return PseudoRandom.nextInt();
    }

    @Override
    public int nextInt(int lo, int hi) {
        requireRange(lo < hi);
        return PseudoRandom.nextInt(lo, hi);
    }

    @Override
    public double nextDouble(double lo, double hi) {
        requireRange(lo < hi);
        return lo + (hi - lo) * PseudoRandom.random();
    }

    @Override
    public float nextFloat(float lo, float hi) {
        requireRange(lo < hi);
        return lo + (hi - lo) * (float) PseudoRandom.random();
    }

    @Override
    public short nextFloat16(short lo, short hi) {
        return floatToFloat16(nextFloat(float16ToFloat(lo), float16ToFloat(hi)));
    }
}
