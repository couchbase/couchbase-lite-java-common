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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

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
import static org.junit.Assert.assertTrue;


@SuppressWarnings("ConstantConditions")
public class ReplicatorMiscTest extends BaseReplicatorTest {

    @Test
    public void testGetExecutor() {
        final Executor executor = runnable -> { };
        final ReplicatorChangeListener listener = change -> { };

        // custom Executor
        try (ReplicatorChangeListenerToken token = new ReplicatorChangeListenerToken(executor, listener, t -> { })) {
            assertEquals(executor, token.getExecutor());
        }
            // UI thread Executor
        try (ReplicatorChangeListenerToken token = new ReplicatorChangeListenerToken(null, listener, t -> { })) {
            assertEquals(CouchbaseLiteInternal.getExecutionService().getDefaultExecutor(), token.getExecutor());
        }
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

        Replicator repl = makeRepl();
        ReplicatorStatus replStatus = new ReplicatorStatus(c4ReplicatorStatus);
        ReplicatorChange repChange = new ReplicatorChange(repl, replStatus);

        assertEquals(repChange.getReplicator(), repl);

        ReplicatorStatus status = repChange.getStatus();
        assertNotNull(status);
        assertEquals(status.getActivityLevel(), status.getActivityLevel());

        ReplicatorProgress progress = status.getProgress();
        assertNotNull(progress);
        assertEquals(progress.getCompleted(), completed);
        assertEquals(progress.getTotal(), total);

        CouchbaseLiteException error = status.getError();
        assertNotNull(error);
        assertEquals(error.getCode(), errorCode);
        assertEquals(error.getDomain(), CBLError.Domain.CBLITE);
    }

    @Test
    public void testDocumentReplication() {
        List<ReplicatedDocument> docs = new ArrayList<>();
        Replicator repl = testReplicator(makeConfig());
        DocumentReplication doc = new DocumentReplication(repl, true, docs);
        assertTrue(doc.isPush());
        assertEquals(doc.getReplicator(), repl);
        assertEquals(doc.getDocuments(), docs);
    }

    // https://issues.couchbase.com/browse/CBL-89
    // Thanks to @James Flather for the ready-made test code
    @Test
    public void testStopBeforeStart() { testReplicator(makeConfig()).stop(); }

    // https://issues.couchbase.com/browse/CBL-88
    // Thanks to @James Flather for the ready-made test code
    @Test
    public void testStatusBeforeStart() { testReplicator(makeConfig()).getStatus(); }

    @Test
    public void testDocumentEndListenerTokenRemove() {
        final Replicator repl = makeRepl();
        assertEquals(0, repl.getDocEndListenerCount());
        ListenerToken token = repl.addDocumentReplicationListener(r -> { });
        assertEquals(1, repl.getDocEndListenerCount());
        token.remove();
        assertEquals(0, repl.getDocEndListenerCount());
        token.remove();
        assertEquals(0, repl.getDocEndListenerCount());
    }

    @Test
    public void testReplicationListenerTokenRemove() {
        final Replicator repl = makeRepl();
        assertEquals(0, repl.getReplicatorListenerCount());
        ListenerToken token = repl.addChangeListener(r -> { });
        assertEquals(1, repl.getReplicatorListenerCount());
        token.remove();
        assertEquals(0, repl.getReplicatorListenerCount());
        token.remove();
        assertEquals(0, repl.getReplicatorListenerCount());
    }

    @Test
    public void testDefaultConnectionOptions() {
        final Replicator repl = makeRepl(makeDefaultConfig().setType(ReplicatorType.PUSH).setContinuous(false));

        Map<String, Object> options = new HashMap<>();
        repl.getSocketFactory().setTestListener(c4Socket -> {
            if (c4Socket == null) { return; }
            synchronized (options) {
                Map<String, Object> opts = ((AbstractCBLWebSocket) c4Socket).getOptions();
                if (opts != null) { options.putAll(opts); }
            }
        });

        // the replicator will fail because the endpoint is bogus
        run(repl, false, CBLError.Domain.CBLITE, CBLError.Code.UNKNOWN_HOST);

        synchronized (options) {
            assertEquals(
                Defaults.Replicator.ACCEPT_PARENT_COOKIES,
                options.get(C4Replicator.REPLICATOR_OPTION_ACCEPT_PARENT_COOKIES));
            assertEquals(
                Defaults.Replicator.ENABLE_AUTO_PURGE,
                options.get(C4Replicator.REPLICATOR_OPTION_ENABLE_AUTO_PURGE));
            assertEquals(
                Defaults.Replicator.HEARTBEAT,
                ((Number) options.get(C4Replicator.REPLICATOR_HEARTBEAT_INTERVAL)).intValue());
            assertEquals(
                Defaults.Replicator.MAX_ATTEMPT_WAIT_TIME,
                ((Number) options.get(C4Replicator.REPLICATOR_OPTION_MAX_RETRY_INTERVAL)).intValue());
            assertEquals(
                Defaults.Replicator.MAX_ATTEMPTS_SINGLE_SHOT - 1,
                ((Number) options.get(C4Replicator.REPLICATOR_OPTION_MAX_RETRIES)).intValue());
        }
    }

    @Test
    public void testCustomConnectionOptions() {
        final Replicator repl = makeRepl(makeConfig()
            .setHeartbeat(33)
            .setMaxAttempts(78)
            .setMaxAttemptWaitTime(45)
            .setAutoPurgeEnabled(false)
            .setAcceptParentDomainCookies(true));

        Map<String, Object> options = new HashMap<>();
        repl.getSocketFactory().setTestListener(delegate -> {
            if (!(delegate instanceof AbstractCBLWebSocket)) { return; }
            synchronized (options) {
                Map<String, Object> opts = ((AbstractCBLWebSocket) delegate).getOptions();
                if (opts != null) { options.putAll(opts); }
            }
        });

        // the replicator will fail because the endpoint is bogus
        run(repl, false, CBLError.Domain.CBLITE, CBLError.Code.UNKNOWN_HOST);

        synchronized (options) {
            assertEquals(Boolean.TRUE, options.get(C4Replicator.REPLICATOR_OPTION_ACCEPT_PARENT_COOKIES));
            assertEquals(Boolean.FALSE, options.get(C4Replicator.REPLICATOR_OPTION_ENABLE_AUTO_PURGE));
            assertEquals(33L, options.get(C4Replicator.REPLICATOR_HEARTBEAT_INTERVAL));
            assertEquals(45L, options.get(C4Replicator.REPLICATOR_OPTION_MAX_RETRY_INTERVAL));
            // A friend once told me: Don't try to teach a pig to sing.  It won't work and it annoys the pig.
            assertEquals(78L - 1, options.get(C4Replicator.REPLICATOR_OPTION_MAX_RETRIES));
        }
    }

    @Test
    public void testStopWhileConnecting() throws InterruptedException {
        Replicator repl = makeRepl();

        final CountDownLatch latch = new CountDownLatch(1);
        final ListenerToken token = repl.addChangeListener(status -> {
            if (status.getStatus().getActivityLevel() == ReplicatorActivityLevel.CONNECTING) {
                repl.stop();
                latch.countDown();
            }
        });

        repl.start();
        try { assertTrue(latch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS)); }
        finally {
            token.remove();
            repl.stop();
        }
    }

    @Test
    public void testReplicatedDocument() {
        String docId = getUniqueName("replicated-doc");
        ReplicatedDocument replicatedDoc = new ReplicatedDocument(
            getTargetCollection().getScope().getName(),
            getTargetCollection().getName(),
            docId,
            C4Constants.DocumentFlags.DELETED,
            new CouchbaseLiteException(
                "Replicator busy",
                CBLError.Domain.CBLITE,
                CBLError.Code.BUSY));

        assertEquals(replicatedDoc.getID(), docId);

        assertEquals(getTargetCollection().getScope().getName(), replicatedDoc.getCollectionScope());
        assertEquals(getTargetCollection().getName(), replicatedDoc.getCollectionName());

        assertTrue(replicatedDoc.getFlags().contains(DocumentFlag.DELETED));

        CouchbaseLiteException err = replicatedDoc.getError();
        assertNotNull(err);
        assertEquals(CBLError.Domain.CBLITE, err.getDomain());
        assertEquals(CBLError.Code.BUSY, err.getCode());
    }

    // CBL-1218
    @Test
    public void testStartReplicatorWithClosedDb() {
        Replicator repl = makeRepl(makeConfig());

        closeDb(getTestDatabase());

        assertThrows(IllegalStateException.class, () -> repl.start());
    }

    // CBL-1218
    @Test
    public void testIsDocumentPendingWithClosedDb() {
        Replicator repl = makeRepl();

        deleteDb(getTestDatabase());

        assertThrows(IllegalStateException.class, () -> repl.getPendingDocumentIds(getTestCollection()));
    }

    // CBL-1218
    @Test
    public void testGetPendingDocIdsWithClosedDb() {
        Replicator repl = makeRepl();

        closeDb(getTestDatabase());

        assertThrows(IllegalStateException.class, () -> repl.isDocumentPending("who-cares", getTestCollection()));
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

    @SuppressWarnings("deprecation")
    @Test
    public void testDeprecatedReplicatorType() {
        ReplicatorConfiguration config = makeDefaultConfig();
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
    public void testReplicatorWithBothAuthenticationAndHeaderCookies() {
        Authenticator authenticator = new SessionAuthenticator("mysessionid");
        HashMap<String, String> header = new HashMap<>();
        header.put(AbstractCBLWebSocket.HEADER_COOKIES, "region=nw; city=sf");
        ReplicatorConfiguration configuration = makeConfig()
            .setAuthenticator(authenticator)
            .setHeaders(header);

        ImmutableReplicatorConfiguration immutableConfiguration = new ImmutableReplicatorConfiguration(configuration);
        HashMap<String, Object> options = (HashMap<String, Object>) immutableConfiguration.getConnectionOptions();

        // cookie option contains both sgw cookie and user specified cookie
        String cookies = (String) options.get(C4Replicator.REPLICATOR_OPTION_COOKIES);
        assertNotNull(cookies);
        assertTrue(cookies.contains("SyncGatewaySession=mysessionid"));
        assertTrue(cookies.contains("region=nw; city=sf"));

        // user specified cookie should have been removed from extra header
        Object httpHeaders = options.get(C4Replicator.REPLICATOR_OPTION_EXTRA_HEADERS);
        assertTrue(httpHeaders instanceof Map);

        // httpHeaders must at least include a mapping for User-Agent
        assertFalse(((Map<?, ?>) httpHeaders).containsKey(AbstractCBLWebSocket.HEADER_COOKIES));
    }

    @Test
    public void testReplicatorWithNoCookie() {
        ImmutableReplicatorConfiguration config = new ImmutableReplicatorConfiguration(makeDefaultConfig());
        Map<?, ?> options = config.getConnectionOptions();
        assertFalse(options.containsKey(C4Replicator.REPLICATOR_OPTION_COOKIES));
    }

    @Test
    public void testReplicatorWithOnlyAuthenticationCookie() {
        assertEquals(
            "SyncGatewaySession=mysessionid",
            new ImmutableReplicatorConfiguration(makeConfig().setAuthenticator(new SessionAuthenticator("mysessionid")))
                .getConnectionOptions()
                .get(C4Replicator.REPLICATOR_OPTION_COOKIES));
    }

    @Test
    public void testReplicatorWithOnlyHeaderCookie() {
        HashMap<String, String> header = new HashMap<>();
        header.put(AbstractCBLWebSocket.HEADER_COOKIES, "region=nw; city=sf");
        ReplicatorConfiguration configuration = makeConfig().setHeaders(header);

        ImmutableReplicatorConfiguration immutableConfiguration = new ImmutableReplicatorConfiguration(configuration);
        HashMap<String, Object> options = (HashMap<String, Object>) immutableConfiguration.getConnectionOptions();

        assertEquals(
            "region=nw; city=sf",
            options.get(C4Replicator.REPLICATOR_OPTION_COOKIES));

        Object httpHeaders = options.get(C4Replicator.REPLICATOR_OPTION_EXTRA_HEADERS);
        assertTrue(httpHeaders instanceof Map);

        // httpHeaders must at least include a mapping for User-Agent
        assertFalse(((Map<?, ?>) httpHeaders).containsKey(AbstractCBLWebSocket.HEADER_COOKIES));
    }

    private ReplicatorActivityLevel getActivityLevelFor(int activityLevel) {
        return new ReplicatorStatus(new C4ReplicatorStatus(activityLevel, 0, 0, 0, 0, 0, 0)).getActivityLevel();
    }

    private ReplicatorConfiguration makeDefaultConfig() {
        return new ReplicatorConfiguration(getMockURLEndpoint())
            .addCollection(getTestCollection(), null);
    }

    private ReplicatorConfiguration makeConfig() {
        return makeDefaultConfig()
            .setType(ReplicatorType.PUSH)
            .setContinuous(false)
            .setHeartbeat(AbstractReplicatorConfiguration.DISABLE_HEARTBEAT);
    }

    // Kotlin shim functions

    private Replicator makeRepl() { return makeRepl(makeConfig()); }

    private Replicator makeRepl(ReplicatorConfiguration config) { return testReplicator(config); }
}
