//
// BaseReplicatorTest.java
//
// Copyright (c) 2017 Couchbase, Inc All rights reserved.
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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;

import com.couchbase.lite.utils.Fn;

import static com.couchbase.lite.AbstractReplicatorConfiguration.ReplicatorType.PULL;
import static com.couchbase.lite.AbstractReplicatorConfiguration.ReplicatorType.PUSH;
import static com.couchbase.lite.AbstractReplicatorConfiguration.ReplicatorType.PUSH_AND_PULL;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


public abstract class BaseReplicatorTest extends BaseDbTest {
    protected static final int STD_TIMEOUT_SECS = 5;

    protected Replicator baseTestReplicator;

    protected Database otherDB;

    @Before
    public final void setUpBaseReplicatorTest() throws CouchbaseLiteException {
        otherDB = createDb("replicator-db");
        assertNotNull(otherDB);
        assertTrue(otherDB.isOpen());
    }

    @After
    public final void tearDownBaseReplicatorTest() { deleteDb(otherDB); }

    // helper method allows kotlin to call isDocumentPending(null)
    // Kotlin type checking prevents this.
    @SuppressWarnings("ConstantConditions")
    protected final boolean callIsDocumentPendingWithNullId(Replicator repl) throws CouchbaseLiteException {
        return repl.isDocumentPending(null);
    }

    protected final ReplicatorConfiguration makeConfig(
        boolean push,
        boolean pull,
        boolean continuous,
        Endpoint target) {
        return makeConfig(push, pull, continuous, baseTestDb, target);
    }

    protected final ReplicatorConfiguration makeConfig(
        boolean push,
        boolean pull,
        boolean continuous,
        Database source,
        Endpoint target) {
        return makeConfig(push, pull, continuous, source, target, null);
    }

    protected final ReplicatorConfiguration makeConfig(
        boolean push,
        boolean pull,
        boolean continuous,
        Database source,
        Endpoint target,
        ConflictResolver resolver) {
        ReplicatorConfiguration config = new ReplicatorConfiguration(source, target);
        config.setReplicatorType(push && pull ? PUSH_AND_PULL : (push ? PUSH : PULL));
        config.setContinuous(continuous);
        if (resolver != null) { config.setConflictResolver(resolver); }
        return config;
    }

    protected final Replicator run(ReplicatorConfiguration config) { return run(config, null); }

    protected final Replicator run(ReplicatorConfiguration config, Fn.Consumer<Replicator> onReady) {
        return run(config, 0, null, false, false, onReady);
    }

    protected final Replicator run(
        ReplicatorConfiguration config,
        int expectedErrorCode,
        String expectedErrorDomain,
        boolean ignoreErrorAtStopped,
        boolean reset,
        Fn.Consumer<Replicator> onReady) {
        return run(
            new Replicator(config),
            expectedErrorCode,
            expectedErrorDomain,
            ignoreErrorAtStopped,
            reset,
            onReady);
    }

    protected final Replicator run(Replicator r) { return run(r, 0, null, false, false, null); }

    protected final Replicator run(
        Replicator r,
        int expectedErrorCode,
        String expectedErrorDomain,
        boolean ignoreErrorAtStopped,
        boolean reset,
        Fn.Consumer<Replicator> onReady) {
        baseTestReplicator = r;

        TestReplicatorChangeListener listener
            = new TestReplicatorChangeListener(r, expectedErrorDomain, expectedErrorCode, ignoreErrorAtStopped);

        if (onReady != null) { onReady.accept(r); }

        boolean success;

        ListenerToken token = r.addChangeListener(testSerialExecutor, listener);
        try {
            r.start(reset);
            success = listener.awaitCompletion(STD_TIMEOUT_SECS, TimeUnit.SECONDS);
        }
        finally {
            r.removeChangeListener(token);
        }

        // see if the replication succeeded
        Throwable err = listener.getFailureReason();
        if (err != null) { throw new RuntimeException(err); }

        assertTrue(success);

        return r;
    }

    protected final void stopContinuousReplicator(Replicator repl) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        ListenerToken token = repl.addChangeListener(
            testSerialExecutor,
            change -> {
                if (change.getStatus().getActivityLevel() == Replicator.ActivityLevel.STOPPED) { latch.countDown(); }
            });

        try {
            repl.stop();
            if (repl.getStatus().getActivityLevel() != Replicator.ActivityLevel.STOPPED) {
                assertTrue(latch.await(STD_TIMEOUT_SECS, TimeUnit.SECONDS));
            }
        }
        finally {
            repl.removeChangeListener(token);
        }
    }

}
