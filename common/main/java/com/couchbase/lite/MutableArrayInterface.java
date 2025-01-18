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
 * This is an internal interface and not part of the public API.
 */
@Internal("This interface is not part of the public API")
public interface MutableArrayInterface extends ArrayInterface {

    @Nullable
    @Override
    MutableArrayInterface getArray(int index);
    @Nullable
    @Override
    MutableDictionaryInterface getDictionary(int index);

    // remove

    @NonNull
    MutableArrayInterface remove(int value);

    // set

    @NonNull
    MutableArrayInterface setInt(int index, int value);

    @NonNull
    MutableArrayInterface setLong(int index, long value);

    @NonNull
    MutableArrayInterface setFloat(int index, float value);

    @NonNull
    MutableArrayInterface setDouble(int index, double value);

    @NonNull
    MutableArrayInterface setBoolean(int index, boolean value);

    @NonNull
    MutableArrayInterface setDate(int index, @Nullable Date value);

    @NonNull
    MutableArrayInterface setBlob(int index, @Nullable Blob value);

    @NonNull
    MutableArrayInterface setArray(int index, @Nullable Array value);

    @NonNull
    MutableArrayInterface setDictionary(int index, @Nullable Dictionary value);

    @NonNull
    MutableArrayInterface setString(int index, @Nullable String value);

    @NonNull
    MutableArrayInterface setNumber(int index, @Nullable Number value);

    @NonNull
    MutableArrayInterface setValue(int index, @Nullable Object value);

    @NonNull
    MutableArrayInterface setData(@NonNull List<?> data);

    @NonNull
    MutableArrayInterface setJSON(@NonNull String json);

    // add

    @NonNull
    MutableArrayInterface addInt(int value);

    @NonNull
    MutableArrayInterface addLong(long value);

    @NonNull
    MutableArrayInterface addFloat(float value);

    @NonNull
    MutableArrayInterface addDouble(double value);

    @NonNull
    MutableArrayInterface addBoolean(boolean value);

    @NonNull
    MutableArrayInterface addString(@Nullable String value);

    @NonNull
    MutableArrayInterface addNumber(@Nullable Number value);

    @NonNull
    MutableArrayInterface addDate(@Nullable Date value);

    @NonNull
    MutableArrayInterface addBlob(@Nullable Blob value);

    @NonNull
    MutableArrayInterface addArray(@Nullable Array value);

    @NonNull
    MutableArrayInterface addDictionary(@Nullable Dictionary value);

    @NonNull
    MutableArrayInterface addValue(@Nullable Object value);

    // insert

    @NonNull
    MutableArrayInterface insertInt(int index, int value);

    @NonNull
    MutableArrayInterface insertLong(int index, long value);

    @NonNull
    MutableArrayInterface insertFloat(int index, float value);

    @NonNull
    MutableArrayInterface insertDouble(int index, double value);

    @NonNull
    MutableArrayInterface insertBoolean(int index, boolean value);

    @NonNull
    MutableArrayInterface insertDate(int index, @Nullable Date value);

    @NonNull
    MutableArrayInterface insertBlob(int index, @Nullable Blob value);

    @NonNull
    MutableArrayInterface insertNumber(int index, @Nullable Number value);

    @NonNull
    MutableArrayInterface insertString(int index, @Nullable String value);

    @NonNull
    MutableArrayInterface insertArray(int index, @Nullable Array value);

    @NonNull
    MutableArrayInterface insertDictionary(int index, @Nullable Dictionary value);

    @NonNull
    MutableArrayInterface insertValue(int index, @Nullable Object value);
}
