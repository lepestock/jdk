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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import jdk.test.lib.jittester.MethodArgumentConstraint;
import jdk.test.lib.jittester.MethodResultWrapper;
import jdk.test.lib.jittester.IRNode;
import jdk.test.lib.jittester.GenerationState;
import jdk.test.lib.jittester.ProductionFailedException;
import jdk.test.lib.jittester.ProductionParams;
import jdk.test.lib.jittester.Symbol;
import jdk.test.lib.jittester.SymbolTable;
import jdk.test.lib.jittester.Type;
import jdk.test.lib.jittester.TypeList;
import jdk.test.lib.jittester.VariableInfo;
import jdk.test.lib.jittester.functions.Function;
import jdk.test.lib.jittester.functions.FunctionInfo;
import jdk.test.lib.jittester.types.TypeKlass;
import jdk.test.lib.jittester.utils.FixedTrees;
import jdk.test.lib.jittester.utils.Genome;
import jdk.test.lib.jittester.utils.PseudoRandom;
import jdk.test.lib.jittester.utils.TypeBoxingUtil;
import jdk.test.lib.jittester.Logger;

public class FunctionFactory extends SafeFactory<Function> {
    private static final String MAGNET_CHANNEL = "use.function";
    private final FunctionInfo functionInfo;
    private final int operatorLimit;
    private final boolean exceptionSafe;
    private final TypeKlass ownerClass;
    public static long SEED;
    private static long INTERVENTION = 131992649516573L;
    public static VariableInfo forbiddenThizz = null;

    FunctionFactory(int operatorLimit, TypeKlass ownerClass,
            Type resultType, boolean exceptionSafe) {
        functionInfo = new FunctionInfo();
        this.operatorLimit = operatorLimit;
        this.ownerClass = ownerClass;
        this.functionInfo.type = resultType;
        this.exceptionSafe = exceptionSafe;
    }

    @Override
    protected Function sproduce() throws ProductionFailedException {
        PseudoRandom.randomBoolean(); //Make the calls from SafeFactory predictable
        SEED = PseudoRandom.getCurrentSeed();
        Logger.log(SEED == INTERVENTION, "FunctionFactory :ownerClass " + ownerClass);
        VariableInfo removedThizz = null;
        boolean thizzRemoved = false;
        if (forbiddenThizz != null && ownerClass.equals(forbiddenThizz.getOwner())) {
            Logger.log(SEED == INTERVENTION, "FunctionFactory :forbiddenThizz " + forbiddenThizz +
                    " :thizz-type " + forbiddenThizz.type +
                    " :result-type " + functionInfo.type +
                    " :types-equals? " + functionInfo.type.equals(forbiddenThizz.type));
            SymbolTable.removeVariable(forbiddenThizz);
            thizzRemoved = true;
        }
        // Currently no function is exception-safe
        if (exceptionSafe) {
            throw new ProductionFailedException();
        }
        ArrayList<Symbol> allFunctions;
        if (functionInfo.type == null) {
            allFunctions = new ArrayList<>(SymbolTable.getAllCombined(FunctionInfo.class));
        } else {
            allFunctions = new ArrayList<>(SymbolTable.get(functionInfo.type, FunctionInfo.class));
        }
        int intrinsicBonus = Math.max(0, ProductionParams.intrinsicCallWeightBonus.value());
        if (!allFunctions.isEmpty()) {
            boolean replayMode = Genome.isReplayActive();
            List<FunctionInfo> remainingFunctions = toFunctionList(allFunctions);
            remainingFunctions.sort(FUNCTION_ORDER);
            Collection<TypeKlass> klassHierarchy = ownerClass.getAllParents();
            while (!remainingFunctions.isEmpty()) {
                List<FunctionInfo> selectionCandidates = new ArrayList<>(remainingFunctions);
                FunctionInfo functionInfo = selectWeightedFunction(remainingFunctions, intrinsicBonus);
                removeFunctionOnce(remainingFunctions, functionInfo);
                // Don't try to construct abstract classes.
                if (functionInfo.isConstructor() && functionInfo.owner.isAbstract()) {
                    continue;
                }
                // We don't call methods from the same class which are not final, because if we
                // do this may produce an infinite recursion. Simple example:
                // class  A
                // {
                //     f1() { }
                //     f2() { f1(); }
                // }
                //
                // class B : A
                // {
                //    f1() { f2(); }
                // }
                //
                // However the same example is obviously safe for static and final functions
                // Also we introduce a special flag NONRECURSIVE to mark functions that
                // are not overrided. We may also call such functions.

                // If it's a local call.. or it's a call using some variable to some object of some type in our hierarchy
                boolean inHierarchy = false;
                if (ownerClass.equals(functionInfo.owner) || (inHierarchy = klassHierarchy.contains(functionInfo.owner))) {
                    if ((functionInfo.flags & FunctionInfo.FINAL) == 0 && (functionInfo.flags & FunctionInfo.STATIC) == 0
                            && (functionInfo.flags & FunctionInfo.NONRECURSIVE) == 0) {
                        continue;
                    }
                    if (inHierarchy && (functionInfo.flags & FunctionInfo.PRIVATE) > 0) {
                        continue;
                    }
                } else {
                    if ((functionInfo.flags & FunctionInfo.PUBLIC) == 0
                            && (functionInfo.flags & FunctionInfo.DEFAULT) == 0) {
                        continue;
                    }
                }
                Long replayTargetGene = null;
                if (replayMode) {
                    replayTargetGene = Genome.consumeMagnetTargetGene("magnet.use." + MAGNET_CHANNEL, 0L);
                    // Failed record attempts keep selection RNG (N...) but roll back magnet target (U...).
                    // In replay we may encounter those attempts and should skip them.
                    if (replayTargetGene == null) {
                        continue;
                    }
                    if (replayTargetGene == -1L) {
                        throw new RuntimeException("Genome broken around magnet gene -1 for channel '"
                                + MAGNET_CHANNEL + "': selected function magnet is "
                                + functionInfo.getMagnetismGeneId());
                    }
                    if (replayTargetGene != functionInfo.getMagnetismGeneId()) {
                        throw new RuntimeException("Genome broken around magnet gene "
                                + replayTargetGene + " for channel '" + MAGNET_CHANNEL
                                + "': selected function magnet is "
                                + functionInfo.getMagnetismGeneId());
                    }
                }
                GenerationState.Checkpoint stateCheckpoint = GenerationState.checkpoint();
                try {
                    if (!replayMode) {
                        Genome.beginSpeculativeRecord();
                        SymbolTable.recordMagnetTargetSelection(MAGNET_CHANNEL,
                                functionInfo.getMagnetismGeneId(), selectionCandidates, functionInfo);
                    }
                    List<IRNode> accum = new ArrayList<>();
                    if (!functionInfo.argTypes.isEmpty()) {
                        // Here we should do some analysis here to determine if
                        // there are any conflicting functions due to possible
                        // constant folding.

                        // For example the following can be done:
                        // Scan all the hieirachy where the class is declared.
                        // If there are function with a same name and same number of args,
                        // then disable usage of foldable expressions in the args.
                        boolean noconsts = false;
                        Collection<Symbol> allFuncsInKlass = SymbolTable.getAllCombined(functionInfo.owner,
                                FunctionInfo.class);
                        for (Symbol s2 : allFuncsInKlass) {
                            FunctionInfo i2 = (FunctionInfo) s2;
                            if (!i2.equals(functionInfo) && i2.name.equals(functionInfo.name)
                                    && i2.argTypes.size() == functionInfo.argTypes.size()) {
                                noconsts = true;
                                break;
                            }
                        }
                        int argumentOperatorLimit = Math.max(1, (operatorLimit - 1) / functionInfo.argTypes.size());
                        for (int argIndex = 0; argIndex < functionInfo.argTypes.size(); argIndex++) {
                            VariableInfo argType = functionInfo.argTypes.get(argIndex);
                            IRNodeBuilder b = new IRNodeBuilder().setOwnerKlass(ownerClass)
                                    .withOperatorLimit(argumentOperatorLimit)
                                    .setExceptionSafe(exceptionSafe)
                                    .setNoConsts(noconsts)
                                    .setResultType(argType.type);
                            accum.add(produceArgument(ownerClass, b,
                                    functionInfo.getArgumentConstraint(argIndex), argType.type));
                            Logger.log(ownerClass, "(FunctionFactory :point1 :function " + functionInfo + ")", accum);
                        }
                    }

                    //if (SEED == INTERVENTION) {
                    if (thizzRemoved) {
                        SymbolTable.add(forbiddenThizz);
                    }
                    Function produced = new Function(ownerClass, functionInfo, accum);
                    Long expressionScopeSeed = Genome.getCurrentExpressionScopeSeed();
                    if (expressionScopeSeed != null) {
                        produced.setExpressionGeneSeed(expressionScopeSeed);
                    }
                    Function result = wrapResultIfNeeded(ownerClass, functionInfo, produced);
                    if (!replayMode) {
                        Genome.commitSpeculativeRecord();
                    }
                    return result;
                } catch (ProductionFailedException e) {
                    GenerationState.rollbackTo(stateCheckpoint);
                    if (!replayMode) {
                        Genome.rollbackSpeculativeRecord();
                    } else if (replayTargetGene != null) {
                        throw new RuntimeException("Genome broken around magnet gene "
                                + replayTargetGene + " for channel '" + MAGNET_CHANNEL
                                + "': selected function failed in replay", e);
                    }
                    // Failed attempts are normal here; keep trying other candidates.
                } catch (RuntimeException e) {
                    GenerationState.rollbackTo(stateCheckpoint);
                    if (!replayMode) {
                        Genome.rollbackSpeculativeRecord();
                    }
                    throw e;
                }
            }
        }
        if (!Genome.isReplayActive()) {
            SymbolTable.recordMagnetTargetGene(MAGNET_CHANNEL, -1L);
        } else {
            long replayTargetGene = SymbolTable.consumeMagnetTargetGene(MAGNET_CHANNEL);
            if (replayTargetGene != -1L) {
                throw new RuntimeException("Genome broken around magnet gene "
                        + replayTargetGene + " for channel '" + MAGNET_CHANNEL
                        + "': expected terminal -1 marker");
            }
        }
        throw new ProductionFailedException();
    }

    private static IRNode produceArgument(TypeKlass ownerClass, IRNodeBuilder builder,
            MethodArgumentConstraint constraint, Type type)
            throws ProductionFailedException {
        if (constraint == MethodArgumentConstraint.NAN_NORMALIZED) {
            IRNode argument = builder.produceExpression();
            Type primitiveType = TypeBoxingUtil.toPrimitiveType(type);
            if (primitiveType != null && (primitiveType.equals(TypeList.DOUBLE)
                    || primitiveType.equals(TypeList.FLOAT))) {
                return FixedTrees.wrapWithNormalizeNaN(ownerClass,
                        new TypeKlass(ProductionParams.runtimeSupportClassName()), argument);
            }
            return argument;
        }
        if (constraint == MethodArgumentConstraint.NONZERO && DenominatorExpressionFactory.canThrowFor(type, type)) {
            return builder.produceExpression(true);
        }
        if (ConstrainedIntegralExpressionFactory.canApplyTo(type, constraint)) {
            return builder.getConstrainedIntegralExpressionFactory(constraint).produce();
        }
        return builder.getExpressionFactory().produce();
    }

    private static Function wrapResultIfNeeded(TypeKlass ownerClass, FunctionInfo functionInfo, Function result) {
        if (GenerationState.currentFlowParams().normalizeNaN()
                && functionInfo.getResultWrapper() == MethodResultWrapper.NORMALIZE_NAN) {
            return FixedTrees.wrapWithNormalizeNaN(ownerClass, new TypeKlass(ProductionParams.runtimeSupportClassName()),
                    result);
        }
        return result;
    }

    private static List<FunctionInfo> toFunctionList(List<Symbol> symbols) {
        ArrayList<FunctionInfo> out = new ArrayList<>(symbols.size());
        for (Symbol symbol : symbols) {
            out.add((FunctionInfo) symbol);
        }
        return out;
    }

    private static final Comparator<FunctionInfo> FUNCTION_ORDER = Comparator
            .comparing((FunctionInfo f) -> f.owner == null ? "" : f.owner.getName())
            .thenComparing(f -> f.name == null ? "" : f.name)
            .thenComparingInt(f -> f.flags)
            .thenComparing(f -> f.type == null ? "" : f.type.getName())
            .thenComparingInt(f -> f.argTypes == null ? -1 : f.argTypes.size())
            .thenComparing(FunctionFactory::argumentTypeSignature);

    private static String argumentTypeSignature(FunctionInfo f) {
        if (f.argTypes == null || f.argTypes.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (VariableInfo v : f.argTypes) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(v == null || v.type == null ? "<null>" : v.type.getName());
        }
        return sb.toString();
    }

    private static void removeFunctionOnce(List<FunctionInfo> functions, FunctionInfo target) {
        Iterator<FunctionInfo> it = functions.iterator();
        while (it.hasNext()) {
            if (it.next() == target) {
                it.remove();
                return;
            }
        }
    }

    private static int getWeight(FunctionInfo info, int intrinsicBonus) {
        return info.isIntrinsic() ? 1 + intrinsicBonus : 1;
    }

    private static FunctionInfo selectWeightedFunction(List<FunctionInfo> functions, int intrinsicBonus) {
        if (functions.isEmpty()) {
            throw new IllegalArgumentException("functions is empty");
        }
        if (intrinsicBonus <= 0) {
            return PseudoRandom.randomElement(functions);
        }

        double totalWeight = 0.0;
        for (FunctionInfo functionInfo : functions) {
            totalWeight += getWeight(functionInfo, intrinsicBonus);
        }
        if (totalWeight <= 0.0) {
            return PseudoRandom.randomElement(functions);
        }

        double draw = PseudoRandom.random() * totalWeight;
        double prefix = 0.0;
        for (FunctionInfo functionInfo : functions) {
            prefix += getWeight(functionInfo, intrinsicBonus);
            if (draw < prefix) {
                return functionInfo;
            }
        }
        return functions.get(functions.size() - 1);
    }
}
