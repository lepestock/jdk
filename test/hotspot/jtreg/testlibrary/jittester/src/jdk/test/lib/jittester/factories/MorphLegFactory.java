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

import java.util.ArrayList;
import java.util.List;
import jdk.test.lib.jittester.GenerationState;
import jdk.test.lib.jittester.IRNode;
import jdk.test.lib.jittester.ProductionFailedException;
import jdk.test.lib.jittester.morph.MorphContext;
import jdk.test.lib.jittester.morph.MorphLegResult;
import jdk.test.lib.jittester.morph.MorphLegTarget;
import jdk.test.lib.jittester.morph.MorphTemplate;
import jdk.test.lib.jittester.utils.Genome;
import jdk.test.lib.jittester.utils.PseudoRandom;

class MorphLegFactory extends Factory<IRNode> {
    private static final String TEMPLATE_ID_EVENT = "morph.template.id";
    private static final String LEG_ID_EVENT = "morph.leg.id";
    private final IRNodeBuilder builder;
    private final MorphLegTarget target;

    MorphLegFactory(IRNodeBuilder builder, MorphLegTarget target) {
        this.builder = builder;
        this.target = target;
    }

    static boolean hasApplicableLeg(MorphContext context, MorphLegTarget target, IRNodeBuilder builder) {
        return !applicableTemplates(context, target, builder).isEmpty();
    }

    static double weight(MorphContext context, double baseWeight, MorphLegTarget target, IRNodeBuilder builder) {
        double multiplier = 0.0;
        for (MorphTemplate template : applicableTemplates(context, target, builder)) {
            multiplier = Math.max(multiplier, template.legWeightMultiplier());
        }
        return baseWeight * multiplier;
    }

    static boolean hasWholeBlockLeg(MorphContext context, IRNodeBuilder builder) {
        for (MorphTemplate template : applicableTemplates(context, MorphLegTarget.BLOCK, builder)) {
            if (template.takesWholeTarget(MorphLegTarget.BLOCK)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public IRNode produce() throws ProductionFailedException {
        MorphContext context = GenerationState.currentMorphContext();
        List<MorphTemplate> applicableTemplates = applicableTemplates(context, target, builder);
        if (applicableTemplates.isEmpty()) {
            throw new ProductionFailedException();
        }
        int liveTemplateIndex = (int) (PseudoRandom.randomSilent() * applicableTemplates.size());
        MorphTemplate liveTemplate = applicableTemplates.get(liveTemplateIndex);
        long templateId = createOrConsumeTemplateEvent(TEMPLATE_ID_EVENT, liveTemplate::id);
        int templateIndex = context.indexOfId(templateId);
        if (templateIndex < 0 || templateIndex >= context.size()) {
            throw new ProductionFailedException();
        }
        MorphTemplate template = context.get(templateIndex);
        if (!template.canProduceLeg(target, builder)) {
            throw new ProductionFailedException();
        }
        int leg = Math.toIntExact(createOrConsumeTemplateEvent(LEG_ID_EVENT, template::nextLeg));
        MorphLegResult result = template.produceLeg(target, leg, builder);
        MorphContext updated = result.templateExhausted()
                ? context.without(templateIndex)
                : context.withReplaced(templateIndex, result.updatedTemplate());
        GenerationState.setCurrentMorphContext(updated);
        return result.node();
    }

    private static List<MorphTemplate> applicableTemplates(MorphContext context,
            MorphLegTarget target, IRNodeBuilder builder) {
        ArrayList<MorphTemplate> result = new ArrayList<>();
        for (int i = 0; i < context.size(); i++) {
            MorphTemplate template = context.get(i);
            if (template.canProduceLeg(target, builder)) {
                result.add(template);
            }
        }
        return result;
    }

    private static long createOrConsumeTemplateEvent(String name, ChoiceSupplier liveChoice) {
        if (Genome.isReplayActive()) {
            Long replayChoice = Genome.consumeTemplateGene(name, 0L);
            if (replayChoice == null) {
                throw new RuntimeException("Genome replay desync: missing morph template event for '"
                        + name + "'");
            }
            return replayChoice;
        }
        long choice = liveChoice.getAsLong();
        Genome.recordTemplateGene(name, choice);
        return choice;
    }

    private interface ChoiceSupplier {
        long getAsLong();
    }
}
