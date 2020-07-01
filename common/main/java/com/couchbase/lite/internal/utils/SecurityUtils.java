
//
// Copyright (c) 2020 Couchbase, Inc All rights reserved.
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

import android.support.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.util.Collection;


public final class SecurityUtils {
    private SecurityUtils() {}

    public static final String BEGIN_CERT = "-----BEGIN CERTIFICATE-----";
    public static final String END_CERT = "-----END CERTIFICATE-----";
    private static final PlatformUtils.Base64Encoder ENCODER = PlatformUtils.getEncoder();

    @NonNull
    public static byte[] encodeCertificateChain(@NonNull Collection<? extends Certificate> certChain)
        throws CertificateEncodingException {
        try (
            ByteArrayOutputStream encodedCerts = new ByteArrayOutputStream();
            PrintStream ps = new PrintStream(encodedCerts, false, "UTF-8")
        ) {
            for (Certificate cert: certChain) { encodeCertificate(cert, ps); }
            ps.flush();
            return encodedCerts.toByteArray();
        }
        catch (IOException e) {
            throw new CertificateEncodingException("I/O error during encoding", e);
        }
    }

    @NonNull
    public static byte[] encodeCertificate(@NonNull Certificate cert) throws CertificateEncodingException {
        try (
            ByteArrayOutputStream encodedCert = new ByteArrayOutputStream();
            PrintStream ps = new PrintStream(encodedCert, false, "UTF-8")
        ) {
            encodeCertificate(cert, ps);
            ps.flush();
            return encodedCert.toByteArray();
        }
        catch (IOException e) {
            throw new CertificateEncodingException("I/O error during encoding", e);
        }
    }

    private static void encodeCertificate(@NonNull Certificate cert, @NonNull PrintStream ps)
        throws CertificateEncodingException {
        final String encodedCert = ENCODER.encodeToString(cert.getEncoded());
        if (encodedCert == null) { return; }

        final int n = encodedCert.length();
        ps.println();
        ps.println(BEGIN_CERT);
        for (int i = 0; i < n; i += 64) { ps.println(encodedCert.substring(i, Math.min(n, i + 64))); }
        ps.println(END_CERT);
    }
}
