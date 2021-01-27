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
import android.support.annotation.VisibleForTesting;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.LogDomain;


/**
 * Unfortunately, the build system depends on having all the classes with native methods
 * in the main source tree.  Moving this class to the test tree would require major changes
 */
@VisibleForTesting
public class C4RawDocument extends C4NativePeer {

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    C4RawDocument(long peer) { super(peer); }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    @Nullable
    public String key() { return key(getPeer()); }

    @Nullable
    public String meta() { return meta(getPeer()); }

    public byte[] body() { return body(getPeer()); }

    @CallSuper
    @Override
    public void close() throws LiteCoreException { closePeer(null); }

    //-------------------------------------------------------------------------
    // protected methods
    //-------------------------------------------------------------------------

    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        try { closePeer(LogDomain.DATABASE); }
        finally { super.finalize(); }
    }

    //-------------------------------------------------------------------------
    // private methods
    //-------------------------------------------------------------------------

    private void closePeer(@Nullable LogDomain domain) throws LiteCoreException {
        releasePeer(domain, C4Database::rawFreeDocument);
    }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    private static native String key(long peer);

    private static native String meta(long peer);

    private static native byte[] body(long peer);
}
