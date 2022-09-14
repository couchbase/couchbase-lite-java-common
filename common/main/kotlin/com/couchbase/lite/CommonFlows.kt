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
package com.couchbase.lite

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.merge
import java.lang.IllegalStateException
import java.util.concurrent.Executor

/**
 * A Flow of database changes.
 *
 * @param executor Optional executor on which to run the change listener: default is the main executor
 * @deprecated Use getDefaultCollection().collectionChangeFlow
 * @see com.couchbase.lite.Database.addChangeListener
 */
@Deprecated(
    "Use getDefaultCollection().collectionChangeFlow(executor",
    replaceWith = ReplaceWith("getDefaultCollection().collectionChangeFlow(executor)"))
fun Database.databaseChangeFlow(executor: Executor? = null) =
    this@databaseChangeFlow.defaultCollection?.collectionChangeFlow(executor)
        ?: throw IllegalStateException("Cannot get default collection for database ${this@databaseChangeFlow.name}")

/**
 * A Flow of Collection changes.
 *
 * @param executor The executor on which to run the change listener (Should be nullable?)
 *
 * @see com.couchbase.lite.Collection.addChangeListener
 */
fun Collection.collectionChangeFlow(executor: Executor?) = callbackFlow {
    val token = this@collectionChangeFlow.addChangeListener(executor) { trySend(it) }
    awaitClose { token.remove() }
}

/**
 * A Flow of document changes.
 *
 * @param executor Optional executor on which to run the change listener: default is the main thread
 * @deprecated Use getDefaultCollection().documentChangeFlow
 * @see com.couchbase.lite.Database.addDocumentChangeListener
 */
@Deprecated(
    "Use getDefaultCollection().documentChangeFlow(documentId, executor)",
    replaceWith = ReplaceWith("getDefaultCollection().documentChangeFlow(documentId, executor)"))
fun Database.documentChangeFlow(documentId: String, executor: Executor? = null) =
    this@documentChangeFlow.defaultCollection?.documentChangeFlow(documentId, executor)
        ?: throw IllegalStateException("Cannot get default collection for database ${this@documentChangeFlow.name}")

/**
 * A Flow of document changes
 * @param executor Optional executor on which to run the change listener: default is the main thread
 *
 * @see com.couchbase.lite.Collection.addDocumentChangeListener
 */
fun Collection.documentChangeFlow(documentId: String, executor: Executor? = null) = callbackFlow {
    val token = this@documentChangeFlow.addDocumentChangeListener(documentId, executor) { trySend(it) }
    awaitClose { token.remove() }
}

/**
 * A Flow of replicator state changes.
 *
 * @param executor Optional executor on which to run the change listener: default is the main thread
 *
 * @see com.couchbase.lite.Replicator.addChangeListener
 */
fun Replicator.replicatorChangesFlow(executor: Executor? = null) = callbackFlow {
    val token = this@replicatorChangesFlow.addChangeListener(executor) { trySend(it) }
    awaitClose { token.remove() }
}

/**
 * A Flow of document replications.
 *
 * @param executor Optional executor on which to run the change listener: default is the main thread
 *
 * @see com.couchbase.lite.Replicator.addDocumentReplicationListener
 */
fun Replicator.documentReplicationFlow(executor: Executor? = null) = callbackFlow {
    val token = this@documentReplicationFlow.addDocumentReplicationListener(executor) { trySend(it) }
    awaitClose { token.remove() }
}

/**
 * A Flow of query changes.
 *
 * @param executor Optional executor on which to run the change listener: default is the main thread
 *
 * @see com.couchbase.lite.Query.addChangeListener
 */

fun Query.queryChangeFlow(executor: Executor? = null) = callbackFlow {
    val token = this@queryChangeFlow.addChangeListener(executor) { trySend(it) }
    awaitClose { token.remove() }
}
