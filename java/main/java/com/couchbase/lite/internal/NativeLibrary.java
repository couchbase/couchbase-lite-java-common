//
// Copyright (c) 2020 Couchbase, Inc.
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import com.couchbase.lite.CouchbaseLiteError;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.logging.Log;


/**
 * Extract and load the native libraries.
 */
final class NativeLibrary {
    private NativeLibrary() { }

    private static final String JAVA_PATH_SEPARATOR = "/";

    private static final String RESOURCE_BASE_DIR = "libs";

    private static final String OS_WINDOWS = "windows";
    private static final String OS_LINUX = "linux";
    private static final String OS_MAC = "mac";

    private static final String ARCH_X86 = "x86_64";
    private static final String ARCH_APPLE_ARM = "aarch64";
    private static final String ARCH_UNIVERSAL = "universal";

    private static final String LIB_JNI = "LiteCoreJNI";
    private static final String LIB_LITE_CORE = "LiteCore";

    private static final String WINDOWS_OS_DIR = "windows";
    private static final String LINUX_OS_DIR = "linux";
    private static final String MAC_OS_DIR = "macos";

    private static final String LIB_DIR = JAVA_PATH_SEPARATOR + "lib";

    private static final String DIGEST_MD5 = ".MD5";

    private static final AtomicBoolean LOADED = new AtomicBoolean();

    public static boolean isWindows() { return isWindows(System.getProperty("os.name")); }

    public static boolean isMacOS() { return isMacOS(System.getProperty("os.name")); }

    public static boolean isLinux() { return isLinux(System.getProperty("os.name")); }

    /**
     * Extracts the two native libraries from the jar file, puts them on the file system and loads them.
     */
    static void load(@NonNull File scratchDir) {
        if (LOADED.getAndSet(true)) { return; }

        final String os = System.getProperty("os.name");
        final String arch = System.getProperty("os.arch");

        loadLibrary(LIB_LITE_CORE, getCoreArchDir(os, arch), os, arch, scratchDir);

        loadLibrary(LIB_JNI, getJniArchDir(os, arch), os, arch, scratchDir);
    }

    /**
     * Extracts the named library and its support libraries from the jar file,
     * puts them on the file system, and returns the path of the containing directory.
     */
    @NonNull
    static String loadExtension(
        @NonNull String libName,
        @NonNull File scratchDir,
        @Nullable List<String> supportLibNames)
        throws IOException {
        final String os = System.getProperty("os.name");
        final String libExtension = getLibExtension(os);
        final String extLib = libName + libExtension;

        final String resDirPath = getCoreArchDir(os, System.getProperty("os.arch"));
        final File targetDir = computeTargetDirectory(scratchDir, resDirPath, extLib);

        extract(extLib, resDirPath, targetDir);

        if (supportLibNames != null) {
            for (String supportLib: supportLibNames) {
                try { extract(supportLib + libExtension, resDirPath, targetDir); }
                catch (IOException err) {
                    Log.d(LogDomain.DATABASE, "Failed loading extension support lib: %s", err, supportLib);
                }
            }
        }

        return targetDir.getCanonicalPath();
    }

    /**
     * Copy the named library from a resource to the file system and load it.
     * Each library must have a corresponding .MD5 digest file
     */
    @SuppressWarnings({
        "PMD.AvoidCatchingThrowable",
        "PMD.AvoidInstanceofChecksInCatchClause",
        "PMD.PreserveStackTrace"})
    private static void loadLibrary(
        @NonNull String libName,
        @NonNull String resDirPath,
        @NonNull String os,
        @NonNull String arch,
        @NonNull File scratchDir) {
        final String lib = System.mapLibraryName(libName);

        final String libPath;
        File targetDir = null;
        try {
            targetDir = computeTargetDirectory(scratchDir, resDirPath, lib);
            libPath = extract(lib, resDirPath, targetDir);
            // On non-windows systems set up permissions for the extracted native library.
            if (!isWindows(os)) { setPermissions(libPath); }
        }
        catch (IOException e) {
            throw new CouchbaseLiteError("Failed extracting library resource: " + lib + " to " + targetDir, e);
        }

        try { System.load(libPath); }
        catch (Throwable e) {
            final Exception err = (e instanceof Exception) ? (Exception) e : new Exception(e);
            throw new CouchbaseLiteError(
                "Failed loading native library " + lib + " @" + libPath + " (" + os + "/" + arch + ")",
                err);
        }
    }

    /**
     * Read the MD5 digest for a library and construct the target directory file from it.
     */
    @NonNull
    private static File computeTargetDirectory(
        @NonNull File scratchDir,
        @NonNull String resDirPath,
        @NonNull String lib)
        throws IOException {
        final String path = resDirPath + JAVA_PATH_SEPARATOR + lib + DIGEST_MD5;
        try (InputStream rezStream = NativeLibrary.class.getResourceAsStream(path)) {
            if (rezStream == null) { throw new IOException("Cannot find digest resource"); }

            final String hash;
            try (BufferedReader in = new BufferedReader(new InputStreamReader(rezStream, StandardCharsets.UTF_8))) {
                hash = in.readLine();
            }
            if (hash == null) { throw new IOException("Digest file is empty"); }

            return new File(scratchDir, hash.trim()).getCanonicalFile();
        }
    }

    // Per the support matrix: Linux, Windows and Mac (loosy goosy checking)
    @NonNull
    private static String getOsDir(@NonNull String osName) {
        if (isWindows(osName)) { return WINDOWS_OS_DIR; }
        if (isLinux(osName)) { return LINUX_OS_DIR; }
        if (isMacOS(osName)) { return MAC_OS_DIR; }
        throw new CouchbaseLiteError("Unsupported OS: " + osName);
    }

    // Per the support matrix:
    //  - Windows: x86_64 (unchecked)
    //  - Linux; x86_64 (unchecked)
    //  - Mac: universal (unchecked)
    @NonNull
    private static String getCoreArchDir(@NonNull String osName, @NonNull String archName) {
        final String rootPath = getRootPath(osName);
        if (isWindows(osName) || isLinux(osName)) { return rootPath + ARCH_X86 + LIB_DIR; }
        if (isMacOS(osName)) { return rootPath + ARCH_UNIVERSAL + LIB_DIR; }
        throw new CouchbaseLiteError("Unsupported LiteCore architecture: " + osName + "/" + archName);
    }

    // Per the support matrix:
    //  - Windows: x86_64 (unchecked)
    //  - Linux; x86_64 (unchecked)
    //  - Mac: x86_64 and aarch64
    @NonNull
    private static String getJniArchDir(@NonNull String osName, @NonNull String archName) {
        final String rootPath = getRootPath(osName);

        if (isWindows(osName) || isLinux(osName)) { return rootPath + ARCH_X86 + LIB_DIR; }

        final String arch = archName.toLowerCase(Locale.getDefault());
        if (isMacOS(osName) && (ARCH_X86.equals(arch) || ARCH_APPLE_ARM.equals(arch))) {
            return rootPath + arch + LIB_DIR;
        }

        throw new CouchbaseLiteError("Unsupported JNI architecture: " + osName + "/" + archName);
    }

    @NonNull
    private static String getLibExtension(@NonNull String osName) {
        if (isWindows(osName)) { return ".dll"; }
        if (isMacOS(osName)) { return ".dylib"; }
        if (isLinux(osName)) { return ".so"; }
        throw new CouchbaseLiteError("Unsupported OS: " + osName);
    }

    private static void setPermissions(String targetPath) throws IOException {
        final Set<PosixFilePermission> perms = new HashSet<>();
        perms.add(PosixFilePermission.OWNER_READ);
        perms.add(PosixFilePermission.OWNER_WRITE);
        perms.add(PosixFilePermission.OWNER_EXECUTE);
        perms.add(PosixFilePermission.GROUP_READ);
        perms.add(PosixFilePermission.GROUP_EXECUTE);
        perms.add(PosixFilePermission.OTHERS_READ);
        perms.add(PosixFilePermission.OTHERS_EXECUTE);
        Files.setPosixFilePermissions(new File(targetPath).toPath(), perms);
    }

    /**
     * Copy a native library from a resource into the target directory.
     * <p>
     * If the native library already exists in the target library, use it.
     */
    @NonNull
    private static String extract(
        @NonNull String lib,
        @NonNull String resDirPath,
        @NonNull File targetDir)
        throws IOException {
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw new IOException("Cannot create target directory: " + targetDir.getCanonicalPath());
        }

        final File targetFile = new File(targetDir, lib);
        final String targetPath = targetFile.getCanonicalPath();

        if (targetFile.exists()) { return targetPath; }

        final String resPath = resDirPath + JAVA_PATH_SEPARATOR + lib;
        // It would be really nice to log here... but the logger hasn't been initialized
        try (InputStream in = NativeLibrary.class.getResourceAsStream(resPath)) {
            if (in == null) { throw new IOException("Cannot find resource for native library at " + resPath); }

            try (OutputStream out = Files.newOutputStream(targetFile.toPath())) {
                final byte[] buf = new byte[1024];
                int bytesRead;
                while ((bytesRead = in.read(buf)) != -1) { out.write(buf, 0, bytesRead); }
            }
        }

        return targetPath;
    }

    // Although it would be more efficient to do this once and use the value everywhere,
    // we want error messages to have the exact thing that we got from the system property.
    private static boolean isWindows(@NonNull String sysOs) {
        return sysOs.toLowerCase(Locale.getDefault()).contains(OS_WINDOWS);
    }

    private static boolean isMacOS(@NonNull String sysOs) {
        return sysOs.toLowerCase(Locale.getDefault()).contains(OS_MAC);
    }

    private static boolean isLinux(@NonNull String sysOs) {
        return sysOs.toLowerCase(Locale.getDefault()).contains(OS_LINUX);
    }

    @NonNull
    private static String getRootPath(@NonNull String os) {
        return JAVA_PATH_SEPARATOR + RESOURCE_BASE_DIR + JAVA_PATH_SEPARATOR + getOsDir(os) + JAVA_PATH_SEPARATOR;
    }
}
