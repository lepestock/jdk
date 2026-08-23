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

package jdk.test.lib.jittester.morph;

import java.util.ArrayList;
import java.util.List;

public record MorphContext(List<MorphTemplate> templates) {
    public static final MorphContext EMPTY = new MorphContext(List.of());

    public MorphContext {
        templates = List.copyOf(templates);
    }

    public boolean hasTemplates() {
        return !templates.isEmpty();
    }

    public int size() {
        return templates.size();
    }

    public MorphTemplate get(int index) {
        return templates.get(index);
    }

    public MorphContext withAdded(MorphTemplate template) {
        ArrayList<MorphTemplate> copy = new ArrayList<>(templates);
        copy.add(template);
        return new MorphContext(copy);
    }

    public MorphContext withReplaced(int index, MorphTemplate template) {
        ArrayList<MorphTemplate> copy = new ArrayList<>(templates);
        copy.set(index, template);
        return new MorphContext(copy);
    }

    public int indexOfId(long id) {
        for (int i = 0; i < templates.size(); i++) {
            if (templates.get(i).id() == id) {
                return i;
            }
        }
        return -1;
    }

    public MorphContext without(int index) {
        ArrayList<MorphTemplate> copy = new ArrayList<>(templates);
        copy.remove(index);
        return new MorphContext(copy);
    }

    public MorphContext withoutId(long id) {
        ArrayList<MorphTemplate> copy = new ArrayList<>(templates);
        copy.removeIf(template -> template.id() == id);
        return new MorphContext(copy);
    }
}
