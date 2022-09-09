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
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.couchbase.lite.internal.utils.Preconditions;


public class MDict extends MCollection implements Iterable<String> {
    @NonNull
    private Map<String, MValue> valueMap = new HashMap<>();
    @Nullable
    private FLDict flDict;
    private long valCount;


    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    public MDict() { }

    public MDict(@NonNull MValue mv, @Nullable MCollection parent) {
        initInSlot(mv, parent, parent != null && parent.hasMutableChildren());
    }

    public MDict(@NonNull MDict mDict, boolean isMutable) {
        super.initAsCopyOf(mDict, isMutable);
        flDict = mDict.flDict;
        valueMap = new HashMap<>(mDict.valueMap);
        valCount = mDict.valCount;
    }

    //---------------------------------------------
    // Public methods
    //---------------------------------------------

    public long count() { return valCount; }

    @NonNull
    public MValue get(@NonNull String key) {
        Preconditions.assertNotNull(key, "key");

        final MValue v = valueMap.get(key);
        if (v != null) { return v; }

        final FLValue value = (flDict == null) ? null : flDict.get(key);
        return (value == null) ? MValue.EMPTY : setInMap(key, new MValue(value));
    }

    public boolean set(String key, @NonNull MValue value) {
        Preconditions.assertNotNull(key, "key");
        Preconditions.assertThat(this, "Cannot call set() on an immutable MDict", MCollection::isMutable);

        final MValue oValue = valueMap.get(key);
        if (oValue != null) {
            // Found in valueMap; update value:
            if (value.isEmpty() && oValue.isEmpty()) { return true; }
            mutate();
            valCount += (value.isEmpty() ? 0 : 1) - (oValue.isEmpty() ? 0 : 1);
            valueMap.put(key, value);
        }
        else {
            // Not found; check flDict:
            if (flDict != null && flDict.get(key) != null) {
                if (value.isEmpty()) { valCount--; }
            }
            else {
                if (value.isEmpty()) { return true; }
                else { valCount++; }
            }

            mutate();
            setInMap(key, value);
        }

        return true;
    }

    public boolean contains(String key) {
        Preconditions.assertNotNull(key, "key");
        final MValue mValue = valueMap.get(key);
        return (mValue != null) ? !mValue.isEmpty() : ((flDict != null) && (flDict.get(key) != null));
    }

    @NonNull
    public List<String> getKeys() {
        final List<String> keys = new ArrayList<>();
        for (Map.Entry<String, MValue> entry: valueMap.entrySet()) {
            if (!entry.getValue().isEmpty()) { keys.add(entry.getKey()); }
        }

        if ((flDict != null) && (flDict.count() > 0)) {
            try (FLDictIterator itr = new FLDictIterator(flDict)) {
                String key;
                while ((key = itr.getKey()) != null) {
                    if (!valueMap.containsKey(key)) { keys.add(key); }
                    itr.next();
                }
            }
        }

        return keys;
    }

    public boolean remove(String key) { return set(key, MValue.EMPTY); }

    public boolean clear() {
        Preconditions.assertThat(this, "Cannot call set on a non-mutable MDict", MCollection::isMutable);

        if (valCount == 0) { return true; }

        mutate();
        valueMap.clear();

        if ((flDict != null) && (flDict.count() > 0)) {
            try (FLDictIterator itr = new FLDictIterator(flDict)) {
                String key;
                while ((key = itr.getKey()) != null) {
                    valueMap.put(key, MValue.EMPTY);
                    itr.next();
                }
            }
        }

        valCount = 0;

        return true;
    }

    /* Iterable */

    @NonNull
    @Override
    public Iterator<String> iterator() { return getKeys().iterator(); }

    /* Encodable */

    @Override
    public void encodeTo(@NonNull FLEncoder enc) {
        if (!isMutated()) {
            if (flDict != null) { enc.writeValue(flDict); }
            else {
                enc.beginDict(0);
                enc.endDict();
            }
        }
        else {
            enc.beginDict(valCount);

            for (Map.Entry<String, MValue> entry: valueMap.entrySet()) {
                final MValue value = entry.getValue();
                if (!value.isEmpty()) {
                    enc.writeKey(entry.getKey());
                    value.encodeTo(enc);
                }
            }

            if ((flDict != null) && (flDict.count() > 0)) {
                try (FLDictIterator itr = new FLDictIterator(flDict)) {
                    String key;
                    while ((key = itr.getKey()) != null) {
                        if (!valueMap.containsKey(key)) {
                            enc.writeKey(key);
                            enc.writeValue(itr.getValue());
                        }
                        itr.next();
                    }
                }
            }

            enc.endDict();
        }
    }

    //---------------------------------------------
    // Protected methods
    //---------------------------------------------

    @Override
    protected final void initInSlot(@NonNull MValue mv, @Nullable MCollection parent, boolean isMutable) {
        super.initInSlot(mv, parent, isMutable);
        if (flDict != null) { throw new IllegalStateException("flDict is not null"); }

        final FLValue value = mv.getValue();
        if (value == null) {
            flDict = null;
            valCount = 0;
            return;
        }

        flDict = value.asFLDict();
        valCount = flDict.count();
    }

    //---------------------------------------------
    // Private (in class only)
    //---------------------------------------------

    @NonNull
    private MValue setInMap(@NonNull String key, @NonNull MValue value) {
        valueMap.put(key, value);
        return value;
    }
}
