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

/**
 * Compact typed magnet gene.
 *
 * The upper four bits encode the requested entity kind, and the remaining
 * sixty bits keep the ordinary magnet id used for distance scoring.
 */
public record MagnetGene(int kind, long id) {
    public static final int KIND_BITS = 4;
    public static final int ID_BITS = Long.SIZE - KIND_BITS;
    public static final int MAX_KIND = (1 << KIND_BITS) - 1;
    public static final long ID_MASK = (1L << ID_BITS) - 1;

    public static final int OTHER = 0;
    public static final int THIS = 1;
    public static final int ARGUMENT_REFERENCE = 2;
    public static final int LOCAL_REFERENCE = 3;
    public static final int FIELD_REFERENCE = 4;
    public static final int PRIMITIVE_INTEGER = 5;
    public static final int PRIMITIVE_FLOAT = 6;
    public static final int SYNTAX_CLASS = 7;
    public static final int SYNTAX_FIELD = 8;
    public static final int SYNTAX_FUNCTION = 9;

    public MagnetGene {
        if (kind < 0 || kind > MAX_KIND) {
            throw new IllegalArgumentException("Magnet kind is out of range: " + kind);
        }
        id &= ID_MASK;
    }

    public long encode() {
        return ((long) kind << ID_BITS) | (id & ID_MASK);
    }

    public static long encode(int kind, long id) {
        return new MagnetGene(kind, id).encode();
    }

    public static MagnetGene decode(long gene) {
        return new MagnetGene(kindPart(gene), idPart(gene));
    }

    public static int kindPart(long gene) {
        return (int) (gene >>> ID_BITS);
    }

    public static long idPart(long gene) {
        return gene & ID_MASK;
    }

    public static String kindName(int kind) {
        return switch (kind) {
            case THIS -> "THIS";
            case ARGUMENT_REFERENCE -> "ARGUMENT_REFERENCE";
            case LOCAL_REFERENCE -> "LOCAL_REFERENCE";
            case FIELD_REFERENCE -> "FIELD_REFERENCE";
            case PRIMITIVE_INTEGER -> "PRIMITIVE_INTEGER";
            case PRIMITIVE_FLOAT -> "PRIMITIVE_FLOAT";
            case SYNTAX_CLASS -> "SYNTAX_CLASS";
            case SYNTAX_FIELD -> "SYNTAX_FIELD";
            case SYNTAX_FUNCTION -> "SYNTAX_FUNCTION";
            default -> "OTHER";
        };
    }
}
