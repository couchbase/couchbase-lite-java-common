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


/**
 * Custom conflict resolution strategies implement this interface.
 */
@FunctionalInterface
public interface ConflictResolver {
    /**
     * The default conflict resolution strategy.
     * Deletion always wins.  A newer doc always beats an older one.
     * Otherwise one of the two document is chosen randomly but deterministically.
     */
    ConflictResolver DEFAULT = new DefaultConflictResolver();

    /**
     * Callback: called when there are conflicting changes in the local
     * and remote versions of a document during replication.
     *
     * @param conflict Description of the conflicting documents.
     * @return the resolved doc.
     */
    @Nullable
    Document resolve(@NonNull Conflict conflict);
}

/**
 * The default conflict resolver.
 */
class DefaultConflictResolver implements ConflictResolver {
    @Nullable
    @Override
    public Document resolve(@NonNull Conflict conflict) {
        // deletion always wins.
        final Document localDoc = conflict.getLocalDocument();
        final Document remoteDoc = conflict.getRemoteDocument();
        if ((localDoc == null) || (remoteDoc == null)) { return null; }

        // if one of the docs is newer, return it
        final long localGen = localDoc.generation();
        final long remoteGen = remoteDoc.generation();
        if (localGen > remoteGen) { return localDoc; }
        else if (localGen < remoteGen) { return remoteDoc; }

        // otherwise, choose one randomly, but deterministically.
        final String localRevId = localDoc.getRevisionID();
        if (localRevId == null) { return remoteDoc; }
        final String remoteRevId = remoteDoc.getRevisionID();
        return ((remoteRevId == null) || (localRevId.compareTo(remoteRevId) <= 0)) ? remoteDoc : localDoc;
    }
}
