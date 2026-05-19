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
    private static Frame head;

    private FlowParams() {
    }

    public static void initializeFromProductionParams() {
        head = new Frame(null,
                normalizeLimit(ProductionParams.statementLimit.value()),
                normalizeLimit(ProductionParams.operatorLimit.value()));
    }

    public static int statementLimit() {
        return current().statementLimit;
    }

    public static int operatorLimit() {
        return current().operatorLimit;
    }

    public static Scope pushFromProductionParams() {
        Frame current = current();
        head = new Frame(current,
                normalizeLimit(ProductionParams.statementLimit.value()),
                normalizeLimit(ProductionParams.operatorLimit.value()));
        return new Scope(current);
    }

    public static Checkpoint checkpoint() {
        return new Checkpoint(current());
    }

    public static void rollbackTo(Checkpoint checkpoint) {
        if (checkpoint == null) {
            throw new IllegalArgumentException("FlowParams checkpoint must not be null");
        }
        head = checkpoint.head;
    }

    public static String dumpSnapshot() {
        Frame frame = current();
        return "FlowParams{statementLimit=" + frame.statementLimit
                + ", operatorLimit=" + frame.operatorLimit
                + ", depth=" + depth(frame)
                + "}";
    }

    private static Frame current() {
        if (head == null) {
            initializeFromProductionParams();
        }
        return head;
    }

    private static int depth(Frame frame) {
        int depth = 0;
        Frame current = frame;
        while (current != null) {
            depth++;
            current = current.parent;
        }
        return depth;
    }

    private static int normalizeLimit(int value) {
        return Math.max(1, value);
    }

    public static final class Scope implements AutoCloseable {
        private Frame previous;

        private Scope(Frame previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (previous == null) {
                return;
            }
            head = previous;
            previous = null;
        }
    }

    public static final class Checkpoint {
        private final Frame head;

        private Checkpoint(Frame head) {
            this.head = head;
        }
    }

    private static final class Frame {
        private final Frame parent;
        private final int statementLimit;
        private final int operatorLimit;

        private Frame(Frame parent, int statementLimit, int operatorLimit) {
            this.parent = parent;
            this.statementLimit = statementLimit;
            this.operatorLimit = operatorLimit;
        }
    }
}
