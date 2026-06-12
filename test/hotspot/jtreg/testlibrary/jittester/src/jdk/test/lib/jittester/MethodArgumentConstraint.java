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

package jdk.test.lib.jittester;

/**
 * Extra generation-domain requirements for method arguments.
 *
 * <p>These are deliberately separate from Java types: a denominator argument is
 * still an {@code int} or {@code long}, but it needs a smaller value-domain than
 * the declared type accepts.</p>
 */
public enum MethodArgumentConstraint {
    NONE("none"),
    NONZERO("nonzero");

    private final String configName;

    MethodArgumentConstraint(String configName) {
        this.configName = configName;
    }

    static MethodArgumentConstraint parse(String value) {
        for (MethodArgumentConstraint constraint : values()) {
            if (constraint.configName.equals(value)) {
                return constraint;
            }
        }
        throw new IllegalArgumentException("Unknown method argument constraint: " + value);
    }
}
