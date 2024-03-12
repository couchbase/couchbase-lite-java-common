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

import com.couchbase.lite.LiteCoreException;


public final class JSONEncoder extends FLEncoder.ManagedFLEncoder {

    public JSONEncoder() { super(newJSONEncoder()); }

    @NonNull
    public String finishJSON() throws LiteCoreException { return withPeerOrThrow(JSONEncoder::finishJSON); }

    @NonNull
    public byte[] finish() {
        throw new UnsupportedOperationException("finish not supported for JSONEncoders");
    }

    @NonNull
    public FLSliceResult finish2() {
        throw new UnsupportedOperationException("finish2 not supported for JSONEncoders");
    }

    @NonNull
    public FLSliceResult finish2Unmanaged() {
        throw new UnsupportedOperationException("finish2Unmanaged not supported for JSONEncoders");
    }


    static native long newJSONEncoder();

    @NonNull
    static native String finishJSON(long peer) throws LiteCoreException;
}
