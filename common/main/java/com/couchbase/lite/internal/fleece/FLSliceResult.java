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


/**
 * This is an interesting object. It frames a piece of mamory that LiteCore owns:
 * it just passes us this view: `base` points at the start of the block, base + size is its end.
 * Its C companion is a struct so there is no memory for Java to manage.
 * The JNI just creates one of these whenever LiteCore returns a native FLSliceResult.
 */
public class FLSliceResult {
    // These fields are accessed by reflection.  Don't change them.
    final long base;
    final long size;

    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------

    // This method is called by reflection.  Don't change its signature.
    public FLSliceResult(long base, long size) {
        this.base = base;
        this.size = size;
    }

    //-------------------------------------------------------------------------
    // Public methods
    //-------------------------------------------------------------------------

    @NonNull
    @Override
    public String toString() { return "SliceResult{" + size + "@0x0" + Long.toHexString(base); }

    // ???  Exposes a native pointer
    public long getBase() { return base; }

    public long getSize() { return size; }

    @Nullable
    public byte[] getContent() { return getBuf(base, size); }

    //-------------------------------------------------------------------------
    // Native methods
    //-------------------------------------------------------------------------

    @Nullable
    private static native byte[] getBuf(long base, long size);
}
