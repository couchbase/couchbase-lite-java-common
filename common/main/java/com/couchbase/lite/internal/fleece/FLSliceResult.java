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
 * This is an interesting object.  In the C code it is a struct.  It is a little bit
 * clumsy to pass structs back adn forth across the JNI boundary, so, instead,
 * the JNI creates a C4SliceResult on the heap, copies the struct into it and the hands
 * the reference to it, to Java.  Similarly, when it comes time to pass the C4SliceResult
 * to C code, the JNI must copy the contents out of the heap into a struct.  It must also
 * <b>free the memory!</b>  The heap artifact is nothing that LiteCore knows anything about.
 * <p>
 * If LiteCore handed the C4SliceResult to the JNI (and the JNI allocated heap space for it)
 * then the Java code must free it.  That's a <code>ManageFLSliceResult</code>.  If, on the
 * other hand, this is a C4SliceResult that the Java is handing to core, then the JNI will
 * free it after it copies the contents: as Jim Borden says: "The bus has reached its last stop"
 * That is an <code>UnmanagedFLSliceResult</code>
 */
public class FLSliceResult {
    final long base;
    final long size;

    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------

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
