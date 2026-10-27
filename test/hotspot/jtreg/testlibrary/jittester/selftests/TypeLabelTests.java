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

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javax.tools.ToolProvider;
import jdk.test.lib.jittester.classes.Interface;
import jdk.test.lib.jittester.classes.Klass;
import jdk.test.lib.jittester.classes.MainKlass;
import jdk.test.lib.jittester.classes.RecordKlass;
import jdk.test.lib.jittester.classes.ValueKlass;
import jdk.test.lib.jittester.types.TypeKlass;
import jdk.test.lib.jittester.visitors.JavaCodeVisitor;

public class TypeLabelTests {
    public static void main(String[] args) throws Exception {
        ProductionParams.initializeFromCmdline(new String[0]);
        require(!ProductionParams.debugTypeLabels.value(), "Diagnostics must default to off");
        var parameters = ProductionParams.generationParameters();
        JavaCodeVisitor visitor = new JavaCodeVisitor();
        TypeKlass identity = new TypeKlass("LabelIdentity");
        TypeKlass value = new TypeKlass("LabelValue");
        value.setValueKlass();
        TypeKlass record = new TypeKlass("LabelRecord");
        record.setRecord();
        TypeKlass valueRecord = new TypeKlass("LabelValueRecord");
        valueRecord.setRecord();
        valueRecord.setValueKlass();
        TypeKlass iface = new TypeKlass("LabelInterface");
        TypeList.add(iface);
        TypeKlass main = new TypeKlass("LabelMain");
        List<IRNode> declarations = List.of(
                identityDeclaration(identity),
                new ValueKlass(value, null, new ArrayList<>(), value.getName(), 0,
                        null, null, null, null, null, null, new Nothing()),
                recordDeclaration(record), recordDeclaration(valueRecord),
                new Interface(null, iface.getName(), 0, new Block(iface, TypeList.VOID, List.of(), 0)),
                new MainKlass(main.getName(), main, new Nothing(), new Nothing(), new Nothing(), new Nothing()));
        List<String> plainSources = new ArrayList<>();
        for (IRNode declaration : declarations) {
            TypeKlass type = (TypeKlass) declaration.getResultType();
            type.addLabel("generic-label");
            type.addLabel("second-label");
            type.addLabel("generic-label");
            String source = declaration.accept(visitor);
            require(!source.contains("// Type labels:"), "Labels printed with diagnostics disabled");
            plainSources.add(source);
        }
        ProductionParams.initializeFromCmdline(new String[] {"--debug-type-labels"});
        require(parameters.equals(ProductionParams.generationParameters()),
                "Diagnostics changed the generation policy snapshot");
        for (int i = 0; i < declarations.size(); i++) {
            String source = declarations.get(i).accept(visitor);
            require(source.equals("// Type labels: generic-label, second-label\n" + plainSources.get(i)),
                    "Unexpected label output: " + source);
        }
        Klass unlabelled = identityDeclaration(new TypeKlass("Unlabelled"));
        require(!unlabelled.accept(visitor).contains("// Type labels:"), "Printed an empty label comment");
        testCommentEscaping(visitor);
        System.out.println("TypeLabelTests passed");
    }

    private static void testCommentEscaping(JavaCodeVisitor visitor) throws Exception {
        TypeKlass type = new TypeKlass("EscapedLabels");
        Klass declaration = identityDeclaration(type);
        String plain = declaration.accept(visitor);
        type.addLabel("first\nsecond\rthird");
        type.addLabel("literal-" + '\\' + "u000a */");
        String source = declaration.accept(visitor);
        require(source.equals("// Type labels: first second third, literal-\\\\u000a */\n" + plain),
                "Label broke out of its comment: " + source);
        var work = Files.createTempDirectory("jittester-type-labels-");
        var file = work.resolve(type.getName() + ".java");
        Files.writeString(file, source);
        require(ToolProvider.getSystemJavaCompiler().run(null, null, null, "-d", work.toString(),
                file.toString()) == 0, "Label comments made valid Java uncompilable");
    }

    private static Klass identityDeclaration(TypeKlass type) {
        return new Klass(type, null, new ArrayList<>(), type.getName(), 0,
                null, null, null, null, null, null, new Nothing());
    }

    private static RecordKlass recordDeclaration(TypeKlass type) {
        return new RecordKlass(type, new ArrayList<>(), type.getName(), 0,
                new ArrayList<>(), null, null, null, new Nothing());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
