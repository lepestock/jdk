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

/**
 * Scoped generation-time parameters that may vary across replay/mutation scopes.
 *
 * <p>This starts with statement/operator limits and is intentionally small,
 * so values can be migrated from {@link ProductionParams} gradually.</p>
 */
public final class FlowParams {
    private final FlowParams prev;
    private final int statementLimit;
    private final int operatorLimit;
    private final String iterationVariable;
    private final boolean inArrayKernel;
    private final boolean preferIterationIndexedArrayTerminal;

    private FlowParams(FlowParams prev, int statementLimit, int operatorLimit,
                       String iterationVariable, boolean inArrayKernel,
                       boolean preferIterationIndexedArrayTerminal) {
        this.prev = prev;
        this.statementLimit = statementLimit;
        this.operatorLimit = operatorLimit;
        this.iterationVariable = iterationVariable;
        this.inArrayKernel = inArrayKernel;
        this.preferIterationIndexedArrayTerminal = preferIterationIndexedArrayTerminal;
    }

    public static FlowParams fromProductionParams() {
        return new FlowParams(null,
                normalizeLimit(ProductionParams.statementLimit.value()),
                normalizeLimit(ProductionParams.operatorLimit.value()),
                null,
                false,
                false);
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

    public Builder withStatementLimit(int value) {
        return new Builder(this).withStatementLimit(value);
    }

    public Builder withOperatorLimit(int value) {
        return new Builder(this).withOperatorLimit(value);
    }

    public Builder withIterationVariable(String value) {
        return new Builder(this).withIterationVariable(value);
    }

    public Builder withProductionParamsLimits() {
        return new Builder(this)
                .withStatementLimit(ProductionParams.statementLimit.value())
                .withOperatorLimit(ProductionParams.operatorLimit.value());
    }

    public String dumpSnapshot() {
        return "FlowParams{statementLimit=" + statementLimit
                + ", operatorLimit=" + operatorLimit
                + ", iterationVariable=" + (iterationVariable == null ? "<none>" : iterationVariable)
                + ", inArrayKernel=" + inArrayKernel
                + ", preferIterationIndexedArrayTerminal=" + preferIterationIndexedArrayTerminal
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

    private static int normalizeLimit(int value) {
        return Math.max(1, value);
    }

    public static final class Builder {
        private final FlowParams base;
        private int statementLimit;
        private int operatorLimit;
        private String iterationVariable;
        private boolean inArrayKernel;
        private boolean preferIterationIndexedArrayTerminal;

        private Builder(FlowParams base) {
            if (base == null) {
                throw new IllegalArgumentException("FlowParams builder base must not be null");
            }
            this.base = base;
            this.statementLimit = base.statementLimit;
            this.operatorLimit = base.operatorLimit;
            this.iterationVariable = base.iterationVariable;
            this.inArrayKernel = base.inArrayKernel;
            this.preferIterationIndexedArrayTerminal = base.preferIterationIndexedArrayTerminal;
        }

        public Builder withStatementLimit(int value) {
            this.statementLimit = normalizeLimit(value);
            return this;
        }

        public Builder withOperatorLimit(int value) {
            this.operatorLimit = normalizeLimit(value);
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

        public FlowParams advance() {
            return new FlowParams(base, statementLimit, operatorLimit, iterationVariable,
                    inArrayKernel, preferIterationIndexedArrayTerminal);
        }
    }
}
