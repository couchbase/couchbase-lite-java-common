//
// Copyright (c) 2023 Couchbase, Inc All rights reserved.
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

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import com.couchbase.lite.CBLError;
import com.couchbase.lite.CouchbaseLiteError;
import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.core.peers.LockManager;
import com.couchbase.lite.internal.fleece.FLSliceResult;


// It is worth considering breaking this up.  You know, OOP and all...
public class C4TestUtils {
    public static class C4IndexOptions {
        @Nullable
        public final String language;
        @Nullable
        public final String stopWords;
        @Nullable
        public final String unnestPath;
        public final boolean ignoreDiacritics;
        public final boolean disableStemming;

        C4IndexOptions(
            @Nullable String language,
            boolean ignoreDiacritics,
            boolean disableStemming,
            @Nullable String stopWords,
            @Nullable String unnestPath) {
            this.language = language;
            this.ignoreDiacritics = ignoreDiacritics;
            this.disableStemming = disableStemming;
            this.stopWords = stopWords;
            this.unnestPath = unnestPath;
        }
    }

    public static class C4FullTextMatch extends C4NativePeer {
        private static final long MOCK_PEER = 0x0cab00d1eL;

        /**
         * Return an array of details of each full-text match
         */
        @NonNull
        public static C4FullTextMatch getFullTextMatches(@NonNull C4QueryEnumerator queryEnumerator, int idx) {
            return new C4FullTextMatch(getFullTextMatch(queryEnumerator.getPeer(), idx));
        }

        public static long getMatchCount(C4QueryEnumerator queryEnumerator) {
            return getFullTextMatchCount(queryEnumerator.getPeer());
        }


        private long dataSource;
        private long property;
        private long term;
        private long start;
        private long length;

        //-------------------------------------------------------------------------
        // Constructors
        //-------------------------------------------------------------------------

        C4FullTextMatch(long peer) { super(peer); }

        C4FullTextMatch(long dataSource, long property, long term, long start, long length) {
            super(MOCK_PEER);
            this.dataSource = dataSource;
            this.property = property;
            this.term = term;
            this.start = start;
            this.length = length;
        }

        @Nullable
        public C4FullTextMatch load() {
            withPeer(peer -> {
                if (peer == MOCK_PEER) { return; }
                this.dataSource = dataSource(peer);
                this.property = property(peer);
                this.term = term(peer);
                this.start = start(peer);
                this.length = length(peer);
            });
            return this;
        }

        @Override
        public void close() { }

        @Override
        public int hashCode() { return Objects.hash(dataSource, property, term, start, length); }

        @Override
        public boolean equals(Object o) {
            if (this == o) { return true; }
            if (!(o instanceof C4FullTextMatch)) { return false; }
            final C4FullTextMatch match = (C4FullTextMatch) o;
            return (dataSource == match.dataSource)
                && (property == match.property)
                && (term == match.term)
                && (start == match.start)
                && (length == match.length);
        }
    }

    public static class C4DocEnumerator extends C4NativePeer {
        private final Object dbLock;

        public C4DocEnumerator(long db, int flags) throws LiteCoreException {
            super(enumerateAllDocs(db, flags));
            this.dbLock = LockManager.INSTANCE.getLock(db);
        }

        @NonNull
        public C4Document getDocument() throws LiteCoreException {
            return new C4Document(withPeerOrThrow(C4TestUtils::getDocument), dbLock);
        }

        public boolean next() throws LiteCoreException {
            return withPeerOrDefault(false, C4TestUtils::next);
        }

        @Override
        public void close() { closePeer(null); }

        @SuppressWarnings("NoFinalizer")
        @Override
        protected void finalize() throws Throwable {
            try { closePeer(LogDomain.DATABASE); }
            finally { super.finalize(); }
        }

        private void closePeer(@Nullable LogDomain domain) { releasePeer(domain, C4TestUtils::free); }
    }

    // managed: Java code is responsible for deleting and freeing it
    static final class ManagedC4BlobStore extends C4BlobStore {
        ManagedC4BlobStore(@NonNull String dirPath) throws LiteCoreException {
            super(
                C4BlobStore.NATIVE_IMPL,
                openStore(dirPath, C4Constants.DatabaseFlags.CREATE),
                C4TestUtils::deleteBlobStore);
        }
    }

    // C4DocEnumerator

    public static C4DocEnumerator enumerateDocsForCollection(C4Collection coll, int flags) throws LiteCoreException {
        return coll.withPeerOrThrow(peer -> new C4DocEnumerator(peer, flags));
    }

    // C4Blob

    /**
     * Returns the exact length in bytes of the stream.
     */
    public static long getBlobLength(@NonNull C4BlobReadStream stream) throws LiteCoreException {
        return stream.withPeerOrDefault(0L, C4TestUtils::getBlobLength);
    }

    // C4BlobStore

    public static void deleteBlobStore(long peer) {
        try { deleteStore(peer); }
        catch (LiteCoreException e) {
            try { freeStore(peer); }
            catch (Exception ignore) { }
            throw new CouchbaseLiteError("Failed deleting blob store", e);
        }
    }

    // C4Database

    @NonNull
    public static byte[] privateUUIDForDb(@NonNull C4Database db) throws LiteCoreException {
        return db.withPeerOrThrow(C4TestUtils::getPrivateUUID);
    }

    @NonNull
    public static FLSliceResult encodeJSONInDb(@NonNull C4Database db, @NonNull String data) throws LiteCoreException {
        final byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
        return db.withPeerOrThrow(peer -> encodeJSON(peer, dataBytes));
    }

    @Nullable
    public static String idForDoc(@NonNull C4Document doc) { return doc.withPeerOrNull(C4TestUtils::getDocID); }

    public static long getFlags(@NonNull C4Database db) throws LiteCoreException {
        return db.withPeerOrThrow(C4TestUtils::getFlags);
    }

    // C4Document

    @SuppressWarnings("PMD.ExcessiveParameterList")
    @NonNull
    public static C4Document create(
        @NonNull C4Collection collection,
        @Nullable byte[] body,
        @NonNull String docID,
        int revFlags,
        boolean existingRevision,
        boolean allowConflict,
        @NonNull String[] history,
        boolean save,
        int maxRevTreeDepth,
        int remoteDBID)
        throws LiteCoreException {
        return collection.withPeerOrThrow(
            peer -> new C4Document(
                put(
                    peer,
                    body,
                    docID,
                    revFlags,
                    existingRevision,
                    allowConflict,
                    history,
                    save,
                    maxRevTreeDepth,
                    remoteDBID),
                collection.getDbLock()));
    }

    @SuppressWarnings("PMD.ExcessiveParameterList")
    @NonNull
    public static C4Document create(
        @NonNull C4Collection collection,
        @NonNull FLSliceResult body, // C4Slice*
        @NonNull String docID,
        int revFlags,
        boolean existingRevision,
        boolean allowConflict,
        @NonNull String[] history,
        boolean save,
        int maxRevTreeDepth,
        int remoteDBID)
        throws LiteCoreException {
        return collection.withPeerOrThrow(peer ->
            new C4Document(
                put2(
                    peer,
                    body.getBase(),
                    body.getSize(),
                    docID,
                    revFlags,
                    existingRevision,
                    allowConflict,
                    history,
                    save,
                    maxRevTreeDepth,
                    remoteDBID),
                collection.getDbLock()));
    }

    // C4Key

    @NonNull
    public static byte[] getCoreKey(@NonNull String password) throws CouchbaseLiteException {
        final byte[] key = deriveKeyFromPassword(password);
        if (key != null) { return key; }

        throw new CouchbaseLiteException("Could not generate key", CBLError.Domain.CBLITE, CBLError.Code.CRYPTO);
    }

    // C4Log

    public static int getLogLevel(String domain) { return getLevel(domain); }

    // C4Index

    public static boolean isIndexTrained(C4Collection collection, @NonNull String name) throws LiteCoreException {
        return collection.withPeerOrThrow(peer -> isIndexTrained(peer, name));
    }

    @NonNull
    public static C4IndexOptions getIndexOptions(@NonNull C4Index idx) throws CouchbaseLiteException {
        return idx.withPeerOrThrow(C4TestUtils::getIndexOptions);
    }

    // This method is called by reflection.  Don't change its signature.
    public static C4IndexOptions createIndexOptions(
        boolean ignoreDiacritics,
        boolean disableStemming,
        @Nullable String language,
        @Nullable String stopWords,
        @Nullable String unnestPath) {
        return new C4IndexOptions(language, ignoreDiacritics, disableStemming, stopWords, unnestPath);
    }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    // C4FullTextMatch

    private static native long dataSource(long peer);

    private static native long property(long peer);

    private static native long term(long peer);

    private static native long start(long peer);

    private static native long length(long peer);

    private static native long getFullTextMatchCount(long peer);

    private static native long getFullTextMatch(long peer, int idx);

    // C4DocEnumerator

    private static native long enumerateAllDocs(long db, int flags) throws LiteCoreException;

    private static native boolean next(long peer) throws LiteCoreException;

    private static native long getDocument(long peer) throws LiteCoreException;

    private static native void free(long peer);

    // C4Blob

    @GuardedBy("streamLock")
    private static native long getBlobLength(long peer) throws LiteCoreException;

    // C4BlobStore

    private static native long openStore(String dirPath, long flags) throws LiteCoreException;

    private static native void deleteStore(long peer) throws LiteCoreException;

    private static native void freeStore(long peer);

    // C4Database

    @NonNull
    private static native byte[] getPrivateUUID(long db) throws LiteCoreException;

    @NonNull
    private static native FLSliceResult encodeJSON(long db, @NonNull byte[] jsonData) throws LiteCoreException;

    @Nullable
    private static native String getDocID(long doc);

    private static native long getFlags(long db) throws LiteCoreException;

    // C4Document

    @SuppressWarnings("PMD.ExcessiveParameterList")
    private static native long put(
        long collection,
        byte[] body,
        String docID,
        int revFlags,
        boolean existingRevision,
        boolean allowConflict,
        String[] history,
        boolean save,
        int maxRevTreeDepth,
        int remoteDBID)
        throws LiteCoreException;

    @SuppressWarnings("PMD.ExcessiveParameterList")
    private static native long put2(
        long collection,
        long bodyPtr,
        long bodySize,
        String docID,
        int revFlags,
        boolean existingRevision,
        boolean allowConflict,
        String[] history,
        boolean save,
        int maxRevTreeDepth,
        int remoteDBID)
        throws LiteCoreException;

    // C4Key

    @Nullable
    private static native byte[] deriveKeyFromPassword(@NonNull String password);

    // C4Log

    private static native int getLevel(@NonNull String domain);

    // C4Index

    private static native boolean isIndexTrained(long collection, @NonNull String name) throws LiteCoreException;

    private static native C4IndexOptions getIndexOptions(long idx) throws CouchbaseLiteException;
}
