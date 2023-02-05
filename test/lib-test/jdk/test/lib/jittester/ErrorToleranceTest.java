/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import org.testng.annotations.Test;

/*
 * @test
 * @summary Check execution process
 * @library /test/lib /test/hotspot/jtreg/testlibrary/jittester/src
 * @run testng jdk.test.lib.jittester.ErrorToleranceTest
 */
public class ErrorToleranceTest {

    @Test
    public void allowsForIdenticalStacks() {
        String[] gold = new String[] {
            "Exception in thread \"main\" java.lang.SomeException: Java heap space",
            "	at java.base/jdk.internal.misc.Unsafe.allocateUninitializedArray0(Unsafe.java:1387)",
            "	at java.base/jdk.internal.misc.Unsafe.allocateUninitializedArray(Unsafe.java:1380)",
            "	at java.base/java.lang.StringConcatHelper.newArray(StringConcatHelper.java:441)"
        };

        String[] run = new String[] {
            "Exception in thread \"main\" java.lang.SomeException: Java heap space",
            "	at java.base/jdk.internal.misc.Unsafe.allocateUninitializedArray0(Unsafe.java:1387)",
            "	at java.base/jdk.internal.misc.Unsafe.allocateUninitializedArray(Unsafe.java:1380)",
            "	at java.base/java.lang.StringConcatHelper.newArray(StringConcatHelper.java:441)"
        };

        ErrorTolerance.assertIsAcceptable("", Arrays.stream(gold), Arrays.stream(run));
    }

    @Test(expectedExceptions = { java.lang.RuntimeException.class })
    public void signalsDifferentStacks() {
        String[] gold = new String[] {
            "Exception in thread \"main\" java.lang.SomeException: Java heap space",
            "	at java.base/jdk.internal.misc.Unsafe.allocateUninitializedArray0(Unsafe.java:1387)",
            "	at java.base/jdk.internal.misc.Unsafe.allocateUninitializedArray(Unsafe.java:1380)",
            "	at java.base/java.lang.StringConcatHelper.newArray(StringConcatHelper.java:441)"
        };

        String[] run = new String[] {
            "Exception in thread \"main\" java.lang.SomeException: Java heap space",
            "	at wrong.module/some.WrongObject.method(WrongObject.java:10)",
            "	at java.base/jdk.internal.misc.Unsafe.allocateUninitializedArray0(Unsafe.java:1387)",
            "	at java.base/jdk.internal.misc.Unsafe.allocateUninitializedArray(Unsafe.java:1380)",
            "	at java.base/java.lang.StringConcatHelper.newArray(StringConcatHelper.java:441)"
        };

        ErrorTolerance.assertIsAcceptable("", Arrays.stream(gold), Arrays.stream(run));
    }

    @Test
    public void skipsIntrinsicCandidateFrames() {
        String[] gold = new String[] {
            "Exception in thread \"main\" java.lang.SomeException: Java heap space",
            "	at java.base/jdk.internal.misc.Unsafe.allocateUninitializedArray0(Unsafe.java:1387)",
            //        ^^^^ An intrinsic candidate ^^^^
            "	at java.base/jdk.internal.misc.Unsafe.allocateUninitializedArray(Unsafe.java:1380)",
            "	at java.base/java.lang.StringConcatHelper.newArray(StringConcatHelper.java:441)"
        };

        String[] run = new String[] {
            "Exception in thread \"main\" java.lang.SomeException: Java heap space",
            "	at java.base/jdk.internal.misc.Unsafe.allocateUninitializedArray(Unsafe.java:1380)",
            "	at java.base/java.lang.StringConcatHelper.newArray(StringConcatHelper.java:441)"
        };

        ErrorTolerance.assertIsAcceptable("", Arrays.stream(gold), Arrays.stream(run));
    }

    @Test(expectedExceptions = { java.lang.RuntimeException.class })
    public void doesNotSkipIntrinsicCandidateFramesInGold() {
        String[] gold = new String[] {
            "Exception in thread \"main\" java.lang.SomeException: Java heap space",
            "	at java.base/jdk.internal.misc.Unsafe.allocateUninitializedArray(Unsafe.java:1380)",
            "	at java.base/java.lang.StringConcatHelper.newArray(StringConcatHelper.java:441)"
        };

        String[] run = new String[] {
            "Exception in thread \"main\" java.lang.SomeException: Java heap space",
            "	at java.base/jdk.internal.misc.Unsafe.allocateUninitializedArray0(Unsafe.java:1387)",
            //        ^^^^ An intrinsic candidate ^^^^
            "	at java.base/jdk.internal.misc.Unsafe.allocateUninitializedArray(Unsafe.java:1380)",
            "	at java.base/java.lang.StringConcatHelper.newArray(StringConcatHelper.java:441)"
        };

        ErrorTolerance.assertIsAcceptable("", Arrays.stream(gold), Arrays.stream(run));
    }

    @Test(expectedExceptions = { java.lang.RuntimeException.class })
    public void intrinsicCandidateDoesNotPreventChecks() {
        String[] gold = new String[] {
            "Exception in thread \"main\" java.lang.SomeException: Java heap space",
            "	at java.base/jdk.internal.misc.Unsafe.allocateUninitializedArray0(Unsafe.java:1387)",
            //        ^^^^ An intrinsic candidate ^^^^
            "	at java.base/jdk.internal.misc.Unsafe.allocateUninitializedArray(Unsafe.java:1380)",
            "	at java.base/java.lang.StringConcatHelper.newArray(StringConcatHelper.java:441)"
        };

        String[] run = new String[] {
            "Exception in thread \"main\" java.lang.SomeException: Java heap space",
            "	at erroneous.module/some.WrongClazz.wrongMethod(WrongClazz.java:13)",
            "	at java.base/java.lang.StringConcatHelper.newArray(StringConcatHelper.java:441)"
        };

        ErrorTolerance.assertIsAcceptable("", Arrays.stream(gold), Arrays.stream(run));
    }

    @Test(expectedExceptions = { java.lang.RuntimeException.class })
    public void checksTheWholeStack1() {
        String[] gold = new String[] {
            "Exception in thread \"main\" java.lang.SomeException: Java heap space",
            "	at java.base/jdk.internal.misc.Unsafe.allocateUninitializedArray(Unsafe.java:1380)",
            "	at java.base/java.lang.StringConcatHelper.newArray(StringConcatHelper.java:441)",
            "	at java.base/java.lang.StringConcatHelper.simpleConcat(StringConcatHelper.java:365)",
            "	at java.base/java.lang.invoke.DirectMethodHandle$Holder.invokeStatic(DirectMethodHandle$Holder)"
        };

        String[] run = new String[] {
            "Exception in thread \"main\" java.lang.SomeException: Java heap space",
            "	at java.base/jdk.internal.misc.Unsafe.allocateUninitializedArray(Unsafe.java:1380)",
            "	at java.base/java.lang.StringConcatHelper.newArray(StringConcatHelper.java:441)"
        };

        ErrorTolerance.assertIsAcceptable("", Arrays.stream(gold), Arrays.stream(run));
    }

    @Test(expectedExceptions = { java.lang.RuntimeException.class })
    public void checksTheWholeStack2() {
        String[] gold = new String[] {
            "Exception in thread \"main\" java.lang.SomeException: Java heap space",
            "	at java.base/jdk.internal.misc.Unsafe.allocateUninitializedArray(Unsafe.java:1380)",
            "	at java.base/java.lang.StringConcatHelper.newArray(StringConcatHelper.java:441)",
            "	at java.base/java.lang.StringConcatHelper.simpleConcat(StringConcatHelper.java:365)",
            "	at java.base/java.lang.invoke.DirectMethodHandle$Holder.invokeStatic(DirectMethodHandle$Holder)"
        };

        String[] run = new String[] {
            "Exception in thread \"main\" java.lang.SomeException: Java heap space",
            "	at java.base/jdk.internal.misc.Unsafe.allocateUninitializedArray(Unsafe.java:1380)",
            "	at java.base/java.lang.StringConcatHelper.newArray(StringConcatHelper.java:441)"
        };

        ErrorTolerance.assertIsAcceptable("", Arrays.stream(run), Arrays.stream(gold));
    }

    @Test
    public void ignoresDifferencesWithOOME() {
        //OOME is too difficult to verify reliably, so we should just ignore such stack traces

        String[] gold = new String[] {
            "Exception in thread \"main\" java.lang.OutOfMemoryError: Java heap space",
            "	at java.base/jdk.internal.misc.Unsafe.allocateUninitializedArray0(Unsafe.java:1387)",
            "	at java.base/jdk.internal.misc.Unsafe.allocateUninitializedArray(Unsafe.java:1380)",
            "	at java.base/java.lang.StringConcatHelper.newArray(StringConcatHelper.java:441)",
            "	at java.base/java.lang.invoke.DirectMethodHandle$Holder.invokeStatic(DirectMethodHandle$Holder)",
            "	at java.base/java.lang.invoke.LambdaForm$MH/0x00000008000c9000.invoke(LambdaForm$MH)",
            "	at java.base/java.lang.invoke.Invokers$Holder.linkToTargetMethod(Invokers$Holder)",
            "	at Test_221.method_float_float_192(Test_221.java:1706)",
            "	at Test_221.mainTest(Test_221.java:1952)",
            "	at Test_221.main(Test_221.java:1968)"
        };

        String[] run = new String[] {
            "Exception in thread \"main\" java.lang.OutOfMemoryError: Overflow: String length out of range",
            "	        at java.base/java.lang.StringConcatHelper.checkOverflow(StringConcatHelper.java:57)",
            "	        at java.base/java.lang.StringConcatHelper.mix(StringConcatHelper.java:116)",
            "	        at Test_221.method_float_float_192(Test_221.java:1706)",
            "	        at Test_221.mainTest(Test_221.java:1952)",
            "	        at Test_221.main(Test_221.java:1968)"
        };

        ErrorTolerance.assertIsAcceptable("", Arrays.stream(run), Arrays.stream(gold));
    }

    @Test(expectedExceptions = { java.lang.RuntimeException.class })
    public void reportsFilesThatDiffer() {
        String[] gold = new String[] {
            "Correct line 1",
            "The line that zigs sometimes",
            "Correct line 2"
        };

        String[] run = new String[] {
            "Correct line 1",
            "The line that zags sometimes",
            "Correct line 2"
        };

        ErrorTolerance.assertIsAcceptable("", Arrays.stream(run), Arrays.stream(gold));
    }

}
