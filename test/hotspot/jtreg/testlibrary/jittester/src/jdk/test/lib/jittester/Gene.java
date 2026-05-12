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

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * Block-level genome unit used for block RNG replay and guided mutations.
 */
public final class Gene {
    private final long blockSeed;
    private final LongSmallSet statementOverrides;

    public Gene(long blockSeed) {
        this(blockSeed, new LongSmallSet());
    }

    public Gene(long blockSeed, List<Long> statementDecisionSeeds) {
        this(blockSeed, toLongSmallSet(statementDecisionSeeds));
    }

    public Gene(long blockSeed, LongSmallSet statementOverrides) {
        this.blockSeed = blockSeed;
        this.statementOverrides = statementOverrides;
    }

    public long blockSeed() {
        return blockSeed;
    }

    public List<Long> statementDecisionSeeds() {
        List<Long> values = new ArrayList<>(statementOverrides.size);
        for (int i = 0; i < statementOverrides.size; i++) {
            long value = statementOverrides.values[i];
            values.add(value == LongSmallSet.NOT_OVERRIDDEN ? null : value);
        }
        return values;
    }

    public LongSmallSet statementOverrides() {
        return statementOverrides;
    }

    public boolean hasStatementDecisionSeeds() {
        return !statementOverrides.isEmpty();
    }

    public boolean hasStatementDecisionOverrides() {
        return statementOverrides.hasOverrides();
    }

    public String toToken() {
        if (statementOverrides.isEmpty()) {
            return "B" + Long.toString(blockSeed);
        }
        StringJoiner joiner = new StringJoiner(":");
        joiner.add("B" + Long.toString(blockSeed));
        for (int i = 0; i < statementOverrides.size; i++) {
            long value = statementOverrides.values[i];
            joiner.add(value == LongSmallSet.NOT_OVERRIDDEN ? "" : Long.toString(value));
        }
        return joiner.toString();
    }

    public static Gene parseToken(String token) {
        return parseString(token);
    }

    public static Gene parseString(String token) {
        String[] segments = token.split(":", -1);
        if (segments.length == 0 || segments[0].isBlank()) {
            throw new NumberFormatException("Missing block gene in token: " + token);
        }
        String blockPart = segments[0];
        if (blockPart.startsWith("B")) {
            blockPart = blockPart.substring(1);
        }
        long parsedBlockSeed = Long.parseLong(blockPart);
        return new Gene(parsedBlockSeed, parseOverridesSuffix(segments));
    }

    private static LongSmallSet parseOverridesSuffix(String[] segments) {
        if (segments.length <= 1) {
            return new LongSmallSet();
        }
        long[] values = new long[segments.length - 1];
        for (int i = 1; i < segments.length; i++) {
            String segment = segments[i];
            values[i - 1] = segment.isBlank()
                    ? LongSmallSet.NOT_OVERRIDDEN
                    : Long.parseLong(segment);
        }
        return new LongSmallSet(values);
    }

    private static LongSmallSet toLongSmallSet(List<Long> statementDecisionSeeds) {
        long[] values = new long[statementDecisionSeeds.size()];
        for (int i = 0; i < statementDecisionSeeds.size(); i++) {
            Long value = statementDecisionSeeds.get(i);
            values[i] = value == null ? LongSmallSet.NOT_OVERRIDDEN : value;
        }
        return new LongSmallSet(values);
    }
}
