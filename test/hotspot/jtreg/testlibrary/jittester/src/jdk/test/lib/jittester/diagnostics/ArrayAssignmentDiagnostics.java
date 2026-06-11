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

package jdk.test.lib.jittester.diagnostics;

import java.util.ArrayList;
import java.util.List;
import jdk.test.lib.jittester.CastOperator;
import jdk.test.lib.jittester.GenerationState;
import jdk.test.lib.jittester.IRNode;
import jdk.test.lib.jittester.OperatorKind;
import jdk.test.lib.jittester.ProductionParams;
import jdk.test.lib.jittester.Symbol;
import jdk.test.lib.jittester.SymbolTable;
import jdk.test.lib.jittester.Type;
import jdk.test.lib.jittester.VariableBase;
import jdk.test.lib.jittester.VariableInfo;
import jdk.test.lib.jittester.arrays.ArrayElement;
import jdk.test.lib.jittester.types.TypeArray;
import jdk.test.lib.jittester.utils.TypeBoxingUtil;

public final class ArrayAssignmentDiagnostics {
    private static final Snapshot DISABLED = new Snapshot(false, null, null,
            List.of(), List.of(), List.of(), List.of(), List.of());

    public Snapshot snapshot(Type leftElementType, Type rightElementType) {
        if (!ProductionParams.debugArrayAssignmentCandidates.value()
                || !GenerationState.currentFlowParams().inArrayKernel()) {
            return DISABLED;
        }
        List<VariableInfo> arrays = visibleInitializedArrays();
        return new Snapshot(true, leftElementType, rightElementType,
                filterOneDimArraysByElementType(arrays, leftElementType),
                filterOneDimArraysByElementType(arrays, rightElementType),
                filterOneDimArraysAssignableTo(arrays, rightElementType),
                filterOneDimArraysAssignableTo(arrays, leftElementType),
                arrays);
    }

    public void attach(Snapshot snapshot, IRNode assignmentNode, OperatorKind opKind,
            IRNode leftOperand, IRNode rightOperand) {
        if (snapshot == null || !snapshot.enabled || !(leftOperand instanceof ArrayElement)) {
            return;
        }
        SourceDiagnostics.attach(assignmentNode, snapshot.render(opKind, leftOperand, rightOperand));
    }

    private static List<VariableInfo> visibleInitializedArrays() {
        ArrayList<VariableInfo> arrays = new ArrayList<>();
        for (Symbol symbol : SymbolTable.getAllCombined(VariableInfo.class)) {
            if (!(symbol instanceof VariableInfo varInfo)) {
                continue;
            }
            if (!(varInfo.type instanceof TypeArray)) {
                continue;
            }
            if ((varInfo.flags & VariableInfo.INITIALIZED) == 0) {
                continue;
            }
            if (varInfo.getArrayLength().isEmpty()) {
                continue;
            }
            arrays.add(varInfo);
        }
        return arrays;
    }

    private static List<VariableInfo> filterOneDimArraysByElementType(List<VariableInfo> arrays,
            Type elementType) {
        ArrayList<VariableInfo> result = new ArrayList<>();
        for (VariableInfo arrayInfo : arrays) {
            if (!(arrayInfo.type instanceof TypeArray arrayType)) {
                continue;
            }
            if (arrayType.dimensions == 1 && arrayType.type.equals(elementType)) {
                result.add(arrayInfo);
            }
        }
        return result;
    }

    private static List<VariableInfo> filterOneDimArraysAssignableTo(List<VariableInfo> arrays,
            Type targetElementType) {
        ArrayList<VariableInfo> result = new ArrayList<>();
        for (VariableInfo arrayInfo : arrays) {
            if (!(arrayInfo.type instanceof TypeArray arrayType)) {
                continue;
            }
            if (arrayType.dimensions == 1
                    && TypeBoxingUtil.isAssignmentCompatibleWithBoxing(arrayType.type,
                            targetElementType)) {
                result.add(arrayInfo);
            }
        }
        return result;
    }

    private static VariableInfo selectedArrayInfo(IRNode node) {
        if (node instanceof CastOperator castOperator) {
            node = castOperator.getChild(0);
        }
        if (!(node instanceof ArrayElement arrayElement)) {
            return null;
        }
        IRNode arrayNode = arrayElement.getChild(0);
        if (arrayNode instanceof VariableBase variableBase) {
            return variableBase.getVariableInfo();
        }
        return null;
    }

    private static String formatArrayInfo(VariableInfo info) {
        StringBuilder sb = new StringBuilder();
        sb.append(safeName(info.name));
        sb.append(":type=").append(typeName(info.type));
        sb.append(":scope=");
        if (info.isLocal()) {
            sb.append("local");
        } else if (info.isStatic()) {
            sb.append("static");
        } else {
            sb.append("field");
        }
        info.getArrayLength().ifPresent(length -> sb.append(":length=").append(length));
        return sb.toString();
    }

    private static String typeName(Type type) {
        return type == null ? "null" : type.getName().replace(' ', '_');
    }

    private static String safeName(String name) {
        return name == null || name.isBlank() ? "<anonymous>" : name.replace(' ', '_');
    }

    public static final class Snapshot {
        private final boolean enabled;
        private final Type leftElementType;
        private final Type rightElementType;
        private final List<VariableInfo> lhsArrays;
        private final List<VariableInfo> rhsArrays;
        private final List<VariableInfo> rhsExpressionCompatibleArrays;
        private final List<VariableInfo> rhsAssignmentCompatibleArrays;
        private final List<VariableInfo> allArrays;

        private Snapshot(boolean enabled, Type leftElementType, Type rightElementType,
                List<VariableInfo> lhsArrays, List<VariableInfo> rhsArrays,
                List<VariableInfo> rhsExpressionCompatibleArrays,
                List<VariableInfo> rhsAssignmentCompatibleArrays,
                List<VariableInfo> allArrays) {
            this.enabled = enabled;
            this.leftElementType = leftElementType;
            this.rightElementType = rightElementType;
            this.lhsArrays = lhsArrays;
            this.rhsArrays = rhsArrays;
            this.rhsExpressionCompatibleArrays = rhsExpressionCompatibleArrays;
            this.rhsAssignmentCompatibleArrays = rhsAssignmentCompatibleArrays;
            this.allArrays = allArrays;
        }

        private String render(OperatorKind opKind, IRNode leftOperand, IRNode rightOperand) {
            int max = Math.max(1, ProductionParams.debugArrayAssignmentCandidatesMax.value());
            StringBuilder sb = new StringBuilder("(array-assignment");
            sb.append(" :op ").append(opKind.name());
            sb.append(" :lhs-element-type ").append(typeName(leftElementType));
            sb.append(" :rhs-type ").append(typeName(rightElementType));
            appendSelectedArray(sb, "lhs-array", selectedArrayInfo(leftOperand));
            appendSelectedArray(sb, "rhs-array", selectedArrayInfo(rightOperand));
            appendCandidateList(sb, "lhs-array-candidates", lhsArrays, max);
            appendCandidateList(sb, "rhs-array-candidates", rhsArrays, max);
            appendCandidateList(sb, "rhs-expression-compatible-array-candidates",
                    rhsExpressionCompatibleArrays, max);
            appendCandidateList(sb, "rhs-assignment-compatible-array-candidates",
                    rhsAssignmentCompatibleArrays, max);
            appendCandidateList(sb, "available-arrays-with-their-types", allArrays, max);
            sb.append(")");
            return sb.toString();
        }

        private static void appendSelectedArray(StringBuilder sb, String label, VariableInfo info) {
            sb.append(" :selected-").append(label).append(" ");
            sb.append(info == null ? "none" : formatArrayInfo(info));
        }

        private static void appendCandidateList(StringBuilder sb, String label,
                List<VariableInfo> candidates, int max) {
            sb.append(" :").append(label).append("-count ").append(candidates.size());
            sb.append(" :").append(label).append(" (");
            int limit = Math.min(max, candidates.size());
            for (int i = 0; i < limit; i++) {
                if (i > 0) {
                    sb.append(' ');
                }
                sb.append(formatArrayInfo(candidates.get(i)));
            }
            if (candidates.size() > limit) {
                if (limit > 0) {
                    sb.append(' ');
                }
                sb.append("...");
            }
            sb.append(')');
        }
    }
}
