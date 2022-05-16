package com.couchbase.lite;

import androidx.annotation.NonNull;

import java.util.concurrent.Executor;


public interface DatabaseChangeObservable {
    @NonNull
    ListenerToken addChangeListener(@NonNull DatabaseChangeListener listener);
    @NonNull
    ListenerToken addChangeListener(@NonNull Executor executor, @NonNull DatabaseChangeListener listener);
}
