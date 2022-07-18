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
package com.couchbase.lite.internal.core;

import androidx.annotation.NonNull;


/**
 * WARNING!
 * This class and its members are referenced by name, from native code.
 */
public class C4DocumentEnded {
    public final long token;
    @NonNull
    public final String scope;
    @NonNull
    public final String collection;
    @NonNull
    public final String docId;
    @NonNull
    public final String revId;
    public final int flags;
    public final long sequence;
    public final boolean errorIsTransient;
    public final int errorDomain;
    public final int errorCode;
    public final int errorInternalInfo;

    // Called from native code
    @SuppressWarnings("PMD.ExcessiveParameterList")
    public C4DocumentEnded(
        long token,
        @NonNull String scope,
        @NonNull String collection,
        @NonNull String docId,
        @NonNull String revId,
        int flags,
        long sequence,
        int errorDomain,
        int errorCode,
        int errorInternalInfo,
        boolean errorIsTransient) {
        this.token = token;
        this.scope = scope;
        this.collection = collection;
        this.docId = docId;
        this.revId = revId;
        this.flags = flags;
        this.sequence = sequence;
        this.errorDomain = errorDomain;
        this.errorCode = errorCode;
        this.errorInternalInfo = errorInternalInfo;
        this.errorIsTransient = errorIsTransient;
    }

    @NonNull
    public C4Error getC4Error() { return new C4Error(errorDomain, errorCode, errorInternalInfo); }

    public boolean isConflicted() {
        return errorDomain == C4Constants.ErrorDomain.LITE_CORE
            && errorCode == C4Constants.LiteCoreError.CONFLICT;
    }

    @NonNull
    @Override
    public String toString() {
        return "C4DocumentEnded{"
            + token
            + ", " + docId
            + ", " + revId
            + ", 0x" + Integer.toHexString(flags)
            + " @" + errorDomain + "#" + errorCode + "(" + errorInternalInfo + "):" + errorIsTransient
            + "}";
    }
}
