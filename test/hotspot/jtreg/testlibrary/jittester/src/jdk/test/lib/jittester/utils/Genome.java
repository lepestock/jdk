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

import java.util.ArrayDeque;
import java.util.Deque;
import jdk.test.lib.jittester.Block;
import jdk.test.lib.jittester.Gene;
import jdk.test.lib.jittester.LongSmallSet;
import jdk.test.lib.jittester.genocode.GenomeBackend;
import jdk.test.lib.jittester.genocode.full.FullGenocode;

/**
 * Facade for pluggable genome genocodes.
 */
public final class Genome {
    private static GenomeBackend genocode = new FullGenocode();
    private static final ThreadLocal<Deque<Long>> expressionScopeSeedStack =
            ThreadLocal.withInitial(ArrayDeque::new);

    private Genome() {
    }

    public static synchronized void setGenocode(GenomeBackend newGenocode) {
        if (newGenocode == null) {
            throw new IllegalArgumentException("Genome backend must not be null");
        }
        genocode.close();
        genocode = newGenocode;
    }

    public static synchronized void useDefaultGenocode() {
        setGenocode(new FullGenocode());
    }

    public static synchronized void initialize(String replayFile, String recordFile,
            Long mutationSeed, String mutationTarget) {
        genocode.initialize(replayFile, recordFile, mutationSeed, mutationTarget);
    }

    public static synchronized long startBlock(long liveSeed) {
        return genocode.startBlock(liveSeed);
    }

    public static synchronized void recordCurrentBlockGene(Gene gene) {
        genocode.recordCurrentBlockGene(gene);
    }

    public static synchronized Long getCurrentStatementSeedOverride(int statementIndex) {
        return genocode.getCurrentStatementSeedOverride(statementIndex);
    }

    public static synchronized LongSmallSet getCurrentStatementSeedOverrides() {
        return genocode.getCurrentStatementSeedOverrides();
    }

    public static synchronized int getCurrentStatementSeedOverrideCount() {
        return genocode.getCurrentStatementSeedOverrideCount();
    }

    public static synchronized boolean isCurrentMutationRootBlock() {
        return genocode.isCurrentMutationRootBlock();
    }

    public static synchronized boolean isReplayActive() {
        return genocode.isReplayActive();
    }

    public static synchronized boolean isReplayMutationScopeActive() {
        return genocode.isReplayMutationScopeActive();
    }

    public static synchronized int getCurrentReplayChildCount() {
        return genocode.getCurrentReplayChildCount();
    }

    public static synchronized boolean isEventGeneRecordingEnabled() {
        return genocode.isEventGeneRecordingEnabled();
    }

    public static synchronized boolean isSourceDebugEnabled() {
        return genocode.isSourceDebugEnabled();
    }

    public static synchronized String formatBlockDebugToken(Block block) {
        return genocode.formatBlockDebugToken(block);
    }

    public static synchronized void recordRuleGene(String ruleName, long geneValue) {
        genocode.recordRuleGene(ruleName, geneValue);
    }

    /**
     * Creates a new rule gene in record mode, or consumes an existing one in replay mode.
     * Replay path does not touch live RNG.
     */
    public static synchronized long createOrConsumeRuleGene(String ruleName) {
        if (genocode.isReplayActive()) {
            Long replayGene = genocode.consumeRuleGene(ruleName, 0L);
            if (replayGene == null) {
                throw new RuntimeException("Genome replay desync: missing rule event for rule '"
                        + ruleName + "'");
            }
            return replayGene;
        }
        long liveRuleGene = PseudoRandom.getCurrentSeed();
        genocode.recordRuleGene(ruleName, liveRuleGene);
        return liveRuleGene;
    }

    public static synchronized void recordChoiceGene(String ruleName, long geneValue) {
        genocode.recordChoiceGene(ruleName, geneValue);
    }

    public static synchronized void recordRngGene(String rngOpName, long geneValue) {
        genocode.recordRngGene(rngOpName, geneValue);
    }

    public static synchronized void recordMagnetGene(String channel, long geneValue) {
        genocode.recordMagnetGene(channel, geneValue);
    }

    public static synchronized void recordMagnetTargetGene(String channel, long geneValue) {
        genocode.recordMagnetTargetChoice(channel, geneValue);
    }

    public static Long consumeRuleGene(String ruleName, long liveGeneValue) {
        return genocode.consumeRuleGene(ruleName, liveGeneValue);
    }

    public static Long consumeChoiceGene(String ruleName, long liveGeneValue) {
        return genocode.consumeChoiceGene(ruleName, liveGeneValue);
    }

    /**
     * Records a boolean choice as a C-event: false -> C0, true -> C1.
     */
    public static synchronized void recordBooleanChoiceGene(String choiceName, boolean value) {
        recordChoiceGene(choiceName, value ? 1L : 0L);
    }

    /**
     * Consumes a replayed boolean C-event in replay mode, or records the live value in record mode.
     * In replay mode only C0/C1 are accepted.
     */
    public static synchronized boolean createOrConsumeBooleanChoiceGene(String choiceName,
            boolean liveValue) {
        if (genocode.isReplayActive()) {
            Long replayChoice = consumeChoiceGene(choiceName, liveValue ? 1L : 0L);
            if (replayChoice == null) {
                throw new RuntimeException("Genome replay desync: missing boolean choice event for '"
                        + choiceName + "'");
            }
            if (replayChoice == 0L) {
                return false;
            }
            if (replayChoice == 1L) {
                return true;
            }
            throw new RuntimeException("Genome replay desync: boolean choice '" + choiceName
                    + "' must be 0/1, got " + replayChoice);
        }
        recordBooleanChoiceGene(choiceName, liveValue);
        return liveValue;
    }

    public static Long consumeRngGene(String rngOpName, long liveGeneValue) {
        return genocode.consumeRngGene(rngOpName, liveGeneValue);
    }

    public static Long consumeMagnetGene(String channel, long liveGeneValue) {
        return genocode.consumeMagnetGene(channel, liveGeneValue);
    }

    public static Long consumeMagnetTargetGene(String channel, long liveGeneValue) {
        return genocode.consumeMagnetTargetChoice(channel, liveGeneValue);
    }

    /**
     * Creates a new symbol-magnet gene in record mode, or consumes an existing one in replay mode.
     * Magnet IDs are tracked with dedicated M-events to make desync and collision analysis explicit.
     */
    public static synchronized long createOrConsumeMagnetGene(String channel) {
        if (genocode.isReplayActive()) {
            Long replayGene = genocode.consumeMagnetGene(channel, 0L);
            assertHard(replayGene != null,
                    "Genome replay desync: missing magnet event for channel '" + channel + "'");
            return replayGene;
        }
        long liveMagnetGene = PseudoRandom.nextLongSilent();
        genocode.recordMagnetGene(channel, liveMagnetGene);
        return liveMagnetGene;
    }

    public static long startScope(char scopeType, long liveSeed) {
        return genocode.startScope(scopeType, liveSeed);
    }

    public static void recordCurrentScopeGene(char scopeType, long scopeSeed) {
        genocode.recordCurrentScopeGene(scopeType, scopeSeed);
    }

    public static void endScope(char scopeType) {
        genocode.endScope(scopeType);
    }

    public static long startExpressionScope(long liveSeed) {
        long scopeSeed = startScope('E', liveSeed);
        expressionScopeSeedStack.get().push(scopeSeed);
        return scopeSeed;
    }

    public static void recordCurrentExpressionGene(long scopeSeed) {
        recordCurrentScopeGene('E', scopeSeed);
    }

    public static void endExpressionScope() {
        endScope('E');
        Deque<Long> stack = expressionScopeSeedStack.get();
        if (!stack.isEmpty()) {
            stack.pop();
        }
    }

    public static Long getCurrentExpressionScopeSeed() {
        Deque<Long> stack = expressionScopeSeedStack.get();
        return stack.isEmpty() ? null : stack.peek();
    }

    public static void setReplayStrict(boolean strict) {
        genocode.setReplayStrict(strict);
    }

    public static synchronized void beginSpeculativeRecord() {
        assertNoReplay("beginSpeculativeRecord");
        genocode.beginSpeculativeRecord();
    }

    public static synchronized void commitSpeculativeRecord() {
        assertNoReplay("commitSpeculativeRecord");
        genocode.commitSpeculativeRecord();
    }

    public static synchronized void rollbackSpeculativeRecord() {
        assertNoReplay("rollbackSpeculativeRecord");
        genocode.rollbackSpeculativeRecord();
    }

    public static synchronized void endBlock() {
        genocode.endBlock();
    }

    public static synchronized void close() {
        genocode.close();
    }

    /**
     * Speculative record API is record-only. Calling it during replay is a hard error,
     * because replay must consume committed choices without speculative retries.
     */
    public static synchronized void assertNoReplay(String operation) {
        if (!genocode.isReplayActive()) {
            return;
        }
        String message = "Internal error: speculative API '" + operation
                + "' used during genome replay";
        RuntimeException debug = new RuntimeException(message);
        System.err.println("[JITTESTER][GENOCODE] " + message);
        debug.printStackTrace(System.err);
        throw new InternalError(message, debug);
    }

    private static void assertHard(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
