//
// Copyright (c) 2020, 2017 Couchbase, Inc All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License")
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

import com.couchbase.lite.internal.core.C4Constants;


@SuppressWarnings("LineLength")
public final class CBLError {
    private CBLError() {}

    // Error Domain
    public static final class Domain {
        private Domain() {}

        public static final String CBLITE = "CouchbaseLite";
        public static final String POSIX = "POSIXErrorDomain";
        public static final String SQLITE = "CouchbaseLite.SQLite";
        public static final String FLEECE = "CouchbaseLite.Fleece";
    }

    // Error Code
    public static final class Code {
        private Code() {}

        // @formatter:off
        public static final int ASSERTION_FAILED = C4Constants.LiteCoreError.ASSERTION_FAILED;
        public static final int UNIMPLEMENTED = C4Constants.LiteCoreError.UNIMPLEMENTED;
        public static final int UNSUPPORTED_ENCRYPTION = C4Constants.LiteCoreError.UNSUPPORTED_ENCRYPTION;
        public static final int BAD_REVISION_ID = C4Constants.LiteCoreError.BAD_REVISION_ID;
        public static final int CORRUPT_REVISION_DATA = C4Constants.LiteCoreError.CORRUPT_REVISION_DATA;
        public static final int NOT_OPEN = C4Constants.LiteCoreError.NOT_OPEN;
        public static final int NOT_FOUND = C4Constants.LiteCoreError.NOT_FOUND;
        public static final int CONFLICT = C4Constants.LiteCoreError.CONFLICT;
        public static final int INVALID_PARAMETER = C4Constants.LiteCoreError.INVALID_PARAMETER;
        public static final int UNEXPECTED_ERROR = C4Constants.LiteCoreError.UNEXPECTED_ERROR;
        public static final int CANT_OPEN_FILE = C4Constants.LiteCoreError.CANT_OPEN_FILE;
        public static final int IO_ERROR = C4Constants.LiteCoreError.IO_ERROR;
        public static final int MEMORY_ERROR = C4Constants.LiteCoreError.MEMORY_ERROR;
        public static final int NOT_WRITABLE = C4Constants.LiteCoreError.NOT_WRITABLE;
        public static final int CORRUPT_DATA = C4Constants.LiteCoreError.CORRUPT_DATA;
        public static final int BUSY = C4Constants.LiteCoreError.BUSY;
        public static final int NOT_IN_TRANSACTION = C4Constants.LiteCoreError.NOT_IN_TRANSACTION;
        public static final int TRANSACTION_NOT_CLOSED = C4Constants.LiteCoreError.TRANSACTION_NOT_CLOSED;
        public static final int UNSUPPORTED = C4Constants.LiteCoreError.UNSUPPORTED;
        public static final int NOT_A_DATABASE_FILE = C4Constants.LiteCoreError.NOT_A_DATABASE_FILE;
        public static final int WRONG_FORMAT = C4Constants.LiteCoreError.WRONG_FORMAT;
        public static final int CRYPTO = C4Constants.LiteCoreError.CRYPTO;
        public static final int INVALID_QUERY = C4Constants.LiteCoreError.INVALID_QUERY;
        public static final int MISSING_INDEX = C4Constants.LiteCoreError.MISSING_INDEX;
        public static final int INVALID_QUERY_PARAM = C4Constants.LiteCoreError.INVALID_QUERY_PARAM;
        public static final int REMOTE_ERROR = C4Constants.LiteCoreError.REMOTE_ERROR;
        public static final int DATABASE_TOO_OLD = C4Constants.LiteCoreError.DATABASE_TOO_OLD;
        public static final int DATABASE_TOO_NEW = C4Constants.LiteCoreError.DATABASE_TOO_NEW;
        public static final int BAD_DOC_ID = C4Constants.LiteCoreError.BAD_DOC_ID;
        public static final int CANT_UPGRADE_DATABASE = C4Constants.LiteCoreError.CANT_UPGRADE_DATABASE;

        // --- Network status codes start here
        // Network error codes (higher level than POSIX, lower level than HTTP.)
        public static final int NETWORK_OFFSET = 5000;
        public static final int DNS_FAILURE = NETWORK_OFFSET + C4Constants.NetworkError.DNS_FAILURE;
        public static final int UNKNOWN_HOST = NETWORK_OFFSET + C4Constants.NetworkError.UNKNOWN_HOST;
        public static final int TIMEOUT = NETWORK_OFFSET + C4Constants.NetworkError.TIMEOUT;
        public static final int INVALID_URL = NETWORK_OFFSET + C4Constants.NetworkError.INVALID_URL;
        public static final int TOO_MANY_REDIRECTS = NETWORK_OFFSET + C4Constants.NetworkError.TOO_MANY_REDIRECTS;
        public static final int TLS_HANDSHAKE_FAILED = NETWORK_OFFSET + C4Constants.NetworkError.TLS_HANDSHAKE_FAILED;
        public static final int TLS_CERT_EXPIRED = NETWORK_OFFSET + C4Constants.NetworkError.TLS_CERT_EXPIRED;
        public static final int TLS_CERT_UNTRUSTED = NETWORK_OFFSET + C4Constants.NetworkError.TLS_CERT_UNTRUSTED;
        public static final int TLS_CLIENT_CERT_REQUIRED = NETWORK_OFFSET + C4Constants.NetworkError.TLS_CLIENT_CERT_REQUIRED;
        public static final int TLS_CLIENT_CERT_REJECTED = NETWORK_OFFSET + C4Constants.NetworkError.TLS_CLIENT_CERT_REJECTED;
        public static final int TLS_CERT_UNKNOWN_ROOT = NETWORK_OFFSET + C4Constants.NetworkError.TLS_CERT_UNKNOWN_ROOT;
        public static final int INVALID_REDIRECT = NETWORK_OFFSET + C4Constants.NetworkError.INVALID_REDIRECT;

        // --- HTTP status codes start here
        public static final int HTTP_BASE = 10000;
        public static final int HTTP_AUTH_REQUIRED = HTTP_BASE + C4Constants.HttpError.AUTH_REQUIRED;
        public static final int HTTP_FORBIDDEN = HTTP_BASE + C4Constants.HttpError.FORBIDDEN;
        public static final int HTTP_NOT_FOUND = HTTP_BASE + C4Constants.HttpError.NOT_FOUND;
        public static final int HTTP_CONFLICT = HTTP_BASE + C4Constants.HttpError.CONFLICT;
        public static final int HTTP_PROXY_AUTH_REQUIRED = HTTP_BASE + C4Constants.HttpError.PROXY_AUTH_REQUIRED;
        public static final int HTTP_ENTITY_TOO_LARGE = HTTP_BASE + C4Constants.HttpError.ENTITY_TOO_LARGE;
        public static final int HTTP_IM_A_TEAPOT = HTTP_BASE + C4Constants.HttpError.IM_A_TEAPOT;
        public static final int HTTP_INTERNAL_SERVER_ERROR = HTTP_BASE + C4Constants.HttpError.INTERNAL_SERVER_ERROR;
        public static final int HTTP_NOT_IMPLEMENTED = HTTP_BASE + C4Constants.HttpError.NOT_IMPLEMENTED;
        public static final int HTTP_SERVICE_UNAVAILABLE = HTTP_BASE + C4Constants.HttpError.SERVICE_UNAVAILABLE;

        // --- Web socket status codes start here
        public static final int WEB_SOCKET_NORMAL_CLOSE = HTTP_BASE + C4Constants.WebSocketError.NORMAL;
        public static final int WEB_SOCKET_GOING_AWAY = HTTP_BASE + C4Constants.WebSocketError.GOING_AWAY;
        public static final int WEB_SOCKET_PROTOCOL_ERROR = HTTP_BASE + C4Constants.WebSocketError.PROTOCOL_ERROR;
        public static final int WEB_SOCKET_DATA_ERROR = HTTP_BASE + C4Constants.WebSocketError.DATA_ERROR;
        public static final int WEB_SOCKET_ABNORMAL_CLOSE = HTTP_BASE + C4Constants.WebSocketError.ABNORMAL_CLOSE;
        public static final int WEB_SOCKET_BAD_MESSAGE_FORMAT = HTTP_BASE + C4Constants.WebSocketError.BAD_MESSAGE_FORMAT;
        public static final int WEB_SOCKET_POLICY_ERROR = HTTP_BASE + C4Constants.WebSocketError.POLICY_ERROR;
        public static final int WEB_SOCKET_MESSAGE_TOO_BIG = HTTP_BASE + C4Constants.WebSocketError.MESSAGE_TO_BIG;
        public static final int WEB_SOCKET_MISSING_EXTENSION = HTTP_BASE + C4Constants.WebSocketError.MISSING_EXTENSION;
        public static final int WEB_SOCKET_CANT_FULFILL = HTTP_BASE + C4Constants.WebSocketError.CANT_FULFILL;
        public static final int WEB_SOCKET_TLS_FAILUIRE = HTTP_BASE + C4Constants.WebSocketError.TLS_FAILURE;
        public static final int WEB_SOCKET_USER = HTTP_BASE + C4Constants.WebSocketError.USER;
        public static final int WEB_SOCKET_CLOSE_USER_TRANSIENT = HTTP_BASE + C4Constants.WebSocketError.USER_TRANSIENT;
        public static final int WEB_SOCKET_CLOSE_USER_PERMANENT = HTTP_BASE + C4Constants.WebSocketError.USER_PERMANENT;
        // @formatter:on
    }
}
