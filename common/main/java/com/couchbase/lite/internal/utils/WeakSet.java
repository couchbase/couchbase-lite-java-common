//
// Copyright (c) 2022 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite.internal.utils;

import androidx.annotation.NonNull;

import java.util.WeakHashMap;


public class WeakSet<T> {
    private final WeakHashMap<T, Void> items = new WeakHashMap<>();

    public void add(@NonNull T item) {
        synchronized (items) { items.put(item, null); }
    }

    public boolean isEmpty() {
        synchronized (items) { return items.isEmpty(); }
    }

    public int size() {
        synchronized (items) { return items.size(); }
    }

    public void forAll(Fn.Consumer<T> op) {
        synchronized (items) {
            for (T item: items.keySet()) { op.accept(item); }
        }
    }

     public void remove(@NonNull Object item) {
        synchronized (items) { items.remove(item); }
    }
}
