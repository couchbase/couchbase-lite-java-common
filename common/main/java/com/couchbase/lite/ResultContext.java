//
// Copyright (c) 2024 Couchbase, Inc All rights reserved.
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.couchbase.lite.internal.DbContext;
import com.couchbase.lite.internal.utils.Internal;


@Internal("This class is not part of the public API")
public class ResultContext extends DbContext {
    @NonNull
    private final ResultSet rs;

    public ResultContext(@Nullable AbstractDatabase db, @NonNull ResultSet rs) {
        super(db);
        this.rs = rs;
    }

    @Override
    @Nullable
    public AbstractDatabase getDatabase() { return (AbstractDatabase) super.getDatabase(); }

    @Override
    public boolean isClosed() { return rs.isClosed(); }

    @NonNull
    public ResultSet getResultSet() { return rs; }
}
