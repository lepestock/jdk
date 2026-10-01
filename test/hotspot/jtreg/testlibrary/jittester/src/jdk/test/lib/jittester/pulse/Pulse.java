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
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

/**
 * Minimal PulseMap prototype sink.
 * Writes one lisp-like beat record per line into a local .pulse file.
 */
public final class Pulse {
    private static final String INDENT = "  ";
    private static final String PULSE_FILE_PROPERTY = "jittester.pulse.file";
    private static final String PULSE_MAX_BYTES_PROPERTY = "jittester.pulse.max.bytes";
    private static final String METHOD_MAX_HITS_PROPERTY = "jittester.pulse.method.max.hits.per.site";
    private static final String METHODS_ONLY_PROPERTY = "jittester.pulse.methods.only";
    private static final int DEFAULT_MAX_RECORDS = 50_000;
    private static final long DEFAULT_MAX_BYTES = 1L << 30;
    private static final int MAX_RECORDS = Integer.getInteger("jittester.pulse.max.records", DEFAULT_MAX_RECORDS);
    private static final long MAX_BYTES = Long.getLong(PULSE_MAX_BYTES_PROPERTY, DEFAULT_MAX_BYTES);
    private static final long METHOD_MAX_HITS = Long.getLong(METHOD_MAX_HITS_PROPERTY, 0L);
    private static final boolean METHODS_ONLY = Boolean.getBoolean(METHODS_ONLY_PROPERTY);
    private static BufferedWriter writer;
    private static boolean stdoutWriter;
    private static int depth;
    private static int recordCount;
    private static long droppedCount;
    private static boolean truncatedNoteWritten;
    private static boolean outputLimitExceeded;
    private static final Map<String, Long> methodHits = new HashMap<>();

    private Pulse() {
    }

    public static void beat(String type, String payload) {
        if (outputLimitExceeded) {
            return;
        }
        if (METHODS_ONLY && !"method".equals(type)) {
            return;
        }
        if (isMethodHitOverLimit(type, payload)) {
            return;
        }
        try {
            ensureOpen();
            writeRecord(type, payload);
        } catch (IOException ignored) {
            // Pulse is a debug/metrics side-channel; never affect test semantics.
        }
    }

    public static void startScope(String type, String payload) {
        if (METHODS_ONLY) {
            return;
        }
        if (outputLimitExceeded) {
            return;
        }
        try {
            ensureOpen();
            if (shouldWriteRecord()) {
                writeIndent(depth);
                writer.write("(");
                writer.write(type == null ? "unknown" : type);
                if (payload != null && !payload.isBlank()) {
                    writer.write(" ");
                    writePayload(payload);
                }
                writer.newLine();
            }
            depth++;
        } catch (IOException ignored) {
            // Pulse is a debug/metrics side-channel; never affect test semantics.
        }
    }

    public static void endScope() {
        if (METHODS_ONLY) {
            return;
        }
        if (outputLimitExceeded) {
            return;
        }
        try {
            ensureOpen();
            if (depth > 0) {
                depth--;
            }
            if (shouldWriteRecord()) {
                writeIndent(depth);
                writer.write(")");
                writer.newLine();
            }
        } catch (IOException ignored) {
            // Pulse is a debug/metrics side-channel; never affect test semantics.
        }
    }

    private static void ensureOpen() throws IOException {
        if (writer != null) {
            return;
        }
        String configuredPath = System.getProperty(PULSE_FILE_PROPERTY, "").trim();
        if ("-".equals(configuredPath) || "stdout".equalsIgnoreCase(configuredPath)) {
            writer = new BufferedWriter(new OutputStreamWriter(
                    new SizeLimitedOutputStream(System.out, false, stdoutMaxBytes()), StandardCharsets.UTF_8));
            stdoutWriter = true;
            recordCount = 0;
            droppedCount = 0L;
            truncatedNoteWritten = false;
            Runtime.getRuntime().addShutdownHook(new Thread(Pulse::closeQuietly, "jittester-pulse-close"));
            return;
        }
        Path path = Paths.get(configuredPath.isEmpty() ? defaultPulseFileName() : configuredPath);
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        writer = new BufferedWriter(new OutputStreamWriter(
                new SizeLimitedOutputStream(Files.newOutputStream(path,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE), true, MAX_BYTES), StandardCharsets.UTF_8));
        recordCount = 0;
        droppedCount = 0L;
        truncatedNoteWritten = false;
        Runtime.getRuntime().addShutdownHook(new Thread(Pulse::closeQuietly, "jittester-pulse-close"));
    }

    private static long stdoutMaxBytes() {
        return System.getProperty(PULSE_MAX_BYTES_PROPERTY) == null ? 0L : MAX_BYTES;
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
            writeTruncationNoteIfNeeded();
            while (depth > 0) {
                depth--;
                if (recordCount < MAX_RECORDS || MAX_RECORDS <= 0) {
                    writeIndent(depth);
                    writer.write(")");
                    writer.newLine();
                    recordCount++;
                }
            }
            if (stdoutWriter) {
                writer.flush();
            } else {
                writer.close();
            }
        } catch (IOException ignored) {
        } finally {
            writer = null;
            stdoutWriter = false;
            depth = 0;
            recordCount = 0;
            droppedCount = 0L;
            truncatedNoteWritten = false;
            methodHits.clear();
        }
    }

    private static boolean isMethodHitOverLimit(String type, String payload) {
        if (!"method".equals(type) || METHOD_MAX_HITS <= 0) {
            return false;
        }
        String site = payload == null ? "" : payload;
        long hits = methodHits.getOrDefault(site, 0L);
        if (hits >= METHOD_MAX_HITS) {
            return true;
        }
        methodHits.put(site, hits + 1);
        return false;
    }

    private static void writeRecord(String type, String payload) throws IOException {
        if (!shouldWriteRecord()) {
            return;
        }
        writeIndent(depth);
        writer.write("(");
        writer.write(type == null ? "unknown" : type);
        if (payload != null && !payload.isBlank()) {
            writer.write(" ");
            writePayload(payload);
        }
        writer.write(")");
        writer.newLine();
    }

    private static void writePayload(String payload) throws IOException {
        for (int i = 0; i < payload.length(); i++) {
            char ch = payload.charAt(i);
            switch (ch) {
                case '\n' -> writer.write("\\n");
                case '\r' -> writer.write("\\r");
                case '\t' -> writer.write("\\t");
                default -> {
                    if (ch < 0x20 || ch == 0x7f) {
                        writer.write("\\u");
                        String hex = Integer.toHexString(ch);
                        for (int j = hex.length(); j < 4; j++) {
                            writer.write('0');
                        }
                        writer.write(hex);
                    } else {
                        writer.write(ch);
                    }
                }
            }
        }
    }

    private static boolean shouldWriteRecord() throws IOException {
        if (MAX_RECORDS <= 0) {
            return true;
        }
        if (recordCount < MAX_RECORDS) {
            recordCount++;
            return true;
        }
        droppedCount++;
        writeTruncationNoteIfNeeded();
        return false;
    }

    private static void writeTruncationNoteIfNeeded() throws IOException {
        if (MAX_RECORDS <= 0 || truncatedNoteWritten || droppedCount == 0L) {
            return;
        }
        writer.write("(pulse-truncated :max-records " + MAX_RECORDS
                + " :dropped-at-least " + droppedCount + ")");
        writer.newLine();
        truncatedNoteWritten = true;
    }

    private static void writeIndent(int level) throws IOException {
        for (int i = 0; i < level; i++) {
            writer.write(INDENT);
        }
    }

    private static final class SizeLimitedOutputStream extends OutputStream {
        private final OutputStream output;
        private final boolean closeOutput;
        private final long maxBytes;
        private long written;

        private SizeLimitedOutputStream(OutputStream output, boolean closeOutput, long maxBytes) {
            this.output = output;
            this.closeOutput = closeOutput;
            this.maxBytes = maxBytes;
        }

        @Override
        public void write(int b) throws IOException {
            reserve(1);
            output.write(b);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            reserve(length);
            output.write(bytes, offset, length);
        }

        @Override
        public void flush() throws IOException {
            output.flush();
        }

        @Override
        public void close() throws IOException {
            flush();
            if (closeOutput) {
                output.close();
            }
        }

        private void reserve(int length) {
            if (maxBytes <= 0 || length <= 0) {
                return;
            }
            if (written > maxBytes - length) {
                outputLimitExceeded = true;
                writer = null;
                stdoutWriter = false;
                throw new PulseOutputLimitExceededError(maxBytes, written, length);
            }
            written += length;
        }
    }

    /*
     * Large file-mode pulsemaps can accidentally grow to many gigabytes.
     * Keep a hard byte cap by default for file output so ad-hoc debug runs fail
     * loudly instead of filling disks. Streaming stdout output is uncapped
     * unless -Djittester.pulse.max.bytes is explicitly set. For
     * method-reachability scoring, set -Djittester.pulse.methods.only=true to
     * suppress block/scope pulses.
     */
    private static final class PulseOutputLimitExceededError extends Error {
        private static final long serialVersionUID = 1L;

        private PulseOutputLimitExceededError(long maxBytes, long written, int requested) {
            super("JitTester PulseMap output exceeded "
                    + PULSE_MAX_BYTES_PROPERTY + "=" + maxBytes
                    + " bytes after " + written
                    + " bytes, while writing " + requested
                    + " more bytes");
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
