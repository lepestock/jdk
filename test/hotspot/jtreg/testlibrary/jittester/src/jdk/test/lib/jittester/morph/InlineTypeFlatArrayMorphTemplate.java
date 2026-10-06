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
import java.util.Set;

import jdk.test.lib.jittester.BinaryOperator;
import jdk.test.lib.jittester.Block;
import jdk.test.lib.jittester.Declaration;
import jdk.test.lib.jittester.FieldDeclarationSequence;
import jdk.test.lib.jittester.FlowParams;
import jdk.test.lib.jittester.GenerationState;
import jdk.test.lib.jittester.IRNode;
import jdk.test.lib.jittester.Literal;
import jdk.test.lib.jittester.LiteralInitializer;
import jdk.test.lib.jittester.LocalVariable;
import jdk.test.lib.jittester.Nothing;
import jdk.test.lib.jittester.OperatorKind;
import jdk.test.lib.jittester.ProductionFailedException;
import jdk.test.lib.jittester.ProductionParams;
import jdk.test.lib.jittester.RawJavaStatement;
import jdk.test.lib.jittester.Rule;
import jdk.test.lib.jittester.Statement;
import jdk.test.lib.jittester.StatementSequence;
import jdk.test.lib.jittester.StaticMemberVariable;
import jdk.test.lib.jittester.Symbol;
import jdk.test.lib.jittester.SymbolTable;
import jdk.test.lib.jittester.Type;
import jdk.test.lib.jittester.TypeList;
import jdk.test.lib.jittester.UnaryOperator;
import jdk.test.lib.jittester.VariableInfo;
import jdk.test.lib.jittester.VariableInitialization;
import jdk.test.lib.jittester.collections.CollectionElement;
import jdk.test.lib.jittester.collections.CollectionInitializer;
import jdk.test.lib.jittester.collections.IndexedStorageKind;
import jdk.test.lib.jittester.diagnostics.SourceDiagnostics;
import jdk.test.lib.jittester.factories.IRNodeBuilder;
import jdk.test.lib.jittester.loops.CounterInitializer;
import jdk.test.lib.jittester.loops.CounterManipulator;
import jdk.test.lib.jittester.loops.For;
import jdk.test.lib.jittester.loops.Loop;
import jdk.test.lib.jittester.loops.LoopingCondition;
import jdk.test.lib.jittester.types.TypeArray;
import jdk.test.lib.jittester.types.TypeKlass;
import jdk.test.lib.jittester.utils.Genome;
import jdk.test.lib.jittester.utils.PseudoRandom;

public record InlineTypeFlatArrayMorphTemplate(long id, int nextLeg, int kind,
                                               TypeKlass elementType,
                                               String loadArrayName,
                                               String storeArrayName,
                                               int loadArrayLength,
                                               int storeArrayLength,
                                               boolean arrayKernelLegCreated) implements MorphTemplate {
    private static final String TEMPLATE_ID_CHANNEL = "morph.inline_type_flat_array.id";
    private static final String TEMPLATE_KIND_CHANNEL = "morph.inline_type_flat_array.kind";
    private static final String TEMPLATE_ELEMENT_TYPE_CHANNEL = "morph.inline_type_flat_array.element_type";
    private static final int KIND_STORE_VALUE = 0;
    private static final int KIND_LOAD_STORE = 1;
    private static final int STORE_VALUE_KIND_WEIGHT = 1;
    private static final int LOAD_STORE_KIND_WEIGHT = 3;
    private static final double STOP_AFTER_LEG_PROBABILITY = 0.75;
    // Keep a small chance to interweave class-scoped MTFA legs with arrays
    // produced by other templates or ordinary code. This is not a tuning knob yet.
    private static final double OTHER_ARRAY_PROBABILITY = 0.20;
    // Class-scoped MTFA templates materialize executable synthetic AKs. The AK
    // body receives local MTFA pressure, capped by repeat probability to avoid
    // flooding short AK bodies with templates that cannot produce legs.
    private static final double DEDICATED_AK_MTFA_PROBABILITY = 0.50;
    private static final int DEDICATED_AK_MT_REPEAT_PROBABILITY_PERCENT = 10;
    private static final int DEDICATED_AK_MAX_TRIP_COUNT = 128;
    private static final int INTERLEAVED_FILLER_MAX_STATEMENTS = 2;
    private static final int INTERLEAVED_FILLER_OPERATOR_LIMIT_PERCENT = 25;
    // MTFA needs class-level field legs to seed array endpoints, but executable
    // block legs should stay much more aggressive. Keep this template-local
    // unless several morph templates need profile-level scope weighting.
    private static final double FIELD_DECLARATION_LEG_WEIGHT_MULTIPLIER = 8.0;
    private static final double BLOCK_LEG_WEIGHT_MULTIPLIER = 80.0;

    public InlineTypeFlatArrayMorphTemplate() {
        this(newId(), 0, KIND_STORE_VALUE, null, null, null, 0, 0, false);
    }

    public static InlineTypeFlatArrayMorphTemplate createOrNull(FlowParams flowParams,
            IRNodeBuilder builder) {
        if (!shouldCreate(flowParams, builder)) {
            return null;
        }
        return create(flowParams, builder);
    }

    public static boolean shouldCreate(FlowParams flowParams, IRNodeBuilder builder) {
        return canBeCreated(flowParams, builder) && shouldCreate(flowParams);
    }

    public static InlineTypeFlatArrayMorphTemplate create(FlowParams flowParams,
            IRNodeBuilder builder) {
        int kind = pickKind(hasBlockLoadStoreTargetCandidates(builder));
        TypeKlass elementType = kind == KIND_LOAD_STORE
                ? pickElementType(blockLoadStoreElementTypes(builder))
                : pickElementType(targetElementTypes(builder));
        if (elementType == null) {
            return null;
        }
        return new InlineTypeFlatArrayMorphTemplate(newId(), 0, kind, elementType, null, null, 0, 0, false);
    }

    public static InlineTypeFlatArrayMorphTemplate createClassScopedOrNull(FlowParams flowParams,
            IRNodeBuilder builder) {
        if (!hasClassScopeTargetCandidates(builder) || !shouldCreate(flowParams)) {
            return null;
        }
        int kind = pickKind(true);
        TypeKlass elementType = pickElementType(classScopeElementTypes(builder));
        if (elementType == null) {
            return null;
        }
        return new InlineTypeFlatArrayMorphTemplate(newId(), 0, kind, elementType, null, null, 0, 0, false);
    }

    public static boolean canBeCreated(FlowParams flowParams, IRNodeBuilder builder) {
        // Reverse loops are plausible future inputs for this morph, but they
        // currently trigger JDK-8375645. Keep MTFA restricted to forward AKs
        // until C2 support and crash status are clear.
        return flowParams.inArrayKernel() && flowParams.arrayKernelForward() && hasTargetCandidates(builder);
    }

    @Override
    public String creationDiagnostic() {
        return "InlineTypeFlatArrayMorph id=" + id + " created kind=" + kindName()
                + " elementType=" + (elementType == null ? "<auto>" : elementType.getName());
    }

    @Override
    public double legSelectionWeight() {
        return Math.max(0, ProductionParams.morphTemplateInlineTypeFlatArrayLegSelectionWeight.value()) / 100.0;
    }

    @Override
    public double legWeightMultiplier() {
        return BLOCK_LEG_WEIGHT_MULTIPLIER;
    }

    @Override
    public double legWeightMultiplier(MorphLegTarget target) {
        return switch (target) {
            case FIELD_DECLARATION -> FIELD_DECLARATION_LEG_WEIGHT_MULTIPLIER;
            case BLOCK -> BLOCK_LEG_WEIGHT_MULTIPLIER;
        };
    }

    @Override
    public boolean canProduceLeg(MorphLegTarget target, IRNodeBuilder builder) {
        return switch (target) {
            case BLOCK -> canProduceBlockLeg(builder);
            case FIELD_DECLARATION -> canProduceFieldDeclarationLeg();
        };
    }

    @Override
    public MorphLegResult produceLeg(MorphLegTarget target, int leg, IRNodeBuilder builder)
            throws ProductionFailedException {
        return switch (target) {
            case BLOCK -> produceLeg(leg, builder);
            case FIELD_DECLARATION -> produceFieldArrayLeg(leg, builder);
        };
    }

    @Override
    public MorphLegResult produceLeg(int leg, IRNodeBuilder builder)
            throws ProductionFailedException {
        if (leg < 0) {
            throw new ProductionFailedException();
        }
        TypeKlass liveElementType = blockElementType(builder);
        if (!GenerationState.currentFlowParams().inArrayKernel()) {
            return produceArrayKernelLeg(builder, liveElementType, leg);
        }
        StatementSequence sequence = kind == KIND_LOAD_STORE
                ? produceLoadStoreLeg(builder, liveElementType, leg)
                : produceStoreValueLeg(builder, liveElementType, leg);
        int nextLeg = leg + 1;
        boolean exhausted = PseudoRandom.randomBoolean(STOP_AFTER_LEG_PROBABILITY);
        return new MorphLegResult(sequence,
                new InlineTypeFlatArrayMorphTemplate(id, nextLeg, kind, liveElementType,
                        loadArrayName, storeArrayName, loadArrayLength, storeArrayLength,
                        arrayKernelLegCreated), exhausted);
    }

    private MorphLegResult produceFieldArrayLeg(int leg, IRNodeBuilder builder)
            throws ProductionFailedException {
        if (leg < 0 || !canProduceFieldDeclarationLeg()) {
            throw new ProductionFailedException();
        }
        TypeKlass liveElementType = classScopeElementType(builder);
        if (kind == KIND_LOAD_STORE && loadArrayName == null && storeArrayName == null) {
            // A load-store class template needs both endpoints before it can
            // build useful block legs. Field order is not semantically relevant
            // here, so materialize the load/store pair as one class-level leg.
            FieldArrayDeclaration load = produceFieldArrayDeclaration(builder, liveElementType, leg, "load");
            FieldArrayDeclaration store = produceFieldArrayDeclaration(builder, liveElementType, leg + 1, "store");
            FieldDeclarationSequence sequence = new FieldDeclarationSequence(
                    List.of(load.declaration(), store.declaration()), builder.currentLevel());
            return new MorphLegResult(sequence,
                    new InlineTypeFlatArrayMorphTemplate(id, leg + 2, kind, liveElementType,
                            load.name(), store.name(), load.length(), store.length(),
                            arrayKernelLegCreated), false);
        }
        boolean loadArray = kind == KIND_LOAD_STORE && loadArrayName == null;
        String role = loadArray ? "load" : "store";
        FieldArrayDeclaration fieldArray = produceFieldArrayDeclaration(builder, liveElementType, leg, role);
        String nextLoadArrayName = loadArray ? fieldArray.name() : loadArrayName;
        String nextStoreArrayName = loadArray ? storeArrayName : fieldArray.name();
        int nextLoadArrayLength = loadArray ? fieldArray.length() : loadArrayLength;
        int nextStoreArrayLength = loadArray ? storeArrayLength : fieldArray.length();
        return new MorphLegResult(fieldArray.declaration(),
                new InlineTypeFlatArrayMorphTemplate(id, leg + 1, kind, liveElementType,
                        nextLoadArrayName, nextStoreArrayName, nextLoadArrayLength,
                        nextStoreArrayLength, arrayKernelLegCreated), false);
    }

    private MorphLegResult produceArrayKernelLeg(IRNodeBuilder builder, TypeKlass elementType, int leg)
            throws ProductionFailedException {
        SymbolTable.push();
        FlowParams previous = GenerationState.currentFlowParams();
        try {
            ensureOwnArraySymbols(builder.currentOwnerKlass(), elementType);
            IRNode node = withPulseBefore(produceSingleArrayKernelLeg(builder, elementType, leg, previous),
                    "leg-array-kernel", leg,
                    ":mt-kind " + kindName() + " :target " + elementType.getName());
            return new MorphLegResult(node,
                    new InlineTypeFlatArrayMorphTemplate(id, leg + 1, kind, elementType,
                            loadArrayName, storeArrayName, loadArrayLength, storeArrayLength, true),
                    true);
        } finally {
            GenerationState.setCurrentFlowParams(previous);
            SymbolTable.pop();
        }
    }

    private For produceSingleArrayKernelLeg(IRNodeBuilder builder, TypeKlass elementType, int leg,
            FlowParams previous) throws ProductionFailedException {
        int tripCount = dedicatedArrayKernelTripCount(builder.currentOwnerKlass(), elementType);
        Loop loop = new Loop();
        loop.initialization = createDedicatedCounterInitializer(builder.currentOwnerKlass(), 0);
        LocalVariable counter = new LocalVariable(loop.initialization.getVariableInfo());
        String iterationVariable = counter.getVariableInfo().name;
        loop.condition = createDedicatedLoopCondition(counter, tripCount);
        Statement headerInit = createDedicatedCounterHeaderInitializer(counter, 0);
        Statement headerUpdate = createDedicatedCounterHeaderUpdate(counter);
        loop.manipulator = new CounterManipulator(new Statement(new Nothing(), false));
        SymbolTable.add(loop.initialization.getVariableInfo());
        FlowParams akParams = previous.withArrayKernelIterationStart(0)
                .withIterationVariable(iterationVariable)
                .withIterationVariableType(TypeList.INT)
                .withArrayKernelIterationLimit(tripCount)
                .withInArrayKernel(true)
                .withArrayKernelForward(true)
                .withMoreReadOnlyVars(iterationVariable)
                .withMoreIterationVariables(iterationVariable)
                .withEnteredLoop(tripCount)
                .withInlineTypeFlatArrayMorphTemplateCreationProbability(DEDICATED_AK_MTFA_PROBABILITY)
                .withMorphTemplateCreationRepeatProbability(DEDICATED_AK_MT_REPEAT_PROBABILITY_PERCENT)
                .withTaperingBlockTerminalProbability(0.0)
                .advance();
        GenerationState.setCurrentFlowParams(akParams);
        try {
            Block bodyBlock = new IRNodeBuilder()
                    .setOwnerKlass(builder.currentOwnerKlass())
                    .setResultType(TypeList.VOID)
                    .setLevel(builder.currentLevel())
                    .setCanHaveReturn(false)
                    .setCanHaveThrow(false)
                    .setCanHaveBreaks(false)
                    .setCanHaveContinues(false)
                    .produceBlock();
            Block header = new Block(builder.currentOwnerKlass(), TypeList.VOID, List.of(),
                    Math.max(0, builder.currentLevel() - 1));
            Block body2 = new Block(builder.currentOwnerKlass(), TypeList.VOID, List.of(),
                    builder.currentLevel());
            Block body3 = new Block(builder.currentOwnerKlass(), TypeList.VOID, List.of(),
                    builder.currentLevel());
            For loopNode = new For(builder.currentLevel(), loop, tripCount, header,
                    headerInit, headerUpdate, bodyBlock, body2, body3);
            attachDiagnostic(loopNode, "ArrayKernel created by InlineTypeFlatArrayMorph"
                    + " id=" + id + " leg=" + leg + " kind=" + kindName()
                    + " counterType=int forward=true start=0 limit=" + tripCount
                    + " tripCount=" + tripCount);
            attachDiagnostic(loopNode, "InlineTypeFlatArrayMorph id=" + id
                    + " leg=" + leg + " array-kernel kind=" + kindName()
                    + " targetType=" + elementType.getName());
            return loopNode;
        } finally {
            GenerationState.setCurrentFlowParams(previous);
        }
    }

    private FieldArrayDeclaration produceFieldArrayDeclaration(IRNodeBuilder builder,
            TypeKlass liveElementType, int leg, String role) throws ProductionFailedException {
        TypeArray arrayType = new TypeArray(liveElementType, 1, IndexedStorageKind.ARRAY, true);
        VariableInfo arrayInfo = new VariableInfo("var_" + SymbolTable.getNextVariableNumber(),
                builder.currentOwnerKlass(), arrayType,
                VariableInfo.STATIC | VariableInfo.INITIALIZED);
        IRNodeBuilder initBuilder = new IRNodeBuilder()
                .setOwnerKlass(builder.currentOwnerKlass())
                .withOperatorLimit(Math.max(1, GenerationState.currentFlowParams().operatorLimit() / 4))
                .setResultType(arrayType)
                .setExceptionSafe(true)
                .setNoConsts(false);
        CollectionInitializer initializer = initBuilder.getCollectionInitializerFactory(arrayInfo).produce();
        SymbolTable.add(arrayInfo);
        Declaration declaration = new Declaration(new VariableInitialization(arrayInfo, initializer));
        attachDiagnostic(declaration, "InlineTypeFlatArrayMorph id=" + id
                + " leg=" + leg + " field-array role=" + role
                + " kind=" + kindName() + " targetType=" + liveElementType.getName());
        return new FieldArrayDeclaration(declaration, arrayInfo.name,
                arrayInfo.getArrayLength().orElse(DEDICATED_AK_MAX_TRIP_COUNT));
    }

    private record FieldArrayDeclaration(Declaration declaration, String name, int length) { }

    private StatementSequence produceStoreValueLeg(IRNodeBuilder builder, TypeKlass elementType, int leg)
            throws ProductionFailedException {
        IndexedArrayCandidate targetCandidate = selectStoreArrayCandidate(builder.currentOwnerKlass(),
                elementType, 0);
        IRNode value = produceValueExpression(builder, elementType);
        StatementSequence sequence = assignmentSequence(builder, elementType, targetCandidate.element(), value);
        sequence = withPulseBefore(sequence, "leg-store", leg,
                ":mt-kind " + kindName()
                + " :target " + elementType.getName()
                + " :store-array " + targetCandidate.arrayInfo().name
                + " :store-array-origin " + arrayOrigin(targetCandidate.arrayInfo().name, storeArrayName));
        attachDiagnostic(sequence, "InlineTypeFlatArrayMorph id=" + id
                + " leg=" + leg + " store kind=" + kindName()
                + " targetType=" + elementType.getName()
                + " storeArray=" + targetCandidate.arrayInfo().name
                + " storeArrayOrigin=" + arrayOrigin(targetCandidate.arrayInfo().name, storeArrayName));
        return sequence;
    }

    private StatementSequence produceLoadStoreLeg(IRNodeBuilder builder, TypeKlass elementType, int leg)
            throws ProductionFailedException {
        LoadStorePair pair = selectLoadStoreArrayCandidates(builder.currentOwnerKlass(), elementType, 0);
        LoadStoreLegShape legShape = shouldProduceNonTrivialLoadStoreShape()
                ? produceInterleavedLoadStoreLeg(builder, elementType, pair)
                : produceDirectLoadStoreLeg(builder, elementType, pair);
        StatementSequence sequence = legShape.sequence();
        sequence = withPulseBefore(sequence, "leg-load", leg,
                ":mt-kind " + kindName()
                + " :target " + elementType.getName()
                + " :load-array " + pair.src().arrayInfo().name
                + " :store-array " + pair.dst().arrayInfo().name
                + " :array-origin " + loadStoreArrayOrigin(pair)
                + " :shape " + legShape.diagnostic().replace(' ', '-'));
        attachDiagnostic(sequence, "InlineTypeFlatArrayMorph id=" + id
                + " leg=" + leg + " load-store kind=" + kindName()
                + " targetType=" + elementType.getName()
                + " loadArray=" + pair.src().arrayInfo().name
                + " storeArray=" + pair.dst().arrayInfo().name
                + " arrayOrigin=" + loadStoreArrayOrigin(pair)
                + " shape=" + legShape.diagnostic());
        return sequence;
    }

    private LoadStoreLegShape produceDirectLoadStoreLeg(IRNodeBuilder builder, TypeKlass elementType,
            LoadStorePair pair) {
        return new LoadStoreLegShape(new StatementSequence(
                List.of(assignmentStatement(elementType, pair.dst().element(), pair.src().element())),
                builder.currentLevel() + 1), "direct");
    }

    private LoadStoreLegShape produceInterleavedLoadStoreLeg(IRNodeBuilder builder, TypeKlass elementType,
            LoadStorePair pair) {
        VariableInfo tmpInfo = new VariableInfo("var_" + SymbolTable.getNextVariableNumber(),
                builder.currentOwnerKlass(), elementType, VariableInfo.LOCAL | VariableInfo.INITIALIZED);
        SymbolTable.add(tmpInfo);
        Declaration tmpLoad = new Declaration(new VariableInitialization(tmpInfo, pair.src().element()));
        Statement tmpStore = assignmentStatement(elementType, pair.dst().element(), new LocalVariable(tmpInfo));
        ArrayList<IRNode> content = new ArrayList<>();
        content.add(tmpLoad);
        int fillerStatements = addInterleavedFillerStatements(builder, content);
        content.add(tmpStore);
        return new LoadStoreLegShape(new StatementSequence(content, builder.currentLevel() + 1),
                "interleaved fillerStatements=" + fillerStatements);
    }

    private int addInterleavedFillerStatements(IRNodeBuilder builder, ArrayList<IRNode> content) {
        int targetCount = 1 + PseudoRandom.randomNotNegative(INTERLEAVED_FILLER_MAX_STATEMENTS);
        int produced = 0;
        for (int i = 0; i < targetCount; i++) {
            try {
                content.add(produceInterleavedFillerDeclaration(builder, pickInterleavedFillerType()));
                produced++;
            } catch (ProductionFailedException ignored) {
                // The interleaving is exploratory. If a stable filler cannot be
                // built in the current context, keep the load/store leg itself.
            }
        }
        return produced;
    }

    private Declaration produceInterleavedFillerDeclaration(IRNodeBuilder builder, Type type)
            throws ProductionFailedException {
        VariableInfo varInfo = new VariableInfo("var_" + SymbolTable.getNextVariableNumber(),
                builder.currentOwnerKlass(), type, VariableInfo.LOCAL | VariableInfo.INITIALIZED);
        IRNode value = new IRNodeBuilder()
                .setOwnerKlass(builder.currentOwnerKlass())
                .setResultType(type)
                .withOperatorLimit(Math.max(1, GenerationState.currentFlowParams().operatorLimit()
                        * INTERLEAVED_FILLER_OPERATOR_LIMIT_PERCENT / 100))
                .setExceptionSafe(true)
                .setNoConsts(false)
                .produceStableExpression(type);
        SymbolTable.add(varInfo);
        return new Declaration(new VariableInitialization(varInfo, value));
    }

    private static Type pickInterleavedFillerType() {
        Type[] types = { TypeList.INT, TypeList.LONG, TypeList.BOOLEAN, TypeList.FLOAT, TypeList.DOUBLE };
        return types[PseudoRandom.randomNotNegative(types.length)];
    }

    private record LoadStoreLegShape(StatementSequence sequence, String diagnostic) { }

    private IndexedArrayCandidate selectStoreArrayCandidate(TypeKlass ownerClass,
            TypeKlass elementType, int iterationOffset) throws ProductionFailedException {
        if (storeArrayName == null) {
            return pickAnyArrayCandidate(ownerClass, elementType, iterationOffset);
        }
        Optional<IndexedArrayCandidate> ownCandidate = namedNullRestrictedArrayCandidate(ownerClass,
                elementType, storeArrayName, iterationOffset);
        ArrayList<IndexedArrayCandidate> otherCandidates = otherArrayCandidates(ownerClass,
                elementType, iterationOffset, Set.of(storeArrayName));
        if (!otherCandidates.isEmpty() && PseudoRandom.randomBoolean(OTHER_ARRAY_PROBABILITY)) {
            return pickArrayCandidate(otherCandidates);
        }
        if (ownCandidate.isPresent()) {
            return ownCandidate.get();
        }
        if (!otherCandidates.isEmpty()) {
            return pickArrayCandidate(otherCandidates);
        }
        throw new ProductionFailedException();
    }

    private LoadStorePair selectLoadStoreArrayCandidates(TypeKlass ownerClass,
            TypeKlass elementType, int iterationOffset) throws ProductionFailedException {
        ArrayList<IndexedArrayCandidate> candidates = nullRestrictedArrayCandidates(ownerClass,
                elementType, iterationOffset);
        if (loadArrayName != null && storeArrayName != null) {
            Optional<LoadStorePair> ownPair = loadStorePairByName(ownerClass, elementType,
                    iterationOffset, loadArrayName, storeArrayName);
            ArrayList<LoadStorePair> otherPairs = otherLoadStorePairs(candidates,
                    Set.of(loadArrayName, storeArrayName));
            if (!otherPairs.isEmpty() && PseudoRandom.randomBoolean(OTHER_ARRAY_PROBABILITY)) {
                return pickLoadStorePair(otherPairs);
            }
            if (ownPair.isPresent()) {
                return ownPair.get();
            }
            if (!otherPairs.isEmpty()) {
                return pickLoadStorePair(otherPairs);
            }
            throw new ProductionFailedException();
        }
        return pickAnyLoadStorePair(candidates);
    }

    private static StatementSequence assignmentSequence(IRNodeBuilder builder, TypeKlass elementType,
            IRNode target, IRNode value) {
        return new StatementSequence(List.of(assignmentStatement(elementType, target, value)),
                builder.currentLevel() + 1);
    }

    private static Statement assignmentStatement(TypeKlass elementType, IRNode target, IRNode value) {
        return new Statement(new BinaryOperator(OperatorKind.ASSIGN, elementType, target, value), true);
    }

    private static TypeKlass selectElementType(IRNodeBuilder builder) throws ProductionFailedException {
        TypeKlass elementType = pickElementType(targetElementTypes(builder));
        if (elementType == null) {
            throw new ProductionFailedException();
        }
        return elementType;
    }

    private static boolean hasTargetCandidates(IRNodeBuilder builder) {
        return !targetElementTypes(builder).isEmpty();
    }

    private static boolean hasBlockLoadStoreTargetCandidates(IRNodeBuilder builder) {
        return !blockLoadStoreElementTypes(builder).isEmpty();
    }

    private static boolean hasClassScopeTargetCandidates(IRNodeBuilder builder) {
        return !classScopeElementTypes(builder).isEmpty();
    }

    private boolean canProduceBlockLeg(IRNodeBuilder builder) {
        FlowParams flowParams = GenerationState.currentFlowParams();
        if (!flowParams.inArrayKernel()) {
            return canProduceDedicatedArrayKernelLeg();
        }
        if (!flowParams.arrayKernelForward()) {
            return false;
        }
        if (elementType == null) {
            return canBeCreated(flowParams, builder);
        }
        if (kind == KIND_LOAD_STORE) {
            if (loadArrayName != null && storeArrayName != null) {
                return namedNullRestrictedArrayCandidate(builder.currentOwnerKlass(), elementType,
                        loadArrayName, 0).isPresent()
                        && namedNullRestrictedArrayCandidate(builder.currentOwnerKlass(), elementType,
                                storeArrayName, 0).isPresent();
            }
            return nullRestrictedArrayCandidates(builder.currentOwnerKlass(), elementType, 0).size() >= 2;
        }
        if (storeArrayName != null) {
            return namedNullRestrictedArrayCandidate(builder.currentOwnerKlass(), elementType,
                    storeArrayName, 0).isPresent();
        }
        return builder.hasOffsetIterationIndexedNullRestrictedArrayElementCandidates(elementType, 0);
    }

    private boolean canProduceDedicatedArrayKernelLeg() {
        return !arrayKernelLegCreated && elementType != null && (kind == KIND_LOAD_STORE
                ? loadArrayName != null && storeArrayName != null && !loadArrayName.equals(storeArrayName)
                : storeArrayName != null);
    }

    private boolean canProduceFieldDeclarationLeg() {
        if (elementType == null) {
            return false;
        }
        return kind == KIND_LOAD_STORE
                ? loadArrayName == null || storeArrayName == null
                : storeArrayName == null;
    }

    private TypeKlass blockElementType(IRNodeBuilder builder) throws ProductionFailedException {
        if (elementType != null) {
            return elementType;
        }
        TypeKlass liveElementType = kind == KIND_LOAD_STORE
                ? pickElementType(blockLoadStoreElementTypes(builder))
                : selectElementType(builder);
        if (liveElementType == null) {
            throw new ProductionFailedException();
        }
        return liveElementType;
    }

    private TypeKlass classScopeElementType(IRNodeBuilder builder) throws ProductionFailedException {
        if (elementType != null) {
            return elementType;
        }
        TypeKlass liveElementType = pickElementType(classScopeElementTypes(builder));
        if (liveElementType == null) {
            throw new ProductionFailedException();
        }
        return liveElementType;
    }

    private static IRNode produceValueExpression(IRNodeBuilder builder, TypeKlass elementType)
            throws ProductionFailedException {
        IRNodeBuilder valueBuilder = builder
                .withOperatorLimit(Math.max(1, GenerationState.currentFlowParams().operatorLimit() / 3))
                .setResultType(elementType)
                .setExceptionSafe(false)
                .setNoConsts(false);
        Rule<IRNode> rule = new Rule<>("inline_type_flat_array_rhs");
        rule.add("constructor", new FactoryAdapter(() -> valueBuilder.produceDefaultConstructor(elementType)), 1.0);
        rule.add("standalone_variable", valueBuilder.setIsConstant(false).setIsInitialized(true).getVariableFactory(), 5.0);
        rule.add("array_element", valueBuilder.getCollectionElementFactory(), 4.0);
        rule.add("ak_array_element",
                valueBuilder.getAssignmentCompatibleOffsetIterationIndexedArrayElementFactory(elementType, 0), 4.0);
        rule.add("function", valueBuilder.getFunctionFactory(), 3.0);
        rule.add("expression", valueBuilder.getLimitedExpressionFactory(), 2.0);
        return rule.produce();
    }

    private static ArrayList<TypeKlass> targetElementTypes(IRNodeBuilder builder) {
        ArrayList<TypeKlass> candidates = new ArrayList<>();
        for (Type type : TypeList.getAll()) {
            if (!(type instanceof TypeKlass typeKlass)) {
                continue;
            }
            if (!typeKlass.isValueKlass() || typeKlass.isAbstract() || typeKlass.isInterface()) {
                continue;
            }
            if (!builder.hasDefaultConstructor(typeKlass)) {
                continue;
            }
            if (builder.hasOffsetIterationIndexedNullRestrictedArrayElementCandidates(typeKlass, 0)) {
                candidates.add(typeKlass);
            }
        }
        return candidates;
    }

    private static ArrayList<TypeKlass> blockLoadStoreElementTypes(IRNodeBuilder builder) {
        ArrayList<TypeKlass> candidates = new ArrayList<>();
        for (TypeKlass type : targetElementTypes(builder)) {
            if (nullRestrictedArrayCandidates(builder.currentOwnerKlass(), type, 0).size() >= 2) {
                candidates.add(type);
            }
        }
        return candidates;
    }

    private static ArrayList<TypeKlass> classScopeElementTypes(IRNodeBuilder builder) {
        ArrayList<TypeKlass> candidates = new ArrayList<>();
        for (Type type : TypeList.getAll()) {
            if (!(type instanceof TypeKlass typeKlass)) {
                continue;
            }
            if (!typeKlass.isValueKlass() || typeKlass.isAbstract() || typeKlass.isInterface()) {
                continue;
            }
            if (!TypeArray.isElementTypeAllowed(typeKlass) || !builder.hasDefaultConstructor(typeKlass)) {
                continue;
            }
            candidates.add(typeKlass);
        }
        return candidates;
    }

    private static TypeKlass pickElementType(ArrayList<TypeKlass> candidates) {
        if (candidates.isEmpty()) {
            return null;
        }
        long rawIndex = Genome.createOrConsumeTemplateGene(TEMPLATE_ELEMENT_TYPE_CHANNEL,
                PseudoRandom.randomNotNegative(candidates.size()));
        return candidates.get(Math.floorMod(rawIndex, candidates.size()));
    }

    private static ArrayList<IndexedArrayCandidate> nullRestrictedArrayCandidates(
            TypeKlass ownerClass, TypeKlass elementType, int iterationOffset) {
        ArrayList<IndexedArrayCandidate> candidates = new ArrayList<>();
        String iterationVariable = GenerationState.currentFlowParams().iterationVariable();
        if (iterationVariable == null || iterationVariable.isBlank()) {
            return candidates;
        }
        Symbol iterationSymbol = SymbolTable.get(iterationVariable, VariableInfo.class);
        if (!(iterationSymbol instanceof VariableInfo iterationInfo)
                || !TypeArray.isElementTypeAllowed(elementType)) {
            return candidates;
        }
        for (Symbol symbol : SymbolTable.getAllCombined(VariableInfo.class)) {
            if (!(symbol instanceof VariableInfo varInfo)) {
                continue;
            }
            if ((varInfo.flags & VariableInfo.INITIALIZED) == 0) {
                continue;
            }
            if (!(varInfo.type instanceof TypeArray arrayType)
                    || !arrayType.isNullRestricted()
                    || arrayType.dimensions != 1
                    || !arrayType.type.equals(elementType)
                    || arrayType.getStorageKind() != IndexedStorageKind.ARRAY) {
                continue;
            }
            if (varInfo.getArrayLength().isEmpty() || !canUseIterationIndex(varInfo, iterationOffset)) {
                continue;
            }
            IRNode baseArray;
            if (varInfo.isLocal()) {
                baseArray = new LocalVariable(varInfo);
            } else if (varInfo.isStatic()) {
                baseArray = new StaticMemberVariable(ownerClass, varInfo);
            } else {
                continue;
            }
            candidates.add(new IndexedArrayCandidate(varInfo,
                    indexedElement(baseArray, iterationInfo, iterationOffset, arrayType.getStorageKind())));
        }
        return candidates;
    }

    private int dedicatedArrayKernelTripCount(TypeKlass ownerClass, TypeKlass elementType)
            throws ProductionFailedException {
        if (kind == KIND_LOAD_STORE) {
            ArrayList<VariableInfo> candidates = nullRestrictedArrayVariables(ownerClass, elementType);
            if (loadArrayName != null && storeArrayName != null) {
                Optional<VariableInfo> load = namedNullRestrictedArrayVariable(ownerClass, elementType,
                        loadArrayName);
                Optional<VariableInfo> store = namedNullRestrictedArrayVariable(ownerClass, elementType,
                        storeArrayName);
                if (load.isPresent() && store.isPresent() && !load.get().equals(store.get())) {
                    return commonTripCount(load.get(), store.get());
                }
            }
            if (candidates.size() < 2) {
                throw new ProductionFailedException();
            }
            VariableInfo first = candidates.get(PseudoRandom.randomNotNegative(candidates.size()));
            candidates.remove(first);
            VariableInfo second = candidates.get(PseudoRandom.randomNotNegative(candidates.size()));
            return commonTripCount(first, second);
        }
        Optional<VariableInfo> target = storeArrayName == null
                ? pickNullRestrictedArrayVariable(ownerClass, elementType)
                : namedNullRestrictedArrayVariable(ownerClass, elementType, storeArrayName);
        if (target.isEmpty()) {
            throw new ProductionFailedException();
        }
        return tripCount(target.get());
    }

    private static Optional<VariableInfo> pickNullRestrictedArrayVariable(TypeKlass ownerClass,
            TypeKlass elementType) {
        ArrayList<VariableInfo> candidates = nullRestrictedArrayVariables(ownerClass, elementType);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(candidates.get(PseudoRandom.randomNotNegative(candidates.size())));
    }

    private static Optional<VariableInfo> namedNullRestrictedArrayVariable(TypeKlass ownerClass,
            TypeKlass elementType, String arrayName) {
        if (arrayName == null || arrayName.isBlank()) {
            return Optional.empty();
        }
        return nullRestrictedArrayVariables(ownerClass, elementType).stream()
                .filter(candidate -> arrayName.equals(candidate.name))
                .findFirst();
    }

    private void ensureOwnArraySymbols(TypeKlass ownerClass, TypeKlass elementType) {
        if (kind == KIND_LOAD_STORE) {
            ensureArraySymbol(ownerClass, elementType, loadArrayName, loadArrayLength);
            ensureArraySymbol(ownerClass, elementType, storeArrayName, storeArrayLength);
        } else {
            ensureArraySymbol(ownerClass, elementType, storeArrayName, storeArrayLength);
        }
    }

    private static void ensureArraySymbol(TypeKlass ownerClass, TypeKlass elementType,
            String arrayName, int arrayLength) {
        if (arrayName == null || arrayName.isBlank()) {
            return;
        }
        if (SymbolTable.get(arrayName, VariableInfo.class) instanceof VariableInfo) {
            return;
        }
        TypeArray arrayType = new TypeArray(elementType, 1, IndexedStorageKind.ARRAY, true);
        VariableInfo arrayInfo = new VariableInfo(arrayName, ownerClass, arrayType,
                VariableInfo.STATIC | VariableInfo.INITIALIZED);
        if (arrayLength > 0) {
            arrayInfo.setArrayLength(arrayLength);
        }
        SymbolTable.add(arrayInfo);
    }

    private static ArrayList<VariableInfo> nullRestrictedArrayVariables(TypeKlass ownerClass,
            TypeKlass elementType) {
        ArrayList<VariableInfo> candidates = new ArrayList<>();
        if (!TypeArray.isElementTypeAllowed(elementType)) {
            return candidates;
        }
        for (Symbol symbol : SymbolTable.getAllCombined(VariableInfo.class)) {
            if (!(symbol instanceof VariableInfo varInfo)) {
                continue;
            }
            if ((varInfo.flags & VariableInfo.INITIALIZED) == 0) {
                continue;
            }
            if (!(varInfo.type instanceof TypeArray arrayType)
                    || !arrayType.isNullRestricted()
                    || arrayType.dimensions != 1
                    || !arrayType.type.equals(elementType)
                    || arrayType.getStorageKind() != IndexedStorageKind.ARRAY) {
                continue;
            }
            if (varInfo.getArrayLength().isEmpty()) {
                continue;
            }
            if (varInfo.isLocal() || varInfo.isStatic()) {
                candidates.add(varInfo);
            }
        }
        return candidates;
    }

    private static int commonTripCount(VariableInfo first, VariableInfo second)
            throws ProductionFailedException {
        int firstCount = tripCount(first);
        int secondCount = tripCount(second);
        return Math.max(1, Math.min(firstCount, secondCount));
    }

    private static int tripCount(VariableInfo varInfo) throws ProductionFailedException {
        if (varInfo.getArrayLength().isEmpty()) {
            throw new ProductionFailedException();
        }
        return Math.max(1, Math.min(DEDICATED_AK_MAX_TRIP_COUNT, varInfo.getArrayLength().getAsInt()));
    }

    private static CounterInitializer createDedicatedCounterInitializer(TypeKlass ownerClass, int initValue) {
        VariableInfo varInfo = new VariableInfo("var_" + SymbolTable.getNextVariableNumber(),
                ownerClass, TypeList.INT, VariableInfo.LOCAL | VariableInfo.INITIALIZED);
        return new CounterInitializer(varInfo, new LiteralInitializer(initValue, TypeList.INT));
    }

    private static LoopingCondition createDedicatedLoopCondition(LocalVariable counter, int iterationLimit) {
        return new LoopingCondition(new BinaryOperator(OperatorKind.LT, TypeList.BOOLEAN, counter,
                new LiteralInitializer(iterationLimit, TypeList.INT)));
    }

    private static Statement createDedicatedCounterHeaderInitializer(LocalVariable counter, int initValue) {
        return new Statement(new BinaryOperator(OperatorKind.ASSIGN, TypeList.INT, counter,
                new LiteralInitializer(initValue, TypeList.INT)), false);
    }

    private static Statement createDedicatedCounterHeaderUpdate(LocalVariable counter) {
        return new Statement(new UnaryOperator(OperatorKind.POST_INC, counter), false);
    }

    private static Optional<IndexedArrayCandidate> namedNullRestrictedArrayCandidate(
            TypeKlass ownerClass, TypeKlass elementType, String arrayName, int iterationOffset) {
        if (arrayName == null || arrayName.isBlank()) {
            return Optional.empty();
        }
        return nullRestrictedArrayCandidates(ownerClass, elementType, iterationOffset).stream()
                .filter(candidate -> arrayName.equals(candidate.arrayInfo().name))
                .findFirst();
    }

    private static IndexedArrayCandidate pickAnyArrayCandidate(TypeKlass ownerClass,
            TypeKlass elementType, int iterationOffset) throws ProductionFailedException {
        ArrayList<IndexedArrayCandidate> candidates = nullRestrictedArrayCandidates(ownerClass,
                elementType, iterationOffset);
        if (candidates.isEmpty()) {
            throw new ProductionFailedException();
        }
        return pickArrayCandidate(candidates);
    }

    private static ArrayList<IndexedArrayCandidate> otherArrayCandidates(TypeKlass ownerClass,
            TypeKlass elementType, int iterationOffset, Set<String> excludedNames) {
        ArrayList<IndexedArrayCandidate> candidates = nullRestrictedArrayCandidates(ownerClass,
                elementType, iterationOffset);
        candidates.removeIf(candidate -> excludedNames.contains(candidate.arrayInfo().name));
        return candidates;
    }

    private static IndexedArrayCandidate pickArrayCandidate(ArrayList<IndexedArrayCandidate> candidates) {
        return candidates.get(PseudoRandom.randomNotNegative(candidates.size()));
    }

    private static Optional<LoadStorePair> loadStorePairByName(TypeKlass ownerClass,
            TypeKlass elementType, int iterationOffset, String loadArrayName, String storeArrayName) {
        Optional<IndexedArrayCandidate> src = namedNullRestrictedArrayCandidate(ownerClass,
                elementType, loadArrayName, iterationOffset);
        Optional<IndexedArrayCandidate> dst = namedNullRestrictedArrayCandidate(ownerClass,
                elementType, storeArrayName, iterationOffset);
        if (src.isEmpty() || dst.isEmpty() || src.get().arrayInfo().equals(dst.get().arrayInfo())) {
            return Optional.empty();
        }
        return Optional.of(new LoadStorePair(src.get(), dst.get()));
    }

    private static LoadStorePair pickAnyLoadStorePair(ArrayList<IndexedArrayCandidate> candidates)
            throws ProductionFailedException {
        ArrayList<LoadStorePair> pairs = loadStorePairs(candidates);
        if (pairs.isEmpty()) {
            throw new ProductionFailedException();
        }
        return pickLoadStorePair(pairs);
    }

    private static ArrayList<LoadStorePair> otherLoadStorePairs(
            ArrayList<IndexedArrayCandidate> candidates, Set<String> ownArrayNames) {
        ArrayList<LoadStorePair> pairs = loadStorePairs(candidates);
        pairs.removeIf(pair -> ownArrayNames.contains(pair.src().arrayInfo().name)
                && ownArrayNames.contains(pair.dst().arrayInfo().name));
        return pairs;
    }

    private static ArrayList<LoadStorePair> loadStorePairs(ArrayList<IndexedArrayCandidate> candidates) {
        ArrayList<LoadStorePair> pairs = new ArrayList<>();
        for (IndexedArrayCandidate src : candidates) {
            for (IndexedArrayCandidate dst : candidates) {
                if (!src.arrayInfo().equals(dst.arrayInfo())) {
                    pairs.add(new LoadStorePair(src, dst));
                }
            }
        }
        return pairs;
    }

    private static LoadStorePair pickLoadStorePair(ArrayList<LoadStorePair> pairs) {
        return pairs.get(PseudoRandom.randomNotNegative(pairs.size()));
    }

    private static String arrayOrigin(String actualName, String ownName) {
        if (ownName == null) {
            return "ambient";
        }
        return ownName.equals(actualName) ? "own" : "other";
    }

    private String loadStoreArrayOrigin(LoadStorePair pair) {
        if (loadArrayName == null || storeArrayName == null) {
            return "ambient";
        }
        return loadArrayName.equals(pair.src().arrayInfo().name)
                && storeArrayName.equals(pair.dst().arrayInfo().name) ? "own" : "other";
    }

    private static CollectionElement indexedElement(IRNode baseArray, VariableInfo iterationInfo,
            int iterationOffset, IndexedStorageKind storageKind) {
        ArrayList<IRNode> indexes = new ArrayList<>(1);
        indexes.add(indexExpression(iterationInfo, iterationOffset));
        return new CollectionElement(baseArray, indexes, storageKind);
    }

    private static IRNode indexExpression(VariableInfo iterationInfo, int iterationOffset) {
        IRNode index = new LocalVariable(iterationInfo);
        if (iterationOffset == 0) {
            return index;
        }
        if (iterationOffset < 0) {
            return new BinaryOperator(OperatorKind.SUB, TypeList.INT, index,
                    new Literal(-iterationOffset, TypeList.INT));
        }
        return new BinaryOperator(OperatorKind.ADD, TypeList.INT, index,
                new Literal(iterationOffset, TypeList.INT));
    }

    private static boolean canUseIterationIndex(VariableInfo varInfo, int offset) {
        int iterationStart = GenerationState.currentFlowParams().arrayKernelIterationStart();
        int iterationLimit = GenerationState.currentFlowParams().arrayKernelIterationLimit();
        if (iterationStart + offset < 0) {
            return false;
        }
        return iterationLimit <= 0 || varInfo.getArrayLength().orElse(0) >= iterationLimit + offset;
    }

    private static void attachDiagnostic(IRNode node, String diagnostic) {
        if (ProductionParams.debugMorphSourceDiagnostics.value()) {
            SourceDiagnostics.attach(node, diagnostic);
        }
    }

    private StatementSequence withPulseBefore(StatementSequence sequence, String kind, int leg, String details) {
        return (StatementSequence) withPulseBefore((IRNode) sequence, kind, leg, details);
    }

    private IRNode withPulseBefore(IRNode node, String kind, int leg, String details) {
        if (!mtfaPulsemapEnabled()) {
            return node;
        }
        return new StatementSequence(List.of(pulseBeat(kind, leg, details), node), node.getLevel());
    }

    private RawJavaStatement pulseBeat(String kind, int leg, String details) {
        String payload = ":template mtfa :kind " + kind
                + " :id " + id
                + " :leg " + leg
                + (details == null || details.isBlank() ? "" : " " + details);
        return new RawJavaStatement("jdk.test.lib.jittester.pulse.Pulse.beat(\"morph-template\", \""
                + escapeJavaString(payload) + "\");");
    }

    private static boolean mtfaPulsemapEnabled() {
        return ProductionParams.pulsemapEnabled()
                && ProductionParams.morphTemplateInlineTypeFlatArrayPulsemap.value();
    }

    private static String escapeJavaString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static long newId() {
        return Genome.createOrConsumeTemplateGene(TEMPLATE_ID_CHANNEL, PseudoRandom.nextLongSilent());
    }

    private static int pickKind(boolean loadStoreAllowed) {
        if (!loadStoreAllowed) {
            return KIND_STORE_VALUE;
        }
        int totalWeight = STORE_VALUE_KIND_WEIGHT + LOAD_STORE_KIND_WEIGHT;
        long rawKind = Genome.createOrConsumeTemplateGene(TEMPLATE_KIND_CHANNEL,
                PseudoRandom.randomNotNegative(totalWeight));
        return Math.floorMod(rawKind, totalWeight) < LOAD_STORE_KIND_WEIGHT
                ? KIND_LOAD_STORE : KIND_STORE_VALUE;
    }

    private String kindName() {
        return kind == KIND_LOAD_STORE ? "load-store" : "store-value";
    }

    private static boolean shouldCreate(FlowParams flowParams) {
        double probability = normalizeProbability(
                flowParams.inlineTypeFlatArrayMorphTemplateCreationProbability());
        if (probability <= 0.0) {
            return false;
        }
        return PseudoRandom.randomBoolean(probability);
    }

    private static boolean shouldProduceNonTrivialLoadStoreShape() {
        double probability = normalizeProbability(
                ProductionParams.morphTemplateInlineTypeFlatArrayNonTrivialProbability.value() / 100.0);
        return probability > 0.0 && PseudoRandom.randomBoolean(probability);
    }

    private static double normalizeProbability(double value) {
        if (Double.isNaN(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    @FunctionalInterface
    private interface Producer {
        IRNode produce() throws ProductionFailedException;
    }

    private static final class FactoryAdapter
            extends jdk.test.lib.jittester.factories.Factory<IRNode> {
        private final Producer producer;

        private FactoryAdapter(Producer producer) {
            this.producer = producer;
        }

        @Override
        public IRNode produce() throws ProductionFailedException {
            return producer.produce();
        }
    }

    private record IndexedArrayCandidate(VariableInfo arrayInfo, CollectionElement element) {
    }

    private record LoadStorePair(IndexedArrayCandidate src, IndexedArrayCandidate dst) {
    }
}
