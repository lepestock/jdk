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

package jdk.test.lib.jittester.factories;

import jdk.test.lib.jittester.Block;
import jdk.test.lib.jittester.Gene;
import jdk.test.lib.jittester.IRNode;
import jdk.test.lib.jittester.If;
import jdk.test.lib.jittester.LongSmallSet;
import jdk.test.lib.jittester.MutationScopeProductionFailedException;
import jdk.test.lib.jittester.ProductionFailedException;
import jdk.test.lib.jittester.ProductionParams;
import jdk.test.lib.jittester.Rule;
import jdk.test.lib.jittester.Switch;
import jdk.test.lib.jittester.SymbolTable;
import jdk.test.lib.jittester.Type;
import jdk.test.lib.jittester.TypeList;
import jdk.test.lib.jittester.utils.TypeUtil;
import jdk.test.lib.jittester.loops.DoWhile;
import jdk.test.lib.jittester.loops.For;
import jdk.test.lib.jittester.loops.While;
import jdk.test.lib.jittester.types.TypeKlass;
import jdk.test.lib.jittester.utils.Genome;
import jdk.test.lib.jittester.utils.DepthProbabilityTaper;
import jdk.test.lib.jittester.utils.PseudoRandom;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;

class BlockFactory extends Factory<Block> {
    private static final long LOCAL_COMPLEXITY_LIMIT = 1024L;
    private static final double CHILD_STATEMENT_LIMIT_FACTOR = 0.92;
    private static final long STATEMENT_COUNT_MASK = 0x3L;
    private static final double LOCAL_DECL_WEIGHT_MIN = 0.03;
    private static final double LOCAL_DECL_WEIGHT_BASE = 0.08;
    private static final double LOCAL_DECL_WEIGHT_SHALLOW_BONUS = 0.10;
    private static final double LOCAL_DECL_WEIGHT_EARLY_BONUS = 0.18;
    private static final int SHALLOW_BLOCK_DEPTH_THRESHOLD = 2;
    private static final int EARLY_STATEMENT_INDEX_THRESHOLD = 2;
    private static final ThreadLocal<Integer> BLOCK_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Integer> BLOCK_MAX_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<ArrayDeque<StatementContext>> STATEMENT_CONTEXT_STACK =
            ThreadLocal.withInitial(ArrayDeque::new);
    private final Type returnType;
    private final int statementLimit;
    private final int operatorLimit;
    private final boolean subBlock;
    private final boolean canHaveBreaks;
    private final boolean canHaveContinues;
    private final boolean canHaveReturn;
    private final boolean canHaveThrow;
    private final int level;
    private final TypeKlass ownerClass;

    BlockFactory(TypeKlass klass, Type returnType, long complexityLimit, int statementLimit,
                 int operatorLimit, int level, boolean subBlock, boolean canHaveBreaks,
                 boolean canHaveContinues, boolean canHaveReturn, boolean canHaveThrows) {
        this.ownerClass = klass;
        this.returnType = returnType;
        this.statementLimit = statementLimit;
        this.operatorLimit = operatorLimit;
        this.level = level;
        this.subBlock = subBlock;
        this.canHaveBreaks = canHaveBreaks;
        this.canHaveContinues = canHaveContinues;
        this.canHaveReturn = canHaveReturn;
        this.canHaveThrow = canHaveThrows;
    }

    @Override
    public Block produce() throws ProductionFailedException {
        int blockDepth = enterBlockDepth();
        if (statementLimit > 0) {
            long blockRngInSeed = Genome.startBlock(PseudoRandom.getCurrentSeed());
            PseudoRandom.setCurrentSeed(blockRngInSeed);
            try {
                List<IRNode> content = new ArrayList<>();
                int attemptedStatements = 0;
                int successfulStatements = 0;
                int failedStatements = 0;
                int plannedStatementAttempts = decideStatementAttemptCount(
                        blockRngInSeed, statementLimit, blockDepth);
                int attemptsSafetyCap = Math.max(1, statementLimit * 12);
                List<Long> statementDecisionSeeds = prepareStatementDecisionSeeds(
                        blockRngInSeed, plannedStatementAttempts);
                LongSmallSet replayStatementSeedSpan = Genome.getCurrentStatementSeedOverrides();
                if (Genome.isReplayActive() && replayStatementSeedSpan.size > 0) {
                    // In replay, when a statement-seed span is present in the gene token,
                    // treat its length as the block statement-attempt span.
                    // This decouples replay from seed-derived block-size bits and allows
                    // reducer edits that drop statement genes without frame desync.
                    plannedStatementAttempts = replayStatementSeedSpan.size;
                    statementDecisionSeeds = prepareStatementDecisionSeeds(
                            blockRngInSeed, plannedStatementAttempts);
                }
                int replayChildCount = Genome.getCurrentReplayChildCount();
                if (Genome.isReplayActive() && replayChildCount >= 0) {
                    // Scope-aware replay (full-genocode >= 1.2): statement attempts are
                    // framed by explicit S-scopes, so derive attempt count from replay tree.
                    plannedStatementAttempts = replayChildCount;
                    statementDecisionSeeds = prepareStatementDecisionSeeds(
                            blockRngInSeed, plannedStatementAttempts);
                }
                if (ProductionParams.blockDebug.value()) {
                    System.out.printf("BLOCK_PLAN depth=%d seedIn=%d replay=%s replayChildCount=%d plannedAttempts=%d%n",
                            blockDepth, blockRngInSeed, Genome.isReplayActive(),
                            replayChildCount, plannedStatementAttempts);
                }
                Gene blockGene = decideBlockGene(blockRngInSeed, statementDecisionSeeds);
                Genome.recordCurrentBlockGene(blockGene);
                IRNodeBuilder builder = new IRNodeBuilder()
                        .setOperatorLimit(operatorLimit)
                        .setOwnerKlass(ownerClass)
                        .setResultType(returnType)
                        .setCanHaveReturn(canHaveReturn)
                        .setCanHaveThrow(canHaveThrow)
                        .setCanHaveBreaks(canHaveBreaks)
                        .setCanHaveContinues(canHaveContinues)
                        .setExceptionSafe(false)
                        .setNoConsts(false);
                int maxCfgDepth = Math.max(1, ProductionParams.maxCfgDepth.value());
                boolean allowNestedControlFlow = level < maxCfgDepth;
                Rule<IRNode> rule;
                SymbolTable.push();
                final int blockSymbolDepth = SymbolTable.checkpoint();
                try {
                    while (attemptedStatements < attemptsSafetyCap
                            && attemptedStatements < plannedStatementAttempts) {
                        long statementSeed = statementDecisionSeeds.get(attemptedStatements);
                        long statementScopeSeed = Genome.startScope('S', statementSeed);
                        PseudoRandom.setCurrentSeed(statementScopeSeed);
                        Genome.recordCurrentScopeGene('S', statementScopeSeed);
                        pushStatementContext(blockDepth, attemptedStatements, plannedStatementAttempts);
                        Throwable statementThrowable = null;
                        builder.setComplexityLimit(LOCAL_COMPLEXITY_LIMIT);
                        rule = new Rule<>("block");
                        rule.add("statement", builder.getStatementFactory(), 8);
                        if (!ProductionParams.disableVarsInBlock.value()) {
                            double localDeclWeight = computeLocalDeclarationWeight(
                                    blockDepth, attemptedStatements);
                            rule.add("decl", builder.setIsLocal(true).getDeclarationFactory(), localDeclWeight);
                        }
                        if (statementLimit > 1 && allowNestedControlFlow) {
                            int childStatementLimit = Math.max(1,
                                    (int) Math.ceil(statementLimit * CHILD_STATEMENT_LIMIT_FACTOR));
                            builder.setStatementLimit(childStatementLimit).setLevel(level + 1);
                            if (!ProductionParams.disableNestedBlocks.value()) {
                                rule.add("block", builder.setCanHaveReturn(false)
                                        .setCanHaveThrow(false)
                                        .setCanHaveBreaks(false)
                                        .setCanHaveContinues(false)
                                        .getBlockFactory());

                                rule.add("try-catch", builder.getTryCatchBlockFactory(), 0.3);
                                builder.setCanHaveReturn(canHaveReturn)
                                        .setCanHaveThrow(canHaveThrow)
                                        .setCanHaveBreaks(canHaveBreaks)
                                        .setCanHaveContinues(canHaveContinues);
                            }
                            addControlFlowDeviation(rule, builder);
                        }
                        try {
                            IRNode choiceResult = rule.produce();
                            attemptedStatements++;
                            successfulStatements++;
                            content.add(choiceResult);
                        } catch (ProductionFailedException e) {
                            if (e instanceof MutationScopeProductionFailedException
                                    && !Genome.isReplayMutationScopeActive()) {
                                throw new RuntimeException(
                                        "Failed to mutate: statement failure escaped mutable scope", e);
                            }
                            if (Genome.isReplayActive()) {
                                System.err.println("[JTDBG][BlockFactory] replay statement ProductionFailedException"
                                        + " blockSeed=" + blockRngInSeed
                                        + " statementScopeSeed=" + statementScopeSeed
                                        + " attemptedStatements=" + attemptedStatements
                                        + " plannedStatementAttempts=" + plannedStatementAttempts);
                                e.printStackTrace(System.err);
                            }
                            attemptedStatements++;
                            failedStatements++;
                        } catch (RuntimeException e) {
                            statementThrowable = e;
                            throw e;
                        } catch (Error e) {
                            statementThrowable = e;
                            throw e;
                        } finally {
                            popStatementContext();
                            try {
                                Genome.endScope('S');
                            } catch (RuntimeException endScopeEx) {
                                if (statementThrowable != null) {
                                    System.err.println("[JTDBG][BlockFactory] statement runtime before endScope failure: "
                                            + statementThrowable.getClass().getName() + ": "
                                            + statementThrowable.getMessage());
                                    statementThrowable.printStackTrace(System.err);
                                    endScopeEx.addSuppressed(statementThrowable);
                                }
                                throw endScopeEx;
                            }
                            if (SymbolTable.checkpoint() != blockSymbolDepth) {
                                // A failed branch may leave pushed symbol frames behind.
                                // Keep block-local symbol depth stable across statement attempts.
                                SymbolTable.rollbackToCheckpoint(blockSymbolDepth);
                            }
                        }
                    }
                    boolean safetyCapReached = attemptedStatements >= attemptsSafetyCap;
                    boolean stoppedByProbability = attemptedStatements < plannedStatementAttempts;
                    maybeLogMutationStatementDecisions(blockRngInSeed, plannedStatementAttempts,
                            statementDecisionSeeds);
                    // Ok, if the block can end with break and continue. Generate the appropriate productions.
                    rule = new Rule<>("block_ending");
                    if (canHaveBreaks && !subBlock) {
                        rule.add("break", builder.getBreakFactory());
                    }
                    if (canHaveContinues && !subBlock) {
                        rule.add("continue", builder.getContinueFactory());
                    }
                    if (canHaveReturn && !subBlock && !returnType.equals(TypeList.VOID)) {
                        rule.add("return", builder.setComplexityLimit(LOCAL_COMPLEXITY_LIMIT)
                                .getReturnFactory());
                    }
                    if (canHaveThrow && !subBlock) {
                        Type rtException = TypeList.find("java.lang.RuntimeException");
                        java.util.Collection<Type> throwTypes = TypeUtil.getImplicitlyCastable(TypeList.getAll(), rtException);
                        if (!throwTypes.isEmpty()) {
                            // Keep block-ending candidate shaping deterministic and out of the
                            // replay event stream: this decision is only used to construct a rule.
                            rtException = throwTypes.iterator().next();
                        }
                        rule.add("throw", builder.setResultType(rtException)
                                .setComplexityLimit(Math.max(LOCAL_COMPLEXITY_LIMIT, 5))
                                .setOperatorLimit(Math.max(operatorLimit, 5))
                                .getThrowFactory());

                                // Throws leave huge chunks of code unexecuted, hence the 30% adjustment.
                                //double adjustedThrowChance = 0.3 * ProductionParams.chanceThrow.value() / 100.0;
                                //rule.add("try-catch", builder.getTryCatchBlockFactory(), adjustedThrowChance);
                    }

                    try {
                        if (rule.size() > 0) {
                            content.add(rule.produce());
                            maybeLogBlockDebug(blockDepth, blockRngInSeed,
                                    attemptedStatements, successfulStatements, failedStatements,
                                    stoppedByProbability, safetyCapReached, content.isEmpty(), true);
                        } else {
                            maybeLogBlockDebug(blockDepth, blockRngInSeed,
                                    attemptedStatements, successfulStatements, failedStatements,
                                    stoppedByProbability, safetyCapReached, content.isEmpty(), false);
                        }
                    } catch (ProductionFailedException e) {
                        if (e instanceof MutationScopeProductionFailedException
                                && !Genome.isReplayMutationScopeActive()) {
                            throw new RuntimeException(
                                    "Failed to mutate: block ending failure escaped mutable scope", e);
                        }
                        maybeLogBlockDebug(blockDepth, blockRngInSeed,
                                attemptedStatements, successfulStatements, failedStatements,
                                stoppedByProbability, safetyCapReached, content.isEmpty(), false);
                    }

                    return finalizeProducedBlock(ownerClass, returnType, content, level, blockGene);
                } finally {
                    if (!subBlock) {
                        SymbolTable.pop();
                    } else {
                        SymbolTable.merge();
                    }
                }
            } catch (Throwable t) {
                System.err.println("[JTDBG][BlockFactory] throwable before endBlock: "
                        + t.getClass().getName() + ": " + t.getMessage());
                t.printStackTrace(System.err);
                throw t;
            } finally {
                Genome.endBlock();
                exitBlockDepth();
            }
        }
        exitBlockDepth();
        throw new ProductionFailedException();
    }

    private static int decideStatementAttemptCount(long blockSeed, int statementLimit,
            int blockDepth) {
        if (statementLimit <= 0) {
            return 0;
        }
        int fromSeed = (int) ((blockSeed & STATEMENT_COUNT_MASK) + 1);
        double boost = Math.max(0.0, ProductionParams.blockStatementBoostPercent.value()) / 100.0;
        int halfDepth = Math.max(1, ProductionParams.blockStatementBoostHalfDepth.value());
        double depthRatio = DepthProbabilityTaper.decayingAsymptote(blockDepth, 1.0, halfDepth);
        int adjusted = (int) Math.ceil(fromSeed * (1.0 + boost * depthRatio));
        return Math.max(1, Math.min(statementLimit, adjusted));
    }

    private static double computeLocalDeclarationWeight(int blockDepth, int statementIndex) {
        double weight = LOCAL_DECL_WEIGHT_BASE;
        if (blockDepth <= SHALLOW_BLOCK_DEPTH_THRESHOLD) {
            weight += LOCAL_DECL_WEIGHT_SHALLOW_BONUS;
        }
        if (statementIndex < EARLY_STATEMENT_INDEX_THRESHOLD) {
            weight += LOCAL_DECL_WEIGHT_EARLY_BONUS;
        }
        if (blockDepth >= SHALLOW_BLOCK_DEPTH_THRESHOLD + 2) {
            weight = LOCAL_DECL_WEIGHT_MIN;
        }
        return Math.max(LOCAL_DECL_WEIGHT_MIN, weight);
    }

    private static long deriveStatementDecisionSeed(long blockSeed, int statementIndex) {
        long z = blockSeed + 0x9E3779B97F4A7C15L * (statementIndex + 1L);
        z ^= (z >>> 30);
        z *= 0xBF58476D1CE4E5B9L;
        z ^= (z >>> 27);
        z *= 0x94D049BB133111EBL;
        z ^= (z >>> 31);
        return z;
    }

    private static List<Long> prepareStatementDecisionSeeds(long blockSeed, int plannedStatementAttempts) {
        List<Long> statementDecisionSeeds = new ArrayList<>(Math.max(0, plannedStatementAttempts));
        for (int statementIndex = 0; statementIndex < plannedStatementAttempts; statementIndex++) {
            Long statementSeedOverride = Genome.getCurrentStatementSeedOverride(statementIndex);
            long statementSeed = statementSeedOverride != null
                    ? statementSeedOverride
                    : deriveStatementDecisionSeed(blockSeed, statementIndex);
            statementDecisionSeeds.add(statementSeed);
        }
        return statementDecisionSeeds;
    }

    private static Gene decideBlockGene(long blockRngInSeed, List<Long> statementDecisionSeeds) {
        LongSmallSet statementDecisionOverrides = Genome.getCurrentStatementSeedOverrides();
        if (!statementDecisionOverrides.isEmpty()) {
            Gene overrideGene = new Gene(blockRngInSeed, statementDecisionOverrides);
            if (overrideGene.hasStatementDecisionOverrides()) {
                return overrideGene;
            }
        }
        return new Gene(blockRngInSeed, statementDecisionSeeds);
    }

    static Block produceEmptyBlock(TypeKlass ownerClass, Type returnType, int level) {
        long blockRngInSeed = Genome.startBlock(PseudoRandom.getCurrentSeed());
        PseudoRandom.setCurrentSeed(blockRngInSeed);
        Gene blockGene = new Gene(blockRngInSeed);
        Genome.recordCurrentBlockGene(blockGene);
        try {
            return finalizeProducedBlock(ownerClass, returnType, Collections.emptyList(), level, blockGene);
        } finally {
            Genome.endBlock();
        }
    }

    static Block finalizeProducedBlock(TypeKlass ownerClass, Type returnType,
            List<? extends IRNode> content, int level, Gene blockGene) {
        // Decouple post-block RNG from block internals: one deterministic step from input seed.
        PseudoRandom.setCurrentSeed(blockGene.blockSeed());
        PseudoRandom.random();
        return new Block(ownerClass, returnType, content, level, blockGene);
    }


    private void addControlFlowDeviation(Rule<IRNode> rule, IRNodeBuilder builder) {
        if (!ProductionParams.disableIf.value()) {
            rule.add("if", builder.getIfFactory());
        }
        if (!ProductionParams.disableWhile.value()) {
            rule.add("while", builder.getWhileFactory());
        }
        if (!ProductionParams.disableDoWhile.value()) {
            rule.add("do_while", builder.getDoWhileFactory());
        }
        if (!ProductionParams.disableFor.value()) {
            rule.add("for", builder.getForFactory());
        }
        if (!ProductionParams.disableSwitch.value()) {
            rule.add("switch", builder.getSwitchFactory(), 0.1);
        }
    }

    private int enterBlockDepth() {
        int depth = BLOCK_DEPTH.get() + 1;
        BLOCK_DEPTH.set(depth);
        if (depth > BLOCK_MAX_DEPTH.get()) {
            BLOCK_MAX_DEPTH.set(depth);
        }
        return depth;
    }

    private void exitBlockDepth() {
        int depth = BLOCK_DEPTH.get() - 1;
        if (depth <= 0) {
            BLOCK_DEPTH.set(0);
            BLOCK_MAX_DEPTH.set(0);
            return;
        }
        BLOCK_DEPTH.set(depth);
    }

    private void maybeLogBlockDebug(int blockDepth, long blockRngInSeed,
            int attemptedStatements, int successfulStatements, int failedStatements,
            boolean stoppedByProbability, boolean safetyCapReached,
            boolean emptyBlock, boolean endingAdded) {
        if (!ProductionParams.blockDebug.value()) {
            return;
        }
        int attemptWarn = Math.max(0, ProductionParams.blockDebugAttemptWarn.value());
        int depthWarn = Math.max(0, ProductionParams.blockDebugDepthWarn.value());
        boolean reportByAttempt = attemptWarn == 0 || attemptedStatements >= attemptWarn;
        boolean reportByDepth = depthWarn == 0 || blockDepth >= depthWarn;
        if (!reportByAttempt && !reportByDepth) {
            return;
        }
        System.out.printf("BLOCK_DEBUG depth=%d maxDepthSoFar=%d level=%d seedIn=%d "
                        + "attempts=%d success=%d fail=%d stopProb=%s cap=%s empty=%s endingAdded=%s%n",
                blockDepth, BLOCK_MAX_DEPTH.get(), level, blockRngInSeed,
                attemptedStatements, successfulStatements, failedStatements, stoppedByProbability,
                safetyCapReached, emptyBlock, endingAdded);
    }

    private void maybeLogMutationStatementDecisions(long blockRngInSeed,
            int plannedStatementAttempts, List<Long> statementDecisionSeeds) {
        if (!ProductionParams.blockRngLogMutationStatements.value()
                || !Genome.isCurrentMutationRootBlock()) {
            return;
        }
        int overrideCount = Genome.getCurrentStatementSeedOverrideCount();
        int ignoredOverrides = Math.max(0, overrideCount - plannedStatementAttempts);
        System.out.printf("MUTATION_BLOCK_DECISIONS seedIn=%d plannedAttempts=%d used=%s "
                        + "overrideCount=%d ignoredOverrides=%d%n",
                blockRngInSeed, plannedStatementAttempts, statementDecisionSeeds,
                overrideCount, ignoredOverrides);
    }

    static int currentStatementBlockDepth() {
        ArrayDeque<StatementContext> stack = STATEMENT_CONTEXT_STACK.get();
        return stack.isEmpty() ? 0 : stack.peekLast().blockDepth;
    }

    static int currentStatementIndex() {
        ArrayDeque<StatementContext> stack = STATEMENT_CONTEXT_STACK.get();
        return stack.isEmpty() ? -1 : stack.peekLast().statementIndex;
    }

    static int currentPlannedStatementAttempts() {
        ArrayDeque<StatementContext> stack = STATEMENT_CONTEXT_STACK.get();
        return stack.isEmpty() ? 0 : stack.peekLast().plannedStatementAttempts;
    }

    static double currentStatementProgress() {
        int statementIndex = currentStatementIndex();
        int planned = currentPlannedStatementAttempts();
        if (statementIndex < 0 || planned <= 1) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(1.0, statementIndex / (double) (planned - 1)));
    }

    private static void pushStatementContext(int blockDepth, int statementIndex,
            int plannedStatementAttempts) {
        STATEMENT_CONTEXT_STACK.get().addLast(
                new StatementContext(blockDepth, statementIndex, plannedStatementAttempts));
    }

    private static void popStatementContext() {
        ArrayDeque<StatementContext> stack = STATEMENT_CONTEXT_STACK.get();
        if (!stack.isEmpty()) {
            stack.removeLast();
        }
    }

    private static final class StatementContext {
        private final int blockDepth;
        private final int statementIndex;
        private final int plannedStatementAttempts;

        private StatementContext(int blockDepth, int statementIndex, int plannedStatementAttempts) {
            this.blockDepth = blockDepth;
            this.statementIndex = statementIndex;
            this.plannedStatementAttempts = plannedStatementAttempts;
        }
    }
}
