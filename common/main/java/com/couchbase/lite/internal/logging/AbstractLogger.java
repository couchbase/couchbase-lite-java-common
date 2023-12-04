//
// Copyright (c) 2023 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite.internal.logging;

import androidx.annotation.NonNull;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.LogLevel;


public abstract class AbstractLogger {
    private final LogLevel logLevel;

    // Base constructor.  A Logger has its filter set for life
    protected AbstractLogger(@NonNull LogLevel level) { this.logLevel = level; }

    @NonNull
    public final LogLevel getLevel() { return logLevel; }

    protected abstract void writeLog(@NonNull LogLevel level, @NonNull LogDomain domain, @NonNull String message);

    protected final void log(@NonNull LogLevel level, @NonNull LogDomain domain, @NonNull String message) {
        if (logLevel.compareTo(level) <= 0) { writeLog(level, domain, message); }
    }
}
