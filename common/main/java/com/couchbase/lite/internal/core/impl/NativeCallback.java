package com.couchbase.lite.internal.core.impl;

// This class is created via reflection (see native_callback.cc) and not by Java.
// So don't delete it, and be careful changing it.
final class NativeCallback implements Runnable {
    private long callbackHandle;

    private NativeCallback(long callbackHandle) {
        this.callbackHandle = callbackHandle;
    }

    // This MUST be called at least once, and will only do anything on the
    // first call.  Failure to call this is a native memory leak.  So be
    // mindful when you use it
    public void run() {
        if (callbackHandle == 0) {
            return;
        }

        nativeRunCallback(callbackHandle);
        callbackHandle = 0;
    }

    // This cannot be called more than once since it deletes the native
    // memory when it is done.
    private static native void nativeRunCallback(long callbackHandle);
}
