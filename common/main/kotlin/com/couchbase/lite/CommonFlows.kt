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
package com.couchbase.lite

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.Executor

/**
 * A Flow of database changes.
 *
 * @param executor Optional executor on which to run the change listener: default is the main executor
 *
 * @see com.couchbase.lite.Database.addChangeListener
 */
@ExperimentalCoroutinesApi
fun Database.databaseChangeFlow(executor: Executor? = null) = callbackFlow<DatabaseChange> {
    val token = this@databaseChangeFlow.addChangeListener(executor) { trySend(it) }
    awaitClose { this@databaseChangeFlow.removeChangeListener(token) }
}

/**
 * A Flow of document changes.
 *
 * @param executor Optional executor on which to run the change listener: default is the main thread
 *
 * @see com.couchbase.lite.Database.addDocumentChangeListener
 */
@ExperimentalCoroutinesApi
fun Database.documentChangeFlow(documentId: String, executor: Executor? = null) = callbackFlow {
    val token =
        this@documentChangeFlow.addDocumentChangeListener(documentId, executor) { trySend(it) }
    awaitClose { this@documentChangeFlow.removeChangeListener(token) }
}

/**
 * A Flow of replicator state changes.
 *
 * @param executor Optional executor on which to run the change listener: default is the main thread
 *
 * @see com.couchbase.lite.Replicator.addChangeListener
 */
@ExperimentalCoroutinesApi
fun Replicator.replicatorChangesFlow(executor: Executor? = null) = callbackFlow {
    val token = this@replicatorChangesFlow.addChangeListener(executor) { trySend(it) }
    awaitClose { this@replicatorChangesFlow.removeChangeListener(token) }
}

/**
 * A Flow of document replications.
 *
 * @param executor Optional executor on which to run the change listener: default is the main thread
 *
 * @see com.couchbase.lite.Replicator.addDocumentReplicationListener
 */
@ExperimentalCoroutinesApi
fun Replicator.documentReplicationFlow(executor: Executor? = null) = callbackFlow {
    val token =
        this@documentReplicationFlow.addDocumentReplicationListener(executor) { trySend(it) }
    awaitClose { this@documentReplicationFlow.removeChangeListener(token) }
}

/**
 * A Flow of query changes.
 *
 * @param executor Optional executor on which to run the change listener: default is the main thread
 *
 * @see com.couchbase.lite.Query.addChangeListener
 */
@ExperimentalCoroutinesApi
fun Query.queryChangeFlow(executor: Executor? = null) = callbackFlow {
    val token = this@queryChangeFlow.addChangeListener(executor) { trySend(it) }
    awaitClose { this@queryChangeFlow.removeChangeListener(token) }
}
