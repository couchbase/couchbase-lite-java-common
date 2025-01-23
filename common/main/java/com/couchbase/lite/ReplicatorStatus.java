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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.couchbase.lite.internal.core.C4ReplicatorStatus;
import com.couchbase.lite.internal.logging.Log;
import com.couchbase.lite.internal.utils.MathUtils;


/**
 * The activity level and progress of a replicator.
 */
public final class ReplicatorStatus {
    static final ReplicatorStatus INIT
        = new ReplicatorStatus(ReplicatorActivityLevel.STOPPED, new ReplicatorProgress(0, 0), null);

    @NonNull
    private static final Map<Integer, ReplicatorActivityLevel> ACTIVITY_LEVEL_FROM_C4;
    static {
        final Map<Integer, ReplicatorActivityLevel> m = new HashMap<>();
        m.put(C4ReplicatorStatus.ActivityLevel.STOPPED, ReplicatorActivityLevel.STOPPED);
        m.put(C4ReplicatorStatus.ActivityLevel.OFFLINE, ReplicatorActivityLevel.OFFLINE);
        m.put(C4ReplicatorStatus.ActivityLevel.CONNECTING, ReplicatorActivityLevel.CONNECTING);
        m.put(C4ReplicatorStatus.ActivityLevel.IDLE, ReplicatorActivityLevel.IDLE);
        m.put(C4ReplicatorStatus.ActivityLevel.BUSY, ReplicatorActivityLevel.BUSY);
        ACTIVITY_LEVEL_FROM_C4 = Collections.unmodifiableMap(m);
    }
    @NonNull
    private static ReplicatorActivityLevel getActivityLevelFromC4(int c4ActivityLevel) {
        final ReplicatorActivityLevel level = ACTIVITY_LEVEL_FROM_C4.get(c4ActivityLevel);
        if (level != null) { return level; }

        Log.w(LogDomain.REPLICATOR, "Unrecognized replicator activity level: " + c4ActivityLevel);

        // Per @Pasin, unrecognized states report as busy.
        return ReplicatorActivityLevel.BUSY;
    }

    @Nullable
    private static CouchbaseLiteException convertC4StatusError(@NonNull C4ReplicatorStatus c4Status) {
        final int errorCode = c4Status.getErrorCode();
        return (errorCode == 0)
            ? null
            : CouchbaseLiteException.toCouchbaseLiteException(
                c4Status.getErrorDomain(),
                errorCode,
                c4Status.getErrorInternalInfo());
    }

    //---------------------------------------------
    // member variables
    //---------------------------------------------

    @NonNull
    private final ReplicatorActivityLevel activityLevel;
    @NonNull
    private final ReplicatorProgress progress;
    @Nullable
    private final CouchbaseLiteException error;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    ReplicatorStatus(@NonNull C4ReplicatorStatus c4Status) {
        this(
            getActivityLevelFromC4(c4Status.getActivityLevel()),
            new ReplicatorProgress(
                MathUtils.asUnsignedInt(c4Status.getProgressUnitsCompleted()),
                MathUtils.asUnsignedInt(c4Status.getProgressUnitsTotal())),
            convertC4StatusError(c4Status));
    }

    ReplicatorStatus(@NonNull ReplicatorStatus status) {
        this(
            status.activityLevel,
            status.progress,
            status.error);
    }

    ReplicatorStatus(
        @NonNull ReplicatorActivityLevel activityLevel,
        @NonNull ReplicatorProgress progress,
        @Nullable CouchbaseLiteException error) {
        this.activityLevel = activityLevel;
        this.progress = progress;
        this.error = error;
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    /**
     * The current activity level.
     */
    @NonNull
    public ReplicatorActivityLevel getActivityLevel() { return activityLevel; }

    /**
     * The current progress of the replicator.
     */
    @NonNull
    public ReplicatorProgress getProgress() { return progress; }

    @Nullable
    public CouchbaseLiteException getError() { return error; }

    @NonNull
    @Override
    public String toString() {
        return "Status{" + "activityLevel=" + activityLevel + ", progress=" + progress + ", error=" + error + '}';
    }
}
