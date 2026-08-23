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
import jdk.test.lib.jittester.morph.MorphContext;
import jdk.test.lib.jittester.morph.MorphLegResult;
import jdk.test.lib.jittester.morph.MorphTemplate;
import jdk.test.lib.jittester.utils.Genome;
import jdk.test.lib.jittester.utils.PseudoRandom;

class MorphLegFactory extends Factory<IRNode> {
    private static final String TEMPLATE_ID_EVENT = "morph.template.id";
    private static final String LEG_ID_EVENT = "morph.leg.id";
    private final IRNodeBuilder builder;

    MorphLegFactory(IRNodeBuilder builder) {
        this.builder = builder;
    }

    static boolean hasApplicableLeg(MorphContext context) {
        return context.hasTemplates();
    }

    @Override
    public IRNode produce() throws ProductionFailedException {
        MorphContext context = GenerationState.currentMorphContext();
        if (!hasApplicableLeg(context)) {
            throw new ProductionFailedException();
        }
        int liveTemplateIndex = (int) (PseudoRandom.randomSilent() * context.size());
        MorphTemplate liveTemplate = context.get(liveTemplateIndex);
        long templateId = createOrConsumeTemplateEvent(TEMPLATE_ID_EVENT, liveTemplate::id);
        int templateIndex = context.indexOfId(templateId);
        if (templateIndex < 0 || templateIndex >= context.size()) {
            throw new ProductionFailedException();
        }
        MorphTemplate template = context.get(templateIndex);
        int leg = Math.toIntExact(createOrConsumeTemplateEvent(LEG_ID_EVENT, template::nextLeg));
        MorphLegResult result = template.produceLeg(leg, builder);
        MorphContext updated = result.templateExhausted()
                ? context.without(templateIndex)
                : context.withReplaced(templateIndex, result.updatedTemplate());
        GenerationState.setCurrentMorphContext(updated);
        return result.node();
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
