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
import java.util.HashMap;
import jdk.test.lib.jittester.utils.Genome;

public class FlatFieldConfigurationTests {
    public static void main(String[] args) throws Exception {
        Path work = Files.createTempDirectory("jittester-flat-field-config-");
        try {
            reject(() -> initialize("--disable-functions=true",
                    "--morph-template-inline-type-flat-field-probability=1"));
            initialize("--disable-functions=true", "--morph-template-inline-type-flat-field-probability=0");
            initialize("--disable-functions=false", "--morph-template-inline-type-flat-field-probability=100");

            Path profile = work.resolve("invalid.profile");
            Files.writeString(profile, "--disable-functions=true\n"
                    + "--morph-template-inline-type-flat-field-probability=35\n");
            reject(() -> initialize("--profile", profile.toString()));
            initialize("--profile", profile.toString(),
                    "--morph-template-inline-type-flat-field-probability=0");

            testReplayAndRestoration(work);
            testMutationOverrides();
            System.out.println("FlatFieldConfigurationTests passed");
        } finally {
            Genome.close();
        }
    }

    private static void testReplayAndRestoration(Path work) {
        String genome = work.resolve("valid").toString();
        initialize("--genome-record", genome, "--disable-functions=false",
                "--morph-template-inline-type-flat-field-probability=100");
        // Caller generation parameters are discarded on replay, so validate the restored ones.
        initialize("--genome-replay", genome, "--disable-functions=true");
        require(!ProductionParams.disableFunctions.value(), "Replay did not restore enabled functions");
        var before = ProductionParams.generationParameters();
        var overrides = new HashMap<>(before.overrides());
        overrides.put("disable-functions", "true");
        var invalid = new ProductionParams.GenerationParameters(before.defaultsFingerprint(), overrides);
        reject(() -> ProductionParams.restoreGenerationParameters(invalid));
        require(before.equals(ProductionParams.generationParameters()),
                "Rejected parameter restoration changed the current configuration");
    }

    private static void testMutationOverrides() {
        initialize("--morph-template-inline-type-flat-field-probability=100",
                "--override", "disable-functions", "true");
        var before = ProductionParams.generationParameters();
        reject(() -> ProductionParams.beginMutationOverrideScope());
        require(before.equals(ProductionParams.generationParameters()),
                "Rejected mutation override leaked parameters");
        initialize("--disable-functions=true",
                "--override", "morph-template-inline-type-flat-field-probability", "100");
        reject(() -> ProductionParams.beginMutationOverrideScope());

        initialize("--morph-template-inline-type-flat-field-probability=100",
                "--override", "disable-functions", "true",
                "--override", "morph-template-inline-type-flat-field-probability", "0");
        var saved = ProductionParams.beginMutationOverrideScope();
        require(ProductionParams.disableFunctions.value(), "Valid paired overrides were rejected");
        ProductionParams.endMutationOverrideScope(saved);
        require(!ProductionParams.disableFunctions.value(), "Valid override scope did not restore parameters");
    }

    private static void initialize(String... args) {
        try {
            ProductionParams.initializeFromCmdline(args);
        } finally {
            Genome.close();
        }
    }

    private static void reject(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Accepted MTFF with disabled functions");
        } catch (IllegalArgumentException expected) {
            String message = expected.getMessage();
            require(message.contains("--disable-functions")
                            && message.contains("--morph-template-inline-type-flat-field-probability"),
                    "Unclear configuration error: " + message);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
