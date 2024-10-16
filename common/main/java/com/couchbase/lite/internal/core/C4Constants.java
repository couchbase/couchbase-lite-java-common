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

import androidx.annotation.VisibleForTesting;


@SuppressWarnings({"unused", "LineLength"})
public final class C4Constants {
    private C4Constants() { }


    ////////////////////////////////////
    // c4Log.h
    ////////////////////////////////////

    // C4LogLevel
    public static final class LogLevel {
        private LogLevel() { }

        public static final int DEBUG = 0;
        public static final int VERBOSE = 1;
        public static final int INFO = 2;
        public static final int WARNING = 3;
        public static final int ERROR = 4;
        public static final int NONE = 5;
    }

    // C4LogDomain
    // Core creates these dynamically, so this is just a guess...
    public static final class LogDomain {
        private LogDomain() { }

        public static final String DEFAULT = "";
        public static final String ACTOR = "Actor";
        public static final String BLIP = "BLIP";
        public static final String BLIP_MESSAGES = "BLIPMessages";
        public static final String BLOB = "Blob";
        public static final String CHANGES = "Changes";
        public static final String DATABASE = "DB";
        public static final String ENUM = "Enum";
        public static final String LISTENER = "Listener";
        public static final String QUERY = "Query";
        public static final String SQL = "SQL";
        public static final String SYNC = "Sync";
        public static final String SYNC_BUSY = "SyncBusy";
        public static final String TLS = "TLS";
        public static final String WEB_SOCKET = "WS";
        public static final String ZIP = "Zip";
    }


    ////////////////////////////////////
    // 4DatabaseTypes.h
    ////////////////////////////////////

    // C4DatabaseFlags
    public static final class DatabaseFlags {
        private DatabaseFlags() { }
        // @formatter:off
        static final int CREATE = 0x01;                 // Create the file if it doesn't exist
        @VisibleForTesting
        static final int READ_ONLY = 0x02;              // Open file read-only
        private static final int AUTO_COMPACT = 0x04;   // Enable auto-compaction [UNIMPLEMENTED]
        static final int VERSION_VECTORS = 0x08;        // Upgrade DB to version vectors instead of rev trees [EXPERIMENTAL]
        public static final int DISABLE_MMAP = 0x10;    // Disable MMAP in SQLite.
        private static final int NO_UPGRADE = 0x20;     // Disable upgrading an older-version database
        private static final int NON_OBSERVABLE = 0x40; // Disable database/collection observers, for slightly faster writes
        @VisibleForTesting
        public static final int DISC_FULL_SYNC = 0x80;  // Flush to disk after each transaction
        @VisibleForTesting
        static final int FAKE_CLOCK = 0x100;            // Use counters instead of timestamps in version vectors (TESTS ONLY)
        // @formatter:on
    }

    // C4EncryptionAlgorithm
    public static final class EncryptionAlgorithm {
        private EncryptionAlgorithm() { }

        public static final int NONE = 0;      //< No encryption (default)
        public static final int AES256 = 1;    //< AES with 256-bit key
    }

    // C4EncryptionKeySize
    public static final class EncryptionKeySize {
        private EncryptionKeySize() { }

        public static final int AES256 = 32;
    }


    ////////////////////////////////////
    // c4DocumentTypes.h
    ////////////////////////////////////
    public static boolean hasFlags(int flags, int targetFlags) { return (flags & targetFlags) == targetFlags; }

    // C4DocumentFlags
    public static final class DocumentFlags {
        private DocumentFlags() { }

        public static final int DELETED = 0x01;         // The document's current revision is deleted.
        public static final int CONFLICTED = 0x02;      // The document is in conflict.
        public static final int HAS_ATTACHMENTS = 0x04; // One or more revisions have attachments.
        public static final int EXISTS = 0x1000;        // The document exists (i.e. has revisions.)
    }

    // C4RevisionFlags
    public static final class RevisionFlags {
        private RevisionFlags() { }

        public static final int DELETED = 0x01;         // Is this revision a deletion/tombstone?
        public static final int LEAF = 0x02;            // Is this revision a leaf (no children?)
        public static final int NEW = 0x04;             // Has this rev been inserted since decoding?
        public static final int HAS_ATTACHMENTS = 0x08; // Does this rev's body contain attachments?
        public static final int KEEP_BODY = 0x10;       // Revision's body should not be discarded when non-leaf
        public static final int IS_CONFLICT = 0x20;     // Unresolved conflicting revision; will never be current
        public static final int CLOSED = 0x40;          // Rev is the (deleted) end of a closed conflicting branch
        public static final int PURGED = 0x80;          // Revision is purged (this flag is never stored in the db)
    }


    ////////////////////////////////////
    // c4DocEnumeratorTypes.h
    ////////////////////////////////////

    // C4EnumeratorFlags
    public static final class EnumeratorFlags {
        private EnumeratorFlags() { }

        public static final int DESCENDING = 0x01;
        public static final int UNSORTED = 0x02;
        public static final int INCLUDE_DELETED = 0x08;
        public static final int INCLUDE_NON_CONFLICTED = 0x10;
        public static final int INCLUDE_BODIES = 0x20;
        public static final int INCLUDE_REV_HISTORY = 0x40;

        public static final int DEFAULT = INCLUDE_NON_CONFLICTED | INCLUDE_BODIES;
    }


    ////////////////////////////////////
    // c4IndexTypes.h
    ////////////////////////////////////

    // C4IndexType
    public static final class IndexType {
        private IndexType() { }

        public static final int VALUE = 0;      //< Regular index of property value
        public static final int FULL_TEXT = 1;  //< Full-text index
        public static final int ARRAY = 2;      //< Index of array values, for use with UNNEST
        public static final int PREDICTIVE = 3; //< Index of prediction() results (Enterprise Edition only)
    }


    ////////////////////////////////////
    // c4Error.h
    ////////////////////////////////////

    // C4ErrorDomain: the domains.
    public static final class ErrorDomain {
        private ErrorDomain() { }

        public static final int LITE_CORE = 1;    // Couchbase Lite Core error code (see below)
        public static final int POSIX = 2;        // errno (errno.h)
        public static final int SQLITE = 3;       // SQLite error; see "sqlite3.h"
        public static final int FLEECE = 4;       // Fleece error; see "FleeceException.h"
        public static final int NETWORK = 5;      // network error; see enum C4NetworkErrorCode, below
        public static final int WEB_SOCKET = 6;   // WebSocket close code (1000...1015) or HTTP error (300..599)
        public static final int MBED_TLS = 7;
        public static final int UNUSED = 8;
        public static final int MAX_ERROR_DOMAINS = UNUSED - 1;
    }

    // C4ErrorCode, in domain LITE_CORE
    public static final class LiteCoreError {
        private LiteCoreError() { }

        // @formatter:off
        public static final int SUCCESS = 0;                 // Couchbase Lite Core error code (see below)
        public static final int ASSERTION_FAILED = 1;        // Internal assertion failure
        public static final int UNIMPLEMENTED = 2;           // Oops, an unimplemented API call
        public static final int UNSUPPORTED_ENCRYPTION = 3;  // Unsupported encryption algorithm
        public static final int BAD_REVISION_ID = 4;         // Invalid revision ID syntax
        public static final int CORRUPT_REVISION_DATA = 5;   // Revision contains corrupted/unreadable data
        public static final int NOT_OPEN = 6;                // Database/KeyStore/index is not open
        public static final int NOT_FOUND = 7;               // Document not found
        public static final int CONFLICT = 8;                // Document update conflict
        public static final int INVALID_PARAMETER = 9;       // Invalid function parameter or struct value
        public static final int UNEXPECTED_ERROR = 10;       // Internal unexpected C++ exception
        public static final int CANT_OPEN_FILE = 11;         // Database file can't be opened; may not exist
        public static final int IO_ERROR = 12;               // File I/O error
        public static final int MEMORY_ERROR = 13;           // Memory allocation failed (out of memory?)
        public static final int NOT_WRITABLE = 14;           // File is not writable
        public static final int CORRUPT_DATA = 15;           // Data is corrupted
        public static final int BUSY = 16;                   // Database is busy/locked
        public static final int NOT_IN_TRANSACTION = 17;     // Function must be called while in a transaction
        public static final int TRANSACTION_NOT_CLOSED = 18; // Database can't be closed while a transaction is open
        public static final int UNSUPPORTED = 19;            // Operation not supported in this database
        public static final int NOT_A_DATABASE_FILE = 20;    // File is not a database, or encryption key is wrong
        public static final int WRONG_FORMAT = 21;           // Database exists but not in the format/storage requested
        public static final int CRYPTO = 22;                 // Encryption/decryption error
        public static final int INVALID_QUERY = 23;          // Invalid query
        public static final int MISSING_INDEX = 24;          // No such index, or query requires a nonexistent index
        public static final int INVALID_QUERY_PARAM = 25;    // Unknown query param name, or param number out of range
        public static final int REMOTE_ERROR = 26;           // Unknown error from remote server
        public static final int DATABASE_TOO_OLD = 27;       // Database file format is older than what I can open
        public static final int DATABASE_TOO_NEW = 28;       // Database file format is newer than what I can open
        public static final int BAD_DOC_ID = 29;             // Invalid document ID
        public static final int CANT_UPGRADE_DATABASE = 30;  // Database can't be upgraded (unsupported dev version?)
        public static final int DELTA_BASE_UNKNOWN = 31;     // Replicator can't apply delta: base revision body is missing
        public static final int CORRUPT_DELTA = 32;          // Replicator can't apply delta: delta data invalid
        public static final int UNUSED = 33;
        public static final int MAX_ERROR_CODES = UNUSED - 1;
        // @formatter:on
    }

    // C4NetworkErrorCode, in domain NETWORK
    // these codes are redundantly defined in WebSocketInterface.hh
    public static final class NetworkError {
        private NetworkError() { }

        // @formatter:off
        public static final int DNS_FAILURE = 1;               // DNS lookup failed
        public static final int UNKNOWN_HOST = 2;              // DNS server doesn't know the hostname [retryable]
        public static final int TIMEOUT = 3;                   // Connection timeout [ETIMEDOUT, retryable]
        public static final int INVALID_URL = 4;               // Invalid URL
        public static final int TOO_MANY_REDIRECTS = 5;        // HTTP redirect loop
        public static final int TLS_HANDSHAKE_FAILED = 6;      // TLS handshake failed, for reasons other than below
        public static final int TLS_CERT_EXPIRED = 7;          // Peer's cert has expired
        public static final int TLS_CERT_UNTRUSTED = 8;        // Peer's cert isn't trusted for other reason
        public static final int TLS_CLIENT_CERT_REQUIRED = 9;  // Peer (server) requires me to provide a (client) cert
        public static final int TLS_CLIENT_CERT_REJECTED = 10; // Peer says my cert is invalid or unauthorized
        public static final int TLS_CERT_UNKNOWN_ROOT = 11;    // Self-signed cert, or unknown anchor cert
        public static final int INVALID_REDIRECT = 12;         // Attempted redirect to invalid replication endpoint
        public static final int UNKNOWN = 13;                  // Unknown error
        public static final int TLS_CERT_REVOKED = 14;         // Peer's cert has been revoked
        public static final int TLS_CERT_NAME_MISMATCH = 15;   // Peer's cert's Common Name doesn't match hostname
        public static final int NETWORK_RESET = 16;            // The network subsystem was reset [ENETRESET, retryable]
        public static final int CONNECTION_ABORTED = 17;       // The connection was aborted by the OS [ECONNABORTED, retryable]
        public static final int CONNECTION_RESET = 18;         // The connection was reset by the other side [ECONNRESET, retryable]
        public static final int CONNECTION_REFUSED = 19;       // The other side refused the connection [ECONNREFUSED, retryable]
        public static final int NETWORK_DOWN = 20;             // The network subsystem is not functioning [ENETDOWN, retryable]
        public static final int NETWORK_UNREACHABLE = 21;      // There is no usable network at the moment [ENETUNREACH, retryable]
        public static final int NOT_CONNECTED = 22;            // The socket in question is no longer connected [ENOTCONN, retryable]
        public static final int HOST_DOWN = 23;                // The other side reports it is down [EHOSTDOWN, retryable]
        public static final int HOST_UNREACHABLE = 24;         // There is no network path to the host [EHOSTUNREACH, retryable]
        public static final int ADDRESS_NOT_AVAILABLE = 25;    // The address in question is already being used [EADDRNOTAVAIL, retryable]
        public static final int BROKEN_PIPE = 26;              // Broken pipe [EPIPE, retryable]
        public static final int UNKNOWN_INTERFACE = 27;        // The requested interface does not exist
        public static final int UNUSED = 28;
        // @formatter:on
    }


    ////////////////////////////////////
    // Externally defined
    ////////////////////////////////////

    // C4NetworkErrorCode, in domain WEB_SOCKET
    public static final class HttpError {
        private HttpError() { }

        public static final int STATUS_MIN = 100;
        public static final int SWITCH_PROTOCOL = 101;         // Switch from HTTP protocol (probably to web socket)
        public static final int MULTIPLE_CHOICE = 300;         // Request has more than one possible response
        public static final int AUTH_REQUIRED = 401;           // Missing or incorrect user authentication
        public static final int FORBIDDEN = 403;               // User doesn't have permission to access resource
        public static final int NOT_FOUND = 404;               // Resource not found
        public static final int CONFLICT = 409;                // Update conflict
        public static final int PROXY_AUTH_REQUIRED = 407;     // HTTP proxy requires authentication
        public static final int ENTITY_TOO_LARGE = 413;        // Data is too large to upload
        public static final int IM_A_TEAPOT = 418;             // HTCPCP/1.0 error (RFC 2324)
        public static final int INTERNAL_SERVER_ERROR = 500;   // Something's wrong with the server
        public static final int NOT_IMPLEMENTED = 501;         // Unimplemented server functionality
        public static final int SERVICE_UNAVAILABLE = 503;     // Service is down temporarily(?)
        public static final int STATUS_MAX = 600;
    }


    ////////////////////////////////////
    // WebSocketInterface.hh
    ////////////////////////////////////

    // CloseCode, in domain NETWORK
    // WebSocket close codes start at 1000, so that they cannot be confused with HTTP errors
    // (which are always less than 600)
    public static final class WebSocketError {
        private WebSocketError() { }

        public static final int NORMAL = 1000;
        public static final int GOING_AWAY = 1001;             // Peer has to close, e.g. host app is quitting
        public static final int PROTOCOL_ERROR = 1002;         // Protocol violation: invalid framing data
        public static final int DATA_ERROR = 1003;             // Message payload cannot be handled
        public static final int NO_CODE = 1005;                // Never sent, only received
        public static final int ABNORMAL_CLOSE = 1006;         // Never sent, only received
        public static final int BAD_MESSAGE_FORMAT = 1007;     // Unparsable message
        public static final int POLICY_ERROR = 1008;           // Catch-all failure
        public static final int MESSAGE_TO_BIG = 1009;         // Message too big
        public static final int MISSING_EXTENSION = 1010;      // Peer doesn't provide a necessary extension
        public static final int CANT_FULFILL = 1011;           // Can't fulfill request: "unexpected condition"
        public static final int TLS_FAILURE = 1015;            // Never sent, only received
        public static final int USER = 4000;                   // First unregistered code for free-form use
        public static final int USER_TRANSIENT = 4001;         // User-defined transient error
        public static final int USER_PERMANENT = 4002;         // User-defined permanent error
    }
}
