//
// Copyright (c) 2020, 2019 Couchbase, Inc All rights reserved.
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

import androidx.annotation.Nullable;


/**
 * The representation of conflicting changes in the remote and local instances of a document.
 */
public class Conflict {
    @Nullable
    private final Document localDoc;
    @Nullable
    private final Document remoteDoc;

    Conflict(@Nullable Document localDoc, @Nullable Document remoteDoc) {
        this.localDoc = localDoc;
        this.remoteDoc = remoteDoc;
    }

    /**
     * The id of the conflicted document
     *
     * @return the document id.
     */
    @Nullable
    public String getDocumentId() {
        return (localDoc != null) ? localDoc.getId() : ((remoteDoc != null) ? remoteDoc.getId() : null);
    }

    /**
     * The local instance of the document.
     *
     * @return the local version of the document
     */
    @Nullable
    public Document getLocalDocument() { return localDoc; }

    /**
     * The remote instance of the document.
     *
     * @return the remote version of the document
     */
    @Nullable
    public Document getRemoteDocument() { return remoteDoc; }
}
