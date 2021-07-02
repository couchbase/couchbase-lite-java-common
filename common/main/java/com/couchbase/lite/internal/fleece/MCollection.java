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
package com.couchbase.lite.internal.fleece;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.Objects;

import com.couchbase.lite.internal.utils.Preconditions;


public abstract class MCollection implements Encodable {
    @Nullable
    private MContext context;
    @Nullable
    private MValue slot;
    @Nullable
    private MCollection parent;
    private boolean mutable;
    private boolean mutated;
    private boolean mutableChildren;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    protected MCollection() { this(MContext.NULL, true); }

    MCollection(@Nullable MContext context, boolean isMutable) {
        this.context = context;
        this.mutable = isMutable;
        mutableChildren = isMutable;
    }

    //---------------------------------------------
    // Public Methods
    //---------------------------------------------

    public boolean isMutable() { return mutable; }

    public boolean isMutated() { return mutated; }

    public boolean hasMutableChildren() { return mutableChildren; }

    @Nullable
    public MContext getContext() { return context; }

    public void initAsCopyOf(@NonNull MCollection original, boolean isMutable) {
        if (context != MContext.NULL) { throw new IllegalStateException("Current context is not null."); }

        context = Preconditions.assertNotNull(original, "original MCollection").getContext();
        this.mutable = isMutable;
        mutableChildren = isMutable;
    }

    //---------------------------------------------
    // Protected Methods
    //---------------------------------------------

    protected void setSlot(@Nullable MValue newSlot, @Nullable MValue oldSlot) {
        if (Objects.equals(slot, oldSlot)) {
            slot = newSlot;
            if (newSlot == null) { parent = null; }
        }
    }

    protected void initInSlot(@NonNull MValue slot, @Nullable MCollection parent, boolean isMutable) {
        this.slot = Preconditions.assertNotNull(slot, "slot");
        if (context != MContext.NULL) { throw new IllegalStateException("Current context must be MContext.Null"); }
        this.parent = parent;
        this.mutable = isMutable;
        mutableChildren = isMutable;
        mutated = this.slot.isMutated();
        if (this.slot.getValue() != null) { context = (parent == null) ? null : parent.getContext(); }
    }

    protected void mutate() {
        if (!mutable) { throw new IllegalStateException("The collection object is not mutable."); }
        if (!mutated) {
            mutated = true;
            if (slot != null) { slot.mutate(); }
            if (parent != null) { parent.mutate(); }
        }
    }
}
