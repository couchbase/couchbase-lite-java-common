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
import androidx.annotation.VisibleForTesting;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.LogDomain;


/**
 * Unfortunately, the build system depends on having all the classes with native methods
 * in the main source tree.  Moving this class to the test tree would require major changes
 */
@VisibleForTesting
public class C4DocEnumerator extends C4NativePeer {

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------
    C4DocEnumerator(long db, long since, int flags) throws LiteCoreException {
        this(enumerateChanges(db, since, flags));
    }

    C4DocEnumerator(long db, int flags) throws LiteCoreException { this(enumerateAllDocs(db, flags)); }

    private C4DocEnumerator(long peer) { super(peer); }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    @NonNull
    public C4Document getDocument() throws LiteCoreException {
        return new C4Document(getDocument(getPeer()));
    }

    public boolean next() throws LiteCoreException { return next(getPeer()); }

    @CallSuper
    @Override
    public void close() { closePeer(null); }

    //-------------------------------------------------------------------------
    // protected methods
    //-------------------------------------------------------------------------

    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        try { closePeer(LogDomain.DATABASE); }
        finally { super.finalize(); }
    }

    private void closePeer(@Nullable LogDomain domain) { releasePeer(domain, C4DocEnumerator::free); }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    private static native long enumerateAllDocs(long db, int flags) throws LiteCoreException;

    private static native long enumerateChanges(long db, long since, int flags) throws LiteCoreException;

    private static native boolean next(long peer) throws LiteCoreException;

    private static native long getDocument(long peer) throws LiteCoreException;

    private static native void free(long peer);
}
