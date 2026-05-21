/*
 * Copyright (c) 2005, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jdk.test.lib.jittester.utils.Genome;
import jdk.test.lib.jittester.utils.OptionResolver;
import jdk.test.lib.jittester.utils.OptionResolver.Option;
import jdk.test.lib.jittester.genocode.full.FullGenocode;
import jdk.test.lib.jittester.utils.PseudoRandom;

public class ProductionParams {

    public static Option<List<String>> mainClassNames = null;
    public static Option<Integer> dataMemberLimit = null;
    public static Option<Integer> statementLimit = null;
    public static Option<Integer> testStatementLimit = null;
    public static Option<Integer> operatorLimit = null;
    public static Option<Long> complexityLimit = null;
    public static Option<Integer> memberFunctionsLimit = null;
    public static Option<Integer> memberFunctionsArgLimit = null;
    public static Option<Integer> stringLiteralSizeLimit = null;
    public static Option<Integer> classesLimit = null;
    public static Option<Integer> implementationLimit = null;
    public static Option<Integer> dimensionsLimit = null;
    public static Option<Integer> minCfgDepth = null;
    public static Option<Integer> maxCfgDepth = null;
    public static Option<Boolean> enableStrictFP = null;
    public static Option<Boolean> printComplexity = null;
    public static Option<Boolean> printHierarchy = null;
    public static Option<Boolean> injectRuntimeNondeterminism = null;
    public static Option<Integer> nondeterminism = null;
    //public static BooleanOption disableFinals = optionResolver.addBooleanOption("disable-finals", "Don\'t use finals");
    public static Option<Boolean> disableFinalClasses = null;
    public static Option<Boolean> disableFinalMethods = null;
    public static Option<Boolean> disableFinalVariables = null;
    public static Option<Boolean> disableIf = null;
    public static Option<Boolean> disableSwitch = null;
    public static Option<Boolean> complexLoops = null;   //FIXME JNP Replace with (very low?) probability?
    public static Option<Boolean> disableWhile = null;
    public static Option<Boolean> disableDoWhile = null;
    public static Option<Boolean> disableFor = null;
    public static Option<Boolean> disableFunctions = null;
    public static Option<Boolean> disableVarsInBlock = null;
    public static Option<Boolean> disableExprInInit = null;
    public static Option<Boolean> disableExternalSymbols = null;
    public static Option<String> addExternalSymbols = null;
    public static Option<Boolean> disableInheritance = null;
    public static Option<Boolean> disableDowncasts = null;
    public static Option<Boolean> disableStatic = null;
    public static Option<Boolean> disableInterfaces = null;
    public static Option<Boolean> disableClasses = null;
    public static Option<Boolean> disableNestedBlocks = null;
    public static Option<Boolean> disableArrays = null;
    public static Option<Boolean> enableFinalizers = null;
    // workaraound: to reduce chance throwing ArrayIndexOutOfBoundsException
    public static Option<Integer> chanceExpressionIndex = null;
    public static Option<Integer> chanceThrow = null;
    public static Option<Integer> identityValueClassBalance = null;
    public static Option<String> testbaseDir = null;
    public static Option<Boolean> individualSandboxes = null;
    public static Option<String> tempDir = null;
    public static Option<Integer> numberOfTests = null;
    public static Option<String> seed = null;
    public static Option<Long> specificSeed = null;
    public static Option<String> classesFile = null;
    public static Option<String> excludeMethodsFile = null;
    public static Option<String> intrinsicMethodsFile = null;
    public static Option<Integer> intrinsicCallWeightBonus = null;
    public static Option<Integer> magnetismLevel = null;
    public static Option<Integer> arrayProductionWeightBonus = null;
    public static Option<Boolean> embedPrinterClass = null;
    public static Option<String> generators = null;
    public static Option<String> generatorsFactories = null;
    public static Option<String> genomeReplayFile = null;
    public static Option<String> genomeRecordFile = null;
    public static Option<Long> genomeMutationSeed = null;
    public static Option<String> genomeMutationTarget = null;
    public static Option<String> genocode = null;
    public static Option<Integer> branchStopPercent = null;
    public static Option<Integer> expressionStopPercent = null;
    public static Option<Integer> expressionStopMaxPercent = null;
    public static Option<Integer> expressionStopHalfDepth = null;
    public static Option<Integer> blockStatementBoostPercent = null;
    public static Option<Integer> blockStatementBoostHalfDepth = null;
    public static Option<Integer> assignmentFieldBiasBoostPercent = null;
    public static Option<Integer> assignmentFieldBiasHalfDepth = null;
    public static Option<Integer> assignmentFieldBiasStartPercent = null;
    public static Option<Integer> assignmentLocalMinWeightPercent = null;
    public static Option<Integer> constBiasBasePercent = null;
    public static Option<Integer> constBiasHalfDepth = null;
    public static Option<Integer> expressionMaxDepth = null;
    public static Option<Integer> mainLoopIterations = null;
    public static Option<Integer> mainLoopJitter = null;
    public static Option<Boolean> expressionDebug = null;
    public static Option<Integer> expressionDebugDepthWarn = null;
    public static Option<Integer> expressionDebugDepthHardLimit = null;
    public static Option<Boolean> blockDebug = null;
    public static Option<Integer> blockDebugAttemptWarn = null;
    public static Option<Integer> blockDebugDepthWarn = null;
    public static Option<Boolean> blockRngLogMutationStatements = null;
    public static Option<Integer> debugRichestExpressionCount = null;
    public static Option<Boolean> debugMethodCallWrapEnabled = null;
    public static Option<String> debugMethodCallWrapTarget = null;
    public static Option<String> debugMethodCallWrapArgKinds = null;
    public static Option<String> debugMethodCallWrapGenes = null;
    public static Option<Integer> debugMethodCallWrapGenesMax = null;
    private static boolean genomeRecordEnabled = false;
    private static OptionResolver activeOptionResolver = null;
    private static Map<String, String> mutationOverrides = Collections.emptyMap();
    private static final Set<String> FORBIDDEN_MUTATION_OVERRIDE_PARAMS = Set.of(
            "main-class",
            "property-file",
            "number-of-tests",
            "seed",
            "specificSeed",
            "classes-file",
            "exclude-methods-file",
            "intrinsic-methods-file",
            "testbase-dir",
            "temp-dir",
            "individual-sandboxes",
            "generators",
            "generatorsFactories",
            "genome-replay",
            "genome-record",
            "genome-mutation-seed",
            "genome-mutation-target",
            "genocode");

    public static final class State {
        private final Map<Option<?>, Object> values;

        private State(Map<Option<?>, Object> values) {
            this.values = values;
        }
    }

    public static void register(OptionResolver optionResolver) {
        mainClassNames = optionResolver.addRepeatingOption('k', "main-class", "", "Main class name");
        dataMemberLimit = optionResolver.addIntegerOption('v', "data-member-limit", 10, "Upper limit on data members");
        statementLimit = optionResolver.addIntegerOption('s', "statement-limit", 30, "Upper limit on statements in function");
        testStatementLimit = optionResolver.addIntegerOption('e', "test-statement-limit", 300, "Upper limit on statements in test() function");
        operatorLimit = optionResolver.addIntegerOption('o', "operator-limit", 50, "Upper limit on operators in a statement");
        complexityLimit = optionResolver.addLongOption('x', "complexity-limit", 10000000, "Upper limit on complexity");
        memberFunctionsLimit = optionResolver.addIntegerOption('m', "member-functions-limit", 15, "Upper limit on member functions");
        memberFunctionsArgLimit = optionResolver.addIntegerOption('a', "member-functions-arg-limit", 5, "Upper limit on the number of member function args");
        stringLiteralSizeLimit = optionResolver.addIntegerOption("string-literal-size-limit", 10, "Upper limit on the number of chars in string literal");
        classesLimit = optionResolver.addIntegerOption('c', "classes-limit", 12, "Upper limit on the number of classes");
        implementationLimit = optionResolver.addIntegerOption('i', "implementation-limit", 3, "Upper limit on a number of interfaces a class can implement");
        dimensionsLimit = optionResolver.addIntegerOption('d', "dimensions-limit", 3, "Upper limit on array dimensions");
        minCfgDepth = optionResolver.addIntegerOption("min-cfg-depth", 2, "A non-negative decimal integer used to restrict the lower bound of depth of control flow graph");
        maxCfgDepth = optionResolver.addIntegerOption("max-cfg-depth", 3, "A non-negative decimal integer used to restrict the upper bound of depth of control flow graph");
        enableStrictFP = optionResolver.addBooleanOption("enable-strict-fp", "Add strictfp attribute to test class");
        printComplexity = optionResolver.addBooleanOption("print-complexity", "Print complexity of each statement");
        printHierarchy = optionResolver.addBooleanOption("print-hierarchy", "Print resulting class hierarchy");
        injectRuntimeNondeterminism = optionResolver.addBooleanOption(null, "inject-runtime-nondeterminism", false,
                "Inject a runtime non-deterministic print (System.nanoTime) into generated main for reproducible mismatch testing");
        nondeterminism = optionResolver.addIntegerOption("nondeterminism", 0,
                "Enable nondeterministic function-call mode with weight bonus for System.nanoTime(); 0 disables");
        //disableFinals = optionResolver.addBooleanOption("disable-finals", "Don\'t use finals");
        disableFinalClasses = optionResolver.addBooleanOption("disable-final-classes", "Don\'t use final classes");
        disableFinalMethods = optionResolver.addBooleanOption("disable-final-methods", "Don\'t use final methods");
        disableFinalVariables = optionResolver.addBooleanOption("disable-final-variabless", "Don\'t use final variables");
        disableIf = optionResolver.addBooleanOption("disable-if", "Don\'t use conditionals");
        disableSwitch = optionResolver.addBooleanOption("disable-switch", "Don\'t use switch");
        complexLoops = optionResolver.addBooleanOption("complex-loops", "Generate loops with non-trivial conditions");
        disableWhile = optionResolver.addBooleanOption("disable-while", "Don\'t use while");
        disableDoWhile = optionResolver.addBooleanOption("disable-do-while", "Don\'t use do-while");
        disableFor = optionResolver.addBooleanOption("disable-for", "Don\'t use for");
        disableFunctions = optionResolver.addBooleanOption("disable-functions", "Don\'t use functions");
        disableVarsInBlock = optionResolver.addBooleanOption("disable-vars-in-block", "Don\'t generate variables in blocks");
        disableExprInInit = optionResolver.addBooleanOption("disable-expr-in-init", "Don\'t use complex expressions in variable initialization");
        disableExternalSymbols = optionResolver.addBooleanOption("disable-external-symbols", "Don\'t use external symbols");
        addExternalSymbols = optionResolver.addStringOption("add-external-symbols", "all", "Add symbols for listed classes (comma-separated list)");
        disableInheritance = optionResolver.addBooleanOption("disable-inheritance", "Disable inheritance");
        disableDowncasts = optionResolver.addBooleanOption(null, "disable-downcasts", true, "Disable downcasting of objects");
        disableStatic = optionResolver.addBooleanOption("disable-static", "Disable generation of static objects and functions");
        disableInterfaces = optionResolver.addBooleanOption("disable-interfaces", "Disable generation of interfaces");
        disableClasses = optionResolver.addBooleanOption("disable-classes", "Disable generation of classes");
        disableNestedBlocks = optionResolver.addBooleanOption("disable-nested-blocks", "Disable generation of nested blocks");
        disableArrays = optionResolver.addBooleanOption("disable-arrays", "Disable generation of arrays");
        enableFinalizers = optionResolver.addBooleanOption("enable-finalizers", "Enable finalizers (for stress testing)");
        chanceExpressionIndex = optionResolver.addIntegerOption("chance-expression-index", 0, "A non negative decimal integer used to restrict chane of generating expression in array index while creating or accessing by index");
        chanceThrow = optionResolver.addIntegerOption("chance-throw", 30, "A non negative decimal integer used to restrict chane of 'throw' statement (is adjusted by 30% afterwards, i.e. value of 100 means 30% chance of generating such a statement)");
        identityValueClassBalance = optionResolver.addIntegerOption("identity-value-class-balance", 80, "Balance (0..100) between identity and value class generation: 0=identity only, 50=equal, 100=value only");
        testbaseDir = optionResolver.addStringOption("testbase-dir", ".", "Testbase dir");
        individualSandboxes = optionResolver.addBooleanOption(
                null,
                "individual-sandboxes",
                true,
                "Generate each test in a dedicated sandbox subdirectory");
        tempDir = optionResolver.addStringOption("temp-dir", ".", "Temp dir path");
        numberOfTests = optionResolver.addIntegerOption('n', "number-of-tests", 0, "Number of test classes to generate");
        seed = optionResolver.addStringOption("seed", "", "Random seed");
        specificSeed = optionResolver.addLongOption('z', "specificSeed", 0L, "A seed to be set for specific test generation(regular seed still needed for initialization)");
        classesFile = optionResolver.addStringOption('f', "classes-file", "conf/classes.lst", "File to read classes from");
        excludeMethodsFile = optionResolver.addStringOption('r', "exclude-methods-file", "conf/exclude.methods.lst", "File to read excluded methods from");
        intrinsicMethodsFile = optionResolver.addStringOption("intrinsic-methods-file", "conf/intrinsics.lst",
                "File with methods preferred for intrinsic-oriented call bias");
        intrinsicCallWeightBonus = optionResolver.addIntegerOption("intrinsic-call-weight-bonus", 0,
                "Additional selection weight for calls marked intrinsic (0 disables bias)");
        magnetismLevel = optionResolver.addIntegerOption("magnetism-level", 0,
                "Magnet matching mode: 0=strict exact-id preference (deterministic), >0 enables distance-weighted stochastic magnetism");
        arrayProductionWeightBonus = optionResolver.addIntegerOption("array-production-weight-bonus", 0,
                "Additional selection weight percent for array productions (0 keeps default)");
        embedPrinterClass = optionResolver.addBooleanOption(null, "embed-printer-class", false,
                "Embed Printer helper class into each generated Java test source");
        generators = optionResolver.addStringOption("generators", "", "Comma-separated list of generator names");
        generatorsFactories = optionResolver.addStringOption("generatorsFactories", "", "Comma-separated list of generators factories class names");
        genomeReplayFile = optionResolver.addStringOption("genome-replay", "",
                "Genome replay base path; genocode-specific suffix is added automatically");
        genomeRecordFile = optionResolver.addStringOption("genome-record", "",
                "Genome record base path; genocode-specific suffix is added automatically");
        genomeMutationSeed = optionResolver.addLongOption(null, "genome-mutation-seed", 0L,
                "Override seed used for replay mutation subtree RNG");
        genomeMutationTarget = optionResolver.addStringOption("genome-mutation-target", "",
                "Mutation target scope token in replay genome (currently block gene only, format: B<seed>)");
        genocode = optionResolver.addStringOption(
                "genocode",
                "full-genocode",
                "Genome genocode backend (supported: full-genocode)");

        branchStopPercent = optionResolver.addIntegerOption("branch-stop-percent", 12,
                "Base probability (0..100) to stop generating more statements in a block");
        expressionStopPercent = optionResolver.addIntegerOption("expression-stop-percent", 12,
                "Base probability (0..100) to force terminal expression generation and stop recursion");
        expressionStopMaxPercent = optionResolver.addIntegerOption("expression-stop-max-percent", 92,
                "Asymptotic upper bound (0..100) for depth-aware expression stop probability");
        expressionStopHalfDepth = optionResolver.addIntegerOption("expression-stop-half-depth", 14,
                "Depth where dynamic stop probability reaches the midpoint between base and max");
        blockStatementBoostPercent = optionResolver.addIntegerOption("block-statement-boost-percent", 140,
                "Depth-tapered shallow-block statement-attempt boost (0..1000), applied on top of block-seed count");
        blockStatementBoostHalfDepth = optionResolver.addIntegerOption("block-statement-boost-half-depth", 3,
                "Depth parameter for block statement-attempt boost taper");
        assignmentFieldBiasBoostPercent = optionResolver.addIntegerOption("assignment-field-bias-boost-percent", 250,
                "Max late-shallow boost (0..1000) for member/static variable choice vs local variables");
        assignmentFieldBiasHalfDepth = optionResolver.addIntegerOption("assignment-field-bias-half-depth", 2,
                "Depth parameter for assignment field-bias taper");
        assignmentFieldBiasStartPercent = optionResolver.addIntegerOption("assignment-field-bias-start-percent", 35,
                "Statement-progress threshold (0..100) where field-target bias begins inside a block");
        assignmentLocalMinWeightPercent = optionResolver.addIntegerOption("assignment-local-min-weight-percent", 20,
                "Lower bound (0..100) for local-variable rule weight under assignment field bias");
        constBiasBasePercent = optionResolver.addIntegerOption("const-bias-base-percent", 100,
                "Base probability (0..100) to bias selected expression builders away from constants/literals");
        constBiasHalfDepth = optionResolver.addIntegerOption("const-bias-half-depth", 5,
                "Depth parameter for const-bias taper: p_bias(depth)=base*(half/(depth+half))");
        expressionMaxDepth = optionResolver.addIntegerOption("expression-max-depth", 10,
                "Hard upper bound on expression recursion depth");
        mainLoopIterations = optionResolver.addIntegerOption("main-loop-iterations", 10,
                "Target iteration count for generated main() loop");
        mainLoopJitter = optionResolver.addIntegerOption("main-loop-jitter", 2,
                "Absolute random jitter added/subtracted from main-loop-iterations");
        expressionDebug = optionResolver.addBooleanOption(null, "expression-debug", false,
                "Enable expression-recursion debug probes");
        expressionDebugDepthWarn = optionResolver.addIntegerOption("expression-debug-depth-warn", 120,
                "Warn when expression-recursion depth reaches this level");
        expressionDebugDepthHardLimit = optionResolver.addIntegerOption("expression-debug-depth-hard-limit", 0,
                "Temporary hard limit for expression-recursion depth; 0 disables this limiter");
        blockDebug = optionResolver.addBooleanOption(null, "block-debug", false,
                "Enable block-generation debug probes");
        blockDebugAttemptWarn = optionResolver.addIntegerOption("block-debug-attempt-warn", 0,
                "Report block debug when attempts >= this value; 0 means report every block");
        blockDebugDepthWarn = optionResolver.addIntegerOption("block-debug-depth-warn", 0,
                "Report block debug when block recursion depth >= this value; 0 means report every block");
        blockRngLogMutationStatements = optionResolver.addBooleanOption(
                null,
                "block-rng-log-mutation-statements",
                false,
                "Log statement-seed decisions for mutation-root block in guided replay mode");
        debugRichestExpressionCount = optionResolver.addIntegerOption(
                null,
                "debug-richest-expression-count",
                0,
                "If > 0, enable expression richness debug and print top N expressions in source header");
        debugMethodCallWrapEnabled = optionResolver.addBooleanOption(
                null,
                "debug-method-call-wrap-enabled",
                false,
                "Enable debug wrapping for selected call arguments via Printer.debug* helpers");
        debugMethodCallWrapTarget = optionResolver.addStringOption(
                "debug-method-call-wrap-target",
                "java.lang.Long.getLong",
                "Target method for debug wrapping, format: <owner>.<name>");
        debugMethodCallWrapArgKinds = optionResolver.addStringOption(
                "debug-method-call-wrap-arg-kinds",
                "literal",
                "Comma-separated wrapped-arg node kinds: literal,operator,function,variable,all");
        debugMethodCallWrapGenes = optionResolver.addStringOption(
                "debug-method-call-wrap-genes",
                "",
                "Comma-separated expression genes (E...) to wrap; empty means no wrapping");
        debugMethodCallWrapGenesMax = optionResolver.addIntegerOption(
                "debug-method-call-wrap-genes-max",
                8,
                "Maximum number of genes to use from --debug-method-call-wrap-genes");
    }

    /**
     * Initializes from the given command-line args
     *
     * @param args command-line arguments to use for initialization
     */
    public static void initializeFromCmdline(String[] args) {
        OverrideParseResult overrideParseResult = parseOverrideArgs(args);
        OptionResolver parser = new OptionResolver();
        Option<String> propertyFileOpt = parser.addStringOption('p', "property-file",
                "conf/default.properties", "File to read properties from");
        ProductionParams.register(parser);
        parser.parse(overrideParseResult.argsWithoutOverrides, propertyFileOpt);
        activeOptionResolver = parser;
        mutationOverrides = Collections.unmodifiableMap(overrideParseResult.overrides);
        validateMutationOverrides();

        GenerationState.initializeFlowParamsFromProductionParams();

        String genocodeName = firstNonBlank(
                valueIfSet(genocode),
                genocode.value());
        if ("full-genocode".equals(genocodeName) || "rule-rng-genocode".equals(genocodeName)) {
            Genome.setGenocode(new FullGenocode());
        } else {
            throw new IllegalArgumentException("Unknown --genocode value: " + genocodeName);
        }

        String replayPath = resolveGenomePath(genocodeName, firstNonBlank(
                valueIfSet(genomeReplayFile),
                ""));
        String recordPath = resolveGenomePath(genocodeName, firstNonBlank(
                valueIfSet(genomeRecordFile),
                ""));

        if (("full-genocode".equals(genocodeName) || "rule-rng-genocode".equals(genocodeName))
                && replayPath != null
                && replayPath.endsWith(".blocks")) {
            throw new IllegalArgumentException(
                    "full-genocode replay requires a .genome file; .blocks is record-only");
        }
        genomeRecordEnabled = recordPath != null && !recordPath.isBlank();

        boolean replayMode = replayPath != null && !replayPath.isBlank();
        if (replayMode && seed.isSet() && !seed.value().isBlank()) {
            throw new IllegalArgumentException("--seed cannot be used together with "
                    + "--genome-replay");
        }

        // In replay mode, global RNG seed is taken from genome header.
        PseudoRandom.reset(replayMode ? "0" : ProductionParams.seed.value());
        Long mutationSeedOverride = firstSetLong(genomeMutationSeed);
        String mutationTarget = valueIfSet(genomeMutationTarget);
        Genome.setReplayStrict(true);
        Genome.initialize(replayPath, recordPath, mutationSeedOverride, mutationTarget);
    }

    public static boolean isGenomeRecordEnabled() {
        return genomeRecordEnabled;
    }

    public static boolean hasMutationOverrides() {
        return !mutationOverrides.isEmpty();
    }

    public static State beginMutationOverrideScope() {
        if (!hasMutationOverrides()) {
            return null;
        }
        ensureResolverInitialized();
        State previousState = captureState();
        applyMutationOverrides();
        return previousState;
    }

    public static void endMutationOverrideScope(State previousState) {
        if (previousState == null) {
            return;
        }
        restoreState(previousState);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String valueIfSet(Option<String> option) {
        return option != null && option.isSet() ? option.value() : "";
    }

    private static Long firstSetLong(Option<Long> option) {
        if (option != null && option.isSet()) {
            return option.value();
        }
        return null;
    }

    private static String resolveGenomePath(String genocodeName, String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return rawPath;
        }
        String suffix = genomeSuffixForGenocode(genocodeName);
        if (rawPath.endsWith(suffix)) {
            return rawPath;
        }
        return rawPath + suffix;
    }

    private static String genomeSuffixForGenocode(String genocodeName) {
        // Both currently use .genome as the main replay/record artifact.
        // full-genocode additionally writes a compact .blocks side file.
        return ".genome";
    }

    public static String printerClassName() {
        return embedPrinterClass.value() ? "Printer" : "jdk.test.lib.jittester.jtreg.Printer";
    }

    private static State captureState() {
        ensureResolverInitialized();
        return new State(activeOptionResolver.snapshotValues());
    }

    private static void restoreState(State state) {
        ensureResolverInitialized();
        activeOptionResolver.restoreValues(state.values);
    }

    private static void ensureResolverInitialized() {
        if (activeOptionResolver == null) {
            throw new IllegalStateException("ProductionParams options are not initialized");
        }
    }

    private static void applyMutationOverrides() {
        for (Map.Entry<String, String> entry : mutationOverrides.entrySet()) {
            Option<?> option = resolveOption(entry.getKey());
            activeOptionResolver.overrideOption(option, entry.getValue());
        }
    }

    private static void validateMutationOverrides() {
        if (mutationOverrides.isEmpty()) {
            return;
        }
        State originalState = captureState();
        for (Map.Entry<String, String> entry : mutationOverrides.entrySet()) {
            String requestedName = entry.getKey();
            String canonicalName = resolveOptionName(requestedName);
            if (FORBIDDEN_MUTATION_OVERRIDE_PARAMS.contains(canonicalName)) {
                throw new IllegalArgumentException("Mutation override is not allowed for --"
                        + canonicalName);
            }
            Option<?> option = resolveOption(canonicalName);
            if (option.getDefaultValue() instanceof List) {
                throw new IllegalArgumentException("Mutation override is not supported for repeating option --"
                        + canonicalName);
            }
            activeOptionResolver.overrideOption(option, entry.getValue());
        }
        // Restore original CLI values after validation parse.
        // Validation above intentionally checks parse compatibility for each override.
        restoreState(originalState);
    }

    private static Option<?> resolveOption(String requestedName) {
        String canonicalName = resolveOptionName(requestedName);
        Option<?> option = activeOptionResolver.findOptionByLongName(canonicalName);
        if (option == null) {
            throw new IllegalArgumentException("Unknown override parameter: " + requestedName);
        }
        return option;
    }

    private static String resolveOptionName(String requestedName) {
        ensureResolverInitialized();
        if (requestedName == null || requestedName.isBlank()) {
            throw new IllegalArgumentException("Override parameter name must not be empty");
        }
        String trimmed = requestedName.trim();
        while (trimmed.startsWith("-")) {
            trimmed = trimmed.substring(1);
        }
        Option<?> exact = activeOptionResolver.findOptionByLongName(trimmed);
        if (exact != null) {
            return exact.getLongName();
        }
        String normalizedInput = normalizeOptionKey(trimmed);
        Option<?> normalizedMatch = null;
        for (Option<?> option : activeOptionResolver.getRegisteredOptions()) {
            if (normalizeOptionKey(option.getLongName()).equals(normalizedInput)) {
                if (normalizedMatch != null) {
                    throw new IllegalArgumentException("Ambiguous override parameter: "
                            + requestedName);
                }
                normalizedMatch = option;
            }
        }
        if (normalizedMatch == null) {
            throw new IllegalArgumentException("Unknown override parameter: " + requestedName);
        }
        return normalizedMatch.getLongName();
    }

    private static String normalizeOptionKey(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    private static OverrideParseResult parseOverrideArgs(String[] args) {
        List<String> passthrough = new ArrayList<>();
        LinkedHashMap<String, String> overrides = new LinkedHashMap<>();
        int i = 0;
        while (i < args.length) {
            String arg = args[i];
            if ("--override".equals(arg)) {
                if (i + 2 >= args.length) {
                    throw new IllegalArgumentException(
                            "--override requires two arguments: <param> <value>");
                }
                String key = args[i + 1];
                String value = args[i + 2];
                overrides.put(key, value);
                i += 3;
                continue;
            }
            passthrough.add(arg);
            i++;
        }
        return new OverrideParseResult(
                passthrough.toArray(new String[0]),
                overrides);
    }

    private record OverrideParseResult(String[] argsWithoutOverrides, Map<String, String> overrides) { }

}
