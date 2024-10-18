//
// Copyright (c) 2024 Couchbase, Inc All rights reserved.
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

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.internal.core.C4Index;


public class NativeC4Index implements C4Index.NativeImpl {
    @Override
    public long nBeginUpdate(long peer, int limit) throws LiteCoreException { return beginUpdate(peer, limit); }

    @Override
    public void nReleaseIndex(long peer) { releaseIndex(peer); }


    //-------------------------------------------------------------------------
    // Native Methods
    //-------------------------------------------------------------------------

    private static native long beginUpdate(long peer, int limit) throws LiteCoreException;

    private static native void releaseIndex(long peer);
}
