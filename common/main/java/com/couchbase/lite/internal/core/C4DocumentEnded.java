//
// Copyright (c) 2020, 2018 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite.internal.core;

import android.support.annotation.NonNull;


/**
 * WARNING!
 * This class and its members are referenced by name, from native code.
 */
public class C4DocumentEnded {
    @NonNull
    private final String docID;
    @NonNull
    private final String revID;
    private final int flags;
    private final long sequence;
    private final boolean errorIsTransient;
    private final int errorDomain;
    private final int errorCode;
    private final int errorInternalInfo;

    // Called from native code
    public C4DocumentEnded(
        @NonNull String docID,
        @NonNull String revID,
        int flags,
        long sequence,
        int errorDomain,
        int errorCode,
        int errorInternalInfo,
        boolean errorIsTransient) {
        this.docID = docID;
        this.revID = revID;
        this.flags = flags;
        this.sequence = sequence;
        this.errorDomain = errorDomain;
        this.errorCode = errorCode;
        this.errorInternalInfo = errorInternalInfo;
        this.errorIsTransient = errorIsTransient;
    }

    @NonNull
    public String getDocID() { return docID; }

    @NonNull
    public String getRevID() { return revID; }

    public int getFlags() { return flags; }

    public long getSequence() { return sequence; }

    public int getErrorDomain() { return errorDomain; }

    public int getErrorCode() { return errorCode; }

    public int getErrorInternalInfo() { return errorInternalInfo; }

    public boolean errorIsTransient() { return errorIsTransient; }

    @NonNull
    public C4Error getC4Error() { return new C4Error(errorDomain, errorCode, errorInternalInfo); }

    public boolean isConflicted() {
        return errorDomain == C4Constants.ErrorDomain.LITE_CORE
            && errorCode == C4Constants.LiteCoreError.CONFLICT;
    }

    @NonNull
    @Override
    public String toString() {
        return "C4DocumentEnded{id=" + docID
            + ",rev=" + revID
            + ",flags=" + flags
            + ",error=@" + errorDomain + "#" + errorCode + "(" + errorInternalInfo + "):" + errorIsTransient
            + "}";
    }
}
