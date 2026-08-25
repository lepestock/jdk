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

import jdk.test.lib.jittester.Block;
import jdk.test.lib.jittester.Declaration;
import jdk.test.lib.jittester.FlowParams;
import jdk.test.lib.jittester.GenerationState;
import jdk.test.lib.jittester.IRNode;
import jdk.test.lib.jittester.LocalVariable;
import jdk.test.lib.jittester.ProductionParams;
import jdk.test.lib.jittester.ProductionFailedException;
import jdk.test.lib.jittester.StatementSequence;
import jdk.test.lib.jittester.Symbol;
import jdk.test.lib.jittester.SymbolTable;
import jdk.test.lib.jittester.SynchronizedBlock;
import jdk.test.lib.jittester.TypeList;
import jdk.test.lib.jittester.VariableInfo;
import jdk.test.lib.jittester.VariableInitialization;
import jdk.test.lib.jittester.types.TypeKlass;
import jdk.test.lib.jittester.diagnostics.SourceDiagnostics;
import jdk.test.lib.jittester.factories.IRNodeBuilder;
import jdk.test.lib.jittester.utils.Genome;
import jdk.test.lib.jittester.utils.MagnetGene;
import jdk.test.lib.jittester.utils.PseudoRandom;
import jdk.test.lib.jittester.utils.SymbolMagnetism;
import jdk.test.lib.jittester.utils.TypeBoxingUtil;

public record LockEliminationMorphTemplate(long id, int nextLeg, long lockMagnet) implements MorphTemplate {
    private static final String TEMPLATE_ID_CHANNEL = "morph.lock_elimination.id";
    private static final String LOCK_MAGNET_CHANNEL = "morph.lock_elimination.lock";
    private static final int LEG_COUNT = 1;

    public LockEliminationMorphTemplate() {
        this(newId(), 0, Genome.createOrConsumeMagnetGene(LOCK_MAGNET_CHANNEL, MagnetGene.LOCAL_REFERENCE));
    }

    public static boolean canBeCreated(FlowParams flowParams) {
        return flowParams.codeContext() != FlowParams.CodeContext.STATIC_INITIALIZER;
    }

    @Override
    public String creationDiagnostic() {
        return "LockEliminationMorph id=" + id + " created";
    }

    @Override
    public MorphLegResult produceLeg(int leg, IRNodeBuilder builder)
            throws ProductionFailedException {
        if (leg < 0 || leg >= LEG_COUNT) {
            throw new ProductionFailedException();
        }
        Declaration lockDeclaration = null;
        VariableInfo createdLockInfo = null;
        if (shouldCreateLockVariable()) {
            lockDeclaration = produceLockDeclaration(builder);
            VariableInitialization initialization = (VariableInitialization) lockDeclaration.getChild(0);
            createdLockInfo = initialization.getVariableInfo();
        }
        Block separator = produceSeparator(builder);
        VariableInfo lockInfo = selectLockVariable();
        Block synchronizedBody = produceSynchronizedBody(builder);
        SynchronizedBlock synchronizedBlock = new SynchronizedBlock(new LocalVariable(lockInfo), synchronizedBody);
        List<IRNode> statements = new ArrayList<>();
        if (lockDeclaration != null) {
            statements.add(lockDeclaration);
        }
        statements.addAll(separator.getChildren());
        statements.add(synchronizedBlock);
        StatementSequence legSequence = new StatementSequence(statements,
                builder.currentLevel() + 1);
        attachDiagnostic(legSequence, "LockEliminationMorph id=" + id
                + " leg=" + leg
                + " magnetKind=" + MagnetGene.kindName(MagnetGene.kindPart(lockMagnet))
                + " magnet=" + Long.toUnsignedString(lockMagnet)
                + " createdVar=" + (createdLockInfo == null ? "<none>" : createdLockInfo.name)
                + " var=" + lockInfo.name
                + " varType=" + lockInfo.type.getName());
        return new MorphLegResult(legSequence,
                new LockEliminationMorphTemplate(id, leg + 1, lockMagnet), leg + 1 >= LEG_COUNT);
    }

    private Declaration produceLockDeclaration(IRNodeBuilder builder) throws ProductionFailedException {
        Declaration declaration = new IRNodeBuilder()
                .setOwnerKlass(builder.currentOwnerKlass())
                .setResultType(TypeList.VOID)
                .setIsLocal(true)
                .setIsConstant(false)
                .setExceptionSafe(false)
                .produceReferenceTypeDeclaration(LockEliminationMorphTemplate::isLockDeclarationTypeCandidate);
        VariableInitialization initialization = (VariableInitialization) declaration.getChild(0);
        VariableInfo lockInfo = initialization.getVariableInfo();
        lockInfo.setMagnetismGeneId(MagnetGene.idPart(lockMagnet));
        return declaration;
    }

    private boolean shouldCreateLockVariable() {
        return PseudoRandom.randomNotNegative(100)
                < ProductionParams.morphLockEliminationCreateLockVarProbability.value();
    }

    private VariableInfo selectLockVariable() throws ProductionFailedException {
        ArrayList<VariableInfo> candidates = new ArrayList<>();
        for (Symbol symbol : SymbolTable.getAllCombined(VariableInfo.class)) {
            VariableInfo variableInfo = (VariableInfo) symbol;
            if ((variableInfo.flags & VariableInfo.LOCAL) == 0
                    || (variableInfo.flags & VariableInfo.INITIALIZED) == 0
                    || "this".equals(variableInfo.name)
                    || !(variableInfo.type instanceof TypeKlass typeKlass)
                    || !isLockDeclarationTypeCandidate(typeKlass)) {
                continue;
            }
            candidates.add(variableInfo);
        }
        if (candidates.isEmpty()) {
            throw new ProductionFailedException();
        }
        return SymbolMagnetism.orderByKindedMagneticPreference(candidates, lockMagnet).get(0);
    }

    private Block produceSeparator(IRNodeBuilder builder) throws ProductionFailedException {
        return produceTemplateOwnedBlock(builder);
    }

    private Block produceSynchronizedBody(IRNodeBuilder builder) throws ProductionFailedException {
        return produceTemplateOwnedBlock(builder);
    }

    private Block produceTemplateOwnedBlock(IRNodeBuilder builder) throws ProductionFailedException {
        FlowParams flowParams = GenerationState.currentFlowParams();
        double deterioration = ProductionParams.morphTemplateReferenceDeteriorationPercent.value() / 100.0;
        int statementLimit = Math.max(1, (int) Math.ceil(flowParams.statementLimit() * deterioration));
        int operatorLimit = Math.max(1, (int) Math.ceil(flowParams.operatorLimit() * deterioration));
        return new IRNodeBuilder()
                .setOwnerKlass(builder.currentOwnerKlass())
                .setResultType(TypeList.VOID)
                .setCanHaveReturn(false)
                .setCanHaveThrow(false)
                .setCanHaveBreaks(false)
                .setCanHaveContinues(false)
                .setExceptionSafe(false)
                .setNoConsts(false)
                .setLevel(builder.currentLevel() + 1)
                .withStatementLimit(statementLimit)
                .withOperatorLimit(operatorLimit)
                .withMtParameters(flowParams.mtCreationProbability() * deterioration,
                        flowParams.mtLegWeight() * deterioration)
                .produceBlock();
    }

    private static boolean isLockDeclarationTypeCandidate(TypeKlass typeKlass) {
        return !typeKlass.isValueKlass()
                && !typeKlass.isInterface()
                && !typeKlass.isAbstract()
                && !TypeBoxingUtil.isWrapperType(typeKlass);
    }

    private static void attachDiagnostic(StatementSequence sequence, String diagnostic) {
        if (ProductionParams.debugMorphSourceDiagnostics.value()) {
            SourceDiagnostics.attach(sequence, diagnostic);
        }
    }

    private static long newId() {
        return Genome.createOrConsumeTemplateGene(TEMPLATE_ID_CHANNEL, PseudoRandom.nextLongSilent());
    }

}
