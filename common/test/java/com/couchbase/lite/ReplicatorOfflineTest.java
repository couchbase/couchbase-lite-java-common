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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import static com.couchbase.lite.internal.utils.TestUtils.assertThrows;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


public class ReplicatorOfflineTest extends BaseReplicatorTest {

    @Test
    public void testEditReadOnlyConfiguration() throws Exception {
        Endpoint endpoint = getRemoteTargetEndpoint();
        ReplicatorConfiguration config = makeConfig(true, false, true, endpoint);
        config.setContinuous(false);
        baseTestReplicator = new Replicator(config);

        assertThrows(IllegalStateException.class, () -> baseTestReplicator.getConfig().setContinuous(true));
    }

    @Test
    public void testStopReplicatorAfterOffline() throws URISyntaxException, InterruptedException {
        // this test crashes the test suite on Android <21
        failImmediatelyForPlatform("testStopReplicatorAfterOffline");

        Endpoint target = getRemoteTargetEndpoint();
        ReplicatorConfiguration config = makeConfig(false, true, true, baseTestDb, target);
        Replicator repl = new Replicator(config);
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
        failImmediatelyForPlatform("testStartSingleShotReplicatorInOffline");

        Endpoint endpoint = getRemoteTargetEndpoint();
        Replicator repl = new Replicator(makeConfig(true, false, false, endpoint));
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

    @SuppressWarnings("ConstantConditions")
    @Test
    public void testDocumentChangeListenerToken() throws Exception {
        Endpoint endpoint = getRemoteTargetEndpoint();
        Replicator repl = new Replicator(makeConfig(true, false, false, endpoint));
        ListenerToken token = repl.addDocumentReplicationListener(replication -> { });
        assertNotNull(token);

        assertThrows(IllegalArgumentException.class, () -> repl.addDocumentReplicationListener(null));

        assertThrows(
            IllegalArgumentException.class,
            () -> repl.addDocumentReplicationListener(testSerialExecutor, null));
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void testChangeListenerEmptyArg() throws Exception {
        Endpoint endpoint = getRemoteTargetEndpoint();
        Replicator repl = new Replicator(makeConfig(true, false, true, endpoint));

        assertThrows(IllegalArgumentException.class, () -> repl.addChangeListener(null));

        assertThrows(IllegalArgumentException.class, () -> repl.addChangeListener(testSerialExecutor, null));
    }

    private URLEndpoint getRemoteTargetEndpoint() throws URISyntaxException {
        return new URLEndpoint(new URI("ws://foo.couchbase.com/db"));
    }
}
