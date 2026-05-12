/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
import jdk.test.lib.jittester.ProductionFailedException;
import jdk.test.lib.jittester.Rule;
import jdk.test.lib.jittester.Type;
import jdk.test.lib.jittester.VariableBase;
import jdk.test.lib.jittester.VariableInfo;
import jdk.test.lib.jittester.types.TypeKlass;
import jdk.test.lib.jittester.utils.DepthProbabilityTaper;

class VariableFactory extends Factory<VariableBase> {
    private final Rule<VariableBase> rule;

    VariableFactory(long complexityLimit, int operatorLimit, TypeKlass ownerClass, Type resultType,
            boolean constant, boolean initialized, boolean exceptionSafe, boolean noconsts) {
        int flags = VariableInfo.NONE;
        if (constant) {
            flags |= VariableInfo.FINAL;
        }
        if (initialized) {
            flags |= VariableInfo.INITIALIZED;
        }
        rule = new Rule<>("variable");
        IRNodeBuilder b = new IRNodeBuilder().setResultType(resultType)
                .setFlags(flags)
                .setComplexityLimit(complexityLimit)
                .setOperatorLimit(operatorLimit)
                .setOwnerKlass(ownerClass)
                .setExceptionSafe(exceptionSafe);
        double nonStaticWeight = 1.0;
        double staticWeight = 1.0;
        double localWeight = 1.0;

        int blockDepth = BlockFactory.currentStatementBlockDepth();
        double progress = BlockFactory.currentStatementProgress();
        double start = clamp01(ProductionParams.assignmentFieldBiasStartPercent.value() / 100.0);
        if (blockDepth > 0 && progress > start) {
            double lateProgress = clamp01((progress - start) / Math.max(1e-9, 1.0 - start));
            double depthRatio = DepthProbabilityTaper.decayingAsymptote(
                    blockDepth,
                    1.0,
                    Math.max(1, ProductionParams.assignmentFieldBiasHalfDepth.value()));
            double boost = Math.max(0.0, ProductionParams.assignmentFieldBiasBoostPercent.value()) / 100.0;
            double memberScale = 1.0 + boost * lateProgress * depthRatio;
            double localMinWeight = clamp01(
                    ProductionParams.assignmentLocalMinWeightPercent.value() / 100.0);
            double localScale = Math.max(localMinWeight, 1.0 - lateProgress * depthRatio);
            nonStaticWeight *= memberScale;
            staticWeight *= memberScale;
            localWeight *= localScale;
        }

        rule.add("non_static_member_variable", b.getNonStaticMemberVariableFactory(), nonStaticWeight);
        rule.add("static_member_variable", b.getStaticMemberVariableFactory(), staticWeight);
        rule.add("local_variable", b.getLocalVariableFactory(), localWeight);
    }

    @Override
    public VariableBase produce() throws ProductionFailedException {
        return rule.produce();
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
