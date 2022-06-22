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

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import com.couchbase.lite.internal.core.C4BlobStore;
import com.couchbase.lite.internal.core.C4Database;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.Internal;
import com.couchbase.lite.internal.utils.Preconditions;


@Internal("This class is not part of the public API")
@SuppressWarnings("PMD.AbstractClassWithoutAbstractMethod")
public abstract class BaseDatabase {
    // Main database lock object for thread-safety
    @NonNull
    private final Object dbLock = new Object();

    @GuardedBy("dbLock")
    @Nullable
    private C4Database c4Database;

    @GuardedBy("dbLock")
    @Nullable
    private String path;

    @GuardedBy("dbLock")
    protected void setC4DatabaseLocked(@Nullable C4Database c4Database) {
        this.c4Database = c4Database;
        if (c4Database != null) { this.path = c4Database.getDbPath(); }
    }

    @GuardedBy("dbLock")
    protected boolean isOpen() { return c4Database != null; }

    @GuardedBy("dbLock")
    protected void assertOpenUnchecked() {
        if (!isOpen()) { throw new IllegalStateException(Log.lookupStandardMessage("DBClosed")); }
    }

    protected void assertOpenChecked() throws CouchbaseLiteException {
        if (!isOpen()) {
            throw new CouchbaseLiteException(
                Log.lookupStandardMessage("DBClosed"),
                CBLError.Domain.CBLITE,
                CBLError.Code.NOT_OPEN);
        }
    }

    @GuardedBy("dbLock")
    @NonNull
    protected C4Database getOpenC4DbLocked() {
        assertOpenUnchecked();
        return Preconditions.assertNotNull(c4Database, "c4db");
    }

    @GuardedBy("dbLock")
    @NonNull
    protected C4Database getC4DbOrThrowLocked() throws CouchbaseLiteException {
        assertOpenChecked();
        return Preconditions.assertNotNull(c4Database, "c4db");
    }

    // When seizing multiple locks, always seize this lock first.
    @NonNull
    protected Object getDbLock() { return dbLock; }

    @Nullable
    protected String getDbPath() {
        synchronized (getDbLock()) { return path; }
    }

    @NonNull
    protected C4BlobStore getBlobStore() throws LiteCoreException {
        synchronized (getDbLock()) { return getOpenC4DbLocked().getBlobStore(); }
    }


    @VisibleForTesting
    @SuppressFBWarnings("NP_NONNULL_RETURN_VIOLATION")
    @NonNull
    C4Database getOpenC4Database() {
        synchronized (getDbLock()) { return getOpenC4DbLocked(); }
    }
}
