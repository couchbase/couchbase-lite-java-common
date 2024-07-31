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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.couchbase.lite.CouchbaseLiteError;


/**
 * Please see the comments in MValue
 */
public final class MDict extends MCollection {
    @NonNull
    private final Map<String, MValue> values = new HashMap<>();
    @Nullable
    private final FLDict baseDict;

    private long valCount;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    // Construct a new empty MDict
    public MDict() {
        super(MContext.NULL, true);
        baseDict = null;
    }

    // Copy constructor
    public MDict(@NonNull MDict dict, boolean isMutable) {
        super(dict, isMutable);
        values.putAll(dict.values);
        baseDict = dict.baseDict;
        valCount = dict.valCount;
    }

    // Slot(??) constructor
    public MDict(@NonNull MValue val, @Nullable MCollection parent) {
        super(val, parent, parent != null && parent.hasMutableChildren());

        final FLValue value = val.getFLValue();
        if (value == null) {
            baseDict = null;
            return;
        }

        baseDict = value.asFLDict();
        valCount = baseDict.count();
    }

    //---------------------------------------------
    // Public methods
    //---------------------------------------------

    /**
     * The number of items in the dictionary.
     *
     * @return dictionary size
     */
    public long count() { return valCount; }

    /**
     * Find out if the dictionary contains the passed key.
     *
     * @return true if the dictionary contains the key
     */
    public boolean contains(String key) {
        assertOpen();
        final MValue val = values.get(key);
        return (val != null) ? !val.isEmpty() : ((baseDict != null) && (baseDict.get(key) != null));
    }

    /**
     * Get a list of the keys in the dictionary.
     *
     * @return the dictionary keys, as a list
     */
    @NonNull
    public List<String> getKeys() {
        assertOpen();

        final List<String> keys = new ArrayList<>();
        for (Map.Entry<String, MValue> entry: values.entrySet()) {
            if (!entry.getValue().isEmpty()) { keys.add(entry.getKey()); }
        }

        if ((baseDict != null) && (baseDict.count() > 0)) {
            try (FLDictIterator itr = baseDict.iterator()) {
                String key;
                while ((key = itr.getKey()) != null) {
                    if (!values.containsKey(key)) { keys.add(key); }
                    itr.next();
                }
            }
        }

        return keys;
    }

    @NonNull
    public MValue get(@NonNull String key) {
        assertOpen();

        MValue mValue = values.get(key);
        if (mValue != null) { return mValue; }

        final FLValue flValue = (baseDict == null) ? null : baseDict.get(key);
        if (flValue == null) { return MValue.EMPTY; }

        mValue = new MValue(flValue);
        values.put(key, mValue);

        return mValue;
    }

    @SuppressWarnings("PMD.CognitiveComplexity")
    public void set(String key, @NonNull MValue value) {
        if (!isMutable()) { throw new CouchbaseLiteError("Cannot set items in a non-mutable MDict"); }
        assertOpen();

        final boolean hasVal = !value.isEmpty();

        final MValue oValue = values.get(key);
        if (oValue != null) {
            // Found in valueMap: update value

            final boolean hasOVal = !oValue.isEmpty();

            // was empty; is still empty.  all done.
            if (!hasVal && !hasOVal) { return; }

            // can't be -1 because of the previous test.
            valCount += (hasVal ? 1 : 0) - (hasOVal ? 1 : 0);
        }
        else {
            // Not found in valueMap: check the baseDict:

            if ((baseDict != null) && (baseDict.get(key) != null)) {
                if (!hasVal) { valCount--; }
            }
            else {
                if (!hasVal) { return; }
                else { valCount++; }
            }
        }

        mutate();
        values.put(key, value);
    }

    public void remove(String key) {
        if (!isMutable()) { throw new CouchbaseLiteError("Cannot remove items in a non-mutable MDict"); }
        set(key, MValue.EMPTY);
    }

    public void clear() {
        if (!isMutable()) { throw new CouchbaseLiteError("Cannot clear items from a non-mutable MDict"); }
        assertOpen();

        if (valCount == 0) { return; }

        mutate();
        values.clear();

        if ((baseDict != null) && (baseDict.count() > 0)) {
            try (FLDictIterator itr = baseDict.iterator()) {
                String key;
                while ((key = itr.getKey()) != null) {
                    values.put(key, MValue.EMPTY);
                    itr.next();
                }
            }
        }

        valCount = 0;
    }

    /* Encodable */

    @Override
    public void encodeTo(@NonNull FLEncoder enc) {
        assertOpen();

        if (!isMutated()) {
            if (baseDict != null) {
                enc.writeValue(baseDict);
                return;
            }

            enc.beginDict(0);
            enc.endDict();
            return;
        }

        enc.beginDict(valCount);
        for (Map.Entry<String, MValue> entry: values.entrySet()) {
            final MValue value = entry.getValue();
            if (!value.isEmpty()) {
                enc.writeKey(entry.getKey());
                value.encodeTo(enc);
            }
        }

        if ((baseDict != null) && (baseDict.count() > 0)) {
            try (FLDictIterator itr = baseDict.iterator()) {
                String key;
                while ((key = itr.getKey()) != null) {
                    if (!values.containsKey(key)) {
                        enc.writeKey(key);
                        enc.writeValue(itr.getFLValue());
                    }
                    itr.next();
                }
            }
        }

        enc.endDict();
    }
}
