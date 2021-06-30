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

import java.util.Arrays;
import java.util.List;


public class C4FullTextMatch extends C4NativePeer {

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------
    C4FullTextMatch(long peer) { super(peer); }

    public long dataSource() { return dataSource(getPeerUnchecked()); }

    public long property() { return property(getPeerUnchecked()); }

    public long term() { return term(getPeerUnchecked()); }

    public long start() { return start(getPeerUnchecked()); }

    public long length() { return length(getPeerUnchecked()); }

    @NonNull
    public List<Long> toList() { return Arrays.asList(dataSource(), property(), term(), start(), length()); }

    @Override
    public void close() { }

    //-------------------------------------------------------------------------
    // Native methods
    //-------------------------------------------------------------------------

    private static native long dataSource(long peer);

    private static native long property(long peer);

    private static native long term(long peer);

    private static native long start(long peer);

    private static native long length(long peer);
}
