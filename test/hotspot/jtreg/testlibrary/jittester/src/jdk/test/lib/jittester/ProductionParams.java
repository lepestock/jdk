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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import jdk.test.lib.jittester.utils.Genome;
import jdk.test.lib.jittester.utils.OptionResolver;
import jdk.test.lib.jittester.utils.OptionResolver.Option;
import jdk.test.lib.jittester.genocode.full.FullGenocode;
import jdk.test.lib.jittester.utils.PseudoRandom;

public class ProductionParams {
    private static final String PROFILE_FILE_OPTION = "--profile-file";
    private static final String PROFILE_ALIAS_OPTION = "--profile";

    public static Option<List<String>> mainClassNames = null;
    public static Option<Integer> dataMemberLimit = null;
    public static Option<Integer> statementLimit = null;
    public static Option<Integer> testStatementLimit = null;
    public static Option<Integer> operatorLimit = null;
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
    public static Option<String> arraysAllowedTypes = null;
    public static Option<Integer> arrayFieldDefinitionWeightBonus = null;
    public static Option<Boolean> enableFinalizers = null;
    // workaraound: to reduce chance throwing ArrayIndexOutOfBoundsException
    public static Option<Integer> chanceExpressionIndex = null;
    public static Option<Integer> chanceThrow = null;
    public static Option<Integer> identityValueClassBalance = null;
    public static Option<String> testbaseDir = null;
    public static Option<Boolean> individualSandboxes = null;
    public static Option<String> tempDir = null;
    public static Option<Integer> numberOfTests = null;
    public static Option<Long> seed = null;
    public static Option<String> confDir = null;
    public static Option<String> classesFile = null;
    public static Option<String> excludeMethodsFile = null;
    public static Option<String> intrinsicMethodsFile = null;
    public static Option<String> coreLibSymbolsFile = null;
    public static Option<String> methodArgumentConstraintsFile = null;
    public static Option<String> methodResultWrappersFile = null;
    public static Option<Integer> expressionGuardRawBp = null;
    public static Option<Integer> intrinsicCallWeightBonus = null;
    public static Option<Integer> magnetismLevel = null;
    public static Option<Integer> arrayProductionWeightBonus = null;
    public static Option<Integer> listStoragePercent = null;
    public static Option<Integer> arrayKernelBodyStatementPercent = null;
    public static Option<Boolean> arrayKernelCollectionElementLValues = null;
    public static Option<Integer> collectionPrintReductionPercent = null;
    public static Option<String> embedUtilsPath = null;
    public static Option<Boolean> pulsemap = null;
    public static Option<Boolean> disableFixedTreeExceptionGuards = null;
    public static Option<String> generators = null;
    public static Option<String> generatorsFactories = null;
    public static Option<String> genomeReplayFile = null;
    public static Option<String> genomeRecordFile = null;
    public static Option<Long> genomeMutationSeed = null;
    public static Option<String> genomeMutationTarget = null;
    public static Option<String> genocode = null;
    public static Option<Integer> expressionStopFloorPercent = null;
    public static Option<Integer> expressionStopStartDepth = null;
    public static Option<Integer> expressionStopFullDepth = null;
    public static Option<Integer> taperingBlockTerminalProbabilityPercent = null;
    public static Option<Integer> taperingBlockTerminalReciprocalK = null;
    public static Option<Integer> taperingBlockStatementLimitMultiplierPercent = null;
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
    public static Option<Boolean> debugArrayAssignmentCandidates = null;
    public static Option<Integer> debugArrayAssignmentCandidatesMax = null;
    public static Option<Boolean> debugMorphSourceDiagnostics = null;
    public static Option<Integer> lockEliminationMorphTemplateProbability = null;
    public static Option<Integer> morphLockEliminationCreateLockVarProbability = null;
    public static Option<Integer> morphTemplateLegWeight = null;
    public static Option<Integer> morphTemplateReferenceDeteriorationPercent = null;
    private static boolean genomeRecordEnabled = false;
    private static OptionResolver activeOptionResolver = null;
    private static Map<String, String> mutationOverrides = Collections.emptyMap();
    private static final Set<String> FORBIDDEN_MUTATION_OVERRIDE_PARAMS = Set.of(
            "main-class",
            "property-file",
            "number-of-tests",
            "seed",
            "conf-dir",
            "classes-file",
            "exclude-methods-file",
            "intrinsic-methods-file",
            "method-argument-constraints-file",
            "method-result-wrappers-file",
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
        disableArrays = optionResolver.addBooleanOption(null, "arrays-disable", true,
                "Disable generation of arrays");
        arraysAllowedTypes = optionResolver.addStringOption(
                "arrays-allowed-types",
                "",
                "Comma-separated array element type allowlist; empty means no restriction");
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
        seed = optionResolver.addLongOption(null, "seed", 0L, "Seed to set for test generation replay");
        confDir = optionResolver.addStringOption("conf-dir", "", "Directory with JitTester configuration files");
        classesFile = optionResolver.addStringOption('f', "classes-file", "conf/classes.lst", "File to read classes from");
        excludeMethodsFile = optionResolver.addStringOption('r', "exclude-methods-file", "conf/exclude.methods.lst", "File to read excluded methods from");
        intrinsicMethodsFile = optionResolver.addStringOption("intrinsic-methods-file", "conf/intrinsics.lst",
                "File with methods preferred for intrinsic-oriented call bias");
        coreLibSymbolsFile = optionResolver.addStringOption(
                "corelib-symbols-file",
                "",
                "File to read precomputed core library symbols from");
        methodArgumentConstraintsFile = optionResolver.addStringOption(
                "method-argument-constraints-file",
                "conf/method-argument-constraints.lst",
                "File with method argument generation constraints");
        methodResultWrappersFile = optionResolver.addStringOption(
                "method-result-wrappers-file",
                "conf/method-result-wrappers.lst",
                "File with method result wrappers");
        expressionGuardRawBp = optionResolver.addIntegerOption(
                "expression-guard-raw-bp",
                20,
                "Raw-expression probability for expression guards, in basis points; 20 means 0.20%");
        intrinsicCallWeightBonus = optionResolver.addIntegerOption("intrinsic-call-weight-bonus", 0,
                "Additional selection weight for calls marked intrinsic (0 disables bias)");
        magnetismLevel = optionResolver.addIntegerOption("magnetism-level", 0,
                "Magnet matching mode: 0=strict exact-id preference (deterministic), >0 enables distance-weighted stochastic magnetism");
        arrayProductionWeightBonus = optionResolver.addIntegerOption("array-production-weight-bonus", 0,
                "Additional selection weight percent for array productions (0 keeps default)");
        listStoragePercent = optionResolver.addIntegerOption("list-storage-percent", 0,
                "Percent chance to use java.util.List-backed one-dimensional indexed storage");
        arrayKernelBodyStatementPercent = optionResolver.addIntegerOption("array-kernel-body-statement-percent", 50,
                "Percent of parent statement budget used for array-kernel body generation");
        arrayKernelCollectionElementLValues = optionResolver.addBooleanOption(null,
                "array-kernel-array-element-lvalues",
                false,
                "Allow array elements as LValues for compound assignments and inc/dec inside array kernels");
        collectionPrintReductionPercent = optionResolver.addIntegerOption("collection-print-reduction-percent", 0,
                "Percent chance to print supported collections as compact CRC summaries");
        arrayFieldDefinitionWeightBonus = optionResolver.addIntegerOption("arrays-field-definition-weight-bonus", 0,
                "Additional selection weight percent for choosing array-typed class field declarations");
        embedUtilsPath = optionResolver.addStringOption("embed-utils-path", "",
                "Source root with JitTester utility sources to embed into each generated Java test source");
        pulsemap = optionResolver.addBooleanOption(null, "pulsemap", false,
                "Enable pulsemap prototype instrumentation with block-level runtime beats");
        disableFixedTreeExceptionGuards = optionResolver.addBooleanOption(
                null,
                "safety-exceptions-disable",
                true,
                "Disable fixed-tree exception guards around generated main/execute scaffolding");
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

        expressionStopFloorPercent = optionResolver.addIntegerOption("expression-stop-floor-percent", 1,
                "Minimum probability (0..100) to force terminal expression generation at shallow depths");
        expressionStopStartDepth = optionResolver.addIntegerOption("expression-stop-start-depth", 3,
                "Expression depth where S-shaped terminal-forcing ramp starts rising");
        expressionStopFullDepth = optionResolver.addIntegerOption("expression-stop-full-depth", 12,
                "Expression depth where S-shaped terminal-forcing ramp reaches 100%");
        taperingBlockTerminalProbabilityPercent = optionResolver.addIntegerOption("tapering-block-terminal-percent", 12,
                "Initial probability (0..100) to produce an empty terminal block");
        taperingBlockTerminalReciprocalK = optionResolver.addIntegerOption(
                "tapering-block-terminal-reciprocal-k",
                2800,
                "Reciprocal block terminal taper parameter: y(n+1)=y(n)-k/y(n), where y is non-empty percent");
        taperingBlockStatementLimitMultiplierPercent = optionResolver.addIntegerOption(
                "tapering-block-statement-limit-multiplier-percent",
                40,
                "Recursive percent multiplier for child block statement limit");
        blockStatementBoostPercent = optionResolver.addIntegerOption("block-statement-boost-percent", 50,
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
        debugArrayAssignmentCandidates = optionResolver.addBooleanOption(
                null,
                "debug-array-assignment-candidates",
                false,
                "Emit source comments with candidate array pools for array-kernel assignments");
        debugArrayAssignmentCandidatesMax = optionResolver.addIntegerOption(
                "debug-array-assignment-candidates-max",
                12,
                "Maximum number of array candidates listed per debug-array-assignment-candidates comment");
        debugMorphSourceDiagnostics = optionResolver.addBooleanOption(
                null,
                "debug-morph-source-diagnostics",
                false,
                "Emit source comments for morph template creation and leg materialization");
        lockEliminationMorphTemplateProbability = optionResolver.addIntegerOption(
                "lock-elimination-morph-template-probability",
                20,
                "Probability (0..100) to create a lock-elimination morph template on produceBlock()");
        morphLockEliminationCreateLockVarProbability = optionResolver.addIntegerOption(
                "morph-lock-elimination-create-lock-var-probability",
                50,
                "Probability (0..100) for lock-elimination morph leg to create a magnetized lock variable");
        morphTemplateLegWeight = optionResolver.addIntegerOption(
                "morph-template-leg-weight",
                100,
                "Weight percent (0..100) for active morph template legs at statement points");
        morphTemplateReferenceDeteriorationPercent = optionResolver.addIntegerOption(
                "morph-template-reference-deterioration-percent",
                5,
                "Percent multiplier for morph parameters inside morph-owned child blocks");
    }

    /**
     * Initializes from the given command-line args
     *
     * @param args command-line arguments to use for initialization
     */
    public static void initializeFromCmdline(String[] args) {
        String[] expandedArgs = expandProfileArgs(args);
        OverrideParseResult overrideParseResult = parseOverrideArgs(expandedArgs);
        OptionResolver parser = new OptionResolver();
        Option<String> propertyFileOpt = parser.addStringOption('p', "property-file",
                "conf/default.properties", "File to read properties from");
        ProductionParams.register(parser);
        parser.parse(overrideParseResult.argsWithoutOverrides, propertyFileOpt);
        applyConfDirDefaults(parser);
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
        if (replayMode && seed.isSet()) {
            throw new IllegalArgumentException("--seed cannot be used together with "
                    + "--genome-replay");
        }

        PseudoRandom.reset();
        if (!replayMode && seed.isSet()) {
            PseudoRandom.setCurrentSeed(seed.value());
        }
        Long mutationSeedOverride = firstSetLong(genomeMutationSeed);
        String mutationTarget = valueIfSet(genomeMutationTarget);
        Genome.setReplayStrict(true);
        Genome.initialize(replayPath, recordPath, mutationSeedOverride, mutationTarget);
    }

    private static void applyConfDirDefaults(OptionResolver parser) {
        if (!confDir.isSet() || confDir.value().isBlank()) {
            return;
        }

        Path dir = Path.of(confDir.value());
        setFromConfDir(parser, classesFile, dir.resolve("classes.lst"));
        setFromConfDir(parser, excludeMethodsFile, dir.resolve("exclude.methods.lst"));
        setFromConfDir(parser, intrinsicMethodsFile, dir.resolve("intrinsics.lst"));
        setFromConfDir(parser, methodArgumentConstraintsFile,
                dir.resolve("method-argument-constraints.lst"));
        setFromConfDir(parser, methodResultWrappersFile,
                dir.resolve("method-result-wrappers.lst"));
    }

    private static void setFromConfDir(OptionResolver parser, Option<String> option, Path path) {
        if (!option.isSet()) {
            parser.overrideOption(option, path.toString());
        }
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
        return embedUtils() ? "Printer" : "jdk.test.lib.jittester.jtreg.Printer";
    }

    public static String runtimeSupportClassName() {
        return embedUtils() ? "RuntimeSupport" : "jdk.test.lib.jittester.jtreg.RuntimeSupport";
    }

    public static boolean embedUtils() {
        return !embedUtilsPath.value().trim().isEmpty();
    }

    public static Path embedUtilsPath() {
        return Path.of(embedUtilsPath.value()).toAbsolutePath().normalize();
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

    private static String[] expandProfileArgs(String[] args) {
        List<String> expanded = expandProfileArgs(List.of(args), new LinkedHashSet<>());
        return expanded.toArray(new String[0]);
    }

    private static List<String> expandProfileArgs(List<String> args, Set<Path> includeStack) {
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < args.size()) {
            String arg = args.get(i);
            ProfileInclude include = parseProfileInclude(arg, i + 1 < args.size() ? args.get(i + 1) : null);
            if (include == null) {
                out.add(arg);
                i++;
                continue;
            }
            Path profilePath = Path.of(include.path()).toAbsolutePath().normalize();
            if (!includeStack.add(profilePath)) {
                throw new IllegalArgumentException("Cyclic profile include detected: " + profilePath);
            }
            List<String> profileTokens = tokenizeProfile(profilePath, readProfile(profilePath));
            out.addAll(expandProfileArgs(profileTokens, includeStack));
            includeStack.remove(profilePath);
            i += include.argsConsumed();
        }
        return out;
    }

    private static ProfileInclude parseProfileInclude(String arg, String nextArg) {
        if (PROFILE_FILE_OPTION.equals(arg) || PROFILE_ALIAS_OPTION.equals(arg)) {
            if (nextArg == null || nextArg.isBlank()) {
                throw new IllegalArgumentException(arg + " requires <path>");
            }
            return new ProfileInclude(nextArg, 2);
        }
        if (arg.startsWith(PROFILE_FILE_OPTION + "=")) {
            return new ProfileInclude(arg.substring((PROFILE_FILE_OPTION + "=").length()), 1);
        }
        if (arg.startsWith(PROFILE_ALIAS_OPTION + "=")) {
            return new ProfileInclude(arg.substring((PROFILE_ALIAS_OPTION + "=").length()), 1);
        }
        return null;
    }

    private static String readProfile(Path profilePath) {
        try {
            return Files.readString(profilePath);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot read profile file: " + profilePath, e);
        }
    }

    private static List<String> tokenizeProfile(Path profilePath, String content) {
        List<String> out = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;
        boolean escaped = false;
        int line = 1;
        for (int i = 0; i < content.length(); i++) {
            char ch = content.charAt(i);
            if (ch == '\n') {
                line++;
            }
            if (escaped) {
                token.append(ch);
                escaped = false;
                continue;
            }
            if (ch == '\\') {
                escaped = true;
                continue;
            }
            if (inSingle) {
                if (ch == '\'') {
                    inSingle = false;
                } else {
                    token.append(ch);
                }
                continue;
            }
            if (inDouble) {
                if (ch == '"') {
                    inDouble = false;
                } else {
                    token.append(ch);
                }
                continue;
            }
            if (ch == '#') {
                while (i < content.length() && content.charAt(i) != '\n') {
                    i++;
                }
                line++;
                continue;
            }
            if (Character.isWhitespace(ch)) {
                flushToken(out, token);
                continue;
            }
            if (ch == '\'') {
                inSingle = true;
                continue;
            }
            if (ch == '"') {
                inDouble = true;
                continue;
            }
            token.append(ch);
        }
        if (escaped) {
            throw new IllegalArgumentException("Profile parse error in " + profilePath + ": trailing escape");
        }
        if (inSingle || inDouble) {
            throw new IllegalArgumentException("Profile parse error in " + profilePath
                    + ": unterminated quote near line " + line);
        }
        flushToken(out, token);
        return out;
    }

    private static void flushToken(List<String> out, StringBuilder token) {
        if (token.isEmpty()) {
            return;
        }
        out.add(token.toString());
        token.setLength(0);
    }

    private record ProfileInclude(String path, int argsConsumed) { }

    private record OverrideParseResult(String[] argsWithoutOverrides, Map<String, String> overrides) { }

}
