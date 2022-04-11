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
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;

import com.couchbase.lite.internal.utils.Fn;
import com.couchbase.lite.internal.utils.Report;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


public abstract class BaseReplicatorTest extends BaseDbTest {
    protected Replicator baseTestReplicator;
    protected Database otherDB;

    @Before
    public final void setUpBaseReplicatorTest() throws CouchbaseLiteException {
        otherDB = createDb("replicator_db");
        Report.log(LogLevel.INFO, "Create other DB: " + otherDB);
        assertNotNull(otherDB);
        synchronized (otherDB.getDbLock()) { assertTrue(otherDB.isOpen()); }
    }

    @After
    public final void tearDownBaseReplicatorTest() {
        deleteDb(otherDB);
        Report.log(LogLevel.INFO, "Deleted other DB: " + otherDB);
    }

    protected final URLEndpoint getRemoteTargetEndpoint() throws URISyntaxException {
        return new URLEndpoint(new URI("ws://foo.couchbase.com/db"));
    }

    // helper method allows kotlin to call isDocumentPending(null)
    // Kotlin type checking prevents this.
    protected final boolean callIsDocumentPendingWithNullId(Replicator repl) throws CouchbaseLiteException {
        return repl.isDocumentPending(null);
    }

    // Don't let the NetworkConnectivityManager confuse tests
    protected final Replicator testReplicator(ReplicatorConfiguration config) { return new Replicator(null, config); }

    protected final ReplicatorConfiguration makeConfig(
        Endpoint target,
        ReplicatorType type,
        boolean continuous) {
        return makeConfig(target, type, continuous, null);
    }

    protected final ReplicatorConfiguration makeConfig(
        Endpoint target,
        ReplicatorType type,
        boolean continuous,
        Certificate pinnedServerCert) {
        return makeConfig(baseTestDb, target, type, continuous, pinnedServerCert);
    }

    protected final ReplicatorConfiguration makeConfig(
        Database source,
        Endpoint target,
        ReplicatorType type,
        boolean continuous,
        Certificate pinnedServerCert,
        ConflictResolver resolver) {
        ReplicatorConfiguration config = makeConfig(source, target, type, continuous, pinnedServerCert);

        if (resolver != null) { config.setConflictResolver(resolver); }

        return config;
    }

    protected final ReplicatorConfiguration makeConfig(
        Database source,
        Endpoint target,
        ReplicatorType type,
        boolean continuous,
        Certificate pinnedServerCert) {
        final ReplicatorConfiguration config = makeConfig(source, target, type, continuous);

        final byte[] pin;
        try { pin = (pinnedServerCert == null) ? null : pinnedServerCert.getEncoded(); }
        catch (CertificateEncodingException e) {
            throw new IllegalArgumentException("Invalid pinned server certificate", e);
        }
        config.setPinnedServerCertificate(pin);

        return config;
    }

    protected final ReplicatorConfiguration makeConfig(
        Database source,
        Endpoint target,
        ReplicatorType type,
        boolean continuous) {
        return new ReplicatorConfiguration(source, target)
            .setType(type)
            .setContinuous(continuous)
            .setHeartbeat(AbstractReplicatorConfiguration.DISABLE_HEARTBEAT);
    }

    protected final Replicator run(ReplicatorConfiguration config) {
        return run(config, null);
    }

    protected final Replicator run(ReplicatorConfiguration config, Fn.Consumer<Replicator> onReady) {
        return run(config, 0, null, false, onReady);
    }

    protected final Replicator run(ReplicatorConfiguration config, boolean reset, Fn.Consumer<Replicator> onReady) {
        return run(config, 0, null, reset, onReady);
    }

    protected final Replicator run(
        ReplicatorConfiguration config,
        int expectedErrorCode,
        String expectedErrorDomain,
        boolean reset,
        Fn.Consumer<Replicator> onReady) {
        return run(
            testReplicator(config),
            expectedErrorCode,
            expectedErrorDomain,
            reset,
            onReady);
    }

    protected final Replicator run(Replicator repl) { return run(repl, 0, null, false, null); }

    protected final Replicator run(Replicator repl, int expectedErrorCode, String expectedErrorDomain) {
        return run(repl, expectedErrorCode, expectedErrorDomain, false, null);
    }

    private Replicator run(
        Replicator repl,
        int expectedErrorCode,
        String expectedErrorDomain,
        boolean reset,
        Fn.Consumer<Replicator> onReady) {
        baseTestReplicator = repl;

        TestReplicatorChangeListener listener = new TestReplicatorChangeListener();

        if (onReady != null) { onReady.accept(repl); }

        ListenerToken token = repl.addChangeListener(testSerialExecutor, listener);

        Report.log("Test replicator starting: " + repl.getConfig());
        boolean success;
        try {
            repl.start(reset);
            success = listener.awaitCompletion(STD_TIMEOUT_SEC, TimeUnit.SECONDS);
        }
        finally {
            repl.removeChangeListener(token);
        }

        Throwable err = listener.getError();
        Report.log(err, "Test replicator finished: " + success);

        if ((expectedErrorCode == 0) && (expectedErrorDomain == null)) {
            if (err != null) { throw new RuntimeException(err); }
        }
        else {
            assertNotNull(err);
            if (!(err instanceof CouchbaseLiteException)) { throw new RuntimeException(err); }
            final CouchbaseLiteException cblErr = (CouchbaseLiteException) err;
            if (expectedErrorCode != 0) { assertEquals(expectedErrorCode, cblErr.getCode()); }
            if (expectedErrorDomain != null) { assertEquals(expectedErrorDomain, cblErr.getDomain()); }
        }

        return repl;
    }
}
