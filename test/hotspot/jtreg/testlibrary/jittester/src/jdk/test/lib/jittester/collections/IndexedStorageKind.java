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

package jdk.test.lib.jittester.collections;

import java.util.List;
import java.util.stream.Collectors;

public enum IndexedStorageKind {
    ARRAY;

    public String access(String receiver, List<String> indexes) {
        return switch (this) {
            case ARRAY -> receiver + arrayIndexes(indexes);
        };
    }

    public String creation(String elementType, List<String> sizes) {
        return switch (this) {
            case ARRAY -> "new " + elementType + arrayIndexes(sizes);
        };
    }

    public String initializer(String elementType, List<String> elements) {
        return switch (this) {
            case ARRAY -> "new " + elementType + "[] { " + String.join(", ", elements) + " }";
        };
    }

    public boolean supportsPulseArrayRead() {
        return this == ARRAY;
    }

    private static String arrayIndexes(List<String> indexes) {
        return indexes.stream().collect(Collectors.joining("][", "[", "]"));
    }
}
