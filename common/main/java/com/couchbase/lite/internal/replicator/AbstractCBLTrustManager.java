//
// Copyright (c) 2020 Couchbase, Inc.
// Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
// except in compliance with the License. You may obtain a copy of the License at
// http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software distributed under the
// License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions
// and limitations under the License.
//
package com.couchbase.lite.internal.replicator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
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

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.logging.Log;
import com.couchbase.lite.internal.utils.StringUtils;


/**
 * The trust manager that supports the followings:
 * 1. Supports pinned server certificate.
 * 2. Supports acceptOnlySelfSignedServerCertificate mode.
 * 3. Supports default trust manager for validating certs when the pinned server
 * certificate and acceptOnlySelfSignedServerCertificate are not used.
 * 4. Allows to listen for the server certificates.
 */
public abstract class AbstractCBLTrustManager implements X509TrustManager {
    public interface ServerCertsListener {
        void certsPresented(@NonNull List<Certificate> certs);
        void requestAuthentication(@NonNull List<Certificate> certs) throws CertificateException;
    }

    @Nullable
    private final X509Certificate pinnedServerCertificate;

    private final boolean acceptOnlySelfSignedServerCertificate;
    private final boolean acceptAllCertificates;

    @NonNull
    private final ServerCertsListener serverCertsListener;

    @NonNull
    private final AtomicReference<X509TrustManager> defaultTrustManager = new AtomicReference<>();

    public AbstractCBLTrustManager(
        @Nullable X509Certificate pinnedServerCert,
        boolean acceptOnlySelfSignedServerCertificate,
        boolean acceptAllCertificates,
        @NonNull ServerCertsListener serverCertsListener) {
        this.pinnedServerCertificate = pinnedServerCert;
        this.acceptOnlySelfSignedServerCertificate = acceptOnlySelfSignedServerCertificate;
        this.acceptAllCertificates = acceptAllCertificates;
        this.serverCertsListener = serverCertsListener;
    }

    @NonNull
    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return (useCBLTrustManagement()) ? new X509Certificate[0] : getDefaultTrustManager().getAcceptedIssuers();
    }

    @Override
    public void checkClientTrusted(@Nullable X509Certificate[] chain, @Nullable String authType) {
        throw new UnsupportedOperationException(
            "checkClientTrusted(X509Certificate[], String) not supported for client");
    }

    @Override
    public void checkServerTrusted(@Nullable X509Certificate[] chain, @Nullable String authType)
        throws CertificateException {
        final List<X509Certificate> serverCerts = asList(chain);
        certsReceived(serverCerts);

        if (acceptAllCerts()) {
            Log.d(LogDomain.NETWORK, "Accepting all certs: %d, %s, %s", serverCerts.size(), authType);
        }
        else if (useCBLTrustManagement()) {
            cBLServerTrustCheck(serverCerts, authType);
        }
        else {
            Log.d(LogDomain.NETWORK, "Default trust check: %d, %s", (chain == null) ? 0 : chain.length, authType);
            getDefaultTrustManager().checkServerTrusted(chain, authType);
        }

        requestAuth(serverCerts);
    }


    // Check chain and authType precondition and throws IllegalArgumentException according to
    // https://docs.oracle.com/javase/8/docs/api/javax/net/ssl/X509TrustManager.html:
    @SuppressWarnings("PMD.NPathComplexity")
    protected final void cBLServerTrustCheck(@Nullable List<X509Certificate> certs, @Nullable String authType)
        throws CertificateException {
        Log.d(LogDomain.NETWORK, "CBL trust check: %d, %s", (certs == null) ? 0 : certs.size(), authType);

        if ((certs == null) || certs.isEmpty()) { throw new IllegalArgumentException("No server certificates"); }
        if (StringUtils.isEmpty(authType)) { throw new IllegalArgumentException("Empty auth type"); }

        X509Certificate cert = certs.get(0);
        cert.checkValidity();

        // pinnedServerCertificate takes precedence: only accept self-signed if no cert is pinned.
        if (pinnedServerCertificate == null) {
            // Accept chain length == 1 containing any self-signed certificate
            if ((certs.size() == 1) && isSelfSignedCertificate(cert)) { return; }
            throw new CertificateException("Server did not present the expected single, self-signed certificate");
        }

        // Compare the pinnedServerCertificate to each cert in the server chain
        int i = 0;
        while (true) {
            if (pinnedServerCertificate.equals(cert)) { return; }
            if (++i >= certs.size()) { break; }
            cert = certs.get(i);
            cert.checkValidity();
        }

        throw new CertificateException("The pinned certificate did not match any certificate in the server chain");
    }

    protected final void certsReceived(@NonNull List<X509Certificate> certs) {
        serverCertsListener.certsPresented(Collections.unmodifiableList(certs));
    }

    protected final void requestAuth(@NonNull List<X509Certificate> certs) throws CertificateException {
        serverCertsListener.requestAuthentication(Collections.unmodifiableList(certs));
    }

    protected final boolean acceptAllCerts() { return acceptAllCertificates; }

    /**
     * Check if the default trust manager should be used.
     * When the pinned server certificate and acceptOnlySelfSignedServerCertificate
     * are not used, the default trust manager will be used.
     */
    protected final boolean useCBLTrustManagement() {
        return acceptOnlySelfSignedServerCertificate || (pinnedServerCertificate != null);
    }

    @NonNull
    protected final List<X509Certificate> asList(@Nullable X509Certificate[] certs) {
        return (certs == null) ? Collections.emptyList() : Arrays.asList(certs);
    }

    /**
     * Get the default trust manager.
     * This code duplicates the code in org.conscrypt.SSLParametersImpl.createDefaultX509TrustManager
     * I believe that this will, properly, return an X509ExtendedTrustManager, on Androids > 24
     * because Platform.createEngineSocket does the right thing.  I have not, however, been able to
     * verify the connection between that method and the TrustManagerFactory.
     * Empirically, on the test Nexus 4a, Android 33, it is returning an X509ExtendedTrustManager
     */
    @NonNull
    protected final X509TrustManager getDefaultTrustManager() {
        X509TrustManager trustManager = defaultTrustManager.get();
        if (trustManager != null) { return trustManager; }

        final TrustManager[] trustManagers;
        try {
            final TrustManagerFactory trustManagerFactory
                = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init((KeyStore) null);
            trustManagers = trustManagerFactory.getTrustManagers();
        }
        catch (NoSuchAlgorithmException | KeyStoreException e) {
            throw new UnsupportedOperationException("Cannot find the default trust manager", e);
        }

        for (TrustManager mgr: trustManagers) {
            if (mgr instanceof X509TrustManager) {
                trustManager = (X509TrustManager) mgr;
                break;
            }
        }
        if (trustManager == null) { throw new UnsupportedOperationException("Cannot find an X509TrustManager"); }

        defaultTrustManager.compareAndSet(null, trustManager);

        return defaultTrustManager.get();
    }

    /**
     * Check if the certificate is a self-signed certificate.
     */
    private boolean isSelfSignedCertificate(@NonNull X509Certificate cert) {
        try { cert.verify(cert.getPublicKey()); }
        catch (GeneralSecurityException ignore) { return false; }
        return cert.getSubjectDN().equals(cert.getIssuerDN());
    }
}
