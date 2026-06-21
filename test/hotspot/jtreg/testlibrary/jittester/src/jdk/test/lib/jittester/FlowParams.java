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

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Scoped generation-time parameters that may vary across replay/mutation scopes.
 *
 * <p>This starts with statement/operator limits and is intentionally small,
 * so values can be migrated from {@link ProductionParams} gradually.</p>
 */
public final class FlowParams {
    private final FlowParams prev;
    private final long complexityLimit;
    private final int statementLimit;
    private final int operatorLimit;
    private final String iterationVariable;
    private final boolean inArrayKernel;
    private final boolean preferIterationIndexedArrayTerminal;
    private final Type fixedOperandType;
    private final int arrayElementExpressionWeightPercent;
    private final int arrayExtractionExpressionWeightPercent;
    private final Set<String> readOnlyVars;
    private final Set<String> iterationVariables;

    private FlowParams(FlowParams prev, long complexityLimit, int statementLimit, int operatorLimit,
                       String iterationVariable, boolean inArrayKernel,
                       boolean preferIterationIndexedArrayTerminal,
                       Type fixedOperandType,
                       int arrayElementExpressionWeightPercent,
                       int arrayExtractionExpressionWeightPercent,
                       Set<String> readOnlyVars,
                       Set<String> iterationVariables) {
        this.prev = prev;
        this.complexityLimit = complexityLimit;
        this.statementLimit = statementLimit;
        this.operatorLimit = operatorLimit;
        this.iterationVariable = iterationVariable;
        this.inArrayKernel = inArrayKernel;
        this.preferIterationIndexedArrayTerminal = preferIterationIndexedArrayTerminal;
        this.fixedOperandType = fixedOperandType;
        this.arrayElementExpressionWeightPercent = arrayElementExpressionWeightPercent;
        this.arrayExtractionExpressionWeightPercent = arrayExtractionExpressionWeightPercent;
        this.readOnlyVars = readOnlyVars;
        this.iterationVariables = iterationVariables;
    }

    public static FlowParams fromProductionParams() {
        return new FlowParams(null,
                ProductionParams.complexityLimit.value(),
                ProductionParams.statementLimit.value(),
                ProductionParams.operatorLimit.value(),
                null,
                false,
                false,
                null,
                100,
                100,
                Collections.emptySet(),
                Collections.emptySet());
    }

    public long complexityLimit() {
        return complexityLimit;
    }

    public int statementLimit() {
        return statementLimit;
    }

    public int operatorLimit() {
        return operatorLimit;
    }

    public String iterationVariable() {
        return iterationVariable;
    }

    public boolean inArrayKernel() {
        return inArrayKernel;
    }

    public boolean preferIterationIndexedArrayTerminal() {
        return preferIterationIndexedArrayTerminal;
    }

    public Optional<Type> fixedOperandType() {
        return Optional.ofNullable(fixedOperandType);
    }

    public int arrayElementExpressionWeightPercent() {
        return arrayElementExpressionWeightPercent;
    }

    public int arrayExtractionExpressionWeightPercent() {
        return arrayExtractionExpressionWeightPercent;
    }

    public boolean isReadOnlyVar(String variableName) {
        if (variableName == null || variableName.isBlank()) {
            return false;
        }
        return readOnlyVars.contains(variableName);
    }

    public Set<String> readOnlyVars() {
        return readOnlyVars;
    }

    public boolean isIterationVariable(String variableName) {
        if (variableName == null || variableName.isBlank()) {
            return false;
        }
        return iterationVariables.contains(variableName);
    }

    public Set<String> iterationVariables() {
        return iterationVariables;
    }

    public Builder withStatementLimit(int value) {
        return new Builder(this).withStatementLimit(value);
    }

    public Builder withComplexityLimit(long value) {
        return new Builder(this).withComplexityLimit(value);
    }

    public Builder withLimits(long complexityLimit, int statementLimit, int operatorLimit) {
        return new Builder(this).withLimits(complexityLimit, statementLimit, operatorLimit);
    }

    public Builder withOperatorLimit(int value) {
        return new Builder(this).withOperatorLimit(value);
    }

    public Builder withIterationVariable(String value) {
        return new Builder(this).withIterationVariable(value);
    }

    public Builder withProductionParamsLimits() {
        return new Builder(this)
                .withComplexityLimit(ProductionParams.complexityLimit.value())
                .withStatementLimit(ProductionParams.statementLimit.value())
                .withOperatorLimit(ProductionParams.operatorLimit.value());
    }

    public Builder withMoreReadOnlyVars(String... values) {
        return new Builder(this).withMoreReadOnlyVars(values);
    }

    public Builder withMoreIterationVariables(String... values) {
        return new Builder(this).withMoreIterationVariables(values);
    }

    public String dumpSnapshot() {
        return "FlowParams{complexityLimit=" + complexityLimit
                + ", statementLimit=" + statementLimit
                + ", operatorLimit=" + operatorLimit
                + ", iterationVariable=" + (iterationVariable == null ? "<none>" : iterationVariable)
                + ", inArrayKernel=" + inArrayKernel
                + ", preferIterationIndexedArrayTerminal=" + preferIterationIndexedArrayTerminal
                + ", fixedOperandType=" + (fixedOperandType == null ? "<none>" : fixedOperandType.getName())
                + ", arrayElementExpressionWeightPercent=" + arrayElementExpressionWeightPercent
                + ", arrayExtractionExpressionWeightPercent=" + arrayExtractionExpressionWeightPercent
                + ", readOnlyVars=" + readOnlyVars
                + ", iterationVariables=" + iterationVariables
                + ", depth=" + depth(this)
                + "}";
    }

    private static int depth(FlowParams frame) {
        int depth = 0;
        FlowParams current = frame;
        while (current != null) {
            depth++;
            current = current.prev;
        }
        return depth;
    }

    private static int normalizePercent(int value) {
        return Math.max(0, value);
    }

    public static final class Builder {
        private final FlowParams base;
        private long complexityLimit;
        private int statementLimit;
        private int operatorLimit;
        private String iterationVariable;
        private boolean inArrayKernel;
        private boolean preferIterationIndexedArrayTerminal;
        private Type fixedOperandType;
        private int arrayElementExpressionWeightPercent;
        private int arrayExtractionExpressionWeightPercent;
        private LinkedHashSet<String> readOnlyVars;
        private LinkedHashSet<String> iterationVariables;

        private Builder(FlowParams base) {
            if (base == null) {
                throw new IllegalArgumentException("FlowParams builder base must not be null");
            }
            this.base = base;
            this.complexityLimit = base.complexityLimit;
            this.statementLimit = base.statementLimit;
            this.operatorLimit = base.operatorLimit;
            this.iterationVariable = base.iterationVariable;
            this.inArrayKernel = base.inArrayKernel;
            this.preferIterationIndexedArrayTerminal = base.preferIterationIndexedArrayTerminal;
            this.fixedOperandType = base.fixedOperandType;
            this.arrayElementExpressionWeightPercent = base.arrayElementExpressionWeightPercent;
            this.arrayExtractionExpressionWeightPercent = base.arrayExtractionExpressionWeightPercent;
            this.readOnlyVars = new LinkedHashSet<>(base.readOnlyVars);
            this.iterationVariables = new LinkedHashSet<>(base.iterationVariables);
        }

        public Builder withComplexityLimit(long value) {
            this.complexityLimit = value;
            return this;
        }

        public Builder withLimits(long complexityLimit, int statementLimit, int operatorLimit) {
            return withComplexityLimit(complexityLimit)
                    .withStatementLimit(statementLimit)
                    .withOperatorLimit(operatorLimit);
        }

        public Builder withStatementLimit(int value) {
            this.statementLimit = value;
            return this;
        }

        public Builder withOperatorLimit(int value) {
            this.operatorLimit = value;
            return this;
        }

        public Builder withIterationVariable(String value) {
            this.iterationVariable = value;
            return this;
        }

        public Builder withInArrayKernel(boolean value) {
            this.inArrayKernel = value;
            return this;
        }

        public Builder withPreferIterationIndexedArrayTerminal(boolean value) {
            this.preferIterationIndexedArrayTerminal = value;
            return this;
        }

        public Builder withFixedOperandType(Type value) {
            this.fixedOperandType = value;
            return this;
        }

        public Builder clearFixedOperandType() {
            this.fixedOperandType = null;
            return this;
        }

        public Builder withArrayElementExpressionWeightPercent(int value) {
            this.arrayElementExpressionWeightPercent = normalizePercent(value);
            return this;
        }

        public Builder withArrayExtractionExpressionWeightPercent(int value) {
            this.arrayExtractionExpressionWeightPercent = normalizePercent(value);
            return this;
        }

        public Builder withMoreReadOnlyVars(String... values) {
            if (values == null) {
                return this;
            }
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    readOnlyVars.add(value);
                }
            }
            return this;
        }

        public Builder withMoreIterationVariables(String... values) {
            if (values == null) {
                return this;
            }
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    iterationVariables.add(value);
                }
            }
            return this;
        }

        public FlowParams advance() {
            return new FlowParams(base, complexityLimit, statementLimit, operatorLimit, iterationVariable,
                    inArrayKernel, preferIterationIndexedArrayTerminal, fixedOperandType,
                    arrayElementExpressionWeightPercent, arrayExtractionExpressionWeightPercent,
                    Collections.unmodifiableSet(new LinkedHashSet<>(readOnlyVars)),
                    Collections.unmodifiableSet(new LinkedHashSet<>(iterationVariables)));
        }
    }
}
