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
package com.couchbase.lite.internal.replicator

import com.couchbase.lite.BaseTest
import org.junit.Assert
import org.junit.Test
import java.io.ByteArrayInputStream
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate


class CBLTrustManagerTest : BaseTest() {
    // This is just a self-signed cert
    // It will expire: Apr 8, 18:27:35 2033 GMT
    private val testServerCert1 = """        -----BEGIN CERTIFICATE-----
        MIICxzCCAjACCQDfNwsfQF766DANBgkqhkiG9w0BAQsFADCBpzELMAkGA1UEBhMC
        VVMxEzARBgNVBAgMCkNhbGlmb3JuaWExFDASBgNVBAcMC1NhbnRhIENsYXJhMRgw
        FgYDVQQKDA9Db3VjaGJhc2UsIEluYy4xDzANBgNVBAsMBk1vYmlsZTEdMBsGA1UE
        AwwUbW9iaWxlLmNvdWNoYmFzZS5jb20xIzAhBgkqhkiG9w0BCQEWFG1vYmlsZUBj
        b3VjaGJhc2UuY29tMB4XDTIzMDQxMTE4Mzk1OVoXDTMzMDQwODE4Mzk1OVowgacx
        CzAJBgNVBAYTAlVTMRMwEQYDVQQIDApDYWxpZm9ybmlhMRQwEgYDVQQHDAtTYW50
        YSBDbGFyYTEYMBYGA1UECgwPQ291Y2hiYXNlLCBJbmMuMQ8wDQYDVQQLDAZNb2Jp
        bGUxHTAbBgNVBAMMFG1vYmlsZS5jb3VjaGJhc2UuY29tMSMwIQYJKoZIhvcNAQkB
        FhRtb2JpbGVAY291Y2hiYXNlLmNvbTCBnzANBgkqhkiG9w0BAQEFAAOBjQAwgYkC
        gYEAv2hcSW170Ysxdxwhoj86XbEdefNQ/VLIkkcZAAkjUMIKpkchVrTbY2mb0tE3
        C2X0aEVXDuX12BLfANLLtGyaA/jhJEhFGV+ece+qvBhRkkXwryZnHY/rQm2yX3+2
        7VjYu+fH0zoCZ30AQNsoMplrS8A1RT6BuW1hCyFQf6nOYQcCAwEAATANBgkqhkiG
        9w0BAQsFAAOBgQCLiuDRKu66NSO34tHjLjQagYrEDcw63xmU/DLFxYb6HHt3JTvf
        LFYpQr9UIVIE7+Bph2akVh+P1CwLiLQHszoS0LENJMlbEjuXJiNuREmYGOCbzI1c
        TH75CzUjbb+aalTw4Xp3bKEkXQJ8JK+zMSbtb9SYwZGjziibUoafsiQFcA==
        -----END CERTIFICATE-----""".trimIndent()

    // This is also just a self-signed cert that will expire
    // Apr 8, 18:27:35 2033 GMT
    // Note that our trust management algorithm does not verify that a
    // server presents a cert chain. If the serverp resents multiple
    // certs, we just check each them for match. We don't verify that
    // they actually form a trust chain.
    val testServerCert2 = """        -----BEGIN CERTIFICATE-----
        MIICxTCCAi4CCQDGRUzDwytxHzANBgkqhkiG9w0BAQsFADCBpjELMAkGA1UEBhMC
        VVMxEzARBgNVBAgMCkNhbGlmb3JuaWExFDASBgNVBAcMC1NhbnRhIENsYXJhMRcw
        FQYDVQQKDA5Db3VjaGJhc2UsIEluYzEPMA0GA1UECwwGTW9iaWxlMR0wGwYDVQQD
        DBRtb2JpbGUuY291Y2hiYXNlLmNvbTEjMCEGCSqGSIb3DQEJARYUbW9iaWxlQGNv
        dWNoYmFzZS5jb20wHhcNMjMwNDExMTg0MDU2WhcNMzMwNDA4MTg0MDU2WjCBpjEL
        MAkGA1UEBhMCVVMxEzARBgNVBAgMCkNhbGlmb3JuaWExFDASBgNVBAcMC1NhbnRh
        IENsYXJhMRcwFQYDVQQKDA5Db3VjaGJhc2UsIEluYzEPMA0GA1UECwwGTW9iaWxl
        MR0wGwYDVQQDDBRtb2JpbGUuY291Y2hiYXNlLmNvbTEjMCEGCSqGSIb3DQEJARYU
        bW9iaWxlQGNvdWNoYmFzZS5jb20wgZ8wDQYJKoZIhvcNAQEBBQADgY0AMIGJAoGB
        AL9oXElte9GLMXccIaI/Ol2xHXnzUP1SyJJHGQAJI1DCCqZHIVa022Npm9LRNwtl
        9GhFVw7l9dgS3wDSy7RsmgP44SRIRRlfnnHvqrwYUZJF8K8mZx2P60Jtsl9/tu1Y
        2Lvnx9M6Amd9AEDbKDKZa0vANUU+gbltYQshUH+pzmEHAgMBAAEwDQYJKoZIhvcN
        AQELBQADgYEAAAAFq7WPW29P9Eanr3GzAO//VOSQRogIOGI2rlfEQKGNmPN67PHt
        QE0TK/ZWeayhNt1Ov+K9e+bOv90PcDeaY0H/IQFCuCiKexYkH4efP0F/eq1GADRL
        tNCcBE9ZMr+BBxulQvWGhe1xcfy/Y64BvvUBN7gItFuT+S4/RjL6BWA=
        -----END CERTIFICATE-----""".trimIndent()

    @Test
    fun testEmptyServerCerts() {
        val trustMgr = object : AbstractCBLTrustManager(makeCert(testServerCert2), false, { }) {}
        Assert.assertThrows(IllegalArgumentException::class.java) {
            trustMgr.cBLServerTrustCheck(emptyList<X509Certificate>(), "ECDHE_RSA")
        }
    }

    @Test
    fun testWrongCert() {
        val trustMgr = object : AbstractCBLTrustManager(makeCert(testServerCert2), false, { }) {}
        Assert.assertThrows(IllegalArgumentException::class.java) {
            trustMgr.cBLServerTrustCheck(listOf(makeCert(testServerCert1)), "")
        }
    }

    @Test
    fun testSelfSignedCert() {
        object : AbstractCBLTrustManager(null, true, { }) {}
            .cBLServerTrustCheck(listOf(makeCert(testServerCert1)), "ECDHE_RSA")
    }

    @Test
    fun testTooManySelfSignedCerts() {
        val trustMgr = object : AbstractCBLTrustManager(null, true, { }) {}
        Assert.assertThrows(CertificateException::class.java) {
            trustMgr.cBLServerTrustCheck(listOf(makeCert(testServerCert1), makeCert(testServerCert2)), "ECDHE_RSA")
        }
    }

    // Verify that a cert that is not self-signed is refused
    @Test
    fun testPinnedCertDoesntMatch() {
        val certs = listOf(makeCert(testServerCert1))
        val trustMgr = object : AbstractCBLTrustManager(makeCert(testServerCert2), false, { }) {}
        Assert.assertThrows(CertificateException::class.java) {
            trustMgr.cBLServerTrustCheck(certs, "ECDHE_RSA")
        }
    }

    @Test
    fun testPinnedCertMatches() {
        val certs = listOf(makeCert(testServerCert1))
        object : AbstractCBLTrustManager(certs[0], false, { }) {}
            .cBLServerTrustCheck(certs, "ECDHE_ECDSA")
    }

    @Test
    fun testPinnedCertInChain() {
        val certs = listOf(makeCert(testServerCert1), makeCert(testServerCert2))
        object : AbstractCBLTrustManager(certs[1], false, { }) {}
            .cBLServerTrustCheck(certs, "ECDHE_RSA")
    }

    @Test
    fun testPinnedCertTakesPrecedence() {
        val certs = listOf(makeCert(testServerCert1), makeCert(testServerCert2))
        object : AbstractCBLTrustManager(certs[1], true, { }) {}
            .cBLServerTrustCheck(certs, "ECDHE_ECDSA")
    }

    private fun makeCert(cert: String): X509Certificate = ByteArrayInputStream(cert.toByteArray()).use {
        CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
    }
}

