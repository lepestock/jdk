/*
 * Copyright (c) 2023, Red Hat, Inc.
 *
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
 * @bug 9999999
 * @requires (jdk.version.major >= 17)
 * @run main/othervm/timeout=30 -XX:CompileCommand=compileonly,POC::jitted* -XX:TieredStopAtLevel=1 POC
 * @author Martin Balao Alonso (mbalao@redhat.com)
 */

public final class POC {
    private static void jitted(int val, int val1) {
        int b = 0;
        int[] array = {0, 1, 2};
        val1 = val1 % 2 + val1;
        if (val >= 0x7fffffff - 2 + val % 3 || val < 0) {
            return;
        }
        int i = array.length + val;
        i += val1;
        b += array[i + 0x1 - 0x1 + 0x80000000];
        b += array[i + 0x0 + 0x1];
        b += array[i + 0x2];
    }

    public static void main(String[] args) throws Throwable {
        for (int i = 0; i < 100_000; ++i) {
            try {
                jitted(0x7fffffff - 2, 0x80000001);
                throw new RuntimeException("No exception thrown");
            } catch (java.lang.ArrayIndexOutOfBoundsException e) {
                // Expected
            }
        }
        System.out.println("TEST PASS - OK");
    }
}
