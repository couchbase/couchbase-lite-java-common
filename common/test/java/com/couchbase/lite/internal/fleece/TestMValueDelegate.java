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

import java.util.concurrent.atomic.AtomicBoolean;


public class TestMValueDelegate implements MValue.Delegate {

    //-------------------------------------------------------------------------
    // Public methods
    //-------------------------------------------------------------------------
    @Nullable
    @Override
    public Object toNative(@NonNull MValue mv, @Nullable MCollection parent, @NonNull AtomicBoolean cacheIt) {
        FLValue value = mv.getValue();
        int type = value.getType();
        switch (type) {
            case FLConstants.ValueType.ARRAY:
                cacheIt.set(true);
                return new TestArray(mv, parent);
            case FLConstants.ValueType.DICT:
                cacheIt.set(true);
                return new TestDictionary(mv, parent);
            default:
                return value.asObject();
        }
    }

    @Nullable
    @Override
    public MCollection collectionFromNative(@Nullable Object object) {
        if (object instanceof TestDictionary) { return ((TestDictionary) object).toMCollection(); }
        else if (object instanceof TestArray) { return ((TestArray) object).toMCollection(); }
        else { return null; }
    }

    @Override
    public void encodeNative(@NonNull FLEncoder enc, @Nullable Object object) {
        if (object == null) { enc.writeNull(); }
        else { enc.writeValue(object); }
    }
}
