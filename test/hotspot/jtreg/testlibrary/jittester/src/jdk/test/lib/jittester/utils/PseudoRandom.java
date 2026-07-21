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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/**
 * This class is used for any random generation operations.
 */
public class PseudoRandom {

    private static Random random = null;
    private static final Field SEED_FIELD;
    private static boolean timingEnabled = false;
    private static final Map<String, TimingStat> TIMING_STATS = new LinkedHashMap<>();

    static {
        try {
            SEED_FIELD = Random.class.getDeclaredField("seed");
            SEED_FIELD.setAccessible(true);
        } catch (ReflectiveOperationException roe) {
            throw new Error("Can't get seed field: " + roe, roe);
        }
    }

    public static void reset(String seed) {
        if (seed == null || seed.length() == 0) {
            seed = String.valueOf(System.currentTimeMillis());
        }
        random = new java.util.Random(seed.hashCode());
        resetTiming();
    }

    public static void setTimingEnabled(boolean enabled) {
        timingEnabled = enabled;
        resetTiming();
    }

    public static void printTimingSummary() {
        if (!timingEnabled) {
            return;
        }
        long totalCount = 0;
        long totalNanos = 0;
        synchronized (TIMING_STATS) {
            for (TimingStat stat : TIMING_STATS.values()) {
                totalCount += stat.count;
                totalNanos += stat.nanos;
            }
            System.err.printf("[JITTESTER][RNG] total calls=%d time=%.3f ms%n",
                    totalCount, totalNanos / 1_000_000.0);
            for (Map.Entry<String, TimingStat> entry : TIMING_STATS.entrySet()) {
                TimingStat stat = entry.getValue();
                double millis = stat.nanos / 1_000_000.0;
                double avgNanos = stat.count == 0 ? 0.0 : (double) stat.nanos / stat.count;
                System.err.printf("[JITTESTER][RNG] %-24s calls=%9d time=%10.3f ms avg=%8.1f ns%n",
                        entry.getKey(), stat.count, millis, avgNanos);
            }
        }
    }

    public static double random() {
        long start = startTiming();
        try {
            applyReplayedRngGene("random");
            recordRngGene("random");
            return random.nextDouble();
        } finally {
            recordTiming("random", start);
        }
    }

    /**
     * Consumes RNG state without recording/consuming a genome RNG event.
     * Intended for internal selection logic where the final choice is recorded explicitly.
     */
    public static double randomSilent() {
        long start = startTiming();
        try {
            return random.nextDouble();
        } finally {
            recordTiming("randomSilent", start);
        }
    }

    public static long nextLong() {
        long start = startTiming();
        try {
            applyReplayedRngGene("nextLong");
            recordRngGene("nextLong");
            return random.nextLong();
        } finally {
            recordTiming("nextLong", start);
        }
    }

    /**
     * Consumes RNG state without recording/consuming a genome RNG event.
     * Intended for cases where the decision is tracked via a dedicated non-N event category.
     */
    public static long nextLongSilent() {
        long start = startTiming();
        try {
            return random.nextLong();
        } finally {
            recordTiming("nextLongSilent", start);
        }
    }

    public static long nextLong(long lo, long hi) {
        long start = startTiming();
        try {
            applyReplayedRngGene("nextLongRange");
            recordRngGene("nextLongRange");
            return random.nextLong(lo, hi);
        } finally {
            recordTiming("nextLongRange", start);
        }
    }

    public static int nextInt() {
        long start = startTiming();
        try {
            applyReplayedRngGene("nextInt");
            recordRngGene("nextInt");
            return random.nextInt();
        } finally {
            recordTiming("nextInt", start);
        }
    }

    public static int nextInt(int lo, int hi) {
        long start = startTiming();
        try {
            applyReplayedRngGene("nextIntRange");
            recordRngGene("nextIntRange");
            return random.nextInt(lo, hi);
        } finally {
            recordTiming("nextIntRange", start);
        }
    }

    // uniformly distributed boolean
    public static boolean randomBoolean() {
        long start = startTiming();
        try {
            applyReplayedRngGene("randomBoolean");
            recordRngGene("randomBoolean");
            return random.nextBoolean();
        } finally {
            recordTiming("randomBoolean", start);
        }
    }

    // non-uniformly distributed boolean. 0 probability - never true, 1 - always true
    public static boolean randomBoolean(double probability) {
        long start = startTiming();
        try {
            applyReplayedRngGene("randomBooleanProb");
            recordRngGene("randomBooleanProb");
            return random.nextDouble() < probability;
        } finally {
            recordTiming("randomBooleanProb", start);
        }
    }

    public static long randomNotZero(long limit) {
        long start = startTiming();
        try {
            applyReplayedRngGene("randomNotZeroLong");
            recordRngGene("randomNotZeroLong");
            long result = (long) (limit * random.nextDouble());
            return result > 0L ? result : 1L;
        } finally {
            recordTiming("randomNotZeroLong", start);
        }
    }

    public static int randomNotZero(int limit) {
        long start = startTiming();
        try {
            applyReplayedRngGene("randomNotZeroInt");
            recordRngGene("randomNotZeroInt");
            int result = (int) (limit * random.nextDouble());
            return result > 0 ? result : 1;
        } finally {
            recordTiming("randomNotZeroInt", start);
        }
    }

    public static void shuffle(List<?> list) {
        long start = startTiming();
        try {
            applyReplayedRngGene("shuffle");
            recordRngGene("shuffle");
            Collections.shuffle(list, random);
        } finally {
            recordTiming("shuffle", start);
        }
    }

    /**
     * Shuffle without replay/record event gene interaction.
     * Uses current RNG state only.
     */
    public static void shuffleSilent(List<?> list) {
        long start = startTiming();
        try {
            Collections.shuffle(list, random);
        } finally {
            recordTiming("shuffleSilent", start);
        }
    }

    public static int randomNotNegative(int limit) {
        long start = startTiming();
        try {
            applyReplayedRngGene("randomNotNegative");
            recordRngGene("randomNotNegative");
            int result = (int) (limit * random.nextDouble());
            return Math.abs(result);
        } finally {
            recordTiming("randomNotNegative", start);
        }
    }

    public static <T> T randomElement(Collection<T> collection) {
        if (collection.isEmpty()) {
            throw new NoSuchElementException("Empty, no element can be randomly selected");
        }
        if (collection instanceof List) {
            return randomElement((List<T>) collection);
        } else {
            long start = startTiming();
            try {
                applyReplayedRngGene("randomElementCollection");
                recordRngGene("randomElementCollection");
                int ix = random.nextInt(collection.size());
                final Iterator<T> iterator = collection.iterator();
                while (ix > 0) {
                    ix--;
                    iterator.next();
                }
                return iterator.next();
            } finally {
                recordTiming("randomElementCollection", start);
            }
        }
    }

    public static <T> T randomElement(List<T> list) {
        if (list.isEmpty()) {
            throw new NoSuchElementException("Empty, no element can be randomly selected");
        }
        long start = startTiming();
        try {
            applyReplayedRngGene("randomElementList");
            recordRngGene("randomElementList");
            return list.get(random.nextInt(list.size()));
        } finally {
            recordTiming("randomElementList", start);
        }
    }

    public static <T> T randomElement(T[] array) {
        if (array.length == 0) {
            throw new NoSuchElementException("Empty, no element can be randomly selected");
        }
        long start = startTiming();
        try {
            applyReplayedRngGene("randomElementArray");
            recordRngGene("randomElementArray");
            return array[random.nextInt(array.length)];
        } finally {
            recordTiming("randomElementArray", start);
        }
    }

    public static long getCurrentSeed() {
        long start = startTiming();
        try {
            return ((AtomicLong) SEED_FIELD.get(random)).get();
        } catch (ReflectiveOperationException roe) {
            throw new Error("Can't get seed: " + roe, roe);
        } finally {
            recordTiming("getCurrentSeed", start);
        }
    }

    public static void setCurrentSeed(long seed) {
        long start = startTiming();
        try {
            AtomicLong seedObject = (AtomicLong) SEED_FIELD.get(random);
            seedObject.set(seed);
        } catch (ReflectiveOperationException roe) {
            throw new Error("Can't set seed: " + roe, roe);
        } finally {
            recordTiming("setCurrentSeed", start);
        }
    }

    private static void recordRngGene(String rngOpName) {
        if (random == null || !Genome.isEventGeneRecordingEnabled()) {
            return;
        }
        Genome.recordRngGene(rngOpName, getCurrentSeed());
    }

    private static void applyReplayedRngGene(String rngOpName) {
        if (random == null || !Genome.isReplayActive()) {
            return;
        }
        Long replayGene = Genome.consumeRngGene(rngOpName, getCurrentSeed());
        if (replayGene != null) {
            setCurrentSeed(replayGene);
        }
    }

    private static long startTiming() {
        return timingEnabled ? System.nanoTime() : 0L;
    }

    private static void recordTiming(String operation, long start) {
        if (!timingEnabled) {
            return;
        }
        long elapsed = System.nanoTime() - start;
        synchronized (TIMING_STATS) {
            TimingStat stat = TIMING_STATS.computeIfAbsent(operation, _unused -> new TimingStat());
            stat.count++;
            stat.nanos += elapsed;
        }
    }

    private static void resetTiming() {
        synchronized (TIMING_STATS) {
            TIMING_STATS.clear();
        }
    }

    private static final class TimingStat {
        private long count;
        private long nanos;
    }
}
