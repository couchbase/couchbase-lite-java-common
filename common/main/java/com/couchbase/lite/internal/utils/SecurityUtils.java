
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

    public static final String LINE_SEPARATOR = System.getProperty("line.separator");
    public static final String BEGIN_CERT = LINE_SEPARATOR + "-----BEGIN CERTIFICATE-----" + LINE_SEPARATOR;
    public static final String END_CERT = LINE_SEPARATOR + "-----END CERTIFICATE-----" + LINE_SEPARATOR;
    private static final Base64Utils.Base64Encoder ENCODER = Base64Utils.getEncoder();

    public static byte[] encodeCertificateChain(Collection<? extends Certificate> certChain)
        throws CertificateEncodingException {
        try (
            ByteArrayOutputStream encodedCerts = new ByteArrayOutputStream();
            PrintStream ps = new PrintStream(new ByteArrayOutputStream(), false, "UTF-8")
        ) {
            for (Certificate cert: certChain) { encodeCertificate(cert, ps); }
            return encodedCerts.toByteArray();
        }
        catch (IOException e) {
            throw new CertificateEncodingException("I/O error durning encoding", e);
        }
    }

    public static byte[] pemEncodeCertificate(Certificate cert) throws CertificateEncodingException {
        try (
            ByteArrayOutputStream encodedCerts = new ByteArrayOutputStream();
            PrintStream ps = new PrintStream(new ByteArrayOutputStream(), false, "UTF-8")
        ) {
            encodeCertificate(cert, ps);
            return encodedCerts.toByteArray();
        }
        catch (IOException e) {
            throw new CertificateEncodingException("I/O error durning encoding", e);
        }
    }

    // !!! this does not correctly limit line length to 64 chars
    private static void encodeCertificate(@NonNull Certificate cert, @NonNull PrintStream ps)
        throws CertificateEncodingException {
        ps.println(BEGIN_CERT);
        ps.println(ENCODER.encodeToString(cert.getEncoded()));
        ps.println(END_CERT);
    }
}
