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

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.logging.Log;


public final class C4DocumentChange {
    // This method is used by reflection.  Don't change its signature.
    @Nullable
    public static C4DocumentChange createC4DocumentChange(
        @Nullable String docId,
        @Nullable String revId,
        long seq,
        boolean ext) {
        if (docId != null) { return new C4DocumentChange(docId, revId, seq, ext); }

        Log.i(LogDomain.DATABASE, "Doc id is null in createC4DocumentChange");
        return null;
    }


    @NonNull
    private final String docID;
    @Nullable
    private final String revID;
    private final long sequence;
    private final boolean external;

    private C4DocumentChange(@NonNull String docID, @Nullable String revID, long seq, boolean ext) {
        this.docID = docID;
        this.revID = revID;
        this.sequence = seq;
        this.external = ext;
    }

    @NonNull
    public String getDocID() { return docID; }

    @Nullable
    public String getRevID() { return revID; }

    public long getSequence() { return sequence; }

    public boolean isExternal() { return external; }

    @Override
    @NonNull
    public String toString() {
        return "C4DocumentChange{" + docID + ", " + revID + ", " + sequence + ", " + external + "}";
    }
}
