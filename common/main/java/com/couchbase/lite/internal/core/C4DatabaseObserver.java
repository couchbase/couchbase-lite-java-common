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
package com.couchbase.lite.internal.core;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.support.Log;


// Class has package protected static factory methods
@SuppressWarnings("PMD.ClassWithOnlyPrivateConstructorsShouldBeFinal")
public class C4DatabaseObserver extends C4NativePeer {

    //-------------------------------------------------------------------------
    // Static Variables
    //-------------------------------------------------------------------------

    // Long: handle to C4DatabaseObserver's native peer
    // C4DatabaseObserver: The Java peer (the instance holding the handle that is the key)
    private static final Map<Long, C4DatabaseObserver> REVERSE_LOOKUP_TABLE
        = Collections.synchronizedMap(new HashMap<>());

    //-------------------------------------------------------------------------
    // JNI callback methods
    //-------------------------------------------------------------------------

    // This method is called by reflection.  Don't change its signature.
    static void callback(long peer) {
        Log.d(LogDomain.DATABASE, "C4DatabaseObserver.callback @%x", peer);

        final C4DatabaseObserver obs = REVERSE_LOOKUP_TABLE.get(peer);
        if (obs == null) { return; }

        obs.listener.callback(obs, obs.context);
    }

    //-------------------------------------------------------------------------
    // Static factory methods
    //-------------------------------------------------------------------------

    @NonNull
    static C4DatabaseObserver newObserver(
        long db,
        @NonNull C4DatabaseObserverListener listener,
        @NonNull Object context) {
        final C4DatabaseObserver observer = new C4DatabaseObserver(db, listener, context);
        REVERSE_LOOKUP_TABLE.put(observer.getPeer(), observer);
        return observer;
    }


    //-------------------------------------------------------------------------
    // Member Variables
    //-------------------------------------------------------------------------

    @NonNull
    private final C4DatabaseObserverListener listener;
    @NonNull
    private final Object context;

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    C4DatabaseObserver(long db, @NonNull C4DatabaseObserverListener listener, @NonNull Object context) {
        super(create(db));
        this.listener = listener;
        this.context = context;
    }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    @Nullable
    public C4DatabaseChange[] getChanges(int maxChanges) { return getChanges(getPeer(), maxChanges); }

    @CallSuper
    @Override
    public void close() {
        REVERSE_LOOKUP_TABLE.remove(getPeerUnchecked());
        closePeer(null);
    }

    // This is whistling in the wind.  Unless the entry is removed
    // from the REVERSE_LOOKUP_TABLE, we'll never get here.
    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        try { closePeer(LogDomain.DATABASE); }
        finally { super.finalize(); }
    }

    private void closePeer(@Nullable LogDomain domain) { releasePeer(domain, C4DatabaseObserver::free); }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    private static native long create(long db);

    @NonNull
    private static native C4DatabaseChange[] getChanges(long peer, int maxChanges);

    private static native void free(long peer);
}


