package com.couchbase.lite.internal;

import androidx.annotation.NonNull;

import com.couchbase.lite.Conflict;
import com.couchbase.lite.ConflictResolver;
import com.couchbase.lite.Document;
import com.couchbase.lite.MultipeerCollectionConfiguration;
import com.couchbase.lite.PeerInfo;

public class MultipeerConflictResolverWrapper implements ConflictResolver {
    @NonNull
    private final PeerInfo.PeerId peerID;

    @NonNull
    private final MultipeerCollectionConfiguration.ConflictResolver resolver;

    public MultipeerConflictResolverWrapper(
            @NonNull PeerInfo.PeerId peerID,
            @NonNull MultipeerCollectionConfiguration.ConflictResolver resolver) {
        this.peerID = peerID;
        this.resolver = resolver;
    }

    @Override
    public Document resolve(@NonNull Conflict conflict) {
        return this.resolver.resolve(peerID, conflict);
    }
}
