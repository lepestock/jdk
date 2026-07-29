/*
 * Copyright (c) 2016, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import jdk.test.lib.jittester.types.TypeKlass;

public abstract class TestsGenerator implements Consumer<IRTreeGenerator.Test> {
    private static final int DEFAULT_JTREG_TIMEOUT = 120;
    protected static final String JAVA_BIN = getJavaPath();
    protected static final String JAVAC = Paths.get(JAVA_BIN, "javac").toString();
    protected static final String JAVA = Paths.get(JAVA_BIN, "java").toString();
    protected final Path generatorDir;
    protected final TempDir tmpDir;
    protected final Function<String, String[]> preRunActions;
    protected final String jtDriverOptions;
    private static final String DISABLE_WARNINGS = "-XX:-PrintWarnings";

    protected TestsGenerator(String suffix) {
        this(suffix, s -> new String[0], "");
    }

    protected TestsGenerator(String suffix, Function<String, String[]> preRunActions,
            String jtDriverOptions) {
        generatorDir = getRoot().resolve(suffix).toAbsolutePath();
        tmpDir = new TempDir(suffix);
        this.preRunActions = preRunActions;
        this.jtDriverOptions = jtDriverOptions;
    }

    protected void generateGoldenOut(String mainClassName) {
        Path targetDir = getGeneratorDir(mainClassName);
        String classPath = tmpDir.path.toString() + File.pathSeparator
                + targetDir.toString();
        ProcessBuilder pb = new ProcessBuilder(JAVA, "-Xint", DISABLE_WARNINGS, "-Xverify",
                "-cp", classPath, mainClassName);
        String goldFile = mainClassName + ".gold";
        try {
            runProcess(pb, targetDir.resolve(goldFile).toString());
        } catch (IOException | InterruptedException e)  {
            throw generationFailure("Can't run generated test", e);
        }
    }

    protected static GenerationFailureException generationFailure(String message) {
        return new GenerationFailureException(message);
    }

    protected static GenerationFailureException generationFailure(String message, Throwable cause) {
        return new GenerationFailureException(message, cause);
    }

    protected static int runProcess(ProcessBuilder pb, String name)
            throws IOException, InterruptedException {
        pb.redirectError(new File(name + ".err"));
        pb.redirectOutput(new File(name + ".out"));
        Process process = pb.start();
        try {
            if (process.waitFor(DEFAULT_JTREG_TIMEOUT, TimeUnit.SECONDS)) {
                try (FileWriter file = new FileWriter(name + ".exit")) {
                    file.write(Integer.toString(process.exitValue()));
                }
                return process.exitValue();
            }
        } finally {
            process.destroyForcibly();
        }
        return -1;
    }

    protected void compilePrinter() {
        if (ProductionParams.embedPrinterClass.value()) {
            return;
        }
        Path root = getRoot();
        ProcessBuilder pbPrinter = new ProcessBuilder(JAVAC,
                "-d", tmpDir.path.toString(),
                resolvePrinterSourcePath(root).toString());
        try {
            int exitCode = runProcess(pbPrinter, root.resolve("Printer").toString());
            if (exitCode != 0) {
                throw generationFailure("Printer compilation returned exit code " + exitCode);
            }
        } catch (IOException | InterruptedException e) {
            throw generationFailure("Can't compile printer", e);
        }
    }

    protected void compilePulse() {
        if (!ProductionParams.pulsemap.value()) {
            return;
        }
        Path root = getRoot();
        ProcessBuilder pbPulse = new ProcessBuilder(JAVAC,
                "-d", tmpDir.path.toString(),
                resolvePulseSourcePath(root).toString());
        try {
            int exitCode = runProcess(pbPulse, root.resolve("Pulse").toString());
            if (exitCode != 0) {
                throw generationFailure("Pulse compilation returned exit code " + exitCode);
            }
        } catch (IOException | InterruptedException e) {
            throw generationFailure("Can't compile pulse", e);
        }
    }

    protected static void ensureExisting(Path path) {
        if (Files.notExists(path)) {
            try {
                Files.createDirectories(path);
            } catch (IOException ex) {
                throw generationFailure("Can't create directory " + path, ex);
            }
        }
    }

    protected String getJtregHeader(String mainClassName, long seed) {
        String synopsis = "seed = '" + seed + "'";
        StringBuilder header = new StringBuilder();
        header.append("/*\n * @test\n * @summary ")
              .append(synopsis)
              .append(" \n * @library / ../\n");
        header.append(" * @run build jdk.test.lib.jittester.jtreg.JitTesterDriver");
        if (!ProductionParams.embedPrinterClass.value()) {
            header.append(" jdk.test.lib.jittester.jtreg.Printer");
        }
        if (ProductionParams.pulsemap.value()) {
            header.append(" jdk.test.lib.jittester.pulse.Pulse");
        }
        header.append("\n");
        for (String action : preRunActions.apply(mainClassName)) {
            header.append(" * ")
                  .append(action)
                  .append("\n");
        }
        header.append(" * @run driver jdk.test.lib.jittester.jtreg.JitTesterDriver ")
              .append(DISABLE_WARNINGS)
              .append(" ")
              .append(jtDriverOptions)
              .append(" ")
              .append(mainClassName)
              .append("\n */\n\n");
        if (ProductionParams.printHierarchy.value()) {
            header.append("/*\n")
                  .append(printHierarchy())
                  .append("*/\n");
        }
        return header.toString();
    }

    protected String loadEmbeddedPrinterSource() {
        Path printerPath = resolvePrinterSourcePath(getRoot());
        try {
            String source = Files.readString(printerPath, StandardCharsets.UTF_8);
            source = source.replaceFirst("(?m)^\\s*package\\s+[^;]+;\\s*$", "");
            source = source.replaceFirst("(?m)^\\s*public\\s+class\\s+Printer\\b", "class Printer");
            return source.trim() + "\n";
        } catch (IOException e) {
            throw generationFailure("Can't load embedded printer source: " + printerPath, e);
        }
    }

    protected static Path getRoot() {
        return Paths.get(ProductionParams.testbaseDir.value());
    }

    private static Path resolvePrinterSourcePath(Path root) {
        Path fromTestbase = root.resolve("jdk/test/lib/jittester/jtreg/Printer.java");
        if (Files.exists(fromTestbase)) {
            return fromTestbase;
        }
        Path fromCwd = Paths.get("src/jdk/test/lib/jittester/jtreg/Printer.java");
        if (Files.exists(fromCwd)) {
            return fromCwd;
        }
        try {
            Path classesDir = Paths.get(TestsGenerator.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            Path fromBuild = classesDir
                    .resolve("../../../src/jdk/test/lib/jittester/jtreg/Printer.java")
                    .normalize();
            if (Files.exists(fromBuild)) {
                return fromBuild;
            }
        } catch (URISyntaxException ignored) {
            // Fall through to final deterministic path.
        }
        return fromTestbase;
    }

    private static Path resolvePulseSourcePath(Path root) {
        Path fromTestbase = root.resolve("jdk/test/lib/jittester/pulse/Pulse.java");
        if (Files.exists(fromTestbase)) {
            return fromTestbase;
        }
        Path fromCwd = Paths.get("src/jdk/test/lib/jittester/pulse/Pulse.java");
        if (Files.exists(fromCwd)) {
            return fromCwd;
        }
        try {
            Path classesDir = Paths.get(TestsGenerator.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            Path fromBuild = classesDir
                    .resolve("../../../src/jdk/test/lib/jittester/pulse/Pulse.java")
                    .normalize();
            if (Files.exists(fromBuild)) {
                return fromBuild;
            }
        } catch (URISyntaxException ignored) {
            // Fall through to final deterministic path.
        }
        return fromTestbase;
    }

    protected Path getGeneratorDir(String mainClassName) {
        if (ProductionParams.individualSandboxes.value()) {
            Path targetDir = generatorDir.resolve(mainClassName);
            ensureExisting(targetDir);
            return targetDir;
        }
        ensureExisting(generatorDir);
        return generatorDir;
    }

    protected static void writeFile(Path targetDir, String fileName, String content) {
        try (FileWriter file = new FileWriter(targetDir.resolve(fileName).toFile())) {
            file.write(content);
        } catch (IOException e) {
            throw generationFailure("Can't write generated file: " + targetDir.resolve(fileName), e);
        }
    }

    private static String printHierarchy() {
        return TypeList.getAll()
                .stream()
                .filter(t -> t instanceof TypeKlass)
                .map(t -> typeDescription((TypeKlass) t))
                .collect(Collectors.joining("\n","CLASS HIERARCHY:\n", "\n"));
    }

    private static String typeDescription(TypeKlass type) {
        StringBuilder result = new StringBuilder();
        String parents = type.getParentsNames().stream().collect(Collectors.joining(","));
        result.append(type.isAbstract() ? "abstract " : "")
              .append(type.isFinal() ? "final " : "")
              .append(type.isValueKlass() ? "value " : "")
              .append(type.isInterface() ? "interface " : "class ")
              .append(type.getName())
              .append(parents.isEmpty() ? "" : ": " + parents);
        return result.toString();
    }

    private static String getJavaPath() {
        String[] env = { "JDK_HOME", "JAVA_HOME", "BOOTDIR" };
        for (String name : env) {
            String path = System.getenv(name);
            if (path != null) {
                return Paths.get(path)
                            .resolve("bin")
                            .toAbsolutePath()
                            .toString();
            }
        }
        return "";
    }
}
