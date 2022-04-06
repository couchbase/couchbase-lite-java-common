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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;


public abstract class AbstractIndex {
    public enum QueryLanguage {
        JSON(0), N1QL(1);

        private final int value;

        QueryLanguage(int value) { this.value = value; }

        public int getValue() { return value; }
    }

    public enum IndexType {
        VALUE(0), FULL_TEXT(1), PREDICTIVE(3);

        private final int value;

        IndexType(int value) { this.value = value; }

        public int getValue() { return value; }
    }

    @NonNull
    private final QueryLanguage queryLanguage;
    @NonNull
    private final IndexType indexType;

    protected AbstractIndex(@NonNull IndexType indexType, @NonNull QueryLanguage queryLanguage) {
        this.indexType = indexType;
        this.queryLanguage = queryLanguage;
    }

    @NonNull
    abstract String getIndexSpec() throws CouchbaseLiteException;

    @NonNull
    final QueryLanguage getQueryLanguage() { return queryLanguage; }

    @NonNull
    final IndexType getIndexType() { return indexType; }

    // Default value: may be overridden
    @SuppressWarnings("PMD.EmptyMethodInAbstractClassShouldBeAbstract")
    @Nullable
    String getLanguage() { return null; }

    // Default value: may be overridden
    boolean isIgnoringDiacritics() { return false; }

    @NonNull
    @Override
    public String toString() { return "IndexDescriptor(" + getQueryLanguage() + ", " + indexType + "}"; }
}
