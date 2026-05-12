/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib.jittester.utils;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import jdk.test.lib.jittester.BinaryOperator;
import jdk.test.lib.jittester.Block;
import jdk.test.lib.jittester.CatchBlock;
import jdk.test.lib.jittester.IRNode;
import jdk.test.lib.jittester.Literal;
import jdk.test.lib.jittester.LocalVariable;
import jdk.test.lib.jittester.NonStaticMemberVariable;
import jdk.test.lib.jittester.Nothing;
import jdk.test.lib.jittester.Operator;
import jdk.test.lib.jittester.OperatorKind;
import jdk.test.lib.jittester.PrintVariables;
import jdk.test.lib.jittester.ProductionParams;
import jdk.test.lib.jittester.Statement;
import jdk.test.lib.jittester.StaticMemberVariable;
import jdk.test.lib.jittester.Symbol;
import jdk.test.lib.jittester.TryCatchBlock;
import jdk.test.lib.jittester.Type;
import jdk.test.lib.jittester.TypeList;
import jdk.test.lib.jittester.VariableInfo;
import jdk.test.lib.jittester.VariableInitialization;
import jdk.test.lib.jittester.functions.ArgumentDeclaration;
import jdk.test.lib.jittester.functions.Function;
import jdk.test.lib.jittester.functions.FunctionDefinition;
import jdk.test.lib.jittester.functions.FunctionInfo;
import jdk.test.lib.jittester.functions.Return;
import jdk.test.lib.jittester.loops.CounterInitializer;
import jdk.test.lib.jittester.loops.CounterManipulator;
import jdk.test.lib.jittester.loops.For;
import jdk.test.lib.jittester.loops.Loop;
import jdk.test.lib.jittester.loops.LoopingCondition;
import jdk.test.lib.jittester.types.TypeArray;
import jdk.test.lib.jittester.types.TypeKlass;

public class FixedTrees {
    private static final Literal EOL = new Literal("\n", TypeList.STRING);

    public static FunctionDefinition printVariablesAsFunction(PrintVariables node) {
        return buildPrintFunction(node.getOwner(), node.getVars(), "toString");
    }

    public static FunctionDefinition printFinalVariablesAsFunction(PrintVariables node) {
        return buildPrintFunction(node.getOwner(), node.getFinalVars(), "printFinalState");
    }

    private static FunctionDefinition buildPrintFunction(TypeKlass owner, List<Symbol> vars, String functionName) {
        ArrayList<IRNode> nodes = new ArrayList<>();

        VariableInfo resultInfo = new VariableInfo("result", owner, TypeList.STRING, VariableInfo.LOCAL);
        nodes.add(new Statement(new VariableInitialization(resultInfo, new Literal("[", TypeList.STRING)), true));
        LocalVariable resultVar = new LocalVariable(resultInfo);

        TypeKlass printerKlass = new TypeKlass(ProductionParams.printerClassName());
        VariableInfo thisInfo = new VariableInfo("this", owner,
                owner, VariableInfo.LOCAL | VariableInfo.INITIALIZED);

        LocalVariable thisVar = new LocalVariable(thisInfo);

        for (int i = 0; i < vars.size(); i++) {
            Symbol v = vars.get(i);
            boolean usePathAwareObjectPrint = v.type instanceof TypeKlass || v.type instanceof TypeArray;
            Function call;
            if (usePathAwareObjectPrint) {
                VariableInfo pathArgInfo = new VariableInfo("path", printerKlass, TypeList.STRING,
                        VariableInfo.LOCAL | VariableInfo.INITIALIZED);
                VariableInfo valueArgInfo = new VariableInfo("arg", printerKlass, TypeList.OBJECT,
                        VariableInfo.LOCAL | VariableInfo.INITIALIZED);
                FunctionInfo printInfo = new FunctionInfo("print", printerKlass,
                        TypeList.STRING, 0, FunctionInfo.PUBLIC | FunctionInfo.STATIC, pathArgInfo, valueArgInfo);
                call = new Function(owner, printInfo, null);
                call.addChild(new Literal(v.owner.getName() + "." + v.name, TypeList.STRING));
            } else {
                nodes.add(new Statement(new BinaryOperator(OperatorKind.COMPOUND_ADD, TypeList.STRING, resultVar,
                        new Literal(v.owner.getName() + "." + v.name + " = ", TypeList.STRING)), true));
                VariableInfo argInfo = new VariableInfo("arg", printerKlass, v.type, VariableInfo.LOCAL
                        | VariableInfo.INITIALIZED);
                FunctionInfo printInfo = new FunctionInfo("print", printerKlass,
                        TypeList.STRING, 0, FunctionInfo.PUBLIC | FunctionInfo.STATIC, argInfo);
                call = new Function(owner, printInfo, null);
            }
            VariableInfo varInfo = new VariableInfo(v.name, v.owner, v.type, v.flags);
            if (v.isStatic()) {
                // Use current owner context so static members from other classes are qualified.
                call.addChild(new StaticMemberVariable(owner, varInfo));
            } else {
                call.addChild(new NonStaticMemberVariable(thisVar, varInfo));
            }
            nodes.add(new Statement(new BinaryOperator(OperatorKind.COMPOUND_ADD, TypeList.STRING, resultVar,
                    call), true));
            if (i < vars.size() - 1) {
                nodes.add(new Statement(new BinaryOperator(OperatorKind.COMPOUND_ADD, TypeList.STRING, resultVar,
                        EOL), true));
            }
        }
        nodes.add(new Statement(
                new BinaryOperator(OperatorKind.COMPOUND_ADD, TypeList.STRING, resultVar, new Literal("]\n", TypeList.STRING)),
                true));

        Block block = new Block(owner, TypeList.STRING, nodes, 1);
        FunctionInfo printInfo = new FunctionInfo(functionName, owner, TypeList.STRING, 0L, FunctionInfo.PUBLIC, thisInfo);
        return new FunctionDefinition(printInfo, new ArrayList<>(), block, new Return(resultVar));
    }

    public static FunctionDefinition generateMainOrExecuteMethod(TypeKlass owner, boolean isMain) {
        Nothing nothing = new Nothing();
        ArrayList<IRNode> testCallNodeContent = new ArrayList<>();
        VariableInfo tInfo = new VariableInfo("t", owner, owner, VariableInfo.LOCAL);
        LocalVariable tVar = new LocalVariable(tInfo);
        Function testCallNode = new Function(owner, new FunctionInfo("test", owner, TypeList.VOID,
                0L, FunctionInfo.PRIVATE, tInfo), null);
        testCallNode.addChild(tVar);
        testCallNodeContent.add(new Statement(testCallNode, true));
        // { t.test(); } or { t.test(); System.out.print(t); }
        Block testCallNodeBlock = new Block(owner, TypeList.VOID, testCallNodeContent, 4);

        FunctionInfo printInfo = buildPrintFunctionInfo(owner);
        Function printFinalState = new Function(owner, new FunctionInfo("printFinalState", owner, TypeList.STRING,
                0L, FunctionInfo.PUBLIC, tInfo), null);
        printFinalState.addChild(tVar);
        TypeKlass systemKlass = new TypeKlass("java.lang.System");
        TypeKlass printStreamKlass = new TypeKlass("java.io.PrintStream");
        VariableInfo systemOutInfo = new VariableInfo("out", systemKlass, printStreamKlass,
                VariableInfo.STATIC | VariableInfo.PUBLIC);
        StaticMemberVariable systemOutVar = new StaticMemberVariable(owner, systemOutInfo);
        ArrayList<IRNode> printArgs = new ArrayList<>();
        printArgs.add(systemOutVar);
        printArgs.add(tVar);
        Function print = new Function(printStreamKlass, printInfo, printArgs);

        ArrayList<IRNode> finalMarkerPrintArgs = new ArrayList<>();
        finalMarkerPrintArgs.add(systemOutVar);
        finalMarkerPrintArgs.add(new Literal("### FINAL STATE ###\n", TypeList.STRING));
        Function finalMarkerPrint = new Function(printStreamKlass, printInfo, finalMarkerPrintArgs);

        ArrayList<IRNode> initialMarkerPrintArgs = new ArrayList<>();
        initialMarkerPrintArgs.add(systemOutVar);
        initialMarkerPrintArgs.add(new Literal("### INITIAL STATE ###\n", TypeList.STRING));
        Function initialMarkerPrint = new Function(printStreamKlass, printInfo, initialMarkerPrintArgs);

        ArrayList<IRNode> printFinalArgs = new ArrayList<>();
        printFinalArgs.add(systemOutVar);
        printFinalArgs.add(printFinalState);
        Function printFinal = new Function(printStreamKlass, printInfo, printFinalArgs);
        ArrayList<IRNode> testAndPrintNodeContent = new ArrayList<>();
        testAndPrintNodeContent.add(new Statement(testCallNode, true));
        testAndPrintNodeContent.add(new Statement(print, true));
        Block testAndPrintNodeBlock = new Block(owner, TypeList.VOID, testAndPrintNodeContent, 4);

        IRNode tryNode = testCallNodeBlock;
        For mainLoopForNode = null;
        Function iterationMarkerPrint = null;
        if (isMain) {
            VariableInfo iInfo = new VariableInfo("i", owner, TypeList.INT, VariableInfo.LOCAL);
            LocalVariable iVar = new LocalVariable(iInfo);
            Operator increaseCounter = new BinaryOperator(OperatorKind.ASSIGN, TypeList.INT,
                    iVar,
                    new BinaryOperator(OperatorKind.ADD, TypeList.INT,
                            iVar, new Literal(1, TypeList.INT)));
            Loop loop = new Loop();
            Block emptyBlock = new Block(owner, TypeList.VOID, new LinkedList<>(), 3);
            loop.initialization = new CounterInitializer(iInfo, new Literal(0, TypeList.INT));
            loop.manipulator = new CounterManipulator(new Statement(increaseCounter, false));
            int baseIterations = Math.max(1, ProductionParams.mainLoopIterations.value());
            int jitter = Math.max(0, ProductionParams.mainLoopJitter.value());
            int delta = jitter == 0 ? 0 : PseudoRandom.randomNotNegative(2 * jitter + 1) - jitter;
            int limit = Math.max(1, baseIterations + delta);
            loop.condition = new LoopingCondition(new BinaryOperator(OperatorKind.LT, TypeList.BOOLEAN, iVar,
                    new Literal(limit, TypeList.INT)));
            ArrayList<IRNode> testAndPrintWithIterationMarkerContent = new ArrayList<>();
            ArrayList<IRNode> markerPrintArgs = new ArrayList<>();
            markerPrintArgs.add(systemOutVar);
            markerPrintArgs.add(new Literal("### ITERATION ###\n", TypeList.STRING));
            iterationMarkerPrint = new Function(printStreamKlass, printInfo, markerPrintArgs);
            testAndPrintWithIterationMarkerContent.add(new Statement(iterationMarkerPrint, true));
            testAndPrintWithIterationMarkerContent.add(new Statement(testCallNode, true));
            testAndPrintWithIterationMarkerContent.add(new Statement(print, true));
            Block testAndPrintWithIterationMarkerBlock = new Block(owner, TypeList.VOID,
                    testAndPrintWithIterationMarkerContent, 4);

            For forNode = new For(4, loop, limit, emptyBlock, new Statement(nothing, false),
                    new Statement(nothing, false), testAndPrintWithIterationMarkerBlock, emptyBlock, emptyBlock);
            mainLoopForNode = forNode;
            tryNode = forNode;
        }

        FunctionInfo constrInfo = new FunctionInfo(owner.getName(), owner, owner, 0, FunctionInfo.PUBLIC);
        Function testConstructor = new Function(owner, constrInfo, null);
        // Test t = new Test()
        VariableInitialization testInit = new VariableInitialization(tInfo, testConstructor);

        TypeKlass throwableKlass = new TypeKlass("java.lang.Throwable");
        List<Type> throwables = new ArrayList<>();
        throwables.add(throwableKlass);

        StaticMemberVariable systemErrVar = new StaticMemberVariable(owner,
                new VariableInfo("err", systemKlass, printStreamKlass, VariableInfo.STATIC | VariableInfo.PUBLIC));

        LocalVariable exVar = new LocalVariable(
                new VariableInfo("ex", owner, throwableKlass, VariableInfo.LOCAL | VariableInfo.INITIALIZED));
        TypeKlass classKlass = new TypeKlass("java.lang.Class");
        FunctionInfo getClassInfo = new FunctionInfo("getClass", TypeList.OBJECT,
                classKlass, 0, FunctionInfo.PUBLIC,
                new VariableInfo("this", owner, TypeList.OBJECT, VariableInfo.LOCAL | VariableInfo.INITIALIZED));
        Function getClass = new Function(TypeList.OBJECT, getClassInfo, Arrays.asList(exVar));
        FunctionInfo getNameInfo = new FunctionInfo("getName", classKlass,
                TypeList.STRING, 0, FunctionInfo.PUBLIC,
                new VariableInfo("this", owner, TypeList.OBJECT, VariableInfo.LOCAL | VariableInfo.INITIALIZED));
        Function getName = new Function(classKlass, getNameInfo, Arrays.asList(getClass));
        ArrayList<IRNode> printExceptionBlockContent = new ArrayList<>();
        // { System.err.print(ex.getClass().getName()); System.err.print("\n"); }
        printExceptionBlockContent.add(new Statement(
            new Function(printStreamKlass, printInfo, Arrays.asList(systemErrVar, getName)), true));
        printExceptionBlockContent.add(new Statement(
            new Function(printStreamKlass, printInfo, Arrays.asList(systemErrVar, EOL)), true));

        Block printExceptionBlock = new Block(owner, TypeList.VOID, printExceptionBlockContent, 3);
        List<CatchBlock> catchBlocks1 = new ArrayList<>();
        catchBlocks1.add(new CatchBlock(printExceptionBlock, throwables, 3));
        List<CatchBlock> catchBlocks2 = new ArrayList<>();
        catchBlocks2.add(new CatchBlock(printExceptionBlock, throwables, 3));
        List<CatchBlock> catchBlocks3 = new ArrayList<>();
        catchBlocks3.add(new CatchBlock(printExceptionBlock, throwables, 2));

        if (mainLoopForNode != null) {
            // Keep main-loop iterations alive and always print state:
            // marker -> try(test) catch(Throwable) -> print(state)
            ArrayList<IRNode> guardedBodyContent = new ArrayList<>();
            if (iterationMarkerPrint != null) {
                guardedBodyContent.add(new Statement(iterationMarkerPrint, true));
            }
            ArrayList<IRNode> testOnlyContent = new ArrayList<>();
            testOnlyContent.add(new Statement(testCallNode, true));
            Block testOnlyBlock = new Block(owner, TypeList.VOID, testOnlyContent, 4);
            guardedBodyContent.add(new TryCatchBlock(testOnlyBlock, nothing, catchBlocks2, 4));
            guardedBodyContent.add(new Statement(print, true));
            Block guardedBody = new Block(owner, TypeList.VOID, guardedBodyContent, 4);
            mainLoopForNode.getChildren().set(For.ForPart.BODY1.ordinal(), guardedBody);
        }

        TryCatchBlock tryCatch1 = new TryCatchBlock(tryNode, nothing, catchBlocks1, 3);
        ArrayList<IRNode> printBlockContent = new ArrayList<>();
        printBlockContent.add(new Statement(print, true));
        Block printBlock = new Block(owner, TypeList.VOID, printBlockContent, 3);
        TryCatchBlock tryCatch2 = new TryCatchBlock(printBlock, nothing, catchBlocks2, 3);

        ArrayList<IRNode> printFinalBlockContent = new ArrayList<>();
        printFinalBlockContent.add(new Statement(finalMarkerPrint, true));
        printFinalBlockContent.add(new Statement(printFinal, true));
        Block printFinalBlock = new Block(owner, TypeList.VOID, printFinalBlockContent, 3);
        TryCatchBlock tryCatchFinal = new TryCatchBlock(printFinalBlock, nothing, catchBlocks2, 3);

        ArrayList<IRNode> printInitialBlockContent = new ArrayList<>();
        printInitialBlockContent.add(new Statement(initialMarkerPrint, true));
        printInitialBlockContent.add(new Statement(printFinal, true));
        Block printInitialBlock = new Block(owner, TypeList.VOID, printInitialBlockContent, 3);
        TryCatchBlock tryCatchInitial = new TryCatchBlock(printInitialBlock, nothing, catchBlocks2, 3);

        List<IRNode> mainTryCatchBlockContent = new ArrayList<>();
        mainTryCatchBlockContent.add(new Statement(testInit, true));
        if (isMain) {
            // Print one-time initial final-field snapshot before iterations start.
            mainTryCatchBlockContent.add(tryCatchInitial);
        }
        mainTryCatchBlockContent.add(tryCatch1);
        if (isMain) {
            // Print final-field snapshot once after the loop for diagnostics.
            mainTryCatchBlockContent.add(tryCatchFinal);
        }
        if (!isMain) {
            // execute() runs test once, then prints once.
            mainTryCatchBlockContent.add(tryCatch2);
        }
        Block mainTryCatchBlock = new Block(owner, TypeList.VOID, mainTryCatchBlockContent, 2);
        TryCatchBlock mainTryCatch = new TryCatchBlock(mainTryCatchBlock, nothing, catchBlocks3, 2);
        ArrayList<IRNode> bodyContent = new ArrayList<>();
        bodyContent.add(mainTryCatch);
        Block funcBody = new Block(owner, TypeList.VOID, bodyContent, 1);

        // static main(String[] args)V or static execute()V
        VariableInfo mainArgs = new VariableInfo("args", owner,
                new TypeArray(TypeList.STRING, 1), VariableInfo.LOCAL);
        FunctionInfo fInfo = isMain
                ? new FunctionInfo("main", owner, TypeList.VOID, 0, FunctionInfo.PUBLIC | FunctionInfo.STATIC, mainArgs)
                : new FunctionInfo("execute", owner, TypeList.VOID, 0, FunctionInfo.PUBLIC | FunctionInfo.STATIC);
        ArrayList<ArgumentDeclaration> argDecl = new ArrayList<>();
        if (isMain) {
            argDecl.add(new ArgumentDeclaration(mainArgs));
        }
        return new FunctionDefinition(fInfo, argDecl, funcBody, new Return(nothing));
    }

    private static FunctionInfo buildPrintFunctionInfo(TypeKlass owner) {
        TypeKlass printStreamKlass = new TypeKlass("java.io.PrintStream");
        return new FunctionInfo("print", printStreamKlass,
                TypeList.VOID, 0, FunctionInfo.PUBLIC,
                new VariableInfo("this", owner, printStreamKlass, VariableInfo.LOCAL | VariableInfo.INITIALIZED),
                new VariableInfo("t", owner, TypeList.OBJECT,
                        VariableInfo.LOCAL | VariableInfo.INITIALIZED));
    }
}
