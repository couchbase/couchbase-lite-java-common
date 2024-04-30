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


/**
 * Internal error in native code.
 */
// This class and its constructor are used by reflection.  Don't change it.
public class LiteCoreException extends Exception {

    // This method is used by reflection.  Don't change its signature.
    public static void throwException(int domain, int code, @Nullable String msg) throws LiteCoreException {
        throw new LiteCoreException(domain, code, msg);
    }


    // the values for domain are in CBLConstants.ErrorDomain
    public final int domain;
    // the values for code are in CBLConstants.{LiteCoreError,NetworkError,HttpError}
    public final int code;

    public LiteCoreException(int domain, int code, @Nullable String message) {
        super(message);
        this.domain = domain;
        this.code = code;
    }

    public int getDomain() { return domain; }

    public int getCode() { return code; }

    @NonNull
    @Override
    public String getMessage() { return super.getMessage() + " [" + domain + ", " + code + "]"; }
}
