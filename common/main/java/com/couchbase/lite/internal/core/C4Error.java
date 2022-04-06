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


public class C4Error {
    private final int domain;        // C4Error.domain
    private final int code;          // C4Error.code
    private final int info;  // C4Error.internal_info

    public C4Error() { this(0, 0, 0); }

    public C4Error(int domain, int code, int info) {
        this.domain = domain;
        this.code = code;
        this.info = info;
    }

    public int getDomain() { return domain; }

    public int getCode() { return code; }

    public int getInternalInfo() { return info; }

    @NonNull
    @Override
    public String toString() {
        return "C4Error{domain=" + domain + ", code=" + code + ", internalInfo=" + info + '}';
    }
}
