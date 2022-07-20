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

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import org.junit.Ignore;
import org.junit.Test;

import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.ImmutableReplicatorConfiguration;
import com.couchbase.lite.internal.core.C4Constants;
import com.couchbase.lite.internal.core.C4Replicator;
import com.couchbase.lite.internal.core.C4ReplicatorStatus;
import com.couchbase.lite.internal.replicator.AbstractCBLWebSocket;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;


public class ReplicatorMiscTest extends BaseReplicatorTest {

    @Test
    public void testGetExecutor() {
        final Executor executor = runnable -> { };
        final ReplicatorChangeListener listener = change -> { };

        // custom Executor
        ReplicatorChangeListenerToken token = new ReplicatorChangeListenerToken(executor, listener, t -> { });
        assertEquals(executor, token.getExecutor());

        // UI thread Executor
        token = new ReplicatorChangeListenerToken(null, listener, t -> { });
        assertEquals(CouchbaseLiteInternal.getExecutionService().getDefaultExecutor(), token.getExecutor());
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
        ReplicatorStatus status = new ReplicatorStatus(c4ReplicatorStatus);
        ReplicatorChange repChange = new ReplicatorChange(baseTestReplicator, status);

        assertEquals(repChange.getReplicator(), baseTestReplicator);
        assertEquals(repChange.getStatus(), status);
        assertEquals(repChange.getStatus().getActivityLevel(), status.getActivityLevel());
        assertEquals(repChange.getStatus().getProgress().getCompleted(), completed);
        assertEquals(repChange.getStatus().getProgress().getTotal(), total);
        assertEquals(repChange.getStatus().getError().getCode(), errorCode);
        assertEquals(repChange.getStatus().getError().getDomain(), CBLError.Domain.CBLITE);
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
        testReplicator(makeConfig(getRemoteTargetEndpoint(), ReplicatorType.PUSH, false)).stop();
    }

    // https://issues.couchbase.com/browse/CBL-88
    // Thanks to @James Flather for the ready-made test code
    @Test
    public void testStatusBeforeStart() throws URISyntaxException {
        testReplicator(makeConfig(getRemoteTargetEndpoint(), ReplicatorType.PUSH, false)).getStatus();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIllegalMaxAttempts() throws URISyntaxException {
        new ReplicatorConfiguration(baseTestDb, getRemoteTargetEndpoint()).setMaxAttempts(-1);
    }

    @Test
    public void testMaxAttemptsZero() throws URISyntaxException {
        new ReplicatorConfiguration(baseTestDb, getRemoteTargetEndpoint()).setMaxAttempts(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIllegalAttemptsWaitTime() throws URISyntaxException {
        new ReplicatorConfiguration(baseTestDb, getRemoteTargetEndpoint()).setMaxAttemptWaitTime(-1);
    }

    @Test
    public void testMaxAttemptsWaitTimeZero() throws URISyntaxException {
        new ReplicatorConfiguration(baseTestDb, getRemoteTargetEndpoint()).setMaxAttemptWaitTime(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIllegalHeartbeatMin() throws URISyntaxException {
        new ReplicatorConfiguration(baseTestDb, getRemoteTargetEndpoint()).setHeartbeat(-1);
    }

    @Test
    public void testHeartbeatZero() throws URISyntaxException {
        new ReplicatorConfiguration(baseTestDb, getRemoteTargetEndpoint()).setHeartbeat(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIllegalHeartbeatMax() throws URISyntaxException {
        new ReplicatorConfiguration(baseTestDb, getRemoteTargetEndpoint()).setHeartbeat(2147484);
    }

    @Test
    public void testDocumentEndListenerTokenRemove() throws URISyntaxException {
        final Replicator repl = testReplicator(new ReplicatorConfiguration(baseTestDb, getRemoteTargetEndpoint()));
        assertEquals(0, repl.getDocEndListenerCount());
        ListenerToken token = repl.addDocumentReplicationListener(r -> { });
        assertEquals(1, repl.getDocEndListenerCount());
        token.remove();
        assertEquals(0, repl.getDocEndListenerCount());
        token.remove();
        assertEquals(0, repl.getDocEndListenerCount());
    }

    @Test
    public void tesReplicationListenerTokenRemove() throws URISyntaxException {
        final Replicator repl = testReplicator(new ReplicatorConfiguration(baseTestDb, getRemoteTargetEndpoint()));
        assertEquals(0, repl.getReplicatorListenerCount());
        ListenerToken token = repl.addChangeListener(r -> { });
        assertEquals(1, repl.getReplicatorListenerCount());
        token.remove();
        assertEquals(0, repl.getReplicatorListenerCount());
        token.remove();
        assertEquals(0, repl.getReplicatorListenerCount());
    }

    @Test
    public void testDefaultConnectionOptions() throws URISyntaxException {
        // Don't use makeConfig: it sets the heartbeat
        final ReplicatorConfiguration config = new ReplicatorConfiguration(baseTestDb, getRemoteTargetEndpoint())
            .setType(ReplicatorType.PUSH)
            .setContinuous(false);

        final Replicator repl = testReplicator(config);

        Map<String, Object> options = new HashMap<>();
        repl.getSocketFactory().setTestListener(c4Socket -> {
            if (c4Socket == null) { return; }
            synchronized (options) {
                Map<String, Object> opts = ((AbstractCBLWebSocket) c4Socket).getOptions();
                if (opts != null) { options.putAll(opts); }
            }
        });

        // the replicator will fail because the endpoint is bogus
        run(repl, CBLError.Code.NETWORK_OFFSET + C4Constants.NetworkError.UNKNOWN_HOST, CBLError.Domain.CBLITE);

        synchronized (options) {
            assertNull(options.get(C4Replicator.REPLICATOR_OPTION_ENABLE_AUTO_PURGE));
            assertFalse(options.containsKey(C4Replicator.REPLICATOR_HEARTBEAT_INTERVAL));
            assertFalse(options.containsKey(C4Replicator.REPLICATOR_OPTION_MAX_RETRY_INTERVAL));
            assertFalse(options.containsKey(C4Replicator.REPLICATOR_OPTION_MAX_RETRIES));
        }
    }

    @Test
    public void testCustomConnectionOptions() throws URISyntaxException {
        final ReplicatorConfiguration config = new ReplicatorConfiguration(baseTestDb, getRemoteTargetEndpoint())
            .setType(ReplicatorType.PUSH)
            .setContinuous(false)
            .setHeartbeat(33)
            .setMaxAttempts(78)
            .setMaxAttemptWaitTime(45)
            .setAutoPurgeEnabled(false);
        final Replicator repl = testReplicator(config);

        Map<String, Object> options = new HashMap<>();
        repl.getSocketFactory().setTestListener(delegate -> {
            if (!(delegate instanceof AbstractCBLWebSocket)) { return; }
            synchronized (options) {
                Map<String, Object> opts = ((AbstractCBLWebSocket) delegate).getOptions();
                if (opts != null) { options.putAll(opts); }
            }
        });

        // the replicator will fail because the endpoint is bogus
        run(repl, CBLError.Code.NETWORK_OFFSET + C4Constants.NetworkError.UNKNOWN_HOST, CBLError.Domain.CBLITE);

        synchronized (options) {
            assertEquals(Boolean.FALSE, options.get(C4Replicator.REPLICATOR_OPTION_ENABLE_AUTO_PURGE));
            assertEquals(33L, options.get(C4Replicator.REPLICATOR_HEARTBEAT_INTERVAL));
            assertEquals(45L, options.get(C4Replicator.REPLICATOR_OPTION_MAX_RETRY_INTERVAL));
            /* A friend once told me: Don't try to teach a pig to sing.  It won't work and it annoys the pig. */
            assertEquals(78L - 1L, options.get(C4Replicator.REPLICATOR_OPTION_MAX_RETRIES));
        }
    }

    @Test
    public void testStopWhileConnecting() throws URISyntaxException {
        Replicator repl = testReplicator(makeConfig(getRemoteTargetEndpoint(), ReplicatorType.PUSH, false));

        final CountDownLatch latch = new CountDownLatch(1);
        final ListenerToken token = repl.addChangeListener(status -> {
            if (status.getStatus().getActivityLevel() == ReplicatorActivityLevel.CONNECTING) {
                repl.stop();
                latch.countDown();
            }
        });

        repl.start();
        try {
            try { assertTrue(latch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS)); }
            catch (InterruptedException ignore) { }
        }
        finally {
            repl.stop();
        }
    }

    @Test
    public void testReplicatedDocument() throws CouchbaseLiteException {
        Collection collection = baseTestDb.getDefaultCollection();
        String docID = "someDocumentID";
        int flags = C4Constants.DocumentFlags.DELETED;
        CouchbaseLiteException error = new CouchbaseLiteException(
            "Replicator busy",
            CBLError.Domain.CBLITE,
            CBLError.Code.BUSY);

        ReplicatedDocument doc
            = new ReplicatedDocument(collection.getScope().getName(), collection.getName(), docID, flags, error);

        assertEquals(doc.getID(), docID);
        assertTrue(doc.getFlags().contains(DocumentFlag.DELETED));
        CouchbaseLiteException err = doc.getError();
        assertEquals(CBLError.Domain.CBLITE, err.getDomain());
        assertEquals(CBLError.Code.BUSY, err.getCode());
        assertEquals(Collection.DEFAULT_NAME, doc.getCollectionName());
    }

    // CBL-1218
    @Test(expected = IllegalStateException.class)
    public void testStartReplicatorWithClosedDb() throws URISyntaxException {
        Replicator replicator = testReplicator(makeConfig(
            baseTestDb,
            getRemoteTargetEndpoint(),
            ReplicatorType.PUSH_AND_PULL,
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
            ReplicatorType.PUSH_AND_PULL,
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
                ReplicatorType.PUSH_AND_PULL,
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
            ReplicatorActivityLevel.BUSY,
            getActivityLevelFor(C4ReplicatorStatus.ActivityLevel.STOPPED - 1));
        assertEquals(
            ReplicatorActivityLevel.STOPPED,
            getActivityLevelFor(C4ReplicatorStatus.ActivityLevel.STOPPED));
        assertEquals(
            ReplicatorActivityLevel.OFFLINE,
            getActivityLevelFor(C4ReplicatorStatus.ActivityLevel.OFFLINE));
        assertEquals(
            ReplicatorActivityLevel.CONNECTING,
            getActivityLevelFor(C4ReplicatorStatus.ActivityLevel.CONNECTING));
        assertEquals(
            ReplicatorActivityLevel.IDLE,
            getActivityLevelFor(C4ReplicatorStatus.ActivityLevel.IDLE));
        assertEquals(
            ReplicatorActivityLevel.BUSY,
            getActivityLevelFor(C4ReplicatorStatus.ActivityLevel.BUSY));
        assertEquals(
            ReplicatorActivityLevel.BUSY,
            getActivityLevelFor(C4ReplicatorStatus.ActivityLevel.BUSY + 1));
    }

    // Verify that deprecated and new ReplicatorTypes are interchangeable
    @Test
    public void testDeprecatedReplicatorType() throws URISyntaxException {
        ReplicatorConfiguration config = new ReplicatorConfiguration(baseTestDb, getRemoteTargetEndpoint());
        assertEquals(AbstractReplicatorConfiguration.ReplicatorType.PUSH_AND_PULL, config.getReplicatorType());
        assertEquals(ReplicatorType.PUSH_AND_PULL, config.getType());

        config.setReplicatorType(AbstractReplicatorConfiguration.ReplicatorType.PUSH);
        assertEquals(AbstractReplicatorConfiguration.ReplicatorType.PUSH, config.getReplicatorType());
        assertEquals(ReplicatorType.PUSH, config.getType());

        config.setReplicatorType(AbstractReplicatorConfiguration.ReplicatorType.PULL);
        assertEquals(AbstractReplicatorConfiguration.ReplicatorType.PULL, config.getReplicatorType());
        assertEquals(ReplicatorType.PULL, config.getType());

        config.setType(ReplicatorType.PUSH);
        assertEquals(AbstractReplicatorConfiguration.ReplicatorType.PUSH, config.getReplicatorType());
        assertEquals(ReplicatorType.PUSH, config.getType());

        config.setType(ReplicatorType.PULL);
        assertEquals(AbstractReplicatorConfiguration.ReplicatorType.PULL, config.getReplicatorType());
        assertEquals(ReplicatorType.PULL, config.getType());
    }

    /**
     * The 4 tests below test replicator cookies option when specifying replicator configuration
     **/

    @Test
    public void testReplicatorWithBothAuthenticationAndHeaderCookies() throws URISyntaxException {
        Authenticator authenticator = new SessionAuthenticator("mysessionid");
        HashMap<String, String> header = new HashMap<>();
        header.put(AbstractCBLWebSocket.HEADER_COOKIES, "region=nw; city=sf");
        ReplicatorConfiguration configuration = new ReplicatorConfiguration(baseTestDb, getRemoteTargetEndpoint())
            .setAuthenticator(authenticator)
            .setHeaders(header);

        ImmutableReplicatorConfiguration immutableConfiguration = new ImmutableReplicatorConfiguration(configuration);
        HashMap<String, Object> options = (HashMap<String, Object>) immutableConfiguration.getConnectionOptions();

        // cookie option contains both sgw cookie and user specified cookie
        String cookies = (String) options.get(C4Replicator.REPLICATOR_OPTION_COOKIES);
        assertNotNull(cookies);
        assertTrue(cookies.contains("SyncGatewaySession=mysessionid"));
        assertTrue(cookies.contains("region=nw; city=sf"));

        // user specified cookie should be removed from extra header
        HashMap<String, Object> httpHeaders
            = (HashMap<String, Object>) options.get(C4Replicator.REPLICATOR_OPTION_EXTRA_HEADERS);
        assertNotNull(httpHeaders); //httpHeaders must at least include a mapping for User-Agent
        assertFalse(httpHeaders.containsKey(AbstractCBLWebSocket.HEADER_COOKIES));
    }

    @Test
    public void testReplicatorWithNoCookie() throws URISyntaxException {
        ImmutableReplicatorConfiguration immutableConfiguration =
            new ImmutableReplicatorConfiguration(new ReplicatorConfiguration(
                baseTestDb,
                getRemoteTargetEndpoint()));
        HashMap<String, Object> options = (HashMap<String, Object>) immutableConfiguration.getConnectionOptions();
        assertFalse(options.containsKey(C4Replicator.REPLICATOR_OPTION_COOKIES));
    }

    @Test
    public void testReplicatorWithOnlyAuthenticationCookie() throws URISyntaxException {
        Authenticator authenticator = new SessionAuthenticator("mysessionid");
        ReplicatorConfiguration configuration = new ReplicatorConfiguration(baseTestDb, getRemoteTargetEndpoint())
            .setAuthenticator(authenticator);

        ImmutableReplicatorConfiguration immutableConfiguration = new ImmutableReplicatorConfiguration(configuration);
        HashMap<String, Object> options = (HashMap<String, Object>) immutableConfiguration.getConnectionOptions();

        assertEquals(
            "SyncGatewaySession=mysessionid",
            options.get(C4Replicator.REPLICATOR_OPTION_COOKIES));
    }

    @Test
    public void testReplicatorWithOnlyHeaderCookie() throws URISyntaxException {
        HashMap<String, String> header = new HashMap<>();
        header.put(AbstractCBLWebSocket.HEADER_COOKIES, "region=nw; city=sf");
        ReplicatorConfiguration configuration = new ReplicatorConfiguration(baseTestDb, getRemoteTargetEndpoint())
            .setHeaders(header);

        ImmutableReplicatorConfiguration immutableConfiguration = new ImmutableReplicatorConfiguration(configuration);
        HashMap<String, Object> options = (HashMap<String, Object>) immutableConfiguration.getConnectionOptions();

        assertEquals(
            "region=nw; city=sf",
            options.get(C4Replicator.REPLICATOR_OPTION_COOKIES));

        HashMap<String, Object> httpHeaders
            = (HashMap<String, Object>) options.get(C4Replicator.REPLICATOR_OPTION_EXTRA_HEADERS);

        assertNotNull(httpHeaders);  // httpHeaders must at least include a mapping for User-Agent
        assertFalse(httpHeaders.containsKey(AbstractCBLWebSocket.HEADER_COOKIES));
    }

    private ReplicatorActivityLevel getActivityLevelFor(int activityLevel) {
        return new ReplicatorStatus(new C4ReplicatorStatus(activityLevel, 0, 0, 0, 0, 0, 0)).getActivityLevel();
    }
}
