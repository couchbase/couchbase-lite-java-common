//
// Copyright (c) 2020, 2017 Couchbase, Inc All rights reserved.
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
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.util.concurrent.TimeUnit;

import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;

import com.couchbase.lite.internal.utils.Fn;
import com.couchbase.lite.internal.utils.Report;

import static com.couchbase.lite.AbstractReplicatorConfiguration.ReplicatorType;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


public abstract class BaseReplicatorTest extends BaseDbTest {
    protected static final long STD_TIMEOUT_SECS = 5;

    protected Replicator baseTestReplicator;

    protected Database otherDB;

    @Before
    public final void setUpBaseReplicatorTest() throws CouchbaseLiteException {
        otherDB = createDb("replicator-db");
        Report.log(LogLevel.INFO, "Create other DB: " + otherDB);
        assertNotNull(otherDB);
        assertTrue(otherDB.isOpen());
    }

    @After
    public final void tearDownBaseReplicatorTest() {
        Report.log(LogLevel.INFO, "Delete other DB: " + otherDB);
        deleteDb(otherDB);
    }

    // helper method allows kotlin to call isDocumentPending(null)
    // Kotlin type checking prevents this.
    @SuppressWarnings("ConstantConditions")
    protected final boolean callIsDocumentPendingWithNullId(Replicator repl) throws CouchbaseLiteException {
        return repl.isDocumentPending(null);
    }

    @NotNull
    protected final ReplicatorType getReplicatorType(boolean push, boolean pull) {
        return (push && pull)
            ? ReplicatorType.PUSH_AND_PULL
            : ((push)
                ? ReplicatorType.PUSH
                : ReplicatorType.PULL);
    }

    // Don't let the NetworkConnectivityManager confuse tests
    protected final Replicator newReplicator(ReplicatorConfiguration config) { return new Replicator(null, config); }

    protected final ReplicatorConfiguration makeConfig(
        boolean push,
        boolean pull,
        boolean continuous,
        Endpoint target) {
        return makeConfig(push, pull, continuous, target, null);
    }

    protected final ReplicatorConfiguration makeConfig(
        boolean push,
        boolean pull,
        boolean continuous,
        Endpoint target,
        Certificate pinnedServerCert) {
        return makeConfig(push, pull, continuous, baseTestDb, target, pinnedServerCert);
    }

    protected final ReplicatorConfiguration makeConfig(
        boolean push,
        boolean pull,
        boolean continuous,
        Database source,
        Endpoint target) {
        return makeConfig(push, pull, continuous, source, target, null, null);
    }

    protected final ReplicatorConfiguration makeConfig(
        boolean push,
        boolean pull,
        boolean continuous,
        Database source,
        Endpoint target,
        Certificate pinnedServerCert) {
        return makeConfig(push, pull, continuous, source, target, pinnedServerCert, null);
    }

    protected final ReplicatorConfiguration makeConfig(
        boolean push,
        boolean pull,
        boolean continuous,
        Database source,
        Endpoint target,
        Certificate pinnedServerCert,
        ConflictResolver resolver) {
        ReplicatorConfiguration config = new ReplicatorConfiguration(source, target);
        config.setReplicatorType(getReplicatorType(push, pull));
        config.setContinuous(continuous);

        final byte[] pin;
        try { pin = pinnedServerCert != null ? pinnedServerCert.getEncoded() : null; }
        catch (CertificateEncodingException e) {
            throw new IllegalArgumentException("Invalid pinned server certificate", e);
        }
        config.setPinnedServerCertificate(pin);

        if (resolver != null) { config.setConflictResolver(resolver); }
        return config;
    }

    protected final Replicator run(ReplicatorConfiguration config) throws CouchbaseLiteException {
        return run(config, null);
    }

    protected final Replicator run(ReplicatorConfiguration config, Fn.Consumer<Replicator> onReady)
        throws CouchbaseLiteException {
        return run(config, 0, null, false, false, onReady);
    }

    protected final Replicator run(URI url, boolean push, boolean pull, boolean continuous, Authenticator auth)
        throws CouchbaseLiteException {
        final ReplicatorConfiguration config = makeConfig(push, pull, continuous, new URLEndpoint(url));
        if (auth != null) { config.setAuthenticator(auth); }
        return run(config);
    }

    protected final Replicator run(
        URI url,
        boolean push,
        boolean pull,
        boolean continuous,
        Authenticator auth,
        Certificate pinnedServerCert)
        throws CouchbaseLiteException {
        final ReplicatorConfiguration config = makeConfig(
            push,
            pull,
            continuous,
            new URLEndpoint(url),
            pinnedServerCert);
        if (auth != null) { config.setAuthenticator(auth); }
        return run(config);
    }

    protected final Replicator run(
        int expectedErrorCode,
        String expectedErrorDomain,
        URI url,
        boolean push,
        boolean pull,
        boolean continuous,
        Authenticator auth,
        Certificate pinnedServerCert)
        throws CouchbaseLiteException {
        final ReplicatorConfiguration config = makeConfig(
            push,
            pull,
            continuous,
            new URLEndpoint(url),
            pinnedServerCert);
        if (auth != null) { config.setAuthenticator(auth); }
        return run(config, expectedErrorCode, expectedErrorDomain, false, false, null);
    }

    protected final Replicator run(
        ReplicatorConfiguration config,
        int expectedErrorCode,
        String expectedErrorDomain,
        boolean ignoreErrorAtStopped,
        boolean reset,
        Fn.Consumer<Replicator> onReady)
        throws CouchbaseLiteException {
        return run(
            newReplicator(config),
            expectedErrorCode,
            expectedErrorDomain,
            ignoreErrorAtStopped,
            reset,
            onReady);
    }

    protected final Replicator run(Replicator r) throws CouchbaseLiteException {
        return run(r, 0, null, false, false, null);
    }

    private Replicator run(
        Replicator r,
        int expectedErrorCode,
        String expectedErrorDomain,
        boolean ignoreErrorAtStopped,
        boolean reset,
        Fn.Consumer<Replicator> onReady)
        throws CouchbaseLiteException {
        baseTestReplicator = r;

        TestReplicatorChangeListener listener
            = new TestReplicatorChangeListener(r, expectedErrorDomain, expectedErrorCode, ignoreErrorAtStopped);

        if (onReady != null) { onReady.accept(r); }

        ListenerToken token = r.addChangeListener(testSerialExecutor, listener);

        boolean success;
        try {
            r.start(reset);
            success = listener.awaitCompletion(STD_TIMEOUT_SECS, TimeUnit.SECONDS);
        }
        finally {
            r.removeChangeListener(token);
        }

        // see if the replication succeeded
        Throwable err = listener.getFailureReason();
        if (err instanceof CouchbaseLiteException) { throw (CouchbaseLiteException) err; }
        if (err != null) { throw new RuntimeException(err); }

        assertTrue(success);

        return r;
    }
}
