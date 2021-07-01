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

import com.couchbase.lite.LiteCoreException;


public class MRoot extends MCollection {

    //---------------------------------------------
    // Data members
    //---------------------------------------------

    @NonNull
    private final MValue data;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------
    public MRoot(MContext context, FLValue value, boolean isMutable) {
        super(context, isMutable);
        data = new MValue(value);
    }

    //---------------------------------------------
    // Public Methods
    //---------------------------------------------

    @Override
    public void encodeTo(@NonNull FLEncoder enc) { data.encodeTo(enc); }

    public boolean isMutated() { return data.isMutated(); }

    @Nullable
    public Object asNative() { return data.asNative(this); }

    @NonNull
    public FLSliceResult encode() throws LiteCoreException {
        try (FLEncoder encoder = FLEncoder.getManagedEncoder()) {
            data.encodeTo(encoder);
            return encoder.finish2();
        }
    }
}
