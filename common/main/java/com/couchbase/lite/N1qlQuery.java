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

import android.support.annotation.NonNull;

import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.core.C4Query;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.ClassUtils;
import com.couchbase.lite.internal.utils.Preconditions;


public final class N1qlQuery extends AbstractQuery {
    @NonNull
    private final String n1ql;
    @NonNull
    private final AbstractDatabase db;

    public N1qlQuery(@NonNull AbstractDatabase db, @NonNull String n1ql) {
        this.n1ql = Preconditions.assertNotNull(n1ql, "query");
        this.db = Preconditions.assertNotNull(db, "database");
    }

    @NonNull
    @Override
    public String toString() {
        return "N1qlQuery{" + ClassUtils.objId(this) + ", n1ql=" + n1ql + "}";
    }

    @NonNull
    @Override
    protected C4Query prepQueryLocked() throws CouchbaseLiteException {
        if (CouchbaseLiteInternal.debugging()) { Log.d(DOMAIN, "N1QL query: %s", n1ql); }

        try { return db.createN1qlQuery(n1ql); }
        catch (LiteCoreException e) { throw CouchbaseLiteException.convertException(e); }
    }

    @NonNull
    @Override
    protected AbstractDatabase getDatabase() { return db; }
}
