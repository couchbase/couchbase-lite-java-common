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

import androidx.annotation.NonNull;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;

import com.couchbase.lite.internal.replicator.ReplicationStatusChange;
import com.couchbase.lite.internal.utils.Fn;
import com.couchbase.lite.internal.utils.Report;
import com.couchbase.lite.mock.TestReplicatorChangeListener;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


class CompletionAwaiter {
    private final AtomicReference<Throwable> err = new AtomicReference<>(null);
    private final CountDownLatch latch = new CountDownLatch(1);
    private final ListenerToken token;

    public CompletionAwaiter(ListenerToken token) { this.token = token; }

    public void changed(ReplicationStatusChange change) {
        final ReplicatorStatus status = change.getStatus();

        final CouchbaseLiteException e = status.getError();
        if (e != null) { err.compareAndSet(null, e); }

        final ReplicatorActivityLevel level = status.getActivityLevel();
        if (!((level == ReplicatorActivityLevel.STOPPED) || (level == ReplicatorActivityLevel.OFFLINE))) { return; }

        latch.countDown();
    }

    public void awaitAndValidate() {
        try {
            final boolean ok = latch.await(BaseTest.LONG_TIMEOUT_SEC, TimeUnit.SECONDS);
            final Throwable e = err.get();
            if (e != null) { throw new IllegalStateException(e); }
            assertTrue("timeout", ok);
        }
        catch (InterruptedException ignore) { }
        finally {
            token.remove();
        }
    }
}

public abstract class BaseReplicatorTest extends BaseDbTest {

    // Don't let the NetworkConnectivityManager confuse tests
    public static Replicator testReplicator(ReplicatorConfiguration config) { return new Replicator(null, config); }


    protected Replicator baseTestReplicator;
    protected Database otherDB;

    @Before
    public final void setUpBaseReplicatorTest() throws CouchbaseLiteException { otherDB = createDb("replicator_db"); }

    @After
    public final void tearDownBaseReplicatorTest() { deleteDb(otherDB); }

    protected final URLEndpoint getRemoteTargetEndpoint() throws URISyntaxException {
        return new URLEndpoint(new URI("ws://foo.couchbase.com/db"));
    }

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
        Endpoint target,
        Set<Collection> source,
        ReplicatorType type,
        boolean continuous,
        Certificate pinnedServerCert) {
        final ReplicatorConfiguration config = makeConfig(target, source, type, continuous);

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

    protected final ReplicatorConfiguration makeConfig(
        Endpoint target,
        Set<Collection> source,
        ReplicatorType type,
        boolean continuous) {
        return new ReplicatorConfiguration(target)
            .addCollections(source, null)
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
        boolean ok;
        try {
            Report.log("Test replicator starting: %s", repl.getConfig());
            repl.start(reset);
            ok = listener.awaitCompletion(STD_TIMEOUT_SEC, TimeUnit.SECONDS);
        }
        finally {
            token.remove();
        }

        Throwable err = listener.getError();
        Report.log(err, "Test replicator finished %ssuccessfully", (ok) ? "" : "un");

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
