/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import jdk.test.lib.Asserts;

import jtreg.SkippedException;

/**
  * Compares reference output to the one that's being verified.
  *
  * The class can tolerate some errors (like OutOfMemoryError),
  * as their nature is unpredictable and ignore missing intrinsics in
  * compiled code stack traces.
  */
public class ErrorTolerance {

    // Put the most annoying intrinsics here
    private static final List<Predicate<String>> PATTERNS = List.of(
        Pattern.compile(".*at java.base.*misc.Unsafe.allocateUninitializedArray0.*").asPredicate()
    );

    private static final Predicate<String> OOME = Pattern.compile(
            "Exception in thread \".*\" java.lang.OutOfMemoryError.*").asPredicate();
    private static final Predicate<String> SOE = Pattern.compile(
            ".*StackOver[fF]lowError.*").asPredicate();
    private static final Predicate<String> patternSyntaxException = Pattern.compile(
            ".*java.util.regex.PatternSyntaxException.*").asPredicate();
    private static final Predicate<String> compileCommandPattern = Pattern.compile(
            "^CompileCommand:.*").asPredicate();

    private static boolean isIntrinsicCandidate(String line) {
        return PATTERNS.stream()
                       .map(pattern -> pattern.test(line))
                       .reduce(false, (acc, match) -> acc | match);
    }

    private static boolean shouldSkipRunLine(String line) {
        return compileCommandPattern.test(line);
    }

    private Optional<String> foundError = Optional.empty();

    private final ExecutionResult gold;
    private final ExecutionResult run;

    private boolean soeFoundInRun = false;
    private boolean oomeFoundInRun = false;
    private boolean patternSyntaxExceptionFoundInGold = false;

    public ErrorTolerance(ExecutionResult gold, ExecutionResult run) {
        this.gold = gold;
        this.run = run;
    }

    private void checkExitValue() {
        if (gold.exitValue().equals("TIMEOUT")) {
            throw new SkippedException("GOLD finished with timeout, ignoring");
        }

        Asserts.assertEquals(gold.exitValue(), run.exitValue(), "Exit codes mismatch: ");
    }

    public void assertIsTolerable() {
        checkExitValue();

        checkStream("STDOUT", gold.stdOut(), run.stdOut());
        checkStream("STDERR", gold.stdErr(), run.stdErr());

        foundError.ifPresent(Asserts::fail);
    }

    public void checkForPatternExceptionSOECombination(String goldLine, String runLine) {
        soeFoundInRun |= SOE.test(runLine);
        oomeFoundInRun |= OOME.test(runLine);
        patternSyntaxExceptionFoundInGold |= patternSyntaxException.test(goldLine);
        if (patternSyntaxExceptionFoundInGold && (soeFoundInRun || oomeFoundInRun)) {
            throw new SkippedException("PatternSyntaxException in GOLD was most probably " +
                    "caused by OOME or SOE, noticed in RUN as well");
        }
    }

    public static void assertGoldIsReliable(ExecutionResult gold) {
        if (gold.exitValue().equals("TIMEOUT")) {
            throw new jtreg.SkippedException("Gold run timed out. Ignoring the run");
        }

        // 4M is an arbitrary value, feel free to change
        if (gold.stdOutSize() > 4_000_000L) {
            throw new jtreg.SkippedException("Gold stdout exceeds 4M - such results are ignored");
        }
    }

    public void checkStream(String streamName, Stream<String> gold, Stream<String> run) {
            Iterator<String> goldIt = gold.iterator();
            Iterator<String> runIt = run.iterator();

            while (goldIt.hasNext() && runIt.hasNext()) {
                String goldLine = goldIt.next();
                String runLine = runIt.next();

                // Skipping debug messages in 'run'
                while (shouldSkipRunLine(runLine) &&
                       runIt.hasNext()) {
                    runLine = runIt.next();
                }

                // Skipping intrinsics in 'gold'
                while (isIntrinsicCandidate(goldLine) &&
                       !runLine.equals(goldLine) &&
                       goldIt.hasNext()) {
                    goldLine = goldIt.next();
                }

                // Results with stress failures in GOLD are ignored.
                if (OOME.test(goldLine) || SOE.test(goldLine)) {
                    throw new SkippedException("GOLD output contains OutOfMemoryError or StackOverflowError");
                }

                checkForPatternExceptionSOECombination(goldLine, runLine);

                if (foundError.isEmpty() && !goldLine.equals(runLine)) {
                    foundError = Optional
                        .of("Difference in '" + streamName + "' stream. \n" +
                            "Expected: " + goldLine + "\n" +
                            "Actual  : " + runLine + "\n");
                }
            }

            if (foundError.isEmpty() && goldIt.hasNext() != runIt.hasNext()) {
                foundError = Optional.of("Stream '" + streamName + "' shows up " +
                                         "different length for GOLD and RUN executions");
            }
    }

}
