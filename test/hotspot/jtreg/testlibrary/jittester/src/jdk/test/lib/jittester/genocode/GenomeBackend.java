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

package jdk.test.lib.jittester.genocode;

import jdk.test.lib.jittester.Block;
import jdk.test.lib.jittester.Gene;
import jdk.test.lib.jittester.FlowParams;
import jdk.test.lib.jittester.LongSmallSet;

/**
 * Pluggable genocode for block-level genome record/replay systems.
 * A genocode is a genome record/replay backend that defines how genome files are read/written and how replay decisions are applied.
 */
public interface GenomeBackend {
    void initialize(String replayFile, String recordFile, Long mutationSeed, String mutationTarget);

    default long startBlock(long liveSeed) {
        return startBlock(liveSeed, null);
    }

    long startBlock(long liveSeed, FlowParams flowParamsAdvance);

    void recordCurrentBlockGene(Gene gene);

    Long getCurrentStatementSeedOverride(int statementIndex);

    LongSmallSet getCurrentStatementSeedOverrides();

    int getCurrentStatementSeedOverrideCount();

    boolean isCurrentMutationRootBlock();

    default boolean isReplayActive() {
        return false;
    }

    default boolean isReplayMutationScopeActive() {
        return false;
    }

    /**
     * Returns the number of direct child scopes recorded for the current replay scope,
     * or {@code -1} when not available.
     */
    default int getCurrentReplayChildCount() {
        return -1;
    }

    default boolean isEventGeneRecordingEnabled() {
        return false;
    }

    /**
     * Whether source-level block gene comments should be rendered.
     */
    default boolean isSourceDebugEnabled() {
        return false;
    }

    /**
     * Formats block-gene debug token for source comments.
     */
    default String formatBlockDebugToken(Block block) {
        return "";
    }

    default void recordRuleGene(String ruleName, long geneValue) {
    }

    default void recordChoiceGene(String ruleName, long geneValue) {
    }

    default void recordRngGene(String rngOpName, long geneValue) {
    }

    default void recordMagnetGene(String channel, long geneValue) {
    }

    default void recordMagnetTargetChoice(String channel, long geneValue) {
    }

    default Long consumeRuleGene(String ruleName, long liveGeneValue) {
        return null;
    }

    default Long consumeChoiceGene(String ruleName, long liveGeneValue) {
        return null;
    }

    default Long consumeRngGene(String rngOpName, long liveGeneValue) {
        return null;
    }

    default Long consumeMagnetGene(String channel, long liveGeneValue) {
        return null;
    }

    default Long consumeMagnetTargetChoice(String channel, long liveGeneValue) {
        return null;
    }

    default long startScope(char scopeType, long liveSeed) {
        return liveSeed;
    }

    default void recordCurrentScopeGene(char scopeType, long scopeSeed) {
    }

    default void endScope(char scopeType) {
    }

    default void setReplayStrict(boolean strict) {
    }

    default void beginSpeculativeRecord() {
    }

    default void commitSpeculativeRecord() {
    }

    default void rollbackSpeculativeRecord() {
    }

    void endBlock();

    void close();
}
