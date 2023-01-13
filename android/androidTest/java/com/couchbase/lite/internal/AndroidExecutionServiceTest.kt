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

import android.os.Looper
import com.couchbase.lite.BaseTest
import com.couchbase.lite.internal.exec.CBLExecutor
import org.junit.Assert
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class AndroidExecutionServiceTest : BaseTest() {

    // Verify that the default executor uses the UI thread.
    @Test
    fun testDefaultThreadExecutor() {
        val exeSvc = getExecutionService(CBLExecutor("Test worker"))
        val latch = CountDownLatch(1)

        val thread1 = AtomicReference<Thread>()
        exeSvc.defaultExecutor.execute {
            thread1.set(Thread.currentThread())
            latch.countDown()
        }

        latch.await(BaseTest.STD_TIMEOUT_SEC, TimeUnit.SECONDS)

        Assert.assertEquals(Looper.getMainLooper().thread, thread1.get())
    }
}
