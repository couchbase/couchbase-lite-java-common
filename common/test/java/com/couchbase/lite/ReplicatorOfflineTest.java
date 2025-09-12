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

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;


@SuppressWarnings("ConstantConditions")
public class ReplicatorOfflineTest extends BaseReplicatorTest {

    // this test crashes the test suite on Android <21
    @Test
    public void testStopReplicatorAfterOffline() throws InterruptedException {
        final CountDownLatch offline = new CountDownLatch(1);
        final CountDownLatch stopped = new CountDownLatch(1);

        Replicator repl = makeRepl(makeConfig().setType(ReplicatorType.PULL));
        ListenerToken token = repl.addChangeListener(
            getTestSerialExecutor(),
            change -> {
                switch (change.getStatus().getActivityLevel()) {
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
            Assert.assertTrue(offline.await(LONG_TIMEOUT_SEC, TimeUnit.SECONDS));
            repl.stop();
            Assert.assertTrue(stopped.await(LONG_TIMEOUT_SEC, TimeUnit.SECONDS));
        }
        finally { token.remove(); }
    }

    // this test crashes the test suite on Android <21
    @Test
    public void testStartSingleShotReplicatorInOffline() throws InterruptedException {
        final CountDownLatch stopped = new CountDownLatch(1);
        Replicator repl = makeRepl(makeConfig().setContinuous(false));
        ListenerToken token = repl.addChangeListener(
            getTestSerialExecutor(),
            change -> {
                if (change.getStatus().getActivityLevel() == ReplicatorActivityLevel.STOPPED) { stopped.countDown(); }
            });

        try {
            repl.start();
            Assert.assertTrue(stopped.await(LONG_TIMEOUT_SEC, TimeUnit.SECONDS));
        }
        finally { token.remove(); }
    }

    @Test
    public void testAddNullDocumentReplicationListener() {
        Replicator repl = makeRepl();

        ListenerToken token = repl.addDocumentReplicationListener(replication -> { });
        Assert.assertNotNull(token);
        token.remove();

        Assert.assertThrows(IllegalArgumentException.class, () -> repl.addDocumentReplicationListener(null));
    }

    @Test
    public void testAddNullDocumentReplicationListenerWithExecutor() {
        Replicator repl = makeRepl();

        ListenerToken token = repl.addDocumentReplicationListener(replication -> { });
        Assert.assertNotNull(token);
        token.remove();

        Assert.assertThrows(
            IllegalArgumentException.class,
            () -> repl.addDocumentReplicationListener(getTestSerialExecutor(), null));
    }

    @Test
    public void testAddNullChangeListener() {
        Assert.assertThrows(
            IllegalArgumentException.class,
            () -> {
                try (ListenerToken token = makeRepl().addChangeListener(null)) { }
            });
    }

    @Test
    public void testNullChangeListenerWithExecutor() {
        Assert.assertThrows(
            IllegalArgumentException.class,
            () -> {
                try (ListenerToken token = makeRepl().addChangeListener(getTestSerialExecutor(), null)) { }
            });
    }

    private ReplicatorConfiguration makeDefaultConfig() {
        CollectionConfiguration collectionConfiguration = new CollectionConfiguration(getTestCollection());
        return new ReplicatorConfiguration(Set.of(collectionConfiguration), getMockURLEndpoint());
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
