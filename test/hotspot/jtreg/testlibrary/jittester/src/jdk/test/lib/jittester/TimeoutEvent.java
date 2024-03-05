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

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import jdk.test.lib.jittester.utils.SystemProperty;

public class TimeoutEvent {
    private static final String CLASS_NAME = "jdk.test.failurehandler.jtreg.GatherProcessInfoTimeoutHandler";
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    private final Path testJdk;
    private final Path timeoutHandlerJar;

    private final Object timeoutHandler;
    private final Class<?> handlerClass;

    public static class FailureHandlerException extends Exception {
        public FailureHandlerException(String message) {
            super(message);
        }

        public FailureHandlerException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static Path getJavaHome() throws FailureHandlerException {
        String[] env = {"test.jdk", "JDK_HOME", "JAVA_HOME", "BOOTDIR"};
        for (String name : env) {
            String path = System.getenv(name);
            path = path != null ? path : System.getProperty(name);
            if (path != null) {
                return Paths.get(path).toAbsolutePath();
            }
        }

        throw new FailureHandlerException("Could not find java home. " +
                "Please check that JAVA_HOME or test.jdk is properly set.");
    }

    private static Path findTimeoutHandler() throws FailureHandlerException {
        Optional<Path> fhJar = Optional.ofNullable(System.getenv("TEST_IMAGE_DIR"))
            .map(Path::of)
            .map(v -> v.resolve("failure_handler").resolve("jtregFailureHandler.jar"))
            .map(v -> v.toFile().exists() ? v : null);

        return fhJar.orElseThrow(() -> new FailureHandlerException(
                "Could find failure_handler jar. " +
                "The environment variable 'TEST_IMAGE_DIR' is not provided or " +
                "The failure_handler/jtregFailureHandler.jar is not present there"));
    }

    private Class<?> loadClass() throws ClassNotFoundException, MalformedURLException, IOException {
        final URL[] urls = new URL[] {timeoutHandlerJar.toUri().toURL()};
        URLClassLoader loader = new URLClassLoader(urls);

        Class<?> result = Class.forName(CLASS_NAME, true, loader);
        return result;
    }

    private Object createHandler(Path outDir) throws Throwable {
        final MethodType type = MethodType
            .methodType(void.class, PrintWriter.class, File.class, File.class);
        MethodHandle ctor = LOOKUP.findConstructor(handlerClass, type);
        return ctor.invoke(new PrintWriter(System.out), outDir.toFile(), testJdk.toFile());
    }

    public TimeoutEvent(Path outDir) throws FailureHandlerException {
        testJdk = getJavaHome();
        timeoutHandlerJar = findTimeoutHandler();
        try {
            handlerClass = loadClass();
            timeoutHandler = createHandler(outDir);
        } catch (Throwable thr) {
            throw new FailureHandlerException("Could not create timeout handler", thr);
        }
    }

    public void fire(Process process) throws FailureHandlerException {
        try {
            MethodType methodType = MethodType.methodType(void.class, Process.class);
            MethodHandle methodHandle = LOOKUP.findVirtual(handlerClass, "handleTimeout", methodType);
            methodHandle.invoke(timeoutHandler, process);
        } catch (Throwable thr) {
            throw new FailureHandlerException("Could not fire TimeoutEvent", thr);
        }
    }

}
