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

package jdk.test.lib.jittester.diagnostics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import jdk.test.lib.jittester.IRNode;

public final class SourceDiagnostics {
    private static final Map<IRNode, List<String>> COMMENTS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private SourceDiagnostics() {
    }

    public static void attach(IRNode node, String comment) {
        if (node == null || comment == null || comment.isBlank()) {
            return;
        }
        synchronized (COMMENTS) {
            COMMENTS.computeIfAbsent(node, unused -> new ArrayList<>())
                    .add(sanitize(comment));
        }
    }

    public static String comment(IRNode node) {
        if (node == null) {
            return "";
        }
        synchronized (COMMENTS) {
            List<String> comments = COMMENTS.get(node);
            if (comments == null || comments.isEmpty()) {
                return "";
            }
            return String.join(" ", comments);
        }
    }

    private static String sanitize(String comment) {
        return comment
                .replace('\n', ' ')
                .replace('\r', ' ')
                .trim();
    }
}
