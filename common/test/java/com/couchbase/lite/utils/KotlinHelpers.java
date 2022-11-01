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
package com.couchbase.lite.utils;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.couchbase.lite.Collection;
import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.LogFileConfiguration;
import com.couchbase.lite.Replicator;


// Utility class to make calls that Kotlin will not allow
public final class KotlinHelpers {
    private KotlinHelpers() { }

    // Kotlin will not allow a the call isDocumentPending(null)
    public static boolean callIsDocumentPendingWithNullId(@NonNull Replicator repl, @Nullable Collection collection)
        throws CouchbaseLiteException {
        return repl.isDocumentPending(null, collection);
    }

    // Kotlin will not allow a the call LogFileConfiguration.<init>((String) null)
    public static LogFileConfiguration createLogFileConfigWithNullDir() {
        return new LogFileConfiguration((String) null);
    }

    // Kotlin will not allow a the call LogFileConfiguration.<init>((LogFileConfiguration) null)
    public static LogFileConfiguration createLogFileConfigWithNullConfig() {
        return new LogFileConfiguration((LogFileConfiguration) null);
    }
}
