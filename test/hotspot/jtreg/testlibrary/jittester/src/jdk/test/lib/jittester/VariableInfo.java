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

import java.util.OptionalInt;
import jdk.test.lib.jittester.types.TypeArray;
import jdk.test.lib.jittester.types.TypeKlass;


public class VariableInfo extends Symbol {

    public static final int LOCAL = 0x40;
    public static final int INITIALIZED = 0x80;
    private OptionalInt arrayLength = OptionalInt.empty();

    protected VariableInfo() {
    }

    public VariableInfo(VariableInfo value) {
        super(value);
        this.arrayLength = value.arrayLength;
    }

    public VariableInfo(String name, TypeKlass owner, Type type, int flags) {
        super(name, owner, type, flags);
        initializeArrayLength(type);
    }

    public VariableInfo(TypeKlass owner, Type type) {
        super("", owner, type, Symbol.NONE);
        initializeArrayLength(type);
    }

    @Override
    protected Symbol copy() {
        return new VariableInfo(this);
    }

    @Override
    public Symbol deepCopy() {
        return new VariableInfo(this);
    }

    public boolean isLocal() {
        return (flags & LOCAL) != 0;
    }

    public OptionalInt getArrayLength() {
        return arrayLength;
    }

    public void setArrayLength(int value) {
        if (value < 1) {
            throw new IllegalArgumentException("Array length must be positive");
        }
        arrayLength = OptionalInt.of(value);
    }

    private void initializeArrayLength(Type symbolType) {
        if (!(symbolType instanceof TypeArray)) {
            return;
        }
        arrayLength = OptionalInt.of(ContainerSizePicker.pickIntContainerSize());
    }
}
