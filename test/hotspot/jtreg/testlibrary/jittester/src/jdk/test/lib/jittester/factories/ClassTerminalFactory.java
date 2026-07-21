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

package jdk.test.lib.jittester.factories;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import jdk.test.lib.jittester.IRNode;
import jdk.test.lib.jittester.LocalVariable;
import jdk.test.lib.jittester.NonStaticMemberVariable;
import jdk.test.lib.jittester.ProductionFailedException;
import jdk.test.lib.jittester.StaticMemberVariable;
import jdk.test.lib.jittester.Symbol;
import jdk.test.lib.jittester.SymbolTable;
import jdk.test.lib.jittester.Type;
import jdk.test.lib.jittester.VariableInfo;
import jdk.test.lib.jittester.functions.Function;
import jdk.test.lib.jittester.functions.FunctionInfo;
import jdk.test.lib.jittester.types.TypeKlass;
import jdk.test.lib.jittester.utils.Genome;
import jdk.test.lib.jittester.utils.PseudoRandom;

/*
 * Produces reference/class terminals for expression fallback.
 * Preference is constructor calls ("new ..."), then initialized variables.
 */
class ClassTerminalFactory extends SafeFactory<IRNode> {
    private static final String CONSTRUCTOR_MAGNET_CHANNEL = "use.class.terminal.constructor";
    private static final String VARIABLE_MAGNET_CHANNEL = "use.class.terminal.variable";
    private static final String PROBE_GENE_PROP = "jittester.debug.classterminal.gene";
    private static final String PROBE_CRASH_PROP = "jittester.debug.classterminal.crash";
    private final long complexityLimit;
    private final int operatorLimit;
    private final TypeKlass ownerClass;
    private final Type resultType;
    private final boolean exceptionSafe;
    private final boolean noConsts;

    ClassTerminalFactory(long complexityLimit, int operatorLimit, TypeKlass ownerClass, Type resultType,
            boolean exceptionSafe, boolean noConsts) {
        this.complexityLimit = complexityLimit;
        this.operatorLimit = operatorLimit;
        this.ownerClass = ownerClass;
        this.resultType = resultType;
        this.exceptionSafe = exceptionSafe;
        this.noConsts = noConsts;
    }

    @Override
    protected IRNode sproduce() throws ProductionFailedException {
        IRNode byCtor = tryConstructorCall();
        if (byCtor != null) {
            return byCtor;
        }
        IRNode byVar = tryInitializedVariable();
        if (byVar != null) {
            return byVar;
        }
        throw new ProductionFailedException();
    }

    private IRNode tryConstructorCall() {
        if (System.getProperty(PROBE_GENE_PROP) != null) {
            System.err.println("[JTDBG][ClassTerminalFactory] entered tryConstructorCall probe="
                    + System.getProperty(PROBE_GENE_PROP)
                    + " expr=" + Genome.getCurrentExpressionScopeSeed());
        }
        maybeProbe("before", null);
        List<FunctionInfo> constructors = availableConstructors();
        maybeProbe("after-available", constructors);
        boolean replayMode = Genome.isReplayActive();
        if (replayMode) {
            long replayTargetGene = SymbolTable.consumeMagnetTargetGene(CONSTRUCTOR_MAGNET_CHANNEL);
            if (replayTargetGene == -1L) {
                return null;
            }
            FunctionInfo selected = SymbolTable.attractByMagnet(constructors, replayTargetGene, CONSTRUCTOR_MAGNET_CHANNEL);
            constructors = new ArrayList<>();
            constructors.add(selected);
        }
        maybeProbe("after-magnet", constructors);
        for (FunctionInfo constructor : constructors) {
            try {
                if (!replayMode) {
                    Genome.beginSpeculativeRecord();
                    SymbolTable.recordMagnetTargetSelection(CONSTRUCTOR_MAGNET_CHANNEL,
                            constructor.getMagnetismGeneId(), constructors, constructor);
                }
                Function produced = produceConstructorCall(constructor);
                if (!replayMode) {
                    Genome.commitSpeculativeRecord();
                }
                return produced;
            } catch (ProductionFailedException ignore) {
                if (!replayMode) {
                    Genome.rollbackSpeculativeRecord();
                }
                if (replayMode) {
                    throw new RuntimeException("Genome broken around magnet gene "
                            + constructor.getMagnetismGeneId()
                            + " for channel '" + CONSTRUCTOR_MAGNET_CHANNEL + "'", ignore);
                }
            } catch (RuntimeException e) {
                if (!replayMode) {
                    Genome.rollbackSpeculativeRecord();
                }
                throw e;
            }
        }
        if (!replayMode) {
            SymbolTable.recordMagnetTargetGene(CONSTRUCTOR_MAGNET_CHANNEL, -1L);
        }
        return null;
    }

    private void maybeProbe(String phase, List<FunctionInfo> constructors) {
        String configured = System.getProperty(PROBE_GENE_PROP);
        if (configured == null || configured.trim().isEmpty()) {
            return;
        }
        configured = configured.trim();
        Long exprSeed = Genome.getCurrentExpressionScopeSeed();
        String currentToken = exprSeed == null ? "none" : ("E" + exprSeed);
        if (!"any".equalsIgnoreCase(configured) && !configured.equals(currentToken)) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[JTDBG][ClassTerminalFactory] phase=").append(phase)
                .append(" expr=").append(currentToken)
                .append(" resultType=").append(resultType.getName())
                .append(" owner=").append(ownerClass.getName())
                .append(" complexityLimit=").append(complexityLimit)
                .append(" operatorLimit=").append(operatorLimit)
                .append(" thisForbidden=").append(ThisVariableControl.isThisForbidden())
                .append('\n');
        if (constructors == null) {
            sb.append("constructors=<null>\n");
        } else {
            sb.append("constructors.size=").append(constructors.size()).append('\n');
            int max = Math.min(12, constructors.size());
            for (int i = 0; i < max; i++) {
                FunctionInfo fi = constructors.get(i);
                sb.append("  [").append(i).append("] ")
                        .append(fi.owner.getName()).append(".<init>")
                        .append(" args=").append(fi.argTypes.size())
                        .append(" flags=0x").append(Integer.toHexString(fi.flags))
                        .append(" magnet=").append(fi.getMagnetismGeneId())
                        .append('\n');
            }
        }
        sb.append(SymbolTable.dumpSnapshot(20, 12));
        System.err.print(sb.toString());
        if (Boolean.getBoolean(PROBE_CRASH_PROP)) {
            throw new RuntimeException("ClassTerminalFactory probe hit for " + currentToken
                    + " at phase " + phase);
        }
    }

    private List<FunctionInfo> availableConstructors() {
        Collection<Symbol> raw = SymbolTable.get(resultType, FunctionInfo.class);
        List<FunctionInfo> zeroArg = new ArrayList<>();
        List<FunctionInfo> withArgs = new ArrayList<>();
        for (Symbol symbol : raw) {
            FunctionInfo f = (FunctionInfo) symbol;
            if (!f.isConstructor()) {
                continue;
            }
            if (f.owner.isAbstract() || f.owner.isInterface()) {
                continue;
            }
            if (!isVisibleFromCurrentOwner(f)) {
                continue;
            }
            if (f.argTypes.isEmpty()) {
                zeroArg.add(f);
            } else {
                withArgs.add(f);
            }
        }
        PseudoRandom.shuffle(zeroArg);
        PseudoRandom.shuffle(withArgs);
        ArrayList<FunctionInfo> ordered = new ArrayList<>(zeroArg.size() + withArgs.size());
        ordered.addAll(zeroArg);
        ordered.addAll(withArgs);
        return ordered;
    }

    private boolean isVisibleFromCurrentOwner(FunctionInfo f) {
        if (ownerClass.equals(f.owner)) {
            return true;
        }
        int access = f.flags & Symbol.ACCESS_ATTRS_MASK;
        return access == Symbol.PUBLIC || access == Symbol.DEFAULT;
    }

    private Function produceConstructorCall(FunctionInfo constructorInfo) throws ProductionFailedException {
        List<IRNode> args = new ArrayList<>(constructorInfo.argTypes.size());
        if (!constructorInfo.argTypes.isEmpty()) {
            long argComplexityLimit = Math.max(1L,
                    (complexityLimit - 1 - constructorInfo.complexity) / constructorInfo.argTypes.size());
            int argOperatorLimit = Math.max(1, (operatorLimit - 1) / constructorInfo.argTypes.size());
            IRNodeBuilder b = new IRNodeBuilder().setOwnerKlass(ownerClass)
                    .withComplexityLimit(argComplexityLimit)
                    .withOperatorLimit(argOperatorLimit)
                    .setExceptionSafe(exceptionSafe)
                    .setNoConsts(noConsts);
            for (VariableInfo argType : constructorInfo.argTypes) {
                args.add(b.setResultType(argType.type).getLimitedExpressionFactory().produce());
            }
        }
        Function produced = new Function(ownerClass, constructorInfo, args);
        Long expressionScopeSeed = Genome.getCurrentExpressionScopeSeed();
        if (expressionScopeSeed != null) {
            produced.setExpressionGeneSeed(expressionScopeSeed);
        }
        return produced;
    }

    private IRNode tryInitializedVariable() {
        ArrayList<Symbol> vars = new ArrayList<>(SymbolTable.get(resultType, VariableInfo.class));
        if (vars.isEmpty()) {
            return null;
        }
        boolean replayMode = Genome.isReplayActive();
        if (replayMode) {
            long replayTargetGene = SymbolTable.consumeMagnetTargetGene(VARIABLE_MAGNET_CHANNEL);
            if (replayTargetGene == -1L) {
                return null;
            }
            Symbol selected = SymbolTable.attractByMagnet(vars, replayTargetGene, VARIABLE_MAGNET_CHANNEL);
            vars = new ArrayList<>();
            vars.add(selected);
        } else {
            // Replay selects directly by recorded magnet target and does not consume shuffle RNG events.
            // Keep this shuffle out of genome event stream.
            PseudoRandom.shuffleSilent(vars);
        }
        for (Symbol symbol : vars) {
            VariableInfo varInfo = (VariableInfo) symbol;
            if ((varInfo.flags & VariableInfo.INITIALIZED) == 0) {
                continue;
            }
            if (ThisVariableControl.isThisForbidden() && "this".equals(varInfo.name)) {
                continue;
            }
            try {
                if (!replayMode) {
                    Genome.beginSpeculativeRecord();
                    SymbolTable.recordMagnetTargetSelection(VARIABLE_MAGNET_CHANNEL,
                            varInfo.getMagnetismGeneId(), vars, varInfo);
                }
                if ((varInfo.flags & VariableInfo.LOCAL) > 0) {
                    if (!replayMode) {
                        Genome.commitSpeculativeRecord();
                    }
                    return new LocalVariable(varInfo);
                }
                if ((varInfo.flags & VariableInfo.STATIC) > 0) {
                    if (!replayMode) {
                        Genome.commitSpeculativeRecord();
                    }
                    return new StaticMemberVariable(ownerClass, varInfo);
                }
                IRNodeBuilder b = new IRNodeBuilder().setOwnerKlass(ownerClass)
                        .withComplexityLimit(Math.max(1L, complexityLimit - 1))
                        .withOperatorLimit(Math.max(1, operatorLimit - 1))
                        .setResultType(varInfo.owner)
                        .setExceptionSafe(exceptionSafe)
                        .setNoConsts(noConsts);
                IRNode object = b.getLimitedExpressionFactory().produce();
                if (!replayMode) {
                    Genome.commitSpeculativeRecord();
                }
                return new NonStaticMemberVariable(object, varInfo);
            } catch (ProductionFailedException ignore) {
                if (!replayMode) {
                    Genome.rollbackSpeculativeRecord();
                }
                if (replayMode) {
                    throw new RuntimeException("Genome broken around magnet gene "
                            + varInfo.getMagnetismGeneId()
                            + " for channel '" + VARIABLE_MAGNET_CHANNEL + "'", ignore);
                }
            } catch (RuntimeException e) {
                if (!replayMode) {
                    Genome.rollbackSpeculativeRecord();
                }
                throw e;
            }
        }
        if (!replayMode) {
            SymbolTable.recordMagnetTargetGene(VARIABLE_MAGNET_CHANNEL, -1L);
        }
        return null;
    }
}
