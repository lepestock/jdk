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

package jdk.test.lib.jittester.morph;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jdk.test.lib.jittester.BinaryOperator;
import jdk.test.lib.jittester.FlowParams;
import jdk.test.lib.jittester.IRNode;
import jdk.test.lib.jittester.OperatorKind;
import jdk.test.lib.jittester.ProductionFailedException;
import jdk.test.lib.jittester.ProductionParams;
import jdk.test.lib.jittester.Statement;
import jdk.test.lib.jittester.StatementSequence;
import jdk.test.lib.jittester.Type;
import jdk.test.lib.jittester.TypeList;
import jdk.test.lib.jittester.diagnostics.SourceDiagnostics;
import jdk.test.lib.jittester.factories.IRNodeBuilder;
import jdk.test.lib.jittester.types.TypeArray;
import jdk.test.lib.jittester.utils.Genome;
import jdk.test.lib.jittester.utils.PseudoRandom;

public record LoopIntrinsificationMorphTemplate(long id, int nextLeg, int legCount,
                                                boolean wholeBlock) implements MorphTemplate {
    private static final String TEMPLATE_ID_CHANNEL = "morph.loop_intrinsification.id";
    // Keep a small amount of off-target exploration: C2 shape support can change,
    // and near-miss shapes may still expose bugs. Promote to a PP only if tuning becomes useful.
    private static final int NON_VIABLE_EXPLORATION_PROBABILITY_PERCENT = 2;
    // Small index offsets are currently part of the LI shape, but not worth a user knob yet.
    private static final int OFFSET_PROBABILITY_PERCENT = 20;
    // Negative offsets are intentionally rarer: they are useful bug-finding shapes,
    // but current C2 support is less mature than for zero/positive offsets.
    private static final int NEGATIVE_OFFSET_SHARE_PERCENT = 50;
    private static final double WHOLE_BLOCK_PROBABILITY = 0.50;
    private static final int MAX_POSITIVE_OFFSET = 4;
    private static final int MAX_NEGATIVE_OFFSET = 4;

    public LoopIntrinsificationMorphTemplate() {
        this(newId(), 0, pickLegCount(), pickWholeBlock());
    }

    public static boolean canBeCreated(FlowParams flowParams) {
        return hardRejectionReason(flowParams, null).isEmpty();
    }

    public static LoopIntrinsificationMorphTemplate createOrNull(FlowParams flowParams,
            IRNodeBuilder builder) {
        if (hardRejectionReason(flowParams, builder).isPresent()
                || !shouldCreate(flowParams)
                || (preferredRejectionReason(flowParams).isPresent() && !shouldExploreNonViable())) {
            return null;
        }
        return new LoopIntrinsificationMorphTemplate();
    }

    public static Optional<String> rejectionDiagnostic(FlowParams flowParams, IRNodeBuilder builder) {
        if (!flowParams.inArrayKernel()) {
            return Optional.empty();
        }
        return hardRejectionReason(flowParams, builder).or(() -> preferredRejectionReason(flowParams))
                .map(reason -> "LoopIntrinsificationMorph rejected reason=" + reason);
    }

    public static boolean shouldPreferViableArrayKernelShape() {
        return !PseudoRandom.randomBoolean(probability(NON_VIABLE_EXPLORATION_PROBABILITY_PERCENT));
    }

    public static int maxPositiveOffset() {
        return MAX_POSITIVE_OFFSET;
    }

    public static int maxNegativeOffset() {
        return MAX_NEGATIVE_OFFSET;
    }

    public static boolean shouldUseNegativeOffsetCompatibleRange() {
        return MAX_NEGATIVE_OFFSET > 0
                && PseudoRandom.randomBoolean(probability(NEGATIVE_OFFSET_SHARE_PERCENT));
    }

    @Override
    public String creationDiagnostic() {
        return "LoopIntrinsificationMorph id=" + id + " legs=" + legCount + " created";
    }

    @Override
    public double legWeightMultiplier() {
        return 80.0;
    }

    @Override
    public boolean takesWholeBlock() {
        return wholeBlock;
    }

    @Override
    public MorphLegResult produceLeg(int leg, IRNodeBuilder builder)
            throws ProductionFailedException {
        if (leg < 0 || leg >= legCount) {
            throw new ProductionFailedException();
        }
        int offset = selectOffset(builder);
        Type elementType = selectElementType(builder, offset);
        IRNode target = builder.getOffsetIterationIndexedArrayElementFactory(elementType, offset).produce();
        IRNode value = builder.produceStableExpression(elementType);
        Statement assignment = new Statement(
                new BinaryOperator(OperatorKind.ASSIGN, elementType, target, value), true);
        StatementSequence sequence = new StatementSequence(List.of(assignment),
                builder.currentLevel() + 1);
        attachDiagnostic(sequence, "LoopIntrinsificationMorph id=" + id
                + " leg=" + leg
                + "/" + legCount
                + " wholeBlock=" + wholeBlock
                + " offset=" + offset
                + " targetType=" + elementType.getName());
        return new MorphLegResult(sequence,
                new LoopIntrinsificationMorphTemplate(id, leg + 1, legCount, wholeBlock),
                leg + 1 >= legCount);
    }

    private static Type selectElementType(IRNodeBuilder builder, int offset) throws ProductionFailedException {
        ArrayList<Type> candidates = new ArrayList<>();
        for (Type type : fillStubElementTypes()) {
            if (!TypeArray.isElementTypeAllowed(type)) {
                continue;
            }
            if (builder.hasOffsetIterationIndexedArrayElementCandidates(type, offset)) {
                candidates.add(type);
            }
        }
        if (candidates.isEmpty()) {
            throw new ProductionFailedException();
        }
        return candidates.get(PseudoRandom.randomNotNegative(candidates.size()));
    }

    private static int selectOffset(IRNodeBuilder builder) {
        if (!PseudoRandom.randomBoolean(probability(OFFSET_PROBABILITY_PERCENT))) {
            return 0;
        }
        if (shouldSelectNegativeOffset()) {
            int offset = selectOffsetWithSign(builder, -1, MAX_NEGATIVE_OFFSET);
            if (offset != 0) {
                return offset;
            }
        }
        return selectOffsetWithSign(builder, 1, MAX_POSITIVE_OFFSET);
    }

    private static int selectOffsetWithSign(IRNodeBuilder builder, int sign, int limit) {
        if (limit == 0) {
            return 0;
        }
        int offset = PseudoRandom.randomNotNegative(limit) + 1;
        for (int attempt = 0; attempt < limit; attempt++) {
            int signedOffset = sign * offset;
            if (hasTargetCandidates(builder, signedOffset)) {
                return signedOffset;
            }
            offset = offset == limit ? 1 : offset + 1;
        }
        return 0;
    }

    private static boolean shouldSelectNegativeOffset() {
        return MAX_NEGATIVE_OFFSET > 0
                && PseudoRandom.randomBoolean(probability(NEGATIVE_OFFSET_SHARE_PERCENT));
    }

    private static boolean hasTargetCandidates(IRNodeBuilder builder) {
        return hasTargetCandidates(builder, 0);
    }

    private static boolean hasTargetCandidates(IRNodeBuilder builder, int offset) {
        for (Type type : fillStubElementTypes()) {
            if (TypeArray.isElementTypeAllowed(type)
                    && builder.hasOffsetIterationIndexedArrayElementCandidates(type, offset)) {
                return true;
            }
        }
        return false;
    }

    private static List<Type> fillStubElementTypes() {
        return List.of(TypeList.BOOLEAN, TypeList.BYTE, TypeList.CHAR,
                TypeList.SHORT, TypeList.INT, TypeList.FLOAT);
    }

    private static Optional<String> hardRejectionReason(FlowParams flowParams, IRNodeBuilder builder) {
        if (!flowParams.inArrayKernel()) {
            return Optional.of("not-array-kernel");
        }
        Optional<Type> iterationType = flowParams.iterationVariableType();
        if (iterationType.isEmpty()) {
            return Optional.of("missing-iteration-type");
        }
        if (builder != null && !hasTargetCandidates(builder)) {
            return Optional.of("no-target");
        }
        return Optional.empty();
    }

    private static Optional<String> preferredRejectionReason(FlowParams flowParams) {
        if (!flowParams.arrayKernelForward()) {
            return Optional.of("not-forward");
        }
        Optional<Type> iterationType = flowParams.iterationVariableType();
        if (iterationType.isEmpty()) {
            return Optional.of("missing-iteration-type");
        }
        Type type = iterationType.get();
        if (!type.equals(TypeList.INT) && !type.equals(TypeList.SHORT)) {
            return Optional.of("unsupported-iteration-type-" + type.getName());
        }
        if (flowParams.codeContext() != FlowParams.CodeContext.TEST
                && flowParams.codeContext() != FlowParams.CodeContext.CONSTRUCTOR) {
            return Optional.of("bad-context-" + flowParams.codeContext());
        }
        return Optional.empty();
    }

    private static void attachDiagnostic(StatementSequence sequence, String diagnostic) {
        if (ProductionParams.debugMorphSourceDiagnostics.value()) {
            SourceDiagnostics.attach(sequence, diagnostic);
        }
    }

    private static long newId() {
        return Genome.createOrConsumeTemplateGene(TEMPLATE_ID_CHANNEL, PseudoRandom.nextLongSilent());
    }

    private static int pickLegCount() {
        int roll = PseudoRandom.randomNotNegative(1000);
        if (roll < 900) {
            return 1;
        }
        if (roll < 980) {
            return 2;
        }
        if (roll < 995) {
            return 3;
        }
        return 4;
    }

    private static boolean pickWholeBlock() {
        return PseudoRandom.randomBoolean(WHOLE_BLOCK_PROBABILITY);
    }

    private static boolean shouldCreate(FlowParams flowParams) {
        double probability = normalizeProbability(flowParams
                .loopIntrinsificationMorphTemplateCreationProbability());
        if (probability <= 0.0) {
            return false;
        }
        boolean liveChoice = PseudoRandom.randomSilent() < probability;
        return Genome.createOrConsumeBooleanDecisionGene("morph.loop_intrinsification.create", liveChoice);
    }

    private static boolean shouldExploreNonViable() {
        double probability = probability(NON_VIABLE_EXPLORATION_PROBABILITY_PERCENT);
        if (probability <= 0.0) {
            return false;
        }
        boolean liveChoice = PseudoRandom.randomSilent() < probability;
        return Genome.createOrConsumeBooleanDecisionGene("morph.loop_intrinsification.explore_non_viable",
                liveChoice);
    }

    private static double probability(int percent) {
        return normalizeProbability(percent / 100.0);
    }

    private static double normalizeProbability(double value) {
        if (Double.isNaN(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }
}
