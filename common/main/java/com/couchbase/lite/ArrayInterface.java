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
package com.couchbase.lite;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Date;
import java.util.List;

import com.couchbase.lite.internal.utils.Internal;


/**
 * Internal interface
 */
// You look at this and the DictionaryInterface and you see that they
// are, obviously, the same and should be a single inteface with a
// generic key type. Just try it...
// Result extends both interfaces.  You can't extend
// two interfaces with the same erasure.  Curses!!!
@Internal("This interface  is not part of the public API")
public interface ArrayInterface extends CollectionInterface {
    int getInt(int index);

    long getLong(int index);

    float getFloat(int index);

    double getDouble(int index);

    boolean getBoolean(int index);

    @Nullable
    Number getNumber(int index);

    @Nullable
    String getString(int index);

    @Nullable
    Date getDate(int index);

    @Nullable
    Blob getBlob(int index);

    @Nullable
    ArrayInterface getArray(int index);

    @Nullable
    DictionaryInterface getDictionary(int index);

    @Nullable
    Object getValue(int index);

    @NonNull
    List<Object> toList();
}
