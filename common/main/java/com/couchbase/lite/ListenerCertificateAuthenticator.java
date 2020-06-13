//
// Copyright (c) 2017 Couchbase, Inc All rights reserved.
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

import android.support.annotation.NonNull;

import java.security.cert.Certificate;
import java.util.List;


/**
 * A Listener Certificate Authenticator Delegate
 */
public abstract class ListenerCertificateAuthenticator
    implements ListenerAuthenticator, ListenerCertificateAuthenticatorDelegate {

    //-------------------------------------------------------------------------
    // Implementation classes
    //-------------------------------------------------------------------------

    static final class RootCertAuthenticator extends ListenerCertificateAuthenticator {
        @NonNull
        private final List<Certificate> rootCerts;

        RootCertAuthenticator(@NonNull List<Certificate> rootCerts) { this.rootCerts = rootCerts; }

        @Override
        public boolean authenticate(@NonNull List<Certificate> certs) {
            return false;
        }
    }

    static final class DelegatingCertAuthenticator extends ListenerCertificateAuthenticator {
        @NonNull
        private final ListenerCertificateAuthenticatorDelegate delegate;

        DelegatingCertAuthenticator(@NonNull ListenerCertificateAuthenticatorDelegate delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean authenticate(@NonNull List<Certificate> certs) { return delegate.authenticate(certs); }
    }

    //-------------------------------------------------------------------------
    // Static Factory Methods
    //-------------------------------------------------------------------------

    public static ListenerCertificateAuthenticator newListenerCertificateAuthenticator(
        @NonNull List<Certificate> rootCerts) {
        return new RootCertAuthenticator(rootCerts);
    }

    public static ListenerCertificateAuthenticator newListenerCertificateAuthenticator(
        @NonNull ListenerCertificateAuthenticatorDelegate delegate) {
        return new DelegatingCertAuthenticator(delegate);
    }

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    ListenerCertificateAuthenticator() {}
}
