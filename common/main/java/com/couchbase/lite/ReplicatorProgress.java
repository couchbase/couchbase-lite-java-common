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


/**
 * Progress of a replicator. If `total` is zero, the progress is indeterminate; otherwise,
 * dividing the two will produce a fraction that can be used to draw a progress bar.
 * The quotient is highly volatile and may be slightly inaccurate by the time it is returned.
 */
public final class ReplicatorProgress {
    //---------------------------------------------
    // member variables
    //---------------------------------------------

    // The number of completed changes processed.
    private final long completed;

    // The total number of changes to be processed.
    private final long total;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    ReplicatorProgress(long completed, long total) {
        this.completed = completed;
        this.total = total;
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    /**
     * The number of completed changes processed.
     */
    public long getCompleted() { return completed; }

    /**
     * The total number of changes to be processed.
     */
    public long getTotal() { return total; }

    @NonNull
    @Override
    public String toString() { return "Progress{" + "completed=" + completed + ", total=" + total + '}'; }
}
