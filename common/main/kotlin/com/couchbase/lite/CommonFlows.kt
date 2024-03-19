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

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.Executor
import kotlin.coroutines.ContinuationInterceptor


/**
 * A Flow of Collection changes.
 *
 * @param executor Optional executor on which to run the change listener. If no executor
 * is provided, the listener will be called on the Flow's CoroutineDispatcher.
 *
 * @see com.couchbase.lite.Collection.addChangeListener
 */
fun Collection.collectionChangeFlow(executor: Executor? = null) = callbackFlow {
    val token = this@collectionChangeFlow.addChangeListener(
        executor ?: (coroutineContext[ContinuationInterceptor] as? CoroutineDispatcher)?.asExecutor()
    ) {
        trySend(it)
    }
    awaitClose { token.remove() }
}

/**
 * A Flow of document changes.
 *
 * @param executor Optional executor on which to run the change listener. If no executor
 * is provided, the listener will be called on the Flow's CoroutineDispatcher.
 *
 * @see com.couchbase.lite.Collection.addDocumentChangeListener
 */
fun Collection.documentChangeFlow(documentId: String, executor: Executor? = null) = callbackFlow {
    val token = this@documentChangeFlow.addDocumentChangeListener(
        documentId,
        executor ?: (coroutineContext[ContinuationInterceptor] as? CoroutineDispatcher)?.asExecutor()
    ) {
        trySend(it)
    }
    awaitClose { token.remove() }
}

/**
 * A Flow of replicator state changes.
 *
 * @param executor Optional executor on which to run the change listener. If no executor
 * is provided, the listener will be called on the Flow's CoroutineDispatcher.
 *
 * @see com.couchbase.lite.Replicator.addChangeListener
 */
fun Replicator.replicatorChangesFlow(executor: Executor? = null) = callbackFlow {
    val token = this@replicatorChangesFlow.addChangeListener(
        executor ?: (coroutineContext[ContinuationInterceptor] as? CoroutineDispatcher)?.asExecutor()
    ) {
        trySend(it)
    }
    awaitClose { token.remove() }
}

/**
 * A Flow of document replications.
 *
 * @param executor Optional executor on which to run the change listener. If no executor
 * is provided, the listener will be called on the Flow's CoroutineDispatcher.
 *
 * @see com.couchbase.lite.Replicator.addDocumentReplicationListener
 */
fun Replicator.documentReplicationFlow(executor: Executor? = null) = callbackFlow {
    val token = this@documentReplicationFlow.addDocumentReplicationListener(
        executor ?: (coroutineContext[ContinuationInterceptor] as? CoroutineDispatcher)?.asExecutor()
    ) {
        trySend(it)
    }
    awaitClose { token.remove() }
}

/**
 * A Flow of query changes.
 *
 * @param executor Optional executor on which to run the change listener. If no executor
 * is provided, the listener will be called on the Flow's CoroutineDispatcher.
 *
 * @see com.couchbase.lite.Query.addChangeListener
 */

fun Query.queryChangeFlow(executor: Executor? = null) = callbackFlow {
    val token = this@queryChangeFlow.addChangeListener(
        executor ?: (coroutineContext[ContinuationInterceptor] as? CoroutineDispatcher)?.asExecutor()
    ) {
        trySend(it)
    }
    awaitClose { token.remove() }
}

/**
 * A Flow of database changes.
 *
 * @param executor Optional executor on which to run the change listener. If no executor
 * is provided, the listener will be called on the Flow's CoroutineDispatcher.
 *
 * @see com.couchbase.lite.Database.addChangeListener
 * @deprecated Use getCollection(String, String?).collectionChangeFlow(Executor?)
 */
@Suppress("DEPRECATION")
@Deprecated(
    "Use getCollection(String, String?).collectionChangeFlow(executor)",
    replaceWith = ReplaceWith("getCollection(String, String?).collectionChangeFlow(executor)")
)
fun Database.databaseChangeFlow(executor: Executor? = null) = callbackFlow {
    val token = this@databaseChangeFlow.addChangeListener(
        executor ?: (coroutineContext[ContinuationInterceptor] as? CoroutineDispatcher)?.asExecutor()
    ) {
        trySend(it)
    }
    awaitClose { this@databaseChangeFlow.removeChangeListener(token) }
}

/**
 * A Flow of document changes.
 *
 * @param executor Optional executor on which to run the change listener. If no executor
 * is provided, the listener will be called on the Flow's CoroutineDispatcher.
 *
 * @see com.couchbase.lite.Database.addDocumentChangeListener
 * @deprecated Use getCollection(String, String?).documentChangeFlow(String, Executor?)
 */
@Suppress("DEPRECATION")
@Deprecated(
    "Use getCollection(String, String?).documentChangeFlow(documentId, executor)",
    replaceWith = ReplaceWith("getCollection(String, String?).documentChangeFlow(documentId, executor)")
)
fun Database.documentChangeFlow(documentId: String, executor: Executor? = null) = callbackFlow {
    val token = this@documentChangeFlow.addDocumentChangeListener(
        documentId,
        executor ?: (coroutineContext[ContinuationInterceptor] as? CoroutineDispatcher)?.asExecutor()
    ) {
        trySend(it)
    }
    awaitClose { this@documentChangeFlow.removeChangeListener(token) }
}
