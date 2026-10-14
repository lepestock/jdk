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

package jdk.test.lib.jittester.classes;

import java.util.ArrayList;
import java.util.List;
import jdk.test.lib.jittester.IRNode;
import jdk.test.lib.jittester.VariableInfo;
import jdk.test.lib.jittester.types.TypeKlass;

public class RecordKlass extends Klass {
    private final ArrayList<VariableInfo> components;

    public RecordKlass(TypeKlass thisKlass, ArrayList<TypeKlass> interfaces, String name, int level,
            ArrayList<VariableInfo> components, IRNode abstractFunctionRedefinitions, IRNode functionDefinitions,
            IRNode functionDeclarations, IRNode printVariablesBlock) {
        super(thisKlass, null, interfaces, name, level,
                null, null, abstractFunctionRedefinitions, null, functionDefinitions,
                functionDeclarations, printVariablesBlock);
        this.components = components;
    }

    public List<VariableInfo> getComponents() {
        return components;
    }
}
