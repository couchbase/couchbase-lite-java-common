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

import java.net.URISyntaxException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


public class ReplicatorOfflineTest extends BaseReplicatorTest {

    @Test
    public void testStopReplicatorAfterOffline() throws URISyntaxException, InterruptedException {
        // this test crashes the test suite on Android <21
        if (handlePlatformSpecially("android<21")) {
            fail("Websockets not supported on Android v < 21");
        }

        Endpoint target = getRemoteTargetEndpoint();
        ReplicatorConfiguration config
            = makeConfig(baseTestDb, target, AbstractReplicatorConfiguration.ReplicatorType.PULL, true);
        Replicator repl = testReplicator(config);
        final CountDownLatch offline = new CountDownLatch(1);
        final CountDownLatch stopped = new CountDownLatch(1);
        ListenerToken token = repl.addChangeListener(
            testSerialExecutor,
            change -> {
                Replicator.Status status = change.getStatus();
                if (status.getActivityLevel() == Replicator.ActivityLevel.OFFLINE) {
                    change.getReplicator().stop();
                    offline.countDown();
                }
                if (status.getActivityLevel() == Replicator.ActivityLevel.STOPPED) { stopped.countDown(); }
            });
        repl.start(false);
        assertTrue(offline.await(10, TimeUnit.SECONDS));
        assertTrue(stopped.await(10, TimeUnit.SECONDS));
        repl.removeChangeListener(token);
    }

    @Test
    public void testStartSingleShotReplicatorInOffline() throws URISyntaxException, InterruptedException {
        // this test crashes the test suite on Android <21
        if (handlePlatformSpecially("android<21")) { fail("Websockets not supported on Android v < 21"); }

        Endpoint endpoint = getRemoteTargetEndpoint();
        Replicator repl = testReplicator(
            makeConfig(endpoint, AbstractReplicatorConfiguration.ReplicatorType.PUSH, false));
        final CountDownLatch stopped = new CountDownLatch(1);
        ListenerToken token = repl.addChangeListener(
            testSerialExecutor,
            change -> {
                Replicator.Status status = change.getStatus();
                if (status.getActivityLevel() == Replicator.ActivityLevel.STOPPED) { stopped.countDown(); }
            });
        repl.start(false);
        assertTrue(stopped.await(10, TimeUnit.SECONDS));
        repl.removeChangeListener(token);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddNullDocumentReplicationListener() throws URISyntaxException {
        Replicator repl = testReplicator(makeConfig(
            getRemoteTargetEndpoint(),
            AbstractReplicatorConfiguration.ReplicatorType.PUSH,
            true));

        ListenerToken token = repl.addDocumentReplicationListener(replication -> { });
        assertNotNull(token);

        repl.addDocumentReplicationListener(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddNullDocumentReplicationListenerWithExecutor() throws URISyntaxException {
        Replicator repl = testReplicator(makeConfig(
            getRemoteTargetEndpoint(),
            AbstractReplicatorConfiguration.ReplicatorType.PUSH,
            true));

        ListenerToken token = repl.addDocumentReplicationListener(replication -> { });
        assertNotNull(token);

        repl.addDocumentReplicationListener(testSerialExecutor, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddNullChangeListener() throws Exception {
        testReplicator(makeConfig(
            getRemoteTargetEndpoint(),
            AbstractReplicatorConfiguration.ReplicatorType.PUSH,
            true))
            .addChangeListener(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullChangeListenerWithExecutor() throws Exception {
        testReplicator(makeConfig(
            getRemoteTargetEndpoint(),
            AbstractReplicatorConfiguration.ReplicatorType.PUSH,
            true))
            .addChangeListener(testSerialExecutor, null);
    }
}
