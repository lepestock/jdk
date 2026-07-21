/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Function;
import jdk.test.lib.jittester.utils.Genome;
import jdk.test.lib.jittester.visitors.JavaCodeVisitor;

/**
 * Generates java source code from IRTree
 */
public class JavaCodeGenerator extends TestsGenerator {
    private static final String DEFAULT_SUFFIX = "java_tests";

    JavaCodeGenerator() {
        this(DEFAULT_SUFFIX, JavaCodeGenerator::generatePrerunAction, "-Xcomp");
    }

    JavaCodeGenerator(String prefix, Function<String, String[]> preRunActions, String jtDriverOptions) {
        super(prefix, preRunActions, jtDriverOptions);
    }

    @Override
    public void accept(IRTreeGenerator.Test test) {
        IRNode mainClass = test.mainClass();
        String mainClassName = mainClass.getName();
        generateSources(test.seed(), mainClass, test.privateClasses());
        compilePrinter();
        compilePulse();
        compileJavaFile(mainClassName);
        generateGoldenOut(mainClassName);
    }

    private void generateSources(long seed, IRNode mainClass, IRNode privateClasses) {
        String mainClassName = mainClass.getName();
        StringBuilder code = new StringBuilder();
        JavaCodeVisitor vis = new JavaCodeVisitor();
        code.append(getJtregHeader(mainClassName, seed));
        int richestExpressionCount = Math.max(0, ProductionParams.debugRichestExpressionCount.value());
        if (richestExpressionCount > 0) {
            ExpressionStats.Snapshot snapshot = ExpressionStats.analyze(privateClasses, mainClass);
            code.append("/*\n")
                .append(snapshot.formatSourceHeader(richestExpressionCount))
                .append("*/\n");
        }
        if (ProductionParams.embedPrinterClass.value()) {
            code.append(loadEmbeddedPrinterSource())
                .append("\n");
        }
        if (privateClasses != null) {
            code.append(privateClasses.accept(vis));
        }
        code.append(mainClass.accept(vis));
        if (ProductionParams.injectRuntimeNondeterminism.value()) {
            injectRuntimeNondeterminismHook(code, mainClassName);
        }
        Path targetDir = getGeneratorDir(mainClassName);
        writeFile(targetDir, mainClassName + ".java", code.toString());
    }

    private static void injectRuntimeNondeterminismHook(StringBuilder code, String mainClassName) {
        String signature = "public static void main(java.lang.String[] args)";
        int sig = code.indexOf(signature);
        if (sig < 0) {
            return;
        }
        int brace = code.indexOf("{", sig);
        if (brace < 0) {
            return;
        }
        String injection = "\n        System.out.println(\"NONDET:" + mainClassName + "=\" + System.nanoTime());";
        code.insert(brace + 1, injection);
    }

    private void compileJavaFile(String mainClassName) {
        Path targetDir = getGeneratorDir(mainClassName);
        String classPath = tmpDir.path.toString();
        ProcessBuilder pb = new ProcessBuilder(JAVAC,
                "-d", classPath,
                "-cp", classPath,
                targetDir.resolve(mainClassName + ".java").toString());
        try {
            int r = runProcess(pb, tmpDir.path.resolve(mainClassName + ".javac").toString());
            if (r != 0) {
                throw generationFailure("Can't compile sources, exit code = " + r);
            }
        } catch (IOException | InterruptedException e) {
            throw generationFailure("Can't compile sources", e);
        }
    }

    protected static String[] generatePrerunAction(String mainClassName) {
        return new String[] {"@compile " + mainClassName + ".java"};
    }

    public static void main(String[] args) throws Exception {
        try {
            ProductionParams.initializeFromCmdline(args);
            IRTreeGenerator.initializeWithProductionParams();

            JavaCodeGenerator generator = new JavaCodeGenerator();

            for (String mainClass : ProductionParams.mainClassNames.value()) {
                var test = IRTreeGenerator.generateIRTree(mainClass);
                generator.generateSources(test.seed(), test.mainClass(), test.privateClasses());
            }
        } finally {
            GenerationWorkStats.printSummary();
            Genome.close();
        }
    }
}
