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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jdk.test.lib.jittester.Type;
import jdk.test.lib.jittester.TypeList;
import jdk.test.lib.jittester.VariableInfo;
import jdk.test.lib.jittester.functions.FunctionInfo;
import jdk.test.lib.jittester.types.TypeArray;
import jdk.test.lib.jittester.types.TypeKlass;

/**
 * Loads precomputed core library symbols without reflecting on the current JVM.
 */
public final class CoreLibSymbolsLoader {
    private CoreLibSymbolsLoader() {
    }

    public static void load(Path inputFile) {
        List<String> lines;
        try {
            lines = Files.readAllLines(inputFile);
        } catch (IOException e) {
            throw new Error("Cannot read corelib symbols file: " + inputFile, e);
        }

        Map<String, TypeKlass> classes = new LinkedHashMap<>();
        TypeKlass current = null;
        for (String line : lines) {
            if (line.isEmpty()) {
                continue;
            }
            if (line.charAt(0) != '\t') {
                current = parseClass(line, classes);
                continue;
            }
            if (current == null) {
                throw new Error("Corelib symbol record without class header: " + line);
            }
            parseFunction(line.substring(1), current, classes);
        }

        for (TypeKlass type : classes.values()) {
            connectParents(type);
        }
    }

    private static TypeKlass parseClass(String line, Map<String, TypeKlass> classes) {
        String[] fields = line.split("\t");
        String name = fields[0];
        int flags = parseIntProperty(fields, "flags", TypeKlass.NONE);
        TypeKlass type = getOrCreateKlass(name, flags, classes, true);
        String parents = parseStringProperty(fields, "parents", "-");
        if (!parents.equals("-")) {
            for (String parent : parents.split(",")) {
                if (!parent.isEmpty()) {
                    type.addParent(parent);
                }
            }
        }
        return type;
    }

    private static void parseFunction(String line, TypeKlass owner, Map<String, TypeKlass> classes) {
        String[] fields = line.split("\t");
        if (fields.length < 3) {
            throw new Error("Malformed platform symbol record: " + line);
        }
        String kind = fields[0];
        if (kind.equals("m")) {
            String name = fields[1];
            Descriptor descriptor = parseDescriptor(fields[2], classes);
            int flags = parseIntProperty(fields, "flags", FunctionInfo.NONE);
            ArrayList<VariableInfo> args = args(owner, descriptor.argumentTypes(), flags, false);
            owner.addSymbol(new FunctionInfo(name, owner, descriptor.returnType(), 1, flags, args));
        } else if (kind.equals("c")) {
            Descriptor descriptor = parseDescriptor(fields[1], classes);
            int flags = parseIntProperty(fields, "flags", FunctionInfo.NONE);
            ArrayList<VariableInfo> args = args(owner, descriptor.argumentTypes(), flags, true);
            owner.addSymbol(new FunctionInfo(owner.getName(), owner, owner, 1, flags, args));
        } else {
            throw new Error("Unknown platform symbol record kind: " + kind);
        }
    }

    private static ArrayList<VariableInfo> args(TypeKlass owner, List<Type> argumentTypes, int flags,
            boolean constructor) {
        ArrayList<VariableInfo> args = new ArrayList<>();
        if (!constructor && (flags & FunctionInfo.STATIC) == 0) {
            args.add(VariableInfo.symbolArgument("this", owner, owner,
                    VariableInfo.LOCAL | VariableInfo.INITIALIZED));
        }
        int index = 0;
        for (Type argumentType : argumentTypes) {
            index++;
            args.add(VariableInfo.symbolArgument("arg" + index, owner, argumentType,
                    VariableInfo.LOCAL | VariableInfo.INITIALIZED));
        }
        return args;
    }

    private static Descriptor parseDescriptor(String descriptor, Map<String, TypeKlass> classes) {
        if (descriptor.charAt(0) != '(') {
            throw new Error("Malformed descriptor: " + descriptor);
        }
        int[] cursor = { 1 };
        ArrayList<Type> arguments = new ArrayList<>();
        while (descriptor.charAt(cursor[0]) != ')') {
            arguments.add(parseType(descriptor, cursor, classes));
        }
        cursor[0]++;
        Type returnType = parseType(descriptor, cursor, classes);
        if (cursor[0] != descriptor.length()) {
            throw new Error("Trailing characters in descriptor: " + descriptor);
        }
        return new Descriptor(arguments, returnType);
    }

    private static Type parseType(String descriptor, int[] cursor, Map<String, TypeKlass> classes) {
        int dimensions = 0;
        while (descriptor.charAt(cursor[0]) == '[') {
            dimensions++;
            cursor[0]++;
        }
        Type type = switch (descriptor.charAt(cursor[0]++)) {
            case 'V' -> TypeList.VOID;
            case 'Z' -> TypeList.BOOLEAN;
            case 'B' -> TypeList.BYTE;
            case 'C' -> TypeList.CHAR;
            case 'S' -> TypeList.SHORT;
            case 'I' -> TypeList.INT;
            case 'J' -> TypeList.LONG;
            case 'F' -> TypeList.FLOAT;
            case 'D' -> TypeList.DOUBLE;
            case 'L' -> parseReferenceType(descriptor, cursor, classes);
            default -> throw new Error("Unknown descriptor type: " + descriptor);
        };
        return dimensions == 0 ? type : new TypeArray(type, dimensions);
    }

    private static Type parseReferenceType(String descriptor, int[] cursor, Map<String, TypeKlass> classes) {
        int end = descriptor.indexOf(';', cursor[0]);
        if (end < 0) {
            throw new Error("Unterminated reference descriptor: " + descriptor);
        }
        String name = descriptor.substring(cursor[0], end).replace('/', '.');
        cursor[0] = end + 1;
        return getOrCreateKlass(name, TypeKlass.NONE, classes, false);
    }

    private static TypeKlass getOrCreateKlass(String name, int flags, Map<String, TypeKlass> classes,
            boolean addToTypeList) {
        TypeKlass type = classes.get(name);
        if (type != null) {
            if (addToTypeList) {
                type.setFlags(flags);
                if (TypeList.find(name) == null) {
                    TypeList.add(type);
                }
            }
            return type;
        }
        Type existing = TypeList.find(name);
        if (existing instanceof TypeKlass) {
            type = (TypeKlass) existing;
            if (addToTypeList) {
                type.setFlags(flags);
            }
        } else {
            type = new TypeKlass(name, flags);
            if (addToTypeList) {
                TypeList.add(type);
            }
        }
        classes.put(name, type);
        return type;
    }

    private static void connectParents(TypeKlass type) {
        for (String parentName : type.getParentsNames()) {
            Type parent = TypeList.find(parentName);
            if (parent instanceof TypeKlass) {
                TypeKlass parentKlass = (TypeKlass) parent;
                if (type.getParent() == null && !parentKlass.isInterface()) {
                    type.setParent(parentKlass);
                }
                parentKlass.addChild(type.getName());
            }
        }
    }

    private static int parseIntProperty(String[] fields, String name, int defaultValue) {
        String value = parseStringProperty(fields, name, null);
        return value == null ? defaultValue : Integer.parseInt(value);
    }

    private static String parseStringProperty(String[] fields, String name, String defaultValue) {
        String prefix = name + "=";
        for (int i = 1; i < fields.length; i++) {
            if (fields[i].startsWith(prefix)) {
                return fields[i].substring(prefix.length());
            }
        }
        return defaultValue;
    }

    private record Descriptor(List<Type> argumentTypes, Type returnType) {
    }
}
