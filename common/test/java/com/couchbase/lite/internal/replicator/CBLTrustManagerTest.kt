//
// Copyright (c) 2022 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite.internal.replicator

import com.couchbase.lite.BaseTest
import org.junit.Test
import java.io.ByteArrayInputStream
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate


class CBLTrustManagerTest : BaseTest() {
    val testServerCert1 = """        -----BEGIN CERTIFICATE-----
        MIICxTCCAi4CCQCVDKAyMSYtyjANBgkqhkiG9w0BAQUFADCBpjELMAkGA1UEBhMC
        VVMxEzARBgNVBAgMCkNhbGlmb3JuaWExFDASBgNVBAcMC1NhbnRhIENsYXJhMRgw
        FgYDVQQKDA9Db3VjaGJhc2UsIEluYy4xDzANBgNVBAsMBk1vYmlsZTEcMBoGA1UE
        AwwTbW9iaWxlLmNvdWNiYXNlLmNvbTEjMCEGCSqGSIb3DQEJARYUbW9iaWxlQGNv
        dWNoYmFzZS5jb20wHhcNMjIwNDA0MjAzMzE5WhcNMjMwNDA0MjAzMzE5WjCBpjEL
        MAkGA1UEBhMCVVMxEzARBgNVBAgMCkNhbGlmb3JuaWExFDASBgNVBAcMC1NhbnRh
        IENsYXJhMRgwFgYDVQQKDA9Db3VjaGJhc2UsIEluYy4xDzANBgNVBAsMBk1vYmls
        ZTEcMBoGA1UEAwwTbW9iaWxlLmNvdWNiYXNlLmNvbTEjMCEGCSqGSIb3DQEJARYU
        bW9iaWxlQGNvdWNoYmFzZS5jb20wgZ8wDQYJKoZIhvcNAQEBBQADgY0AMIGJAoGB
        AMhWhhddpZS8NuFunWY9ZETEtfRGrnahmgzvIbzfGsZtk6wwTj9AwSPxG+gShItp
        m277erZnQu9zl8e7BVUP+Vn6PmRHc4wIs2qC60Z2g+u8WMyzzw5crFhZRIyAJjyR
        ph6UskmQUR7+AKL/c+6JJgDZBNgqZH55bL/cRQUpJPXxAgMBAAEwDQYJKoZIhvcN
        AQEFBQADgYEAP3Q3BPsTz7YxcIikAlYz3ysbdRmFwm1MzCRxfbNmHmUrqcIkqVIN
        UzwbyleqiUYUJRzzHvS7KV/8bQKAEl/ZTY7PBR3G9/rQEyNIbEdGB0Zf7xeN0hH3
        zMM2Hgbpo257MYjfpXAoW1MqvS+OtISJTZA8kAYUDpKzmEEfFRFRVT4=
        -----END CERTIFICATE-----""".trimIndent()

    val testServerCert2 = """        -----BEGIN CERTIFICATE-----
        MIICxTCCAi4CCQC+BB4REq9jxTANBgkqhkiG9w0BAQUFADCBpjELMAkGA1UEBhMC
        VVMxEzARBgNVBAgMCkNhbGlmb3JuaWExFDASBgNVBAcMC1NhbnRhIENsYXJhMRgw
        FgYDVQQKDA9Db3VjaGJhc2UsIEluYy4xDzANBgNVBAsMBk1vYmlsZTEcMBoGA1UE
        AwwTbW9iaWxlLmNvdWNiYXNlLmNvbTEjMCEGCSqGSIb3DQEJARYUbW9iaWxlQGNv
        dWNoYmFzZS5jb20wHhcNMjIwNDA0MjAzMzMyWhcNMjMwNDA0MjAzMzMyWjCBpjEL
        MAkGA1UEBhMCVVMxEzARBgNVBAgMCkNhbGlmb3JuaWExFDASBgNVBAcMC1NhbnRh
        IENsYXJhMRgwFgYDVQQKDA9Db3VjaGJhc2UsIEluYy4xDzANBgNVBAsMBk1vYmls
        ZTEcMBoGA1UEAwwTbW9iaWxlLmNvdWNiYXNlLmNvbTEjMCEGCSqGSIb3DQEJARYU
        bW9iaWxlQGNvdWNoYmFzZS5jb20wgZ8wDQYJKoZIhvcNAQEBBQADgY0AMIGJAoGB
        AMhWhhddpZS8NuFunWY9ZETEtfRGrnahmgzvIbzfGsZtk6wwTj9AwSPxG+gShItp
        m277erZnQu9zl8e7BVUP+Vn6PmRHc4wIs2qC60Z2g+u8WMyzzw5crFhZRIyAJjyR
        ph6UskmQUR7+AKL/c+6JJgDZBNgqZH55bL/cRQUpJPXxAgMBAAEwDQYJKoZIhvcN
        AQEFBQADgYEAkuo0DdOOeH0cxWtjl9JhbV2RcqaCU8yN7MqVfMi56zHYanCcZ2E+
        zauY7WA/uvBQpNJbZ9EZAu6AV+FQDyN38gOamozYSqTtH3EOGN/oBQ6RK1k0vVMz
        OSklpn0DSweV0yMn3CIJ8N1szmOXWDgI02r8ltnZ3mX9tyUm99E88g0=
        -----END CERTIFICATE-----""".trimIndent()

    val testServerCert3 = """        -----BEGIN CERTIFICATE-----
        MIICxTCCAi4CCQCtogm1dt9IbTANBgkqhkiG9w0BAQUFADCBpjELMAkGA1UEBhMC
        VVMxEzARBgNVBAgMCkNhbGlmb3JuaWExFDASBgNVBAcMC1NhbnRhIENsYXJhMRgw
        FgYDVQQKDA9Db3VjaGJhc2UsIEluYy4xDzANBgNVBAsMBk1vYmlsZTEcMBoGA1UE
        AwwTbW9iaWxlLmNvdWNiYXNlLmNvbTEjMCEGCSqGSIb3DQEJARYUbW9iaWxlQGNv
        dWNoYmFzZS5jb20wHhcNMjIwNDA0MjAzNDI3WhcNMjMwNDA0MjAzNDI3WjCBpjEL
        MAkGA1UEBhMCVVMxEzARBgNVBAgMCkNhbGlmb3JuaWExFDASBgNVBAcMC1NhbnRh
        IENsYXJhMRgwFgYDVQQKDA9Db3VjaGJhc2UsIEluYy4xDzANBgNVBAsMBk1vYmls
        ZTEcMBoGA1UEAwwTbW9iaWxlLmNvdWNiYXNlLmNvbTEjMCEGCSqGSIb3DQEJARYU
        bW9iaWxlQGNvdWNoYmFzZS5jb20wgZ8wDQYJKoZIhvcNAQEBBQADgY0AMIGJAoGB
        AMhWhhddpZS8NuFunWY9ZETEtfRGrnahmgzvIbzfGsZtk6wwTj9AwSPxG+gShItp
        m277erZnQu9zl8e7BVUP+Vn6PmRHc4wIs2qC60Z2g+u8WMyzzw5crFhZRIyAJjyR
        ph6UskmQUR7+AKL/c+6JJgDZBNgqZH55bL/cRQUpJPXxAgMBAAEwDQYJKoZIhvcN
        AQEFBQADgYEASpHD9NG8inh6eNckK/YML512j+FqfL8RprhBRx9i9RzD+y9a+7YJ
        hhi99PSnk+kLGtHjKv7q1Cvl9XTM/rvRoF76Hqv6OfZHGBG7eMmZlDhgSJmKgQxg
        rRWcuUZ+XNkR0YSObubO7cblBU4Ldj/w2bv1rXBhwodM/unw1fzGPI4=
        -----END CERTIFICATE-----""".trimIndent()

    @Test(expected = IllegalArgumentException::class)
    fun testEmptyServerCerts() {
        object : AbstractCBLTrustManager(makeCert(testServerCert2), false, { _ -> }) {}
            .cBLServerTrustCheck(emptyList<X509Certificate>(), "ECDHE_RSA")
    }

    @Test(expected = IllegalArgumentException::class)
    fun testBadAuthType() {
        object : AbstractCBLTrustManager(makeCert(testServerCert2), false, { _ -> }) {}
            .cBLServerTrustCheck(listOf(makeCert(testServerCert1)), "")
    }

    @Test
    fun testSelfSignedCert() {
        object : AbstractCBLTrustManager(null, true, { _ -> }) {}
            .cBLServerTrustCheck(listOf(makeCert(testServerCert1)), "ECDHE_RSA")
    }

    @Test(expected = CertificateException::class)
    fun testTooManySelfSignedCerts() {
        object : AbstractCBLTrustManager(null, true, { _ -> }) {}
            .cBLServerTrustCheck(
                listOf(makeCert(testServerCert1), makeCert(testServerCert2)),
                "ECDHE_RSA"
            )
    }

    // Should verify that a cert that is not self-signed is refused

    @Test(expected = CertificateException::class)
    fun testPinnedCertDoesntMatch() {
        val certs = listOf(makeCert(testServerCert1))
        object : AbstractCBLTrustManager(makeCert(testServerCert2), false, { _ -> }) {}
            .cBLServerTrustCheck(certs, "ECDHE_RSA")
    }

    @Test
    fun testPinnedCertMatches() {
        val certs = listOf(makeCert(testServerCert1))
        object : AbstractCBLTrustManager(certs[0], false, { _ -> }) {}
            .cBLServerTrustCheck(certs, "ECDHE_ECDSA")
    }

    @Test
    fun testPinnedCertInChain() {
        val certs = listOf(makeCert(testServerCert1), makeCert(testServerCert2))
        object : AbstractCBLTrustManager(certs[1], false, { _ -> }) {}
            .cBLServerTrustCheck(certs, "ECDHE_RSA")
    }

    @Test
    fun testPinnedCertTakesPrecedence() {
        val certs = listOf(makeCert(testServerCert1), makeCert(testServerCert2))
        object : AbstractCBLTrustManager(certs[1], true, { _ -> }) {}
            .cBLServerTrustCheck(certs, "ECDHE_ECDSA")
    }

    private fun makeCert(cert: String): X509Certificate =
        ByteArrayInputStream(cert.toByteArray()).use {
            CertificateFactory.getInstance("X.509").generateCertificate(it)
                    as X509Certificate
        }
}

