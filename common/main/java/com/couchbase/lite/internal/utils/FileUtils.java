//
// Copyright (c) 2020, 2017 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite.internal.utils;

import android.support.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.support.Log;


public final class FileUtils {
    private FileUtils() { }

    @NonNull
    public static File verifyDir(@NonNull String dirPath) {
        Preconditions.assertNotNull(dirPath, "dirPath");
        return verifyDir(new File(dirPath));
    }

    @NonNull
    public static File verifyDir(@NonNull File dir) {
        Preconditions.assertNotNull(dir, "directory");

        IOException err = null;
        try {
            dir = dir.getCanonicalFile();
            if ((dir.exists() && dir.isDirectory()) || dir.mkdirs()) { return dir; }
        }
        catch (IOException e) { err = e; }

        throw new IllegalStateException("Cannot create or access directory at " + dir, err);
    }

    public static void copyFile(InputStream in, OutputStream out) throws IOException {
        final byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) { out.write(buffer, 0, read); }
    }

    public static boolean eraseFileOrDir(@NonNull String fileOrDirectory) {
        Preconditions.assertNotNull(fileOrDirectory, "file or directory");
        return eraseFileOrDir(new File(fileOrDirectory));
    }

    public static boolean eraseFileOrDir(@NonNull File fileOrDirectory) {
        Preconditions.assertNotNull(fileOrDirectory, "file or directory");
        return deleteRecursive(fileOrDirectory);
    }

    public static boolean deleteContents(String fileOrDirectory) {
        return deleteContents((fileOrDirectory == null) ? null : new File(fileOrDirectory));
    }

    @SuppressFBWarnings({"RV_RETURN_VALUE_IGNORED_BAD_PRACTICE"})
    public static boolean deleteContents(File fileOrDirectory) {
        if ((fileOrDirectory == null) || (!fileOrDirectory.isDirectory())) { return true; }

        final File[] contents = fileOrDirectory.listFiles();
        if (contents == null) { return true; }

        boolean succeeded = true;
        for (File file: contents) {
            if (!deleteRecursive(file)) {
                Log.i(LogDomain.DATABASE, "Failed deleting file: " + file);
                succeeded = false;
            }
        }

        return succeeded;
    }

    public static boolean setPermissionRecursive(@NonNull File fileOrDirectory, boolean readable, boolean writable) {
        if (fileOrDirectory.isDirectory()) {
            final File[] files = fileOrDirectory.listFiles();
            if (files != null) {
                for (File child: files) { setPermissionRecursive(child, readable, writable); }
            }
        }
        return fileOrDirectory.setReadable(readable) && fileOrDirectory.setWritable(writable);
    }

    private static boolean deleteRecursive(File fileOrDirectory) {
        return (!fileOrDirectory.exists()) || (deleteContents(fileOrDirectory) && fileOrDirectory.delete());
    }
}
