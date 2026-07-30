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

package jdk.test.lib.jittester.corelib;

import java.nio.file.Path;

import jdk.test.lib.jittester.TypesParser;

/**
 * Library API for discovering and persisting core library symbols.
 */
public final class CoreLibSymbols {
    public static final String CLASSES_FILE = "classes.lst";
    public static final String EXCLUDE_METHODS_FILE = "exclude.methods.lst";
    public static final String INTRINSIC_METHODS_FILE = "intrinsics.lst";
    public static final String DEFAULT_OUTPUT_FILE = "corelib-symbols.lst";

    private CoreLibSymbols() {
    }

    public static void dump(Path confDir, Path outputFile) {
        dump(confDir.resolve(CLASSES_FILE),
                confDir.resolve(EXCLUDE_METHODS_FILE),
                confDir.resolve(INTRINSIC_METHODS_FILE),
                outputFile);
    }

    public static void dump(Path classesFile, Path excludeMethodsFile, Path intrinsicMethodsFile,
            Path outputFile) {
        TypesParser.parseTypesAndMethods(classesFile.toString(), excludeMethodsFile.toString(),
                intrinsicMethodsFile.toString());
        CoreLibSymbolsPrinter.printAll(outputFile);
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: CoreLibSymbols <conf-dir> <output-file>");
        }
        dump(Path.of(args[0]), Path.of(args[1]));
    }
}
