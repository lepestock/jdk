/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib.jittester;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jdk.test.lib.jittester.types.TypeKlass;
import jdk.test.lib.jittester.utils.PseudoRandom;

public class Logger {
    private static final HashSet<String> ownerClassFilter = new HashSet<>(Set.of("Gentest_Class_3"));
//    private static final String[] breakpoints = new String[] { "false ? this : this"};
    private static final HashSet<String> breakpoints = new HashSet<>(Set.of("CDF."));
//    private static final String[] breakpoints = new String[] { };
    private static final String breakCode = null;
//    private static final boolean loggingEnabled = Boolean.getBoolean("jittester.log.enabled");

    /**
     * Checks if message properties allow it to be printed
     */
    private static boolean checkMessage(String ownerClass, String point) {
        // Checking the class name
        boolean result = ownerClassFilter.contains(ownerClass);
//        boolean result = (Arrays.stream(ownerClassFilter).anyMatch(ownerClass.toString()::equals));

        // Checking the point name (may start with ':', as in Lisp)
        result &= breakpoints.stream().anyMatch(
                breakpoint -> point.startsWith(breakpoint) || point.startsWith(":" + breakpoint));

        return result;
    }

    public static void enableTrace() {
        enableClass("TRACE");
        enableBreakpoint("trace");
    }

    public static void disableTrace() {
        disableClass("TRACE");
        disableBreakpoint("trace");
    }

    public static void trace(String message) {
        log("TRACE", ":trace", message);
    }

    public static boolean isTraceEnabled() {
        return (ownerClassFilter.contains("TRACE") && breakpoints.contains("trace"));
    }

    public static void enableBreakpoint(String breakpoint) {
        breakpoints.add(breakpoint);
    }

    public static void disableBreakpoint(String breakpoint) {
        breakpoints.remove(breakpoint);
    }

    public static void enableClass(String klass) {
        ownerClassFilter.add(klass);
    }

    public static void disableClass(String klass) {
        ownerClassFilter.remove(klass);
    }

    public static void log(TypeKlass ownerClass, String point, String message) {
        log(ownerClass.toString(), point, message);
    }

    public static void log(String ownerClass, String point, String message) {
        if (checkMessage(ownerClass, point)) {
            System.out.println("(log :class " + ownerClass + " " + point + " " + message + ")");
        }
    }

    public static void log(TypeKlass ownerClass, String point, List<IRNode> nodes) {
        if (checkMessage(ownerClass.toString(), point)) {
//        if (loggingEnabled && Arrays.stream(ownerClassFilter).anyMatch(ownerClass.toString()::equals)) {
//        if (ownerClass.toString().equals("Gentest_Value_Class_1")) {
            System.out.println("\n\n==== " + point + " ====");

            String result = Formatter.format(nodes);
            System.out.println(result);
            if (breakpoints.stream().anyMatch(result::equals) && breakCode != null && breakCode.equals(point)) {
                throw new Error("Breakpoint reached");
            }
        }
    }

    public static void log(String ownerClass, String point, IRNode node) {
        if (checkMessage(ownerClass.toString(), point)) {
            System.out.println("\n\n(" + ownerClass + " " + point);
            System.out.println("    " + Formatter.format(node) + ")");
        }
    }
    public static void log(TypeKlass ownerClass, String point, IRNode node)
    { log (ownerClass.toString(), point, node); }

    public static void log(boolean condition, String message) {
        if (condition) {
            System.out.println("ASSERT: " + message);
        }
    }

    public static void forSeed(long seed, String message) {
        if (PseudoRandom.getCurrentSeed() == seed) {
            System.out.println(message);
        }
    }

}
