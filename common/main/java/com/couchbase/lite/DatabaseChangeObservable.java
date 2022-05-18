package com.couchbase.lite;

import androidx.annotation.NonNull;

import java.util.concurrent.Executor;


public interface DatabaseChangeObservable {
    /**
     * Add an observer for database changes.
     *
     * @param listener the observer
     * @return a token that can be used to cancel the observer
     */
    @NonNull
    ListenerToken addChangeListener(@NonNull DatabaseChangeListener listener);

    /**
     * Add an observer for database changes.
     *
     * @param executor the executor on which the listener is run
     * @param listener the observer
     * @return a token that can be used to cancel the observer
     */
    @NonNull
    ListenerToken addChangeListener(@NonNull Executor executor, @NonNull DatabaseChangeListener listener);
}
