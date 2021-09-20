//
// Copyright (c) 2021 Couchbase, Inc All rights reserved.
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

    @SuppressWarnings("ConstantConditions")
    @SuppressFBWarnings("NP_NONNULL_RETURN_VIOLATION")
    @GuardedBy("dbLock")
    @NonNull
    protected C4Database getOpenC4DbLocked() {
        mustBeOpen();
        return c4Database;
    }

    @GuardedBy("dbLock")
    protected boolean isOpen() { return c4Database != null; }

    @GuardedBy("dbLock")
    protected void mustBeOpen() {
        if (!isOpen()) { throw new IllegalStateException(Log.lookupStandardMessage("DBClosed")); }
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

    @SuppressWarnings("ConstantConditions")
    @SuppressFBWarnings("NP_NONNULL_RETURN_VIOLATION")
    @NonNull
    @VisibleForTesting
    C4Database getOpenC4Database() {
        synchronized (getDbLock()) {
            mustBeOpen();
            return c4Database;
        }
    }
}
