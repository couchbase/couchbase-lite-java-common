//
// Copyright (c) 2020 Couchbase, Inc.
// Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
// except in compliance with the License. You may obtain a copy of the License at
// http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software distributed under the
// License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions
// and limitations under the License.
//
package com.couchbase.lite.internal.core;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import javax.annotation.Nullable;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.core.impl.NativeC4QueryObserver;
import com.couchbase.lite.internal.core.peers.TaggedWeakPeerBinding;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.ClassUtils;
import com.couchbase.lite.internal.utils.Fn;


public class C4QueryObserver extends C4NativePeer {
    @FunctionalInterface
    public interface QueryChangeCallback {
        void onQueryChanged(@Nullable C4QueryEnumerator results, @Nullable LiteCoreException err);
    }

    public interface NativeImpl {
        long nCreate(long token, long c4Query);
        void nSetEnabled(long peer, boolean enabled);
        void nFree(long peer);
        long nGetEnumerator(long peer, boolean forget) throws LiteCoreException;
    }

    @VisibleForTesting
    @NonNull
    private static final NativeImpl NATIVE_IMPL = new NativeC4QueryObserver();

    @NonNull
    @VisibleForTesting
    static final TaggedWeakPeerBinding<C4QueryObserver> QUERY_OBSERVER_CONTEXT = new TaggedWeakPeerBinding<>();

    //-------------------------------------------------------------------------
    // Static Factory Methods
    //-------------------------------------------------------------------------

    @NonNull
    public static C4QueryObserver create(@NonNull C4Query query, @NonNull QueryChangeCallback callback) {
        return create(NATIVE_IMPL, C4QueryEnumerator::create, query, callback);
    }

    @NonNull
    public static C4QueryObserver create(
        @NonNull C4QueryObserver.NativeImpl impl,
        @NonNull Fn.Function<Long, C4QueryEnumerator> queryEnumeratorFactory,
        @NonNull C4Query query,
        @NonNull QueryChangeCallback callback) {
        final long token = QUERY_OBSERVER_CONTEXT.reserveKey();

        final long peer = impl.nCreate(token, query.getPeer());

        final C4QueryObserver observer = new C4QueryObserver(impl, peer, queryEnumeratorFactory, token, callback);
        QUERY_OBSERVER_CONTEXT.bind(token, observer);

        return observer;
    }

    //-------------------------------------------------------------------------
    // Native callback method
    //-------------------------------------------------------------------------

    // This method is called by reflection.  Don't change its signature.
    static void onQueryChanged(long token) {
        final C4QueryObserver observer = QUERY_OBSERVER_CONTEXT.getBinding(token);
        if (observer == null) {
            Log.w(LogDomain.QUERY, "No observer for token: " + token);
            return;
        }
        observer.queryChanged();
    }


    private final long token;
    @NonNull
    private final C4QueryObserver.NativeImpl impl;
    @NonNull
    private final Fn.Function<Long, C4QueryEnumerator> c4QueryEnumeratorFactory;
    @NonNull
    private final QueryChangeCallback callback;

    @VisibleForTesting
    C4QueryObserver(
        @NonNull NativeImpl impl,
        long peer,
        @NonNull Fn.Function<Long, C4QueryEnumerator> c4QueryEnumeratorFactory,
        long token,
        @NonNull QueryChangeCallback callback) {
        super(peer);
        this.impl = impl;
        this.c4QueryEnumeratorFactory = c4QueryEnumeratorFactory;
        this.token = token;
        this.callback = callback;
    }

    @CallSuper
    @Override
    public void close() {
        QUERY_OBSERVER_CONTEXT.unbind(token);
        closePeer(null);
    }

    @NonNull
    @Override
    public String toString() {
        return "C4QueryObserver{" + ClassUtils.objId(this) + "/" + super.toString() + ": " + token + "}";
    }

    public void setEnabled(boolean enabled) { impl.nSetEnabled(getPeer(), enabled); }

    @Override
    protected void finalize() throws Throwable {
        try { closePeer(LogDomain.LISTENER); }
        finally { super.finalize(); }
    }

    void queryChanged() {
        C4QueryEnumerator results = null;
        LiteCoreException err = null;
        try { results = getEnumerator(); }
        catch (LiteCoreException e) { err = e; }
        if ((results != null) || (err != null)) { callback.onQueryChanged(results, err); }
    }

    // ??? This is called only from LiteCore.
    // Should it still be guarded by the db lock?
    @Nullable
    private C4QueryEnumerator getEnumerator() throws LiteCoreException {
        return withPeerOrNull(h -> c4QueryEnumeratorFactory.apply(impl.nGetEnumerator(h, false)));
    }

    private void closePeer(LogDomain domain) { releasePeer(domain, impl::nFree); }
}
