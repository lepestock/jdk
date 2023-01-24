/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.time.Duration;
import java.util.stream.Stream;

public record ExecutionResult
    (Duration elapsed, String exitValue, Stream<String> stdErr, Stream<String> stdOut, long stdOutSize) {
    public static class Builder {
        private Duration elapsed;
        private String exitValue = "0";
        private Stream<String> stdOut = Stream.empty();
        private Stream<String> stdErr = Stream.empty();
        private long stdOutSize = 0;

        public Builder elapsed(Duration value) {
            elapsed = value;
            return this;
        }

        public Builder exitValue(String value) {
            exitValue = value;
            return this;
        }

        public Builder exitValue(int value) {
            return exitValue(Integer.toString(value));
        }

        public Builder stdOut(Stream<String> value) {
            stdOut = value;
            return this;
        }

        public Builder stdErr(Stream<String> value) {
            stdErr = value;
            return this;
        }

        public Builder stdOutSize(long newSize) {
            stdOutSize = newSize;
            return this;
        }

        public ExecutionResult build() {
            return new ExecutionResult(elapsed, exitValue, stdErr, stdOut, stdOutSize);
        }
    }
}

