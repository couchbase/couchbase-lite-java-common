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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.core.C4Constants;
import com.couchbase.lite.internal.core.C4ReplicatorStatus;
import com.couchbase.lite.internal.replicator.AbstractCBLWebSocket;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


public class ReplicatorMiscTest extends BaseReplicatorTest {

    @Test
    public void testGetExecutor() {
        final Executor executor = runnable -> { };
        final ReplicatorChangeListener listener = change -> { };

        // custom Executor
        ReplicatorChangeListenerToken token = new ReplicatorChangeListenerToken(executor, listener);
        assertEquals(executor, token.getExecutor());

        // UI thread Executor
        token = new ReplicatorChangeListenerToken(null, listener);
        assertEquals(CouchbaseLiteInternal.getExecutionService().getMainExecutor(), token.getExecutor());
    }

    @Test(expected = IllegalStateException.class)
    public void testEditReadOnlyConfiguration() throws Exception {
        baseTestReplicator = testReplicator(makeConfig(
            getRemoteTargetEndpoint(),
            AbstractReplicatorConfiguration.ReplicatorType.PUSH, true)
            .setContinuous(false));

        baseTestReplicator.getConfig().setContinuous(true);
    }

    @Test
    public void testReplicatorChange() {
        long completed = 10;
        long total = 20;
        int errorCode = CBLError.Code.BUSY;
        int errorDomain = 1; // CBLError.Domain.CBLErrorDomain: LiteCoreDomain
        C4ReplicatorStatus c4ReplicatorStatus = new C4ReplicatorStatus(
            C4ReplicatorStatus.ActivityLevel.CONNECTING,
            completed,
            total,
            1,
            errorDomain,
            errorCode,
            0
        );
        Replicator.Status status = new Replicator.Status(c4ReplicatorStatus);
        ReplicatorChange repChange = new ReplicatorChange(baseTestReplicator, status);

        assertEquals(repChange.getReplicator(), baseTestReplicator);
        assertEquals(repChange.getStatus(), status);
        assertEquals(repChange.getStatus().getActivityLevel(), status.getActivityLevel());
        assertEquals(repChange.getStatus().getProgress().getCompleted(), completed);
        assertEquals(repChange.getStatus().getProgress().getTotal(), total);
        assertEquals(repChange.getStatus().getError().getCode(), errorCode);
        assertEquals(repChange.getStatus().getError().getDomain(), CBLError.Domain.CBLITE);
        String changeInString = "ReplicatorChange{replicator=" + baseTestReplicator + ", status=" + status + '}';
        assertEquals(repChange.toString(), changeInString);
    }

    @Test
    public void testDocumentReplication() {
        final boolean isPush = true;
        List<ReplicatedDocument> docs = new ArrayList<>();
        DocumentReplication doc = new DocumentReplication(baseTestReplicator, isPush, docs);
        assertEquals(doc.isPush(), isPush);
        assertEquals(doc.getReplicator(), baseTestReplicator);
        assertEquals(doc.getDocuments(), docs);
    }

    // https://issues.couchbase.com/browse/CBL-89
    // Thanks to @James Flather for the ready-made test code
    @Test
    public void testStopBeforeStart() throws URISyntaxException {
        testReplicator(makeConfig(
            getRemoteTargetEndpoint(),
            AbstractReplicatorConfiguration.ReplicatorType.PUSH, false))
            .stop();
    }

    // https://issues.couchbase.com/browse/CBL-88
    // Thanks to @James Flather for the ready-made test code
    @Test
    public void testStatusBeforeStart() throws URISyntaxException {
        testReplicator(makeConfig(
            getRemoteTargetEndpoint(),
            AbstractReplicatorConfiguration.ReplicatorType.PUSH, false))
            .getStatus();
    }

    @Test
    public void testGetHeartbeatBeforeSet() throws URISyntaxException {
        final ReplicatorConfiguration config = new ReplicatorConfiguration(baseTestDb, getRemoteTargetEndpoint());
        assertEquals(AbstractCBLWebSocket.DEFAULT_HEARTBEAT_SEC, config.getHeartbeat());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetIllegalHeartbeat() throws URISyntaxException {
        final ReplicatorConfiguration config = new ReplicatorConfiguration(baseTestDb, getRemoteTargetEndpoint())
            .setHeartbeat(-47);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetZeroHeartbeat() throws URISyntaxException {
        final ReplicatorConfiguration config = new ReplicatorConfiguration(baseTestDb, getRemoteTargetEndpoint())
            .setHeartbeat(0);
    }

    @Test
    public void testDefaultHeartbeat() throws URISyntaxException {
        // Don't use makeConfig: it sets heartbeat to 0
        final ReplicatorConfiguration config = new ReplicatorConfiguration(baseTestDb, getRemoteTargetEndpoint())
            .setReplicatorType(AbstractReplicatorConfiguration.ReplicatorType.PUSH)
            .setContinuous(false);

        final Replicator repl = testReplicator(config);

        final AtomicInteger heartbeat = new AtomicInteger();
        repl.getSocketFactory().setListener(sf ->
            heartbeat.compareAndSet(0, ((AbstractCBLWebSocket) sf).getOkHttpSocketFactory().pingIntervalMillis()));

        try { run(repl); }
        catch (CouchbaseLiteException ignore) { }

        assertEquals(AbstractCBLWebSocket.DEFAULT_HEARTBEAT_SEC * 1000, heartbeat.get());
    }

    @Test
    public void testCustomHeartbeat() throws URISyntaxException {
        final ReplicatorConfiguration config = makeConfig(
            getRemoteTargetEndpoint(),
            AbstractReplicatorConfiguration.ReplicatorType.PUSH, false)
            .setHeartbeat(67L);

        Replicator repl = testReplicator(config);

        final AtomicInteger heartbeat = new AtomicInteger();
        repl.getSocketFactory().setListener(sf ->
            heartbeat.compareAndSet(0, ((AbstractCBLWebSocket) sf).getOkHttpSocketFactory().pingIntervalMillis()));

        try { run(repl); }
        catch (CouchbaseLiteException ignore) { }

        assertEquals(config.getHeartbeat() * 1000, heartbeat.get());
    }

    @Test
    public void testStopWhileConnecting() throws URISyntaxException {
        final ReplicatorConfiguration config = makeConfig(
            getRemoteTargetEndpoint(),
            AbstractReplicatorConfiguration.ReplicatorType.PUSH,
            false);
        Replicator repl = testReplicator(config);

        final CountDownLatch latch = new CountDownLatch(1);
        repl.addChangeListener(status -> {
            if (status.getStatus().getActivityLevel() == AbstractReplicator.ActivityLevel.CONNECTING) {
                repl.stop();
                latch.countDown();
            }
        });

        repl.start();
        try {
            try { latch.await(STD_TIMEOUT_SECS, TimeUnit.SECONDS); }
            catch (InterruptedException ignore) { }
        }
        finally {
            repl.stop();
        }
    }

    @Test
    public void testReplicatedDocument() {
        String docID = "someDocumentID";
        int flags = C4Constants.DocumentFlags.DELETED;
        CouchbaseLiteException error = new CouchbaseLiteException(
            "Replicator busy",
            CBLError.Domain.CBLITE,
            CBLError.Code.BUSY);
        ReplicatedDocument doc = new ReplicatedDocument(docID, flags, error, true);

        assertEquals(doc.getID(), docID);
        assertTrue(doc.flags().contains(DocumentFlag.DocumentFlagsDeleted));
        assertEquals(doc.getError().getDomain(), CBLError.Domain.CBLITE);
        assertEquals(doc.getError().getCode(), CBLError.Code.BUSY);
    }

    // CBL-1218
    @Test(expected = IllegalStateException.class)
    public void testStartReplicatorWithClosedDb() throws URISyntaxException {
        Replicator replicator = testReplicator(makeConfig(
            baseTestDb,
            getRemoteTargetEndpoint(),
            AbstractReplicatorConfiguration.ReplicatorType.PUSH_AND_PULL,
            false,
            null,
            null));

        closeDb(baseTestDb);

        replicator.start(false);
    }

    // CBL-1218
    @Test(expected = IllegalStateException.class)
    public void testIsDocumentPendingWithClosedDb() throws CouchbaseLiteException, URISyntaxException {
        Replicator replicator = testReplicator(makeConfig(
            baseTestDb,
            getRemoteTargetEndpoint(),
            AbstractReplicatorConfiguration.ReplicatorType.PUSH_AND_PULL,
            false,
            null,
            null));

        closeDb(baseTestDb);

        replicator.getPendingDocumentIds();
    }

    // CBL-1218
    @Test(expected = IllegalStateException.class)
    public void testGetPendingDocIdsWithClosedDb() throws CouchbaseLiteException, URISyntaxException {
        MutableDocument doc = new MutableDocument();
        otherDB.save(doc);

        Replicator replicator = testReplicator(
            makeConfig(
                baseTestDb,
                getRemoteTargetEndpoint(),
                AbstractReplicatorConfiguration.ReplicatorType.PUSH_AND_PULL,
                false,
                null,
                null));

        closeDb(baseTestDb);

        replicator.isDocumentPending(doc.getId());
    }

    // CBL-1441
    @Test
    public void testReplicatorStatus() {
        assertEquals(
            AbstractReplicator.ActivityLevel.BUSY,
            getActivityLevelFor(C4ReplicatorStatus.ActivityLevel.STOPPED - 1));
        assertEquals(
            AbstractReplicator.ActivityLevel.STOPPED,
            getActivityLevelFor(C4ReplicatorStatus.ActivityLevel.STOPPED));
        assertEquals(
            AbstractReplicator.ActivityLevel.OFFLINE,
            getActivityLevelFor(C4ReplicatorStatus.ActivityLevel.OFFLINE));
        assertEquals(
            AbstractReplicator.ActivityLevel.CONNECTING,
            getActivityLevelFor(C4ReplicatorStatus.ActivityLevel.CONNECTING));
        assertEquals(
            AbstractReplicator.ActivityLevel.IDLE,
            getActivityLevelFor(C4ReplicatorStatus.ActivityLevel.IDLE));
        assertEquals(
            AbstractReplicator.ActivityLevel.BUSY,
            getActivityLevelFor(C4ReplicatorStatus.ActivityLevel.BUSY));
        assertEquals(
            AbstractReplicator.ActivityLevel.BUSY,
            getActivityLevelFor(C4ReplicatorStatus.ActivityLevel.BUSY + 1));
    }

    private AbstractReplicator.ActivityLevel getActivityLevelFor(int activityLevel) {
        return new AbstractReplicator.Status(new C4ReplicatorStatus(activityLevel, 0, 0, 0, 0, 0, 0))
            .getActivityLevel();
    }
}
