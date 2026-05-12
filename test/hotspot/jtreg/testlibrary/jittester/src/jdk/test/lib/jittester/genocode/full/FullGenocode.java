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

package jdk.test.lib.jittester.genocode.full;

import jdk.test.lib.jittester.CheckpointArrayList;
import jdk.test.lib.jittester.Gene;
import jdk.test.lib.jittester.GenerationState;
import jdk.test.lib.jittester.LongSmallSet;
import jdk.test.lib.jittester.SymbolTable;
import jdk.test.lib.jittester.TypeList;
import jdk.test.lib.jittester.genocode.GenomeBackend;
import jdk.test.lib.jittester.utils.PseudoRandom;
import jdk.test.lib.jittester.Block;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Genocode that replays/records block genes and additionally records rule/RNG event genes.
 * Statement-level overrides are intentionally ignored in this genocode.
 */
public final class FullGenocode implements GenomeBackend {
    private static final String GENOCODE_VERSION = "full-1.3";
    private static final String PREVIOUS_GENOCODE_VERSION = "full-1.2";
    private static final int MAX_EVENT_LINE_WIDTH = 120;

    private List<ReplayNode> replayRoots = Collections.emptyList();
    private int nextRootIndex = 0;
    private List<ReplayEvent> replayRootEvents = Collections.emptyList();
    private int nextRootEventIndex = 0;
    private final Deque<ReplayFrame> replayStack = new ArrayDeque<>();
    private BufferedWriter recordWriter = null;
    private BufferedWriter blocksWriter = null;
    private StringWriter recordBufferWriter = null;
    private StringWriter blocksBufferWriter = null;
    private int recordDepth = 0;
    private int blocksDepth = 0;
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\(|\\)|[^\\s()]+");
    private static final Pattern GENOCODE_VERSION_HEADER_PATTERN =
            Pattern.compile("^\\(genocode_version\\s+([^\\s()]+)\\)$");
    private static final Pattern GLOBAL_SEED_HEADER_PATTERN =
            Pattern.compile("^\\(global_seed\\s+(-?\\d+)\\)$");
    private Long mutationSeedOverride = null;
    private Long mutationTargetBlockSeed = null;
    private Path recordFilePath = null;
    private Path snapshotFilePath = null;
    private Path blocksFilePath = null;
    private BufferedWriter snapshotWriter = null;
    private Set<String> snapshotTokens = Collections.emptySet();
    private Set<String> snapshotPhases = Collections.emptySet();
    private boolean snapshotIncludeSymbolTable = true;
    private boolean snapshotIncludeTypeList = false;
    private int snapshotSymbolTableMaxTypes = 24;
    private int snapshotSymbolTableMaxSymbolsPerType = 16;
    private int snapshotTypeListMaxTypes = 48;
    // Pretty writer state: groups consecutive N-tokens into wrapped lines at current depth.
    private StringBuilder pendingNLine = new StringBuilder();
    private int pendingNDepth = -1;
    private boolean replayStrict = true;
    private boolean replayModeActive = false;
    private boolean replaySupportsStatementScopes = false;
    private String replayTraceToken = null;
    private Set<String> replayTraceTokens = Collections.emptySet();
    private boolean replayTraceCrash = false;
    private Set<String> replayTraceSymbolTableTokens = Collections.emptySet();
    private int replayTraceSymbolTableMaxTypes = 24;
    private int replayTraceSymbolTableMaxSymbolsPerType = 16;
    private Set<String> replayTraceTypeListTokens = Collections.emptySet();
    private int replayTraceTypeListMaxTypes = 48;
    private final CheckpointArrayList<String> speculativeRecordChunks = new CheckpointArrayList<>();
    private final CheckpointArrayList<String> speculativeBlocksChunks = new CheckpointArrayList<>();
    private final Deque<SpeculativeCheckpoint> speculativeCheckpoints = new ArrayDeque<>();

    @Override
    public synchronized void initialize(String replayFile, String recordFile,
            Long mutationSeed, String mutationTarget) {
        closeWriterQuietly();
        boolean replayMode = replayFile != null && !replayFile.isBlank();
        replayModeActive = replayMode;
        ReplayData replayData = loadReplayData(replayFile);
        replayRoots = replayData.roots;
        nextRootIndex = 0;
        replayRootEvents = replayData.rootEvents;
        nextRootEventIndex = 0;
        replayStack.clear();
        mutationSeedOverride = mutationSeed;
        mutationTargetBlockSeed = parseMutationTargetBlockSeed(mutationTarget);
        if (replayMode && replayData.globalSeed == null) {
            throw new RuntimeException("Replay genome must start with global seed header: "
                    + "(global_seed <seed>)");
        }
        if (replayMode && replayData.genocodeVersion == null) {
            throw new RuntimeException("Replay genome must start with version header: "
                    + "(genocode_version " + GENOCODE_VERSION + ")");
        }
        if (replayMode && !isSupportedReplayVersion(replayData.genocodeVersion)) {
            throw new RuntimeException("Replay genome version mismatch: expected "
                    + GENOCODE_VERSION + " (or " + PREVIOUS_GENOCODE_VERSION + ")"
                    + ", got " + replayData.genocodeVersion);
        }
        replaySupportsStatementScopes = GENOCODE_VERSION.equals(replayData.genocodeVersion);
        replayTraceToken = normalizeTraceToken(System.getProperty("jittester.replay.trace.token"));
        replayTraceTokens = parseTraceTokenSet(System.getProperty("jittester.replay.trace.tokens"));
        replayTraceCrash = Boolean.getBoolean("jittester.replay.trace.crash");
        replayTraceSymbolTableTokens =
                parseTraceTokenSet(System.getProperty("jittester.replay.trace.symboltable.tokens"));
        replayTraceSymbolTableMaxTypes = Integer.getInteger(
                "jittester.replay.trace.symboltable.maxTypes", 24);
        replayTraceSymbolTableMaxSymbolsPerType = Integer.getInteger(
                "jittester.replay.trace.symboltable.maxSymbolsPerType", 16);
        replayTraceTypeListTokens =
                parseTraceTokenSet(System.getProperty("jittester.replay.trace.typelist.tokens"));
        replayTraceTypeListMaxTypes = Integer.getInteger(
                "jittester.replay.trace.typelist.maxTypes", 48);
        snapshotTokens = parseTraceTokenSet(System.getProperty("jittester.replay.snapshot.tokens"));
        snapshotPhases = parseTraceTokenSet(System.getProperty("jittester.replay.snapshot.phases"));
        snapshotIncludeSymbolTable = Boolean.parseBoolean(
                System.getProperty("jittester.replay.snapshot.include.symboltable", "true"));
        snapshotIncludeTypeList = Boolean.parseBoolean(
                System.getProperty("jittester.replay.snapshot.include.typelist", "false"));
        snapshotSymbolTableMaxTypes = Integer.getInteger(
                "jittester.replay.snapshot.symboltable.maxTypes", 24);
        snapshotSymbolTableMaxSymbolsPerType = Integer.getInteger(
                "jittester.replay.snapshot.symboltable.maxSymbolsPerType", 16);
        snapshotTypeListMaxTypes = Integer.getInteger(
                "jittester.replay.snapshot.typelist.maxTypes", 48);
        String snapshotFile = System.getProperty("jittester.replay.snapshot.file");
        snapshotFilePath = (snapshotFile == null || snapshotFile.isBlank())
                ? null : Paths.get(snapshotFile.trim());
        if (snapshotFilePath != null) {
            ensureParentDirs(snapshotFilePath);
            try {
                snapshotWriter = Files.newBufferedWriter(snapshotFilePath,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE);
            } catch (IOException e) {
                throw new RuntimeException("Failed to open replay snapshot file: " + snapshotFilePath, e);
            }
        }
        if (replayData.globalSeed != null) {
            PseudoRandom.setCurrentSeed(replayData.globalSeed);
        }
        recordFilePath = (recordFile == null || recordFile.isBlank()) ? null : Paths.get(recordFile);
        if (recordFilePath != null) {
            ensureParentDirs(recordFilePath);
        }
        recordWriter = openMemoryWriter(false);
        blocksFilePath = toBlocksPath(recordFilePath);
        if (blocksFilePath != null) {
            ensureParentDirs(blocksFilePath);
            blocksWriter = openMemoryWriter(true);
        } else {
            blocksWriter = null;
        }
        recordDepth = 0;
        blocksDepth = 0;
        resetPendingNLine();
        writeGlobalSeedHeader();
    }

    @Override
    public synchronized long startBlock(long liveSeed) {
        return startScope('B', liveSeed);
    }

    @Override
    public synchronized void recordCurrentBlockGene(Gene gene) {
        if (recordWriter == null) {
            return;
        }
        ReplayFrame frame = replayStack.peek();
        if (frame == null) {
            throw new RuntimeException("Block RNG replay/record stack is empty");
        }
        if (frame.recorded) {
            return;
        }
        // This genocode intentionally stores only block seed (no statement override suffix).
        recordScopeStart('B', new Gene(gene.blockSeed()).toToken(), true);
        frame.recorded = true;
    }

    @Override
    public synchronized long startScope(char scopeType, long liveSeed) {
        boolean mutableByParent = !replayStack.isEmpty() && replayStack.peek().mutable;
        if (mutableByParent) {
            replayStack.push(ReplayFrame.mutableFrame(scopeType, liveSeed));
            return liveSeed;
        }

        ReplayNode replayNode = nextReplayNode();
        boolean replayPresent = replayNode != null;
        if (!replayPresent && replayModeActive && replayStrict) {
            // Experimental strict mode: fail fast on replay node underflow to expose desync early.
            ReplayFrame parent = replayStack.peek();
            String parentInfo = "none";
            if (parent != null) {
                if (parent.node != null) {
                    parentInfo = "" + parent.scopeType + parent.node.seed
                            + " childIndex=" + parent.nextChildIndex
                            + "/" + parent.node.children.size();
                } else {
                    parentInfo = "" + parent.scopeType + "<mutable>";
                }
            }
            throw new RuntimeException("Genome replay desync: missing scope " + scopeType
                    + " node in replay stream (parent=" + parentInfo + ")");
        }
        if (replayPresent && replayNode.type != scopeType) {
            if (replayStrict) {
                // Experimental mode: strict replay is useful for debugging determinism and algorithm issues.
                // It may turn out excessive for routine mutation runs.
                throw new RuntimeException("Genome replay desync: expected scope " + scopeType
                        + ", got " + replayNode.type);
            }
            // Experimental mode: relaxed replay keeps generation running by skipping mismatched scope replay.
            // This may become unnecessary once scope-aware replay fully stabilizes.
            replayPresent = false;
            replayNode = null;
        }
        boolean mutable = replayPresent
                && mutationTargetBlockSeed != null
                && scopeType == 'B'
                && replayNode.seed == mutationTargetBlockSeed;
        long selectedSeed;
        if (!replayPresent) {
            selectedSeed = liveSeed;
        } else if (mutable) {
            selectedSeed = mutationSeedOverride != null
                    ? mutationSeedOverride
                    : nextMutationSeed();
        } else {
            selectedSeed = replayNode.seed;
        }
        if (replayPresent) {
            String token = Character.toString(scopeType) + replayNode.seed;
            maybeTraceToken(token, "startScope",
                    "selectedSeed=" + selectedSeed + ", mutable=" + mutable);
        }

        replayStack.push(new ReplayFrame(replayNode, mutable, scopeType, selectedSeed));
        return selectedSeed;
    }

    @Override
    public synchronized void recordCurrentScopeGene(char scopeType, long scopeSeed) {
        if (recordWriter == null) {
            return;
        }
        ReplayFrame frame = replayStack.peek();
        if (frame == null) {
            throw new RuntimeException("Scope replay/record stack is empty");
        }
        if (frame.recorded) {
            return;
        }
        if (frame.scopeType != scopeType) {
            throw new RuntimeException("Scope replay/record mismatch: expected " + frame.scopeType
                    + ", got " + scopeType);
        }
        boolean mirrorToBlocks = scopeType == 'B';
        recordScopeStart(scopeType, scopeType + Long.toString(scopeSeed), mirrorToBlocks);
        frame.recorded = true;
    }

    @Override
    public synchronized Long getCurrentStatementSeedOverride(int statementIndex) {
        return null;
    }

    @Override
    public synchronized LongSmallSet getCurrentStatementSeedOverrides() {
        return new LongSmallSet();
    }

    @Override
    public synchronized int getCurrentStatementSeedOverrideCount() {
        return 0;
    }

    @Override
    public synchronized boolean isCurrentMutationRootBlock() {
        if (replayStack.isEmpty()) {
            return false;
        }
        ReplayFrame frame = replayStack.peek();
        return frame != null && frame.mutable && frame.node != null
                && frame.scopeType == 'B'
                && mutationTargetBlockSeed != null
                && frame.node.seed == mutationTargetBlockSeed;
    }

    @Override
    public synchronized int getCurrentReplayChildCount() {
        if (!replaySupportsStatementScopes || replayStack.isEmpty()) {
            return -1;
        }
        ReplayFrame frame = replayStack.peek();
        if (frame == null || frame.node == null) {
            return -1;
        }
        int count = 0;
        for (ReplayNode child : frame.node.children) {
            if (child != null && child.type == 'S') {
                count++;
            }
        }
        return count;
    }

    @Override
    public synchronized boolean isReplayActive() {
        if (!replayModeActive) {
            return false;
        }
        if (replayStack.isEmpty()) {
            return true;
        }
        ReplayFrame frame = replayStack.peek();
        return frame == null || !frame.mutable;
    }

    @Override
    public synchronized boolean isReplayMutationScopeActive() {
        if (!replayModeActive || replayStack.isEmpty()) {
            return false;
        }
        ReplayFrame frame = replayStack.peek();
        return frame != null && frame.mutable;
    }

    @Override
    public synchronized boolean isEventGeneRecordingEnabled() {
        return recordWriter != null;
    }

    @Override
    public synchronized boolean isSourceDebugEnabled() {
        return replayModeActive || recordWriter != null;
    }

    @Override
    public synchronized String formatBlockDebugToken(Block block) {
        if (block == null || !block.hasBlockRngSeed()) {
            return "";
        }
        return "B" + Long.toString(block.getBlockRngSeed());
    }

    @Override
    public synchronized void recordRuleGene(String ruleName, long geneValue) {
        writeEventGene("R", geneValue);
    }

    @Override
    public synchronized void recordChoiceGene(String ruleName, long geneValue) {
        writeEventGene("C", geneValue);
    }

    @Override
    public synchronized void recordRngGene(String rngOpName, long geneValue) {
        writeEventGene("N", geneValue);
    }

    @Override
    public synchronized void recordMagnetGene(String channel, long geneValue) {
        writeEventGene("M", geneValue);
    }

    @Override
    public synchronized void recordMagnetTargetChoice(String channel, long geneValue) {
        writeEventGene("U", geneValue);
    }

    @Override
    public synchronized Long consumeRuleGene(String ruleName, long liveGeneValue) {
        Long value = consumeEventGene('R', liveGeneValue, ruleName);
        if (value == null) {
            throw new RuntimeException("Genome replay desync: missing rule event for rule '"
                    + ruleName + "'");
        }
        writeEventGene("R", value);
        return value;
    }

    @Override
    public synchronized Long consumeChoiceGene(String ruleName, long liveGeneValue) {
        Long value = consumeEventGene('C', liveGeneValue, ruleName);
        if (value != null) {
            writeEventGene("C", value);
        }
        return value;
    }

    @Override
    public synchronized Long consumeRngGene(String rngOpName, long liveGeneValue) {
        return consumeEventGene('N', liveGeneValue, rngOpName);
    }

    @Override
    public synchronized Long consumeMagnetGene(String channel, long liveGeneValue) {
        Long value = consumeEventGene('M', liveGeneValue, channel);
        if (value != null) {
            writeEventGene("M", value);
        }
        return value;
    }

    @Override
    public synchronized Long consumeMagnetTargetChoice(String channel, long liveGeneValue) {
        Long value = consumeEventGene('U', liveGeneValue, channel);
        if (value != null) {
            writeEventGene("U", value);
        }
        return value;
    }

    @Override
    public synchronized void setReplayStrict(boolean strict) {
        this.replayStrict = strict;
    }

    private Long consumeEventGene(char expectedType, long liveGeneValue, String debugName) {
        boolean optionalMagnetEvent = isOptionalMagnetRuleEvent(expectedType, debugName);
        boolean optionalFunctionMagnetTargetEvent = isOptionalFunctionMagnetTargetEvent(expectedType, debugName);
        if (replayStack.isEmpty()) {
            if (nextRootEventIndex >= replayRootEvents.size()) {
                if (optionalMagnetEvent || optionalFunctionMagnetTargetEvent) {
                    return null;
                }
                if (replayStrict && replayModeActive) {
                    throw new AssertionError("Genome replay desync: missing event "
                            + expectedType + " (" + debugName + ") at root");
                }
                return null;
            }
            ReplayEvent rootEvent = replayRootEvents.get(nextRootEventIndex);
            maybeTraceToken(Character.toString(rootEvent.type) + rootEvent.value,
                    "consumeEventGene", "expectedType=" + expectedType + ", debugName=" + debugName);
            if (rootEvent.type != expectedType) {
                if (optionalMagnetEvent || optionalFunctionMagnetTargetEvent) {
                    return null;
                }
                if (replayStrict) {
                    throw new AssertionError("Genome replay desync: expected root event "
                            + expectedType + " (" + debugName + "), got " + rootEvent.type);
                }
                nextRootEventIndex++;
                return null;
            }
            nextRootEventIndex++;
            return rootEvent.value;
        }
        ReplayFrame frame = replayStack.peek();
        if (frame == null || frame.mutable || frame.node == null) {
            return null;
        }
        if (frame.nextEventIndex >= frame.node.events.size()) {
            if (optionalMagnetEvent || optionalFunctionMagnetTargetEvent) {
                return null;
            }
            if (replayStrict && replayModeActive) {
                // Experimental strict mode: fail fast on replay event underflow.
                throw new AssertionError("Genome replay desync: missing event "
                        + expectedType + " (" + debugName + ") in scope "
                        + frame.scopeType + frame.node.seed);
            }
            return null;
        }
        ReplayEvent event = frame.node.events.get(frame.nextEventIndex);
        maybeTraceToken(Character.toString(event.type) + event.value,
                "consumeEventGene", "expectedType=" + expectedType + ", debugName=" + debugName);
        if (event.type != expectedType) {
            if (optionalMagnetEvent || optionalFunctionMagnetTargetEvent) {
                // Magnet-use channels are advisory in replay:
                // their call sites can be pruned or introduced by reducer edits.
                // Keep stream alignment by not consuming mismatched non-magnet event.
                return null;
            }
            if (replayStrict) {
                throw new AssertionError("Genome replay desync: expected event "
                        + expectedType + " (" + debugName + "), got " + event.type
                        + " in scope " + frame.scopeType + frame.node.seed);
            }
            // Relaxed replay: consume mismatched event and continue with live RNG for this decision.
            frame.nextEventIndex++;
            return null;
        }
        frame.nextEventIndex++;
        return event.value;
    }

    private static boolean isOptionalMagnetRuleEvent(char expectedType, String debugName) {
        return expectedType == 'R'
                && debugName != null
                && debugName.startsWith("magnet.");
    }

    private static boolean isOptionalFunctionMagnetTargetEvent(char expectedType, String debugName) {
        return expectedType == 'U'
                && debugName != null
                && "magnet.use.use.function".equals(debugName);
    }

    private void writeEventGene(String prefix, long geneValue) {
        if (recordWriter == null) {
            return;
        }
        String token = prefix + Long.toString(geneValue);
        maybeTraceToken(token, "recordEventGene", "prefix=" + prefix + ", value=" + geneValue);
        try {
            if (isSpeculativeRecordActive()) {
                flushPendingNLine();
                writeTokenLine(recordDepth, token);
                return;
            }
            if (!"N".equals(prefix)) {
                flushPendingNLine();
                writeTokenLine(recordDepth, token);
                return;
            }
            appendNToken(token);
        } catch (IOException e) {
            throw new RuntimeException("Failed to record event gene", e);
        }
    }

    private void appendNToken(String token) throws IOException {
        if (pendingNLine.length() == 0 || pendingNDepth != recordDepth) {
            flushPendingNLine();
            pendingNDepth = recordDepth;
            pendingNLine.append(token);
            return;
        }

        int maxContentWidth = Math.max(16, MAX_EVENT_LINE_WIDTH - (recordDepth * 2));
        int candidateLen = pendingNLine.length() + 1 + token.length();
        if (candidateLen > maxContentWidth) {
            flushPendingNLine();
            pendingNDepth = recordDepth;
            pendingNLine.append(token);
            return;
        }

        pendingNLine.append(' ').append(token);
    }

    private void flushPendingNLine() throws IOException {
        if (pendingNLine.length() == 0 || recordWriter == null) {
            return;
        }
        writeIndent(pendingNDepth < 0 ? recordDepth : pendingNDepth);
        writeRecord(pendingNLine.toString());
        writeRecord("\n");
        flushRecordIfNeeded();
        resetPendingNLine();
    }

    private void resetPendingNLine() {
        pendingNLine.setLength(0);
        pendingNDepth = -1;
    }

    private void writeTokenLine(int depth, String token) throws IOException {
        writeIndent(depth);
        writeRecord(token);
        writeRecord("\n");
        flushRecordIfNeeded();
    }

    private long nextMutationSeed() {
        return ThreadLocalRandom.current().nextLong();
    }

    @Override
    public synchronized void endBlock() {
        endScope('B');
    }

    @Override
    public synchronized void endScope(char scopeType) {
        ReplayFrame frame = replayStack.poll();
        if (frame == null) {
            throw new RuntimeException("Scope replay/record stack underflow");
        }
        if (frame.scopeType != scopeType) {
            throw new RuntimeException("Scope replay/record mismatch on end: expected "
                    + frame.scopeType + ", got " + scopeType);
        }

        if (recordWriter == null) {
            return;
        }
        if (!frame.recorded) {
            throw new RuntimeException("Block gene was not recorded before endBlock");
        }
        String scopeToken = Character.toString(frame.scopeType) + frame.selectedSeed;
        maybeSnapshot(scopeToken, "endScope",
                "scopeType=" + frame.scopeType
                        + ", selectedSeed=" + frame.selectedSeed
                        + ", mutable=" + frame.mutable
                        + ", replayNode=" + (frame.node != null));
        if (frame.node != null && !frame.mutable
                && frame.nextEventIndex != frame.node.events.size()) {
            if (replayStrict) {
                String firstUnconsumed = "<none>";
                if (frame.nextEventIndex < frame.node.events.size()) {
                    ReplayEvent nextEvent = frame.node.events.get(frame.nextEventIndex);
                    firstUnconsumed = "" + nextEvent.type + nextEvent.value;
                }
                throw new RuntimeException("Genome replay desync: unconsumed event genes in scope "
                        + frame.scopeType + frame.node.seed + " (consumed " + frame.nextEventIndex + " of "
                        + frame.node.events.size() + "), first_unconsumed=" + firstUnconsumed);
            }
        }
            if (recordDepth > 0) {
                recordDepth--;
            }
            try {
                flushPendingNLine();
                writeIndent(recordDepth);
                writeRecord(")");
                writeRecord("\n");
                flushRecordIfNeeded();
                if (blocksWriter != null && scopeType == 'B') {
                    if (blocksDepth > 0) {
                        blocksDepth--;
                    }
                    writeIndent(blocksWriter, blocksDepth);
                    writeBlocks(")");
                    writeBlocks("\n");
                    flushBlocksIfNeeded();
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to record block end", e);
            }
        }

    private ReplayNode nextReplayNode() {
        if (replayRoots.isEmpty()) {
            return null;
        }
        if (replayStack.isEmpty()) {
            return nextRootIndex < replayRoots.size() ? replayRoots.get(nextRootIndex++) : null;
        }

        ReplayFrame parent = replayStack.peek();
        if (parent == null || parent.node == null) {
            return null;
        }
        if (parent.nextChildIndex >= parent.node.children.size()) {
            return null;
        }
        return parent.node.children.get(parent.nextChildIndex++);
    }

    private ReplayData loadReplayData(String replayFile) {
        if (replayFile == null || replayFile.isBlank()) {
            return new ReplayData(null, null, Collections.emptyList(), Collections.emptyList());
        }
        Path path = Paths.get(replayFile);
        try {
            List<String> lines = Files.readAllLines(path);
            Long globalSeed = null;
            String genocodeVersion = null;
            List<String> tokens = new ArrayList<>();
            for (String line : lines) {
                int commentStart = line.indexOf('#');
                String noComment = commentStart >= 0 ? line.substring(0, commentStart) : line;
                String trimmed = noComment.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                Matcher versionMatcher = GENOCODE_VERSION_HEADER_PATTERN.matcher(trimmed);
                if (versionMatcher.matches()) {
                    genocodeVersion = versionMatcher.group(1);
                    continue;
                }
                Matcher seedMatcher = GLOBAL_SEED_HEADER_PATTERN.matcher(trimmed);
                if (seedMatcher.matches()) {
                    globalSeed = Long.parseLong(seedMatcher.group(1));
                    continue;
                }
                Matcher matcher = TOKEN_PATTERN.matcher(trimmed);
                while (matcher.find()) {
                    tokens.add(matcher.group());
                }
            }
            if (tokens.isEmpty()) {
                return new ReplayData(genocodeVersion, globalSeed, Collections.emptyList(),
                        Collections.emptyList());
            }

            boolean hasParens = tokens.stream().anyMatch(t -> "(".equals(t) || ")".equals(t));
            if (!hasParens) {
                List<ReplayNode> roots = new ArrayList<>(tokens.size());
                List<ReplayEvent> rootEvents = new ArrayList<>();
                for (String token : tokens) {
                    if (isEventToken(token)) {
                        rootEvents.add(parseEventToken(token));
                        continue;
                    }
                    ScopeSeed scopeSeed = parseScopeSeed(token);
                    roots.add(new ReplayNode(scopeSeed.type, scopeSeed.seed));
                }
                return new ReplayData(genocodeVersion, globalSeed, roots, rootEvents);
            }
            ReplayParse replayParse = parseReplayForest(tokens);
            return new ReplayData(genocodeVersion, globalSeed, replayParse.roots,
                    replayParse.rootEvents);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read block RNG replay file: " + replayFile, e);
        } catch (NumberFormatException e) {
            throw new RuntimeException("Invalid seed value in block RNG replay file: " + replayFile, e);
        }
    }

    private ReplayParse parseReplayForest(List<String> tokens) {
        IntRef idx = new IntRef();
        List<ReplayNode> roots = new ArrayList<>();
        List<ReplayEvent> rootEvents = new ArrayList<>();
        while (idx.value < tokens.size()) {
            String token = tokens.get(idx.value);
            if (!"(".equals(token)) {
                if (isEventToken(token)) {
                    rootEvents.add(parseEventToken(token));
                }
                idx.value++;
                continue;
            }
            roots.add(parseReplayNode(tokens, idx));
        }
        return new ReplayParse(roots, rootEvents);
    }

    private ReplayNode parseReplayNode(List<String> tokens, IntRef idx) {
        expect(tokens, idx, "(");
        ScopeSeed scopeSeed = parseScopeSeedToken(tokens, idx);
        ReplayNode node = new ReplayNode(scopeSeed.type, scopeSeed.seed);
        while (idx.value < tokens.size()) {
            String token = tokens.get(idx.value);
            if (")".equals(token)) {
                break;
            }
            if ("(".equals(token)) {
                node.children.add(parseReplayNode(tokens, idx));
            } else {
                if (isEventToken(token)) {
                    node.events.add(parseEventToken(token));
                }
                idx.value++;
            }
        }
        expect(tokens, idx, ")");
        return node;
    }

    private ScopeSeed parseScopeSeedToken(List<String> tokens, IntRef idx) {
        if (idx.value >= tokens.size()) {
            throw new RuntimeException("Unexpected end of replay tokens, expected scope gene");
        }
        String token = tokens.get(idx.value++);
        if ("(".equals(token) || ")".equals(token)) {
            throw new RuntimeException("Expected scope gene token, got: " + token);
        }
        return parseScopeSeed(token);
    }

    private ScopeSeed parseScopeSeed(String token) {
        if (isEventToken(token)) {
            throw new RuntimeException("Expected scope gene token, got event token: " + token);
        }
        if (token.isEmpty()) {
            throw new RuntimeException("Empty scope gene token");
        }
        char type = token.charAt(0);
        if (type == 'B') {
            return new ScopeSeed('B', Gene.parseToken(token).blockSeed());
        }
        if (type == 'E') {
            return new ScopeSeed('E', Long.parseLong(token.substring(1)));
        }
        if (type == 'S') {
            return new ScopeSeed('S', Long.parseLong(token.substring(1)));
        }
        // Backward compatibility: tokens without explicit prefix are treated as block scopes.
        if (Character.isDigit(type) || type == '-') {
            return new ScopeSeed('B', Gene.parseToken(token).blockSeed());
        }
        throw new RuntimeException("Unsupported scope gene token: " + token);
    }

    private static boolean isEventToken(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        char c = token.charAt(0);
        return c == 'R' || c == 'C' || c == 'N' || c == 'M' || c == 'U';
    }

    private static ReplayEvent parseEventToken(String token) {
        char type = token.charAt(0);
        long value = Long.parseLong(token.substring(1));
        return new ReplayEvent(type, value);
    }

    private void expect(List<String> tokens, IntRef idx, String expected) {
        if (idx.value >= tokens.size()) {
            throw new RuntimeException("Unexpected end of replay tokens, expected: " + expected);
        }
        String token = tokens.get(idx.value++);
        if (!expected.equals(token)) {
            throw new RuntimeException("Expected token '" + expected + "', got '" + token + "'");
        }
    }

    private BufferedWriter openMemoryWriter(boolean blocks) {
        if (blocks) {
            blocksBufferWriter = new StringWriter(8192);
            return new BufferedWriter(blocksBufferWriter);
        }
        recordBufferWriter = new StringWriter(8192);
        return new BufferedWriter(recordBufferWriter);
    }

    private static void ensureParentDirs(Path path) {
        Path parent = path.getParent();
        if (parent == null) {
            return;
        }
        try {
            Files.createDirectories(parent);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create genome output directory: " + parent, e);
        }
    }

    private void recordScopeStart(char scopeType, String token, boolean mirrorToBlocks) {
        if (recordWriter == null) {
            return;
        }
        try {
            flushPendingNLine();
            writeIndent(recordDepth);
            writeRecord("(");
            writeRecord(token);
            writeRecord("\n");
            recordDepth++;
            flushRecordIfNeeded();
            if (mirrorToBlocks && blocksWriter != null) {
                writeIndent(blocksWriter, blocksDepth);
                writeBlocks("(");
                writeBlocks(token);
                writeBlocks("\n");
                blocksDepth++;
                flushBlocksIfNeeded();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to record block RNG seed", e);
        }
    }

    private void writeGlobalSeedHeader() {
        if (recordWriter == null) {
            return;
        }
        try {
            recordWriter.write("(genocode_version ");
            recordWriter.write(GENOCODE_VERSION);
            recordWriter.write(")");
            recordWriter.newLine();
            flushPendingNLine();
            recordWriter.write("(global_seed ");
            recordWriter.write(Long.toString(PseudoRandom.getCurrentSeed()));
            recordWriter.write(")");
            recordWriter.newLine();
            recordWriter.flush();
            if (blocksWriter != null) {
                blocksWriter.write("(genocode_version ");
                blocksWriter.write(GENOCODE_VERSION);
                blocksWriter.write(")");
                blocksWriter.newLine();
                blocksWriter.write("(global_seed ");
                blocksWriter.write(Long.toString(PseudoRandom.getCurrentSeed()));
                blocksWriter.write(")");
                blocksWriter.newLine();
                blocksWriter.flush();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to write global seed header", e);
        }
    }

    private void writeIndent(int depth) throws IOException {
        if (recordWriter == null) {
            return;
        }
        if (isSpeculativeRecordActive()) {
            for (int i = 0; i < depth; i++) {
                writeRecord("  ");
            }
            return;
        }
        writeIndent(recordWriter, depth);
    }

    private void writeIndent(BufferedWriter writer, int depth) throws IOException {
        if (writer == null) {
            return;
        }
        if (isSpeculativeRecordActive() && writer == blocksWriter) {
            for (int i = 0; i < depth; i++) {
                writeBlocks("  ");
            }
            return;
        }
        for (int i = 0; i < depth; i++) {
            writer.write("  ");
        }
    }

    private void closeWriterQuietly() {
        if (recordWriter == null && blocksWriter == null) {
            return;
        }
        try {
            flushPendingNLine();
            if (recordWriter != null) {
                recordWriter.flush();
                recordWriter.close();
            }
            if (recordFilePath != null && recordBufferWriter != null) {
                Files.writeString(recordFilePath, recordBufferWriter.toString(),
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                rewritePrettyGenome(recordFilePath);
            }
            if (blocksWriter != null) {
                blocksWriter.flush();
                blocksWriter.close();
            }
            if (blocksFilePath != null && blocksBufferWriter != null) {
                Files.writeString(blocksFilePath, blocksBufferWriter.toString(),
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            }
            if (snapshotWriter != null) {
                snapshotWriter.flush();
                snapshotWriter.close();
            }
        } catch (IOException ignored) {
            // Ignore close failures; writer is being replaced.
        } finally {
            recordWriter = null;
            blocksWriter = null;
            snapshotWriter = null;
            recordBufferWriter = null;
            blocksBufferWriter = null;
            speculativeRecordChunks.clear();
            speculativeBlocksChunks.clear();
            speculativeCheckpoints.clear();
            resetPendingNLine();
        }
    }

    @Override
    public synchronized void close() {
        closeWriterQuietly();
        replayRoots = Collections.emptyList();
        replayRootEvents = Collections.emptyList();
        replayStack.clear();
        nextRootIndex = 0;
        nextRootEventIndex = 0;
        mutationSeedOverride = null;
        mutationTargetBlockSeed = null;
        replayModeActive = false;
        replaySupportsStatementScopes = false;
        replayTraceToken = null;
        replayTraceTokens = Collections.emptySet();
        replayTraceCrash = false;
        replayTraceSymbolTableTokens = Collections.emptySet();
        replayTraceSymbolTableMaxTypes = 24;
        replayTraceSymbolTableMaxSymbolsPerType = 16;
        replayTraceTypeListTokens = Collections.emptySet();
        replayTraceTypeListMaxTypes = 48;
        snapshotTokens = Collections.emptySet();
        snapshotPhases = Collections.emptySet();
        snapshotIncludeSymbolTable = true;
        snapshotIncludeTypeList = false;
        snapshotSymbolTableMaxTypes = 24;
        snapshotSymbolTableMaxSymbolsPerType = 16;
        snapshotTypeListMaxTypes = 48;
        speculativeRecordChunks.clear();
        speculativeBlocksChunks.clear();
        speculativeCheckpoints.clear();
        recordFilePath = null;
        snapshotFilePath = null;
        blocksFilePath = null;
        recordDepth = 0;
        blocksDepth = 0;
        resetPendingNLine();
    }

    private static Path toBlocksPath(Path recordPath) {
        if (recordPath == null) {
            return null;
        }
        String raw = recordPath.toString();
        if (raw.endsWith(".genome")) {
            return Paths.get(raw.substring(0, raw.length() - ".genome".length()) + ".blocks");
        }
        return Paths.get(raw + ".blocks");
    }

    private void rewritePrettyGenome(Path path) throws IOException {
        List<String> input = Files.readAllLines(path);
        List<String> output = new ArrayList<>(input.size());
        String currentIndent = null;
        StringBuilder nLine = new StringBuilder();

        for (String line : input) {
            int leadingSpaces = countLeadingSpaces(line);
            String indent = line.substring(0, leadingSpaces);
            List<String> nTokens = extractNLineTokens(line.substring(leadingSpaces));
            if (nTokens == null) {
                flushBufferedNLine(output, currentIndent, nLine);
                currentIndent = null;
                output.add(line);
                continue;
            }
            if (currentIndent == null || !currentIndent.equals(indent)) {
                flushBufferedNLine(output, currentIndent, nLine);
                currentIndent = indent;
            }
            for (String token : nTokens) {
                appendWrappedNToken(output, indent, nLine, token);
            }
        }
        flushBufferedNLine(output, currentIndent, nLine);

        Files.write(path, output, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    private static int countLeadingSpaces(String line) {
        int i = 0;
        while (i < line.length() && line.charAt(i) == ' ') {
            i++;
        }
        return i;
    }

    private static List<String> extractNLineTokens(String content) {
        String trimmed = content.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String[] rawTokens = trimmed.split("\\s+");
        List<String> tokens = new ArrayList<>(rawTokens.length);
        for (String token : rawTokens) {
            if (token.isEmpty() || token.charAt(0) != 'N') {
                return null;
            }
            tokens.add(token);
        }
        return tokens;
    }

    private void appendWrappedNToken(List<String> output, String indent, StringBuilder nLine,
            String token) {
        if (nLine.length() == 0) {
            nLine.append(token);
            return;
        }
        int maxContentWidth = Math.max(16, MAX_EVENT_LINE_WIDTH - indent.length());
        int candidateLen = nLine.length() + 1 + token.length();
        if (candidateLen > maxContentWidth) {
            flushBufferedNLine(output, indent, nLine);
            nLine.append(token);
            return;
        }
        nLine.append(' ').append(token);
    }

    private static void flushBufferedNLine(List<String> output, String indent, StringBuilder nLine) {
        if (nLine.length() == 0 || indent == null) {
            return;
        }
        output.add(indent + nLine.toString());
        nLine.setLength(0);
    }

    private static final class ReplayNode {
        final char type;
        final long seed;
        final List<ReplayEvent> events = new ArrayList<>();
        final List<ReplayNode> children = new ArrayList<>();

        ReplayNode(char type, long seed) {
            this.type = type;
            this.seed = seed;
        }
    }

    private static final class ReplayFrame {
        final ReplayNode node;
        final boolean mutable;
        final char scopeType;
        final long selectedSeed;
        boolean recorded = false;
        int nextEventIndex = 0;
        int nextChildIndex = 0;

        ReplayFrame(ReplayNode node, boolean mutable, char scopeType, long selectedSeed) {
            this.node = node;
            this.mutable = mutable;
            this.scopeType = scopeType;
            this.selectedSeed = selectedSeed;
        }

        static ReplayFrame mutableFrame(char scopeType, long selectedSeed) {
            return new ReplayFrame(null, true, scopeType, selectedSeed);
        }
    }

    private static final class IntRef {
        int value = 0;
    }

    private static final class ReplayEvent {
        final char type;
        final long value;

        ReplayEvent(char type, long value) {
            this.type = type;
            this.value = value;
        }
    }

    private static final class ScopeSeed {
        final char type;
        final long seed;

        ScopeSeed(char type, long seed) {
            this.type = type;
            this.seed = seed;
        }
    }

    private static final class ReplayData {
        final String genocodeVersion;
        final Long globalSeed;
        final List<ReplayNode> roots;
        final List<ReplayEvent> rootEvents;

        ReplayData(String genocodeVersion, Long globalSeed, List<ReplayNode> roots,
                List<ReplayEvent> rootEvents) {
            this.genocodeVersion = genocodeVersion;
            this.globalSeed = globalSeed;
            this.roots = roots;
            this.rootEvents = rootEvents;
        }
    }

    private static final class ReplayParse {
        final List<ReplayNode> roots;
        final List<ReplayEvent> rootEvents;

        ReplayParse(List<ReplayNode> roots, List<ReplayEvent> rootEvents) {
            this.roots = roots;
            this.rootEvents = rootEvents;
        }
    }

    private static boolean isSupportedReplayVersion(String version) {
        return GENOCODE_VERSION.equals(version) || PREVIOUS_GENOCODE_VERSION.equals(version);
    }

    private static Long parseMutationTargetBlockSeed(String mutationTarget) {
        if (mutationTarget == null || mutationTarget.isBlank()) {
            return null;
        }
        String token = mutationTarget.trim();
        if (!token.startsWith("B") || token.length() == 1) {
            throw new RuntimeException("Invalid --genome-mutation-target token: '" + token
                    + "', expected B<seed>");
        }
        try {
            return Long.parseLong(token.substring(1));
        } catch (NumberFormatException nfe) {
            throw new RuntimeException("Invalid --genome-mutation-target token: '" + token
                    + "', expected B<seed>", nfe);
        }
    }

    private static String normalizeTraceToken(String raw) {
        if (raw == null) {
            return null;
        }
        String token = raw.trim();
        return token.isEmpty() ? null : token;
    }

    private static Set<String> parseTraceTokenSet(String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptySet();
        }
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        for (String part : raw.split("[,\\s]+")) {
            String token = normalizeTraceToken(part);
            if (token != null) {
                tokens.add(token);
            }
        }
        if (tokens.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(tokens);
    }

    private void maybeTraceToken(String token, String phase, String details) {
        if (token == null || !isTracedToken(token)) {
            return;
        }
        String message = "Replay trace token hit: token=" + token
                + ", phase=" + phase
                + ", details={" + details + "}";
        maybeTraceSymbolTable(token, phase);
        maybeTraceTypeList(token, phase);
        RuntimeException trace = new RuntimeException(message);
        if (replayTraceCrash) {
            throw trace;
        }
        trace.printStackTrace(System.err);
    }

    private boolean isTracedToken(String token) {
        if (replayTraceToken != null && replayTraceToken.equals(token)) {
            return true;
        }
        return replayTraceTokens.contains(token);
    }

    private boolean isSnapshotToken(String token) {
        if (token == null || snapshotTokens.isEmpty()) {
            return false;
        }
        return snapshotTokens.contains(token);
    }

    private boolean isSnapshotPhase(String phase) {
        return snapshotPhases.isEmpty() || snapshotPhases.contains(phase);
    }

    private void maybeSnapshot(String token, String phase, String details) {
        if (snapshotWriter == null || !isSnapshotToken(token) || !isSnapshotPhase(phase)) {
            return;
        }
        try {
            snapshotWriter.write("=== SNAPSHOT BEGIN ===\n");
            snapshotWriter.write("token=" + token + "\n");
            snapshotWriter.write("phase=" + phase + "\n");
            snapshotWriter.write("mode=" + (replayModeActive ? "replay" : "record") + "\n");
            snapshotWriter.write("details={" + details + "}\n");
            if (snapshotIncludeSymbolTable && snapshotIncludeTypeList) {
                snapshotWriter.write(GenerationState.dumpSnapshot(token));
            } else {
                if (snapshotIncludeSymbolTable) {
                    int maxTypes = Math.max(1, snapshotSymbolTableMaxTypes);
                    int maxSymbolsPerType = Math.max(1, snapshotSymbolTableMaxSymbolsPerType);
                    snapshotWriter.write("--- SymbolTable ---\n");
                    snapshotWriter.write(SymbolTable.dumpSnapshot(maxTypes, maxSymbolsPerType));
                }
                if (snapshotIncludeTypeList) {
                    int maxTypes = Math.max(1, snapshotTypeListMaxTypes);
                    snapshotWriter.write("--- TypeList ---\n");
                    snapshotWriter.write(TypeList.dumpSnapshot(maxTypes));
                }
            }
            snapshotWriter.write("=== SNAPSHOT END ===\n");
            snapshotWriter.flush();
        } catch (IOException e) {
            throw new RuntimeException("Failed to write replay snapshot", e);
        }
    }

    private void maybeTraceSymbolTable(String token, String phase) {
        if (!replayTraceSymbolTableTokens.contains(token)) {
            return;
        }
        int maxTypes = Math.max(1, replayTraceSymbolTableMaxTypes);
        int maxSymbolsPerType = Math.max(1, replayTraceSymbolTableMaxSymbolsPerType);
        System.err.println("[JTDBG][ReplayTrace][SymbolTable] token=" + token
                + " phase=" + phase
                + " maxTypes=" + maxTypes
                + " maxSymbolsPerType=" + maxSymbolsPerType);
        System.err.print(SymbolTable.dumpSnapshot(maxTypes, maxSymbolsPerType));
    }

    private void maybeTraceTypeList(String token, String phase) {
        if (!replayTraceTypeListTokens.contains(token)) {
            return;
        }
        int maxTypes = Math.max(1, replayTraceTypeListMaxTypes);
        System.err.println("[JTDBG][ReplayTrace][TypeList] token=" + token
                + " phase=" + phase
                + " maxTypes=" + maxTypes);
        System.err.print(TypeList.dumpSnapshot(maxTypes));
    }

    @Override
    public synchronized void beginSpeculativeRecord() {
        if (recordWriter == null) {
            return;
        }
        try {
            flushPendingNLine();
        } catch (IOException e) {
            throw new RuntimeException("Failed to flush pending genome line before speculative record", e);
        }
        speculativeCheckpoints.push(new SpeculativeCheckpoint(
                speculativeRecordChunks.checkpoint(), speculativeBlocksChunks.checkpoint()));
    }

    @Override
    public synchronized void commitSpeculativeRecord() {
        if (recordWriter == null || speculativeCheckpoints.isEmpty()) {
            return;
        }
        speculativeCheckpoints.pop();
        try {
            if (speculativeCheckpoints.isEmpty()) {
                for (String chunk : speculativeRecordChunks) {
                    recordWriter.write(chunk);
                }
                speculativeRecordChunks.clear();
                if (blocksWriter != null) {
                    for (String chunk : speculativeBlocksChunks) {
                        blocksWriter.write(chunk);
                    }
                    blocksWriter.flush();
                }
                speculativeBlocksChunks.clear();
                recordWriter.flush();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to commit speculative genome record", e);
        }
    }

    @Override
    public synchronized void rollbackSpeculativeRecord() {
        if (recordWriter == null || speculativeCheckpoints.isEmpty()) {
            return;
        }
        SpeculativeCheckpoint checkpoint = speculativeCheckpoints.pop();
        speculativeRecordChunks.shrink(checkpoint.recordSize);
        speculativeBlocksChunks.shrink(checkpoint.blocksSize);
    }

    private boolean isSpeculativeRecordActive() {
        return !speculativeCheckpoints.isEmpty();
    }

    private void writeRecord(String text) throws IOException {
        if (isSpeculativeRecordActive()) {
            speculativeRecordChunks.add(text);
            return;
        }
        recordWriter.write(text);
    }

    private void writeBlocks(String text) throws IOException {
        if (blocksWriter == null) {
            return;
        }
        if (isSpeculativeRecordActive()) {
            speculativeBlocksChunks.add(text);
            return;
        }
        blocksWriter.write(text);
    }

    private void flushRecordIfNeeded() throws IOException {
        if (!isSpeculativeRecordActive() && recordWriter != null) {
            recordWriter.flush();
        }
    }

    private void flushBlocksIfNeeded() throws IOException {
        if (!isSpeculativeRecordActive() && blocksWriter != null) {
            blocksWriter.flush();
        }
    }

    private static final class SpeculativeCheckpoint {
        final int recordSize;
        final int blocksSize;

        SpeculativeCheckpoint(int recordSize, int blocksSize) {
            this.recordSize = recordSize;
            this.blocksSize = blocksSize;
        }
    }
}
