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

package jdk.test.lib.jittester.utils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import jdk.test.lib.jittester.ProductionParams;
import jdk.test.lib.jittester.Symbol;
import jdk.test.lib.jittester.Type;
import jdk.test.lib.jittester.TypeList;
import jdk.test.lib.jittester.VariableInfo;
import jdk.test.lib.jittester.functions.FunctionInfo;
import jdk.test.lib.jittester.types.TypeArray;
import jdk.test.lib.jittester.types.TypeKlass;

/**
 * Generic helpers for magnet-biased symbol selection.
 */
public final class SymbolMagnetism {
    private static final double DISTANCE_DECAY = 0.125;
    private static final double EXACT_MATCH_WEIGHT = 1_000_000.0;
    private static final double MIN_WEIGHT = 1e-15;

    private SymbolMagnetism() {
    }

    private static boolean isStrictExactMode() {
        return ProductionParams.magnetismLevel != null
                && ProductionParams.magnetismLevel.value() <= 0;
    }

    public static long unsignedDistance(long a, long b) {
        return Long.compareUnsigned(a, b) >= 0 ? a - b : b - a;
    }

    public static double magnetWeight(long candidateGeneId, long targetGeneId) {
        long distance = unsignedDistance(candidateGeneId, targetGeneId);
        if (distance == 0L) {
            return EXACT_MATCH_WEIGHT;
        }
        int bucket = 64 - Long.numberOfLeadingZeros(distance);
        double weight = Math.pow(DISTANCE_DECAY, bucket);
        return Math.max(MIN_WEIGHT, weight);
    }

    public static <T extends Symbol> int pickMagneticIndex(List<T> candidates, long targetGeneId) {
        if (candidates.isEmpty()) {
            return -1;
        }
        if (isStrictExactMode()) {
            for (int i = 0; i < candidates.size(); i++) {
                if (candidates.get(i).getMagnetismGeneId() == targetGeneId) {
                    return i;
                }
            }
            // Deterministic fallback in strict mode: no stochastic draw.
            return 0;
        }
        double totalWeight = 0.0;
        for (T candidate : candidates) {
            totalWeight += magnetWeight(candidate.getMagnetismGeneId(), targetGeneId);
        }
        if (totalWeight <= 0.0) {
            return PseudoRandom.randomNotNegative(candidates.size());
        }
        double draw = PseudoRandom.random() * totalWeight;
        double prefix = 0.0;
        for (int i = 0; i < candidates.size(); i++) {
            prefix += magnetWeight(candidates.get(i).getMagnetismGeneId(), targetGeneId);
            if (draw <= prefix) {
                return i;
            }
        }
        return candidates.size() - 1;
    }

    public static <T extends Symbol> List<T> orderByMagneticPreference(List<T> candidates, long targetGeneId) {
        if (isStrictExactMode()) {
            ArrayList<T> ordered = new ArrayList<>(candidates);
            ordered.sort(new Comparator<T>() {
                @Override
                public int compare(T left, T right) {
                    long l = left.getMagnetismGeneId();
                    long r = right.getMagnetismGeneId();
                    boolean le = l == targetGeneId;
                    boolean re = r == targetGeneId;
                    if (le != re) {
                        return le ? -1 : 1;
                    }
                    long ld = unsignedDistance(l, targetGeneId);
                    long rd = unsignedDistance(r, targetGeneId);
                    int byDist = Long.compareUnsigned(ld, rd);
                    if (byDist != 0) {
                        return byDist;
                    }
                    return Long.compareUnsigned(l, r);
                }
            });
            return ordered;
        }
        ArrayList<T> remaining = new ArrayList<>(candidates);
        ArrayList<T> ordered = new ArrayList<>(candidates.size());
        while (!remaining.isEmpty()) {
            int picked = pickMagneticIndex(remaining, targetGeneId);
            if (picked < 0) {
                break;
            }
            ordered.add(remaining.remove(picked));
        }
        return ordered;
    }

    public static int kindOf(Symbol symbol) {
        if (symbol instanceof FunctionInfo) {
            return MagnetGene.SYNTAX_FUNCTION;
        }
        if (!(symbol instanceof VariableInfo variableInfo)) {
            return MagnetGene.OTHER;
        }
        Type type = variableInfo.type;
        if ("this".equals(variableInfo.name)) {
            return MagnetGene.THIS;
        }
        if (isArgumentName(variableInfo.name) && isReference(type)) {
            return MagnetGene.ARGUMENT_REFERENCE;
        }
        if (isReference(type)) {
            return variableInfo.isLocal()
                    ? MagnetGene.LOCAL_REFERENCE
                    : MagnetGene.FIELD_REFERENCE;
        }
        if (TypeList.isBuiltInInt(type)) {
            return MagnetGene.PRIMITIVE_INTEGER;
        }
        if (TypeList.FLOAT.equals(type) || TypeList.DOUBLE.equals(type)) {
            return MagnetGene.PRIMITIVE_FLOAT;
        }
        return MagnetGene.OTHER;
    }

    public static <T extends Symbol> List<T> orderByKindedMagneticPreference(List<T> candidates, long targetGene) {
        MagnetGene target = MagnetGene.decode(targetGene);
        if (isStrictExactMode()) {
            ArrayList<T> ordered = new ArrayList<>(candidates);
            ordered.sort(new Comparator<T>() {
                @Override
                public int compare(T left, T right) {
                    double lk = kindAffinity(target.kind(), kindOf(left));
                    double rk = kindAffinity(target.kind(), kindOf(right));
                    int byKind = Double.compare(rk, lk);
                    if (byKind != 0) {
                        return byKind;
                    }
                    long l = MagnetGene.idPart(left.getMagnetismGeneId());
                    long r = MagnetGene.idPart(right.getMagnetismGeneId());
                    long ld = unsignedDistance(l, target.id());
                    long rd = unsignedDistance(r, target.id());
                    int byDist = Long.compareUnsigned(ld, rd);
                    if (byDist != 0) {
                        return byDist;
                    }
                    return Long.compareUnsigned(l, r);
                }
            });
            return ordered;
        }
        ArrayList<T> remaining = new ArrayList<>(candidates);
        ArrayList<T> ordered = new ArrayList<>(candidates.size());
        while (!remaining.isEmpty()) {
            int picked = pickKindedMagneticIndex(remaining, target);
            if (picked < 0) {
                break;
            }
            ordered.add(remaining.remove(picked));
        }
        return ordered;
    }

    private static <T extends Symbol> int pickKindedMagneticIndex(List<T> candidates, MagnetGene target) {
        double totalWeight = 0.0;
        for (T candidate : candidates) {
            totalWeight += kindedWeight(candidate, target);
        }
        if (totalWeight <= 0.0) {
            return PseudoRandom.randomNotNegative(candidates.size());
        }
        double draw = PseudoRandom.random() * totalWeight;
        double prefix = 0.0;
        for (int i = 0; i < candidates.size(); i++) {
            prefix += kindedWeight(candidates.get(i), target);
            if (draw <= prefix) {
                return i;
            }
        }
        return candidates.size() - 1;
    }

    private static double kindedWeight(Symbol candidate, MagnetGene target) {
        return kindAffinity(target.kind(), kindOf(candidate))
                * magnetWeight(MagnetGene.idPart(candidate.getMagnetismGeneId()), target.id());
    }

    private static double kindAffinity(int requestedKind, int candidateKind) {
        if (requestedKind == candidateKind) {
            return 1.0;
        }
        return switch (requestedKind) {
            case MagnetGene.LOCAL_REFERENCE -> switch (candidateKind) {
                case MagnetGene.ARGUMENT_REFERENCE -> 0.15;
                case MagnetGene.THIS -> 0.05;
                case MagnetGene.FIELD_REFERENCE -> 0.03;
                default -> 0.0;
            };
            case MagnetGene.ARGUMENT_REFERENCE -> switch (candidateKind) {
                case MagnetGene.LOCAL_REFERENCE -> 0.20;
                case MagnetGene.THIS -> 0.08;
                case MagnetGene.FIELD_REFERENCE -> 0.03;
                default -> 0.0;
            };
            case MagnetGene.FIELD_REFERENCE -> switch (candidateKind) {
                case MagnetGene.LOCAL_REFERENCE -> 0.12;
                case MagnetGene.ARGUMENT_REFERENCE -> 0.08;
                case MagnetGene.THIS -> 0.03;
                default -> 0.0;
            };
            case MagnetGene.PRIMITIVE_INTEGER -> candidateKind == MagnetGene.PRIMITIVE_FLOAT ? 0.10 : 0.0;
            case MagnetGene.PRIMITIVE_FLOAT -> candidateKind == MagnetGene.PRIMITIVE_INTEGER ? 0.10 : 0.0;
            default -> candidateKind == MagnetGene.OTHER ? 0.01 : 0.0;
        };
    }

    private static boolean isReference(Type type) {
        return type instanceof TypeArray || type instanceof TypeKlass;
    }

    private static boolean isArgumentName(String name) {
        if (name == null || !name.startsWith("arg")) {
            return false;
        }
        if (name.startsWith("arg_")) {
            return true;
        }
        for (int i = 3; i < name.length(); i++) {
            if (!Character.isDigit(name.charAt(i))) {
                return false;
            }
        }
        return name.length() > 3;
    }
}
