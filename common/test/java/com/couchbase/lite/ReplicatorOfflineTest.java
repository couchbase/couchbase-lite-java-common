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
package com.couchbase.lite;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


@SuppressWarnings("ConstantConditions")
public class ReplicatorOfflineTest extends BaseReplicatorTest {

    @Test
    public void testStopReplicatorAfterOffline() throws InterruptedException {
        // this test crashes the test suite on Android <21
        skipTestWhen("android<21");

        final CountDownLatch offline = new CountDownLatch(1);
        final CountDownLatch stopped = new CountDownLatch(1);

        Replicator repl = makeRepl(makeConfig().setType(ReplicatorType.PULL));
        ListenerToken token = repl.addChangeListener(
            testSerialExecutor,
            change -> {
                switch(change.getStatus().getActivityLevel()) {
                    case STOPPED:
                        stopped.countDown();
                        break;
                    case OFFLINE:
                        offline.countDown();
                        break;
                    default:
                }
            });

        try {
            repl.start();
            assertTrue(offline.await(LONG_TIMEOUT_SEC, TimeUnit.SECONDS));
            repl.stop();
            assertTrue(stopped.await(LONG_TIMEOUT_SEC, TimeUnit.SECONDS));
        }
        finally { token.remove(); }
    }

    @Test
    public void testStartSingleShotReplicatorInOffline() throws InterruptedException {
        // this test crashes the test suite on Android <21
        skipTestWhen("android<21");

        final CountDownLatch stopped = new CountDownLatch(1);
        Replicator repl = makeRepl(makeConfig().setContinuous(false));
        ListenerToken token = repl.addChangeListener(
            testSerialExecutor,
            change -> {
                if (change.getStatus().getActivityLevel() == ReplicatorActivityLevel.STOPPED) { stopped.countDown(); }
            });

        try {
            repl.start();
            assertTrue(stopped.await(LONG_TIMEOUT_SEC, TimeUnit.SECONDS));
        }
        finally { token.remove(); }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddNullDocumentReplicationListener() {
        Replicator repl = makeRepl();

        ListenerToken token = repl.addDocumentReplicationListener(replication -> { });
        assertNotNull(token);
        token.remove();

        repl.addDocumentReplicationListener(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddNullDocumentReplicationListenerWithExecutor() {
        Replicator repl = makeRepl();

        ListenerToken token = repl.addDocumentReplicationListener(replication -> { });
        assertNotNull(token);
        token.remove();

        repl.addDocumentReplicationListener(testSerialExecutor, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddNullChangeListener() {
        ListenerToken token = null;
        try { token = makeRepl().addChangeListener(null); }
        finally {
            if (token != null) { token.remove(); }
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullChangeListenerWithExecutor() {
        ListenerToken token = null;
        try { token = makeRepl().addChangeListener(testSerialExecutor, null); }
        finally {
            if (token != null) { token.remove(); }
        }
    }

    private ReplicatorConfiguration makeDefaultConfig() {
        return new ReplicatorConfiguration(getMockURLEndpoint())
            .addCollection(getTestCollection(), null);
    }

    private ReplicatorConfiguration makeConfig() {
        return makeDefaultConfig()
            .setType(ReplicatorType.PUSH)
            .setContinuous(true)
            .setHeartbeat(AbstractReplicatorConfiguration.DISABLE_HEARTBEAT);
    }

    // Kotlin shim functions

    private Replicator makeRepl() { return makeRepl(makeConfig()); }

    private Replicator makeRepl(ReplicatorConfiguration config) { return testReplicator(config); }
}
