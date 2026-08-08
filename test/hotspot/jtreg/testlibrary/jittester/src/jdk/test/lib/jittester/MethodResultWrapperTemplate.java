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

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.test.lib.Asserts;
import jdk.test.lib.jittester.functions.FunctionInfo;

/**
 * Method-template rule describing wrappers to apply to selected method results.
 *
 * <p>The left side uses the same method-template format as excluded and
 * intrinsic method lists. The right side names the wrapper, for example:</p>
 *
 * <pre>
 * java/lang/Math::pow(DD)D -> normalize-nan
 * </pre>
 */
public final class MethodResultWrapperTemplate {
    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("(?<method>.+?)\\s*->\\s*(?<wrapper>[a-z][a-z0-9_-]*)");

    private final MethodTemplate methodTemplate;
    private final MethodResultWrapper wrapper;

    private MethodResultWrapperTemplate(MethodTemplate methodTemplate, MethodResultWrapper wrapper) {
        this.methodTemplate = methodTemplate;
        this.wrapper = wrapper;
    }

    public static MethodResultWrapperTemplate parse(String line) {
        Matcher matcher = TEMPLATE_PATTERN.matcher(line);
        String msg = String.format("Format of method result wrappers line \"%s\" is incorrect", line);
        Asserts.assertTrue(matcher.matches(), msg);
        return new MethodResultWrapperTemplate(
                MethodTemplate.parse(normalizeMethodTemplate(matcher.group("method").trim())),
                MethodResultWrapper.parse(matcher.group("wrapper")));
    }

    public static void applyAll(Collection<MethodResultWrapperTemplate> templates, FunctionInfo functionInfo) {
        if (templates == null || templates.isEmpty()) {
            return;
        }
        for (MethodResultWrapperTemplate template : templates) {
            template.apply(functionInfo);
        }
    }

    private void apply(FunctionInfo functionInfo) {
        if (methodTemplate.matches(functionInfo)) {
            functionInfo.setResultWrapper(wrapper);
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
