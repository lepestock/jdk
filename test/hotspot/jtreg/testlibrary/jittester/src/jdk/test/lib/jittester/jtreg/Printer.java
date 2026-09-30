/*
 * Copyright (c) 2016, 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Collection;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.zip.CRC32;

public class Printer {
    private static final int NULL_VALUE_SLOT = 0x4E554C4C; // "NULL"
    private static final int EMPTY_CONTAINER_VALUE_SLOT = 0x45434E54; // "ECNT"
    private static final int EMPTY_MAP_VALUE_SLOT = 0x454D4150; // "EMAP"
    private static final int FIELDLESS_OBJECT_VALUE_SLOT = 0x464F424A; // "FOBJ"
    private static final int STRING_ABSENT_CHAR_SLOT = 0x53414253; // "SABS"
    private static final int STRING_EMPTY_VALUE_SLOT = 0x53454D50; // "SEMP"
    private static final long SHAPE_HASH_OFFSET = 0xcbf29ce484222325L;
    private static final long SHAPE_HASH_PRIME = 0x100000001b3L;
    private static final ClassValue<List<Field>> FIELD_CACHE = new ClassValue<>() {
        @Override
        protected List<Field> computeValue(Class<?> type) {
            return collectFieldsUncached(type);
        }
    };

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

    public static String printReduced(String rootPath, Object arg, Mode mode) {
        if (mode == Mode.FULL) {
            return print(rootPath, arg);
        }
        ArrayList<Object> values = new ArrayList<>();
        values.add(arg);
        StringBuilder sb = new StringBuilder();
        reduceValueGroup(sb, rootPath, values, new IdentityHashMap<>(), ReductionShape.empty());
        return sb.toString().trim();
    }

    public static String printIntArray(String rootPath, int[] arg, Mode mode) {
        return printReduced(rootPath, arg, mode);
    }

    public static String printIntArray(String rootPath, int[][] arg, Mode mode) {
        return printReduced(rootPath, arg, mode);
    }

    public static String printIntArray(String rootPath, int[][][] arg, Mode mode) {
        return printReduced(rootPath, arg, mode);
    }

    public static String printIntList(String rootPath, ArrayList<Integer> arg, Mode mode) {
        return printReduced(rootPath, arg, mode);
    }

    private static void updateInt(CRC32 crc, int value) {
        crc.update(value);
        crc.update(value >>> 8);
        crc.update(value >>> 16);
        crc.update(value >>> 24);
    }

    private static void updateLong(CRC32 crc, long value) {
        updateInt(crc, (int) value);
        updateInt(crc, (int) (value >>> 32));
    }

    private static void reduceValueGroup(StringBuilder sb,
                                         String path,
                                         List<Object> values,
                                         IdentityHashMap<Object, Boolean> activeObjects,
                                         ReductionShape shape) {
        int nonNull = 0;
        for (Object value : values) {
            if (value != null) {
                nonNull++;
            }
        }
        if (nonNull == 0) {
            reduceNullGroup(sb, path, values, shape);
            return;
        }

        boolean allScalars = true;
        boolean allContainers = true;
        boolean allMaps = true;
        boolean allObjects = true;
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            boolean scalar = isScalar(value);
            boolean container = isContainer(value);
            boolean map = value instanceof Map<?, ?>;
            allScalars &= scalar;
            allContainers &= container;
            allMaps &= map;
            allObjects &= !scalar && !container && !map;
        }

        if (allScalars) {
            reduceScalarGroup(sb, path, values, shape);
        } else if (allContainers) {
            reduceContainerGroup(sb, path, values, activeObjects, shape);
        } else if (allMaps) {
            reduceMapGroup(sb, path, values, activeObjects, shape);
        } else if (allObjects) {
            reduceObjectGroup(sb, path, values, activeObjects, shape);
        } else {
            reduceMixedGroup(sb, path, values, activeObjects, shape);
        }
    }

    private static void reduceMixedGroup(StringBuilder sb,
                                         String path,
                                         List<Object> values,
                                         IdentityHashMap<Object, Boolean> activeObjects,
                                         ReductionShape shape) {
        ReductionShape childShape = shape.copy();
        ArrayList<Object> nulls = new ArrayList<>();
        ArrayList<Object> scalars = new ArrayList<>();
        ArrayList<Object> containers = new ArrayList<>();
        ArrayList<Object> maps = new ArrayList<>();
        ArrayList<Object> objects = new ArrayList<>();

        childShape.addInt("mixed:size", values.size());
        for (Object value : values) {
            childShape.addString("mixed:kind", mixedKind(value));
            if (value == null) {
                nulls.add(null);
                continue;
            }
            if (isScalar(value)) {
                scalars.add(value);
            } else if (isContainer(value)) {
                containers.add(value);
            } else if (value instanceof Map<?, ?>) {
                maps.add(value);
            } else {
                objects.add(value);
            }
        }

        if (!nulls.isEmpty()) {
            reduceNullGroup(sb, path + ".null", nulls, childShape);
        }
        if (!scalars.isEmpty()) {
            reduceScalarGroup(sb, path + ".scalar", scalars, childShape);
        }
        if (!containers.isEmpty()) {
            reduceContainerGroup(sb, path + ".container", containers, activeObjects, childShape);
        }
        if (!maps.isEmpty()) {
            reduceMapGroup(sb, path + ".map", maps, activeObjects, childShape);
        }
        if (!objects.isEmpty()) {
            reduceObjectGroup(sb, path + ".object", objects, activeObjects, childShape);
        }
    }

    private static String mixedKind(Object value) {
        if (value == null) {
            return "null";
        }
        if (isScalar(value)) {
            return "scalar:" + value.getClass().getName();
        }
        if (isContainer(value)) {
            return "container:" + value.getClass().getName();
        }
        if (value instanceof Map<?, ?>) {
            return "map:" + value.getClass().getName();
        }
        return "object:" + value.getClass().getName();
    }

    private static boolean isContainer(Object value) {
        return value != null && (value.getClass().isArray() || value instanceof Collection<?>);
    }

    private static void reduceNullGroup(StringBuilder sb,
                                        String path,
                                        List<Object> values,
                                        ReductionShape shape) {
        CRC32 crc = new CRC32();
        updateInt(crc, values.size());
        for (Object ignored : values) {
            updateNullSlot(crc);
        }
        appendReducedValue(sb, path, shape, Long.toString(crc.getValue()));
    }

    private static void updateNullSlot(CRC32 crc) {
        updateInt(crc, NULL_VALUE_SLOT);
    }

    private static void reduceStructuralOnlyGroup(StringBuilder sb,
                                                  String path,
                                                  List<Object> values,
                                                  ReductionShape shape,
                                                  int presentValueSlot) {
        CRC32 crc = new CRC32();
        updateInt(crc, values.size());
        for (Object value : values) {
            updateInt(crc, value == null ? NULL_VALUE_SLOT : presentValueSlot);
        }
        appendReducedValue(sb, path, shape, Long.toString(crc.getValue()));
    }

    private static void reduceContainerGroup(StringBuilder sb,
                                             String path,
                                             List<Object> containers,
                                             IdentityHashMap<Object, Boolean> activeObjects,
                                             ReductionShape shape) {
        if (canReducePrimitiveArrayGroup(containers)) {
            reducePrimitiveArrayGroup(sb, path, containers, shape);
            return;
        }
        if (reduceFlatScalarContainerGroup(sb, path, containers, activeObjects, shape)) {
            return;
        }

        ReductionShape childShape = shape.copy();
        ArrayList<Object> elements = new ArrayList<>();
        IdentityHashMap<Object, Boolean> groupObjects = new IdentityHashMap<>();

        for (Object container : containers) {
            if (container == null) {
                childShape.addInt("container:null", -1);
                elements.add(null);
                continue;
            }
            if (activeObjects.containsKey(container)) {
                childShape.addInt("container:recursive", -2);
                continue;
            }
            groupObjects.put(container, Boolean.TRUE);
            int size = containerSize(container);
            childShape.addString("container:type", container.getClass().getName());
            childShape.addInt("container:size", size);
            addContainerElements(container, elements);
        }

        try {
            groupObjects.forEach((object, value) -> activeObjects.put(object, Boolean.TRUE));
            if (elements.isEmpty()) {
                reduceStructuralOnlyGroup(sb, path, containers, childShape, EMPTY_CONTAINER_VALUE_SLOT);
                return;
            }

            reduceValueGroup(sb, path, elements, activeObjects, childShape);
        } finally {
            groupObjects.forEach((object, value) -> activeObjects.remove(object));
        }
    }

    private static boolean canReducePrimitiveArrayGroup(List<Object> containers) {
        Class<?> componentType = null;
        boolean found = false;
        for (Object container : containers) {
            if (container == null) {
                continue;
            }
            Class<?> containerClass = container.getClass();
            if (!containerClass.isArray()) {
                return false;
            }
            Class<?> currentComponentType = containerClass.getComponentType();
            if (!currentComponentType.isPrimitive()) {
                return false;
            }
            if (componentType == null) {
                componentType = currentComponentType;
            } else if (componentType != currentComponentType) {
                return false;
            }
            found = true;
        }
        return found;
    }

    private static void reducePrimitiveArrayGroup(StringBuilder sb,
                                                  String path,
                                                  List<Object> containers,
                                                  ReductionShape shape) {
        ReductionShape childShape = shape.copy();
        int valueCount = 0;
        for (Object container : containers) {
            if (container == null) {
                childShape.addInt("container:null", -1);
                valueCount++;
                continue;
            }
            Class<?> containerClass = container.getClass();
            int size = Array.getLength(container);
            childShape.addString("container:type", containerClass.getName());
            childShape.addInt("container:size", size);
            valueCount += size;
        }

        if (valueCount == 0) {
            reduceStructuralOnlyGroup(sb, path, containers, childShape, EMPTY_CONTAINER_VALUE_SLOT);
            return;
        }

        CRC32 crc = new CRC32();
        updateInt(crc, valueCount);
        for (Object container : containers) {
            if (container == null) {
                updateNullSlot(crc);
                continue;
            }
            updatePrimitiveArrayValues(crc, container);
        }
        appendReducedValue(sb, path, childShape, Long.toString(crc.getValue()));
    }

    private static void updatePrimitiveArrayValues(CRC32 crc, Object array) {
        if (array instanceof boolean[] values) {
            for (boolean value : values) {
                updateInt(crc, value ? 1 : 0);
            }
        } else if (array instanceof byte[] values) {
            for (byte value : values) {
                updateLong(crc, value);
            }
        } else if (array instanceof short[] values) {
            for (short value : values) {
                updateLong(crc, value);
            }
        } else if (array instanceof char[] values) {
            for (char value : values) {
                updateLong(crc, value);
            }
        } else if (array instanceof int[] values) {
            for (int value : values) {
                updateLong(crc, value);
            }
        } else if (array instanceof long[] values) {
            for (long value : values) {
                updateLong(crc, value);
            }
        } else if (array instanceof float[] values) {
            for (float value : values) {
                updateLong(crc, normalizedFloatBits(value));
            }
        } else if (array instanceof double[] values) {
            for (double value : values) {
                updateLong(crc, normalizedDoubleBits(value));
            }
        }
    }

    private static boolean reduceFlatScalarContainerGroup(StringBuilder sb,
                                                          String path,
                                                          List<Object> containers,
                                                          IdentityHashMap<Object, Boolean> activeObjects,
                                                          ReductionShape shape) {
        ReductionShape childShape = shape.copy();
        FlatScalarStats stats = new FlatScalarStats();

        for (Object container : containers) {
            if (container == null) {
                childShape.addInt("container:null", -1);
                stats.add(null);
                continue;
            }
            if (activeObjects.containsKey(container)) {
                return false;
            }
            int size = containerSize(container);
            childShape.addString("container:type", container.getClass().getName());
            childShape.addInt("container:size", size);
            if (!scanFlatScalarElements(container, stats)) {
                return false;
            }
        }

        if (stats.valueCount == 0) {
            reduceStructuralOnlyGroup(sb, path, containers, childShape, EMPTY_CONTAINER_VALUE_SLOT);
            return true;
        }
        if (stats.nonNullCount == 0) {
            appendReducedValue(sb, path, childShape, nullValueCrc(stats.valueCount));
            return true;
        }

        FlatScalarKind kind = stats.kind();
        if (kind == FlatScalarKind.STRING) {
            reduceFlatStringContainerGroup(sb, path, containers, childShape, stats);
            return true;
        }

        CRC32 crc = new CRC32();
        updateInt(crc, stats.valueCount);
        for (Object container : containers) {
            if (container == null) {
                updateNullSlot(crc);
            } else {
                updateFlatScalarValues(crc, container, kind);
            }
        }
        appendReducedValue(sb, path, childShape, Long.toString(crc.getValue()));
        return true;
    }

    private static boolean scanFlatScalarElements(Object container, FlatScalarStats stats) {
        if (container.getClass().isArray()) {
            if (container.getClass().getComponentType().isPrimitive()) {
                return false;
            }
            Object[] array = (Object[]) container;
            for (Object value : array) {
                if (value != null && !isScalar(value)) {
                    return false;
                }
                stats.add(value);
            }
            return true;
        }
        for (Object value : (Collection<?>) container) {
            if (value != null && !isScalar(value)) {
                return false;
            }
            stats.add(value);
        }
        return true;
    }

    private static String nullValueCrc(int count) {
        CRC32 crc = new CRC32();
        updateInt(crc, count);
        for (int i = 0; i < count; i++) {
            updateNullSlot(crc);
        }
        return Long.toString(crc.getValue());
    }

    private static void updateFlatScalarValues(CRC32 crc, Object container, FlatScalarKind kind) {
        if (container.getClass().isArray()) {
            Object[] array = (Object[]) container;
            for (Object value : array) {
                updateFlatScalarValue(crc, value, kind);
            }
            return;
        }
        for (Object value : (Collection<?>) container) {
            updateFlatScalarValue(crc, value, kind);
        }
    }

    private static void updateFlatScalarValue(CRC32 crc, Object value, FlatScalarKind kind) {
        if (value == null) {
            updateNullSlot(crc);
            return;
        }
        switch (kind) {
            case BOOLEAN -> updateInt(crc, (Boolean) value ? 1 : 0);
            case INTEGRAL -> updateLong(crc, integralValue(value));
            case FLOATING -> {
                double current = ((Number) value).doubleValue();
                updateLong(crc, value instanceof Float
                        ? normalizedFloatBits((Float) value)
                        : normalizedDoubleBits(current));
            }
            case GENERIC -> updateString(crc, printScalar(value));
            case STRING -> throw new IllegalArgumentException("string groups use positional CRCs");
        }
    }

    private static void reduceFlatStringContainerGroup(StringBuilder sb,
                                                       String path,
                                                       List<Object> containers,
                                                       ReductionShape shape,
                                                       FlatScalarStats stats) {
        if (stats.maxStringLength == 0) {
            CRC32 crc = new CRC32();
            updateInt(crc, stats.valueCount);
            for (Object container : containers) {
                if (container == null) {
                    updateNullSlot(crc);
                } else {
                    updateFlatEmptyStringValues(crc, container);
                }
            }
            appendReducedValue(sb, path, shape, "<empty>_" + hex32(crc.getValue()));
            return;
        }

        CRC32[] positionCrcs = new CRC32[stats.maxStringLength];
        for (int position = 0; position < positionCrcs.length; position++) {
            positionCrcs[position] = new CRC32();
            updateInt(positionCrcs[position], stats.valueCount);
            updateInt(positionCrcs[position], stats.maxStringLength);
            updateInt(positionCrcs[position], position);
        }

        for (Object container : containers) {
            if (container == null) {
                updateStringNullSlot(positionCrcs);
            } else {
                updateFlatStringValues(positionCrcs, container);
            }
        }
        appendReducedValue(sb, path, shape, stringCrcVector(positionCrcs));
    }

    private static void updateFlatEmptyStringValues(CRC32 crc, Object container) {
        if (container.getClass().isArray()) {
            Object[] array = (Object[]) container;
            for (Object value : array) {
                if (value == null) {
                    updateNullSlot(crc);
                } else {
                    updateInt(crc, STRING_EMPTY_VALUE_SLOT);
                }
            }
            return;
        }
        for (Object value : (Collection<?>) container) {
            if (value == null) {
                updateNullSlot(crc);
            } else {
                updateInt(crc, STRING_EMPTY_VALUE_SLOT);
            }
        }
    }

    private static void updateFlatStringValues(CRC32[] positionCrcs, Object container) {
        if (container.getClass().isArray()) {
            Object[] array = (Object[]) container;
            for (Object value : array) {
                updateStringValue(positionCrcs, (String) value);
            }
            return;
        }
        for (Object value : (Collection<?>) container) {
            updateStringValue(positionCrcs, (String) value);
        }
    }

    private static void updateStringNullSlot(CRC32[] positionCrcs) {
        for (CRC32 crc : positionCrcs) {
            updateNullSlot(crc);
        }
    }

    private static void updateStringValue(CRC32[] positionCrcs, String value) {
        if (value == null) {
            updateStringNullSlot(positionCrcs);
            return;
        }
        int length = value.length();
        for (int position = 0; position < positionCrcs.length; position++) {
            CRC32 crc = positionCrcs[position];
            updateInt(crc, length);
            updateInt(crc, position < length ? value.charAt(position) : STRING_ABSENT_CHAR_SLOT);
        }
    }

    private static boolean allNonNullMatch(List<Object> values, java.util.function.Predicate<Object> predicate) {
        for (Object value : values) {
            if (value != null && !predicate.test(value)) {
                return false;
            }
        }
        return true;
    }

    private static int containerSize(Object container) {
        if (container.getClass().isArray()) {
            return Array.getLength(container);
        }
        return ((Collection<?>) container).size();
    }

    private static void addContainerElements(Object container, List<Object> elements) {
        if (container.getClass().isArray()) {
            int length = Array.getLength(container);
            for (int i = 0; i < length; i++) {
                elements.add(Array.get(container, i));
            }
            return;
        }
        elements.addAll((Collection<?>) container);
    }

    private static void reduceMapGroup(StringBuilder sb,
                                       String path,
                                       List<Object> maps,
                                       IdentityHashMap<Object, Boolean> activeObjects,
                                       ReductionShape shape) {
        ReductionShape childShape = shape.copy();
        ArrayList<Object> nulls = new ArrayList<>();
        ArrayList<Object> keys = new ArrayList<>();
        ArrayList<Object> values = new ArrayList<>();
        IdentityHashMap<Object, Boolean> groupObjects = new IdentityHashMap<>();

        for (Object value : maps) {
            if (value == null) {
                childShape.addInt("map:null", -1);
                nulls.add(null);
                continue;
            }
            if (activeObjects.containsKey(value)) {
                childShape.addInt("map:recursive", -2);
                continue;
            }
            groupObjects.put(value, Boolean.TRUE);
            Map<?, ?> map = (Map<?, ?>) value;
            int size = map.size();
            childShape.addString("map:type", map.getClass().getName());
            childShape.addInt("map:size", size);
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                keys.add(entry.getKey());
                values.add(entry.getValue());
            }
        }

        try {
            groupObjects.forEach((object, value) -> activeObjects.put(object, Boolean.TRUE));
            if (keys.isEmpty() && values.isEmpty() && nulls.isEmpty()) {
                reduceStructuralOnlyGroup(sb, path, maps, childShape, EMPTY_MAP_VALUE_SLOT);
                return;
            }
            if (!nulls.isEmpty()) {
                reduceNullGroup(sb, path + ".null", nulls, childShape);
            }
            if (!keys.isEmpty()) {
                reduceValueGroup(sb, path + ".key", keys, activeObjects, childShape);
            }
            if (!values.isEmpty()) {
                reduceValueGroup(sb, path + ".value", values, activeObjects, childShape);
            }
        } finally {
            groupObjects.forEach((object, value) -> activeObjects.remove(object));
        }
    }

    private static void reduceObjectGroup(StringBuilder sb,
                                          String fieldPath,
                                          List<Object> objects,
                                          IdentityHashMap<Object, Boolean> activeObjects,
                                          ReductionShape shape) {
        Map<String, ArrayList<Object>> fieldValues = new LinkedHashMap<>();
        ArrayList<Map<String, ArrayList<Object>>> objectFieldValues = new ArrayList<>();
        IdentityHashMap<Object, Boolean> groupObjects = new IdentityHashMap<>();
        ReductionShape childShape = shape.copy();

        for (Object object : objects) {
            if (object == null) {
                childShape.addInt("object:null", -1);
                objectFieldValues.add(null);
                continue;
            }
            if (activeObjects.containsKey(object)) {
                childShape.addInt("object:recursive", -2);
                objectFieldValues.add(null);
                continue;
            }
            groupObjects.put(object, Boolean.TRUE);
            childShape.addString("object:type", object.getClass().getName());
            Map<String, ArrayList<Object>> currentFieldValues = new LinkedHashMap<>();
            for (Field field : collectFields(object.getClass())) {
                Object fieldValue;
                try {
                    fieldValue = field.get(object);
                } catch (IllegalAccessException e) {
                    continue;
                }
                String key = fieldPath + "." + field.getName();
                fieldValues.computeIfAbsent(key, ignored -> new ArrayList<>());
                currentFieldValues.computeIfAbsent(key, ignored -> new ArrayList<>()).add(fieldValue);
            }
            objectFieldValues.add(currentFieldValues);
        }

        try {
            groupObjects.forEach((object, value) -> activeObjects.put(object, Boolean.TRUE));
            if (fieldValues.isEmpty()) {
                reduceStructuralOnlyGroup(sb, fieldPath, objects, childShape, FIELDLESS_OBJECT_VALUE_SLOT);
                return;
            }
            for (Map.Entry<String, ArrayList<Object>> entry : fieldValues.entrySet()) {
                for (Map<String, ArrayList<Object>> currentFieldValues : objectFieldValues) {
                    if (currentFieldValues == null || !currentFieldValues.containsKey(entry.getKey())) {
                        entry.getValue().add(null);
                    } else {
                        entry.getValue().addAll(currentFieldValues.get(entry.getKey()));
                    }
                }
                reduceValueGroup(sb, entry.getKey(), entry.getValue(), activeObjects, childShape);
            }
        } finally {
            groupObjects.forEach((object, value) -> activeObjects.remove(object));
        }
    }

    private static void reduceScalarGroup(StringBuilder sb,
                                          String path,
                                          List<Object> values,
                                          ReductionShape shape) {
        ArrayList<Object> nonNull = new ArrayList<>();
        for (Object value : values) {
            if (value == null) {
                continue;
            } else {
                nonNull.add(value);
            }
        }
        if (nonNull.isEmpty()) {
            reduceNullGroup(sb, path, values, shape);
            return;
        }
        if (allNonNullMatch(nonNull, value -> value instanceof String)) {
            reduceStringGroup(sb, path, values, shape);
            return;
        }
        if (allNonNullMatch(nonNull, Printer::isBooleanScalar)) {
            reduceBooleanGroup(sb, path, values, shape);
            return;
        }
        if (allNonNullMatch(nonNull, Printer::isIntegralScalar)) {
            reduceIntegralGroup(sb, path, values, shape);
            return;
        }
        if (allNonNullMatch(nonNull, Printer::isFloatingScalar)) {
            reduceFloatingGroup(sb, path, values, shape);
            return;
        }
        reduceGenericScalarGroup(sb, path, values, shape);
    }

    private static boolean isBooleanScalar(Object value) {
        return value instanceof Boolean;
    }

    private static boolean isIntegralScalar(Object value) {
        return value instanceof Byte
                || value instanceof Short
                || value instanceof Character
                || value instanceof Integer
                || value instanceof Long;
    }

    private static boolean isFloatingScalar(Object value) {
        return value instanceof Float || value instanceof Double;
    }

    private static void reduceBooleanGroup(StringBuilder sb,
                                           String path,
                                           List<Object> values,
                                           ReductionShape shape) {
        CRC32 crc = new CRC32();
        updateInt(crc, values.size());
        for (Object value : values) {
            if (value == null) {
                updateNullSlot(crc);
                continue;
            }
            boolean current = (Boolean) value;
            updateInt(crc, current ? 1 : 0);
        }
        appendReducedValue(sb, path, shape, Long.toString(crc.getValue()));
    }

    private static void reduceIntegralGroup(StringBuilder sb,
                                            String path,
                                            List<Object> values,
                                            ReductionShape shape) {
        CRC32 crc = new CRC32();
        updateInt(crc, values.size());
        for (Object value : values) {
            if (value == null) {
                updateNullSlot(crc);
                continue;
            }
            long current = integralValue(value);
            updateLong(crc, current);
        }
        appendReducedValue(sb, path, shape, Long.toString(crc.getValue()));
    }

    private static long integralValue(Object value) {
        if (value instanceof Character) {
            return (Character) value;
        }
        return ((Number) value).longValue();
    }

    private static void reduceFloatingGroup(StringBuilder sb,
                                            String path,
                                            List<Object> values,
                                            ReductionShape shape) {
        CRC32 crc = new CRC32();
        updateInt(crc, values.size());
        for (Object value : values) {
            if (value == null) {
                updateNullSlot(crc);
                continue;
            }
            double current = ((Number) value).doubleValue();
            updateLong(crc, value instanceof Float
                    ? normalizedFloatBits((Float) value)
                    : normalizedDoubleBits(current));
        }
        appendReducedValue(sb, path, shape, Long.toString(crc.getValue()));
    }

    private static int normalizedFloatBits(float value) {
        return Float.floatToIntBits(value);
    }

    private static long normalizedDoubleBits(double value) {
        return Double.doubleToLongBits(value);
    }

    private static void reduceStringGroup(StringBuilder sb,
                                          String path,
                                          List<Object> values,
                                          ReductionShape shape) {
        int maxLength = 0;
        for (Object value : values) {
            if (value != null) {
                maxLength = Math.max(maxLength, ((String) value).length());
            }
        }

        if (maxLength == 0) {
            CRC32 crc = new CRC32();
            updateInt(crc, values.size());
            for (Object value : values) {
                if (value == null) {
                    updateNullSlot(crc);
                } else {
                    updateInt(crc, STRING_EMPTY_VALUE_SLOT);
                }
            }
            appendReducedValue(sb, path, shape, "<empty>_" + hex32(crc.getValue()));
            return;
        }

        CRC32[] positionCrcs = new CRC32[maxLength];
        for (int position = 0; position < positionCrcs.length; position++) {
            positionCrcs[position] = new CRC32();
            updateInt(positionCrcs[position], values.size());
            updateInt(positionCrcs[position], maxLength);
            updateInt(positionCrcs[position], position);
        }

        for (Object value : values) {
            if (value == null) {
                for (CRC32 crc : positionCrcs) {
                    updateNullSlot(crc);
                }
                continue;
            }
            String string = (String) value;
            int length = string.length();
            for (int position = 0; position < positionCrcs.length; position++) {
                CRC32 crc = positionCrcs[position];
                updateInt(crc, length);
                updateInt(crc, position < length ? string.charAt(position) : STRING_ABSENT_CHAR_SLOT);
            }
        }

        appendReducedValue(sb, path, shape, stringCrcVector(positionCrcs));
    }

    private static String stringCrcVector(CRC32[] crcs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < crcs.length; i++) {
            if (i > 0) {
                sb.append("_");
            }
            sb.append(hex32(crcs[i].getValue()));
        }
        return sb.toString();
    }

    private static String hex32(long value) {
        String hex = Long.toHexString(value);
        StringBuilder sb = new StringBuilder();
        for (int pad = hex.length(); pad < 8; pad++) {
            sb.append("0");
        }
        sb.append(hex);
        return sb.toString();
    }

    private enum FlatScalarKind {
        STRING,
        BOOLEAN,
        INTEGRAL,
        FLOATING,
        GENERIC
    }

    private static final class FlatScalarStats {
        int valueCount;
        int nonNullCount;
        int stringCount;
        int booleanCount;
        int integralCount;
        int floatingCount;
        int maxStringLength;

        void add(Object value) {
            valueCount++;
            if (value == null) {
                return;
            }
            nonNullCount++;
            if (value instanceof String string) {
                stringCount++;
                maxStringLength = Math.max(maxStringLength, string.length());
            } else if (isBooleanScalar(value)) {
                booleanCount++;
            } else if (isIntegralScalar(value)) {
                integralCount++;
            } else if (isFloatingScalar(value)) {
                floatingCount++;
            }
        }

        FlatScalarKind kind() {
            if (stringCount == nonNullCount) {
                return FlatScalarKind.STRING;
            }
            if (booleanCount == nonNullCount) {
                return FlatScalarKind.BOOLEAN;
            }
            if (integralCount == nonNullCount) {
                return FlatScalarKind.INTEGRAL;
            }
            if (floatingCount == nonNullCount) {
                return FlatScalarKind.FLOATING;
            }
            return FlatScalarKind.GENERIC;
        }
    }

    private static void reduceGenericScalarGroup(StringBuilder sb,
                                                 String path,
                                                 List<Object> values,
                                                 ReductionShape shape) {
        CRC32 crc = new CRC32();
        updateInt(crc, values.size());
        for (Object value : values) {
            if (value == null) {
                updateNullSlot(crc);
                continue;
            }
            updateString(crc, printScalar(value));
        }
        appendReducedValue(sb, path, shape, Long.toString(crc.getValue()));
    }

    private static void updateString(CRC32 crc, String value) {
        updateInt(crc, value.length());
        for (int i = 0; i < value.length(); i++) {
            updateInt(crc, value.charAt(i));
        }
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

    private static void appendReducedValue(StringBuilder sb, String path, ReductionShape shape, String value) {
        if (shape.isEmpty()) {
            appendLine(sb, path + " = [" + value + "]");
        } else {
            appendLine(sb, path + " = [" + shape.crc() + ":" + value + "]");
        }
    }

    /*
     * Reduced output keeps structure and values in separate components. For example,
     * int[][] {{1, 2}, {3, 4}} and {{1, 2, 3, 4}} have the same flattened value
     * stream, but different shapes. They should therefore print as:
     *
     *   x = [shapeCrcA:valueCrc]
     *   x = [shapeCrcB:valueCrc]
     *
     * The value component detects leaf-value changes, while the shape component
     * keeps container boundaries, null/recursive markers, and object/container
     * runtime types from being lost during recursive reduction.
     */
    private static final class ReductionShape {
        private long hash;
        private boolean empty;

        private ReductionShape(long hash, boolean empty) {
            this.hash = hash;
            this.empty = empty;
        }

        static ReductionShape empty() {
            return new ReductionShape(SHAPE_HASH_OFFSET, true);
        }

        ReductionShape copy() {
            return new ReductionShape(hash, empty);
        }

        boolean isEmpty() {
            return empty;
        }

        void addInt(String tag, int value) {
            addString(tag, Integer.toString(value));
        }

        void addString(String tag, String value) {
            empty = false;
            hash = updateShapeString(updateShapeString(hash, tag), value);
        }

        long crc() {
            return hash & 0xffffffffL;
        }

        private static long updateShapeString(long hash, String value) {
            hash = updateShapeInt(hash, value.length());
            for (int i = 0; i < value.length(); i++) {
                hash = updateShapeInt(hash, value.charAt(i));
            }
            return hash;
        }

        private static long updateShapeInt(long hash, int value) {
            for (int shift = 0; shift < Integer.SIZE; shift += Byte.SIZE) {
                hash ^= (value >>> shift) & 0xffL;
                hash *= SHAPE_HASH_PRIME;
            }
            return hash;
        }
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
        return FIELD_CACHE.get(clazz);
    }

    private static List<Field> collectFieldsUncached(Class<?> clazz) {
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
                if (!f.trySetAccessible()) {
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
