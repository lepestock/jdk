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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import jdk.test.lib.jittester.arrays.ArrayCreation;
import jdk.test.lib.jittester.arrays.ArrayElement;
import jdk.test.lib.jittester.arrays.ArrayExtraction;
import jdk.test.lib.jittester.functions.Function;
import jdk.test.lib.jittester.visitors.JavaCodeVisitor;

/**
 * Collects expression richness statistics from generated IR.
 */
public final class ExpressionStats {
    private ExpressionStats() {
    }

    /**
     * Immutable expression record for top-richness entries.
     */
    public static final class Entry {
        private final IRNode root;
        private final long score;
        private final int nodeCount;
        private final int operatorCount;
        private final int depth;
        private final String blockGeneToken;
        private final EnumSet<OperatorKind> operatorKinds;

        private Entry(IRNode root, long score, int nodeCount, int operatorCount,
                int depth, String blockGeneToken, EnumSet<OperatorKind> operatorKinds) {
            this.root = root;
            this.score = score;
            this.nodeCount = nodeCount;
            this.operatorCount = operatorCount;
            this.depth = depth;
            this.blockGeneToken = blockGeneToken;
            this.operatorKinds = operatorKinds.clone();
        }

        public IRNode root() {
            return root;
        }

        public long score() {
            return score;
        }

        public int nodeCount() {
            return nodeCount;
        }

        public int operatorCount() {
            return operatorCount;
        }

        public int depth() {
            return depth;
        }

        public String blockGeneToken() {
            return blockGeneToken;
        }

        public EnumSet<OperatorKind> operatorKinds() {
            return operatorKinds.clone();
        }
    }

    /**
     * Aggregated expression stats snapshot.
     */
    public static final class Snapshot {
        private final int expressionCount;
        private final long totalExpressionNodes;
        private final long totalExpressionOperators;
        private final long totalExpressionDepth;
        private final int maxExpressionDepth;
        private final int maxExpressionNodes;
        private final int maxExpressionOperators;
        private final EnumSet<OperatorKind> distinctOperatorKinds;
        private final List<Entry> topEntries;

        private Snapshot(int expressionCount, long totalExpressionNodes,
                long totalExpressionOperators, long totalExpressionDepth, int maxExpressionDepth,
                int maxExpressionNodes, int maxExpressionOperators,
                EnumSet<OperatorKind> distinctOperatorKinds, List<Entry> topEntries) {
            this.expressionCount = expressionCount;
            this.totalExpressionNodes = totalExpressionNodes;
            this.totalExpressionOperators = totalExpressionOperators;
            this.totalExpressionDepth = totalExpressionDepth;
            this.maxExpressionDepth = maxExpressionDepth;
            this.maxExpressionNodes = maxExpressionNodes;
            this.maxExpressionOperators = maxExpressionOperators;
            this.distinctOperatorKinds = distinctOperatorKinds.clone();
            this.topEntries = Collections.unmodifiableList(new ArrayList<>(topEntries));
        }

        public int expressionCount() {
            return expressionCount;
        }

        public double averageExpressionNodes() {
            return expressionCount == 0 ? 0.0 : (double) totalExpressionNodes / expressionCount;
        }

        public double averageExpressionOperators() {
            return expressionCount == 0 ? 0.0 : (double) totalExpressionOperators / expressionCount;
        }

        public double averageExpressionDepth() {
            return expressionCount == 0 ? 0.0 : (double) totalExpressionDepth / expressionCount;
        }

        public int maxExpressionDepth() {
            return maxExpressionDepth;
        }

        public int maxExpressionNodes() {
            return maxExpressionNodes;
        }

        public int maxExpressionOperators() {
            return maxExpressionOperators;
        }

        public int distinctOperatorKinds() {
            return distinctOperatorKinds.size();
        }

        public List<Entry> topEntries() {
            return topEntries;
        }

        public String formatReport(int topN) {
            StringBuilder sb = new StringBuilder();
            sb.append("EXPR_STATS ")
              .append("expressions=").append(expressionCount)
              .append(" avg_nodes=").append(fmt2(averageExpressionNodes()))
              .append(" avg_ops=").append(fmt2(averageExpressionOperators()))
              .append(" avg_depth=").append(fmt2(averageExpressionDepth()))
              .append(" max_nodes=").append(maxExpressionNodes)
              .append(" max_ops=").append(maxExpressionOperators)
              .append(" max_depth=").append(maxExpressionDepth)
              .append(" op_kinds=").append(distinctOperatorKinds.size())
              .append("\n");
            appendTopEntries(sb, topN, false);
            return sb.toString();
        }

        public String formatSourceHeader(int topN) {
            StringBuilder sb = new StringBuilder();
            sb.append(" EXPRESSION_STATS ")
              .append("expressions=").append(expressionCount)
              .append(" avg_nodes=").append(fmt2(averageExpressionNodes()))
              .append(" avg_ops=").append(fmt2(averageExpressionOperators()))
              .append(" avg_depth=").append(fmt2(averageExpressionDepth()))
              .append(" max_nodes=").append(maxExpressionNodes)
              .append(" max_ops=").append(maxExpressionOperators)
              .append(" max_depth=").append(maxExpressionDepth)
              .append(" op_kinds=").append(distinctOperatorKinds.size())
              .append("\n");
            appendTopEntries(sb, topN, true);
            return sb.toString();
        }

        private void appendTopEntries(StringBuilder out, int topN, boolean sourceFooter) {
            int count = Math.max(0, Math.min(topEntries.size(), topN));
            JavaCodeVisitor vis = new JavaCodeVisitor();
            for (int i = 0; i < count; i++) {
                Entry entry = topEntries.get(i);
                String expr;
                try {
                    expr = compactExpressionText(entry.root.accept(vis));
                } catch (RuntimeException e) {
                    expr = "<expression-render-failed>";
                }
                if (sourceFooter) {
                    out.append(" TOP_").append(i + 1).append(" ")
                       .append("score=").append(entry.score)
                       .append(" nodes=").append(entry.nodeCount)
                       .append(" ops=").append(entry.operatorCount)
                       .append(" depth=").append(entry.depth)
                       .append(" gene=").append(entry.blockGeneToken)
                       .append(" expr=").append(expr)
                       .append("\n");
                } else {
                    out.append("EXPR_TOP ").append(i + 1).append(" ")
                       .append("score=").append(entry.score)
                       .append(" nodes=").append(entry.nodeCount)
                       .append(" ops=").append(entry.operatorCount)
                       .append(" depth=").append(entry.depth)
                       .append(" gene=").append(entry.blockGeneToken)
                       .append(" expr=").append(expr)
                       .append("\n");
                }
            }
        }
    }

    private static final class Mutable {
        int expressionCount;
        long totalExpressionNodes;
        long totalExpressionOperators;
        long totalExpressionDepth;
        int maxExpressionDepth;
        int maxExpressionNodes;
        int maxExpressionOperators;
        final EnumSet<OperatorKind> distinctOperatorKinds = EnumSet.noneOf(OperatorKind.class);
        final List<Entry> topEntries = new ArrayList<>();
    }

    private static final class ExpressionMetrics {
        int nodeCount;
        int operatorCount;
        int maxDepth;
        final EnumSet<OperatorKind> operatorKinds = EnumSet.noneOf(OperatorKind.class);
    }

    public static Snapshot analyze(IRNode... roots) {
        Mutable m = new Mutable();
        if (roots != null) {
            for (IRNode root : roots) {
                walk(root, m);
            }
        }
        m.topEntries.sort(Comparator.comparingLong(Entry::score).reversed());
        return new Snapshot(m.expressionCount, m.totalExpressionNodes, m.totalExpressionOperators,
                m.totalExpressionDepth, m.maxExpressionDepth, m.maxExpressionNodes,
                m.maxExpressionOperators, m.distinctOperatorKinds, m.topEntries);
    }

    private static void walk(IRNode node, Mutable m) {
        if (node == null) {
            return;
        }
        if (isExpressionRoot(node)) {
            ExpressionMetrics metrics = new ExpressionMetrics();
            collectExpressionMetrics(node, 1, metrics);
            m.expressionCount++;
            m.totalExpressionNodes += metrics.nodeCount;
            m.totalExpressionOperators += metrics.operatorCount;
            m.totalExpressionDepth += metrics.maxDepth;
            m.maxExpressionDepth = Math.max(m.maxExpressionDepth, metrics.maxDepth);
            m.maxExpressionNodes = Math.max(m.maxExpressionNodes, metrics.nodeCount);
            m.maxExpressionOperators = Math.max(m.maxExpressionOperators, metrics.operatorCount);
            m.distinctOperatorKinds.addAll(metrics.operatorKinds);
            String gene = findEnclosingBlockGeneToken(node);
            long score = score(metrics);
            Entry entry = new Entry(node, score, metrics.nodeCount, metrics.operatorCount,
                    metrics.maxDepth, gene, metrics.operatorKinds);
            m.topEntries.add(entry);
            m.topEntries.sort(Comparator.comparingLong(Entry::score).reversed());
            if (m.topEntries.size() > 3) {
                m.topEntries.remove(m.topEntries.size() - 1);
            }
        }
        for (IRNode child : node.getChildren()) {
            walk(child, m);
        }
    }

    private static long score(ExpressionMetrics metrics) {
        // Prefer operator-rich and structurally deep expressions.
        return metrics.operatorCount * 1_000_000L
                + metrics.nodeCount * 1_000L
                + metrics.maxDepth;
    }

    private static void collectExpressionMetrics(IRNode node, int depth, ExpressionMetrics out) {
        if (node == null || !isExpressionNode(node)) {
            return;
        }
        out.nodeCount++;
        out.maxDepth = Math.max(out.maxDepth, depth);
        if (node instanceof Operator) {
            out.operatorCount++;
            OperatorKind kind = ((Operator) node).getOperationKind();
            if (kind != null) {
                out.operatorKinds.add(kind);
            }
        }
        for (IRNode child : node.getChildren()) {
            if (isExpressionNode(child)) {
                collectExpressionMetrics(child, depth + 1, out);
            }
        }
    }

    private static boolean isExpressionRoot(IRNode node) {
        if (!isExpressionNode(node)) {
            return false;
        }
        IRNode parent = node.getParent();
        return parent == null || !isExpressionNode(parent);
    }

    private static boolean isExpressionNode(IRNode node) {
        if (node == null || node.getResultType().equals(TypeList.VOID)) {
            return false;
        }
        return node instanceof Operator
                || node instanceof Literal
                || node instanceof VariableBase
                || node instanceof Function
                || node instanceof CastOperator
                || node instanceof TernaryOperator
                || node instanceof ArrayCreation
                || node instanceof ArrayElement
                || node instanceof ArrayExtraction;
    }

    private static String findEnclosingBlockGeneToken(IRNode node) {
        IRNode cur = node;
        while (cur != null) {
            if (cur instanceof Block) {
                Block block = (Block) cur;
                if (block.hasBlockRngSeed() && block.getBlockGene() != null) {
                    return block.getBlockGene().toToken();
                }
            }
            cur = cur.getParent();
        }
        return "<no-gene>";
    }

    private static String compactExpressionText(String value) {
        if (value == null) {
            return "<null>";
        }
        String normalized = value
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.length() > 240) {
            return normalized.substring(0, 237) + "...";
        }
        return normalized;
    }

    private static String fmt2(double v) {
        return String.format((Locale) null, "%.2f", v);
    }
}
