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

import jdk.test.lib.jittester.IRNode;
import jdk.test.lib.jittester.OperatorKind;
import jdk.test.lib.jittester.GenerationState;
import jdk.test.lib.jittester.ProductionFailedException;
import jdk.test.lib.jittester.ProductionParams;
import jdk.test.lib.jittester.Rule;
import jdk.test.lib.jittester.Type;
import jdk.test.lib.jittester.TypeList;
import jdk.test.lib.jittester.types.TypeKlass;
import jdk.test.lib.jittester.Logger;
import jdk.test.lib.jittester.Symbol;
import jdk.test.lib.jittester.SymbolTable;
import jdk.test.lib.jittester.VariableInfo;
import jdk.test.lib.jittester.types.TypeArray;
import jdk.test.lib.jittester.utils.Genome;
import jdk.test.lib.jittester.utils.GenomeChoice;
import jdk.test.lib.jittester.utils.DepthProbabilityTaper;
import jdk.test.lib.jittester.utils.PseudoRandom;
import jdk.test.lib.jittester.utils.TypeBoxingUtil;

class ExpressionFactory extends SafeFactory<IRNode> {
    private static final double CAST_WEIGHT = 0.02;
    private static final double ARITHMETIC_WEIGHT = 2.5;
    private static final double NUMERIC_CAST_WEIGHT = 0.005;
    private static final double NUMERIC_ARITHMETIC_WEIGHT = 4.0;
    private static final double NUMERIC_ASSIGNMENT_WEIGHT = 0.6;
    private static final double ITERATION_INDEXED_ARRAY_TERMINAL_WEIGHT = 8.0;
    private static final ThreadLocal<Integer> EXPRESSION_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<DebugStats> DEBUG_STATS =
            ThreadLocal.withInitial(DebugStats::new);

    private final Rule<IRNode> rule;
    private final Rule<IRNode> terminalRule;
    private final TypeKlass ownerClass;
    private final Type resultType;
    private final boolean noConsts;
    private final long SEED;
    private final double baseStopProbability;
    private final double maxStopProbability;
    private final int stopHalfDepth;
    private final boolean expressionDebugEnabled;
    private final int expressionDepthWarn;
    private final int expressionDepthHardLimit;
    private final int expressionMaxDepth;

    ExpressionFactory(long complexityLimit, int operatorLimit, TypeKlass ownerClass, Type resultType,
            boolean exceptionSafe, boolean noconsts) throws ProductionFailedException {
        SEED = PseudoRandom.getCurrentSeed();
        this.ownerClass = ownerClass; //FIXME JNP Remove
        this.resultType = resultType;
        this.noConsts = noconsts;
        IRNodeBuilder builder = new IRNodeBuilder()
                .setComplexityLimit(complexityLimit)
                .setOperatorLimit(operatorLimit)
                .setOwnerKlass(ownerClass)
                .setResultType(resultType)
                .setExceptionSafe(exceptionSafe)
                .setNoConsts(noconsts);
        rule = new Rule<>("expression");
        terminalRule = new Rule<>("expression_terminal");
        this.expressionDebugEnabled = ProductionParams.expressionDebug.value();
        this.expressionDepthWarn = Math.max(1, ProductionParams.expressionDebugDepthWarn.value());
        this.expressionDepthHardLimit = Math.max(0, ProductionParams.expressionDebugDepthHardLimit.value());
        this.expressionMaxDepth = Math.max(1, ProductionParams.expressionMaxDepth.value());
        this.baseStopProbability = Math.max(0.0, Math.min(0.999,
                ProductionParams.expressionStopPercent.value() / 100.0));
        this.maxStopProbability = Math.max(baseStopProbability, Math.min(0.999,
                ProductionParams.expressionStopMaxPercent.value() / 100.0));
        this.stopHalfDepth = Math.max(1, ProductionParams.expressionStopHalfDepth.value());
        double terminalWeight = Math.max(1.0,
                ProductionParams.expressionStopPercent.value() / 2.0);
        boolean preferIterationIndexedArrayTerminal =
                GenerationState.currentFlowParams().preferIterationIndexedArrayTerminal()
                        && !ProductionParams.disableArrays.value()
                        && !exceptionSafe
                        && !(resultType instanceof TypeArray);
        boolean groupTerminals = preferIterationIndexedArrayTerminal;
        double groupedTerminalWeight = 0.0;
        if (!noconsts) {
            Factory<? extends IRNode> literalFactory = builder.getLiteralFactory();
            Factory<? extends IRNode> constantFactory = builder.setIsConstant(true)
                    .setIsInitialized(true)
                    //.setVariableType(resultType)
                    .getVariableFactory();
            addTerminal("literal", literalFactory, terminalWeight, 1.0, groupTerminals);
            groupedTerminalWeight += groupTerminals ? terminalWeight : 0.0;
            addTerminal("constant", constantFactory, terminalWeight, 1.0, groupTerminals);
            groupedTerminalWeight += groupTerminals ? terminalWeight : 0.0;
        }
        Factory<? extends IRNode> variableFactory = builder
                .setIsConstant(false)
                .setIsInitialized(true)
                .getVariableFactory();
        addTerminal("variable", variableFactory, terminalWeight, 1.0, groupTerminals);
        groupedTerminalWeight += groupTerminals ? terminalWeight : 0.0;
        if (preferIterationIndexedArrayTerminal) {
            Factory<? extends IRNode> iterationArrayTerminalFactory =
                    new IterationIndexedArrayElementFactory(ownerClass, resultType, true);
            terminalRule.add("iteration_indexed_array_terminal", iterationArrayTerminalFactory,
                    ITERATION_INDEXED_ARRAY_TERMINAL_WEIGHT);
            groupedTerminalWeight += terminalWeight;
        }
        if (isReferenceTerminalType(resultType)) {
            Factory<? extends IRNode> classTerminalFactory = new ClassTerminalFactory(
                    complexityLimit, operatorLimit, ownerClass, resultType, exceptionSafe, noconsts);
            double classTerminalWeight = terminalWeight * 2.0;
            addTerminal("class_terminal", classTerminalFactory, classTerminalWeight, 2.0, groupTerminals);
            groupedTerminalWeight += groupTerminals ? classTerminalWeight : 0.0;
        }
        if (groupTerminals) {
            rule.add("terminal", terminalRule, groupedTerminalWeight);
        }
        double operatorEnableProbability = Math.max(0.0,
                (1.0 - ProductionParams.expressionStopPercent.value() / 100.0) * 0.6);
        boolean operatorsEnabledLive = operatorLimit > 0 && complexityLimit > 0
                && PseudoRandom.randomSilent() < operatorEnableProbability;
        boolean operatorsEnabled = GenomeChoice.bool(operatorsEnabledLive);
        if (expressionDebugEnabled) {
            System.out.printf("EXPR_DEBUG init seed=%d terminalWeight=%.3f operatorEnableP=%.3f operatorEnabled=%s baseStopP=%.3f maxStopP=%.3f halfDepth=%d%n",
                    SEED, terminalWeight, operatorEnableProbability, operatorsEnabled,
                    baseStopProbability, maxStopProbability, stopHalfDepth);
            System.out.printf("EXPR_DEBUG terminal_context type=%s noConsts=%s literalSupported=%s%n",
                    resultType.getName(), noConsts, supportsLiteral(resultType));
        }
        if (operatorsEnabled) {
            final boolean numericResultType = isArithmeticFriendlyResultType(resultType);
            final double castWeight = numericResultType ? NUMERIC_CAST_WEIGHT : CAST_WEIGHT;
            final double arithmeticWeight = numericResultType ? NUMERIC_ARITHMETIC_WEIGHT : ARITHMETIC_WEIGHT;
            final double assignmentWeight = numericResultType ? NUMERIC_ASSIGNMENT_WEIGHT : 1.0;
            rule.add("cast", builder.getCastOperatorFactory(), castWeight);
            rule.add("arithmetic", builder.getArithmeticOperatorFactory(), arithmeticWeight);
            rule.add("logic", builder.getLogicOperatorFactory());
            rule.add("bitwise", new BitwiseOperatorFactory(complexityLimit, operatorLimit, ownerClass,
                    resultType, exceptionSafe, noconsts));
            rule.add("assignment", builder.getAssignmentOperatorFactory(), assignmentWeight);
            rule.add("ternary", builder.getTernaryOperatorFactory());
            double functionWeight = ProductionParams.nondeterminism.value() > 0 ? 1.2 : 0.1;
            rule.add("function", builder.getFunctionFactory(), functionWeight);
            rule.add("str_plus", builder.setOperatorKind(OperatorKind.STRADD).getBinaryOperatorFactory());
            if (!ProductionParams.disableArrays.value() && !exceptionSafe) {
                //rule.add("array_creation", builder.getArrayCreationFactory());
                double arrayWeight = 1.0
                        + Math.max(0, ProductionParams.arrayProductionWeightBonus.value()) / 100.0;
                rule.add("array_element", builder.getArrayElementFactory(), arrayWeight);
                rule.add("array_extraction", builder.getArrayExtractionFactory(), arrayWeight);
            }
        }
    }

    private void addTerminal(String name, Factory<? extends IRNode> factory, double expressionWeight,
            double terminalChoiceWeight, boolean grouped) {
        if (!grouped) {
            rule.add(name, factory, expressionWeight);
        }
        terminalRule.add(name, factory, terminalChoiceWeight);
    }

    private static boolean isArithmeticFriendlyResultType(Type resultType) {
        return TypeBoxingUtil.isArithmeticResultType(resultType);
    }

    private static boolean isReferenceTerminalType(Type resultType) {
        return resultType instanceof TypeKlass && !resultType.equals(TypeList.STRING);
    }

    @Override
    protected IRNode sproduce() throws ProductionFailedException {
        long expressionRngInSeed = Genome.startExpressionScope(PseudoRandom.getCurrentSeed());
        PseudoRandom.setCurrentSeed(expressionRngInSeed);
        Genome.recordCurrentExpressionGene(expressionRngInSeed);
        int depth = enterDepth();
        DebugStats stats = DEBUG_STATS.get();
        stats.maxDepth = Math.max(stats.maxDepth, depth);
        Throwable inFlight = null;
        try {
            Logger.trace(":expression-seed " + PseudoRandom.getCurrentSeed());
            if (expressionDebugEnabled && depth >= expressionDepthWarn) {
                System.out.printf("EXPR_DEBUG depth_warn depth=%d seed=%d stopP=%.3f%n",
                        depth, PseudoRandom.getCurrentSeed(), computeStopProbability(depth));
            }
            if (expressionDepthHardLimit > 0 && depth > expressionDepthHardLimit) {
                stats.depthHardLimitTrips++;
                if (expressionDebugEnabled) {
                    System.out.printf("EXPR_DEBUG depth_hard_limit depth=%d hardLimit=%d seed=%d%n",
                            depth, expressionDepthHardLimit, PseudoRandom.getCurrentSeed());
                }
                throw new ProductionFailedException();
            }
            if (depth > expressionMaxDepth) {
                stats.depthHardLimitTrips++;
                return produceTerminalOrThrow(depth, stats);
            }
            if (shouldStopExpressionRecursion(depth)) {
                return produceTerminalOrThrow(depth, stats);
            }
            return rule.produce();
        } catch (Throwable t) {
            inFlight = t;
            throw t;
        } finally {
            exitDepthAndMaybeReport();
            try {
                Genome.endExpressionScope();
            } catch (Throwable endEx) {
                if (inFlight != null) {
                    inFlight.addSuppressed(endEx);
                    if (inFlight instanceof Error err) {
                        throw err;
                    }
                    if (inFlight instanceof RuntimeException re) {
                        throw re;
                    }
                    if (inFlight instanceof ProductionFailedException pfe) {
                        throw pfe;
                    }
                    throw new RuntimeException("Unexpected checked throwable in expression production", inFlight);
                }
                throw endEx;
            }
        }
    }

    private boolean shouldStopExpressionRecursion(int depth) {
        DebugStats stats = DEBUG_STATS.get();
        stats.stopChecks++;
        double dynamicStopProbability = computeStopProbability(depth);
        double draw = Double.NaN;
        if (!Genome.isReplayActive()) {
            // Keep stop decision RNG local: choice is tracked by C-event, so N-event must remain unchanged.
            draw = PseudoRandom.randomSilent();
        }
        boolean shouldStop = GenomeChoice.bool(!Double.isNaN(draw) && draw < dynamicStopProbability);
        if (shouldStop) {
            stats.stopHits++;
        }
        if (expressionDebugEnabled) {
            if (Double.isNaN(draw)) {
                System.out.printf("EXPR_DEBUG stop_decision depth=%d seed=%d stopP=%.3f draw=<replay> stop=%s%n",
                        depth, PseudoRandom.getCurrentSeed(), dynamicStopProbability, shouldStop);
            } else {
                System.out.printf("EXPR_DEBUG stop_decision depth=%d seed=%d stopP=%.3f draw=%.6f stop=%s%n",
                        depth, PseudoRandom.getCurrentSeed(), dynamicStopProbability, draw, shouldStop);
            }
        }
        return shouldStop;
    }

    /*
     * Why this is needed:
     * With a flat stop probability, recursive operator expansions can keep growing for too long
     * and only be cut by hard limits/timeouts. We need a depth-aware taper so deeper recursion
     * becomes increasingly likely to terminate into a terminal expression.
     *
     * Function:
     *   p(depth) = base + (max - base) * depth / (depth + halfDepth)
     *
     * Properties:
     * - simple rational asymptote (no exp/log),
     * - monotonic increase with depth,
     * - p(halfDepth) is midpoint between base and max,
     * - tends to max as depth -> infinity.
     */
    private double computeStopProbability(int depth) {
        return DepthProbabilityTaper.risingAsymptote(
                depth, baseStopProbability, maxStopProbability, stopHalfDepth);
    }

    private int enterDepth() {
        int depth = EXPRESSION_DEPTH.get() + 1;
        EXPRESSION_DEPTH.set(depth);
        return depth;
    }

    private IRNode produceTerminalOrThrow(int depth, DebugStats stats) throws ProductionFailedException {
        try {
            IRNode terminal = terminalRule.produce();
            stats.terminalSuccess++;
            if (expressionDebugEnabled) {
                System.out.printf("EXPR_DEBUG terminal_outcome depth=%d result=success seed=%d%n",
                        depth, PseudoRandom.getCurrentSeed());
            }
            return terminal;
        } catch (ProductionFailedException e) {
            stats.terminalFailed++;
            if (expressionDebugEnabled) {
                System.out.printf("EXPR_DEBUG terminal_outcome depth=%d result=failure seed=%d%n",
                        depth, PseudoRandom.getCurrentSeed());
                logTerminalFailureContext(depth);
            }
            if (depth > 1) {
                throw e;
            }
            return rule.produce();
        }
    }

    private void exitDepthAndMaybeReport() {
        int depth = EXPRESSION_DEPTH.get() - 1;
        if (depth <= 0) {
            if (expressionDebugEnabled) {
                DebugStats stats = DEBUG_STATS.get();
                System.out.printf("EXPR_DEBUG summary maxDepth=%d stopChecks=%d stopHits=%d terminalSuccess=%d terminalFailed=%d depthHardTrips=%d%n",
                        stats.maxDepth, stats.stopChecks, stats.stopHits, stats.terminalSuccess,
                        stats.terminalFailed, stats.depthHardLimitTrips);
            }
            EXPRESSION_DEPTH.set(0);
            DEBUG_STATS.set(new DebugStats());
            return;
        }
        EXPRESSION_DEPTH.set(depth);
    }

    private void logTerminalFailureContext(int depth) {
        long all = 0;
        long initAny = 0;
        long initLocal = 0;
        long initStatic = 0;
        long initNonStaticMember = 0;
        long initFinal = 0;
        for (Symbol s : SymbolTable.get(resultType, VariableInfo.class)) {
            all++;
            VariableInfo vi = (VariableInfo) s;
            if ((vi.flags & VariableInfo.INITIALIZED) > 0) {
                initAny++;
                if ((vi.flags & VariableInfo.LOCAL) > 0) {
                    initLocal++;
                } else if ((vi.flags & VariableInfo.STATIC) > 0) {
                    initStatic++;
                } else {
                    initNonStaticMember++;
                }
                if ((vi.flags & VariableInfo.FINAL) > 0) {
                    initFinal++;
                }
            }
        }
        System.out.printf(
                "EXPR_DEBUG terminal_failure_context depth=%d type=%s noConsts=%s literalSupported=%s vars(all=%d initAny=%d initLocal=%d initStatic=%d initNonStaticMember=%d initFinal=%d)%n",
                depth,
                resultType.getName(),
                noConsts,
                supportsLiteral(resultType),
                all,
                initAny,
                initLocal,
                initStatic,
                initNonStaticMember,
                initFinal);
    }

    private static boolean supportsLiteral(Type t) {
        String n = t.getName();
        return t.equals(TypeList.BOOLEAN)
                || t.equals(TypeList.CHAR)
                || t.equals(TypeList.INT)
                || t.equals(TypeList.LONG)
                || t.equals(TypeList.FLOAT)
                || t.equals(TypeList.DOUBLE)
                || t.equals(TypeList.BYTE)
                || t.equals(TypeList.SHORT)
                || t.equals(TypeList.STRING)
                || "java.lang.Boolean".equals(n)
                || "java.lang.Character".equals(n)
                || "java.lang.Integer".equals(n)
                || "java.lang.Long".equals(n)
                || "java.lang.Float".equals(n)
                || "java.lang.Double".equals(n)
                || "java.lang.Byte".equals(n)
                || "java.lang.Short".equals(n);
    }

    private static final class DebugStats {
        int maxDepth = 0;
        int stopChecks = 0;
        int stopHits = 0;
        int terminalSuccess = 0;
        int terminalFailed = 0;
        int depthHardLimitTrips = 0;
    }
}
