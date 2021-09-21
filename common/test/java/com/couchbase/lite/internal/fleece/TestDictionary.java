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

import androidx.annotation.NonNull;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


// Simplified com.couchbase.lite.Dictionary, for testing
public class TestDictionary implements Map<String, Object>, Encodable {
    //---------------------------------------------
    // Data members
    //---------------------------------------------

    private final MDict dict;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    // Call from native method
    TestDictionary(MValue mv, MCollection parent) { dict = new MDict(mv, parent); }

    //---------------------------------------------
    // Public methods
    //---------------------------------------------

    // FLEncodable

    @Override
    public void encodeTo(FLEncoder enc) { dict.encodeTo(enc); }

    // Map

    @Override
    public int size() { return (int) dict.count(); }

    @Override
    public boolean isEmpty() { return size() == 0; }

    @Override
    public boolean containsKey(Object o) {
        if (!(o instanceof String)) { return false; }
        return dict.contains((String) o);
    }

    @Override
    public Object get(Object key) {
        if (!(key instanceof String)) { return null; }
        return dict.get((String) key).asNative(dict);
    }

    @Override
    public Object put(String key, Object o) {
        Object prev = null;
        if (dict.contains(key)) { prev = dict.get(key); }
        dict.set(key, new MValue(o));
        return prev;
    }

    @Override
    public Object remove(Object key) {
        if (!(key instanceof String)) { return null; }
        Object prev = null;
        if (dict.contains((String) key)) { prev = get(key); }
        dict.remove((String) key);
        return prev;
    }

    @Override
    public void clear() { dict.clear(); }

    @NonNull
    @Override
    public Set<String> keySet() { return new HashSet<>(dict.getKeys()); }

    @NonNull
    @Override
    public Set<Entry<String, Object>> entrySet() {
        Set<Entry<String, Object>> entrySet = new HashSet<>();
        for (String key: dict.getKeys()) { entrySet.add(new AbstractMap.SimpleEntry<>(key, get(key))); }
        return entrySet;
    }

    @Override
    public boolean containsValue(Object o) {
        throw new UnsupportedOperationException("containsValue(Object) not supported for FleeceDict");
    }

    @Override
    public void putAll(@NonNull Map<? extends String, ?> map) {
        throw new UnsupportedOperationException("putAll(Map) not supported for FleeceDict");
    }

    @NonNull
    @Override
    public Collection<Object> values() {
        throw new UnsupportedOperationException("values() not supported for FleeceDict");
    }

    public boolean isMutated() { return dict.isMutated(); }

    //---------------------------------------------
    // Package protected methods
    //---------------------------------------------

    // MValue
    MCollection toMCollection() { return dict; }
}
