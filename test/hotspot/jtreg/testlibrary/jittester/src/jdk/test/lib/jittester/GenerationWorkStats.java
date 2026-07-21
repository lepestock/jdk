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
import java.util.stream.Collectors;

public final class GenerationWorkStats {
    private static boolean enabled;
    private static long factoryCreationPoints;
    private static long builderProduces;
    private static final Map<String, Long> factoryCreationPointCounts = new HashMap<>();
    private static final Map<String, Long> builderProduceCounts = new HashMap<>();

    private GenerationWorkStats() {
    }

    public static final class Snapshot {
        private final long factoryCreationPoints;
        private final long builderProduces;

        private Snapshot(long factoryCreationPoints, long builderProduces) {
            this.factoryCreationPoints = factoryCreationPoints;
            this.builderProduces = builderProduces;
        }
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static void reset() {
        factoryCreationPoints = 0;
        builderProduces = 0;
        factoryCreationPointCounts.clear();
        builderProduceCounts.clear();
    }

    public static void factoryCreationPoint(String name) {
        if (!enabled) {
            return;
        }
        factoryCreationPoints++;
        factoryCreationPointCounts.merge(name, 1L, Long::sum);
    }

    public static void builderProduce(String name) {
        if (!enabled) {
            return;
        }
        builderProduces++;
        builderProduceCounts.merge(name, 1L, Long::sum);
    }

    public static Snapshot snapshot() {
        return new Snapshot(factoryCreationPoints, builderProduces);
    }

    public static void printCatchDelta(String site, Snapshot before, Throwable throwable) {
        if (!enabled) {
            return;
        }
        long factoryDelta = factoryCreationPoints - before.factoryCreationPoints;
        long builderProduceDelta = builderProduces - before.builderProduces;
        if (factoryDelta == 0 && builderProduceDelta == 0) {
            return;
        }
        System.err.println("[JTDBG][WorkDelta] site=" + site
                + " exception=" + throwable.getClass().getSimpleName()
                + " factoryCreationPointsDelta=" + factoryDelta
                + " builderProducesDelta=" + builderProduceDelta
                + " factoryCreationPointsTotal=" + factoryCreationPoints
                + " builderProducesTotal=" + builderProduces);
    }

    public static void printSummary() {
        if (!enabled) {
            return;
        }
        System.err.println("[JTDBG][Work] factoryCreationPoints=" + factoryCreationPoints
                + " builderProduces=" + builderProduces);
        System.err.println("[JTDBG][Work] factoryCreationPointTop="
                + formatTop(factoryCreationPointCounts, 12));
        System.err.println("[JTDBG][Work] builderProduceTop="
                + formatTop(builderProduceCounts, 12));
    }

    private static String formatTop(Map<String, Long> values, int limit) {
        return values.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(limit)
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", "));
    }
}
