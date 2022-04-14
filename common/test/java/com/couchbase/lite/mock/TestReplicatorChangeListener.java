//
// Copyright (c) 2020 Couchbase, Inc.
// Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
// except in compliance with the License. You may obtain a copy of the License at
// http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software distributed under the
// License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions
// and limitations under the License.
//
package com.couchbase.lite.mock;

import androidx.annotation.NonNull;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.ReplicatorActivityLevel;
import com.couchbase.lite.ReplicatorChange;
import com.couchbase.lite.ReplicatorChangeListener;
import com.couchbase.lite.ReplicatorStatus;
import com.couchbase.lite.internal.utils.Report;


public class TestReplicatorChangeListener implements ReplicatorChangeListener {
    private final AtomicReference<Throwable> observedError = new AtomicReference<>();
    private final CountDownLatch latch = new CountDownLatch(1);

    public boolean awaitCompletion(long timeout, TimeUnit unit) {
        try { return latch.await(timeout, unit); }
        catch (InterruptedException ignore) { }
        return false;
    }

    @Override
    public void changed(@NonNull ReplicatorChange change) {
        final ReplicatorStatus status = change.getStatus();
        final CouchbaseLiteException error = status.getError();
        final ReplicatorActivityLevel state = status.getActivityLevel();
        Report.log(error, "Test replicator state change: " + state);

        observedError.compareAndSet(null, error);

        switch (state) {
            case OFFLINE:
            case STOPPED:
            case IDLE:
                latch.countDown();
                break;
            default:
        }
    }

    public Throwable getError() { return observedError.get(); }
}

