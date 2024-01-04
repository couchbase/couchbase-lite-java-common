//
// Copyright (c) 2023 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite.internal

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.couchbase.lite.AbstractReplicatorConfiguration
import com.couchbase.lite.CBLError
import com.couchbase.lite.CouchbaseLiteException
import com.couchbase.lite.LogDomain
import com.couchbase.lite.Replicator
import com.couchbase.lite.ReplicatorActivityLevel
import com.couchbase.lite.ReplicatorStatus
import com.couchbase.lite.WorkManagerReplicatorFactory
import com.couchbase.lite.internal.logging.Log
import com.couchbase.lite.replicatorChangesFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.runBlocking


/**
 * Implementation Notes:
 *
 * Android does not support daemon processes. Although it is less likely to happen on modern phones
 * with lots of memory, Android will still kill off a running application if it needs the memory space
 * to run a new app. Under these circumstance, CouchbaseLite's continuous replication makes sense only
 * while an application is in the foreground. Once an application is put in the background, it will,
 * eventually, get killed and replicators will be stopped with prejudice. They will not be restarted
 * until a user manually restarts the app and the replication.
 *
 * In addition to this issue, continuous replication is incredibly wasteful of battery. It will force
 * a mobile device to keep its radio on: the second most expensive thing a device can do, battery-wise.
 *
 * Android provides a facility for managing long running processes: the `WorkManager`. Jobs scheduled
 * with the `WorkManager` are persistent. They are also batched across applications in order to
 * optimize radio use. This package integrates replication into the `WorkManage`. It works like this:
 * Client code schedules replication work using normal `WorkManager` code, like this:
 * ```
 * val factory = MyReplicatorFactory().
 * WorkManager.getInstance(context).enqueue(
 *    factory.periodicWorkRequestBuilder(15L, TimeUnit.MINUTES)
 *         .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1L, TimeUnit.HOURS)
 *         .setConstraints(
 *             Constraints.Builder()
 *                 .setRequiresBatteryNotLow(true)
 *                 .setRequiredNetworkType(NetworkType.CONNECTED)
 *                 .setRequiresStorageNotLow(true)
 *                 .build()
 *         )
 *         .build())
 * ```
 * The only novelty here is that the client code obtains an instance of a `WorkRequest.Builder`
 * using one of the factory methods `periodicWorkRequestBuilder()` or `oneShotWorkRequestBuilder()`
 * defined on a client-created subclass of `WorkManagerReplicatorFactory`, `MyReplicatorFactory`
 * in the code above. These methods the are analogs of the standard `PeriodicWorkRequestBuilder()`
 * and `OneShotWorkRequestBuilder()` extension functions and return the appropriate `WorkRequest.Builder`
 * that can be configured, built and scheduled, as any other WorkManager request.
 *
 * Remember, now, that the work manager may start an entirely new instance of the client
 * application, every time it runs a new replication! None of the state from any previous
 * instance of the application -- fields, objects, any of it -- may be available.
 * The `ReplicatorWorker`, on the other hand, needs a properly constructed `ReplicatorConfiguration`
 * in order to create and run a new replicator. This is why the client code creates a subclass of
 * `WorkManagerReplicatorFactory`. When the factory method from the subclass of `WorkManagerReplicatorFactory`
 * (`periodicWorkRequestBuilder()` or `oneShotWorkRequestBuilder()`) supplied the `WorkRequest.Builder`,
 * it used the value of the factory defined property `tag`, to store the FQN of the factory class,
 * in the work request. The `ReplicatorWorker` recovers this class name and creates a new instance
 * using reflection. The instance is responsible for creating a properly constructed
 * `WorkManagerReplicatorConfiguration` from scratch and supplying it to the `ReplicatorWorker`.
 *
 * A `WorkManagerReplicatorConfiguration` is very similar to a `ReplicatorConfiguration`, except that it
 * hides properties that should be configured in the `WorkManager` request: `continuous`, `heartbeat`,
 * `maxAttempts`, and `maxAttemptWaitTime`.
 *
 * An application can observe the status of a work manager replication by subscribing to its `LiveData`.
 * Assuming that replications processes are 1-1 with the factory defined tag (an assumption pervasive
 * in this implementation) the client code can obtain a `LiveData` object that provides `ReplicatorStatus`
 * updates for the replicator like this:
 * ```
 * val replId = MyReplicatorFactory().tag
 * val liveData = WorkManager.getInstance(context)
 *     .getWorkInfosByTagLiveData(replId)
 *     .map {
 *         if (it.isEmpty()) {
 *             null
 *         } else {
 *             it[0].progress.toReplicatorStatus(replId)
 *         }
 *     }
 * return liveData
 * ```
 * The application terminates a repeating WorkManager replication, normally, by cancelling all of
 * the jobs for the identifying tag:
 * ```
 * WorkManager.getInstance(context).cancelAllWorkByTag(factory.tag)
 * ```
 */
class ReplicatorWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        var repl: Replicator? = null
        var tag: String? = null
        return try {
            val workerClass = inputData.getString(KEY_REPLICATOR)
            Log.i(LogDomain.REPLICATOR, "Background replicator ${workerClass} started")

            val configFactory = getFactory(workerClass)

            tag = configFactory.tag
            Log.i(LogDomain.REPLICATOR, "Background replicator ${workerClass} has tag: ${tag}")

            val config = configFactory.getConfig()?.getConfig()
                ?: throw IllegalArgumentException("Could not get configuration from factory")

            // enforce single-shot and no retries
            config.isContinuous = false
            config.heartbeat = AbstractReplicatorConfiguration.DISABLE_HEARTBEAT
            config.maxAttempts = 1
            config.maxAttemptWaitTime = 1

            repl = Replicator(config)
            val flow = repl.replicatorChangesFlow()

            var ok = true
            // ??? If this replication runs for a very long time (more than 10 minutes)
            // or if the scheduling conditions are no longer met, the system will force stop it.
            runBlocking {
                repl.start()
                Log.i(LogDomain.REPLICATOR, "Background replicator ${tag} started")
                flow.onEach { change ->
                    val status = change.status
                    Log.i(LogDomain.REPLICATOR, "Background replicator ${tag} changed state: ${status}")

                    ok = ok && status.error == null
                    setProgressAsync(buildData(tag, status))
                }.takeWhile { change ->
                    !stopStates.contains(change.status.activityLevel)
                }.collect { }
            }
            Log.i(LogDomain.REPLICATOR, "Background replicator ${tag} finished ${ok}")

            if (ok) Result.success() else Result.failure()
        } catch (e: Throwable) {
            setProgressAsync(buildData(tag, e))
            Log.w(LogDomain.REPLICATOR, "Background replicator ${tag} failed", e)
            Result.failure()
        } finally {
            repl?.stop()
        }
    }

    private fun getFactory(factoryClass: String?): WorkManagerReplicatorFactory {
        factoryClass ?: throw IllegalArgumentException("Factory class name is null")
        try {
            return (Class.forName(factoryClass).getDeclaredConstructor().newInstance() as WorkManagerReplicatorFactory)
        } catch (e: Exception) {
            throw IllegalStateException("Failed creating factory ${factoryClass}", e)
        }
    }

    private fun buildData(tag: String, status: ReplicatorStatus): Data {
        val dataBuilder = Data.Builder()
            .putString(KEY_REPLICATOR, tag)
            .putString(KEY_REPLICATION_ACTIVITY_LEVEL, status.activityLevel.toString())

        status.progress.let {
            dataBuilder.putLong(KEY_REPLICATION_COMPLETED, it.completed)
                .putLong(KEY_REPLICATION_TOTAL, it.total)
        }

        status.error?.let {
            dataBuilder.putInt(KEY_REPLICATION_ERROR_CODE, it.code)
                .putString(KEY_REPLICATION_ERROR_MESSAGE, it.message)
        }

        return dataBuilder.build()
    }

    private fun buildData(tag: String?, err: Throwable): Data {
        var e: Throwable? = err
        while (e != null) {
            if (e is CouchbaseLiteException) break
            else e = e.cause
        }

        var code = CBLError.Code.UNEXPECTED_ERROR
        val msg: String?
        if (e !is CouchbaseLiteException) {
            msg = err.message
        } else {
            msg = e.message
            code = e.code
        }

        return Data.Builder()
            .putString(KEY_REPLICATOR, tag ?: "unknown")
            .putString(KEY_REPLICATION_ACTIVITY_LEVEL, ReplicatorActivityLevel.STOPPED.toString())
            .putInt(KEY_REPLICATION_ERROR_CODE, code)
            .putString(KEY_REPLICATION_ERROR_MESSAGE, if (msg.isNullOrEmpty()) "error" else msg)
            .build()
    }

    companion object {
        const val KEY_REPLICATOR = "com.couchbase.mobile.android.replicator.ID"
        const val KEY_REPLICATION_ACTIVITY_LEVEL = "com.couchbase.mobile.android.replicator.ACTIVITY_LEVEL"
        const val KEY_REPLICATION_COMPLETED = "com.couchbase.mobile.android.replicator.progress.COMPLETED"
        const val KEY_REPLICATION_TOTAL = "com.couchbase.mobile.android.replicator.progress.TOTAL"
        const val KEY_REPLICATION_ERROR_CODE = "com.couchbase.mobile.android.replicator.error.CODE"
        const val KEY_REPLICATION_ERROR_MESSAGE = "com.couchbase.mobile.android.replicator.error.MESSAGE"

        val stopStates = listOf(
            ReplicatorActivityLevel.OFFLINE,
            ReplicatorActivityLevel.IDLE,
            ReplicatorActivityLevel.STOPPED
        )
        val activityLevelFromString = mapOf(
            ReplicatorActivityLevel.STOPPED.toString() to ReplicatorActivityLevel.STOPPED,
            ReplicatorActivityLevel.OFFLINE.toString() to ReplicatorActivityLevel.OFFLINE,
            ReplicatorActivityLevel.CONNECTING.toString() to ReplicatorActivityLevel.CONNECTING,
            ReplicatorActivityLevel.IDLE.toString() to ReplicatorActivityLevel.IDLE,
            ReplicatorActivityLevel.BUSY.toString() to ReplicatorActivityLevel.BUSY
        )
    }
}
