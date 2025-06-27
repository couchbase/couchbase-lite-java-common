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
package com.couchbase.lite.internal.sockets;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.couchbase.lite.internal.utils.ClassUtils;


public class CloseStatus {
    public final int domain;
    public final int code;

    @Nullable
    public final String message;

    public CloseStatus(int code, @Nullable String message) { this(0, code, message); }

    public CloseStatus(int domain, int code, @Nullable String message) {
        this.domain = domain;
        this.code = code;
        this.message = message;
    }

    @NonNull
    @Override
    public String toString() { return "CloseStatus{" + domain + ":" + code + ", " + message + "}"; }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (!(o instanceof CloseStatus)) { return false; }
        final CloseStatus that = (CloseStatus) o;
        return (domain == that.domain) && (code == that.code) && ClassUtils.isEqual(message, that.message);
    }

    @Override
    public int hashCode() { return ClassUtils.hash(domain, code, message); }
}
