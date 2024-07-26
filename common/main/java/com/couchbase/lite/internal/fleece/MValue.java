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

import com.couchbase.lite.CouchbaseLiteError;
import com.couchbase.lite.MValueConverter;
import com.couchbase.lite.internal.utils.Preconditions;


public class MValue extends MValueConverter implements Encodable {

    //-------------------------------------------------------------------------
    // Static members
    //-------------------------------------------------------------------------

    static final MValue EMPTY = new MValue(null, null) {
        @Override
        public boolean isEmpty() { return true; }
    };

    //-------------------------------------------------------------------------
    // Instance members
    //-------------------------------------------------------------------------

    @Nullable
    private FLValue flValue;
    @Nullable
    private Object value;

    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------

    public MValue(@Nullable Object obj) { this(obj, null); }

    MValue(@Nullable FLValue val) { this(null, val); }

    private MValue(@Nullable Object obj, @Nullable FLValue val) {
        value = obj;
        this.flValue = val;
    }

    //-------------------------------------------------------------------------
    // Public methods
    //-------------------------------------------------------------------------

    @Override
    public void encodeTo(@NonNull FLEncoder enc) {
        if (isEmpty()) { throw new CouchbaseLiteError("MValue is empty."); }

        if (flValue != null) { enc.writeValue(flValue); }
        else if (value != null) { enc.writeValue(value); }
        else { enc.writeNull(); }
    }

    public boolean isEmpty() { return false; }

    public boolean isMutated() { return flValue == null; }

    @Nullable
    public FLValue getFLValue() { return flValue; }

    public void mutate() {
        Preconditions.assertNotNull(value, "Mutated object");
        flValue = null;
    }

    @Nullable
    public Object toJava(@Nullable MCollection parent) {
        if ((value != null) || (flValue == null)) { return value; }

        final JavaValue val = toJava(this, parent);
        if (val.cacheIt) { value = val.nVal; }
        return val.nVal;
    }

    @Override
    @NonNull
    public String toString() {
        return "MValue{"
            + ((flValue == null) ? -1 : flValue.getType())
            + ", " + ((value == null) ? "" : (value.getClass()) + "=")
            + value
            + "}";
    }
}
