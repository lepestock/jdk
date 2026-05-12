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

import compiler.lib.generators.Generators;
import compiler.lib.generators.RestrictableGenerator;
import jdk.test.lib.jittester.Literal;
import jdk.test.lib.jittester.ProductionFailedException;
import jdk.test.lib.jittester.ProductionParams;
import jdk.test.lib.jittester.Type;
import jdk.test.lib.jittester.TypeList;
import jdk.test.lib.jittester.utils.PseudoRandom;
import jdk.test.lib.jittester.utils.PseudoRandomnessSourceAdapter;

class LiteralFactory extends Factory<Literal> {
    private static final int PRINTABLE_CHAR_MIN = 32;
    private static final int PRINTABLE_CHAR_MAX = 126;

    protected final Type resultType;

    LiteralFactory(Type resultType) {
        this.resultType = resultType;
    }

    private static Generators generators() {
        return new Generators(new PseudoRandomnessSourceAdapter());
    }

    @Override
    public Literal produce() throws ProductionFailedException {
        Literal literal;
        Generators g = generators();
        if (resultType.equals(TypeList.BOOLEAN) || isTypeName("java.lang.Boolean")) {
            literal = new Literal(PseudoRandom.randomBoolean(), resultType);
        } else if (resultType.equals(TypeList.CHAR) || isTypeName("java.lang.Character")) {
            int c = g.safeRestrict(g.ints(), PRINTABLE_CHAR_MIN, PRINTABLE_CHAR_MAX).next();
            literal = new Literal((char) c, resultType);
        } else if (resultType.equals(TypeList.INT) || isTypeName("java.lang.Integer")) {
            literal = new Literal(g.ints().next(), resultType);
        } else if (resultType.equals(TypeList.LONG) || isTypeName("java.lang.Long")) {
            literal = new Literal(g.longs().next(), resultType);
        } else if (resultType.equals(TypeList.FLOAT) || isTypeName("java.lang.Float")) {
            literal = new Literal(g.floats().next(), resultType);
        } else if (resultType.equals(TypeList.DOUBLE) || isTypeName("java.lang.Double")) {
            literal = new Literal(g.doubles().next(), resultType);
        } else if (resultType.equals(TypeList.BYTE) || isTypeName("java.lang.Byte")) {
            RestrictableGenerator<Integer> bounded = g.safeRestrict(g.ints(), Byte.MIN_VALUE, Byte.MAX_VALUE);
            literal = new Literal((byte) (int) bounded.next(), resultType);
        } else if (resultType.equals(TypeList.SHORT) || isTypeName("java.lang.Short")) {
            RestrictableGenerator<Integer> bounded = g.safeRestrict(g.ints(), Short.MIN_VALUE, Short.MAX_VALUE);
            literal = new Literal((short) (int) bounded.next(), resultType);
        } else if (resultType.equals(TypeList.STRING)) {
            int size = (int) (PseudoRandom.random() * ProductionParams.stringLiteralSizeLimit.value());
            byte[] str = new byte[size];
            for (int i = 0; i < size; i++) {
                str[i] = (byte) ((int) (('z' - 'a') * PseudoRandom.random()) + 'a');
            }
            literal = new Literal(new String(str), resultType);
        } else {
            throw new ProductionFailedException();
        }
        return literal;
    }

    private boolean isTypeName(String expectedName) {
        return resultType.getName().equals(expectedName);
    }
}
