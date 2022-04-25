package com.couchbase.lite.internal.replicator;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import com.couchbase.lite.internal.utils.Fn;


/**
 * The trust manager that supports the followings:
 * 1. Supports pinned server certificate.
 * 2. Supports acceptOnlySelfSignedServerCertificate mode.
 * 3. Supports default trust manager for validating certs when the pinned server
 *    certificate and acceptOnlySelfSignedServerCertificate are not used.
 * 4. Allows to listen for the server certificates.
 */
public final class CBLTrustManager implements X509TrustManager {
    @Nullable
    private final byte[] pinnedServerCertificate;

    private final boolean acceptOnlySelfSignedServerCertificate;

    @NonNull
    private final Fn.Consumer<List<Certificate>> serverCertslistener;

    @NonNull
    private final AtomicReference<X509TrustManager> defaultTrustManager = new AtomicReference<>();

    public CBLTrustManager(
        @Nullable byte[] pinnedServerCert,
        boolean acceptOnlySelfSignedServerCertificate,
        @NonNull Fn.Consumer<List<Certificate>> serverCertslistener) {
        this.pinnedServerCertificate = (pinnedServerCert != null ? pinnedServerCert.clone() : null);
        this.acceptOnlySelfSignedServerCertificate = acceptOnlySelfSignedServerCertificate;
        this.serverCertslistener = serverCertslistener;
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        throw new UnsupportedOperationException("Checking Client Trust is a server operation");
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        try { doCheckServerTrusted(chain, authType); }
        finally {
            serverCertslistener.accept((chain == null)
                ? Collections.emptyList()
                : Collections.unmodifiableList(Arrays.asList(chain)));
        }
    }

    @SuppressWarnings("PMD.NPathComplexity")
    private void doCheckServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        // Use default trust manager if the pinned server certificate
        // and acceptOnlySelfSignedServerCertificate are not used:
        if (useDefaultTrustManager()) {
            getDefaultTrustManager().checkServerTrusted(chain, authType);
            return;
        }

        // Check chain and authType precondition and throws IllegalArgumentException according to
        // https://docs.oracle.com/javase/8/docs/api/javax/net/ssl/X509TrustManager.html:
        if (chain == null || chain.length == 0) {
            throw new IllegalArgumentException("No server certificates");
        }
        if (authType == null || authType.length() == 0) {
            throw new IllegalArgumentException("Invalid auth type: " + authType);
        }

        // Validate certificate:
        X509Certificate cert = chain[0];
        cert.checkValidity();

        // pinnedServerCertificate takes precedence: only accept self-signed if no cert is pinned.
        if (pinnedServerCertificate == null) {
            // Accept chain length == 1 containing any self-signed certificate
            if ((chain.length == 1) && isSelfSignedCertificate(cert)) { return; }
            throw new CertificateException("Server did not present the expected single, self-signed certificate");
        }

        // Compare the pinnedServerCertificate to each cert in the server chain
        int i = 0;
        while (true) {
            if (Arrays.equals(pinnedServerCertificate, cert.getEncoded())) { return; }
            if (++i >= chain.length) { break; }
            cert = chain[i];
            cert.checkValidity();
        }

        throw new CertificateException("The pinned certificate did not match any certificate in the server chain");
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        if (useDefaultTrustManager()) { return getDefaultTrustManager().getAcceptedIssuers(); }
        return new X509Certificate[0];
    }

    /**
     * Check if the default trust manager should be used.
     * When the pinned server certificate and acceptOnlySelfSignedServerCertificate
     * are not used, the default trust manager will be used.
     */
    private boolean useDefaultTrustManager() {
        return pinnedServerCertificate == null && !acceptOnlySelfSignedServerCertificate;
    }

    /**
     * Get the default trust manager.
     */
    private X509TrustManager getDefaultTrustManager() {
        final X509TrustManager trustManager = defaultTrustManager.get();
        if (trustManager != null) { return trustManager; }

        final TrustManager[] trustManagers;
        try {
            final TrustManagerFactory trustManagerFactory
                = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init((KeyStore) null);
            trustManagers = trustManagerFactory.getTrustManagers();
        }
        catch (NoSuchAlgorithmException | KeyStoreException e) {
            throw new IllegalStateException("Cannot find the default trust manager", e);
        }

        if (trustManagers == null || trustManagers.length == 0) {
            throw new IllegalStateException("Cannot find the default trust manager");
        }

        defaultTrustManager.compareAndSet(null, (X509TrustManager) trustManagers[0]);
        return defaultTrustManager.get();
    }

    /**
     * Check if the certificate is a self-signed certificate.
     */
    private boolean isSelfSignedCertificate(X509Certificate cert) {
        if (!cert.getSubjectDN().equals(cert.getIssuerDN())) { return false; }

        try {
            cert.verify(cert.getPublicKey());
            return true;
        }
        catch (CertificateException | NoSuchAlgorithmException | InvalidKeyException
            | NoSuchProviderException | SignatureException e) {
            return false;
        }
    }
}
