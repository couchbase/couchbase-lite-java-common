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
public class C4DatabaseObserver extends C4NativePeer {

    //-------------------------------------------------------------------------
    // Static Variables
    //-------------------------------------------------------------------------

    private static final NativeRefPeerBinding<C4DatabaseObserver> BOUND_OBSERVERS = new NativeRefPeerBinding<>();

    //-------------------------------------------------------------------------
    // JNI callback methods
    //-------------------------------------------------------------------------

    // This method is called by reflection.  Don't change its signature.
    static void callback(long peer) {
        Log.d(LogDomain.DATABASE, "C4DatabaseObserver.callback @%x", peer);

        final C4DatabaseObserver observer = BOUND_OBSERVERS.getBinding(peer);
        if (observer == null) { return; }

        observer.listener.run();
    }

    //-------------------------------------------------------------------------
    // Static factory methods
    //-------------------------------------------------------------------------

    @NonNull
    static C4DatabaseObserver newObserver(long db, @NonNull Runnable listener) {
        final C4DatabaseObserver observer = new C4DatabaseObserver(db, listener);
        BOUND_OBSERVERS.bind(observer.getPeer(), observer);
        return observer;
    }


    //-------------------------------------------------------------------------
    // Member Variables
    //-------------------------------------------------------------------------

    @NonNull
    private final Runnable listener;

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    C4DatabaseObserver(long db, @NonNull Runnable listener) {
        super(create(db));
        this.listener = listener;
    }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    @Nullable
    public C4DatabaseChange[] getChanges(int maxChanges) { return getChanges(getPeer(), maxChanges); }

    @CallSuper
    @Override
    public void close() {
        BOUND_OBSERVERS.unbind(getPeerUnchecked());
        closePeer(null);
    }

    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        try { closePeer(LogDomain.DATABASE); }
        finally { super.finalize(); }
    }

    private void closePeer(@Nullable LogDomain domain) {
        BOUND_OBSERVERS.unbind(getPeerUnchecked());
        releasePeer(domain, C4DatabaseObserver::free);
    }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    private static native long create(long db);

    @NonNull
    private static native C4DatabaseChange[] getChanges(long peer, int maxChanges);

    private static native void free(long peer);
}


