/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib.jittester.jtreg;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.stream.Stream;
import java.util.function.Predicate;
import java.nio.file.NoSuchFileException;

import jdk.test.lib.Utils;
import jdk.test.lib.process.ProcessTools;

import jdk.test.lib.jittester.ErrorTolerance;
import jdk.test.lib.jittester.ExecutionResult;
import jdk.test.lib.jittester.Phase;
import jdk.test.lib.jittester.ProcessRunner;
import jtreg.SkippedException;
import static jdk.test.lib.jittester.ProcessRunner.Option.*;


public class JitTesterDriver {
    private final String[] args;

    private final String testName;
    private final Path testDir = Paths.get(Utils.TEST_SRC);

    public JitTesterDriver(String[] args) {
        this.args = args;
        testName = args[args.length - 1];
    }

    private ExecutionResult extractFingerprint(Path path, Phase phase) throws IOException {
        return new ExecutionResult.Builder()
            .exitValue(streamFile(path, testName, phase, "exit").findFirst().get())
            .stdErr(streamFile(path, testName, phase, "err"))
            .stdOut(streamFile(path, testName, phase, "out"))
            .stdOutSize(path.resolve(testName + "." + phase.suffix + ".out").toFile().length())
            .build();
    }

    private ExecutionResult extractOrCreateGoldFingerprint(Path path)
            throws IOException, InterruptedException {
        try {
            return extractFingerprint(path, Phase.GOLD_RUN);
        } catch (NoSuchFileException exception) {
            System.out.println("[DBG] Could not load golden fingerprint" +
                    " (" + exception.getMessage() + "), trying to create one");
        }

        // Perform the golden run
        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder("-XX:-PrintWarnings", "-Xint", "-Dstdout.encoding=UTF-8", testName);
        ExecutionResult runResult = ProcessRunner.runProcessExt(pb, testName, Phase.GOLD_RUN,
                DESTROY_FORCIBLY, GATHER_INFO);
        Duration elapsed = runResult.elapsed();
        System.out.printf("[DBG]: Golden run took %d:%02d:%02d%n",
                elapsed.toHoursPart(), elapsed.toMinutesPart(), elapsed.toSecondsPart());

        // Move the golden files to the source path
        System.out.printf("[DBG]: Moving the golden files to the test source directory");
        try (Stream<Path> walkStream = Files.walk(Path.of("."), 1)) {
            Predicate<Path> filter = p -> p.toFile().isFile()
                                     && p.getFileName().toString().contains(testName + ".gold.");
            walkStream.filter(filter).forEach(goldFile -> {
                try {
                    Files.move(goldFile, path.resolve(goldFile.getFileName()));
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                    throw new RuntimeException("Failed to access " + goldFile);
                }
            });
        }

        return extractFingerprint(path, Phase.GOLD_RUN);
    }

    public void performTheRun() {
        try {
            ExecutionResult goldFingerprint = extractOrCreateGoldFingerprint(testDir);
            ErrorTolerance.assertGoldIsReliable(goldFingerprint);

            ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(args);
            ExecutionResult runResult = ProcessRunner.runProcessExt(pb, testName, Phase.RUN,
                    DESTROY_FORCIBLY, GATHER_INFO);
            Duration elapsed = runResult.elapsed();
            System.out.printf("[DBG]: Verification run took %d:%02d:%02d%n",
                    elapsed.toHoursPart(), elapsed.toMinutesPart(), elapsed.toSecondsPart());

            // Verification
            var runFingerprint = new ExecutionResult.Builder()
                .exitValue(runResult.exitValue())
                .stdOut(streamFile(Path.of("."), testName, Phase.RUN, "out"))
                .stdErr(streamFile(Path.of("."), testName, Phase.RUN, "err"))
                .build();

            new ErrorTolerance(goldFingerprint, runFingerprint).assertIsTolerable();
        } catch (SkippedException toRethrow) {
            throw toRethrow;    // Not an error, just forward it to JTReg
        } catch (Exception e) {
            throw new Error("Unexpected exception on test jvm start :" + e, e);
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            throw new IllegalArgumentException(
                    "[TESTBUG]: wrong number of argument : " + args.length
                    + ". Expected at least 1 argument -- jit-tester test name.");
        }

        new JitTesterDriver(args).performTheRun();
    }

    private static Stream<String> streamFile(Path dir, String name, Phase phase, String kind) throws IOException {
        String fullName = name + "." + phase.suffix + "." + kind;
        return Files.lines(dir.resolve(fullName), Charset.forName("UTF-8"));
    }

}
