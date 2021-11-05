//
// Copyright (c) 2020, 2018 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite.internal.core;

import androidx.annotation.NonNull;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;


// THIS FILE IS AUTOMATICALLY CREATED FROM templates/CBLVersion.java
// Edit the version in that directory!
// Changes made to this file in the source directory will be lost.
// See the "copyVersion" task in the build file.
@SuppressWarnings("LineLength")
public final class CBLVersion {
    private CBLVersion() {}

    private static final String USER_AGENT_TMPLT = "CouchbaseLite/@VERSION@ (%s) %s";
    private static final String VERSION_INFO_TMPLT = "CouchbaseLite Java v@VERSION@ (%s) %s";
    private static final String LIB_INFO_TMPLT = "@VARIANT@/@TYPE@ Build/@BUILD@ Commit/@COMMIT@ Core/%s";
    private static final String SYS_INFO_TMPLT = "Java %s; %s";

    private static final AtomicReference<String> USER_AGENT = new AtomicReference<>();
    private static final AtomicReference<String> VERSION_INFO = new AtomicReference<>();
    private static final AtomicReference<String> LIB_INFO = new AtomicReference<>();
    private static final AtomicReference<String> SYS_INFO = new AtomicReference<>();

    @NonNull
    public static String getUserAgent() {
        String agent = USER_AGENT.get();

        if (agent == null) {
            agent = String.format(Locale.ENGLISH, USER_AGENT_TMPLT, getSysInfo(), getLibInfo());

            USER_AGENT.compareAndSet(null, agent);
        }

        return agent;
    }

    // This is the full library build and environment information.
    @NonNull
    public static String getVersionInfo() {
        String info = VERSION_INFO.get();
        if (info == null) {
            info = String.format(Locale.ENGLISH, VERSION_INFO_TMPLT, getLibInfo(), getSysInfo());

            VERSION_INFO.compareAndSet(null, info);
        }

        return info;
    }

    // This is the short library build information.
    @NonNull
    public static String getLibInfo() {
        String info = LIB_INFO.get();
        if (info == null) {
            info = String.format(Locale.ENGLISH, LIB_INFO_TMPLT, C4.getVersion());

            LIB_INFO.compareAndSet(null, info);
        }

        return info;
    }

    // This is information about the system on which we are running.
    @NonNull
    public static String getSysInfo() {
        String info = SYS_INFO.get();

        if (info == null) {
            info = String.format(
                Locale.ENGLISH, SYS_INFO_TMPLT,
                System.getProperty("java.version", "?"),
                System.getProperty("os.name", "unknown"));

            SYS_INFO.compareAndSet(null, info);
        }

        return info;
    }
}
