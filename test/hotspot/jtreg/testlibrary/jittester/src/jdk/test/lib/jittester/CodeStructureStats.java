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

package jdk.test.lib.jittester;

import jdk.test.lib.jittester.classes.Klass;
import jdk.test.lib.jittester.classes.MainKlass;
import jdk.test.lib.jittester.functions.ConstructorDefinition;
import jdk.test.lib.jittester.functions.FunctionDefinition;
import jdk.test.lib.jittester.functions.FunctionInfo;
import jdk.test.lib.jittester.functions.FunctionRedefinition;
import jdk.test.lib.jittester.functions.StaticConstructorDefinition;

public final class CodeStructureStats {
    private final int functions;
    private final int supportFunctions;
    private final int constructors;
    private final int staticInitializers;
    private final int testMethods;

    private CodeStructureStats(int functions, int supportFunctions, int constructors, int staticInitializers,
            int testMethods) {
        this.functions = functions;
        this.supportFunctions = supportFunctions;
        this.constructors = constructors;
        this.staticInitializers = staticInitializers;
        this.testMethods = testMethods;
    }

    public static CodeStructureStats analyze(IRNode privateClasses, IRNode mainClass) {
        Counter counter = new Counter();
        counter.visit(privateClasses);
        counter.visit(mainClass);
        return new CodeStructureStats(counter.functions, counter.supportFunctions + counter.syntheticPrintFunctions(),
                counter.constructors,
                counter.staticInitializers, mainClass == null ? 0 : 1);
    }

    public String formatSourceHeader() {
        return "CODE STRUCTURE:\n"
                + "function=" + functions + "\n"
                + "support=" + supportFunctions + "\n"
                + "constructor=" + constructors + "\n"
                + "static-init=" + staticInitializers + "\n"
                + "test=" + testMethods + "\n"
                + "score-methods=" + (functions + constructors) + "\n";
    }

    public static boolean isSupportFunction(FunctionInfo functionInfo) {
        String name = functionInfo.name;
        return "main".equals(name)
                || "execute".equals(name)
                || "toString".equals(name)
                || "printFinalState".equals(name);
    }

    private static final class Counter {
        private int functions;
        private int supportFunctions;
        private int printableClasses;
        private int constructors;
        private int staticInitializers;

        private void visit(IRNode node) {
            if (node == null) {
                return;
            }
            if (node instanceof Klass || node instanceof MainKlass) {
                printableClasses++;
            }
            if (node instanceof FunctionDefinition functionDefinition) {
                countFunction(functionDefinition.getFunctionInfo());
            } else if (node instanceof FunctionRedefinition functionRedefinition) {
                countFunction(functionRedefinition.getFunctionInfo());
            } else if (node instanceof ConstructorDefinition) {
                constructors++;
            } else if (node instanceof StaticConstructorDefinition) {
                staticInitializers++;
            }
            for (IRNode child : node.getChildren()) {
                visit(child);
            }
        }

        private void countFunction(FunctionInfo functionInfo) {
            if (isSupportFunction(functionInfo)) {
                supportFunctions++;
            } else {
                functions++;
            }
        }

        private int syntheticPrintFunctions() {
            return printableClasses * 2;
        }
    }
}
