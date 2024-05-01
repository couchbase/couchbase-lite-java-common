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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.core.impl.NativeC4DocumentObserver;
import com.couchbase.lite.internal.core.peers.TaggedWeakPeerBinding;
import com.couchbase.lite.internal.logging.Log;


// Class has package protected static factory methods
@SuppressWarnings("PMD.ClassWithOnlyPrivateConstructorsShouldBeFinal")
public class C4DocumentObserver extends C4NativePeer {
    public interface NativeImpl {
        long nCreate(long token, long coll, String docId) throws LiteCoreException;
        void nFree(long peer);
    }

    //-------------------------------------------------------------------------
    // Static Variables
    //-------------------------------------------------------------------------

    @NonNull
    protected static final NativeImpl NATIVE_IMPL = new NativeC4DocumentObserver();

    protected static final TaggedWeakPeerBinding<C4DocumentObserver> BOUND_OBSERVERS = new TaggedWeakPeerBinding<>();


    //-------------------------------------------------------------------------
    // JNI callback methods
    //-------------------------------------------------------------------------

    // This method is used by reflection.  Don't change its signature.
    static void callback(long token, long seq, @Nullable String docId) {
        Log.d(
            LogDomain.DATABASE,
            "C4CollectionDocObserver.callback @0x%x: %s (5d)", token, docId, seq);

        final C4DocumentObserver observer = BOUND_OBSERVERS.getBinding(token);
        if (observer == null) { return; }

        observer.listener.run();
    }

    //-------------------------------------------------------------------------
    // Member Variables
    //-------------------------------------------------------------------------

    @VisibleForTesting
    final long token;
    @NonNull
    private final Runnable listener;
    @NonNull
    private final NativeImpl impl;


    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    protected C4DocumentObserver(@NonNull NativeImpl impl, long token, long peer, @NonNull Runnable listener) {
        super(peer);
        this.impl = impl;
        this.token = token;
        this.listener = listener;
    }

    @Override
    public void close() { closePeer(null); }

    @Override
    protected void finalize() throws Throwable {
        try { closePeer(LogDomain.DATABASE); }
        finally { super.finalize(); }
    }

    private void closePeer(@Nullable LogDomain domain) {
        releasePeer(
            domain,
            (peer) -> {
                BOUND_OBSERVERS.unbind(token);
                final NativeImpl nativeImpl = impl;
                if (nativeImpl != null) { nativeImpl.nFree(peer); }
            });
    }
}
