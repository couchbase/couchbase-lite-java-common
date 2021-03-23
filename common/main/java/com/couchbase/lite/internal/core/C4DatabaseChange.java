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

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.support.Log;


public final class C4DatabaseChange {
    private final String docID;
    private final String revID;
    private final long sequence;
    private final boolean external;

    // This method is called by reflection.  Don't change its signature.
    public static C4DatabaseChange createC4DatabaseChange(
        @Nullable String docId,
        @Nullable String revId,
        long seq,
        boolean ext) {
        if ((docId != null) && (revId != null)) { return new C4DatabaseChange(docId, revId, seq, ext); }

        Log.i(LogDomain.DATABASE, "Bad db change notification: (%s, %s)", docId, revId);
        return null;
    }

    private C4DatabaseChange(@Nullable String docID, @NonNull String revID, long seq, boolean ext) {
        this.docID = docID;
        this.revID = revID;
        this.sequence = seq;
        this.external = ext;
    }

    public String getDocID() { return docID; }

    public String getRevID() { return revID; }

    public long getSequence() { return sequence; }

    public boolean isExternal() { return external; }
}
