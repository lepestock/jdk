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
import jdk.test.lib.jittester.BinaryOperator;
import jdk.test.lib.jittester.FlowParams;
import jdk.test.lib.jittester.GenerationState;
import jdk.test.lib.jittester.IRNode;
import jdk.test.lib.jittester.Literal;
import jdk.test.lib.jittester.LocalVariable;
import jdk.test.lib.jittester.OperatorKind;
import jdk.test.lib.jittester.ProductionFailedException;
import jdk.test.lib.jittester.Rule;
import jdk.test.lib.jittester.Symbol;
import jdk.test.lib.jittester.SymbolTable;
import jdk.test.lib.jittester.Type;
import jdk.test.lib.jittester.TypeList;
import jdk.test.lib.jittester.VariableInfo;
import jdk.test.lib.jittester.types.TypeArray;
import jdk.test.lib.jittester.utils.PseudoRandom;

/**
 * Produces expressions that are stable by construction.
 *
 * <p>The initial scope is intentionally small: a fuzzy literal, or an exact
 * integer identity wrapped around a local scalar that is not an active iteration
 * variable. Integer identities rely on Java's defined two's-complement
 * wraparound for int/long arithmetic.</p>
 */
class StableExpressionFactory extends SafeFactory<IRNode> {
    private static final int MAX_STABLE_DEPTH = 3;
    private final Type resultType;
    private final int depth;

    StableExpressionFactory(Type resultType) {
        this(resultType, 0);
    }

    private StableExpressionFactory(Type resultType, int depth) {
        this.resultType = resultType;
        this.depth = depth;
    }

    @Override
    protected IRNode sproduce() throws ProductionFailedException {
        Rule<IRNode> rule = new Rule<>("stable_expression");
        rule.add("literal", new LiteralFactory(resultType), literalWeight());
        if (depth < MAX_STABLE_DEPTH && supportsExactIntegerIdentities(resultType)) {
            rule.add("cancel_sub_add", new Factory<>() {
                @Override
                public IRNode produce() throws ProductionFailedException {
                    return produceCancelSubAdd();
                }
            }, 2.0);
            rule.add("literal_add_cancel", new Factory<>() {
                @Override
                public IRNode produce() throws ProductionFailedException {
                    return produceLiteralAddCancel();
                }
            }, 1.0);
            rule.add("mul_zero_add", new Factory<>() {
                @Override
                public IRNode produce() throws ProductionFailedException {
                    return produceMulZeroAdd();
                }
            }, 1.0);
            rule.add("literal_sub_mul_zero", new Factory<>() {
                @Override
                public IRNode produce() throws ProductionFailedException {
                    return produceLiteralSubMulZero();
                }
            }, 1.0);
        }
        return rule.produce();
    }

    private IRNode produceCancelSubAdd() throws ProductionFailedException {
        IRNode expression = subExpression();
        IRNode difference = new BinaryOperator(OperatorKind.SUB, resultType,
                expression, copy(expression));
        return new BinaryOperator(OperatorKind.ADD, resultType, difference, literal());
    }

    private IRNode produceLiteralAddCancel() throws ProductionFailedException {
        IRNode expression = subExpression();
        IRNode sum = new BinaryOperator(OperatorKind.ADD, resultType, literal(), expression);
        return new BinaryOperator(OperatorKind.SUB, resultType, sum, copy(expression));
    }

    private IRNode produceMulZeroAdd() throws ProductionFailedException {
        IRNode expression = subExpression();
        IRNode product = new BinaryOperator(OperatorKind.MUL, resultType,
                expression, zeroLiteral());
        return new BinaryOperator(OperatorKind.ADD, resultType, product, literal());
    }

    private IRNode produceLiteralSubMulZero() throws ProductionFailedException {
        IRNode expression = subExpression();
        IRNode product = new BinaryOperator(OperatorKind.MUL, resultType,
                expression, zeroLiteral());
        return new BinaryOperator(OperatorKind.SUB, resultType, literal(), product);
    }

    private IRNode subExpression() throws ProductionFailedException {
        Rule<IRNode> rule = new Rule<>("stable_expression_basis");
        rule.add("stable", new StableExpressionFactory(resultType, depth + 1), 6.0 + 8.0 * depth);
        if (hasLocalScalarCandidates(resultType)) {
            rule.add("local_scalar", new Factory<>() {
                @Override
                public IRNode produce() throws ProductionFailedException {
                    return localScalar();
                }
            }, 1.0);
        }
        return rule.produce();
    }

    private LocalVariable localScalar() throws ProductionFailedException {
        ArrayList<VariableInfo> candidates = localScalarCandidates(resultType);
        if (candidates.isEmpty()) {
            throw new ProductionFailedException();
        }
        return new LocalVariable(candidates.get(PseudoRandom.randomNotNegative(candidates.size())));
    }

    private static IRNode copy(IRNode node) throws ProductionFailedException {
        if (node instanceof Literal literal) {
            return new Literal(literal.getValue(), literal.getResultType());
        }
        if (node instanceof LocalVariable local) {
            return new LocalVariable(local.getVariableInfo());
        }
        if (node instanceof BinaryOperator binary) {
            return new BinaryOperator(binary.getOperationKind(), binary.getResultType(),
                    copy(binary.getChild(0)), copy(binary.getChild(1)));
        }
        throw new ProductionFailedException();
    }

    private Literal literal() throws ProductionFailedException {
        return new LiteralFactory(resultType).produce();
    }

    private Literal zeroLiteral() {
        if (resultType.equals(TypeList.LONG)) {
            return new Literal(0L, resultType);
        }
        return new Literal(0, resultType);
    }

    private static boolean supportsExactIntegerIdentities(Type type) {
        return type.equals(TypeList.INT) || type.equals(TypeList.LONG);
    }

    private double literalWeight() {
        return 16.0 * (depth + 1) * (depth + 1);
    }

    private static boolean hasLocalScalarCandidates(Type type) {
        return !localScalarCandidates(type).isEmpty();
    }

    private static ArrayList<VariableInfo> localScalarCandidates(Type type) {
        ArrayList<VariableInfo> result = new ArrayList<>();
        FlowParams flowParams = GenerationState.currentFlowParams();
        for (Symbol symbol : SymbolTable.get(type, VariableInfo.class)) {
            if (!(symbol instanceof VariableInfo varInfo)) {
                continue;
            }
            if (!varInfo.isLocal()
                    || (varInfo.flags & VariableInfo.INITIALIZED) == 0
                    || varInfo.type instanceof TypeArray
                    || flowParams.isIterationVariable(varInfo.name)) {
                continue;
            }
            result.add(varInfo);
        }
        return result;
    }
}
