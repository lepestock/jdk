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
import java.util.HashSet;
import java.util.Set;
import jdk.test.lib.jittester.IRNode;
import jdk.test.lib.jittester.ProductionParams;
import jdk.test.lib.jittester.Symbol;
import jdk.test.lib.jittester.SymbolTable;
import jdk.test.lib.jittester.Type;
import jdk.test.lib.jittester.TypeList;
import jdk.test.lib.jittester.VariableDeclarationBlock;
import jdk.test.lib.jittester.VariableInfo;
import jdk.test.lib.jittester.functions.FunctionInfo;
import jdk.test.lib.jittester.types.TypeKlass;
import jdk.test.lib.jittester.utils.PseudoRandom;

public class InheritedFunctionMagnetTests {
    public static void main(String[] args) throws Exception {
        ProductionParams.initializeFromCmdline(new String[] {
                "--disable-external-symbols", "--disable-inheritance=false",
                "--disable-functions", "--implementation-limit", "2",
                "--lock-elimination-morph-template-probability", "0", "--seed", "12345"});
        SymbolTable.removeAll();
        TypeList.removeAll();
        testClassInheritance();
        testInterfaceInheritance();
        System.out.println("InheritedFunctionMagnetTests passed");
    }

    private static void testClassInheritance() throws Exception {
        TypeKlass parent = new TypeKlass("Test_MagnetParent");
        TypeList.add(parent);
        FunctionInfo instance = addFunction(parent, "instanceMethod", false);
        FunctionInfo statik = addFunction(parent, "staticMethod", true);
        SymbolTable.add(new FunctionInfo(parent.getName(), parent, parent, 0, Symbol.PUBLIC));
        FunctionInfo hidden = addFunction(parent, "privateMethod", false);
        hidden.flags = Symbol.PRIVATE;
        Set<Long> ids = new HashSet<>(Set.of(instance.getMagnetismGeneId(), statik.getMagnetismGeneId()));
        for (int i = 0; i < 2; i++) {
            TypeKlass child = new InheritingFactory("Test_MagnetChild" + i, parent)
                    .produce().getThisKlass();
            for (FunctionInfo original : new FunctionInfo[] {instance, statik}) {
                FunctionInfo inherited = find(child, original.name);
                check(inherited != null, "Missing inherited function " + original.name);
                check(ids.add(inherited.getMagnetismGeneId()),
                        "Inherited function reused a parent/sibling magnet: " + child + "." + original.name);
                checkCopy(original, inherited, child);
                checkScopeCopies(inherited);
            }
            check(find(child, hidden.name) == null, "Inherited a private function");
            check(find(child, parent.getName()) == null, "Inherited a constructor");
        }
        check(instance.argTypes.getFirst().type.equals(parent), "Changed the parent's receiver");
    }

    private static void testInterfaceInheritance() throws Exception {
        TypeKlass iface = new TypeKlass("Test_MagnetInterface", TypeKlass.INTERFACE);
        TypeList.add(iface);
        FunctionInfo original = addFunction(iface, "defaultMethod", false);
        original.flags = Symbol.PUBLIC;
        Set<Long> ids = new HashSet<>(Set.of(original.getMagnetismGeneId()));
        int implementations = 0;
        for (int seed = 0; seed < 20 && implementations < 2; seed++) {
            PseudoRandom.setCurrentSeed(seed);
            TypeKlass child = new InheritingFactory("Test_MagnetImplementation" + seed, TypeList.OBJECT)
                    .produce().getThisKlass();
            FunctionInfo inherited = find(child, original.name);
            if (inherited == null) {
                continue;
            }
            implementations++;
            check(ids.add(inherited.getMagnetismGeneId()), "Interface function reused a magnet");
            checkCopy(original, inherited, child);
            checkScopeCopies(inherited);
        }
        check(implementations == 2, "Did not exercise interface inheritance twice");
        check(original.argTypes.getFirst().type.equals(iface), "Changed the interface's receiver");
    }

    private static FunctionInfo addFunction(TypeKlass owner, String name, boolean isStatic) {
        VariableInfo[] args = isStatic ? new VariableInfo[0] : new VariableInfo[] {
                new VariableInfo("this", owner, owner, VariableInfo.LOCAL | VariableInfo.INITIALIZED)};
        FunctionInfo function = new FunctionInfo(name, owner, TypeList.VOID, 7,
                Symbol.PUBLIC | Symbol.FINAL | (isStatic ? Symbol.STATIC : 0), args);
        function.generatedRank = 3;
        SymbolTable.add(function);
        return function;
    }

    private static void checkCopy(FunctionInfo original, FunctionInfo inherited, TypeKlass owner) {
        check(inherited.hasMagnetismGeneId(), "Missing inherited magnet");
        check(inherited.owner.equals(owner), "Incorrect inherited owner");
        check(inherited.flags == original.flags && inherited.complexity == original.complexity
                && inherited.generatedRank == original.generatedRank, "Lost function metadata");
        check(inherited.argTypes.size() == original.argTypes.size(), "Changed function arity");
        if (!inherited.isStatic()) {
            check(inherited.argTypes.getFirst().type.equals(owner), "Incorrect inherited receiver");
        }
    }

    private static void checkScopeCopies(FunctionInfo function) {
        long id = function.getMagnetismGeneId();
        check(function.deepCopy().getMagnetismGeneId() == id, "Deep copy changed identity");
        SymbolTable.push();
        try {
            FunctionInfo copy = find(function.owner, function.name);
            check(copy != function && copy.getMagnetismGeneId() == id, "Scope copy changed identity");
        } finally {
            SymbolTable.pop();
        }
        check(find(function.owner, function.name).getMagnetismGeneId() == id, "Scope pop changed identity");
    }

    private static FunctionInfo find(TypeKlass owner, String name) {
        return SymbolTable.getAllCombined(owner, FunctionInfo.class).stream()
                .map(FunctionInfo.class::cast).filter(function -> function.name.equals(name))
                .findFirst().orElse(null);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class InheritingFactory extends KlassFactory {
        private final TypeKlass parent;

        private InheritingFactory(String name, TypeKlass parent) {
            super(name, 0, 0, 0, 0, 0);
            this.parent = parent;
        }

        @Override
        protected boolean canInheritFrom(Type type) {
            return type.equals(parent);
        }

        @Override
        protected IRNode produceVariableDeclarations(IRNodeBuilder builder) {
            return new VariableDeclarationBlock(new ArrayList<>(), 1);
        }
    }
}
