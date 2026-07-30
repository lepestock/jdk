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

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import jdk.test.lib.jittester.Symbol;
import jdk.test.lib.jittester.Type;
import jdk.test.lib.jittester.TypeList;
import jdk.test.lib.jittester.VariableInfo;
import jdk.test.lib.jittester.functions.FunctionInfo;
import jdk.test.lib.jittester.types.TypeArray;
import jdk.test.lib.jittester.types.TypeKlass;

/**
 * Development-only printer for core library methods discovered by JitTester.
 */
public final class CoreLibSymbolsPrinter {
    // FIXME: remove after development in favor of the proper corelib-symbols API.
    public static boolean enabled = true;

    private CoreLibSymbolsPrinter() {
    }

    public static void printAll(Path outputFile) {
        if (!enabled) {
            return;
        }
        Path parent = outputFile.getParent();
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(outputFile))) {
                for (Type type : TypeList.getAll()) {
                    if (type instanceof TypeKlass) {
                        print((TypeKlass) type, out);
                    }
                }
            }
        } catch (IOException e) {
            throw new Error("Cannot write corelib symbols file: " + outputFile, e);
        }
    }

    public static void print(TypeKlass typeKlass, PrintWriter out) {
        String parents = String.join(",", typeKlass.getParentsNames());
        out.printf("%s\tflags=%d\tparents=%s%n", typeKlass.getName(), typeKlass.getFlags(),
                parents.isEmpty() ? "-" : parents);
        for (Symbol symbol : typeKlass.getSymbols()) {
            if (symbol instanceof FunctionInfo) {
                print((FunctionInfo) symbol, out);
            }
        }
    }

    public static void print(FunctionInfo functionInfo, PrintWriter out) {
        if (functionInfo.isConstructor()) {
            out.printf("\tc\t%s\tflags=%d%n",
                    descriptor(functionInfo, TypeList.VOID), functionInfo.flags);
        } else {
            out.printf("\tm\t%s\t%s\tflags=%d%n",
                    functionInfo.name, descriptor(functionInfo, functionInfo.type), functionInfo.flags);
        }
    }

    private static String descriptor(FunctionInfo functionInfo, Type returnType) {
        StringBuilder sb = new StringBuilder();
        sb.append('(');
        int firstVisibleArgument = functionInfo.isStatic() || functionInfo.isConstructor() ? 0 : 1;
        for (int i = firstVisibleArgument; i < functionInfo.argTypes.size(); i++) {
            VariableInfo argument = functionInfo.argTypes.get(i);
            sb.append(typeDescriptor(argument.type));
        }
        sb.append(')');
        sb.append(typeDescriptor(returnType));
        return sb.toString();
    }

    private static String typeDescriptor(Type type) {
        if (type instanceof TypeArray) {
            TypeArray arrayType = (TypeArray) type;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < arrayType.getDimensions(); i++) {
                sb.append('[');
            }
            sb.append(typeDescriptor(arrayType.getType()));
            return sb.toString();
        }
        if (type.equals(TypeList.VOID)) {
            return "V";
        } else if (type.equals(TypeList.BOOLEAN)) {
            return "Z";
        } else if (type.equals(TypeList.BYTE)) {
            return "B";
        } else if (type.equals(TypeList.CHAR)) {
            return "C";
        } else if (type.equals(TypeList.SHORT)) {
            return "S";
        } else if (type.equals(TypeList.INT)) {
            return "I";
        } else if (type.equals(TypeList.LONG)) {
            return "J";
        } else if (type.equals(TypeList.FLOAT)) {
            return "F";
        } else if (type.equals(TypeList.DOUBLE)) {
            return "D";
        }
        return "L" + type.getName().replace('.', '/') + ";";
    }
}
