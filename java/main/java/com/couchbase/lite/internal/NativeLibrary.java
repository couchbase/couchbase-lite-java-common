//
// Copyright (c) 2020, 2019 Couchbase, Inc All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package com.couchbase.lite.internal;

import android.support.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * For extracting and loading native libraries for couchbase-lite-java.
 */
final class NativeLibrary {
    private NativeLibrary() { }

    private static final String JAVA_PATH_SEPARATOR = "/";

    private static final String RESOURCE_BASE_DIR = "libs";

    private static final String ARCH_X86 = "x86_64";
    private static final String LIB_DIR_MAC = "macos";
    private static final String LIB_DIR_LINUX = "linux";
    private static final String LIB_DIR_WINDOWS = "windows";

    private static final String DIGEST_MD5 = "MD5";

    private static final AtomicBoolean LOADED = new AtomicBoolean(false);

    private static final List<String> LIBRARIES;

    static {
        final List<String> l = new ArrayList<>();
        l.add("LiteCore");
        l.add("LiteCoreJNI");
        LIBRARIES = Collections.unmodifiableList(l);
    }

    /**
     * Extracts and loads native libraries.
     */
    @SuppressWarnings("PMD.AvoidCatchingThrowable")
    static void load(@NonNull File scratchDir) {
        CouchbaseLiteInternal.requireInit("Cannot load native libraries");

        if (LOADED.getAndSet(true)) { return; }

        // get the OS id
        final String osName = System.getProperty("os.name");
        final String osDir = getOsDir(osName);

        // get the resource path
        final String resDirPath = JAVA_PATH_SEPARATOR + RESOURCE_BASE_DIR
            + JAVA_PATH_SEPARATOR + osDir
            + JAVA_PATH_SEPARATOR + ARCH_X86;

        // get OS-appropriate names for the libraries
        final List<String> libs = new ArrayList<>(LIBRARIES.size());
        for (String lib: LIBRARIES) { libs.add(System.mapLibraryName(lib)); }

        // buffer
        final byte[] buf = new byte[1024];

        final File targetDir;
        try {
            targetDir = new File(
                scratchDir,
                String.format("%032x", computeTargetDirectory(resDirPath, libs, buf)))
                .getCanonicalFile();
        }
        catch (IOException e) { throw new IllegalStateException("Cannot compute target directory name", e); }

        for (String lib: libs) {
            final String libPath;
            try {
                libPath = extract(lib, resDirPath, targetDir, buf);
                // On non-windows systems set up permissions for the extracted native library.
                if (!LIB_DIR_WINDOWS.equals(osDir)) { setPermissions(libPath); }
            }
            catch (IOException e) {
                throw new IllegalStateException("Cannot extract library resource: " + lib + " to " + targetDir, e);
            }

            try { System.load(libPath); }
            catch (Throwable e) {
                throw new IllegalStateException(
                    "Cannot load native library " + lib + " @" + libPath
                        + " for " + osName + "/" + System.getProperty("os.arch"),
                    e);
            }
        }
    }

    /**
     * Calculate the MD5 digest for all of the libraries.
     */
    private static BigInteger computeTargetDirectory(
        @NonNull String resDirPath,
        @NonNull List<String> libs,
        @NonNull byte[] buf)
        throws IOException {

        // get a message digest for
        final MessageDigest md;
        try { md = MessageDigest.getInstance(DIGEST_MD5); }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("No such digest algorithm: " + DIGEST_MD5, e);
        }

        for (String lib: libs) {
            final String path = resDirPath + JAVA_PATH_SEPARATOR + lib + "." + DIGEST_MD5;
            try (InputStream in = NativeLibrary.class.getResourceAsStream(path)) {
                if (in == null) { throw new IOException("Cannot find MD5 for library at " + path); }
                int bytesRead;
                while ((bytesRead = in.read(buf)) != -1) { md.update(buf, 0, bytesRead); }
            }
        }

        return new BigInteger(1, md.digest());
    }

    @NonNull
    private static String getOsDir(@NonNull String osName) {
        final String os = osName.toLowerCase(Locale.getDefault());
        if (os.contains("mac")) { return LIB_DIR_MAC; }
        if (os.contains(LIB_DIR_LINUX)) { return LIB_DIR_LINUX; }
        if (os.contains(LIB_DIR_WINDOWS)) { return LIB_DIR_WINDOWS; }
        throw new IllegalStateException("Unrecongnized OS: " + osName);
    }

    private static void setPermissions(String targetPath) throws IOException {
        try { Runtime.getRuntime().exec(new String[] {"chmod", "755", targetPath}).waitFor(); }
        catch (InterruptedException ignore) {
            // nothing we can do about this.  Might as well try to proceed and see if it works.
        }
    }

    /*
     * Copy the named native library from a resource into the target directory.
     * If the native library already exists in the target library, the existing native library will be used.
     */
    @NonNull
    private static String extract(
        @NonNull String lib,
        @NonNull String resDirPath,
        @NonNull File targetDir,
        @NonNull byte[] buf)
        throws IOException {
        final File targetFile = new File(targetDir, lib);
        final String targetPath = targetFile.getCanonicalPath();
        if (targetFile.exists()) { return targetPath; }

        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw new IOException("Cannot create target directory: " + targetDir.getCanonicalPath());
        }

        final String resPath = resDirPath + JAVA_PATH_SEPARATOR + lib;
        try (
            InputStream in = NativeLibrary.class.getResourceAsStream(resPath);
            OutputStream out = Files.newOutputStream(targetFile.toPath())) {
            if (in == null) { throw new IOException("Cannot find resource for native library at " + resPath); }
            int bytesRead;
            while ((bytesRead = in.read(buf)) != -1) { out.write(buf, 0, bytesRead); }
        }

        return targetPath;
    }
}
