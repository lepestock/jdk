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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import jdk.test.lib.jittester.types.TypeKlass;
import jdk.test.lib.jittester.visitors.Visitor;

public class PrintVariables extends IRNode {
    private final ArrayList<Symbol> vars;
    private final ArrayList<Symbol> finalVars;

    public PrintVariables(TypeKlass owner, int level) {
        super(TypeList.VOID);
        this.owner = owner;
        Selection selection = selectVarsForPrinting(owner);
        this.vars = selection.mutable;
        this.finalVars = selection.finals;
        this.level = level;
    }

    private static final class Selection {
        private final ArrayList<Symbol> mutable;
        private final ArrayList<Symbol> finals;

        private Selection(List<Symbol> mutable, List<Symbol> finals) {
            this.mutable = new ArrayList<>(mutable);
            this.finals = new ArrayList<>(finals);
        }
    }

    private static Selection selectVarsForPrinting(TypeKlass owner) {
        List<Symbol> allVars = SymbolTable.getAllCombined(VariableInfo.class);
        String nestedPrefix = owner.getName() + "_";

        List<Symbol> candidates = allVars.stream()
                .filter(VariableInfo.class::isInstance)
                .filter(symbol -> {
                    if (owner.equals(symbol.owner)) {
                        return true;
                    }
                    // Include related nested-class static fields because they are often mutated
                    // during test() and make iteration-to-iteration state changes visible.
                    return symbol.owner.getName().startsWith(nestedPrefix) && symbol.isStatic();
                })
                .filter(symbol -> !((VariableInfo) symbol).isLocal())
                .filter(symbol -> !"this".equals(symbol.name))
                .sorted(Comparator.comparing((Symbol s) -> s.owner.getName())
                        .thenComparing(s -> s.name))
                .collect(Collectors.toList());

        List<Symbol> mutable = candidates.stream()
                .filter(symbol -> !symbol.isFinal())
                .collect(Collectors.toList());
        List<Symbol> finals = candidates.stream()
                .filter(Symbol::isFinal)
                .collect(Collectors.toList());
        // Keep iteration snapshots focused on mutable state only.
        // Final fields are printed separately as one-time diagnostic snapshots.
        return new Selection(mutable, finals);
    }

    @Override
    public<T> T accept(Visitor<T> v) {
        return v.visit(this);
    }

    public List<Symbol> getVars() {
        return vars;
    }

    public List<Symbol> getFinalVars() {
        return finalVars;
    }
}
