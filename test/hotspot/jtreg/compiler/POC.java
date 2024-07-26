/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package compiler;

import java.util.Arrays;
import java.io.UnsupportedEncodingException;

/**
 * @test
 * @run main/othervm -Xmx6g compiler.POC
 */
public class POC {

    public static void main(String[] args) {
        byte[] b = new byte[(0x55555556 >> 1) + 0x30000];
        Arrays.fill(b,(byte)0x81);
        try {
            String s = new String(b,"UTF-8");
            Class c = Class.forName(s);
        }catch(ClassNotFoundException|UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }
}
