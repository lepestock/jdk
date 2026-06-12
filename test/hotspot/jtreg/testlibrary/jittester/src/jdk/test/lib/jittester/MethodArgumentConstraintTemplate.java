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

package jdk.test.lib.jittester;

import java.lang.reflect.Executable;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.test.lib.Asserts;
import jdk.test.lib.jittester.functions.FunctionInfo;

/**
 * Method-template rule describing generation constraints for selected method
 * arguments.
 *
 * <p>The left side uses the same method-template format as excluded and
 * intrinsic method lists. The right side maps one-based, source-visible
 * argument numbers to constraints, for example:</p>
 *
 * <pre>
 * java/lang/Math::ceilDivExact(II) -> arg#2=nonzero
 * java/lang/Integer::remainderUnsigned(II) -> arg#2=nonzero
 * </pre>
 *
 * <p>Instance-method receiver expressions are internal implementation details
 * and are not counted by {@code arg#N}. If a rule ever targets an instance
 * method, {@code arg#1} still means the first printed Java call argument.</p>
 */
public final class MethodArgumentConstraintTemplate {
    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("(?<method>.+?)\\s*->\\s*(?<args>.+)");
    private static final Pattern ARGUMENT_PATTERN = Pattern.compile("arg#(?<index>\\d+)\\s*=\\s*(?<constraint>[a-z][a-z0-9_-]*)");

    private final MethodTemplate methodTemplate;
    private final Map<Integer, MethodArgumentConstraint> visibleArgumentConstraints;

    private MethodArgumentConstraintTemplate(MethodTemplate methodTemplate,
            Map<Integer, MethodArgumentConstraint> visibleArgumentConstraints) {
        this.methodTemplate = methodTemplate;
        this.visibleArgumentConstraints = visibleArgumentConstraints;
    }

    public static MethodArgumentConstraintTemplate parse(String line) {
        Matcher matcher = TEMPLATE_PATTERN.matcher(line);
        String msg = String.format("Format of method argument constraints line \"%s\" is incorrect", line);
        Asserts.assertTrue(matcher.matches(), msg);

        MethodTemplate methodTemplate = MethodTemplate.parse(normalizeMethodTemplate(matcher.group("method").trim()));
        Map<Integer, MethodArgumentConstraint> argumentConstraints = new LinkedHashMap<>();
        for (String argSpec : matcher.group("args").split(",")) {
            Matcher argMatcher = ARGUMENT_PATTERN.matcher(argSpec.trim());
            Asserts.assertTrue(argMatcher.matches(), msg);

            int visibleIndex = Integer.parseInt(argMatcher.group("index"));
            Asserts.assertTrue(visibleIndex > 0, "Method argument index must be one-based: " + line);
            argumentConstraints.put(visibleIndex, MethodArgumentConstraint.parse(argMatcher.group("constraint")));
        }
        Asserts.assertFalse(argumentConstraints.isEmpty(), "No method argument constraints in line: " + line);
        return new MethodArgumentConstraintTemplate(methodTemplate, argumentConstraints);
    }

    public static void applyAll(Collection<MethodArgumentConstraintTemplate> templates,
            Executable method, FunctionInfo functionInfo) {
        if (templates == null || templates.isEmpty()) {
            return;
        }
        for (MethodArgumentConstraintTemplate template : templates) {
            template.apply(method, functionInfo);
        }
    }

    private void apply(Executable method, FunctionInfo functionInfo) {
        if (!methodTemplate.matches(method)) {
            return;
        }
        int internalOffset = functionInfo.isStatic() || functionInfo.isConstructor() ? 0 : 1;
        int visibleArgumentCount = functionInfo.argTypes.size() - internalOffset;
        for (Map.Entry<Integer, MethodArgumentConstraint> entry : visibleArgumentConstraints.entrySet()) {
            int visibleIndex = entry.getKey();
            Asserts.assertTrue(visibleIndex <= visibleArgumentCount,
                    "Method argument constraint index is out of range for " + method);
            functionInfo.setArgumentConstraint(internalOffset + visibleIndex - 1, entry.getValue());
        }
    }

    private static String normalizeMethodTemplate(String source) {
        int open = source.indexOf('(');
        int close = source.lastIndexOf(')');
        if (open >= 0 && close >= open && close + 1 < source.length()) {
            return source.substring(0, close + 1);
        }
        return source;
    }
}
