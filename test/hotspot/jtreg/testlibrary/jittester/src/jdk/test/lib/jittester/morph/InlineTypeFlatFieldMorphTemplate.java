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

import static jdk.test.lib.jittester.morph.MorphClassLabels.fieldBytes;
import static jdk.test.lib.jittester.morph.MorphClassLabels.payloadBytes;

import java.util.ArrayList;
import java.util.List;

import jdk.test.lib.jittester.BinaryOperator;
import jdk.test.lib.jittester.Declaration;
import jdk.test.lib.jittester.FlowParams;
import jdk.test.lib.jittester.GenerationState;
import jdk.test.lib.jittester.IRNode;
import jdk.test.lib.jittester.LocalVariable;
import jdk.test.lib.jittester.NonStaticMemberVariable;
import jdk.test.lib.jittester.OperatorKind;
import jdk.test.lib.jittester.ProductionFailedException;
import jdk.test.lib.jittester.ProductionParams;
import jdk.test.lib.jittester.Statement;
import jdk.test.lib.jittester.StatementSequence;
import jdk.test.lib.jittester.StaticMemberVariable;
import jdk.test.lib.jittester.SymbolTable;
import jdk.test.lib.jittester.Type;
import jdk.test.lib.jittester.TypeList;
import jdk.test.lib.jittester.VariableDeclaration;
import jdk.test.lib.jittester.VariableInfo;
import jdk.test.lib.jittester.VariableInitialization;
import jdk.test.lib.jittester.classes.ClassDefinitionBlock;
import jdk.test.lib.jittester.classes.ClassField;
import jdk.test.lib.jittester.classes.Klass;
import jdk.test.lib.jittester.diagnostics.SourceDiagnostics;
import jdk.test.lib.jittester.factories.IRNodeBuilder;
import jdk.test.lib.jittester.factories.ThisVariableControl;
import jdk.test.lib.jittester.functions.FunctionInfo;
import jdk.test.lib.jittester.types.TypeArray;
import jdk.test.lib.jittester.types.TypeKlass;
import jdk.test.lib.jittester.utils.Genome;
import jdk.test.lib.jittester.utils.PseudoRandom;
import jdk.test.lib.jittester.utils.TypeBoxingUtil;

public record InlineTypeFlatFieldMorphTemplate(long id, int nextLeg, boolean load,
                                               int useLegCount, TypeKlass payloadType,
                                               TypeKlass holderType, VariableInfo flatField,
                                               VariableInfo sink) implements MorphTemplate {
    private static final int TYPE_LEG = 0;
    private static final int FIELD_LEG = 1;
    private static final int SINK_LEG = 2;
    private static final int FIRST_USE_LEG = 3;
    // Small atomic layouts are the portable flat-field target without relying
    // on @LooselyConsistentValue. Layout selection remains a VM decision.
    private static final int PAYLOAD_BYTES = 8;
    private static final double ANOTHER_FIELD_PROBABILITY = 0.25;
    private static final double AMBIENT_REUSE_PROBABILITY = 0.50;
    private static final double VALUE_RECORD_PROBABILITY = 0.12;
    private static final double AMBIENT_RECEIVER_PROBABILITY = 0.50;
    private static final double OWN_SINK_PROBABILITY = 0.40;
    private static final double LOCAL_SINK_PROBABILITY = 0.35;
    private static final int HELPER_GENERATION_DIVISOR = 4;

    public static List<MorphTemplate> createGentestScopedReserve() {
        int probability = ProductionParams.morphTemplateInlineTypeFlatFieldProbability.value();
        if (probability <= 0 || !PseudoRandom.randomBoolean(Math.min(1.0, probability / 100.0))) {
            return List.of();
        }
        ArrayList<MorphTemplate> templates = new ArrayList<>();
        int reserve = Math.max(0, ProductionParams.morphTemplateGentestScopeReserveSize.value());
        for (int i = 0; i < reserve; i++) {
            templates.add(createGentestScoped());
        }
        return List.copyOf(templates);
    }

    private static InlineTypeFlatFieldMorphTemplate createGentestScoped() {
        long id = Genome.createOrConsumeTemplateGene("morph.inline_type_flat_field.id",
                PseudoRandom.nextLongSilent());
        boolean load = Genome.createOrConsumeTemplateGene("morph.inline_type_flat_field.kind",
                PseudoRandom.randomNotNegative(2)) == 1;
        int limit = Math.max(1, ProductionParams.morphTemplateInlineTypeFlatFieldUseLegLimit.value());
        return new InlineTypeFlatFieldMorphTemplate(id, TYPE_LEG, load,
                1 + PseudoRandom.randomNotNegative(limit), null, null, null, null);
    }

    @Override
    public String creationDiagnostic() {
        return "InlineTypeFlatFieldMorph id=" + id + " created kind=" + kindName()
                + " useLegs=" + useLegCount;
    }

    @Override
    public double legSelectionWeight() {
        return Math.max(0, ProductionParams.morphTemplateInlineTypeFlatFieldLegSelectionWeight.value()) / 100.0;
    }

    @Override
    public double legWeightMultiplier(MorphLegTarget target) {
        return target == MorphLegTarget.BLOCK ? 60.0 : 8.0;
    }

    @Override
    public boolean canProduceLeg(MorphLegTarget target, IRNodeBuilder builder) {
        return switch (target) {
            case TYPE_DECLARATION -> nextLeg == TYPE_LEG;
            case FIELD_DECLARATION -> (nextLeg == FIELD_LEG
                        && !builder.currentOwnerKlass().getName().equals(GenerationState.currentMainClassName()))
                    || (load && nextLeg == SINK_LEG && !builder.currentOwnerKlass().isValueKlass());
            case BLOCK -> (nextLeg >= FIRST_USE_LEG && nextLeg < FIRST_USE_LEG + useLegCount
                        && (load || (flatField.flags & VariableInfo.FINAL) == 0)
                    || load && nextLeg == SINK_LEG)
                    && (GenerationState.currentFlowParams().codeContext() == FlowParams.CodeContext.TEST
                        || GenerationState.currentFlowParams().codeContext() == FlowParams.CodeContext.METHOD);
        };
    }

    @Override
    public MorphLegResult produceLeg(MorphLegTarget target, int leg, IRNodeBuilder builder)
            throws ProductionFailedException {
        if (leg != nextLeg || !canProduceLeg(target, builder)) {
            throw new ProductionFailedException();
        }
        return switch (target) {
            case TYPE_DECLARATION -> produceTypes(builder);
            case FIELD_DECLARATION -> nextLeg == FIELD_LEG ? produceFlatField(builder) : produceSinkField(builder);
            case BLOCK -> produceUse(builder);
        };
    }

    @Override
    public MorphLegResult produceLeg(int leg, IRNodeBuilder builder) throws ProductionFailedException {
        return produceLeg(MorphLegTarget.BLOCK, leg, builder);
    }

    private MorphLegResult produceTypes(IRNodeBuilder builder) throws ProductionFailedException {
        ArrayList<IRNode> declarations = new ArrayList<>();
        List<TypeKlass> ambient = TypeList.getReferenceTypes().stream()
                .filter(TypeKlass::isValueKlass).filter(type -> !type.isAbstract())
                .filter(type -> type.getLabels().stream().anyMatch(label ->
                        label.startsWith(MorphClassLabels.MTFF_PAYLOAD_PREFIX)))
                .filter(type -> payloadBytes(type) > 0 && payloadBytes(type) <= PAYLOAD_BYTES)
                .filter(InlineTypeFlatFieldMorphTemplate::hasConstructor).toList();
        TypeKlass value;
        if (!ambient.isEmpty() && PseudoRandom.randomBoolean(AMBIENT_REUSE_PROBABILITY)) {
            value = PseudoRandom.randomElement(ambient);
        } else {
            List<ClassField> fields = payloadFields();
            IRNodeBuilder typeBuilder = helperBuilder(builder.currentName(), builder.currentLevel());
            Klass payload = fields.stream().noneMatch(ClassField::nullRestricted)
                    && PseudoRandom.randomBoolean(VALUE_RECORD_PROBABILITY)
                    ? typeBuilder.getValueRecordKlassFactory(fields).produce()
                    : typeBuilder.getValueKlassFactory(fields).produce();
            value = payload.getThisKlass();
            declarations.add(payload);
        }
        IRNode node = new ClassDefinitionBlock(declarations, builder.currentLevel());
        diagnostic(node, "payload payloadClass=" + value.getName());
        return new MorphLegResult(node, new InlineTypeFlatFieldMorphTemplate(id, FIELD_LEG, load,
                useLegCount, value, null, null, null), false);
    }

    private MorphLegResult produceFlatField(IRNodeBuilder builder) {
        TypeKlass owner = builder.currentOwnerKlass();
        VariableInfo field = new VariableInfo("var_" + SymbolTable.getNextVariableNumber(), owner,
                payloadType, VariableInfo.INITIALIZED | VariableInfo.NULL_RESTRICTED
                        | (owner.isValueKlass() ? VariableInfo.FINAL : 0));
        SymbolTable.add(field);
        IRNode node = new Declaration(new VariableDeclaration(field));
        diagnostic(node, "flat-field holderClass=" + owner.getName());
        boolean constructorOnly = !load && owner.isValueKlass();
        return new MorphLegResult(node, new InlineTypeFlatFieldMorphTemplate(id,
                load ? SINK_LEG : FIRST_USE_LEG, load, useLegCount,
                payloadType, owner, field, null), constructorOnly);
    }

    private MorphLegResult produceSinkField(IRNodeBuilder builder) throws ProductionFailedException {
        TypeKlass owner = builder.currentOwnerKlass();
        boolean staticField = !ProductionParams.disableStatic.value() && PseudoRandom.randomBoolean();
        IRNode initializer;
        ThisVariableControl.pushForbidThis();
        try {
            initializer = new IRNodeBuilder().setOwnerKlass(owner).setResultType(payloadType)
                    .setExceptionSafe(true).setNoConsts(false).getConstructorCallFactory().produce();
        } finally {
            ThisVariableControl.popForbidThis();
        }
        VariableInfo sinkField = new VariableInfo("var_" + SymbolTable.getNextVariableNumber(), owner,
                payloadType, VariableInfo.INITIALIZED | (staticField ? VariableInfo.STATIC : 0));
        SymbolTable.add(sinkField);
        IRNode node = new Declaration(new VariableInitialization(sinkField, initializer));
        diagnostic(node, "sink-field ownerClass=" + owner.getName());
        return new MorphLegResult(node, new InlineTypeFlatFieldMorphTemplate(id, FIRST_USE_LEG, load,
                useLegCount, payloadType, holderType, flatField, sinkField), false);
    }

    private MorphLegResult produceUse(IRNodeBuilder builder) throws ProductionFailedException {
        TypeKlass owner = builder.currentOwnerKlass();
        IRNode field = new NonStaticMemberVariable(receiver(builder, holderType), flatField);
        IRNode node;
        if (load) {
            node = produceLoad(builder, owner, field);
        } else {
            IRNode value = builder.setResultType(payloadType).setNoConsts(false).produceExpression();
            node = new Statement(new BinaryOperator(OperatorKind.ASSIGN, payloadType, field, value), true);
        }
        diagnostic(node, kindName() + " payloadClass=" + payloadType.getName());
        int useLeg = Math.max(FIRST_USE_LEG, nextLeg);
        return new MorphLegResult(node, new InlineTypeFlatFieldMorphTemplate(id, useLeg + 1, load,
                useLegCount, payloadType, holderType, flatField, sink),
                useLeg + 1 >= FIRST_USE_LEG + useLegCount);
    }

    private IRNode produceLoad(IRNodeBuilder builder, TypeKlass owner, IRNode field)
            throws ProductionFailedException {
        if (PseudoRandom.randomBoolean(LOCAL_SINK_PROBABILITY)) {
            VariableInfo local = new VariableInfo("var_" + SymbolTable.getNextVariableNumber(), owner,
                    payloadType, VariableInfo.LOCAL | VariableInfo.INITIALIZED);
            boolean interleave = PseudoRandom.randomBoolean();
            IRNode initialValue = interleave ? builder.fork().setResultType(payloadType)
                    .setExceptionSafe(true).setNoConsts(false).getConstructorCallFactory().produce() : field;
            SymbolTable.add(local);
            ArrayList<IRNode> content = new ArrayList<>();
            content.add(new Declaration(new VariableInitialization(local, initialValue)));
            if (interleave) {
                while (PseudoRandom.randomBoolean(0.35)) {
                    try {
                        content.add(builder.fork().setIsLocal(true).getDeclarationFactory().produce());
                    } catch (ProductionFailedException ignored) {
                        break;
                    }
                }
                content.add(new Statement(new BinaryOperator(OperatorKind.ASSIGN,
                        payloadType, new LocalVariable(local), field), true));
            }
            return new StatementSequence(content, builder.currentLevel());
        }
        IRNode target = null;
        if (sink != null && PseudoRandom.randomBoolean(OWN_SINK_PROBABILITY)) {
            target = sink.isStatic() ? new StaticMemberVariable(owner, sink)
                    : new NonStaticMemberVariable(receiver(builder, sink.owner), sink);
        }
        if (target == null) {
            try {
                target = builder.fork().setResultType(payloadType).setIsConstant(false)
                        .setIsInitialized(true).setExceptionSafe(true).setNoConsts(false)
                        .getVariableFactory().produce();
            } catch (ProductionFailedException ignored) {
                VariableInfo local = new VariableInfo("var_" + SymbolTable.getNextVariableNumber(), owner,
                        payloadType, VariableInfo.LOCAL | VariableInfo.INITIALIZED);
                SymbolTable.add(local);
                return new StatementSequence(List.of(new Declaration(new VariableInitialization(local, field))),
                        builder.currentLevel());
            }
        }
        return new Statement(new BinaryOperator(OperatorKind.ASSIGN, payloadType, target, field), true);
    }

    private static IRNode receiver(IRNodeBuilder builder, TypeKlass type) throws ProductionFailedException {
        if (PseudoRandom.randomBoolean(AMBIENT_RECEIVER_PROBABILITY)) {
            boolean preferFinal = PseudoRandom.randomBoolean();
            for (boolean finalVariable : new boolean[] {preferFinal, !preferFinal}) {
                try {
                    return builder.fork().setResultType(type).setIsConstant(finalVariable)
                            .setIsInitialized(true).setExceptionSafe(true).setNoConsts(false)
                            .getVariableFactory().produce();
                } catch (ProductionFailedException ignored) {
                    // Try the other variable category before constructing a receiver.
                }
            }
        }
        return builder.fork().setResultType(type).setExceptionSafe(true).setNoConsts(false)
                .getConstructorCallFactory().produce();
    }

    private static IRNodeBuilder helperBuilder(String name, int level) {
        FlowParams params = GenerationState.currentFlowParams();
        return new IRNodeBuilder().setName(name).setLevel(level)
                .setMemberFunctionsLimit(Math.max(0,
                        ProductionParams.memberFunctionsLimit.value() / HELPER_GENERATION_DIVISOR))
                .setMemberFunctionsArgLimit(ProductionParams.memberFunctionsArgLimit.value())
                .withStatementLimit(Math.max(1, params.statementLimit() / HELPER_GENERATION_DIVISOR))
                .withOperatorLimit(Math.max(1, params.operatorLimit() / HELPER_GENERATION_DIVISOR));
    }

    private static List<ClassField> payloadFields() {
        ArrayList<Type> types = new ArrayList<>(List.of(TypeList.BOOLEAN, TypeList.BYTE, TypeList.SHORT,
                TypeList.CHAR, TypeList.INT, TypeList.LONG, TypeList.FLOAT, TypeList.DOUBLE,
                TypeList.STRING, TypeList.OBJECT));
        for (Type type : TypeList.getAll()) {
            if (TypeBoxingUtil.isWrapperType(type)
                    || type instanceof TypeArray && !ProductionParams.disableArrays.value()
                    || type instanceof TypeKlass klass && klass.isValueKlass()
                        && !klass.isAbstract() && hasConstructor(klass)) {
                types.add(type);
            }
        }
        int remaining = PAYLOAD_BYTES;
        ArrayList<ClassField> fields = new ArrayList<>();
        do {
            int available = remaining;
            List<Type> candidates = types.stream().filter(type -> fieldBytes(type, false) <= available
                    || fitsNullRestrictedPayload(type, available)).toList();
            Type type = PseudoRandom.randomElement(candidates);
            // A compact inline value can fit even when its nullable reference cannot.
            boolean nullRestricted = fitsNullRestrictedPayload(type, remaining)
                    && (fieldBytes(type, false) > remaining || PseudoRandom.randomBoolean());
            fields.add(new ClassField(type, nullRestricted));
            remaining -= fieldBytes(type, nullRestricted);
        } while (remaining > 0 && PseudoRandom.randomBoolean(ANOTHER_FIELD_PROBABILITY));
        return List.copyOf(fields);
    }

    private static boolean fitsNullRestrictedPayload(Type type, int available) {
        if (!(type instanceof TypeKlass klass) || !klass.isValueKlass()) {
            return false;
        }
        int bytes = payloadBytes(klass);
        return bytes > 0 && bytes <= available;
    }

    private static boolean hasConstructor(TypeKlass type) {
        return SymbolTable.get(type, FunctionInfo.class).stream().map(FunctionInfo.class::cast)
                .anyMatch(function -> function.isConstructor() && !function.isPrivate());
    }

    private void diagnostic(IRNode node, String detail) {
        if (ProductionParams.debugMorphSourceDiagnostics.value()) {
            SourceDiagnostics.attach(node, "InlineTypeFlatFieldMorph id=" + id + " leg=" + nextLeg
                    + " " + detail);
        }
    }

    private String kindName() {
        return load ? "load-value" : "store-value";
    }
}
