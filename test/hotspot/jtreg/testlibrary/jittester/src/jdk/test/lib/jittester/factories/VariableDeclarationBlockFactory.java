/*
 * Copyright (c) 2015, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;

import jdk.test.lib.jittester.Declaration;
import jdk.test.lib.jittester.FieldDeclarationSequence;
import jdk.test.lib.jittester.ProductionFailedException;
import jdk.test.lib.jittester.ProductionParams;
import jdk.test.lib.jittester.GenerationState;
import jdk.test.lib.jittester.IRNode;
import jdk.test.lib.jittester.Rule;
import jdk.test.lib.jittester.VariableDeclarationBlock;
import jdk.test.lib.jittester.morph.MorphLegTarget;
import jdk.test.lib.jittester.types.TypeKlass;
import jdk.test.lib.jittester.utils.PseudoRandom;

class VariableDeclarationBlockFactory extends Factory<VariableDeclarationBlock> {
    private final int operatorLimit;
    private final boolean exceptionSafe;
    private final boolean constantsOnly;
    private final int level;
    private final TypeKlass ownerClass;

    VariableDeclarationBlockFactory(TypeKlass ownerClass,
            int operatorLimit, int level, boolean exceptionSafe) {
        this(ownerClass, operatorLimit, level, exceptionSafe, false);
    }

    VariableDeclarationBlockFactory(TypeKlass ownerClass,
            int operatorLimit, int level, boolean exceptionSafe, boolean constantsOnly) {
        this.ownerClass = ownerClass;
        this.operatorLimit = operatorLimit;
        this.level = level;
        this.exceptionSafe = exceptionSafe;
        this.constantsOnly = constantsOnly;
    }

    @Override
    public VariableDeclarationBlock produce() throws ProductionFailedException {
        ArrayList<IRNode> content = new ArrayList<>();
        int configuredLimit = ProductionParams.dataMemberLimit.value();
        int randomPart = (int) Math.ceil(PseudoRandom.random() * configuredLimit);
        int floor = Math.max(1, configuredLimit / 2);
        int limit = Math.max(floor, randomPart);
        IRNodeBuilder builder = new IRNodeBuilder()
                .setOwnerKlass(ownerClass)
                .withOperatorLimit(operatorLimit)
                .setLevel(level)
                .setIsLocal(false)
                .setExceptionSafe(exceptionSafe);
        Factory<Declaration> declFactory = constantsOnly
                ? builder.getConstantDeclarationFactory()
                : builder.getDeclarationFactory();
        for (int i = 0; i < limit; i++) {
            try {
                double mtLegWeight = GenerationState.currentFlowParams().mtLegWeight();
                if (mtLegWeight > 0.0 && MorphLegFactory.hasApplicableLeg(GenerationState.currentMorphContext(),
                        MorphLegTarget.FIELD_DECLARATION, builder)) {
                    Rule<IRNode> rule = new Rule<>("field_declaration");
                    rule.add("morph_field_leg", new MorphLegFactory(builder, MorphLegTarget.FIELD_DECLARATION),
                            MorphLegFactory.weight(GenerationState.currentMorphContext(), mtLegWeight,
                                    MorphLegTarget.FIELD_DECLARATION, builder));
                    rule.add("declaration", declFactory, 1.0);
                    IRNode node = rule.produce();
                    if (!(node instanceof Declaration || node instanceof FieldDeclarationSequence)) {
                        throw new ProductionFailedException();
                    }
                    content.add(node);
                } else {
                    content.add(declFactory.produce());
                }
            } catch (ProductionFailedException e) {
            }
        }
        return new VariableDeclarationBlock(content, level);
    }
}
