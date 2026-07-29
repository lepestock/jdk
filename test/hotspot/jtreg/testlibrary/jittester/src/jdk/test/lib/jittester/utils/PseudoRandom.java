/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/**
 * This class is used for any random generation operations.
 */
public class PseudoRandom {

    private static Random random = null;
    private static final Field SEED_FIELD;

    static {
        try {
            SEED_FIELD = Random.class.getDeclaredField("seed");
            SEED_FIELD.setAccessible(true);
        } catch (ReflectiveOperationException roe) {
            throw new Error("Can't get seed field: " + roe, roe);
        }
    }

    public static void reset() {
        random = new java.util.Random();
    }

    public static double random() {
        applyReplayedRngGene("random");
        recordRngGene("random");
        return random.nextDouble();
    }

    /**
     * Consumes RNG state without recording/consuming a genome RNG event.
     * Intended for internal selection logic where the final choice is recorded explicitly.
     */
    public static double randomSilent() {
        return random.nextDouble();
    }

    public static long nextLong() {
        applyReplayedRngGene("nextLong");
        recordRngGene("nextLong");
        return random.nextLong();
    }

    /**
     * Consumes RNG state without recording/consuming a genome RNG event.
     * Intended for cases where the decision is tracked via a dedicated non-N event category.
     */
    public static long nextLongSilent() {
        return random.nextLong();
    }

    public static long nextLong(long lo, long hi) {
        applyReplayedRngGene("nextLongRange");
        recordRngGene("nextLongRange");
        return random.nextLong(lo, hi);
    }

    public static int nextInt() {
        applyReplayedRngGene("nextInt");
        recordRngGene("nextInt");
        return random.nextInt();
    }

    public static int nextInt(int lo, int hi) {
        applyReplayedRngGene("nextIntRange");
        recordRngGene("nextIntRange");
        return random.nextInt(lo, hi);
    }

    // uniformly distributed boolean
    public static boolean randomBoolean() {
        applyReplayedRngGene("randomBoolean");
        recordRngGene("randomBoolean");
        return random.nextBoolean();
    }

    // non-uniformly distributed boolean. 0 probability - never true, 1 - always true
    public static boolean randomBoolean(double probability) {
        applyReplayedRngGene("randomBooleanProb");
        recordRngGene("randomBooleanProb");
        return random.nextDouble() < probability;
    }

    public static long randomNotZero(long limit) {
        applyReplayedRngGene("randomNotZeroLong");
        recordRngGene("randomNotZeroLong");
        long result = (long) (limit * random.nextDouble());
        return result > 0L ? result : 1L;
    }

    public static int randomNotZero(int limit) {
        applyReplayedRngGene("randomNotZeroInt");
        recordRngGene("randomNotZeroInt");
        int result = (int) (limit * random.nextDouble());
        return result > 0 ? result : 1;
    }

    public static void shuffle(List<?> list) {
        applyReplayedRngGene("shuffle");
        recordRngGene("shuffle");
        Collections.shuffle(list, random);
    }

    /**
     * Shuffle without replay/record event gene interaction.
     * Uses current RNG state only.
     */
    public static void shuffleSilent(List<?> list) {
        Collections.shuffle(list, random);
    }

    public static int randomNotNegative(int limit) {
        applyReplayedRngGene("randomNotNegative");
        recordRngGene("randomNotNegative");
        int result = (int) (limit * random.nextDouble());
        return Math.abs(result);
    }

    public static <T> T randomElement(Collection<T> collection) {
        if (collection.isEmpty()) {
            throw new NoSuchElementException("Empty, no element can be randomly selected");
        }
        if (collection instanceof List) {
            return randomElement((List<T>) collection);
        } else {
            applyReplayedRngGene("randomElementCollection");
            recordRngGene("randomElementCollection");
            int ix = random.nextInt(collection.size());
            final Iterator<T> iterator = collection.iterator();
            while (ix > 0) {
                ix--;
                iterator.next();
            }
            return iterator.next();
        }
    }

    public static <T> T randomElement(List<T> list) {
        if (list.isEmpty()) {
            throw new NoSuchElementException("Empty, no element can be randomly selected");
        }
        applyReplayedRngGene("randomElementList");
        recordRngGene("randomElementList");
        return list.get(random.nextInt(list.size()));
    }

    public static <T> T randomElement(T[] array) {
        if (array.length == 0) {
            throw new NoSuchElementException("Empty, no element can be randomly selected");
        }
        applyReplayedRngGene("randomElementArray");
        recordRngGene("randomElementArray");
        return array[random.nextInt(array.length)];
    }

    public static long getCurrentSeed() {
        try {
            return ((AtomicLong) SEED_FIELD.get(random)).get();
        } catch (ReflectiveOperationException roe) {
            throw new Error("Can't get seed: " + roe, roe);
        }
    }

    public static void setCurrentSeed(long seed) {
        try {
            AtomicLong seedObject = (AtomicLong) SEED_FIELD.get(random);
            seedObject.set(seed);
        } catch (ReflectiveOperationException roe) {
            throw new Error("Can't set seed: " + roe, roe);
        }
    }

    private static void recordRngGene(String rngOpName) {
        if (random == null || !Genome.isEventGeneRecordingEnabled()) {
            return;
        }
        Genome.recordRngGene(rngOpName, getCurrentSeed());
    }

    private static void applyReplayedRngGene(String rngOpName) {
        if (random == null) {
            return;
        }
        Long replayGene = Genome.consumeRngGene(rngOpName, getCurrentSeed());
        if (replayGene != null) {
            setCurrentSeed(replayGene);
        }
    }
}
