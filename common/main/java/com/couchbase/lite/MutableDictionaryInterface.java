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
import java.util.Map;

import com.couchbase.lite.internal.utils.Internal;


/**
 * This is an internal interface and not part of the public API.
 */
@Internal("This interface is not part of the public API")
public interface MutableDictionaryInterface extends DictionaryInterface {
    @Nullable
    @Override
    MutableArrayInterface getArray(@NonNull String key);

    @Nullable
    @Override
    MutableDictionaryInterface getDictionary(@NonNull String key);

    // remove

    @NonNull
    MutableDictionaryInterface remove(@NonNull String key);

    // set

    @NonNull
    MutableDictionaryInterface setInt(@NonNull String key, int value);

    @NonNull
    MutableDictionaryInterface setLong(@NonNull String key, long value);

    @NonNull
    MutableDictionaryInterface setFloat(@NonNull String key, float value);

    @NonNull
    MutableDictionaryInterface setDouble(@NonNull String key, double value);

    @NonNull
    MutableDictionaryInterface setBoolean(@NonNull String key, boolean value);

    @NonNull
    MutableDictionaryInterface setNumber(@NonNull String key, @NonNull Number value);

    @NonNull
    MutableDictionaryInterface setString(@NonNull String key, @NonNull String value);

    @NonNull
    MutableDictionaryInterface setDate(@NonNull String key, @NonNull Date value);

    @NonNull
    MutableDictionaryInterface setBlob(@NonNull String key, @NonNull Blob value);

    @NonNull
    MutableDictionaryInterface setValue(@NonNull String key, @Nullable Object value);

    @NonNull
    MutableDictionaryInterface setArray(@NonNull String key, @NonNull Array value);

    @NonNull
    MutableDictionaryInterface setDictionary(@NonNull String key, @NonNull Dictionary value);

    @NonNull
    MutableDictionaryInterface setData(@NonNull Map<String, ?> data);

    @NonNull
    MutableDictionaryInterface setJSON(@NonNull String json);
}
