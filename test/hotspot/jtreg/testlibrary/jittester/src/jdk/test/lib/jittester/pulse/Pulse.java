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

package jdk.test.lib.jittester.pulse;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * Minimal PulseMap prototype sink.
 * Writes one lisp-like beat record per line into a local .pulse file.
 */
public final class Pulse {
    private static final String INDENT = "  ";
    private static BufferedWriter writer;
    private static int depth;

    private Pulse() {
    }

    public static void beat(String type, String payload) {
        try {
            ensureOpen();
            writeRecord(type, payload);
        } catch (IOException ignored) {
            // Pulse is a debug/metrics side-channel; never affect test semantics.
        }
    }

    public static void startScope(String type, String payload) {
        try {
            ensureOpen();
            writeIndent(depth);
            writer.write("(");
            writer.write(type == null ? "unknown" : type);
            if (payload != null && !payload.isBlank()) {
                writer.write(" ");
                writer.write(payload);
            }
            writer.newLine();
            depth++;
        } catch (IOException ignored) {
            // Pulse is a debug/metrics side-channel; never affect test semantics.
        }
    }

    public static void endScope() {
        try {
            ensureOpen();
            if (depth > 0) {
                depth--;
            }
            writeIndent(depth);
            writer.write(")");
            writer.newLine();
        } catch (IOException ignored) {
            // Pulse is a debug/metrics side-channel; never affect test semantics.
        }
    }

    private static void ensureOpen() throws IOException {
        if (writer != null) {
            return;
        }
        Path path = Paths.get(defaultPulseFileName());
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        Runtime.getRuntime().addShutdownHook(new Thread(Pulse::closeQuietly, "jittester-pulse-close"));
    }

    private static String defaultPulseFileName() {
        String cmd = System.getProperty("sun.java.command", "").trim();
        if (cmd.isEmpty()) {
            return "run.pulse";
        }
        int firstSpace = cmd.indexOf(' ');
        String mainToken = firstSpace >= 0 ? cmd.substring(0, firstSpace) : cmd;
        if (mainToken.isBlank()) {
            return "run.pulse";
        }
        int lastDot = mainToken.lastIndexOf('.');
        String simpleName = lastDot >= 0 ? mainToken.substring(lastDot + 1) : mainToken;
        if (simpleName.isBlank()) {
            return "run.pulse";
        }
        return simpleName + ".pulse";
    }

    private static void closeQuietly() {
        if (writer == null) {
            return;
        }
        try {
            while (depth > 0) {
                depth--;
                writeIndent(depth);
                writer.write(")");
                writer.newLine();
            }
            writer.close();
        } catch (IOException ignored) {
        } finally {
            writer = null;
            depth = 0;
        }
    }

    private static void writeRecord(String type, String payload) throws IOException {
        writeIndent(depth);
        writer.write("(");
        writer.write(type == null ? "unknown" : type);
        if (payload != null && !payload.isBlank()) {
            writer.write(" ");
            writer.write(payload);
        }
        writer.write(")");
        writer.newLine();
    }

    private static void writeIndent(int level) throws IOException {
        for (int i = 0; i < level; i++) {
            writer.write(INDENT);
        }
    }

    public static byte byteArrayRead(byte[] array, int index, String payload) {
        return logArrayReadAndGet(array, index, payload);
    }

    public static short shortArrayRead(short[] array, int index, String payload) {
        return logArrayReadAndGet(array, index, payload);
    }

    public static int intArrayRead(int[] array, int index, String payload) {
        return logArrayReadAndGet(array, index, payload);
    }

    public static long longArrayRead(long[] array, int index, String payload) {
        return logArrayReadAndGet(array, index, payload);
    }

    public static float floatArrayRead(float[] array, int index, String payload) {
        return logArrayReadAndGet(array, index, payload);
    }

    public static double doubleArrayRead(double[] array, int index, String payload) {
        return logArrayReadAndGet(array, index, payload);
    }

    public static char charArrayRead(char[] array, int index, String payload) {
        return logArrayReadAndGet(array, index, payload);
    }

    public static boolean booleanArrayRead(boolean[] array, int index, String payload) {
        return logArrayReadAndGet(array, index, payload);
    }

    public static <T> T objectArrayRead(T[] array, int index, String payload) {
        return logArrayReadAndGet(array, index, payload);
    }

    private static byte logArrayReadAndGet(byte[] array, int index, String payload) {
        int length = array == null ? -1 : array.length;
        boolean inBounds = array != null && index >= 0 && index < length;
        if (inBounds) {
            byte value = array[index];
            beat("array-read", appendArrayReadPayload(payload, index, length, true, Byte.toString(value)));
            return value;
        }
        beat("array-read", appendArrayReadPayload(payload, index, length, false, "n/a"));
        return array[index];
    }

    private static short logArrayReadAndGet(short[] array, int index, String payload) {
        int length = array == null ? -1 : array.length;
        boolean inBounds = array != null && index >= 0 && index < length;
        if (inBounds) {
            short value = array[index];
            beat("array-read", appendArrayReadPayload(payload, index, length, true, Short.toString(value)));
            return value;
        }
        beat("array-read", appendArrayReadPayload(payload, index, length, false, "n/a"));
        return array[index];
    }

    private static int logArrayReadAndGet(int[] array, int index, String payload) {
        int length = array == null ? -1 : array.length;
        boolean inBounds = array != null && index >= 0 && index < length;
        if (inBounds) {
            int value = array[index];
            beat("array-read", appendArrayReadPayload(payload, index, length, true, Integer.toString(value)));
            return value;
        }
        beat("array-read", appendArrayReadPayload(payload, index, length, false, "n/a"));
        return array[index];
    }

    private static long logArrayReadAndGet(long[] array, int index, String payload) {
        int length = array == null ? -1 : array.length;
        boolean inBounds = array != null && index >= 0 && index < length;
        if (inBounds) {
            long value = array[index];
            beat("array-read", appendArrayReadPayload(payload, index, length, true, Long.toString(value)));
            return value;
        }
        beat("array-read", appendArrayReadPayload(payload, index, length, false, "n/a"));
        return array[index];
    }

    private static float logArrayReadAndGet(float[] array, int index, String payload) {
        int length = array == null ? -1 : array.length;
        boolean inBounds = array != null && index >= 0 && index < length;
        if (inBounds) {
            float value = array[index];
            beat("array-read", appendArrayReadPayload(payload, index, length, true, Float.toString(value)));
            return value;
        }
        beat("array-read", appendArrayReadPayload(payload, index, length, false, "n/a"));
        return array[index];
    }

    private static double logArrayReadAndGet(double[] array, int index, String payload) {
        int length = array == null ? -1 : array.length;
        boolean inBounds = array != null && index >= 0 && index < length;
        if (inBounds) {
            double value = array[index];
            beat("array-read", appendArrayReadPayload(payload, index, length, true, Double.toString(value)));
            return value;
        }
        beat("array-read", appendArrayReadPayload(payload, index, length, false, "n/a"));
        return array[index];
    }

    private static char logArrayReadAndGet(char[] array, int index, String payload) {
        int length = array == null ? -1 : array.length;
        boolean inBounds = array != null && index >= 0 && index < length;
        if (inBounds) {
            char value = array[index];
            beat("array-read", appendArrayReadPayload(payload, index, length, true, Character.toString(value)));
            return value;
        }
        beat("array-read", appendArrayReadPayload(payload, index, length, false, "n/a"));
        return array[index];
    }

    private static boolean logArrayReadAndGet(boolean[] array, int index, String payload) {
        int length = array == null ? -1 : array.length;
        boolean inBounds = array != null && index >= 0 && index < length;
        if (inBounds) {
            boolean value = array[index];
            beat("array-read", appendArrayReadPayload(payload, index, length, true, Boolean.toString(value)));
            return value;
        }
        beat("array-read", appendArrayReadPayload(payload, index, length, false, "n/a"));
        return array[index];
    }

    private static <T> T logArrayReadAndGet(T[] array, int index, String payload) {
        int length = array == null ? -1 : array.length;
        boolean inBounds = array != null && index >= 0 && index < length;
        if (inBounds) {
            T value = array[index];
            beat("array-read", appendArrayReadPayload(payload, index, length, true, String.valueOf(value)));
            return value;
        }
        beat("array-read", appendArrayReadPayload(payload, index, length, false, "n/a"));
        return array[index];
    }

    private static String appendArrayReadPayload(String payload, int index, int length,
            boolean inBounds, String valueString) {
        String base = payload == null ? "" : payload.trim();
        if (!base.isEmpty()) {
            base += " ";
        }
        return base
                + ":index " + index
                + " :length " + length
                + " :inBounds " + inBounds
                + " :value " + valueString;
    }
}
