//
// Copyright (c) 2020, 2019 Couchbase, Inc All rights reserved.
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

import java.io.File;
import java.util.concurrent.ThreadPoolExecutor;

import com.couchbase.lite.internal.exec.AbstractExecutionService;
import com.couchbase.lite.internal.utils.Fn;


/**
 * Contains methods required for the tests to run on both Android and Java platforms.
 */
public interface PlatformTest {
    class Exclusion {
        final String msg;
        final Fn.Provider<Boolean> test;

        Exclusion(@NonNull String msg, Fn.Provider<Boolean> test) {
            this.msg = msg;
            this.test = test;
        }
    }

    /* initialize the platform */
    void setupPlatform();

    /* get a scratch directory */
    File getTmpDir();

    /* Reload the cross-platform error messages. */
    void reloadStandardErrorMessages();

    /* Skip the test on some platforms */
    Exclusion getExclusions(@NonNull String tag);

    AbstractExecutionService getExecutionService(ThreadPoolExecutor executor);

    /* Schedule a task to be executed asynchronously. */
    void executeAsync(long delayMs, Runnable task);
}
