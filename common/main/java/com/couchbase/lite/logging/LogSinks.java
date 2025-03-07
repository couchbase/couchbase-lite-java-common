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
package com.couchbase.lite.logging;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.logging.LogSinksImpl;
import com.couchbase.lite.internal.utils.Preconditions;


public interface LogSinks {
    /**
     * @return the singleton holder for the three log sinks.
     */
    @NonNull
    static LogSinks get() {
        CouchbaseLiteInternal.requireInit("Logging not initialized");
        return Preconditions.assertNotNull(LogSinksImpl.getLogSinks(), "loggers");
    }

    /**
     * The File Log Sink: a sink that writes log messages to the
     * Couchbase Lite Mobile File logger.
     */
    @Nullable
    FileLogSink getFile();
    void setFile(@Nullable FileLogSink newLogger);

    /**
     * The Console Log Sink: a sink that writes log messages to system console.
     */
    @Nullable
    ConsoleLogSink getConsole();
    void setConsole(@Nullable ConsoleLogSink newLogger);

    /**
     * The Custom Log Sink: a user-defined log sink that can forward log messages
     * to a custom destination.
     * <p>
     * Note that logging to the Custom Logger is asynchronous.
     * A logger may receive several log messages after it has been removed
     * or replaced as the current logger.
     */
    @Nullable
    BaseLogSink getCustom();
    void setCustom(@Nullable BaseLogSink newLogger);
}
