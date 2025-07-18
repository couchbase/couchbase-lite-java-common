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
package com.couchbase.lite.internal.replicator;

import android.net.http.X509TrustManagerExtensions;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.logging.Log;


/**
 * The Android version of conscrypt supports Android's extended network configuration feature
 * by looking for each of two overloaded methods (mmm) checkServerTrusted and checkClientTrusted
 * with each of three signatures, in order:
 * <ol>
 * <li>TrustManager.mmm(X509Certificate[] certChain, String authType, [Socket | SSLEngine])
 * <li>TrustManager.mmm(X509Certificate[] certChain, String authType, String)
 * <li>TrustManager.mmm(X509Certificate[] certChain, String authType)
 * </ol>
 * The type of the third parameter to the first call depends on type of the object doing the calling:
 * if it is a Socket, the third parameter to the call the the trust manager will be a socket, and so on.
 * It will attempt to call each of the three methods, in order, until one succeeds or throws an exception
 * other than NoSuchMethodException or IllegalAccessException.
 * <p>
 * Based on that the design here is as follows:
 * <ul>
 * <li>Don't worry about checkClientTrusted: we don't support it.
 * AbstractCBLTrust manager will ding the call, after two failed calls
 * <li>Support the <b>second</b> override of checkServerTrusted.  It will be called after one failed call.
 * CBL validation takes precedence, if configured
 * </ul>
 */
public final class CBLTrustManager extends AbstractCBLTrustManager {
    public CBLTrustManager(
        @Nullable X509Certificate pinnedServerCert,
        boolean acceptOnlySelfSignedServerCertificate,
        boolean acceptAllCertificates,
        @NonNull ServerCertsListener serverCertsListener) {
        super(pinnedServerCert, acceptOnlySelfSignedServerCertificate, acceptAllCertificates, serverCertsListener);
    }

    /**
     * Hostname aware version of {@link #checkServerTrusted(X509Certificate[], String)}.
     * This method is called using introspection by conscrypt and android.net.http.X509TrustManagerExtensions
     */
    @SuppressWarnings("unused")
    @Nullable
    public List<X509Certificate> checkServerTrusted(
        @Nullable X509Certificate[] chain,
        @Nullable String authType,
        @Nullable String host)
        throws CertificateException {
        final List<X509Certificate> serverCerts = asList(chain);
        certsReceived(serverCerts);

        // There are a couple of things to worry about, here, here:
        // - Need to test to verify that this stuff works with the Android extended network configuration feature.
        //   Perhaps we should be passing the result of the TMExtensions to cblServerTrustCheck() and requestAuth.
        // - This method return a list of certs.  If that list is then passed to
        // AbstractCBLTrustManager.checkServerTrusted(X509Certificate[], String), the calls

        List<X509Certificate> certsToCheck = serverCerts;
        if (acceptAllCerts()) {
            Log.d(LogDomain.NETWORK, "Accepting all certs: %d, %s, %s", serverCerts.size(), authType, host);
        }
        else if (useCBLTrustManagement()) {
            cBLServerTrustCheck(serverCerts, authType);
        }
        else {
            Log.d(LogDomain.NETWORK, "Extended trust check: %d, %s, %s", serverCerts.size(), authType, host);
            certsToCheck
                = new X509TrustManagerExtensions(getDefaultTrustManager()).checkServerTrusted(chain, authType, host);
        }

        requestAuth(certsToCheck);

        return certsToCheck;
    }
}
