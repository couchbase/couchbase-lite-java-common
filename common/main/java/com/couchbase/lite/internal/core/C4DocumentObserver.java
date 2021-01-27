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

import android.support.annotation.CallSuper;
import android.support.annotation.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.support.Log;


// Class has package protected static factory methods
@SuppressWarnings("PMD.ClassWithOnlyPrivateConstructorsShouldBeFinal")
public class C4DocumentObserver extends C4NativePeer {
    //-------------------------------------------------------------------------
    // Static Variables
    //-------------------------------------------------------------------------

    // Long: handle to C4DocumentObserver's native peer
    // C4DocumentObserver: The Java peer (the instance holding the handle that is the key)
    private static final Map<Long, C4DocumentObserver> REVERSE_LOOKUP_TABLE
        = Collections.synchronizedMap(new HashMap<>());

    //-------------------------------------------------------------------------
    // Static Factory Methods
    //-------------------------------------------------------------------------

    static C4DocumentObserver newObserver(long db, String docID, C4DocumentObserverListener listener, Object context) {
        final C4DocumentObserver observer = new C4DocumentObserver(db, docID, listener, context);
        REVERSE_LOOKUP_TABLE.put(observer.getPeer(), observer);
        return observer;
    }

    //-------------------------------------------------------------------------
    // JNI callback methods
    //-------------------------------------------------------------------------

    // This method is called by reflection.  Don't change its signature.
    static void callback(long peer, @Nullable String docID, long sequence) {
        Log.v(
            LogDomain.DATABASE,
            "C4DocumentObserver.callback @" + Long.toHexString(peer) + " (" + sequence + "): " + docID);

        final C4DocumentObserver obs = REVERSE_LOOKUP_TABLE.get(peer);
        if (obs == null) { return; }

        final C4DocumentObserverListener listener = obs.listener;
        if (listener == null) { return; }

        listener.callback(obs, docID, sequence, obs.context);
    }


    //-------------------------------------------------------------------------
    // Member Variables
    //-------------------------------------------------------------------------

    private final C4DocumentObserverListener listener;
    private final Object context;

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    private C4DocumentObserver(long db, String docID, C4DocumentObserverListener listener, Object context) {
        super(create(db, docID));
        this.listener = listener;
        this.context = context;
    }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    @CallSuper
    @Override
    public void close() {
        REVERSE_LOOKUP_TABLE.remove(getPeerUnchecked());
        closePeer(null);
    }

    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        try { closePeer(LogDomain.DATABASE); }
        finally { super.finalize(); }
    }

    //-------------------------------------------------------------------------
    // Private methods
    //-------------------------------------------------------------------------

    private void closePeer(@Nullable LogDomain domain) { releasePeer(domain, C4DocumentObserver::free); }


    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    private static native long create(long db, String docID);

    /**
     * Free C4DocumentObserver* instance
     *
     * @param peer (C4DocumentObserver*)
     */
    private static native void free(long peer);
}
