//
// Copyright (c) 2025 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite.internal.utils;

import androidx.annotation.NonNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;


public final class CertUtils {
    private static final String CERT_TYPE = "X.509";

    private CertUtils() {
        // Prevent instantiation
    }

    @NonNull
    public static X509Certificate createCertificate(@NonNull byte[] certBytes) throws CertificateException {
        try (InputStream in = new ByteArrayInputStream(certBytes)) {
            return (X509Certificate) CertificateFactory.getInstance(CERT_TYPE).generateCertificate(in);
        }
        catch (IOException e) { throw new CertificateException("Failed streaming cert bytes on create", e); }
    }

    @NonNull
    public static byte[] toBytes(@NonNull List<Certificate> certs) throws CertificateException {
        CertificateFactory factory = CertificateFactory.getInstance(CERT_TYPE);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for(int i = 0; i < certs.size(); i++) {
            try {
                baos.write(certs.get(i).getEncoded());
            } catch(IOException e) {
                throw new CertificateException("Unable to write certificate", e);
            }
        }

        return baos.toByteArray();
    }

    @NonNull
    public static List<X509Certificate> fromBytes(@NonNull byte[] certs) throws CertificateException {
        final CertificateFactory cf = CertificateFactory.getInstance(CERT_TYPE);
        List<X509Certificate> retVal = new ArrayList<X509Certificate>();
        try (InputStream in = new ByteArrayInputStream(certs)) {
            while(in.available() > 0) {
                X509Certificate cert = (X509Certificate)cf.generateCertificate(in);
                retVal.add(cert);
            }

            return retVal;
        }
        catch (IOException e) { throw new CertificateException("Failed streaming cert on fromBytes", e); }
    }
}

