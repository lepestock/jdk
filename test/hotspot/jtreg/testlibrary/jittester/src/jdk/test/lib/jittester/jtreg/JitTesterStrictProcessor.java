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

package jdk.test.lib.jittester.jtreg;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import jdk.test.lib.helpers.StrictProcessor;

/**
 * Applies strict-initialization classfile processing to one generated JitTester
 * test. A batch may share a class directory, therefore only class files whose
 * binary name belongs to the supplied test prefix are considered.
 */
public final class JitTesterStrictProcessor {
    private static final String CLASS_SUFFIX = ".class";
    private JitTesterStrictProcessor() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "Expected: <test classes directory> <generated test prefix>");
        }
        int processed = process(Path.of(args[0]), args[1]);
        if (processed == 0) {
            throw new IllegalArgumentException("No class files found for generated test '" + args[1] + "'");
        }
    }

    public static int process(Path classesDirectory, String testPrefix) throws IOException {
        if (!Files.isDirectory(classesDirectory)) {
            throw new IllegalArgumentException("Not a class directory: " + classesDirectory);
        }

        List<Path> classFiles;
        try (var paths = Files.walk(classesDirectory)) {
            classFiles = paths.filter(Files::isRegularFile)
                    .filter(path -> belongsToTest(path, testPrefix))
                    .toList();
        }

        for (Path classFile : classFiles) {
            Files.write(classFile, StrictProcessor.patchStrictInit(Files.readAllBytes(classFile)));
        }
        return classFiles.size();
    }

    private static boolean belongsToTest(Path classFile, String testPrefix) {
        String fileName = classFile.getFileName().toString();
        if (!fileName.endsWith(CLASS_SUFFIX)) {
            return false;
        }
        String className = fileName.substring(0, fileName.length() - CLASS_SUFFIX.length());
        return className.equals(testPrefix)
                || className.startsWith(testPrefix + "_")
                || className.startsWith(testPrefix + "$");
    }
}
