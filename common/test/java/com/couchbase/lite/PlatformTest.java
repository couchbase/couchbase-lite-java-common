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

/**
 * Contains methods required for the tests to run on both Android and Java platforms.
 */
public interface PlatformTest {

    /* initialize the platform */
    void setupPlatform();

    /* Reload the cross-platform error messages. */
    void reloadStandardErrorMessages();

    /* Terminate the test with prejudice, on this platform */
    boolean handlePlatformSpecially(String tag);

    /* Scheduled to execute a task asynchronously. */
    void executeAsync(long delayMs, Runnable task);
}
