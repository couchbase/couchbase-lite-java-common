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

import com.couchbase.lite.internal.core.C4Query;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.ClassUtils;
import com.couchbase.lite.internal.utils.Preconditions;
import com.couchbase.lite.internal.utils.StringUtils;


final class N1qlQuery extends AbstractQuery {
    @NonNull
    private final String n1ql;
    @NonNull
    private final AbstractDatabase db;

    N1qlQuery(@NonNull AbstractDatabase db, @NonNull String n1ql) {
        this.n1ql = Preconditions.assertNotNull(n1ql, "query");
        this.db = Preconditions.assertNotNull(db, "database");
    }

    @NonNull
    @Override
    public String toString() { return "N1qlQuery{" + ClassUtils.objId(this) + ", n1ql=" + n1ql + "}"; }

    @NonNull
    @Override
    protected AbstractDatabase getDatabase() { return db; }

    @GuardedBy("AbstractQuery.lock")
    @NonNull
    @Override
    protected C4Query prepQueryLocked(@NonNull AbstractDatabase db) throws CouchbaseLiteException {
        Log.d(DOMAIN, "N1QL query: %s", n1ql);
        if (StringUtils.isEmpty(n1ql)) { throw new CouchbaseLiteException("Query is null or empty."); }
        try { return db.createN1qlQuery(n1ql); }
        catch (LiteCoreException e) { throw CouchbaseLiteException.convertException(e); }
    }
}
