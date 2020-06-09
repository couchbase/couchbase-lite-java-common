//
// Copyright (c) 2020 Couchbase, Inc All rights reserved.
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

import android.support.annotation.GuardedBy;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import com.couchbase.lite.ConnectionStatus;
import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.internal.utils.MathUtils;


// !!! Temporary warning suppression.  Remove this.
@SuppressFBWarnings("DLS_DEAD_LOCAL_STORE")
@SuppressWarnings({"PMD.PrematureDeclaration", "PMD.UnusedLocalVariable", "PMD.UnusedPrivateField"})
public final class C4Listener extends C4NativePeer {
    public enum Option {REST, SYNC}

    public enum KeyMode {CERT, KEY}

    private static final Map<Option, Integer> OPTION_TO_C4;

    static {
        final Map<Option, Integer> m = new HashMap<>();
        m.put(Option.REST, 0x01);
        m.put(Option.SYNC, 0x02);
        OPTION_TO_C4 = Collections.unmodifiableMap(m);
    }

    private static final Map<KeyMode, Integer> KEY_MODE_TO_C4;

    static {
        final Map<KeyMode, Integer> m = new HashMap<>();
        m.put(KeyMode.CERT, 1);
        m.put(KeyMode.KEY, 2);
        KEY_MODE_TO_C4 = Collections.unmodifiableMap(m);
    }

    private static final Object CLASS_LOCK = new Object();

    @GuardedBy("CLASS_LOCK")
    private static final Map<Long, WeakReference<C4Listener>> LISTENERS = new HashMap<>();


    //-------------------------------------------------------------------------
    // Native callback methods
    //-------------------------------------------------------------------------

    // This method is called by reflection.  Don't change its signature.
    @SuppressWarnings("unused")
    static boolean certAuthCallback(long handle, Object clientCertData, long context) {
        return true;
    }

    // This method is called by reflection.  Don't change its signature.
    @SuppressWarnings("unused")
    static boolean httpAuthCallback(long handle, Object clientCertData, long context) {
        return true;
    }

    //-------------------------------------------------------------------------
    // Static Factory Methods
    //-------------------------------------------------------------------------

    @NonNull
    public static C4Listener createHttpListener(
        int port,
        String networkInterface,
        EnumSet<Option> opts,
        String dbPath,
        boolean allowCreateDBs,
        boolean allowDeleteDBs)
        throws LiteCoreException {
        final long key = reserveKey();
        final long hdl = startHttp(
            port,
            networkInterface,
            optsToC4(opts),
            key,
            dbPath,
            allowCreateDBs,
            allowDeleteDBs);

        final C4Listener listener = new C4Listener(hdl);
        bind(key, listener);

        return listener;
    }

    //-------------------------------------------------------------------------
    // Private Static Methods
    //-------------------------------------------------------------------------

    private static long reserveKey() {
        long key;
        synchronized (CLASS_LOCK) {
            do { key = MathUtils.RANDOM.get().nextLong(); }
            while (LISTENERS.containsKey(key));
            LISTENERS.put(key, null);
        }
        return key;
    }

    private static void bind(long key, C4Listener listener) {
        synchronized (CLASS_LOCK) { LISTENERS.put(key, new WeakReference<>(listener)); }
    }

    private static int optsToC4(@Nullable EnumSet<Option> opts) {
        if (opts == null) { return 0; }

        int c4Opts = 0;
        for (Option opt: opts) {
            final Integer c4Opt = OPTION_TO_C4.get(opt);
            if (c4Opt == null) { continue; }
            c4Opts |= c4Opt;
        }
        return c4Opts;
    }

    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------

    private C4Listener(long handle) { super(handle); }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    public void shareDb(@NonNull String name, @NonNull C4Database db) throws LiteCoreException {
        shareDb(getPeer(), name, db.getHandle());
    }

    public void unshareDb(@NonNull C4Database db) throws LiteCoreException {
        unshareDb(getPeer(), db.getHandle());
    }

    @NonNull
    public List<String> getUrls(@NonNull C4Database db, @Nullable EnumSet<Option> options) throws LiteCoreException {
        final long ret = getUrls(getPeer(), db.getHandle(), optsToC4(options));
        return new ArrayList<>();
    }

    public int getPort() { return getPort(getPeer()); }

    public ConnectionStatus getConnectionStatus() { return getConnectionStatus(getPeer()); }

    public String getUriFromPath(String path) { return getUriFromPath(getPeer(), path); }

    //-------------------------------------------------------------------------
    // protected methods
    //-------------------------------------------------------------------------

    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        try { free(getPeer()); }
        finally { super.finalize(); }
    }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    private static native long startHttp(
        int port,
        String networkInterface,
        int opts,
        long context,
        String dbPath,
        boolean allowCreateDBs,
        boolean allowDeleteDBs) throws LiteCoreException;

    private static native void free(long handle);

    private static native void shareDb(long handle, String name, long c4Db) throws LiteCoreException;

    private static native void unshareDb(long handle, long c4Db) throws LiteCoreException;

    private static native long getUrls(long handle, long c4Db, int api) throws LiteCoreException;

    private static native int getPort(long handle);

    private static native ConnectionStatus getConnectionStatus(long handle);

    private static native String getUriFromPath(long handle, String path);
}
