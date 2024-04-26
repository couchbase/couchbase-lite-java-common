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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.internal.core.C4Query;


public final class NativeC4Query implements C4Query.NativeImpl {
    @Override
    public long nCreateQuery(long db, int language, @NonNull String params) throws LiteCoreException {
        return createQuery(db, language, params);
    }

    @Override
    public void nSetParameters(long peer, long paramPtr, long paramSize) { setParameters(peer, paramPtr, paramSize); }

    @Nullable
    @Override
    public String nExplain(long peer) { return explain(peer); }

    @Override
    public long nRun(long peer, long paramPtr, long paramSize) throws LiteCoreException {
        return run(peer, paramPtr, paramSize);
    }

    @Override
    public int nColumnCount(long peer) { return columnCount(peer); }

    @Nullable
    @Override
    public String nColumnName(long peer, int colIdx) { return columnName(peer, colIdx); }

    @Override
    public void nFree(long peer) { free(peer); }


    //-------------------------------------------------------------------------
    // Native methods
    //-------------------------------------------------------------------------

    private static native long createQuery(long db, int language, @NonNull String params) throws LiteCoreException;

    private static native void setParameters(long peer, long paramPtr, long paramSize);

    @Nullable
    private static native String explain(long peer);

    private static native long run(long peer, long paramPtr, long paramSize)
        throws LiteCoreException;

    private static native int columnCount(long peer);

    @Nullable
    private static native String columnName(long peer, int colIdx);

    private static native void free(long peer);
}
