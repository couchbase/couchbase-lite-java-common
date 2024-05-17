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
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.couchbase.lite.internal.CouchbaseLiteInternal;


/**
 * Holder for the three Couchbase Lite loggers:  console, file, and custom.
 */
public final class Log {
    // Singleton instance.
    private final ConsoleLogger consoleLogger = new ConsoleLogger();

    // Singleton instance.
    private final FileLogger fileLogger = new FileLogger();

    // Singleton instance.
    @Nullable
    private Logger customLogger;

    // The singleton instance is available from Database.log
    Log() { }

    /**
     * Gets the logger that writes to the system console
     *
     * @return The logger that writes to the system console
     */
    @NonNull
    public ConsoleLogger getConsole() {
        CouchbaseLiteInternal.requireInit("Console logging not initialized");
        return consoleLogger;
    }

    /**
     * Gets the logger that writes to log files
     *
     * @return The logger that writes to log files
     */
    @NonNull
    public FileLogger getFile() {
        CouchbaseLiteInternal.requireInit("File logging not initialized");
        return fileLogger;
    }

    /**
     * Gets the custom logger that was registered by the
     * application (if any)
     *
     * @return The custom logger that was registered by
     * the application, or null.
     */
    @Nullable
    public Logger getCustom() { return customLogger; }

    /**
     * Sets an application specific logging method
     *
     * @param customLogger A Logger implementation that will receive logging messages
     */
    public void setCustom(@Nullable Logger customLogger) { this.customLogger = customLogger; }

    // Damn singletons...
    @VisibleForTesting
    void reset() {
        consoleLogger.reset();
        fileLogger.reset(true);
        customLogger = null;
    }
}
