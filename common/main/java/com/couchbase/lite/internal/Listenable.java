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
package com.couchbase.lite.internal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.Executor;

import com.couchbase.lite.ChangeListener;
import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.ListenerToken;


// Requiring implementer to specify the types of both T and the ChangeListener for T
// is pretty ugly and annoying.  I could not figure out another way to do it, though...
public interface Listenable<T, L extends ChangeListener<T>> {
    @NonNull
    ListenerToken addChangeListener(@NonNull L listener) throws CouchbaseLiteException;
    @NonNull
    ListenerToken addChangeListener(@Nullable Executor executor, @NonNull L listener) throws CouchbaseLiteException;
 }
