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

import jdk.test.lib.jittester.ProductionParams;
import jdk.test.lib.jittester.utils.PseudoRandom;

final class ExpressionGuards {
    private static final int BASIS_POINTS = 10_000;

    private ExpressionGuards() {
    }

    static boolean shouldSkipGuard() {
        return shouldSkipGuard(1);
    }

    static boolean shouldSkipGuard(int probabilityDivisor) {
        int divisor = Math.max(1, probabilityDivisor);
        int rawBasisPoints = Math.max(0, Math.min(BASIS_POINTS, ProductionParams.expressionGuardRawBp.value()));
        int adjustedBasisPoints = rawBasisPoints / divisor;
        return adjustedBasisPoints > 0
                && PseudoRandom.randomNotNegative(BASIS_POINTS) < adjustedBasisPoints;
    }
}
