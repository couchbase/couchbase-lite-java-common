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

import android.support.annotation.VisibleForTesting;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.internal.DbContext;


public class MRoot extends MCollection {

    //---------------------------------------------
    // Data members
    //---------------------------------------------

    private final MValue slot;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    public MRoot(DbContext context, FLValue value, boolean isMutable) {
        super(context, isMutable);
        slot = new MValue(value);
    }

    @VisibleForTesting
    public MRoot(MContext context, FLValue value) {
        super(context);
        slot = new MValue(value);
    }

    //---------------------------------------------
    // Public Methods
    //---------------------------------------------

    @Override
    public void encodeTo(FLEncoder enc) { slot.encodeTo(enc); }

    public boolean isMutated() { return slot.isMutated(); }

    public Object asNative() { return slot.asNative(this); }

    public FLSliceResult encode() throws LiteCoreException {
        try (FLEncoder encoder = new FLEncoder()) {
            slot.encodeTo(encoder);
            return encoder.finish2();
        }
    }
}
