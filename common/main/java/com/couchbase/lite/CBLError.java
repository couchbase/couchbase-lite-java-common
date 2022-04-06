//
// Copyright (c) 2020 Couchbase, Inc.
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

    /**
     * The error type: roughly, where it originated.
     */
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
        /**
         * Internal assertion failure
         */
        public static final int ASSERTION_FAILED = C4Constants.LiteCoreError.ASSERTION_FAILED;

        /**
         * An unimplemented API call
         */
        public static final int UNIMPLEMENTED = C4Constants.LiteCoreError.UNIMPLEMENTED;

        /**
         * Unsupported encryption algorithm
         */
        public static final int UNSUPPORTED_ENCRYPTION = C4Constants.LiteCoreError.UNSUPPORTED_ENCRYPTION;

        /**
         * An was made to insert a document with an invalid revision ID
         * This is frequently caused by an invalid revision ID written directly into Sync Gateway via the REST API
         */
        public static final int BAD_REVISION_ID = C4Constants.LiteCoreError.BAD_REVISION_ID;

        /**
         * Revision contains corrupted/unreadable data
         */
        public static final int CORRUPT_REVISION_DATA = C4Constants.LiteCoreError.CORRUPT_REVISION_DATA;

        /**
         * Database/KeyStore is not open
         */
        public static final int NOT_OPEN = C4Constants.LiteCoreError.NOT_OPEN;

        /**
         * Document not found
         */
        public static final int NOT_FOUND = C4Constants.LiteCoreError.NOT_FOUND;

        /**
         * Document update conflict
         */
        public static final int CONFLICT = C4Constants.LiteCoreError.CONFLICT;

        /**
         * Invalid function parameter or struct value
         */
        public static final int INVALID_PARAMETER = C4Constants.LiteCoreError.INVALID_PARAMETER;

        /**
         * Internal unexpected C++ exception
         */
        public static final int UNEXPECTED_ERROR = C4Constants.LiteCoreError.UNEXPECTED_ERROR;

        /**
         * Database file can't be opened; may not exist
         */
        public static final int CANT_OPEN_FILE = C4Constants.LiteCoreError.CANT_OPEN_FILE;

        /**
         * File I/O error
         */
        public static final int IO_ERROR = C4Constants.LiteCoreError.IO_ERROR;

        /**
         * Memory allocation failed (out of memory?)
         */
        public static final int MEMORY_ERROR = C4Constants.LiteCoreError.MEMORY_ERROR;

        /**
         * File is not writeable
         */
        public static final int NOT_WRITABLE = C4Constants.LiteCoreError.NOT_WRITABLE;

        /**
         * Data is corrupted
         */
        public static final int CORRUPT_DATA = C4Constants.LiteCoreError.CORRUPT_DATA;

        /**
         * Database is busy / locked
         */
        public static final int BUSY = C4Constants.LiteCoreError.BUSY;

        /**
         * Function cannot be called while in a transaction
         */
        public static final int NOT_IN_TRANSACTION = C4Constants.LiteCoreError.NOT_IN_TRANSACTION;

        /**
         * Database can't be closed while a transaction is open
         */
        public static final int TRANSACTION_NOT_CLOSED = C4Constants.LiteCoreError.TRANSACTION_NOT_CLOSED;

        /**
         * Operation not supported on this database
         */
        public static final int UNSUPPORTED = C4Constants.LiteCoreError.UNSUPPORTED;

        public static final int NOT_A_DATABASE_FILE = C4Constants.LiteCoreError.NOT_A_DATABASE_FILE;

        /**
         * Database exists but not in the format/storage requested
         */
        public static final int WRONG_FORMAT = C4Constants.LiteCoreError.WRONG_FORMAT;

        /**
         * Encryption / Decryption error
         */
        public static final int CRYPTO = C4Constants.LiteCoreError.CRYPTO;

        /**
         * Invalid query
         */
        public static final int INVALID_QUERY = C4Constants.LiteCoreError.INVALID_QUERY;

        /**
         * No such index, or query requires a nonexistent index
         */
        public static final int MISSING_INDEX = C4Constants.LiteCoreError.MISSING_INDEX;

        /**
         * Unknown query param name, or param number out of range
         */
        public static final int INVALID_QUERY_PARAM = C4Constants.LiteCoreError.INVALID_QUERY_PARAM;

        /**
         * Unknown error from remote server
         */
        public static final int REMOTE_ERROR = C4Constants.LiteCoreError.REMOTE_ERROR;

        /**
         * Database file format is older than what I can open
         */
        public static final int DATABASE_TOO_OLD = C4Constants.LiteCoreError.DATABASE_TOO_OLD;

        /**
         * Database file format is newer than what I can open
         */
        public static final int DATABASE_TOO_NEW = C4Constants.LiteCoreError.DATABASE_TOO_NEW;

        /**
         * Invalid document ID
         */
        public static final int BAD_DOC_ID = C4Constants.LiteCoreError.BAD_DOC_ID;

        /**
         * Database can't be upgraded (might be unsupported dev version)
         */
        public static final int CANT_UPGRADE_DATABASE = C4Constants.LiteCoreError.CANT_UPGRADE_DATABASE;

        // --- Network status codes start here
        // Network error codes (higher level than POSIX, lower level than HTTP.)
        public static final int NETWORK_OFFSET = 5000;

        /**
         * DNS Lookup failed
         */
        public static final int DNS_FAILURE = NETWORK_OFFSET + C4Constants.NetworkError.DNS_FAILURE;

        /**
         * DNS server doesn't know the hostname
         */
        public static final int UNKNOWN_HOST = NETWORK_OFFSET + C4Constants.NetworkError.UNKNOWN_HOST;

        /**
         * Socket timeout during an operation
         */
        public static final int TIMEOUT = NETWORK_OFFSET + C4Constants.NetworkError.TIMEOUT;

        /**
         * The provided URL is not valid
         */
        public static final int INVALID_URL = NETWORK_OFFSET + C4Constants.NetworkError.INVALID_URL;

        /**
         * Too many HTTP redirects for the HTTP client to handle
         */
        public static final int TOO_MANY_REDIRECTS = NETWORK_OFFSET + C4Constants.NetworkError.TOO_MANY_REDIRECTS;

        /**
         * Failure during TLS handshake process
         */
        public static final int TLS_HANDSHAKE_FAILED = NETWORK_OFFSET + C4Constants.NetworkError.TLS_HANDSHAKE_FAILED;

        /**
         * The provided TLS certificate has expired
         */
        public static final int TLS_CERT_EXPIRED = NETWORK_OFFSET + C4Constants.NetworkError.TLS_CERT_EXPIRED;

        /**
         * Cert isn't trusted for other reason
         */
        public static final int TLS_CERT_UNTRUSTED = NETWORK_OFFSET + C4Constants.NetworkError.TLS_CERT_UNTRUSTED;

        /**
         * A required client certificate was not provided
         */
        public static final int TLS_CLIENT_CERT_REQUIRED = NETWORK_OFFSET + C4Constants.NetworkError.TLS_CLIENT_CERT_REQUIRED;

        /**
         * Client certificate was rejected by the server
         */
        public static final int TLS_CLIENT_CERT_REJECTED = NETWORK_OFFSET + C4Constants.NetworkError.TLS_CLIENT_CERT_REJECTED;

        /**
         * Self-signed cert, or unknown anchor cert
         */
        public static final int TLS_CERT_UNKNOWN_ROOT = NETWORK_OFFSET + C4Constants.NetworkError.TLS_CERT_UNKNOWN_ROOT;

        /**
         * The client was redirected to an invalid location by the server
         */
        public static final int INVALID_REDIRECT = NETWORK_OFFSET + C4Constants.NetworkError.INVALID_REDIRECT;

        // --- HTTP status codes start here
        public static final int HTTP_BASE = 10000;

        /**
         * Missing or incorrect user authentication
         */
        public static final int HTTP_AUTH_REQUIRED = HTTP_BASE + C4Constants.HttpError.AUTH_REQUIRED;

        /**
         * User doesn't have permission to access resource
         */
        public static final int HTTP_FORBIDDEN = HTTP_BASE + C4Constants.HttpError.FORBIDDEN;

        /**
         * Resource not found
         */
        public static final int HTTP_NOT_FOUND = HTTP_BASE + C4Constants.HttpError.NOT_FOUND;

        /**
         * Update conflict
         */
        public static final int HTTP_CONFLICT = HTTP_BASE + C4Constants.HttpError.CONFLICT;

        /**
         * HTTP proxy requires authentication
         */
        public static final int HTTP_PROXY_AUTH_REQUIRED = HTTP_BASE + C4Constants.HttpError.PROXY_AUTH_REQUIRED;

        /**
         * Data is too large to upload
         */
        public static final int HTTP_ENTITY_TOO_LARGE = HTTP_BASE + C4Constants.HttpError.ENTITY_TOO_LARGE;

        public static final int HTTP_IM_A_TEAPOT = HTTP_BASE + C4Constants.HttpError.IM_A_TEAPOT;

        /**
         * Something's wrong with the server
         */
        public static final int HTTP_INTERNAL_SERVER_ERROR = HTTP_BASE + C4Constants.HttpError.INTERNAL_SERVER_ERROR;

        /**
         * Unimplemented server functionality
         */
        public static final int HTTP_NOT_IMPLEMENTED = HTTP_BASE + C4Constants.HttpError.NOT_IMPLEMENTED;

        /**
         * Service is down temporarily
         */
        public static final int HTTP_SERVICE_UNAVAILABLE = HTTP_BASE + C4Constants.HttpError.SERVICE_UNAVAILABLE;

        // --- Web socket status codes start here
        /**
         * Not an error: This is the lower bound for WebSocket related errors
         */
        public static final int WEB_SOCKET_NORMAL_CLOSE = HTTP_BASE + C4Constants.WebSocketError.NORMAL;

        /**
         * Peer has to close, e.g. because host app is quitting
         */
        public static final int WEB_SOCKET_GOING_AWAY = HTTP_BASE + C4Constants.WebSocketError.GOING_AWAY;

        /**
         * Protocol violation: invalid framing data
         */
        public static final int WEB_SOCKET_PROTOCOL_ERROR = HTTP_BASE + C4Constants.WebSocketError.PROTOCOL_ERROR;

        /**
         * Message payload cannot be handled
         */
        public static final int WEB_SOCKET_DATA_ERROR = HTTP_BASE + C4Constants.WebSocketError.DATA_ERROR;

        /**
         * TCP socket closed unexpectedly
         */
        public static final int WEB_SOCKET_ABNORMAL_CLOSE = HTTP_BASE + C4Constants.WebSocketError.ABNORMAL_CLOSE;

        /**
         * Unparseable WebSocket message
         */
        public static final int WEB_SOCKET_BAD_MESSAGE_FORMAT = HTTP_BASE + C4Constants.WebSocketError.BAD_MESSAGE_FORMAT;

        /**
         * Message violated unspecified policy
         */
        public static final int WEB_SOCKET_POLICY_ERROR = HTTP_BASE + C4Constants.WebSocketError.POLICY_ERROR;

        /**
         * Message is too large for peer to handle
         */
        public static final int WEB_SOCKET_MESSAGE_TOO_BIG = HTTP_BASE + C4Constants.WebSocketError.MESSAGE_TO_BIG;

        /**
         * Peer doesn't provide a necessary extension
         */
        public static final int WEB_SOCKET_MISSING_EXTENSION = HTTP_BASE + C4Constants.WebSocketError.MISSING_EXTENSION;

        /**
         * Can't fulfill request due to "unexpected condition"
         */
        public static final int WEB_SOCKET_CANT_FULFILL = HTTP_BASE + C4Constants.WebSocketError.CANT_FULFILL;

        public static final int WEB_SOCKET_TLS_FAILURE = HTTP_BASE + C4Constants.WebSocketError.TLS_FAILURE;

        public static final int WEB_SOCKET_USER = HTTP_BASE + C4Constants.WebSocketError.USER;

        /**
         * Exceptions during P2P replication that are transient will be assigned this error code
         */
        public static final int WEB_SOCKET_CLOSE_USER_TRANSIENT = HTTP_BASE + C4Constants.WebSocketError.USER_TRANSIENT;

        /**
         * Exceptions during P2P replication that are permanent will be assigned this error code
         */
        public static final int WEB_SOCKET_CLOSE_USER_PERMANENT = HTTP_BASE + C4Constants.WebSocketError.USER_PERMANENT;
        // @formatter:on
    }
}
