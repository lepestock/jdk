/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Example test with multi-threaded use of the CompileFramework.
 *          Tests that the source and class directories are set up correctly.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run driver compile_framework.tests.TestConcurrentCompilation
 */

package compile_framework.tests;

import compiler.lib.compile_framework.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import jdk.test.lib.Asserts;
import java.util.stream.IntStream;
import java.util.concurrent.Future;
import java.util.stream.Stream;

public class TestConcurrentCompilation {

    // Generate a source java file as String
    public static String generate(int i) {
        return String.format("""
                             public class XYZ {
                                 public static int test() {
                                     return %d;
                                 }
                             }
                             """, i);
    }

    public static int test(int i) {
        System.out.println("Generate and compile XYZ for " + i);
        CompileFramework comp = new CompileFramework();
        comp.add(SourceCode.newJavaSourceCode("XYZ", generate(i)));
        comp.compile();

        // Now, sleep to give the other threads time to compile and store their class-files.
        System.out.println("Sleep for " + i);
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            System.out.println("Sleep interrupted for " + i);
        }

        // Now, hopefully all threads have compiled and stored their class-files.
        // We can check if we get the expected result, i.e. the class-file from the current thread.
        System.out.println("Executing XYZ.test for " + i);
        int result = (int)comp.invoke("XYZ", "test", new Object[] {});
        return result;
    }

    public static class MyRunnable implements Runnable {
        private int i;

        public MyRunnable(int i) {
            this.i = i;
        }

        public void run() {
            TestConcurrentCompilation.test(i);
        }
    }

    public static void main(String args[]) throws Exception {
        System.out.println("Generating threads:");
        final int GENERATORS = 3;
        var es = Executors.newFixedThreadPool(GENERATORS);
        Future[] results = IntStream.range(0, GENERATORS).boxed()
            .map(i -> es.submit(() -> {
                return TestConcurrentCompilation.test(i);
            }))
            .toArray(Future[]::new);

        for (int i = 0; i < GENERATORS; i++) {
            Asserts.assertEquals(i, results[i].get());
        }
    }
}
