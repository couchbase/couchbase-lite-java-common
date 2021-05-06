package com.couchbase.lite;

import android.support.annotation.NonNull;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.couchbase.lite.internal.utils.Report;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;


public class TestReplicatorChangeListener implements ReplicatorChangeListener {
    private final AtomicReference<Throwable> testFailureReason = new AtomicReference<>();
    private final CountDownLatch latch = new CountDownLatch(1);

    private final boolean continuous;
    private final String expectedErrDomain;
    private final int expectedErrCode;

    public TestReplicatorChangeListener(boolean continuous, String expectedErrDomain, int expectedErrCode) {
        this.expectedErrDomain = expectedErrDomain;
        this.expectedErrCode = expectedErrCode;
        this.continuous = continuous;
    }

    public Throwable getFailureReason() { return testFailureReason.get(); }

    public boolean awaitCompletion(long timeout, TimeUnit unit) {
        try { return latch.await(timeout, unit); }
        catch (InterruptedException ignore) { }
        return false;
    }

    @Override
    public void changed(@NonNull ReplicatorChange change) {
        Report.log(LogLevel.DEBUG, "Test replicator state change: " + change);
        final ReplicatorStatus status = change.getStatus();
        try {
            if (continuous) { checkContinuousStatus(status); }
            else { checkOneShotStatus(status); }
        }
        catch (RuntimeException | CouchbaseLiteException | AssertionError e) {
            testFailureReason.compareAndSet(null, e);
        }
    }

    private void checkOneShotStatus(ReplicatorStatus status) throws CouchbaseLiteException {
        final CouchbaseLiteException error = status.getError();
        final ReplicatorActivityLevel state = status.getActivityLevel();

        if (state != ReplicatorActivityLevel.STOPPED) { return; }

        try {
            if (expectedErrCode != 0) { verifyError(error); }
            else if (error != null) { throw error; }
        }
        finally {
            latch.countDown();
        }
    }


    private void checkContinuousStatus(ReplicatorStatus status) throws CouchbaseLiteException {
        final ReplicatorProgress progress = status.getProgress();
        final CouchbaseLiteException error = status.getError();
        final ReplicatorActivityLevel state = status.getActivityLevel();

        switch (state) {
            case OFFLINE:
                try {
                    if (expectedErrCode != 0) { verifyError(error); }
                    else {
                        // TBD
                    }
                }
                finally {
                    latch.countDown();
                }
            case IDLE:
                try {
                    assertEquals(status.getProgress().getTotal(), status.getProgress().getCompleted());
                    if (expectedErrCode != 0) { verifyError(error); }
                    else {
                        if (error != null) { throw error; }
                    }
                }
                finally {
                    latch.countDown();
                }
            default:
        }
    }

    private void verifyError(CouchbaseLiteException error) {
        assertNotNull(error);
        assertEquals(expectedErrCode, error.getCode());
        if (expectedErrDomain != null) { assertEquals(expectedErrDomain, error.getDomain()); }
    }
}

