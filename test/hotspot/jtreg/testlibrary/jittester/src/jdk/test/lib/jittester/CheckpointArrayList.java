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

import java.util.ArrayList;
import java.util.Iterator;

/**
 * A small checkpoint-capable list wrapper.
 *
 * <p>Usage:
 * <pre>{@code
 * int cp = list.checkpoint();
 * // append data
 * if (failed) {
 *     list.shrink(cp);
 * }
 * }</pre>
 *
 * <p>Nested checkpoints work naturally via Java call stack:
 * each caller stores its own checkpoint value.
 */
public final class CheckpointArrayList<T> implements Iterable<T> {
    private final ArrayList<T> data;

    public CheckpointArrayList() {
        this.data = new ArrayList<>();
    }

    public CheckpointArrayList(int initialCapacity) {
        this.data = new ArrayList<>(Math.max(0, initialCapacity));
    }

    public int checkpoint() {
        return data.size();
    }

    public void shrink(int size) {
        if (size < 0 || size > data.size()) {
            throw new IllegalArgumentException("Invalid shrink size: " + size
                    + ", current size: " + data.size());
        }
        data.subList(size, data.size()).clear();
    }

    public void add(T value) {
        data.add(value);
    }

    public T get(int index) {
        return data.get(index);
    }

    public int size() {
        return data.size();
    }

    public boolean isEmpty() {
        return data.isEmpty();
    }

    public void clear() {
        data.clear();
    }

    @Override
    public Iterator<T> iterator() {
        return data.iterator();
    }
}
