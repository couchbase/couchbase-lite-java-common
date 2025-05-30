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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;

import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.ImmutableReplicatorConfiguration;
import com.couchbase.lite.internal.core.C4Constants;
import com.couchbase.lite.internal.core.C4Replicator;
import com.couchbase.lite.internal.core.C4ReplicatorStatus;
import com.couchbase.lite.internal.replicator.AbstractCBLWebSocket;
import com.couchbase.lite.internal.utils.FlakyTest;


@SuppressWarnings("ConstantConditions")
public class ReplicatorMiscTest extends BaseReplicatorTest {
    @Test
    public void testGetExecutor() {
        final Executor executor = runnable -> { };
        final ReplicatorChangeListener listener = change -> { };

        // custom Executor
        try (ReplicatorChangeListenerToken token = new ReplicatorChangeListenerToken(executor, listener, t -> { })) {
            Assert.assertEquals(executor, token.getExecutor());
        }
        // UI thread Executor
        try (ReplicatorChangeListenerToken token = new ReplicatorChangeListenerToken(null, listener, t -> { })) {
            Assert.assertEquals(CouchbaseLiteInternal.getExecutionService().getDefaultExecutor(), token.getExecutor());
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

        Replicator repl = makeBasicRepl();
        ReplicatorStatus replStatus = new ReplicatorStatus(c4ReplicatorStatus);

        ReplicatorChange replChange = new ReplicatorChange(repl, replStatus);
        Assert.assertEquals(replChange.getReplicator(), repl);

        ReplicatorStatus status = replChange.getStatus();
        Assert.assertNotNull(status);
        Assert.assertEquals(status.getActivityLevel(), replStatus.getActivityLevel());

        ReplicatorProgress progress = status.getProgress();
        Assert.assertNotNull(progress);
        Assert.assertEquals(completed, progress.getCompleted());
        Assert.assertEquals(total, progress.getTotal());

        CouchbaseLiteException error = status.getError();
        Assert.assertNotNull(error);
        Assert.assertEquals(errorCode, error.getCode());
        Assert.assertEquals(CBLError.Domain.CBLITE, error.getDomain());
    }

    @Test
    public void testDocumentReplication() {
        List<ReplicatedDocument> docs = new ArrayList<>();
        Replicator repl = makeBasicRepl();
        DocumentReplication doc = new DocumentReplication(repl, true, docs);
        Assert.assertTrue(doc.isPush());
        Assert.assertEquals(doc.getReplicator(), repl);
        Assert.assertEquals(doc.getDocuments(), docs);
    }

    // https://issues.couchbase.com/browse/CBL-89
    // Thanks to @James Flather for the ready-made test code
    @Test
    public void testStopBeforeStart() { makeBasicRepl().stop(); }

    // https://issues.couchbase.com/browse/CBL-88
    // Thanks to @James Flather for the ready-made test code
    @Test
    public void testStatusBeforeStart() { makeBasicRepl().getStatus(); }

    @Test
    public void testDocumentEndListenerTokenRemove() {
        final Replicator repl = makeBasicRepl();
        Assert.assertEquals(0, repl.getDocEndListenerCount());
        ListenerToken token = repl.addDocumentReplicationListener(r -> { });
        Assert.assertEquals(1, repl.getDocEndListenerCount());
        token.remove();
        Assert.assertEquals(0, repl.getDocEndListenerCount());
        token.remove();
        Assert.assertEquals(0, repl.getDocEndListenerCount());
    }

    @Test
    public void testReplicationListenerTokenRemove() {
        final Replicator repl = makeBasicRepl();
        Assert.assertEquals(0, repl.getReplicatorListenerCount());
        ListenerToken token = repl.addChangeListener(r -> { });
        Assert.assertEquals(1, repl.getReplicatorListenerCount());
        token.remove();
        Assert.assertEquals(0, repl.getReplicatorListenerCount());
        token.remove();
        Assert.assertEquals(0, repl.getReplicatorListenerCount());
    }

    @Test
    public void testDefaultConnectionOptions() {
        final Replicator repl = testReplicator(makeDefaultConfig());

        Map<String, Object> options = new HashMap<>();
        repl.getSocketFactory().setTestListener(c4Socket -> {
            if (c4Socket == null) { return; }
            synchronized (options) {
                Map<String, Object> opts = ((AbstractCBLWebSocket) c4Socket).getOptions();
                if (opts != null) { options.putAll(opts); }
            }
        });

        // the replicator will fail because the endpoint is bogus
        run(
            repl,
            false,
            LONG_TIMEOUT_SEC,
            new CouchbaseLiteException("", CBLError.Domain.CBLITE, CBLError.Code.TIMEOUT),
            new CouchbaseLiteException("", CBLError.Domain.CBLITE, CBLError.Code.UNKNOWN_HOST));

        synchronized (options) {
            Assert.assertEquals(
                Defaults.Replicator.ACCEPT_PARENT_COOKIES,
                options.get(C4Replicator.REPLICATOR_OPTION_ACCEPT_PARENT_COOKIES));
            Assert.assertEquals(
                Defaults.Replicator.ENABLE_AUTO_PURGE,
                options.get(C4Replicator.REPLICATOR_OPTION_ENABLE_AUTO_PURGE));
            Assert.assertEquals(
                Defaults.Replicator.HEARTBEAT,
                ((Number) options.get(C4Replicator.REPLICATOR_HEARTBEAT_INTERVAL)).intValue());
            Assert.assertEquals(
                Defaults.Replicator.MAX_ATTEMPTS_WAIT_TIME,
                ((Number) options.get(C4Replicator.REPLICATOR_OPTION_MAX_RETRY_INTERVAL)).intValue());
            Assert.assertEquals(
                Defaults.Replicator.MAX_ATTEMPTS_SINGLE_SHOT - 1,
                ((Number) options.get(C4Replicator.REPLICATOR_OPTION_MAX_RETRIES)).intValue());
        }
    }

    @FlakyTest(description = "fails, occasionally, on a Nexus 4 running Android 23")
    @Test
    public void testCustomConnectionOptions() {
        // Caution: not disabling heartbeat
        final Replicator repl = testReplicator(makeDefaultConfig()
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
        run(
            repl,
            false,
            STD_TIMEOUT_SEC,
            new CouchbaseLiteException("", CBLError.Domain.CBLITE, CBLError.Code.TIMEOUT),
            new CouchbaseLiteException("", CBLError.Domain.CBLITE, CBLError.Code.UNKNOWN_HOST));

        synchronized (options) {
            Assert.assertEquals(Boolean.TRUE, options.get(C4Replicator.REPLICATOR_OPTION_ACCEPT_PARENT_COOKIES));
            Assert.assertEquals(Boolean.FALSE, options.get(C4Replicator.REPLICATOR_OPTION_ENABLE_AUTO_PURGE));
            Assert.assertEquals(33L, options.get(C4Replicator.REPLICATOR_HEARTBEAT_INTERVAL));
            Assert.assertEquals(45L, options.get(C4Replicator.REPLICATOR_OPTION_MAX_RETRY_INTERVAL));
            // A friend once told me: Don't try to teach a pig to sing.  It won't work and it annoys the pig.
            Assert.assertEquals(78L - 1, options.get(C4Replicator.REPLICATOR_OPTION_MAX_RETRIES));
        }
    }

    @FlakyTest(description = "fails, occasionally, on a Nexus 4 running Android 23")
    @Test
    public void testBasicAuthOptions() {
        final ReplicatorConfiguration config = makeBasicConfig();
        config.setAuthenticator(new BasicAuthenticator("user", "sekrit".toCharArray()));
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
        run(
            repl,
            false,
            STD_TIMEOUT_SEC,
            new CouchbaseLiteException("", CBLError.Domain.CBLITE, CBLError.Code.TIMEOUT),
            new CouchbaseLiteException("", CBLError.Domain.CBLITE, CBLError.Code.UNKNOWN_HOST));

        synchronized (options) {
            final Object authOpts = options.get(C4Replicator.REPLICATOR_OPTION_AUTHENTICATION);
            Assert.assertTrue(authOpts instanceof Map);
            final Map<?, ?> auth = (Map<?, ?>) authOpts;
            Assert.assertEquals(C4Replicator.AUTH_TYPE_BASIC, auth.get(C4Replicator.REPLICATOR_AUTH_TYPE));
            Assert.assertEquals("sekrit", auth.get(C4Replicator.REPLICATOR_AUTH_PASSWORD));
        }
    }

    @Test
    public void testStopWhileConnecting() throws InterruptedException {
        Replicator repl = makeBasicRepl();

        final CountDownLatch latch = new CountDownLatch(1);
        final ListenerToken token = repl.addChangeListener(status -> {
            if (status.getStatus().getActivityLevel() == ReplicatorActivityLevel.CONNECTING) {
                repl.stop();
                latch.countDown();
            }
        });

        repl.start();
        try { Assert.assertTrue(latch.await(STD_TIMEOUT_SEC, TimeUnit.SECONDS)); }
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

        Assert.assertEquals(replicatedDoc.getID(), docId);

        Assert.assertEquals(getTargetCollection().getScope().getName(), replicatedDoc.getScope());
        Assert.assertEquals(getTargetCollection().getName(), replicatedDoc.getCollection());

        Assert.assertTrue(replicatedDoc.getFlags().contains(DocumentFlag.DELETED));

        CouchbaseLiteException err = replicatedDoc.getError();
        Assert.assertNotNull(err);
        Assert.assertEquals(CBLError.Domain.CBLITE, err.getDomain());
        Assert.assertEquals(CBLError.Code.BUSY, err.getCode());
    }

    // CBL-1218
    @Test
    public void testStartReplicatorWithClosedDb() {
        Replicator repl = makeBasicRepl();

        closeDb(getTestDatabase());

        Assert.assertThrows(CouchbaseLiteError.class, repl::start);
    }

    // CBL-1218
    @Test
    public void testIsDocumentPendingWithClosedDb() {
        Replicator repl = makeBasicRepl();

        deleteDb(getTestDatabase());

        Assert.assertThrows(CouchbaseLiteError.class, () -> repl.getPendingDocumentIds(getTestCollection()));
    }

    // CBL-1218
    @Test
    public void testGetPendingDocIdsWithClosedDb() {
        Replicator repl = makeBasicRepl();

        closeDb(getTestDatabase());

        Assert.assertThrows(CouchbaseLiteError.class, () -> repl.isDocumentPending("who-cares", getTestCollection()));
    }

    // CBL-1441
    @Test
    public void testReplicatorStatus() {
        Assert.assertEquals(
            ReplicatorActivityLevel.BUSY,
            getActivityLevelFor(C4ReplicatorStatus.ActivityLevel.STOPPED - 1));
        Assert.assertEquals(
            ReplicatorActivityLevel.STOPPED,
            getActivityLevelFor(C4ReplicatorStatus.ActivityLevel.STOPPED));
        Assert.assertEquals(
            ReplicatorActivityLevel.OFFLINE,
            getActivityLevelFor(C4ReplicatorStatus.ActivityLevel.OFFLINE));
        Assert.assertEquals(
            ReplicatorActivityLevel.CONNECTING,
            getActivityLevelFor(C4ReplicatorStatus.ActivityLevel.CONNECTING));
        Assert.assertEquals(
            ReplicatorActivityLevel.IDLE,
            getActivityLevelFor(C4ReplicatorStatus.ActivityLevel.IDLE));
        Assert.assertEquals(
            ReplicatorActivityLevel.BUSY,
            getActivityLevelFor(C4ReplicatorStatus.ActivityLevel.BUSY));
        Assert.assertEquals(
            ReplicatorActivityLevel.BUSY,
            getActivityLevelFor(C4ReplicatorStatus.ActivityLevel.BUSY + 1));
    }

    // Verify that deprecated and new ReplicatorTypes are interchangeable

    @SuppressWarnings("deprecation")
    @Test
    public void testDeprecatedReplicatorType() {
        final ReplicatorConfiguration config = makeDefaultConfig();

        Assert.assertEquals(AbstractReplicatorConfiguration.ReplicatorType.PUSH_AND_PULL, config.getReplicatorType());
        Assert.assertEquals(ReplicatorType.PUSH_AND_PULL, config.getType());

        config.setReplicatorType(AbstractReplicatorConfiguration.ReplicatorType.PUSH);
        Assert.assertEquals(AbstractReplicatorConfiguration.ReplicatorType.PUSH, config.getReplicatorType());
        Assert.assertEquals(ReplicatorType.PUSH, config.getType());

        config.setReplicatorType(AbstractReplicatorConfiguration.ReplicatorType.PULL);
        Assert.assertEquals(AbstractReplicatorConfiguration.ReplicatorType.PULL, config.getReplicatorType());
        Assert.assertEquals(ReplicatorType.PULL, config.getType());

        config.setType(ReplicatorType.PUSH);
        Assert.assertEquals(AbstractReplicatorConfiguration.ReplicatorType.PUSH, config.getReplicatorType());
        Assert.assertEquals(ReplicatorType.PUSH, config.getType());

        config.setType(ReplicatorType.PULL);
        Assert.assertEquals(AbstractReplicatorConfiguration.ReplicatorType.PULL, config.getReplicatorType());
        Assert.assertEquals(ReplicatorType.PULL, config.getType());
    }

    /**
     * The 4 tests below test replicator cookies option when specifying replicator configuration
     **/

    @Test
    public void testReplicatorWithBothAuthenticationAndHeaderCookies() {
        Authenticator authenticator = new SessionAuthenticator("mysessionid");
        HashMap<String, String> header = new HashMap<>();
        header.put(AbstractCBLWebSocket.HEADER_COOKIES, "region=nw; city=sf");
        ReplicatorConfiguration configuration = makeDefaultConfig()
            .setAuthenticator(authenticator)
            .setHeaders(header);

        ImmutableReplicatorConfiguration immutableConfiguration = new ImmutableReplicatorConfiguration(configuration);
        HashMap<String, Object> options = (HashMap<String, Object>) immutableConfiguration.getConnectionOptions();

        // cookie option contains both sgw cookie and user specified cookie
        String cookies = (String) options.get(C4Replicator.REPLICATOR_OPTION_COOKIES);
        Assert.assertNotNull(cookies);
        Assert.assertTrue(cookies.contains("SyncGatewaySession=mysessionid"));
        Assert.assertTrue(cookies.contains("region=nw; city=sf"));

        // user specified cookie should have been removed from extra header
        Object httpHeaders = options.get(C4Replicator.REPLICATOR_OPTION_EXTRA_HEADERS);
        Assert.assertTrue(httpHeaders instanceof Map);

        // httpHeaders must at least include a mapping for User-Agent
        Assert.assertFalse(((Map<?, ?>) httpHeaders).containsKey(AbstractCBLWebSocket.HEADER_COOKIES));
    }

    @Test
    public void testReplicatorWithNoCookie() {
        ImmutableReplicatorConfiguration config = new ImmutableReplicatorConfiguration(makeDefaultConfig());
        Map<?, ?> options = config.getConnectionOptions();
        Assert.assertFalse(options.containsKey(C4Replicator.REPLICATOR_OPTION_COOKIES));
    }

    @Test
    public void testReplicatorWithOnlyAuthenticationCookie() {
        Assert.assertEquals(
            "SyncGatewaySession=mysessionid",
            new ImmutableReplicatorConfiguration(
                makeDefaultConfig().setAuthenticator(new SessionAuthenticator("mysessionid")))
                .getConnectionOptions()
                .get(C4Replicator.REPLICATOR_OPTION_COOKIES));
    }

    @Test
    public void testReplicatorWithOnlyHeaderCookie() {
        HashMap<String, String> header = new HashMap<>();
        header.put(AbstractCBLWebSocket.HEADER_COOKIES, "region=nw; city=sf");
        ReplicatorConfiguration configuration = makeDefaultConfig().setHeaders(header);

        ImmutableReplicatorConfiguration immutableConfiguration = new ImmutableReplicatorConfiguration(configuration);
        HashMap<String, Object> options = (HashMap<String, Object>) immutableConfiguration.getConnectionOptions();

        Assert.assertEquals(
            "region=nw; city=sf",
            options.get(C4Replicator.REPLICATOR_OPTION_COOKIES));

        Object httpHeaders = options.get(C4Replicator.REPLICATOR_OPTION_EXTRA_HEADERS);
        Assert.assertTrue(httpHeaders instanceof Map);

        // httpHeaders must at least include a mapping for User-Agent
        Assert.assertFalse(((Map<?, ?>) httpHeaders).containsKey(AbstractCBLWebSocket.HEADER_COOKIES));
    }


    // 3.1 TestCreateProxyAuthenticator
    //    Create a new ProxyAuthenticator object with a username and password.
    //    Get a username from the object and check if the returned value is correct.
    //    Get a password from the object and check if the returned value is correct.
    //    Check that the returned password object is a new copy.
    //    Get a password from the object again and check that the returned password object is a new copy.
    @Test
    public void testCreateProxyAuthenticator() {
        final char[] password = "Xakk".toCharArray();

        final char[] pwd = new char[password.length];
        System.arraycopy(password, 0, pwd, 0, password.length);
        final ProxyAuthenticator proxyAuth = new ProxyAuthenticator("Stewart", pwd);

        final char[] pwd1 = proxyAuth.getPassword();
        Assert.assertArrayEquals(password, pwd1);
        Assert.assertNotSame(pwd, pwd1);

        pwd[0] = 'Y';
        final char[] pwd2 = proxyAuth.getPassword();
        Assert.assertArrayEquals(password, pwd2);
        Assert.assertNotSame(pwd1, pwd2);
    }

    // 3.2 TestGetDefaultProxyAuthenticator
    //    Create a ReplicatorConfiguration object.
    //    Get the proxy authenticator object and check that the returned object is null.
    @Test
    public void testGetDefaultProxyAuthenticator() throws URISyntaxException {
        Assert.assertNull(new ReplicatorConfiguration(new URLEndpoint(new URI("ws://foo.com"))).getProxyAuthenticator());
    }

    // 3.3 TestSetNewProxyAuthenticator
    //    Create a ReplicatorConfiguration object.
    //    Create a new ProxyAuthenticator object with a username and password.
    //    Set the ProxyAuthenticator object to the ReplicatorConfiguration object.
    //    Get the ProxyAuthenticator object from the ReplicatorConfiguration object.
    //    Check that the returned ProxyAuthenticator object has the correct username and password.
    @Test
    public void testSetNewProxyAuthenticator() throws URISyntaxException {
        final ProxyAuthenticator proxyAuth = new ProxyAuthenticator("Wilson", "Vince".toCharArray());
        Assert.assertEquals(
            proxyAuth,
            new ReplicatorConfiguration(new URLEndpoint(new URI("ws://foo.com")))
                .setProxyAuthenticator(proxyAuth)
                .getProxyAuthenticator());
    }

    // 3.4 TestSetNullProxyAuthenticator
    //    Create a ReplicatorConfiguration object.
    //    Set null ProxyAuthenticator to the ReplicatorConfiguration object.
    //    Get the ProxyAuthenticator object from the ReplicatorConfiguration object.
    //    Check that the returned ProxyAuthenticator is null.
    @Test
    public void testSetNullProxyAuthenticator() throws URISyntaxException {
        Assert.assertNull(new ReplicatorConfiguration(new URLEndpoint(new URI("ws://foo.com")))
            .setProxyAuthenticator(null)
            .getProxyAuthenticator());
    }

    // 3.5 TestUpdateProxyAuthenticator
    //    Create a ReplicatorConfiguration object.
    //    Create a new ProxyAuthenticator object with a username and password.
    //    Set the ProxyAuthenticator object to the ReplicatorConfiguration object.
    //    Get the ProxyAuthenticator object from the ReplicatorConfiguration object.
    //    Check that the returned ProxyAuthenticator object has the correct username and password.
    //    Create a new ProxyAuthenticator object with a different username and password.
    //    Set the new ProxyAuthenticator object to the ReplicatorConfiguration object.
    //    Get the ProxyAuthenticator object from the ReplicatorConfiguration object.
    //    Check that the returned ProxyAuthenticator object has the updated correct username and password.
    @Test
    public void testUpdateProxyAuthenticator() throws URISyntaxException {
        final ReplicatorConfiguration config = new ReplicatorConfiguration(new URLEndpoint(new URI("ws://foo.com")));
        ProxyAuthenticator proxyAuth = new ProxyAuthenticator("Wilson", "Vince".toCharArray());
        Assert.assertEquals(proxyAuth, config.setProxyAuthenticator(proxyAuth).getProxyAuthenticator());
        proxyAuth = new ProxyAuthenticator("Matheson", "Charlie".toCharArray());
        Assert.assertEquals(proxyAuth, config.setProxyAuthenticator(proxyAuth).getProxyAuthenticator());
    }

    // 3.6 TestResetProxyAuthenticator
    //    Create a ReplicatorConfiguration object.
    //    Create a new ProxyAuthenticator object with a username and password.
    //    Set the ProxyAuthenticator object to the ReplicatorConfiguration object.
    //    Get the ProxyAuthenticator object from the ReplicatorConfiguration object.
    //    Check that the returned ProxyAuthenticator object has the correct username and password.
    //    Set null ProxyAuthenticator to the ReplicatorConfiguration object.
    //    Get the ProxyAuthenticator object from the ReplicatorConfiguration object.
    //    Check that the returned ProxyAuthenticator is null.
    @Test
    public void testResetProxyAuthenticator() throws URISyntaxException {
        final ReplicatorConfiguration config = new ReplicatorConfiguration(new URLEndpoint(new URI("ws://foo.com")));
        final ProxyAuthenticator proxyAuth = new ProxyAuthenticator("Wilson", "Vince".toCharArray());
        Assert.assertEquals(proxyAuth, config.setProxyAuthenticator(proxyAuth).getProxyAuthenticator());
        Assert.assertNull(config.setProxyAuthenticator(null).getProxyAuthenticator());
    }

    /// ////// Utility functions

    private ReplicatorActivityLevel getActivityLevelFor(int activityLevel) {
        return new ReplicatorStatus(new C4ReplicatorStatus(activityLevel, 0, 0, 0, 0, 0, 0)).getActivityLevel();
    }

    // return a nearly default config.
    // Not using makeSimpleReplConfig, in order to make sure this is pure vanilla
    // Note that this does not set heartbeat.  This config is likely to cause flaky tests
    // when used in test with a live replicator
    private ReplicatorConfiguration makeDefaultConfig() {
        return new ReplicatorConfiguration(getMockURLEndpoint()).addCollection(getTestCollection(), null);
    }

    private ReplicatorConfiguration makeBasicConfig() {
        final ReplicatorConfiguration config = makeDefaultConfig();
        config.setHeartbeat(AbstractReplicatorConfiguration.DISABLE_HEARTBEAT);
        return config;
    }

    private Replicator makeBasicRepl() { return testReplicator(makeBasicConfig()); }
}
