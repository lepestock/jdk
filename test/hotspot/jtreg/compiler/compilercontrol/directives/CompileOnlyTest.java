/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @key randomness
 * @bug 8137167
 * @summary Tests directives to be able to compile only specified  methods
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver compiler.compilercontrol.directives.CompileOnlyTest
 */

package compiler.compilercontrol.directives;

import compiler.compilercontrol.share.SingleCommand;
import compiler.compilercontrol.share.scenario.Command;
import compiler.compilercontrol.share.scenario.Scenario;
import java.util.Arrays;


import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.List;

public class CompileOnlyTest {
    public static void main(String[] args) {
        { //FIXME JNP Remove
            RuntimeMXBean bean = ManagementFactory.getRuntimeMXBean();
            List<String> jvmArgs = bean.getInputArguments();

            for (int i = 0; i < jvmArgs.size(); i++) {
                System.out.println( jvmArgs.get( i ) );
            }
            System.out.println(" -classpath " + System.getProperty("java.class.path"));
            // print the non-JVM command line arguments
            // print name of the main class with its arguments, like org.ClassName param1 param2
            System.out.println(" " + System.getProperty("sun.java.command"));
        }

        if (false) { //FIXME JNP Remove
            System.out.println("JNP(environmentInfo");
            System.out.println(" :path " + System.getProperty("user.dir"));

            System.out.println(" :arguments '(");
            Arrays.stream(args)
                .forEach( entry -> System.out.println(" '" + entry + "'"));
            System.out.println(" )");

            System.out.println(" :environment '(");
            System.getenv()
                .entrySet()
                .stream()
                .forEach( entry -> System.out.println(" " + entry) );
            System.out.println(" )");

            System.out.println(" :properties '(");
            System.getProperties()
                .entrySet()
                .stream()
                .forEach( entry -> System.out.println(" " + entry));
            System.out.println("))");
        }
        new SingleCommand(Command.COMPILEONLY, Scenario.Type.DIRECTIVE)
            .test();
    }
}
