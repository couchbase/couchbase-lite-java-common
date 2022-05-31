//
// Copyright (c) 2022 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite.internal.core.impl;

import com.couchbase.lite.internal.core.C4CollectionObserver;


public class NativeC4CollectionObserver implements C4CollectionObserver.NativeImpl {

    @Override
    public long nCreate(long coll, long token) { return create(coll, token); }

    @Override
    public void nFree(long peer) { free(peer); }


    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    private static native long create(long coll, long token);

    private static native void free(long peer);
}
