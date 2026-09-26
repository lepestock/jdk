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

import jdk.test.lib.jittester.jtreg.Printer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class ReducerTests {
    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--print-drawing")) {
            printDrawingSample();
            return;
        }
        testNestedArrayShapeIsPreserved();
        testDrawingHierarchyReduction();
        testMixedElementKindsArePreserved();
        testNullOnlyGroupsUseValueCrc();
        testNullsInNonNullGroupsAffectValueCrc();
        testNullMapsHaveValueBucket();
        testStructuralOnlyGroupsUseValueCrc();
        testStringReductionPreservesValueSequence();
    }

    private static void testNestedArrayShapeIsPreserved() {
        String twoByTwo = reduced(new int[][] {{1, 2}, {3, 4}});
        String oneByFour = reduced(new int[][] {{1, 2, 3, 4}});

        if (twoByTwo.equals(oneByFour)) {
            throw new AssertionError("Different nested array shapes reduced to the same output: " + twoByTwo);
        }

        String[] twoByTwoParts = reducedParts(twoByTwo);
        String[] oneByFourParts = reducedParts(oneByFour);

        assertNotEquals(twoByTwoParts[0], oneByFourParts[0], "shape CRC");
        assertEquals(twoByTwoParts[1], oneByFourParts[1], "value CRC");
    }

    private static void testDrawingHierarchyReduction() {
        String output = reduced(sampleDrawing());

        assertContains(output, "x.bounds = [", "drawing bounds");
        assertContains(output, "x.figures.color = [", "figure inherited scalar field");
        assertContains(output, "x.figures.points.x = [", "nested point x field");
        assertContains(output, "x.figures.points.flags = [", "nested point collection field");
        assertContains(output, "x.meta.tags = [", "nested metadata collection");

        for (String line : output.split("\\R")) {
            if (!line.startsWith("x.")) {
                throw new AssertionError("Unexpected reduced hierarchy line: " + line);
            }
            String[] parts = line.substring(line.indexOf("[") + 1, line.length() - 1).split(":", -1);
            if (parts.length != 2) {
                throw new AssertionError("Expected shape:value output for hierarchy line: " + line);
            }
        }
    }

    private static void testMixedElementKindsArePreserved() {
        String scalarOne = reduced(new Object[] {1, new MixedBox(2)});
        String scalarNine = reduced(new Object[] {9, new MixedBox(2)});
        assertNotEquals(scalarOne, scalarNine, "mixed scalar/object element values");

        String arrayOneTwo = reduced(new Object[] {new int[] {1, 2}, new MixedBox(3)});
        String arrayNineNine = reduced(new Object[] {new int[] {9, 9}, new MixedBox(3)});
        assertNotEquals(arrayOneTwo, arrayNineNine, "mixed container/object element values");
    }

    private static void testNullOnlyGroupsUseValueCrc() {
        String oneNull = reduced(new Object[] {null});
        String twoNulls = reduced(new Object[] {null, null});

        String[] oneNullParts = reducedParts(oneNull);
        String[] twoNullsParts = reducedParts(twoNulls);

        assertNotEquals("null", oneNullParts[1], "one-null value component");
        assertNotEquals("null", twoNullsParts[1], "two-null value component");
        assertNotEquals(oneNullParts[1], twoNullsParts[1], "null-only value CRC");
        assertNotEquals("null", reducedPayload(reduced(null)), "root null value component");
    }

    private static void testNullsInNonNullGroupsAffectValueCrc() {
        String objectWithNull = reduced(new MixedBox[] {new MixedBox(1), null, new MixedBox(2)});
        String objectWithoutNull = reduced(new MixedBox[] {new MixedBox(1), new MixedBox(2)});
        assertNotEquals(reducedPartsForPath(objectWithNull, "x.value")[1],
                reducedPartsForPath(objectWithoutNull, "x.value")[1],
                "object-group null value CRC");

        String containerWithNull = reduced(new Object[] {new int[] {1}, null, new int[] {2}});
        String containerWithoutNull = reduced(new Object[] {new int[] {1}, new int[] {2}});
        assertNotEquals(reducedParts(containerWithNull)[1], reducedParts(containerWithoutNull)[1],
                "container-group null value CRC");
    }

    private static void testNullMapsHaveValueBucket() {
        String withNull = reduced(new Map[] {map("a", 1), null, map("b", 2)});
        String withoutNull = reduced(new Map[] {map("a", 1), map("b", 2)});
        String withTwoNulls = reduced(new Map[] {map("a", 1), null, null, map("b", 2)});

        assertContains(withNull, "x.null = [", "map null bucket");
        assertNotContains(withoutNull, "x.null = [", "map null bucket");
        assertNotEquals(reducedPartsForPath(withNull, "x.null")[1],
                reducedPartsForPath(withTwoNulls, "x.null")[1],
                "map null bucket count");
    }

    private static void testStructuralOnlyGroupsUseValueCrc() {
        assertNotEquals("0", reducedParts(reduced(new EmptyBox[] {new EmptyBox()}))[1],
                "fieldless object value CRC");
        assertNotEquals("0", reducedParts(reduced(new Object[] {}))[1],
                "empty array value CRC");
        assertNotEquals("0", reducedParts(reduced(new Map[] {new LinkedHashMap<>()}))[1],
                "empty map value CRC");

        assertNotEquals(reducedParts(reduced(new EmptyBox[] {new EmptyBox()}))[1],
                reducedParts(reduced(new EmptyBox[] {new EmptyBox(), null}))[1],
                "fieldless object null value CRC");
    }

    private static void testStringReductionPreservesValueSequence() {
        assertNotEquals(reducedParts(reduced(new String[] {"a", "a"}))[1],
                reducedParts(reduced(new String[] {"\0", "\0"}))[1],
                "string duplicate cancellation");
        assertNotEquals(reducedParts(reduced(new String[] {"ab", "cd"}))[1],
                reducedParts(reduced(new String[] {"cd", "ab"}))[1],
                "string element order");
        assertNotEquals(reducedParts(reduced(new String[] {"abc", null}))[1],
                reducedParts(reduced(new String[] {"abc"}))[1],
                "string null slot");
    }

    private static void printDrawingSample() {
        Drawing drawing = sampleDrawing();
        System.out.println("Drawing");
        System.out.println("  title = " + drawing.title);
        System.out.println("  visible = " + drawing.visible);
        System.out.println("  bounds = " + Arrays.toString(drawing.bounds));
        System.out.println("  meta.created = " + drawing.meta.created);
        System.out.println("  meta.tags = " + drawing.meta.tags);
        System.out.println("  figures = [");
        for (Figure figure : drawing.figures) {
            System.out.println("    " + figure);
            System.out.println("      points = " + figure.points);
        }
        System.out.println("  ]");
        System.out.println();
        System.out.println(reduced(drawing));
    }

    private static Drawing sampleDrawing() {
        Drawing drawing = new Drawing();
        drawing.title = "Floor plan";
        drawing.visible = true;
        drawing.bounds = new double[] {0.0, 0.0, 640.0, 480.0};
        drawing.meta = new DrawingMeta(178L, tags("draft", "fuzz", "valhalla"));
        drawing.figures = new ArrayList<>();
        drawing.figures.add(new PolygonFigure("outer", 0x224466, 0.75,
                points(point(0, 0, 0.1, 1, 2), point(20, 0, 0.2, 3), point(20, 10, 0.3, 5, 8)),
                3,
                new double[] {1.5, 2.5}));
        drawing.figures.add(new LabelFigure("label", 0x88aacc, 1.0,
                points(point(4, 5, 0.4, 13), point(8, 9, 0.5, 21, 34)),
                point(6, 7, 0.6, 55),
                "door"));
        return drawing;
    }

    private static String reduced(Object value) {
        return Printer.printReduced("x", value, Printer.Mode.REDUCED);
    }

    private static String[] reducedParts(String line) {
        String payload = reducedPayload(line);
        String[] parts = payload.split(":", -1);
        if (parts.length != 2) {
            throw new AssertionError("Expected shape:value output, got: " + line);
        }
        return parts;
    }

    private static String[] reducedPartsForPath(String output, String path) {
        String prefix = path + " = [";
        for (String line : output.split("\\R")) {
            if (line.startsWith(prefix) && line.endsWith("]")) {
                String payload = line.substring(prefix.length(), line.length() - 1);
                String[] parts = payload.split(":", -1);
                if (parts.length != 2) {
                    throw new AssertionError("Expected shape:value output, got: " + line);
                }
                return parts;
            }
        }
        throw new AssertionError("Path not found: " + path + "\n" + output);
    }

    private static String reducedPayload(String line) {
        String prefix = "x = [";
        String suffix = "]";
        if (!line.startsWith(prefix) || !line.endsWith(suffix)) {
            throw new AssertionError("Unexpected reduced output: " + line);
        }
        return line.substring(prefix.length(), line.length() - suffix.length());
    }

    private static void assertEquals(String expected, String actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + " mismatch: expected " + expected + ", got " + actual);
        }
    }

    private static void assertContains(String text, String expected, String label) {
        if (!text.contains(expected)) {
            throw new AssertionError(label + " missing: " + expected + "\n" + text);
        }
    }

    private static void assertNotContains(String text, String unexpected, String label) {
        if (text.contains(unexpected)) {
            throw new AssertionError(label + " unexpectedly present: " + unexpected + "\n" + text);
        }
    }

    private static void assertNotEquals(String left, String right, String label) {
        if (left.equals(right)) {
            throw new AssertionError(label + " unexpectedly matched: " + left);
        }
    }

    private static ArrayList<String> tags(String... values) {
        return new ArrayList<>(Arrays.asList(values));
    }

    private static ArrayList<Point> points(Point... values) {
        return new ArrayList<>(Arrays.asList(values));
    }

    private static Point point(int x, int y, double pressure, int... flags) {
        Point point = new Point();
        point.x = x;
        point.y = y;
        point.pressure = pressure;
        point.flags = new ArrayList<>();
        for (int flag : flags) {
            point.flags.add(flag);
        }
        return point;
    }

    private static Map<Object, Object> map(Object key, Object value) {
        LinkedHashMap<Object, Object> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }

    private static class Drawing {
        double[] bounds;
        ArrayList<Figure> figures;
        DrawingMeta meta;
        String title;
        boolean visible;
    }

    private static class DrawingMeta {
        long created;
        ArrayList<String> tags;

        DrawingMeta(long created, ArrayList<String> tags) {
            this.created = created;
            this.tags = tags;
        }
    }

    private abstract static class Figure {
        int color;
        String id;
        double opacity;
        ArrayList<Point> points;

        Figure(String id, int color, double opacity, ArrayList<Point> points) {
            this.id = id;
            this.color = color;
            this.opacity = opacity;
            this.points = points;
        }
    }

    private static class PolygonFigure extends Figure {
        int sides;
        double[] stroke;

        PolygonFigure(String id, int color, double opacity, ArrayList<Point> points, int sides, double[] stroke) {
            super(id, color, opacity, points);
            this.sides = sides;
            this.stroke = stroke;
        }

        @Override
        public String toString() {
            return "PolygonFigure{id=" + id + ", color=" + color + ", sides=" + sides
                    + ", stroke=" + Arrays.toString(stroke) + "}";
        }
    }

    private static class LabelFigure extends Figure {
        Point anchor;
        String text;

        LabelFigure(String id, int color, double opacity, ArrayList<Point> points, Point anchor, String text) {
            super(id, color, opacity, points);
            this.anchor = anchor;
            this.text = text;
        }

        @Override
        public String toString() {
            return "LabelFigure{id=" + id + ", color=" + color + ", text=" + text + ", anchor=" + anchor + "}";
        }
    }

    private static class Point {
        ArrayList<Integer> flags;
        double pressure;
        int x;
        int y;

        @Override
        public String toString() {
            return "Point{x=" + x + ", y=" + y + ", pressure=" + pressure + ", flags=" + flags + "}";
        }
    }

    private static class MixedBox {
        int value;

        MixedBox(int value) {
            this.value = value;
        }
    }

    private static class EmptyBox {
    }
}
