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
}
