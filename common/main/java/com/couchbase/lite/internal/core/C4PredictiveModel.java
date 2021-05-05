package com.couchbase.lite.internal.core;

/**
 * This is a simplified version of the C4PredictiveModel:
 * 1. No context object is passed to the callback predict() method.
 * 2. The predict() method does not return the error. Any error will be logged from
 * the Java code. If this causes confusion we could make the predict method return
 * a complex object that includes a C4Error.
 */
public interface C4PredictiveModel {
    /* FLSliceResult */
    long predict(
        /* FLDict */ long input,
        /* C4Database */ long c4db);
}

