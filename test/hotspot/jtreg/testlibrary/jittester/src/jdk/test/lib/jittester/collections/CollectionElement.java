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

package jdk.test.lib.jittester.collections;

import java.util.ArrayList;
import jdk.test.lib.jittester.IRNode;
import jdk.test.lib.jittester.types.TypeArray;
import jdk.test.lib.jittester.visitors.Visitor;

public class CollectionElement extends IRNode {
    public static final long UNSET_EXPRESSION_GENE_SEED = Long.MIN_VALUE;
    private final IndexedStorageKind storageKind;
    private long expressionGeneSeed = UNSET_EXPRESSION_GENE_SEED;

    public CollectionElement(IRNode array, ArrayList<IRNode> dimensionExpressions) {
        this(array, dimensionExpressions, IndexedStorageKind.ARRAY);
    }

    public CollectionElement(IRNode array, ArrayList<IRNode> dimensionExpressions, IndexedStorageKind storageKind) {
        super(((TypeArray) array.getResultType()).type);
        this.storageKind = storageKind;
        addChild(array);
        addChildren(dimensionExpressions);
    }

    @Override
    public<T> T accept(Visitor<T> v) {
        return v.visit(this);
    }

    public void setExpressionGeneSeed(long seed) {
        this.expressionGeneSeed = seed;
    }

    public boolean hasExpressionGeneSeed() {
        return expressionGeneSeed != UNSET_EXPRESSION_GENE_SEED;
    }

    public long getExpressionGeneSeed() {
        return expressionGeneSeed;
    }

    public String getExpressionGeneToken() {
        return hasExpressionGeneSeed() ? "E" + expressionGeneSeed : "E<unset>";
    }

    public IndexedStorageKind getStorageKind() {
        return storageKind;
    }
}
