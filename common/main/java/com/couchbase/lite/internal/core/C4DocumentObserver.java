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

package com.couchbase.lite.internal.core;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.core.peers.NativeRefPeerBinding;
import com.couchbase.lite.internal.support.Log;


// Class has package protected static factory methods
@SuppressWarnings("PMD.ClassWithOnlyPrivateConstructorsShouldBeFinal")
public class C4DocumentObserver extends C4NativePeer {
    //-------------------------------------------------------------------------
    // Static Variables
    //-------------------------------------------------------------------------

    private static final NativeRefPeerBinding<C4DocumentObserver> BOUND_OBSERVERS = new NativeRefPeerBinding<>();

    //-------------------------------------------------------------------------
    // Static Factory Methods
    //-------------------------------------------------------------------------

    @NonNull
    static C4DocumentObserver newObserver(long db, @NonNull String docID, @NonNull Runnable listener) {
        final C4DocumentObserver observer = new C4DocumentObserver(db, docID, listener);
        BOUND_OBSERVERS.bind(observer.getPeer(), observer);
        return observer;
    }

    //-------------------------------------------------------------------------
    // JNI callback methods
    //-------------------------------------------------------------------------

    // This method is called by reflection.  Don't change its signature.
    static void callback(long peer, @Nullable String docID, long sequence) {
        Log.d(
            LogDomain.DATABASE,
            "C4DocumentObserver.callback @0x%x (%s): %s", peer, sequence, docID);


        final C4DocumentObserver observer = BOUND_OBSERVERS.getBinding(peer);
        if (observer == null) { return; }

        observer.listener.run();
    }


    //-------------------------------------------------------------------------
    // Member Variables
    //-------------------------------------------------------------------------

    @NonNull
    private final Runnable listener;

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    C4DocumentObserver(long db, @NonNull String docID, @NonNull Runnable listener) {
        super(create(db, docID));
        this.listener = listener;
    }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    @CallSuper
    @Override
    public void close() { closePeer(null); }

    // !!! This method will, invariably, cause a native crash: CBL-3193
    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        try { closePeer(LogDomain.DATABASE); }
        finally { super.finalize(); }
    }

    //-------------------------------------------------------------------------
    // Private methods
    //-------------------------------------------------------------------------

    private void closePeer(@Nullable LogDomain domain) {
        releasePeer(
            domain,
            (peer) -> {
                BOUND_OBSERVERS.unbind(peer);
                C4DocumentObserver.free(peer);
            });
    }

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
