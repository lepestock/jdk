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

import java.nio.file.Files;
import java.nio.file.Path;
import jdk.test.lib.jittester.utils.Genome;

public class GenomeParametersTests {
    private static Path work;

    public static void main(String[] args) throws Exception {
        work = Files.createTempDirectory("jittester-genome-parameters-");
        try {
            testReplayWithoutProfile();
            testReplayConfigurationIsolation();
            testPropertyFileAndEscaping();
            testHeaderValidation();
            System.out.println("GenomeParametersTests passed: " + work);
        } finally {
            Genome.close();
        }
    }

    private static void testReplayWithoutProfile() throws Exception {
        Path included = work.resolve("included.profile");
        Path profile = work.resolve("founder.profile");
        Files.writeString(included, "--statement-limit=5\n--arrays-disable=false\n");
        Files.writeString(profile, "--profile " + included + "\n--statement-limit=7\n"
                + "--type-declaration-attempts-mean=2.5\n");
        String founder = work.resolve("founder").toString();
        ProductionParams.initializeFromCmdline(new String[] {
                "--profile", profile.toString(), "--statement-limit=9",
                "--operator-limit=50", "--genome-record", founder, "--seed=314159",
                "--testbase-dir", work.resolve("old-output").toString(),
                "--debug-morph-source-diagnostics", "--debug-type-labels"});
        Genome.close();

        // Replay must need neither the original profile nor its included files.
        Files.delete(profile);
        Files.delete(included);
        String replay = work.resolve("replay").toString();
        ProductionParams.initializeFromCmdline(new String[] {
                "--genome-replay", founder, "--genome-record", replay,
                "--testbase-dir", work.resolve("new-output").toString()});
        check(ProductionParams.statementLimit.value() == 9,
                "Replay did not restore the explicit generation parameter");
        check(GenerationState.currentFlowParams().statementLimit() == 9,
                "FlowParams was initialized before parameter restoration");
        check(!ProductionParams.disableArrays.value(), "Replay did not restore false");
        check(ProductionParams.typeDeclarationAttemptsMean.value() == 2.5,
                "Replay did not restore a double parameter");
        check(ProductionParams.operatorLimit.isSet(),
                "An explicitly supplied default value must remain recorded");
        check(!ProductionParams.memberFunctionsLimit.isSet(),
                "An untouched default must remain implicit");
        check(!ProductionParams.debugMorphSourceDiagnostics.value(),
                "Diagnostic settings must not be restored");
        check(!ProductionParams.debugTypeLabels.value(), "Type-label diagnostics must not be restored");
        check(ProductionParams.testbaseDir.value().endsWith("new-output"),
                "The caller's output path must remain usable");
        Genome.close();
        check(Files.mismatch(Path.of(founder + ".genome"), Path.of(replay + ".genome")) == -1,
                "Replay changed the genome header");
        check(Files.mismatch(Path.of(founder + ".blocks"), Path.of(replay + ".blocks")) == -1,
                "Replay changed the block-only header");
        String header = Files.readString(Path.of(founder + ".genome"));
        check(header.contains("(genocode_version full-2.0)"), "Missing major version bump");
        check(!header.contains("testbase-dir") && !header.contains("debug-morph-source-diagnostics")
                && !header.contains("debug-type-labels") && !header.contains("member-functions-limit"),
                "Header is not a sparse policy snapshot");
    }

    private static void testReplayConfigurationIsolation() throws Exception {
        ProductionParams.initializeFromCmdline(new String[] {
                "--genome-replay", work.resolve("founder").toString(),
                "--statement-limit=100", "--member-functions-limit=99", "--arrays-disable",
                "--runtime-output-mode=pulse", "--debug-morph-source-diagnostics",
                "--debug-type-labels",
                "--override", "statement-limit", "77"});
        check(ProductionParams.statementLimit.value() == 9, "Caller changed a recorded PP");
        check(ProductionParams.memberFunctionsLimit.value() == ProductionParams.memberFunctionsLimit.getDefaultValue(),
                "Caller changed an implicit genome default");
        check(!ProductionParams.memberFunctionsLimit.isSet(), "Caller explicitness leaked into replay");
        check(!ProductionParams.disableArrays.value(), "Caller changed a recorded boolean");
        check(ProductionParams.pulsemapEnabled(), "Caller instrumentation was overwritten");
        check(ProductionParams.debugMorphSourceDiagnostics.value(), "Caller diagnostics were overwritten");
        check(ProductionParams.debugTypeLabels.value(), "Caller type-label diagnostics were overwritten");
        var before = ProductionParams.generationParameters();
        var backup = ProductionParams.beginMutationOverrideScope();
        check(ProductionParams.statementLimit.value() == 77, "Scoped override was lost");
        ProductionParams.endMutationOverrideScope(backup);
        check(before.equals(ProductionParams.generationParameters()), "Scoped override leaked");
        Genome.close();

        ProductionParams.initializeFromCmdline(new String[] {"--genome-record", work.resolve("defaults").toString()});
        check(ProductionParams.generationParameters().overrides().isEmpty(), "Previous run's PPs leaked");
        Genome.close();
    }

    private static void testPropertyFileAndEscaping() throws Exception {
        Path properties = work.resolve("founder.properties");
        Files.writeString(properties, "statement-limit=4\noperator-limit=6\nprint-hierarchy=true\n");
        String special = "java.lang.String # comment-like (B123) \"quoted\" \\\\ \n\t\r\b\f";
        String founder = work.resolve("strings").toString();
        ProductionParams.initializeFromCmdline(new String[] {
                "--property-file", properties.toString(), "--statement-limit=8",
                "--add-external-symbols", special, "--arrays-allowed-types=",
                "--genome-record", founder});
        Genome.close();
        Files.delete(properties);
        ProductionParams.initializeFromCmdline(new String[] {"--genome-replay", founder});
        check(ProductionParams.statementLimit.value() == 8, "CLI precedence changed");
        check(ProductionParams.operatorLimit.value() == 6, "Property-file generation PP was lost");
        check(!ProductionParams.printHierarchy.value(), "Property-file diagnostic was recorded");
        check(special.equals(ProductionParams.addExternalSymbols.value()), "String escaping did not round-trip");
        check(ProductionParams.arraysAllowedTypes.isSet() && ProductionParams.arraysAllowedTypes.value().isEmpty(),
                "Explicit empty string was lost");
        Genome.close();
    }

    private static void testHeaderValidation() throws Exception {
        String header = Files.readString(work.resolve("founder.genome"));
        String defaultsLine = header.lines().filter(line -> line.startsWith("(production_defaults "))
                .findFirst().orElseThrow();
        reject(header.replace(defaultsLine + "\n", ""), "missing production-parameter header");
        reject(header.replace("full-2.0", "full-1.4"), "version mismatch");
        reject(header.replace(defaultsLine, "(production_defaults " + "0".repeat(64) + ")"), "defaults mismatch");
        reject(header + defaultsLine + "\n", "Duplicate");
        reject(header + "(production_parameter statement-limit \"9\")\n", "Duplicate");
        reject(header + "(production_parameter testbase-dir \"/tmp/no\")\n", "Not a genome generation parameter");
        reject(header + "(production_parameter unknown-option \"1\")\n", "Not a genome generation parameter");
        reject(header.replace("arrays-disable \"false\"", "arrays-disable \"tru\""), "Non-canonical");
        reject(header + "(production_parameter chance-throw)\n", "Malformed");
        reject(header + "(B123)\n(production_parameter chance-throw \"1\")\n", "must precede");
    }

    private static void reject(String source, String message) throws Exception {
        Path file = work.resolve("invalid.genome");
        Files.writeString(file, source);
        try {
            ProductionParams.initializeFromCmdline(new String[] {"--genome-replay", file.toString()});
            throw new AssertionError("Accepted invalid genome: " + message);
        } catch (RuntimeException e) {
            check(e.getMessage().contains(message), "Wrong rejection: " + e);
        } finally {
            Genome.close();
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
