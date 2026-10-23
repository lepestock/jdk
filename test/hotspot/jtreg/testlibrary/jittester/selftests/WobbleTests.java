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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import jdk.test.lib.jittester.utils.Genome;
import jdk.test.lib.jittester.utils.OptionResolver;
import jdk.test.lib.jittester.utils.PseudoRandom;
import jdk.test.lib.jittester.utils.Wobble;

public class WobbleTests {
    private static final String RESERVE = "morph-template-gentest-scope-reserve-size";
    private static final String LEG_WEIGHT = "morph-template-leg-weight";
    private static final String USE_LEGS = "morph-template-inline-type-flat-field-use-leg-limit";
    private static final String SELECTION_WEIGHT = "morph-template-inline-type-flat-field-leg-selection-weight";

    public static void main(String[] args) throws Exception {
        Path work = Files.createTempDirectory("jittester-wobble-");
        try {
            testParser(work);
            testDistributions();
            testProfilesAndStableMode(work);
            testReplayAndMultipleGentests(work);
            System.out.println("WobbleTests passed: " + work);
        } finally {
            Genome.close();
        }
    }

    private static void testParser(Path work) throws Exception {
        OptionResolver parser = new OptionResolver();
        var weight = parser.addWobblyIntegerOption("weight", 100, 0, "weight");
        var path = parser.addStringOption("path", "", "path");
        parser.addBooleanOption("flag", "flag");
        parser.addIntegerOption("fixed", 10, "fixed");
        var properties = parser.addStringOption("property-file", "", "properties");
        parser.parse(new String[] {"--weight=100:wobbly", "--path=C:/tmp/a:b"});
        check(weight.value() == 100 && path.value().equals("C:/tmp/a:b"), "Parsing changed baseline/path");
        check(parser.wobbleSpecifications().get("weight").equals("100:wobbly"), "Missing specification");
        parser.resolveWobbles(9876);
        int first = weight.value();
        parser.resolveWobbles(9876);
        check(weight.value() == first, "Sampling compounded a previous effective value");
        parser.parse(new String[] {"--weight=100:wobbly", "--weight", "80:stable"});
        check(weight.value() == 80 && parser.wobbleSpecifications().isEmpty(), "Stable override did not win");
        parser.parse(new String[] {"--weight=100:wobbly", "--weight=75"});
        check(weight.value() == 75 && parser.wobbleSpecifications().isEmpty(), "Numeric override did not win");

        Path file = work.resolve("options.properties");
        Files.writeString(file, "weight=25:very_wobbly\n");
        parser.parse(new String[] {"--property-file", file.toString()}, properties);
        check(parser.wobbleSpecifications().get("weight").equals("25:very_wobbly"), "Property specification lost");
        parser.parse(new String[] {"--property-file", file.toString(), "--weight=80:stable"}, properties);
        check(weight.value() == 80 && parser.wobbleSpecifications().isEmpty(), "Lower-priority property leaked");
        for (String invalid : List.of("--weight=10:unknown", "--weight=10:", "--weight=10:wobbly:extra",
                "--weight=-1:wobbly", "--fixed=10:wobbly", "--flag=false:wobbly")) {
            reject(() -> parser.parse(new String[] {invalid}), invalid);
        }
    }

    private static void testDistributions() {
        double previousVariance = -1;
        for (Wobble.Level level : Wobble.Level.values()) {
            var spec = new Wobble.Specification(10, level);
            double total = 0;
            double squares = 0;
            int low = Integer.MAX_VALUE;
            int high = 0;
            int count = 100_000;
            for (int i = 0; i < count; i++) {
                int value = spec.sample(i, RESERVE, 0);
                check(value >= 0, "Negative count");
                total += value;
                squares += (double) value * value;
                low = Math.min(low, value);
                high = Math.max(high, value);
            }
            double mean = total / count;
            double variance = squares / count - mean * mean;
            check(Math.abs(mean - 10) < 0.4, "Mean drift for " + level + ": " + mean);
            check(variance > previousVariance, "Wobbliness classes did not increase variance");
            if (level == Wobble.Level.STABLE) {
                check(low == 10 && high == 10, "Stable sampling varied");
            } else {
                check(low < 5 && high > 20, "Missing broad excursions for " + level);
            }
            previousVariance = variance;
            System.out.printf("%s mean=%.4f variance=%.4f min=%d max=%d%n", level, mean, variance, low, high);
        }
        int different = 0;
        for (int i = 0; i < 1000; i++) {
            var spec = new Wobble.Specification(5, Wobble.Level.WOBBLY);
            check(spec.sample(i, USE_LEGS, 1) >= 1, "Use-leg count below one");
            check(new Wobble.Specification(0, Wobble.Level.VERY_WOBBLY).sample(i, RESERVE, 0) == 0,
                    "Disabled setting was enabled");
            check(new Wobble.Specification(1, Wobble.Level.VERY_WOBBLY).sample(i, USE_LEGS, 1) == 1,
                    "Minimum count drifted");
            check(new Wobble.Specification(Integer.MAX_VALUE, Wobble.Level.VERY_WOBBLY)
                    .sample(i, RESERVE, 0) >= 0, "Integer overflow");
            if (spec.sample(i, RESERVE, 0) != spec.sample(i, LEG_WEIGHT, 0)) {
                different++;
            }
        }
        check(different > 100, "Named parameter streams appear correlated");
    }

    private static void testProfilesAndStableMode(Path work) throws Exception {
        Path inner = work.resolve("inner.profile");
        Path outer = work.resolve("outer.profile");
        Files.writeString(inner, "--" + RESERVE + "=10:wobbly\n");
        Files.writeString(outer, "--profile " + inner + "\n--" + RESERVE + "=20:slightly_wobbly\n");
        ProductionParams.initializeFromCmdline(new String[] {"--profile", outer.toString(), "--seed=345"});
        check(ProductionParams.generationParameters().wobbleSpecifications().get(RESERVE)
                .equals("20:slightly_wobbly"), "Nested profile precedence changed");
        long seed = PseudoRandom.getCurrentSeed();
        ProductionParams.resolveWobblyParameters(seed);
        check(PseudoRandom.getCurrentSeed() == seed, "Wobble consumed structural RNG");
        ProductionParams.initializeFromCmdline(new String[] {"--profile", outer.toString(),
                "--" + RESERVE + "=30:stable", "--seed=345"});
        var stable = ProductionParams.generationParameters();
        check(stable.wobbleSpecifications().isEmpty(), "Stable suffix retained wobble");
        check(ProductionParams.effectiveWobblyParameters().isEmpty(), "Stable override retained wobbly values");
        ProductionParams.initializeFromCmdline(new String[] {"--" + RESERVE + "=30", "--seed=345"});
        check(stable.equals(ProductionParams.generationParameters()), "Stable suffix differs from bare value");
        check(PseudoRandom.getCurrentSeed() == seed, "Stable mode changed seed");
        reject(() -> ProductionParams.initializeFromCmdline(new String[] {
                "--override", RESERVE, "10:wobbly"}), "wobbly mutation override");
        reject(() -> ProductionParams.initializeFromCmdline(new String[] {
                "--override", "arrays-disable", "false:wobbly"}), "boolean wobbly mutation override");
    }

    private static void testReplayAndMultipleGentests(Path work) throws Exception {
        String founder = work.resolve("founder").toString();
        ProductionParams.initializeFromCmdline(new String[] {"--seed=345", "--genome-record", founder,
                "--" + RESERVE + "=10:wobbly", "--" + LEG_WEIGHT + "=100:wobbly",
                "--" + USE_LEGS + "=5:wobbly", "--" + SELECTION_WEIGHT + "=200:wobbly"});
        List<Map<String, String>> values = exerciseGentests(null);
        Genome.close();
        check(values.stream().distinct().count() > 1, "Multiple gentests reused one sample");
        for (int replay = 0; replay < 2; replay++) {
            String input = replay == 0 ? founder : work.resolve("replay0").toString();
            String output = work.resolve("replay" + replay).toString();
            ProductionParams.initializeFromCmdline(new String[] {"--genome-replay", input,
                    "--genome-record", output, "--" + RESERVE + "=999:very_wobbly"});
            check(values.equals(exerciseGentests(values)), "Replay resampled effective parameters");
            Genome.close();
            for (String suffix : List.of(".genome", ".blocks")) {
                check(Files.mismatch(Path.of(input + suffix), Path.of(output + suffix)) == -1,
                        "Replay changed " + suffix);
            }
        }
        String source = Files.readString(Path.of(founder + ".genome"));
        testCommentedMetadata(work, founder, source, values);
        Path invalid = work.resolve("invalid.genome");
        Files.writeString(invalid, source.replace("full-2.1", "full-2.0"));
        reject(() -> ProductionParams.initializeFromCmdline(new String[] {"--genome-replay", invalid.toString()}),
                "wobble metadata under old version");
        String wobbleLine = source.lines().filter(line -> line.startsWith("(production_wobble ")).findFirst().orElseThrow();
        Files.writeString(invalid, source + wobbleLine + "\n");
        reject(() -> ProductionParams.initializeFromCmdline(new String[] {"--genome-replay", invalid.toString()}),
                "duplicate wobble metadata");
        Files.writeString(invalid, source.replace("production_gentest_parameter 1 ", "production_gentest_parameter 0 "));
        reject(() -> ProductionParams.initializeFromCmdline(new String[] {"--genome-replay", invalid.toString()}),
                "invalid gentest index");
        Files.writeString(invalid, source.replace(RESERVE + " \"10:wobbly\"", RESERVE + " \"10:unknown\""));
        reject(() -> ProductionParams.initializeFromCmdline(new String[] {"--genome-replay", invalid.toString()}),
                "unknown metadata policy");
        ProductionParams.initializeFromCmdline(new String[] {"--seed=345"});
        check(ProductionParams.effectiveWobblyParameters().isEmpty(), "Previous invocation leaked specs");

        String stable = work.resolve("stable").toString();
        ProductionParams.initializeFromCmdline(new String[] {"--seed=345", "--genome-record", stable});
        Genome.close();
        Path legacy = work.resolve("legacy.genome");
        Files.writeString(legacy, Files.readString(Path.of(stable + ".genome")).replace("full-2.1", "full-2.0"));
        ProductionParams.initializeFromCmdline(new String[] {"--genome-replay", legacy.toString()});
        check(ProductionParams.effectiveWobblyParameters().isEmpty(), "Legacy genome gained wobbles");
    }

    private static void testCommentedMetadata(Path work, String founder, String source,
            List<Map<String, String>> expected) throws Exception {
        int index = 0;
        for (String entry : List.of("production_wobble", "production_gentest_parameter")) {
            for (String comment : List.of("#comment", " \t# comment with \"quotes\" and (B123)", " #")) {
                String prefix = "(" + entry + " ";
                check(source.lines().anyMatch(line -> line.startsWith(prefix)), "Missing fixture: " + entry);
                String annotated = String.join("\n", source.lines()
                        .map(line -> line.startsWith(prefix) ? line + comment : line).toList()) + "\n";
                Path input = work.resolve("commented-" + index + ".genome");
                String output = work.resolve("commented-replay-" + index++).toString();
                Files.writeString(input, annotated);
                ProductionParams.initializeFromCmdline(new String[] {
                        "--genome-replay", input.toString(), "--genome-record", output});
                check(expected.equals(exerciseGentests(expected)), "Comments changed effective parameters");
                Genome.close();
                for (String suffix : List.of(".genome", ".blocks")) {
                    check(Files.mismatch(Path.of(founder + suffix), Path.of(output + suffix)) == -1,
                            "Comments changed replayed " + suffix + " for " + entry);
                }
            }
        }
    }

    private static List<Map<String, String>> exerciseGentests(List<Map<String, String>> expected) {
        List<Map<String, String>> values = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            PseudoRandom.setCurrentSeed(1000 + i);
            long before = PseudoRandom.getCurrentSeed();
            Genome.beginGentest();
            check(PseudoRandom.getCurrentSeed() == before, "Gentest policy consumed structural RNG");
            Map<String, String> effective = ProductionParams.effectiveWobblyParameters();
            values.add(effective);
            if (expected != null) {
                check(effective.equals(expected.get(i)), "Wrong gentest snapshot");
            }
            check(GenerationState.currentFlowParams().mtLegWeight()
                    == Integer.parseInt(effective.get(LEG_WEIGHT)) / 100.0, "Stale/clamped FlowParams weight");
            long seed = Genome.startScope('B', 500 + i);
            Genome.recordCurrentScopeGene('B', seed);
            Genome.endBlock();
        }
        return values;
    }

    private static void reject(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException e) {
            return;
        }
        throw new AssertionError("Accepted " + message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
