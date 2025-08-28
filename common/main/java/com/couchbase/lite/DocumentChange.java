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


/**
 * Provides details about a Document change.
 */
public final class DocumentChange {
    @NonNull
    private final Collection collection;
    @NonNull
    private final String documentID;

    DocumentChange(@NonNull Collection collection, @NonNull String documentID) {
        this.collection = collection;
        this.documentID = documentID;
    }

    /**
     * Return the Document's collection
     */
    @NonNull
    public Collection getCollection() { return collection; }

    /**
     * Returns the changed document ID
     */
    @NonNull
    public String getDocumentID() { return documentID; }

    @NonNull
    @Override
    public String toString() { return "DocumentChange{" + collection + ", " + documentID + "}"; }
}
