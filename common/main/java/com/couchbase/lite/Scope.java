package com.couchbase.lite;

import androidx.annotation.NonNull;


public class Scope {
    public static final String DEFAULT_SCOPE_NAME = "_default";
    private final String defaultScopeName;

    public Scope() { defaultScopeName = DEFAULT_SCOPE_NAME; }

    @NonNull
    public String getDefaultScopeName() { return defaultScopeName; }
}
