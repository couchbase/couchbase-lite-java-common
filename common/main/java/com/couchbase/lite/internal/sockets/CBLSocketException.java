//
// Copyright (c) 2022 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite.internal.sockets;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;


/**
 * Unchecked exception for fast failing in CBL network handling
 */
public class CBLSocketException extends RuntimeException {
    private final int domain;
    private final int code;

    public CBLSocketException(int domain, int code, @NonNull String message) { this(domain, code, message, null); }

    public CBLSocketException(
        int domain,
        int code,
        @NonNull String message,
        @Nullable Throwable cause) {
        super(message, cause);
        this.domain = domain;
        this.code = code;
    }

    public int getDomain() { return code; }
    public int getCode() { return code; }

    @NonNull
    public String toString() { return "[" + domain + ", " + code + "]: " + super.toString(); }
}
