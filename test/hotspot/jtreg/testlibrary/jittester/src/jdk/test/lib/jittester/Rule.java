/*
 * Copyright (c) 2005, 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Iterator;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.TreeSet;
import java.util.Set;
import java.util.LinkedHashSet;
import jdk.test.lib.jittester.factories.Factory;
import jdk.test.lib.jittester.utils.Genome;
import jdk.test.lib.jittester.utils.PseudoRandom;
import java.util.stream.Collectors;

/**
 * The Rule. A helper to perform production.
 */
public class Rule<T extends IRNode> extends Factory<T> implements Comparable<Rule<T>> {
    private final String name;
    private final TreeSet<RuleEntry> variants;
    private Integer limit = -1;
    private static final Set<String> TRACE_RULE_GENES = parseTraceRuleGenes();

    @Override
    public int compareTo(Rule<T> rule) {
        return name.compareTo(rule.name);
    }

    public Rule(String name) {
        this.name = name;
        variants = new TreeSet<>();
    }

    @Override
    public String toString() {
        return "(Rule :name " + name +
            ":variants (list" +
                variants.stream()
                        .map(entry -> "(rule-entry :name " + entry.name + " :weight " + entry.weight + ")")
                        .collect(Collectors.joining(" "))
            + ")" +
          ")";
    }

    public void add(String ruleName, Factory<? extends T> factory) {
        add(ruleName, factory, 1.0);
    }

    public void add(String ruleName, Factory<? extends T> factory, double weight) {
        variants.add(new RuleEntry(ruleName, factory, weight));
    }

    public int size() {
        return variants.size();
    }

    @Override
    public T produce() throws ProductionFailedException {
        if (!variants.isEmpty()) {
            // Rule identity gene (R): unique long marker for this rule decision site.
            // It is recorded once per rule invocation and is not speculative.
            long ruleGene = Genome.createOrConsumeRuleGene(name);
            boolean trace = isTraceRuleGene(ruleGene);

            // Begin production.
            ArrayList<RuleEntry> originals = new ArrayList<>(variants);
            LinkedList<IndexedRuleEntry> rulesList = new LinkedList<>();
            for (int i = 0; i < originals.size(); i++) {
                rulesList.add(new IndexedRuleEntry(i, originals.get(i)));
            }
            if (trace) {
                traceRule("enter", ruleGene, rulesList, null);
            }

            if (Genome.isReplayActive()) {
                Long replayChoice = Genome.consumeChoiceGene(name, -1L);
                if (trace) {
                    traceRule("replay-choice", ruleGene, rulesList, replayChoice);
                }
                if (replayChoice == null) {
                    throw new RuntimeException("Genome replay desync: missing choice event for rule '"
                            + name + "'");
                }
                if (replayChoice == -1L) {
                    // Explicit failure marker: this rule invocation failed in record mode.
                    throw new ProductionFailedException();
                }
                IndexedRuleEntry selected = removeByOriginalIndex(rulesList, replayChoice);
                if (selected == null) {
                    throw new RuntimeException("Genome replay desync: rule '" + name
                            + "' requested missing variant index " + replayChoice);
                }
                GenerationState.Checkpoint stateCheckpoint = GenerationState.checkpoint();
                SymbolTable.push();
                try {
                    T produced = selected.entry.produce();
                    SymbolTable.merge();
                    if (trace) {
                        traceRule("replay-success", ruleGene, rulesList, replayChoice);
                    }
                    return produced;
                } catch (ProductionFailedException e) {
                    if (e instanceof MutationScopeProductionFailedException
                            && !Genome.isReplayMutationScopeActive()) {
                        throw new RuntimeException("Failed to mutate: failure escaped mutable scope in rule '"
                                + name + "'", e);
                    }
                    GenerationState.rollbackTo(stateCheckpoint);
                    if (trace) {
                        traceRule("replay-failed", ruleGene, rulesList, replayChoice);
                    }
                    throw e;
                } catch (RuntimeException e) {
                    GenerationState.rollbackTo(stateCheckpoint);
                    throw e;
                }
            }
            PseudoRandom.shuffleSilent(rulesList);

            while (!rulesList.isEmpty() && (limit == -1 || limit > 0)) {
                long selectedOriginalIndex = pickWeightedOriginalIndex(rulesList);
                IndexedRuleEntry selected = removeByOriginalIndex(rulesList, selectedOriginalIndex);
                if (selected == null) {
                    throw new RuntimeException("Rule '" + name
                            + "' selected missing variant index " + selectedOriginalIndex);
                }
                GenerationState.Checkpoint stateCheckpoint = GenerationState.checkpoint();
                SymbolTable.push();
                Genome.beginSpeculativeRecord();
                try {
                    Genome.recordChoiceGene(name, selected.originalIndex);
                    if (trace) {
                        traceRule("record-choice", ruleGene, rulesList, (long) selected.originalIndex);
                    }
                    T produced = selected.entry.produce();
                    Genome.commitSpeculativeRecord();
                    SymbolTable.merge();
                    if (trace) {
                        traceRule("record-success", ruleGene, rulesList, (long) selected.originalIndex);
                    }
                    return produced;
                } catch (ProductionFailedException e) {
                    if (e instanceof MutationScopeProductionFailedException
                            && !Genome.isReplayMutationScopeActive()) {
                        throw new RuntimeException("Failed to mutate: failure escaped mutable scope in rule '"
                                + name + "'", e);
                    }
                    Genome.rollbackSpeculativeRecord();
                    GenerationState.rollbackTo(stateCheckpoint);
                    if (trace) {
                        traceRule("record-failed", ruleGene, rulesList, (long) selected.originalIndex);
                    }
                } catch (RuntimeException e) {
                    Genome.rollbackSpeculativeRecord();
                    GenerationState.rollbackTo(stateCheckpoint);
                    throw e;
                }
                if (limit != -1) {
                    limit--;
                }
            }
            // Explicitly record a failed rule invocation so every R has a corresponding C.
            Genome.recordChoiceGene(name, -1L);
            if (Genome.isReplayMutationScopeActive()) {
                throw new MutationScopeProductionFailedException();
            }
            if (trace) {
                traceRule("record-terminal-fail", ruleGene, rulesList, -1L);
            }
        }
        // should probably throw exception here..
        //return getChildren().size() > 0 ? getChild(0).produce() : null;
        throw new ProductionFailedException();
    }

    private static Set<String> parseTraceRuleGenes() {
        String raw = System.getProperty("jittester.debug.rule.trace.genes");
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String part : raw.split("[,\\s]+")) {
            String t = part == null ? "" : part.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    private static boolean isTraceRuleGene(long ruleGene) {
        if (TRACE_RULE_GENES.isEmpty()) {
            return false;
        }
        String raw = Long.toString(ruleGene);
        return TRACE_RULE_GENES.contains(raw) || TRACE_RULE_GENES.contains("R" + raw);
    }

    private void traceRule(String phase, long ruleGene, LinkedList<IndexedRuleEntry> rulesList, Long choice) {
        String variantsDump = rulesList.stream()
                .map(r -> r.originalIndex + ":" + r.entry.name + ":" + r.entry.weight)
                .collect(Collectors.joining(", "));
        System.err.println("[JTDBG][Rule] phase=" + phase
                + " rule=" + name
                + " gene=R" + ruleGene
                + " replay=" + Genome.isReplayActive()
                + " choice=" + (choice == null ? "<null>" : choice)
                + " variants=[" + variantsDump + "]");
    }

    private long pickWeightedOriginalIndex(LinkedList<IndexedRuleEntry> rulesList) {
        double sum = rulesList.stream().mapToDouble(r -> r.entry.weight).sum();
        double rnd = PseudoRandom.randomSilent() * sum;
        double weightAccumulator = 0;
        IndexedRuleEntry selected = null;
        Iterator<IndexedRuleEntry> iterator = rulesList.iterator();
        while (iterator.hasNext()) {
            IndexedRuleEntry candidate = iterator.next();
            selected = candidate;
            weightAccumulator += candidate.entry.weight;
            if (weightAccumulator >= rnd) {
                break;
            }
        }
        if (selected == null) {
            throw new RuntimeException("Rule '" + name + "' has no selectable variants");
        }
        return selected.originalIndex;
    }

    private IndexedRuleEntry removeByOriginalIndex(LinkedList<IndexedRuleEntry> rulesList, long index) {
        Iterator<IndexedRuleEntry> it = rulesList.iterator();
        while (it.hasNext()) {
            IndexedRuleEntry candidate = it.next();
            if (candidate.originalIndex == index) {
                it.remove();
                return candidate;
            }
        }
        return null;
    }

    private final class IndexedRuleEntry {
        final int originalIndex;
        final RuleEntry entry;

        IndexedRuleEntry(int originalIndex, RuleEntry entry) {
            this.originalIndex = originalIndex;
            this.entry = entry;
        }
    }

    private class RuleEntry extends Factory<T> implements Comparable<RuleEntry> {
        private final double weight;
        private final Factory<? extends T> factory;
        private final String name;

        private RuleEntry(String name, Factory<? extends T> factory, double weight) {
            this.name = name;
            this.weight = weight;
            this.factory = factory;
        }

        @Override
        public T produce() throws ProductionFailedException {
            return factory.produce();
        }

        @Override
        public int compareTo(RuleEntry entry) {
            return name.compareTo(entry.name);
        }
    }
}
