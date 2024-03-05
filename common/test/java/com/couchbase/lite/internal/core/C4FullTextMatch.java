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

import java.util.Objects;


class C4FullTextMatch extends C4NativePeer {
    private static final long MOCK_PEER = 0x0cab00d1eL;

    /**
     * Return an array of details of each full-text match
     */
    @NonNull
    public static C4FullTextMatch getFullTextMatches(@NonNull C4QueryEnumerator queryEnumerator, int idx) {
        return new C4FullTextMatch(getFullTextMatch(queryEnumerator.getPeer(), idx));
    }

    public static long getFullTextMatchCount(C4QueryEnumerator queryEnumerator) {
        return getFullTextMatchCount(queryEnumerator.getPeer());
    }


    private long dataSource;
    private long property;
    private long term;
    private long start;
    private long length;

    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------

    C4FullTextMatch(long peer) { super(peer); }

    C4FullTextMatch(long dataSource, long property, long term, long start, long length) {
        super(MOCK_PEER);
        this.dataSource = dataSource;
        this.property = property;
        this.term = term;
        this.start = start;
        this.length = length;
    }

    @Nullable
    public C4FullTextMatch load() {
        withPeer(peer -> {
            if (peer == MOCK_PEER) { return; }
            this.dataSource = dataSource(peer);
            this.property = property(peer);
            this.term = term(peer);
            this.start = start(peer);
            this.length = length(peer);
        });
        return this;
    }

    @Override
    public void close() { }

    @Override
    public int hashCode() { return Objects.hash(dataSource, property, term, start, length); }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (!(o instanceof C4FullTextMatch)) { return false; }
        final C4FullTextMatch match = (C4FullTextMatch) o;
        return (dataSource == match.dataSource)
            && (property == match.property)
            && (term == match.term)
            && (start == match.start)
            && (length == match.length);
    }

    //-------------------------------------------------------------------------
    // Native methods
    //-------------------------------------------------------------------------

    private static native long dataSource(long peer);

    private static native long property(long peer);

    private static native long term(long peer);

    private static native long start(long peer);

    private static native long length(long peer);

    private static native long getFullTextMatchCount(long peer);

    private static native long getFullTextMatch(long peer, int idx);
}
