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
package com.couchbase.lite.internal.fleece;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.couchbase.lite.CouchbaseLiteError;


/**
 * Please see the comments in MValue
 */
public abstract class MCollection implements Encodable {
    @Nullable
    private final MValue slot;
    @Nullable
    private final MContext context;
    @Nullable
    private final MCollection parent;
    private final boolean mutable;
    private final boolean mutableChildren;

    private final AtomicBoolean mutated = new AtomicBoolean();
    private final AtomicInteger localMutations = new AtomicInteger();


    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    protected MCollection(@Nullable MContext context, boolean isMutable) { this(null, null, context, isMutable); }

    // Copy constructor
    protected MCollection(@NonNull MCollection original, boolean isMutable) {
        this(null, null, original.getContext(), isMutable);
    }

    // Slot constructor
    protected MCollection(@NonNull MValue slot, @Nullable MCollection parent, boolean isMutable) {
        this(slot, parent, ((slot.getValue() == null) || (parent == null)) ? null : parent.getContext(), isMutable);
        if (slot.isMutated()) { mutated.set(true); }
    }

    private MCollection(
        @Nullable MValue slot,
        @Nullable MCollection parent,
        @Nullable MContext context,
        boolean isMutable) {
        this.slot = slot;
        this.context = context;
        this.parent = parent;
        this.mutable = isMutable;
        mutableChildren = isMutable;
    }

    //---------------------------------------------
    // Public Methods
    //---------------------------------------------

    public final boolean isMutable() { return mutable; }

    public final boolean hasMutableChildren() { return mutableChildren; }

    public boolean isMutated() { return mutated.get(); }

    public final long getLocalMutationCount() { return localMutations.get(); }

    @Nullable
    public final MContext getContext() { return context; }

    @NonNull
    public final String getStateString() { return ((mutable) ? "+" : ".") + ((isMutated()) ? "!" : "."); }

    //---------------------------------------------
    // Protected Methods
    //---------------------------------------------

    protected void assertOpen() {
        if ((context != null) && context.isClosed()) {
            throw new CouchbaseLiteError("Cannot use a Fleece object after its parent has been closed");
        }
    }

    protected final void mutate() { mutate(true); }

    protected final void mutate(boolean isLocal) {
        if (isLocal) { localMutations.incrementAndGet(); }

        if (mutated.getAndSet(true)) { return; }

        if (slot != null) { slot.mutate(); }
        if (parent != null) { parent.mutate(false); }
    }
}
