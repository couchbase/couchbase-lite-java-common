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
import androidx.annotation.Nullable;


/**
 * WARNING!
 * This class and its members are referenced by name, from native code.
 */
public final class C4DocumentEnded {
    public final long token;
    @Nullable
    public final String scope;
    @Nullable
    public final String collection;
    @Nullable
    public final String docId;
    @Nullable
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
        @Nullable String scope,
        @Nullable String collection,
        @Nullable String docId,
        @Nullable String revId,
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

    public int getErrorDomain() { return errorDomain; }

    public int getErrorCode() { return errorCode; }

    public int getErrorInfo() { return errorInternalInfo; }

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
