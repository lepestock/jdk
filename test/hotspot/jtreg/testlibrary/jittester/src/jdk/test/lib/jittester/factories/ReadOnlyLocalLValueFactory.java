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

package jdk.test.lib.jittester.factories;

import jdk.test.lib.jittester.GenerationState;
import jdk.test.lib.jittester.IRNode;
import jdk.test.lib.jittester.ProductionFailedException;
import jdk.test.lib.jittester.VariableBase;

/**
 * Filters out local lvalues currently marked as read-only in FlowParams.
 */
class ReadOnlyLocalLValueFactory extends Factory<IRNode> {
    private final Factory<? extends IRNode> delegate;
    private final int retries;

    ReadOnlyLocalLValueFactory(Factory<? extends IRNode> delegate, int retries) {
        this.delegate = delegate;
        this.retries = Math.max(1, retries);
    }

    @Override
    public IRNode produce() throws ProductionFailedException {
        ProductionFailedException lastFailure = null;
        for (int i = 0; i < retries; i++) {
            try {
                IRNode candidate = delegate.produce();
                if (candidate instanceof VariableBase variableBase
                        && variableBase.getVariableInfo().isLocal()
                        && GenerationState.currentFlowParams()
                                .isReadOnlyVar(variableBase.getVariableInfo().name)) {
                    continue;
                }
                return candidate;
            } catch (ProductionFailedException e) {
                lastFailure = e;
            }
        }
        if (lastFailure != null) {
            throw lastFailure;
        }
        throw new ProductionFailedException();
    }
}
