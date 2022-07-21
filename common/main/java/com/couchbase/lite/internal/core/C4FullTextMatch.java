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


import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.Objects;


// This class exists only for testing.
// It is never used in production code

@VisibleForTesting
class C4FullTextMatch extends C4NativePeer {
    private final boolean loadable;
    private long dataSource;
    private long property;
    private long term;
    private long start;
    private long length;

    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------

    C4FullTextMatch(long peer) {
        super(peer);
        loadable = true;
    }

    C4FullTextMatch(long dataSource, long property, long term, long start, long length) {
        super(0x0cab00d1eL);
        loadable = false;
        this.dataSource = dataSource;
        this.property = property;
        this.term = term;
        this.start = start;
        this.length = length;
    }

    @Nullable
    public C4FullTextMatch load() {
        if (loadable) {
            withPeer(peer -> {
                this.dataSource = dataSource(peer);
                this.property = property(peer);
                this.term = term(peer);
                this.start = start(peer);
                this.length = length(peer);
            });
        }
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
}
