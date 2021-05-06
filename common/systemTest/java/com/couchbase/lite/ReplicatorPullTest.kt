//
// Copyright (c) 2020 Couchbase, Inc All rights reserved.
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

import android.util.Log
import com.couchbase.lite.utils.FileUtils
import com.couchbase.lite.utils.SlowTest
import org.junit.Test
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors


const val KEY_ORD = "ord"
const val KEY_NAME = "name"
const val KEY_UPDATED = "updatedAt"
const val N_DOCS = 2000

// This test sometimes drove the bug described in CBSE-8145
class ReplicatorPullTest : BaseTest() {
    @SlowTest
    @Test
    fun testStopDuringPull() {
        // Executors
        val docExecutor = Executors.newSingleThreadExecutor()
        val stateExecutor = Executors.newSingleThreadExecutor()

        // Clean up
        val dbConfig = DatabaseConfiguration()
        FileUtils.deleteContents(dbConfig.directory)

        // Load up the target db (this will be a pull)
        val targetDb = Database("targetDb", dbConfig)
        for (i in 0..2000) {
            val doc = MutableDocument("doc-${i}")
                .setInt(KEY_ORD, i)
                .setString(KEY_NAME, "${i}-boop-${i}")
                .setDate(KEY_UPDATED, Date())
            targetDb.save(doc)
        }

        // Create the source db
        val srcDb = Database("sourceDb", dbConfig)

        // replication filter filters to only even docs
        val filter = ReplicationFilter { doc, _ ->
            val good = doc.getInt("ord") % 2 == 0
            Log.d("###", "DOC FILTER ${doc.id}: ${good}")
            Thread.sleep(500)
            return@ReplicationFilter good
        }

        // Create the replicator
        val repl = Replicator(
            ReplicatorConfiguration(srcDb, DatabaseEndpoint(targetDb))
                .setReplicatorType(AbstractReplicatorConfiguration.ReplicatorType.PULL)
                .setContinuous(false)
                .setPullFilter(filter)
        )

        // Document watcher will stop the replicator after 500 docs
        var n = 0
        val stopLatch = CountDownLatch(1)
        val docToken = repl.addDocumentReplicationListener(
            docExecutor,
            DocumentReplicationListener { change ->
                n += change.documents.size
                Log.d("###", "DOC CHANGE @${n}")
                if ((n > 500) and (stopLatch.count > 0)) {
                    Log.d("###", "DOC CHANGE: STOPPING")
                    stopLatch.countDown()
                }
            })

        // Change watcher will finish the test after the replicator stops
        val stoppedLatch = CountDownLatch(1)
        val changeToken = repl.addChangeListener(
            stateExecutor,
            ReplicatorChangeListener { change ->
                val state = change.status.activityLevel
                Log.d("###", "STATE CHANGE: ${state}")
                when (state) {
                    ReplicatorActivityLevel.STOPPED -> stoppedLatch.countDown()
                    else -> Unit
                }
            })

        // run the replicator, stop it after 500 docs and wait for it to stop
        try {
            Log.d("###", "REPL START")
            repl.start()
            Log.d("###", "REPL STARTED")
            stopLatch.await()
            Log.d("###", "REPL STOPPING")
            repl.stop()
            stoppedLatch.await()
        } finally {
            Log.d("###", "REPL STOPPED")
            // give it a few seconds to fail
            Thread.sleep(10 * 1000)
            Log.d("###", "REPL CLEANUP")
            repl.removeChangeListener(docToken)
            repl.removeChangeListener(changeToken)
        }
    }
}
