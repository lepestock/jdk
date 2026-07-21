/*
 * Copyright (c) 2015, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collection;
import java.util.Optional;

import jdk.test.lib.jittester.BinaryOperator;
import jdk.test.lib.jittester.Block;
import jdk.test.lib.jittester.Break;
import jdk.test.lib.jittester.CastOperator;
import jdk.test.lib.jittester.Continue;
import jdk.test.lib.jittester.Declaration;
import jdk.test.lib.jittester.FlowParams;
import jdk.test.lib.jittester.GenerationWorkStats;
import jdk.test.lib.jittester.GenerationState;
import jdk.test.lib.jittester.IRNode;
import jdk.test.lib.jittester.If;
import jdk.test.lib.jittester.Literal;
import jdk.test.lib.jittester.LocalVariable;
import jdk.test.lib.jittester.MethodArgumentConstraint;
import jdk.test.lib.jittester.NonStaticMemberVariable;
import jdk.test.lib.jittester.Nothing;
import jdk.test.lib.jittester.Operator;
import jdk.test.lib.jittester.OperatorKind;
import jdk.test.lib.jittester.PrintVariables;
import jdk.test.lib.jittester.ProductionFailedException;
import jdk.test.lib.jittester.ProductionParams;
import jdk.test.lib.jittester.Statement;
import jdk.test.lib.jittester.StaticMemberVariable;
import jdk.test.lib.jittester.Switch;
import jdk.test.lib.jittester.Symbol;
import jdk.test.lib.jittester.TernaryOperator;
import jdk.test.lib.jittester.Throw;
import jdk.test.lib.jittester.TryCatchBlock;
import jdk.test.lib.jittester.Type;
import jdk.test.lib.jittester.TypeList;
import jdk.test.lib.jittester.UnaryOperator;
import jdk.test.lib.jittester.VariableBase;
import jdk.test.lib.jittester.VariableDeclaration;
import jdk.test.lib.jittester.VariableDeclarationBlock;
import jdk.test.lib.jittester.VariableInfo;
import jdk.test.lib.jittester.VariableInitialization;
import jdk.test.lib.jittester.collections.CollectionCreation;
import jdk.test.lib.jittester.collections.CollectionElement;
import jdk.test.lib.jittester.collections.CollectionExtraction;
import jdk.test.lib.jittester.collections.CollectionInitializer;
import jdk.test.lib.jittester.classes.ClassDefinitionBlock;
import jdk.test.lib.jittester.classes.Interface;
import jdk.test.lib.jittester.classes.Klass;
import jdk.test.lib.jittester.classes.MainKlass;
import jdk.test.lib.jittester.classes.ValueKlass;
import jdk.test.lib.jittester.functions.ArgumentDeclaration;
import jdk.test.lib.jittester.functions.ConstructorDefinition;
import jdk.test.lib.jittester.functions.ConstructorDefinitionBlock;
import jdk.test.lib.jittester.functions.Function;
import jdk.test.lib.jittester.functions.FunctionDeclaration;
import jdk.test.lib.jittester.functions.FunctionDeclarationBlock;
import jdk.test.lib.jittester.functions.FunctionDefinition;
import jdk.test.lib.jittester.functions.FunctionDefinitionBlock;
import jdk.test.lib.jittester.functions.FunctionInfo;
import jdk.test.lib.jittester.functions.FunctionRedefinition;
import jdk.test.lib.jittester.functions.FunctionRedefinitionBlock;
import jdk.test.lib.jittester.functions.Return;
import jdk.test.lib.jittester.functions.StaticConstructorDefinition;
import jdk.test.lib.jittester.loops.CounterInitializer;
import jdk.test.lib.jittester.loops.CounterManipulator;
import jdk.test.lib.jittester.loops.DoWhile;
import jdk.test.lib.jittester.loops.For;
import jdk.test.lib.jittester.loops.LoopingCondition;
import jdk.test.lib.jittester.loops.While;
import jdk.test.lib.jittester.types.TypeKlass;

public class IRNodeBuilder {
    //private Optional<Type> variableType = Optional.empty();
    private FlowParams flowParams;
    private Optional<TypeKlass> argumentType = Optional.empty();
    private Optional<Integer> variableNumber = Optional.empty();
    private Optional<TypeKlass> ownerClass = Optional.empty();
    private Optional<Type> resultType = Optional.empty();
    private Optional<Boolean> safe = Optional.empty();
    private Optional<Boolean> noConsts = Optional.empty();
    private Optional<OperatorKind> opKind = Optional.empty();
    private Optional<Boolean> subBlock = Optional.empty();
    private Optional<Boolean> canHaveBreaks = Optional.empty();
    private Optional<Boolean> canHaveContinues = Optional.empty();
    private Optional<Boolean> canHaveReturn = Optional.empty();
    //not in use yet because 'throw' is only placed to the locations where 'return' is allowed
    private Optional<Boolean> canHaveThrow = Optional.empty();
    private Optional<Integer> level = Optional.empty();
    private Optional<String> prefix = Optional.empty();
    private Optional<Integer> memberFunctionsLimit = Optional.empty();
    private Optional<Integer> memberFunctionsArgLimit = Optional.empty();
    private Optional<LocalVariable> localVariable = Optional.empty();
    private Optional<Boolean> isLocal = Optional.empty();
    private Optional<Boolean> isStatic = Optional.empty();
    private boolean isSynchronizedAllowed = false;
    private Optional<Boolean> isConstant = Optional.empty();
    private Optional<Boolean> isInitialized = Optional.empty();
    private Optional<String> name = Optional.empty();
    private Optional<Integer> flags = Optional.empty();
    private Optional<FunctionInfo> functionInfo = Optional.empty();
    private Optional<Boolean> semicolon = Optional.empty();
    private Optional<String> arrayKernelIterationVariable = Optional.empty();
    private Optional<Integer> arrayKernelIterationLimit = Optional.empty();
    private Optional<Boolean> inArrayKernel = Optional.empty();
    private Optional<Boolean> preferIterationIndexedArrayTerminal = Optional.empty();
    private Optional<Type> fixedOperandType = Optional.empty();
    private boolean clearFixedOperandType = false;
    private Optional<Integer> arrayElementExpressionWeightPercent = Optional.empty();
    private Optional<Integer> arrayExtractionExpressionWeightPercent = Optional.empty();
    private Optional<String[]> moreReadOnlyVars = Optional.empty();
    private Optional<String[]> moreIterationVariables = Optional.empty();

    public Factory<ArgumentDeclaration> getArgumentDeclarationFactory() {
        GenerationWorkStats.factoryCreationPoint("getArgumentDeclarationFactory");
        return new ArgumentDeclarationFactory(getArgumentType(), getVariableNumber());
    }

    public Factory<Operator> getArithmeticOperatorFactory() throws ProductionFailedException {
        GenerationWorkStats.factoryCreationPoint("getArithmeticOperatorFactory");
        return new ArithmeticOperatorFactory(flowParams().complexityLimit(), flowParams().operatorLimit(),
                getOwnerClass(), getResultType(), getExceptionSafe(), getNoConsts());
    }

    public Factory<CollectionCreation> getCollectionCreationFactory() {
        GenerationWorkStats.factoryCreationPoint("getCollectionCreationFactory");
        return new CollectionCreationFactory(flowParams().complexityLimit(), flowParams().operatorLimit(), getOwnerClass(),
                getResultType(), getExceptionSafe(), getNoConsts());
    }

    public Factory<CollectionElement> getCollectionElementFactory() {
        GenerationWorkStats.factoryCreationPoint("getCollectionElementFactory");
        return new CollectionElementFactory(flowParams().complexityLimit(), flowParams().operatorLimit(), getOwnerClass(),
                getResultType(), getExceptionSafe(), getNoConsts());
    }

    public Factory<CollectionExtraction> getCollectionExtractionFactory() {
        GenerationWorkStats.factoryCreationPoint("getCollectionExtractionFactory");
        return new CollectionExtractionFactory(flowParams().complexityLimit(), flowParams().operatorLimit(), getOwnerClass(),
                getResultType(), getExceptionSafe(), getNoConsts());
    }

    public Factory<CollectionInitializer> getCollectionInitializerFactory(VariableInfo targetVariableInfo) {
        GenerationWorkStats.factoryCreationPoint("getCollectionInitializerFactory");
        return new CollectionInitializerFactory(flowParams().complexityLimit(), flowParams().operatorLimit(), getOwnerClass(),
                getResultType(), getExceptionSafe(), getNoConsts(), targetVariableInfo);
    }

    public Factory<Operator> getAssignmentOperatorFactory() {
        GenerationWorkStats.factoryCreationPoint("getAssignmentOperatorFactory");
        return new AssignmentOperatorFactory(flowParams().complexityLimit(), flowParams().operatorLimit(),
                getOwnerClass(), resultType.orElse(null), getExceptionSafe(), getNoConsts());
    }

    public Factory<BinaryOperator> getBinaryOperatorFactory() throws ProductionFailedException {
        GenerationWorkStats.factoryCreationPoint("getBinaryOperatorFactory");
        OperatorKind o = getOperatorKind();
        switch (o) {
            case ASSIGN:
                return new AssignmentOperatorImplFactory(flowParams().complexityLimit(), flowParams().operatorLimit(),
                        getOwnerClass(), resultType.orElse(null), getExceptionSafe(), getNoConsts());
            case AND:
            case OR:
                return new BinaryLogicOperatorFactory(o, flowParams().complexityLimit(), flowParams().operatorLimit(),
                        getOwnerClass(), resultType.orElse(null), getExceptionSafe(), getNoConsts());
            case BIT_OR:
            case BIT_XOR:
            case BIT_AND:
                return new BinaryBitwiseOperatorFactory(o, flowParams().complexityLimit(), flowParams().operatorLimit(),
                        getOwnerClass(), resultType.orElse(null), getExceptionSafe(), getNoConsts());

            case EQ:
            case NE:
                return new BinaryEqualityOperatorFactory(o, flowParams().complexityLimit(),
                        flowParams().operatorLimit(), getOwnerClass(), resultType.orElse(null), getExceptionSafe(),
                        getNoConsts());
            case GT:
            case LT:
            case GE:
            case LE:
                return new BinaryComparisonOperatorFactory(o, flowParams().complexityLimit(),
                        flowParams().operatorLimit(), getOwnerClass(), resultType.orElse(null), getExceptionSafe(),
                        getNoConsts());
            case SHR:
            case SHL:
            case SAR:
                return new BinaryShiftOperatorFactory(o, flowParams().complexityLimit(), flowParams().operatorLimit(),
                        getOwnerClass(), resultType.orElse(null), getExceptionSafe(), getNoConsts());
            case ADD:
            case SUB:
            case MUL:
            case DIV:
            case MOD:
                return new BinaryArithmeticOperatorFactory(o, flowParams().complexityLimit(),
                        flowParams().operatorLimit(), getOwnerClass(), resultType.orElse(null), getExceptionSafe(),
                        getNoConsts());
            case STRADD:
                return new BinaryStringPlusFactory(flowParams().complexityLimit(), flowParams().operatorLimit(),
                        getOwnerClass(), resultType.orElse(null), getExceptionSafe(), getNoConsts());
            case COMPOUND_ADD:
            case COMPOUND_SUB:
            case COMPOUND_MUL:
            case COMPOUND_DIV:
            case COMPOUND_MOD:
                return new CompoundArithmeticAssignmentOperatorFactory(o, flowParams().complexityLimit(),
                        flowParams().operatorLimit(), getOwnerClass(), resultType.orElse(null), getExceptionSafe(),
                        getNoConsts());
            case COMPOUND_AND:
            case COMPOUND_OR:
            case COMPOUND_XOR:
                return new CompoundBitwiseAssignmentOperatorFactory(o, flowParams().complexityLimit(),
                        flowParams().operatorLimit(), getOwnerClass(), resultType.orElse(null), getExceptionSafe(),
                        getNoConsts());
            case COMPOUND_SHR:
            case COMPOUND_SHL:
            case COMPOUND_SAR:
                return new CompoundShiftAssignmentOperatorFactory(o, flowParams().complexityLimit(),
                        flowParams().operatorLimit(), getOwnerClass(), resultType.orElse(null), getExceptionSafe(),
                        getNoConsts());
            default:
                throw new ProductionFailedException();
        }
    }

    public Factory<UnaryOperator> getUnaryOperatorFactory() throws ProductionFailedException {
        GenerationWorkStats.factoryCreationPoint("getUnaryOperatorFactory");
        OperatorKind o = getOperatorKind();
        switch (o) {
            case NOT:
                return new LogicalInversionOperatorFactory(flowParams().complexityLimit(),
                        flowParams().operatorLimit(), getOwnerClass(), resultType.orElse(null), getExceptionSafe(),
                        getNoConsts());
            case BIT_NOT:
                return new BitwiseInversionOperatorFactory(flowParams().complexityLimit(),
                        flowParams().operatorLimit(), getOwnerClass(), resultType.orElse(null), getExceptionSafe(),
                        getNoConsts());
            case UNARY_PLUS:
            case UNARY_MINUS:
                return new UnaryPlusMinusOperatorFactory(o, flowParams().complexityLimit(),
                        flowParams().operatorLimit(), getOwnerClass(), resultType.orElse(null), getExceptionSafe(),
                        getNoConsts());
            case PRE_DEC:
            case POST_DEC:
            case PRE_INC:
            case POST_INC:
                return new IncDecOperatorFactory(o, flowParams().complexityLimit(), flowParams().operatorLimit(),
                        getOwnerClass(), resultType.orElse(null), getExceptionSafe(), getNoConsts());
            default:
                throw new ProductionFailedException();
        }
    }

    public Factory<Block> getBlockFactory() {
        GenerationWorkStats.factoryCreationPoint("getBlockFactory");
        return new BlockFactory(getOwnerClass(), getResultType(), flowParams().complexityLimit(),
                flowParams().statementLimit(), flowParams().operatorLimit(), getLevel(), subBlock.orElse(false),
                canHaveBreaks.orElse(false), canHaveContinues.orElse(false),
                canHaveReturn.orElse(false), canHaveReturn.orElse(false));
        //now 'throw' can be placed only in the same positions as 'return'
    }

    public Factory<For> getArrayKernelLoopFactory() {
        GenerationWorkStats.factoryCreationPoint("getArrayKernelLoopFactory");
        return new ArrayKernelLoopFactory(getOwnerClass(), getResultType(), flowParams().complexityLimit(),
                flowParams().statementLimit(), flowParams().operatorLimit(), getLevel(),
                canHaveReturn.orElse(false));
    }

    /**
     * Produces a block under an atomically advanced FlowParams frame.
     * This is intended for context-shaped block production (e.g. array kernels).
     */
    public Block produceBlock() throws ProductionFailedException {
        GenerationWorkStats.builderProduce("produceBlock");
        FlowParams previous = GenerationState.currentFlowParams();
        FlowParams.Builder flowBuilder = flowParams().withLimits(
                flowParams().complexityLimit(),
                flowParams().statementLimit(),
                flowParams().operatorLimit());
        arrayKernelIterationVariable.ifPresent(flowBuilder::withIterationVariable);
        arrayKernelIterationLimit.ifPresent(flowBuilder::withArrayKernelIterationLimit);
        inArrayKernel.ifPresent(flowBuilder::withInArrayKernel);
        arrayElementExpressionWeightPercent.ifPresent(flowBuilder::withCollectionElementExpressionWeightPercent);
        arrayExtractionExpressionWeightPercent.ifPresent(flowBuilder::withCollectionExtractionExpressionWeightPercent);
        moreReadOnlyVars.ifPresent(flowBuilder::withMoreReadOnlyVars);
        moreIterationVariables.ifPresent(flowBuilder::withMoreIterationVariables);
        GenerationState.setCurrentFlowParams(flowBuilder.advance());
        try {
            return getBlockFactory().produce();
        } finally {
            GenerationState.setCurrentFlowParams(previous);
        }
    }

    /**
     * Produces an expression under an atomically advanced FlowParams frame.
     * This allows expression-context hints without leaking them outside.
     */
    public IRNode produceExpression() throws ProductionFailedException {
        return produceExpression(false);
    }

    /**
     * Produces an expression under an atomically advanced FlowParams frame.
     *
     * @param denominator when true, produce the expression through the
     *        denominator wrapper used by division-like operators
     */
    public IRNode produceExpression(boolean denominator) throws ProductionFailedException {
        GenerationWorkStats.builderProduce(denominator ? "produceExpression.denominator" : "produceExpression");
        FlowParams previous = GenerationState.currentFlowParams();
        FlowParams.Builder flowBuilder = flowParams().withLimits(
                flowParams().complexityLimit(),
                flowParams().statementLimit(),
                flowParams().operatorLimit());
        preferIterationIndexedArrayTerminal.ifPresent(flowBuilder::withPreferIterationIndexedArrayTerminal);
        fixedOperandType.ifPresent(flowBuilder::withFixedOperandType);
        if (clearFixedOperandType) {
            flowBuilder.clearFixedOperandType();
        }
        arrayElementExpressionWeightPercent.ifPresent(flowBuilder::withCollectionElementExpressionWeightPercent);
        arrayExtractionExpressionWeightPercent.ifPresent(flowBuilder::withCollectionExtractionExpressionWeightPercent);
        moreReadOnlyVars.ifPresent(flowBuilder::withMoreReadOnlyVars);
        moreIterationVariables.ifPresent(flowBuilder::withMoreIterationVariables);
        GenerationState.setCurrentFlowParams(flowBuilder.advance());
        try {
            return denominator ? getDenominatorExpressionFactory().produce() : getExpressionFactory().produce();
        } finally {
            GenerationState.setCurrentFlowParams(previous);
        }
    }

    public Factory<Break> getBreakFactory() {
        GenerationWorkStats.factoryCreationPoint("getBreakFactory");
        return new BreakFactory();
    }

    public Factory<CastOperator> getCastOperatorFactory() {
        GenerationWorkStats.factoryCreationPoint("getCastOperatorFactory");
        return new CastOperatorFactory(flowParams().complexityLimit(), flowParams().operatorLimit(), getOwnerClass(),
                getResultType(), getExceptionSafe(), getNoConsts());
    }

    public Factory<ClassDefinitionBlock> getClassDefinitionBlockFactory() {
        GenerationWorkStats.factoryCreationPoint("getClassDefinitionBlockFactory");
        return new ClassDefinitionBlockFactory(getPrefix(),
                ProductionParams.classesLimit.value(),
                ProductionParams.memberFunctionsLimit.value(),
                ProductionParams.memberFunctionsArgLimit.value(),
                flowParams().complexityLimit(),
                flowParams().statementLimit(),
                flowParams().operatorLimit(),
                getLevel());
    }

    public Factory<MainKlass> getMainKlassFactory() {
        GenerationWorkStats.factoryCreationPoint("getMainKlassFactory");
        return new MainKlassFactory(getName(), flowParams().complexityLimit(),
                ProductionParams.memberFunctionsLimit.value(),
                ProductionParams.memberFunctionsArgLimit.value(),
                flowParams().statementLimit(),
                ProductionParams.testStatementLimit.value(),
                flowParams().operatorLimit());
    }

    public Factory<ConstructorDefinitionBlock> getConstructorDefinitionBlockFactory() {
        GenerationWorkStats.factoryCreationPoint("getConstructorDefinitionBlockFactory");
        return new ConstructorDefinitionBlockFactory(getOwnerClass(), getMemberFunctionsLimit(),
                ProductionParams.memberFunctionsArgLimit.value(), flowParams().complexityLimit(),
                flowParams().statementLimit(), flowParams().operatorLimit(), getLevel());
    }

    public Factory<ConstructorDefinition> getConstructorDefinitionFactory() {
        GenerationWorkStats.factoryCreationPoint("getConstructorDefinitionFactory");
        return new ConstructorDefinitionFactory(getOwnerClass(), flowParams().complexityLimit(),
                flowParams().statementLimit(), flowParams().operatorLimit(),
                getMemberFunctionsArgLimit(), getLevel());
    }

    public Factory<Continue> getContinueFactory() {
        GenerationWorkStats.factoryCreationPoint("getContinueFactory");
        return new ContinueFactory();
    }

    public Factory<CounterInitializer> getCounterInitializerFactory(int counterValue) {
        GenerationWorkStats.factoryCreationPoint("getCounterInitializerFactory");
        return new CounterInitializerFactory(getOwnerClass(), counterValue);
    }

    public CounterManipulatorFactory getCounterManipulatorFactory() {
        GenerationWorkStats.factoryCreationPoint("getCounterManipulatorFactory");
        return new CounterManipulatorFactory(getLocalVariable());
    }

    public Factory<Declaration> getDeclarationFactory() {
        GenerationWorkStats.factoryCreationPoint("getDeclarationFactory");
        return new DeclarationFactory(getOwnerClass(), flowParams().complexityLimit(), flowParams().operatorLimit(),
                getIsLocal(), getExceptionSafe());
    }

    public Factory<Declaration> getConstantDeclarationFactory() {
        GenerationWorkStats.factoryCreationPoint("getConstantDeclarationFactory");
        return new DeclarationFactory(getOwnerClass(), flowParams().complexityLimit(), flowParams().operatorLimit(),
                getIsLocal(), getExceptionSafe(), true);
    }

    public Factory<DoWhile> getDoWhileFactory() {
        GenerationWorkStats.factoryCreationPoint("getDoWhileFactory");
        return new DoWhileFactory(getOwnerClass(), getResultType(), flowParams().complexityLimit(),
                flowParams().statementLimit(), flowParams().operatorLimit(), getLevel(), getCanHaveReturn());
    }

    public Factory<While> getWhileFactory() {
        GenerationWorkStats.factoryCreationPoint("getWhileFactory");
        return new WhileFactory(getOwnerClass(), getResultType(), flowParams().complexityLimit(),
                flowParams().statementLimit(), flowParams().operatorLimit(), getLevel(), getCanHaveReturn());
    }

    public Factory<If> getIfFactory() {
        GenerationWorkStats.factoryCreationPoint("getIfFactory");
        return new IfFactory(getOwnerClass(), getResultType(), flowParams().complexityLimit(),
                flowParams().statementLimit(), flowParams().operatorLimit(), getLevel(), getCanHaveBreaks(),
                getCanHaveContinues(), getCanHaveReturn());
    }

    public Factory<For> getForFactory() {
        GenerationWorkStats.factoryCreationPoint("getForFactory");
        return new ForFactory(getOwnerClass(), getResultType(), flowParams().complexityLimit(),
                flowParams().statementLimit(), flowParams().operatorLimit(), getLevel(), getCanHaveReturn());
    }

    public Factory<Switch> getSwitchFactory() { // TODO: switch is not used now
        GenerationWorkStats.factoryCreationPoint("getSwitchFactory");
        return new SwitchFactory(getOwnerClass(), flowParams().complexityLimit(), flowParams().statementLimit(),
                flowParams().operatorLimit(), getLevel(), getCanHaveReturn());
    }

    public Factory<IRNode> getExpressionFactory() throws ProductionFailedException {
        GenerationWorkStats.factoryCreationPoint("getExpressionFactory");
        return new ExpressionFactory(flowParams().complexityLimit(), flowParams().operatorLimit(), getOwnerClass(),
                getResultType(), getExceptionSafe(), getNoConsts());
    }

    public Factory<IRNode> getBooleanConditionFactory() throws ProductionFailedException {
        GenerationWorkStats.factoryCreationPoint("getBooleanConditionFactory");
        return new BooleanConditionFactory(flowParams().complexityLimit(), flowParams().operatorLimit(),
                getOwnerClass(), getExceptionSafe());
    }

    public Factory<IRNode> getDenominatorExpressionFactory() throws ProductionFailedException {
        GenerationWorkStats.factoryCreationPoint("getDenominatorExpressionFactory");
        return new DenominatorExpressionFactory(flowParams().complexityLimit(), flowParams().operatorLimit(), getOwnerClass(),
                getResultType(), getExceptionSafe(), getNoConsts());
    }

    public Factory<IRNode> getConstrainedIntegralExpressionFactory(MethodArgumentConstraint constraint)
            throws ProductionFailedException {
        GenerationWorkStats.factoryCreationPoint("getConstrainedIntegralExpressionFactory");
        return new ConstrainedIntegralExpressionFactory(flowParams().complexityLimit(), flowParams().operatorLimit(), getOwnerClass(),
                getResultType(), getExceptionSafe(), getNoConsts(), constraint);
    }

    public Factory<FunctionDeclarationBlock> getFunctionDeclarationBlockFactory() {
        GenerationWorkStats.factoryCreationPoint("getFunctionDeclarationBlockFactory");
        return new FunctionDeclarationBlockFactory(getOwnerClass(), getMemberFunctionsLimit(),
                getMemberFunctionsArgLimit(), getLevel());
    }

    public Factory<FunctionDeclaration> getFunctionDeclarationFactory() {
        GenerationWorkStats.factoryCreationPoint("getFunctionDeclarationFactory");
        return new FunctionDeclarationFactory(getName(), getOwnerClass(),resultType.orElse(TypeList.VOID),
                getMemberFunctionsArgLimit(), getFlags());
    }

    public Factory<FunctionDefinitionBlock> getFunctionDefinitionBlockFactory() {
        GenerationWorkStats.factoryCreationPoint("getFunctionDefinitionBlockFactory");
        Factory<FunctionDefinitionBlock> result =
            new FunctionDefinitionBlockFactory(getOwnerClass(), getMemberFunctionsLimit(),
                getMemberFunctionsArgLimit(), flowParams().complexityLimit(), flowParams().statementLimit(),
                flowParams().operatorLimit(), getLevel(), getFlags(), isSynchronizedAllowed);
        isSynchronizedAllowed = true;
        return result;
    }

    public Factory<FunctionDefinition> getFunctionDefinitionFactory() {
        GenerationWorkStats.factoryCreationPoint("getFunctionDefinitionFactory");
        return new FunctionDefinitionFactory(getName(), getOwnerClass(), resultType.orElse(TypeList.VOID),
                flowParams().complexityLimit(), flowParams().statementLimit(), flowParams().operatorLimit(),
                getMemberFunctionsArgLimit(), getLevel(), getFlags());
    }

    public Factory<Function> getFunctionFactory() {
        GenerationWorkStats.factoryCreationPoint("getFunctionFactory");
        return new FunctionFactory(flowParams().complexityLimit(), flowParams().operatorLimit(), getOwnerClass(),
                resultType.orElse(null), getExceptionSafe());
    }

    public Factory<FunctionRedefinitionBlock> getFunctionRedefinitionBlockFactory(Collection<Symbol>
                                                                                        functionSet) {
        GenerationWorkStats.factoryCreationPoint("getFunctionRedefinitionBlockFactory");
        return new FunctionRedefinitionBlockFactory(functionSet, getOwnerClass(),
                flowParams().complexityLimit(), flowParams().statementLimit(), flowParams().operatorLimit(), getLevel());
    }

    public Factory<FunctionRedefinition> getFunctionRedefinitionFactory() {
        GenerationWorkStats.factoryCreationPoint("getFunctionRedefinitionFactory");
        return new FunctionRedefinitionFactory(getFunctionInfo(), getOwnerClass(),
                flowParams().complexityLimit(), flowParams().statementLimit(), flowParams().operatorLimit(), getLevel(),
                getFlags());
    }

    public Factory<Interface> getInterfaceFactory() {
        GenerationWorkStats.factoryCreationPoint("getInterfaceFactory");
        return new InterfaceFactory(getName(), getMemberFunctionsLimit(),
                getMemberFunctionsArgLimit(), getLevel());
    }

    public Factory<Klass> getKlassFactory() {
        GenerationWorkStats.factoryCreationPoint("getKlassFactory");
        return new KlassFactory(getName(), flowParams().complexityLimit(),
                getMemberFunctionsLimit(), getMemberFunctionsArgLimit(), flowParams().statementLimit(),
                flowParams().operatorLimit(), getLevel());
    }

    public Factory<ValueKlass> getValueKlassFactory() {
        GenerationWorkStats.factoryCreationPoint("getValueKlassFactory");
        return new ValueKlassFactory(getName(), flowParams().complexityLimit(),
                getMemberFunctionsLimit(), getMemberFunctionsArgLimit(), flowParams().statementLimit(),
                flowParams().operatorLimit(), getLevel());
    }

    public Factory<IRNode> getLimitedExpressionFactory() throws ProductionFailedException {
        GenerationWorkStats.factoryCreationPoint("getLimitedExpressionFactory");
        return new ExpressionFactory(flowParams().complexityLimit(), flowParams().operatorLimit(),
                getOwnerClass(), getResultType(), getExceptionSafe(), getNoConsts());
    }

    public Factory<Literal> getLiteralFactory() {
        GenerationWorkStats.factoryCreationPoint("getLiteralFactory");
        return new LiteralFactory(getResultType());
    }

    public Factory<LocalVariable> getLocalVariableFactory() {
        GenerationWorkStats.factoryCreationPoint("getLocalVariableFactory");
        return new LocalVariableFactory(/*getVariableType()*/getResultType(), getFlags());
    }

    public Factory<Operator> getLogicOperatorFactory() throws ProductionFailedException {
        GenerationWorkStats.factoryCreationPoint("getLogicOperatorFactory");
        return new LogicOperatorFactory(flowParams().complexityLimit(), flowParams().operatorLimit(), getOwnerClass(),
                getResultType(), getExceptionSafe(), getNoConsts());
    }

    public Factory<LoopingCondition> getLoopingConditionFactory(Literal _limiter) {
        GenerationWorkStats.factoryCreationPoint("getLoopingConditionFactory");
        return new LoopingConditionFactory(flowParams().complexityLimit(), flowParams().operatorLimit(), getOwnerClass(),
                getLocalVariable(), _limiter);
    }

    public Factory<NonStaticMemberVariable> getNonStaticMemberVariableFactory() {
        GenerationWorkStats.factoryCreationPoint("getNonStaticMemberVariableFactory");
        return new NonStaticMemberVariableFactory(flowParams().complexityLimit(), flowParams().operatorLimit(),
                getOwnerClass(), /*getVariableType()*/getResultType(), getFlags(), getExceptionSafe());
    }

    public Factory<Nothing> getNothingFactory() {
        GenerationWorkStats.factoryCreationPoint("getNothingFactory");
        return new NothingFactory();
    }

    public Factory<PrintVariables> getPrintVariablesFactory() {
        GenerationWorkStats.factoryCreationPoint("getPrintVariablesFactory");
        return new PrintVariablesFactory(getOwnerClass(), getLevel());
    }

    public Factory<Return> getReturnFactory() {
        GenerationWorkStats.factoryCreationPoint("getReturnFactory");
        return new ReturnFactory(flowParams().complexityLimit(), flowParams().operatorLimit(), getOwnerClass(),
                getResultType(), getExceptionSafe());
    }

    public Factory<Throw> getThrowFactory() {
        GenerationWorkStats.factoryCreationPoint("getThrowFactory");
        return new ThrowFactory(flowParams().complexityLimit(), flowParams().operatorLimit(), getOwnerClass(), getResultType(), getExceptionSafe());
    }

    public Factory<Statement> getStatementFactory() {
        GenerationWorkStats.factoryCreationPoint("getStatementFactory");
        return new StatementFactory(flowParams().complexityLimit(), flowParams().operatorLimit(), getOwnerClass(),
                getExceptionSafe(), getNoConsts(), semicolon.orElse(true));
    }

    public Factory<StaticConstructorDefinition> getStaticConstructorDefinitionFactory() {
        GenerationWorkStats.factoryCreationPoint("getStaticConstructorDefinitionFactory");
        return new StaticConstructorDefinitionFactory(getOwnerClass(), flowParams().complexityLimit(),
                flowParams().statementLimit(), flowParams().operatorLimit(), getLevel());
    }

    public Factory<StaticMemberVariable> getStaticMemberVariableFactory() {
        GenerationWorkStats.factoryCreationPoint("getStaticMemberVariableFactory");
        return new StaticMemberVariableFactory(getOwnerClass(), /*getVariableType()*/getResultType(), getFlags());
    }

    public Factory<TernaryOperator> getTernaryOperatorFactory() {
        GenerationWorkStats.factoryCreationPoint("getTernaryOperatorFactory");
        return new TernaryOperatorFactory(flowParams().complexityLimit(), flowParams().operatorLimit(), getOwnerClass(),
                getResultType(), getExceptionSafe(), getNoConsts());
    }

    public Factory<VariableDeclarationBlock> getVariableDeclarationBlockFactory() {
        GenerationWorkStats.factoryCreationPoint("getVariableDeclarationBlockFactory");
        return new VariableDeclarationBlockFactory(getOwnerClass(), flowParams().complexityLimit(),
                flowParams().operatorLimit(), getLevel(), getExceptionSafe(), false);
    }

    /**
     * Creates a variable declaration block factory constrained to constants.
     *
     * @return constant-only declaration block factory
     */
    public Factory<VariableDeclarationBlock> getConstantVariableDeclarationBlockFactory() {
        GenerationWorkStats.factoryCreationPoint("getConstantVariableDeclarationBlockFactory");
        return new VariableDeclarationBlockFactory(getOwnerClass(), flowParams().complexityLimit(),
                flowParams().operatorLimit(), getLevel(), getExceptionSafe(), true);
    }

    public Factory<VariableDeclaration> getVariableDeclarationFactory() {
        GenerationWorkStats.factoryCreationPoint("getVariableDeclarationFactory");
        return new VariableDeclarationFactory(getOwnerClass(), getIsStatic(), getIsLocal(), getResultType());
    }

    public Factory<VariableBase> getVariableFactory() {
        GenerationWorkStats.factoryCreationPoint("getVariableFactory");
        return new VariableFactory(flowParams().complexityLimit(), flowParams().operatorLimit(), getOwnerClass(),
                /*getVariableType()*/getResultType(), getIsConstant(), getIsInitialized(), getExceptionSafe(), getNoConsts());
    }

    public Factory<VariableInitialization> getVariableInitializationFactory() {
        GenerationWorkStats.factoryCreationPoint("getVariableInitializationFactory");
        return new VariableInitializationFactory(getOwnerClass(), getIsConstant(), getIsStatic(),
                getIsLocal(), flowParams().complexityLimit(), flowParams().operatorLimit(), getExceptionSafe());
    }

    public Factory<TryCatchBlock> getTryCatchBlockFactory() {
        GenerationWorkStats.factoryCreationPoint("getTryCatchBlockFactory");
        return new TryCatchBlockFactory(getOwnerClass(), getResultType(),
                flowParams().complexityLimit(), flowParams().statementLimit(), flowParams().operatorLimit(),
                getLevel(), subBlock.orElse(false), getCanHaveBreaks(),
                getCanHaveContinues(), getCanHaveReturn());
    }

/*    public IRNodeBuilder setVariableType(Type value) {
        variableType = Optional.of(value);
        return this;
    }*/

    public IRNodeBuilder setArgumentType(TypeKlass value) {
        argumentType = Optional.of(value);
        return this;
    }

    public IRNodeBuilder setVariableNumber(int value) {
        variableNumber = Optional.of(value);
        return this;
    }

    public IRNodeBuilder withComplexityLimit(long value) {
        flowParams = flowParams()
                .withComplexityLimit(value)
                .advance();
        return this;
    }

    public IRNodeBuilder withOperatorLimit(int value) {
        flowParams = flowParams()
                .withOperatorLimit(value)
                .advance();
        return this;
    }

    public IRNodeBuilder withStatementLimit(int value) {
        flowParams = flowParams()
                .withStatementLimit(value)
                .advance();
        return this;
    }

    public IRNodeBuilder setOwnerKlass(TypeKlass value) {
        ownerClass = Optional.of(value);
        return this;
    }

    public IRNodeBuilder setResultType(Type value) {
        resultType = Optional.of(value);
        return this;
    }
    // TODO: check if safe is always true in current implementation
    public IRNodeBuilder setExceptionSafe(boolean value) {
        safe = Optional.of(value);
        return this;
    }
    // TODO: check is noconsts is always false in current implementation
    public IRNodeBuilder setNoConsts(boolean value) {
        noConsts = Optional.of(value);
        return this;
    }

    public IRNodeBuilder setOperatorKind(OperatorKind value) {
        opKind = Optional.of(value);
        return this;
    }

    public IRNodeBuilder setLevel(int value) {
        level = Optional.of(value);
        return this;
    }

    public IRNodeBuilder setSubBlock(boolean value) {
        subBlock = Optional.of(value);
        return this;
    }

    public IRNodeBuilder setCanHaveBreaks(boolean value) {
        canHaveBreaks = Optional.of(value);
        return this;
    }

    public IRNodeBuilder setCanHaveContinues(boolean value) {
        canHaveContinues = Optional.of(value);
        return this;
    }

    public IRNodeBuilder setCanHaveReturn(boolean value) {
        canHaveReturn = Optional.of(value);
        return this;
    }

    public IRNodeBuilder setCanHaveThrow(boolean value) {
        canHaveThrow = Optional.of(value);
        return this;
    }

    public IRNodeBuilder setPrefix(String value) {
        prefix = Optional.of(value);
        return this;
    }

    public IRNodeBuilder setMemberFunctionsLimit(int value) {
        memberFunctionsLimit = Optional.of(value);
        return this;
    }

    public IRNodeBuilder setMemberFunctionsArgLimit(int value) {
        memberFunctionsArgLimit = Optional.of(value);
        return this;
    }

    public IRNodeBuilder setLocalVariable(LocalVariable value) {
        localVariable = Optional.of(value);
        return this;
    }

    public IRNodeBuilder setIsLocal(boolean value) {
        isLocal = Optional.of(value);
        return this;
    }

    public IRNodeBuilder setIsStatic(boolean value) {
        isStatic = Optional.of(value);
        return this;
    }

    public IRNodeBuilder setIsSynchronizedAllowed(boolean value) {
        isSynchronizedAllowed = value;
        return this;
    }

    public IRNodeBuilder setIsInitialized(boolean value) {
        isInitialized = Optional.of(value);
        return this;
    }

    public IRNodeBuilder setIsConstant(boolean value) {
        isConstant = Optional.of(value);
        return this;
    }

    public IRNodeBuilder setName(String value) {
        name = Optional.of(value);
        return this;
    }

    public IRNodeBuilder setFlags(int value) {
        flags = Optional.of(value);
        return this;
    }

    public IRNodeBuilder setFunctionInfo(FunctionInfo value) {
        functionInfo = Optional.of(value);
        return this;
    }

    public IRNodeBuilder setSemicolon(boolean value) {
        semicolon = Optional.of(value);
        return this;
    }

    public IRNodeBuilder setArrayKernelIterationVariable(String value) {
        arrayKernelIterationVariable = Optional.ofNullable(value);
        return this;
    }

    public IRNodeBuilder withArrayKernelVariable(String value) {
        return setArrayKernelIterationVariable(value);
    }

    public IRNodeBuilder setArrayKernelIterationLimit(int value) {
        arrayKernelIterationLimit = Optional.of(value);
        return this;
    }

    public IRNodeBuilder withArrayKernelIterationLimit(int value) {
        return setArrayKernelIterationLimit(value);
    }

    public IRNodeBuilder setInArrayKernel(boolean value) {
        inArrayKernel = Optional.of(value);
        return this;
    }

    public IRNodeBuilder withInArrayKernel(boolean value) {
        return setInArrayKernel(value);
    }

    public IRNodeBuilder setPreferIterationIndexedArrayTerminal(boolean value) {
        preferIterationIndexedArrayTerminal = Optional.of(value);
        return this;
    }

    public IRNodeBuilder withPreferIterationIndexedArrayTerminal(boolean value) {
        return setPreferIterationIndexedArrayTerminal(value);
    }

    public IRNodeBuilder setFixedOperandType(Type value) {
        fixedOperandType = Optional.ofNullable(value);
        clearFixedOperandType = false;
        return this;
    }

    public IRNodeBuilder clearFixedOperandType() {
        fixedOperandType = Optional.empty();
        clearFixedOperandType = true;
        return this;
    }

    public IRNodeBuilder withCollectionElementExpressionWeightPercent(int value) {
        arrayElementExpressionWeightPercent = Optional.of(value);
        return this;
    }

    public IRNodeBuilder withCollectionExtractionExpressionWeightPercent(int value) {
        arrayExtractionExpressionWeightPercent = Optional.of(value);
        return this;
    }

    public IRNodeBuilder withMoreReadOnlyVars(String... values) {
        moreReadOnlyVars = Optional.ofNullable(values);
        return this;
    }

    public IRNodeBuilder withMoreIterationVariables(String... values) {
        moreIterationVariables = Optional.ofNullable(values);
        return this;
    }

    // getters
/*    private Type getVariableType() {
        return variableType.orElseThrow(() -> new IllegalArgumentException(
                "Variable type wasn't set"));
    }*/

    private FlowParams flowParams() {
        if (flowParams == null) {
            flowParams = GenerationState.currentFlowParams();
        }
        return flowParams;
    }

    private TypeKlass getArgumentType() {
        return argumentType.orElseThrow(() -> new IllegalArgumentException(
                "Argument type wasn't set"));
    }

    private int getVariableNumber() {
        return variableNumber.orElseThrow(() -> new IllegalArgumentException(
                "Variable number wasn't set"));
    }

    private TypeKlass getOwnerClass() {
        return ownerClass.orElseThrow(() -> new IllegalArgumentException("Type_Klass wasn't set"));
    }

    private Type getResultType() {
        return resultType.orElseThrow(() -> new IllegalArgumentException("Return type wasn't set"));
    }

    private boolean getExceptionSafe() {
        return safe.orElseThrow(() -> new IllegalArgumentException("Safe wasn't set"));
    }

    private boolean getNoConsts() {
        return noConsts.orElseThrow(() -> new IllegalArgumentException("NoConsts wasn't set"));
    }

    private OperatorKind getOperatorKind() {
        return opKind.orElseThrow(() -> new IllegalArgumentException("Operator kind wasn't set"));
    }

    private int getLevel() {
        return level.orElseThrow(() -> new IllegalArgumentException("Level wasn't set"));
    }

    private String getPrefix() {
        return prefix.orElseThrow(() -> new IllegalArgumentException("Prefix wasn't set"));
    }

    private int getMemberFunctionsLimit() {
        return memberFunctionsLimit.orElseThrow(() -> new IllegalArgumentException(
                "memberFunctions limit wasn't set"));
    }

    private int getMemberFunctionsArgLimit() {
        return memberFunctionsArgLimit.orElseThrow(() -> new IllegalArgumentException(
                "memberFunctionsArg limit wasn't set"));
    }

    private LocalVariable getLocalVariable() {
        return localVariable.orElseThrow(() -> new IllegalArgumentException(
                "local variable wasn't set"));
    }

    private boolean getIsLocal() {
        return isLocal.orElseThrow(() -> new IllegalArgumentException("isLocal wasn't set"));
    }

    private boolean getIsStatic() {
        return isStatic.orElseThrow(() -> new IllegalArgumentException("isStatic wasn't set"));
    }

    private boolean getIsSynchronizedAllowed() {
        return isSynchronizedAllowed;
    }

    private boolean getIsInitialized() {
        return isInitialized.orElseThrow(() -> new IllegalArgumentException(
                "isInitialized wasn't set"));
    }

    private boolean getIsConstant() {
        return isConstant.orElseThrow(() -> new IllegalArgumentException("isConstant wasn't set"));
    }

    private boolean getCanHaveReturn() {
        return canHaveReturn.orElseThrow(() -> new IllegalArgumentException(
                "canHaveReturn wasn't set"));
    }

    private boolean getCanHaveBreaks() {
        return canHaveBreaks.orElseThrow(() -> new IllegalArgumentException(
                "canHaveBreaks wasn't set"));
    }

    private boolean getCanHaveContinues() {
        return canHaveContinues.orElseThrow(() -> new IllegalArgumentException(
                "canHaveContinues wasn't set"));
    }

    private String getName() {
        return name.orElseThrow(() -> new IllegalArgumentException("Name wasn't set"));
    }

    private int getFlags() {
        return flags.orElseThrow(() -> new IllegalArgumentException("Flags wasn't set"));
    }

    private FunctionInfo getFunctionInfo() {
        return functionInfo.orElseThrow(() -> new IllegalArgumentException(
                "FunctionInfo wasn't set"));
    }
}
