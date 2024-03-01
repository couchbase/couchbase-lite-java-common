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

import java.util.Map;

import com.couchbase.lite.internal.core.C4;
import com.couchbase.lite.internal.core.C4Constants;
import com.couchbase.lite.internal.core.CBLVersion;
import com.couchbase.lite.internal.logging.Log;


/**
 * Misfortune: The little fox gets its tail wet.
 */
public final class CouchbaseLiteException extends Exception {

    @NonNull
    public static CouchbaseLiteException convertException(@Nullable LiteCoreException e) {
        return (e == null)
            ? new CouchbaseLiteException("Unknown LiteCore exception")
            : toCouchbaseLiteException(e.domain, e.code, null, e);
    }

    @NonNull
    public static CouchbaseLiteException convertException(@Nullable LiteCoreException e, @NonNull String msg) {
        return (e == null)
            ? new CouchbaseLiteException(msg)
            : toCouchbaseLiteException(e.domain, e.code, msg, e);
    }

    @NonNull
    public static CouchbaseLiteException toCouchbaseLiteException(int domain, int status, int info) {
        return ((domain == 0) || (status == 0))
            ? toCouchbaseLiteException(domain, status, null, null)
            : toCouchbaseLiteException(domain, status, C4.getMessage(domain, status, info), null);
    }

    @NonNull
    public static CouchbaseLiteException toCouchbaseLiteException(
        int domainCode,
        int statusCode,
        @Nullable String msg,
        @Nullable Exception e) {
        // log a LiteCoreException in case the client swallows it.
        if (e instanceof LiteCoreException) { Log.w(LogDomain.DATABASE, "Lite Core exception", e); }

        int code = statusCode;
        String domain = CBLError.Domain.CBLITE;
        switch (domainCode) {
            case C4Constants.ErrorDomain.LITE_CORE:
                break;
            case C4Constants.ErrorDomain.POSIX:
                domain = CBLError.Domain.POSIX;
                break;
            case C4Constants.ErrorDomain.SQLITE:
                domain = CBLError.Domain.SQLITE;
                break;
            case C4Constants.ErrorDomain.FLEECE:
                domain = CBLError.Domain.FLEECE;
                break;
            case C4Constants.ErrorDomain.NETWORK:
                code += CBLError.Code.NETWORK_OFFSET;
                break;
            case C4Constants.ErrorDomain.WEB_SOCKET:
                code += CBLError.Code.HTTP_BASE;
                break;
            default:
                Log.w(
                    LogDomain.DATABASE,
                    "Unable to map C4Error(%d,%d) to an CouchbaseLiteException",
                    domainCode,
                    statusCode);
                break;
        }

        return new CouchbaseLiteException(msg, e, domain, code, null);
    }

    static boolean isConflict(@Nullable CouchbaseLiteException err) {
        return (err != null)
            && CBLError.Domain.CBLITE.equals(err.getDomain())
            && (CBLError.Code.CONFLICT == err.getCode());
    }

    @NonNull
    static String getErrorMessage(@Nullable String msg, @Nullable Exception e) {
        String errMsg = msg;

        if ((msg == null) && (e != null)) { errMsg = e.getMessage(); }

        return Log.lookupStandardMessage(errMsg);
    }

    private final int code;
    @NonNull
    private final String domain;
    @Nullable
    private final Map<String, Object> info;

    /**
     * Constructs a new exception with the specified detail message.
     *
     * @param message the detail message.
     */
    public CouchbaseLiteException(@NonNull String message) { this(message, null, null, 0, null); }

    /**
     * Constructs a new exception with the specified cause
     *
     * @param cause the cause
     * @deprecated Must supply an error message
     */
    @Deprecated
    public CouchbaseLiteException(@NonNull Exception cause) { this(null, cause, null, 0, null); }

    /**
     * Constructs a new exception with the specified cause
     *
     * @param cause the cause
     */
    public CouchbaseLiteException(@NonNull String message, @NonNull Exception cause) {
        this(message, cause, null, 0, null);
    }

    /**
     * Constructs a new exception with the specified error domain and error code
     *
     * @param domain the error domain
     * @param code   the error code
     * @deprecated Must supply an error message
     */
    @Deprecated
    public CouchbaseLiteException(@NonNull String domain, int code) { this(null, null, domain, code, null); }

    /**
     * Constructs a new exception with the specified detail message, error domain and error code
     *
     * @param message the detail message
     * @param domain  the error domain
     * @param code    the error code
     */
    public CouchbaseLiteException(@NonNull String message, @NonNull String domain, int code) {
        this(message, null, domain, code, null);
    }

    /**
     * Constructs a new exception with the specified error domain, error code and the specified cause
     *
     * @param domain the error domain
     * @param code   the error code
     * @param cause  the cause
     * @deprecated Must supply an error message
     */
    @Deprecated
    public CouchbaseLiteException(@NonNull String domain, int code, @NonNull Exception cause) {
        this(null, cause, domain, code, null);
    }

    /**
     * Constructs a new exception with the specified error domain, error code and the specified cause
     *
     * @param domain the error domain
     * @param code   the error code
     * @param info   the internal info map
     * @deprecated Must supply an error message
     */
    @Deprecated
    public CouchbaseLiteException(@NonNull String domain, int code, @Nullable Map<String, Object> info) {
        this(null, null, domain, code, info);
    }

    /**
     * Constructs a new exception with the specified error domain, error code and the specified cause
     *
     * @param message the detail message
     * @param cause   the cause
     * @param domain  the error domain
     * @param code    the error code
     */
    public CouchbaseLiteException(@NonNull String message, @NonNull Exception cause, @NonNull String domain, int code) {
        this(message, cause, domain, code, null);
    }

    /**
     * This method is not part of the public API.
     * Do not use it.  It may change or disappear at any time.
     */
    public CouchbaseLiteException(
        @Nullable String message,
        @Nullable Exception cause,
        @Nullable String domain,
        int code,
        @Nullable Map<String, Object> info) {
        super(getErrorMessage(message, cause), cause);
        this.domain = (domain != null) ? domain : CBLError.Domain.CBLITE;
        this.code = (code > 0) ? code : CBLError.Code.UNEXPECTED_ERROR;
        this.info = info;
    }

    /**
     * Access the error domain for this error.
     *
     * @return The numerical domain code for this error.
     */
    @NonNull
    public String getDomain() { return domain; }

    /**
     * Access the error code for this error.
     *
     * @return The numerical error code for this error.
     */
    public int getCode() { return code; }

    @Nullable
    public Map<String, Object> getInfo() { return info; }

    @NonNull
    @Override
    public String getMessage() {
        return super.getMessage() + " (" + domain + ", " + code + ")" + "  [" + CBLVersion.getVersionInfo() + "]";
    }

    @NonNull
    @Override
    public String toString() { return "CouchbaseLiteException{" + domain + ", " + code + ": " + super.getMessage(); }

    @Override
    public int hashCode() { return (37 * domain.hashCode()) + code; }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (!(o instanceof CouchbaseLiteException)) { return false; }
        final CouchbaseLiteException e = (CouchbaseLiteException) o;
        return (code == e.code) && domain.equals(e.domain);
    }
}
