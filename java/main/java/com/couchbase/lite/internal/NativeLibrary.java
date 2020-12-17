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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.jetbrains.annotations.NotNull;


/**
 * For extracting and loading native libraries for couchbase-lite-java.
 */
final class NativeLibrary {
    private NativeLibrary() { }

    private static final String RESOURCE_BASE_DIR = "/libs";

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
    static void load() {
        CouchbaseLiteInternal.requireInit("Cannot load native libraries");

        if (LOADED.getAndSet(true)) { return; }

        final MessageDigest md;
        try { md = MessageDigest.getInstance(DIGEST_MD5); }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Cannot find digest algorithm: " + DIGEST_MD5, e);
        }

        final byte[] buf = new byte[1024];

        final String osName = getOSName();

        final List<File> libs = getLibResources(osName);

        final File targetDir;
        try {
            computeTargetDirectory(libs, buf, md);
            targetDir = new File(
                CouchbaseLiteInternal.getTmpDirectoryPath(),
                String.format("%032x", new BigInteger(1, md.digest())))
                .getCanonicalFile();
        }
        catch (IOException e) { throw new IllegalStateException("Cannot compute target directory name", e); }

        for (File lib: libs) {
            final String libPath;
            try { libPath = extract(osName, lib, targetDir, buf); }
            catch (IOException e) {
                throw new IllegalStateException("Cannot extract library resource: " + lib + " to " + targetDir, e);
            }

            try { System.load(libPath); }
            catch (Throwable e) {
                throw new IllegalStateException(
                    "Cannot load native library " + libPath + "for "
                        + System.getProperty("os.name") + "/" + System.getProperty("os.arch"),
                    e);
            }
        }
    }

    /**
     * Calculate the MD5 digest for all of the libraries.
     */
    private static void computeTargetDirectory(
        @NonNull List<File> libs,
        @NonNull byte[] buf,
        @NonNull MessageDigest md)
        throws IOException {
        for (File f: libs) {
            final String path = f.getPath();
            try (InputStream in = NativeLibrary.class.getResourceAsStream(path + "." + DIGEST_MD5)) {
                if (in == null) { throw new IOException("Cannot find MD5 for library at " + path); }
                int bytesRead;
                while ((bytesRead = in.read(buf)) != -1) { md.update(buf, 0, bytesRead); }
            }
        }
    }

    /**
     * Extracts the given path to the native library in the resource directory into the target directory.
     * If the native library already exists in the target library, the existing native library will be used.
     */
    @NonNull
    private static String extract(
        @NonNull String osName,
        @NonNull File lib,
        @NonNull File targetDir,
        @NonNull byte[] buf)
        throws IOException {
        final File targetFile = new File(targetDir, lib.getName());
        final String targetPath = targetFile.getCanonicalPath();
        if (targetFile.exists()) { return targetPath; }

        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw new IOException("Cannot create target directory: " + targetDir.getCanonicalPath());
        }

        final String libResPath = lib.getPath();
        try (
            InputStream in = NativeLibrary.class.getResourceAsStream(libResPath);
            OutputStream out = Files.newOutputStream(targetFile.toPath())) {
            if (in == null) { throw new IOException("Cannot find resource for native library at " + libResPath); }
            int bytesRead;
            while ((bytesRead = in.read(buf)) != -1) { out.write(buf, 0, bytesRead); }
        }

        // On non-windows systems set up permissions for the extracted native library.
        if (!"windows".equals(osName)) {
            try { Runtime.getRuntime().exec(new String[] {"chmod", "755", targetFile.getCanonicalPath()}).waitFor(); }
            catch (InterruptedException ignore) {
                // nothing we can do about this.  Might as well try to proceed and see if it works.
            }
        }

        return targetPath;
    }

    /**
     * Returns the paths to the native library resources.
     */
    @SuppressFBWarnings("DMI_HARDCODED_ABSOLUTE_FILENAME")
    @NonNull
    private static List<File> getLibResources(@NonNull String osName) {
        final List<File> libs = new ArrayList<>(LIBRARIES.size());
        for (String lib: LIBRARIES) {
            File libFile = new File(RESOURCE_BASE_DIR, osName);
            libFile = new File(libFile, "x86_64");
            libFile = new File(libFile, System.mapLibraryName(lib));
            libs.add(libFile);
        }
        return libs;
    }

    @NotNull
    private static String getOSName() {
        final String osName = System.getProperty("os.name").toLowerCase(Locale.getDefault());
        if (osName.contains("linux")) { return "linux"; }
        if (osName.contains("mac")) { return "macos"; }
        if (osName.contains("mindows")) { return "windows"; }
        return osName;
    }
}
