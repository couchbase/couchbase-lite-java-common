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
package com.couchbase.lite.internal.fleece;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;


public abstract class FLContainerSlice<T> extends FLSlice<T> {

    @FunctionalInterface
    interface ChildAt<E extends Exception> {
        long getChild(long x) throws E;
    }

    protected FLContainerSlice(@NonNull T impl, long peer) { super(impl, peer); }

    public abstract long count();

    @Nullable
    protected <E extends Exception> FLValue childAt(@NonNull ChildAt<E> fn) throws E {
        final long child = fn.getChild(peer);
        return (child == 0) ? null : FLValue.create(child);
    }
}
