/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Stack;
import java.util.zip.CRC32;

public class Printer {
    public enum Mode {
        FULL,
        REDUCED
    }

    public static void debugPrint(String gene, String value) {
        System.err.println("[JTDBG] gene=" + gene + " value=" + value);
    }

    public static boolean debugBoolean(String gene, boolean value) {
        debugPrint(gene, print(value));
        return value;
    }

    public static byte debugByte(String gene, byte value) {
        debugPrint(gene, print(value));
        return value;
    }

    public static short debugShort(String gene, short value) {
        debugPrint(gene, print(value));
        return value;
    }

    public static char debugChar(String gene, char value) {
        debugPrint(gene, print(value));
        return value;
    }

    public static int debugInt(String gene, int value) {
        debugPrint(gene, print(value));
        return value;
    }

    public static long debugLong(String gene, long value) {
        debugPrint(gene, print(value));
        return value;
    }

    public static float debugFloat(String gene, float value) {
        debugPrint(gene, print(value));
        return value;
    }

    public static double debugDouble(String gene, double value) {
        debugPrint(gene, print(value));
        return value;
    }

    public static String debugString(String gene, String value) {
        debugPrint(gene, value);
        return value;
    }

    public static String print(boolean arg) {
        return String.valueOf(arg);
    }

    public static String print(byte arg) {
        return String.valueOf(arg);
    }

    public static String print(short arg) {
        return String.valueOf(arg);
    }

    public static String print(char arg) {
        return String.valueOf((int) arg);
    }

    public static String print(int arg) {
        return String.valueOf(arg);
    }

    public static String print(long arg) {
        return String.valueOf(arg);
    }

    public static String print(float arg) {
        return String.valueOf(arg);
    }

    public static String print(double arg) {
        return String.valueOf(arg);
    }

    public static String print(Object arg) {
        if (arg == null) {
            return "null";
        }
        if (isScalar(arg)) {
            return printScalar(arg);
        }
        StringBuilder sb = new StringBuilder();
        Stack<Object> visitedObjects = new Stack<>();
        if (arg.getClass().isArray()) {
            printArrayDot(sb, visitedObjects, "", arg, true);
        } else {
            printObjectDot(sb, visitedObjects, "", arg, true);
        }
        return sb.toString().trim();
    }

    public static String print(String rootPath, Object arg) {
        if (arg == null) {
            return rootPath + " = null";
        }
        if (isScalar(arg)) {
            return rootPath + " = " + printScalar(arg);
        }
        StringBuilder sb = new StringBuilder();
        Stack<Object> visitedObjects = new Stack<>();
        if (arg.getClass().isArray()) {
            printArrayDot(sb, visitedObjects, rootPath, arg, false);
        } else {
            printObjectDot(sb, visitedObjects, rootPath, arg, false);
        }
        return sb.toString().trim();
    }

    public static String printIntArray(String rootPath, int[] arg, Mode mode) {
        if (mode == Mode.FULL) {
            return print(rootPath, arg);
        }
        CRC32 crc = new CRC32();
        updateInt(crc, arg == null ? -1 : arg.length);
        if (arg != null) {
            for (int value : arg) {
                updateInt(crc, value);
            }
        }
        return reducedCollectionString(rootPath, "int[]", arg == null ? -1 : arg.length, crc);
    }

    public static String printIntArray(String rootPath, int[][] arg, Mode mode) {
        if (mode == Mode.FULL) {
            return print(rootPath, arg);
        }
        CRC32 crc = new CRC32();
        updateInt(crc, arg == null ? -1 : arg.length);
        if (arg != null) {
            for (int[] nested : arg) {
                updateInt(crc, nested == null ? -1 : nested.length);
                if (nested != null) {
                    for (int value : nested) {
                        updateInt(crc, value);
                    }
                }
            }
        }
        return reducedCollectionString(rootPath, "int[][]", arg == null ? -1 : arg.length, crc);
    }

    public static String printIntArray(String rootPath, int[][][] arg, Mode mode) {
        if (mode == Mode.FULL) {
            return print(rootPath, arg);
        }
        CRC32 crc = new CRC32();
        updateInt(crc, arg == null ? -1 : arg.length);
        if (arg != null) {
            for (int[][] nested2 : arg) {
                updateInt(crc, nested2 == null ? -1 : nested2.length);
                if (nested2 != null) {
                    for (int[] nested1 : nested2) {
                        updateInt(crc, nested1 == null ? -1 : nested1.length);
                        if (nested1 != null) {
                            for (int value : nested1) {
                                updateInt(crc, value);
                            }
                        }
                    }
                }
            }
        }
        return reducedCollectionString(rootPath, "int[][][]", arg == null ? -1 : arg.length, crc);
    }

    public static String printIntList(String rootPath, ArrayList<Integer> arg, Mode mode) {
        if (mode == Mode.FULL) {
            return print(rootPath, arg);
        }
        CRC32 crc = new CRC32();
        updateInt(crc, arg == null ? -1 : arg.size());
        if (arg != null) {
            for (Integer value : arg) {
                if (value == null) {
                    updateInt(crc, 0);
                } else {
                    updateInt(crc, 1);
                    updateInt(crc, value);
                }
            }
        }
        return reducedCollectionString(rootPath, "ArrayList<Integer>", arg == null ? -1 : arg.size(), crc);
    }

    private static String reducedCollectionString(String rootPath, String typeName, int size, CRC32 crc) {
        if (size < 0) {
            return rootPath + " = (" + typeName + ") null";
        }
        return rootPath + " = (" + typeName + ") [" + size + "] crc32=" + crc.getValue();
    }

    private static void updateInt(CRC32 crc, int value) {
        crc.update(value);
        crc.update(value >>> 8);
        crc.update(value >>> 16);
        crc.update(value >>> 24);
    }

    private static String print_r(Stack<Object> visitedObjects, Object arg) {
        String result = "";
        if (arg == null) {
            result += "null";
        } else if (arg.getClass().isArray()) {
            for (int i = 0; i < visitedObjects.size(); i++) {
                if (visitedObjects.elementAt(i) == arg) {
                    return "<recursive>";
                }
            }

            visitedObjects.push(arg);

            final String delimiter = ", ";
            result += "[";

            if (arg instanceof Object[]) {
                Object[] array = (Object[]) arg;
                for (int i = 0; i < array.length; i++) {
                    result += print_r(visitedObjects, array[i]);
                    if (i < array.length - 1) {
                        result += delimiter;
                    }
                }
            } else if (arg instanceof boolean[]) {
                boolean[] array = (boolean[]) arg;
                for (int i = 0; i < array.length; i++) {
                    result += print(array[i]);
                    if (i < array.length - 1) {
                        result += delimiter;
                    }
                }
            } else if (arg instanceof byte[]) {
                byte[] array = (byte[]) arg;
                for (int i = 0; i < array.length; i++) {
                    result += print(array[i]);
                    if (i < array.length - 1) {
                        result += delimiter;
                    }
                }
            } else if (arg instanceof short[]) {
                short[] array = (short[]) arg;
                for (int i = 0; i < array.length; i++) {
                    result += print(array[i]);
                    if (i < array.length - 1) {
                        result += delimiter;
                    }
                }
            } else if (arg instanceof char[]) {
                char[] array = (char[]) arg;
                for (int i = 0; i < array.length; i++) {
                    result += print(array[i]);
                    if (i < array.length - 1) {
                        result += delimiter;
                    }
                }
            } else if (arg instanceof int[]) {
                int[] array = (int[]) arg;
                for (int i = 0; i < array.length; i++) {
                    result += print(array[i]);
                    if (i < array.length - 1) {
                        result += delimiter;
                    }
                }
            } else if (arg instanceof long[]) {
                long[] array = (long[]) arg;
                for (int i = 0; i < array.length; i++) {
                    result += print(array[i]);
                    if (i < array.length - 1) {
                        result += delimiter;
                    }
                }
            } else if (arg instanceof float[]) {
                float[] array = (float[]) arg;
                for (int i = 0; i < array.length; i++) {
                    result += print(array[i]);
                    if (i < array.length - 1) {
                        result += delimiter;
                    }
                }
            } else if (arg instanceof double[]) {
                double[] array = (double[]) arg;
                for (int i = 0; i < array.length; i++) {
                    result += print(array[i]);
                    if (i < array.length - 1) {
                        result += delimiter;
                    }
                }
            }

            result += "]";
            visitedObjects.pop();

        } else {
            result += arg.toString();
        }

        return result;
    }

    private static boolean isScalar(Object value) {
        return value instanceof Boolean
                || value instanceof Byte
                || value instanceof Short
                || value instanceof Character
                || value instanceof Integer
                || value instanceof Long
                || value instanceof Float
                || value instanceof Double
                || value instanceof String
                || value instanceof Enum<?>;
    }

    private static String printScalar(Object value) {
        if (value instanceof Boolean) {
            return print((boolean) value);
        }
        if (value instanceof Byte) {
            return print((byte) value);
        }
        if (value instanceof Short) {
            return print((short) value);
        }
        if (value instanceof Character) {
            return print((char) value);
        }
        if (value instanceof Integer) {
            return print((int) value);
        }
        if (value instanceof Long) {
            return print((long) value);
        }
        if (value instanceof Float) {
            return print((float) value);
        }
        if (value instanceof Double) {
            return print((double) value);
        }
        if (value instanceof String) {
            return "\"" + escapeString((String) value) + "\"";
        }
        return String.valueOf(value);
    }

    private static String escapeString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void appendLine(StringBuilder sb, String text) {
        sb.append(text).append("\n");
    }

    private static String simpleTypeName(Class<?> clazz) {
        String simple = clazz.getSimpleName();
        return simple.isEmpty() ? clazz.getName() : simple;
    }

    private static boolean alreadyVisited(Stack<Object> visitedObjects, Object candidate) {
        for (int i = 0; i < visitedObjects.size(); i++) {
            if (visitedObjects.elementAt(i) == candidate) {
                return true;
            }
        }
        return false;
    }

    private static List<Field> collectFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null) {
            Field[] declared = current.getDeclaredFields();
            for (Field f : declared) {
                if (f.isSynthetic()) {
                    continue;
                }
                if (Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                fields.add(f);
            }
            current = current.getSuperclass();
        }
        fields.sort(Comparator.comparing(Field::getName));
        return fields;
    }

    private static void printObjectDot(StringBuilder sb,
                                       Stack<Object> visitedObjects,
                                       String path,
                                       Object value,
                                       boolean root) {
        Class<?> type = value.getClass();
        String effectivePath = root ? simpleTypeName(type) : path;
        if (root) {
            appendLine(sb, "(" + simpleTypeName(type) + ")");
        } else {
            appendLine(sb, path + " = (" + simpleTypeName(type) + ")");
        }

        if (alreadyVisited(visitedObjects, value)) {
            appendLine(sb, (root ? "<root>" : path) + " = <recursive>");
            return;
        }
        visitedObjects.push(value);
        try {
            List<Field> fields = collectFields(type);
            for (Field f : fields) {
                if (!f.trySetAccessible()) {
                    continue;
                }
                Object fieldValue;
                try {
                    fieldValue = f.get(value);
                } catch (IllegalAccessException e) {
                    continue;
                }
                String fieldPath = effectivePath + "." + f.getName();
                printValueDot(sb, visitedObjects, fieldPath, f.getType(), fieldValue);
            }
        } finally {
            visitedObjects.pop();
        }
    }

    private static void printArrayDot(StringBuilder sb,
                                      Stack<Object> visitedObjects,
                                      String path,
                                      Object value,
                                      boolean root) {
        Class<?> arrayType = value.getClass();
        String arrayTypeName = simpleTypeName(arrayType);
        int length = Array.getLength(value);
        if (root) {
            appendLine(sb, "(" + arrayTypeName + ") [" + length + "]");
        } else {
            appendLine(sb, path + " = (" + arrayTypeName + ") [" + length + "]");
        }
        if (alreadyVisited(visitedObjects, value)) {
            appendLine(sb, (root ? "<root>" : path) + " = <recursive>");
            return;
        }
        visitedObjects.push(value);
        try {
            Class<?> componentType = arrayType.getComponentType();
            for (int i = 0; i < length; i++) {
                Object element = Array.get(value, i);
                String itemPath = (root ? "" : path) + "[" + i + "]";
                printValueDot(sb, visitedObjects, itemPath, componentType, element);
            }
        } finally {
            visitedObjects.pop();
        }
    }

    private static void printValueDot(StringBuilder sb,
                                      Stack<Object> visitedObjects,
                                      String path,
                                      Class<?> declaredType,
                                      Object value) {
        if (value == null) {
            appendLine(sb, path + " = (" + simpleTypeName(declaredType) + ") null");
            return;
        }
        if (isScalar(value)) {
            appendLine(sb, path + " = " + printScalar(value));
            return;
        }
        if (value.getClass().isArray()) {
            printArrayDot(sb, visitedObjects, path, value, false);
            return;
        }
        printObjectDot(sb, visitedObjects, path, value, false);
    }
}
