//
//  Copyright (c) 2020 Couchbase, Inc. All rights reserved.
//
//  Licensed under the Couchbase License Agreement (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//  https://info.couchbase.com/rs/302-GJY-034/images/2017-10-30_License_Agreement.pdf
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.
//
package com.couchbase.lite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.cert.Certificate
import java.util.Date


class TLSIdentityTest() : PlatformBaseTest() {
    val serverCertLabel = "CBL-Swift-Server-Cert"
    val clientCertLabel = "CBL-Swift-Client-Cert"
/*
    @Before
    fun setUpTLSIdentityTest() {
        try {
            TLSIdentity.deleteIdentity(serverCertLabel)
            TLSIdentity.deleteIdentity(clientCertLabel)
        } catch (_: CouchbaseLiteException) {
        }
    }

    @Before
    fun tearDownTLSIdentityTest() {
        try {
            TLSIdentity.deleteIdentity(serverCertLabel)
            TLSIdentity.deleteIdentity(clientCertLabel)
        } catch (_: CouchbaseLiteException) {
        }
    }

    @Test
    fun testCreateGetDeleteServerIdentity() {
        TLSIdentity.deleteIdentity(serverCertLabel)

        // Get:
        var identity = TLSIdentity.getIdentity(serverCertLabel, null)
        assertNull(identity)

        // Create:
        val attrs = mapOf("certAttrCommonName" to "CBL-Server")
        identity = TLSIdentity.getIdentity(true, attrs, null, serverCertLabel, null)
        assertNotNull(identity)
        assertEquals(1, identity!!.certs?.count())
        checkIdentityInKeyChain(identity)

        // Get:
        identity = TLSIdentity.getIdentity(serverCertLabel)
        assertNotNull(identity)
        assertEquals(1, identity.certs?.count())
        checkIdentityInKeyChain(identity)

        // Delete:
        TLSIdentity.deleteIdentity(serverCertLabel)

        // Get:
        identity = TLSIdentity.getIdentity(serverCertLabel)
        assertNull(identity)
    }

    @Test(expected = CouchbaseLiteException::class)
    fun testCreateDuplicateServerIdentity() {
        // Create:
        val attrs = mapOf("certAttrCommonName" to "CBL-Server")
        var identity = TLSIdentity.createIdentity(true, attrs, null, serverCertLabel)
        assertNotNull(identity)
        checkIdentityInKeyChain(identity)

        // Get:
        identity = TLSIdentity.getIdentity(serverCertLabel)
        assertNotNull(identity)
        assertEquals(1, identity.certs?.count())
        checkIdentityInKeyChain(identity)

        // Create again with the same label:
        identity = TLSIdentity.createIdentity(true, attrs, null, serverCertLabel)
    }

    @Test
    fun testCreateGetDeleteClientIdentity() {
        // Delete:
        TLSIdentity.deleteIdentity(clientCertLabel)

        // Get:
        var identity = TLSIdentity.getIdentity(clientCertLabel)
        assertNull(identity)

        // Create:
        val attrs = mapOf("certAttrCommonName" to "CBL-Client")
        identity = TLSIdentity.createIdentity(false, attrs, null, clientCertLabel)
        assertNotNull(identity)
        assertEquals(1, identity!!.certs.count)
        checkIdentityInKeyChain(identity)

        // Get:
        identity = TLSIdentity.getIdentity(clientCertLabel)
        assertNotNull(identity)
        assertEquals(1, identity.certs.count)
        checkIdentityInKeyChain(identity)

        // Delete:
        TLSIdentity.deleteIdentity(clientCertLabel)

        // Get:
        identity = TLSIdentity.getIdentity(clientCertLabel)
        assertNull(identity)
    }

    @Test(expected = CouchbaseLiteException::class)
    fun testCreateDuplicateClientIdentity() {
        // Create:
        val attrs = mapOf("certAttrCommonName" to "CBL-Client")
        var identity = TLSIdentity.createIdentity(false, attrs, null, clientCertLabel)
        assertNotNull(identity)
        checkIdentityInKeyChain(identity)

        // Get:
        identity = TLSIdentity.getIdentity(clientCertLabel)
        assertNotNull(identity)
        assertEquals(1, identity.certs.count())
        checkIdentityInKeyChain(identity)

        // Create again with the same label:
        identity = TLSIdentity.createIdentity(false, attrs, null, clientCertLabel)
    }

    @Test
    fun testGetIdentityWithIdentity() {
        // Use SecPKCS12Import to import the PKCS12 ByteArray:
        var result: CFArray?
        val ByteArray = ByteArrayFromResource("identity/certs", ofType: "p12")
        val options = mapOf(SEC_PASSPHRASE to "123")
        var status = errSecSuccess
        try {
            status = SecPKCS12Import(data as CFData, options as CFDictionary, & result)
        } catch (_: CouchbaseLiteException) {
        }
        assertEquals(status, errSecSuccess)

        // Identity:
        val importedItems = result!as NSArray
        assertTrue(importedItems.count > 0)
        val item = importedItems[0] as ! [String: Any]
        val secIdentity = item[kSecImportItemIdentity] as SecIdentity

        // Private Key:
        var privateKey: SecKey?
        status = SecIdentityCopyPrivateKey(secIdentity, & privateKey)
        assertEquals(status, errSecSuccess)

        // Certs:
        val certs = item[String(kSecImportItemCertChain)] as ! [Certificate]
        assertEquals(certs.count, 2)

        // For iOS, need to save the identity into the KeyChain.
        // Save or Update identity with a label so that it could be cleaned up easily:

        // !!! Choose one
        store(privateKey: privateKey!, certs, serverCertLabel)
        update(certs[0] as Certificate, serverCertLabel)

        // Get identity:
        val identity = TLSIdentity.getIdentity(secIdentity, certs[1])
        assertNotNull(identity)
        assertEquals(2, identity.certs.count())

        // Delete from KeyChain:
        TLSIdentity.deleteIdentity(serverCertLabel)
    }

    @Test
    fun testImportIdentity() {
        val data = ByteArrayFromResource("identity/certs", ofType: "p12")

        var identity: TLSIdentity? = null

        // When importing P12 file on macOS unit test, there is an internal exception thrown
        // inside SecPKCS12Import() which doesn't actually cause anything. Ignore the exception
        // so that the exception breakpoint will not be triggered.
        try {
            identity = TLSIdentity.importIdentity(data, "123", serverCertLabel)
        } catch (_: CouchbaseLiteException) {
        }

        assertNotNull(identity)
        assertEquals(2, identity!!.certs?.count())
        checkIdentityInKeyChain(identity)

        // Get:
        identity = TLSIdentity.getIdentity(serverCertLabel)
        assertNotNull(identity)
        assertEquals(2, identity.certs.count())
        checkIdentityInKeyChain(identity!)

        // Delete:
        TLSIdentity.deleteIdentity(serverCertLabel)

        // Get:
        identity = TLSIdentity.getIdentity(serverCertLabel)
        assertNull(identity)
    }

    @Test
    fun testCreateIdentityWithNoAttributes() {
        // Delete:
        TLSIdentity.deleteIdentity(serverCertLabel)

        // Get:
        var identity = TLSIdentity.getIdentity(serverCertLabel)
        assertNull(identity)

        // Create:
        identity = TLSIdentity.createIdentity(false, emptyMap(), null, clientCertLabel)
    }

    @Test
    fun testCertificateExpiration() {
        // Delete:
        TLSIdentity.deleteIdentity(serverCertLabel)

        // Get:
        var identity = TLSIdentity.getIdentity(serverCertLabel)
        assertNull(identity)

        val attrs = mapOf(certAttrCommonName to "CBL-Server"]
        val expiration = Date(System.currentTimeMillis() + 300)
        identity = TLSIdentity.createIdentity(true, attrs, expiration, serverCertLabel)
        assertNotNull(identity)
        assertEquals(1, identity!!.certs.count())
        checkIdentityInKeyChain(identity)

        // The actual expiration will be slightly less than the set expiration time:
        assertTrue(Math.abs(expiration.time - identity.expiration.time) < 5.0)
    }

    private fun findInKeyChain(params: Map<String, Any>): CFTypeRef? {
        var result: CFTypeRef? = null
        val status = SecItemCopyMatching(params as CFDictionary, & result)
        if (status != errSecSuccess) {
            if (status == errSecItemNotFound) {
                return null
            } else {
                print("Couldn't get an item from the Keychain: ${params} (error: ${status}")
            }
        }
        assertTrue(result != null)
        return result
    }

    private fun publicKeyHashFromCert(cert: Certificate): ByteArray {
        var trust: SecTrust? = null
        val status = SecTrustCreateWithCertificates(cert, SecPolicyCreateBasicX509(), & trust)
        assertEquals(status, errSecSuccess)
        assertNotNull(trust)
        val publicKey = SecTrustCopyPublicKey(trust!)
        assertNotNull(publicKey)
        val attrs = SecKeyCopyAttributes(publicKey!!) as Map<String, Any>
        return attrs[String(kSecAttrApplicationLabel)] as ByteArray
    }

    private fun checkIdentityInKeyChain(identity: TLSIdentity) {
        // Cert:
        val certs = identity.certs
        assertTrue(certs?.count() ?: 0 > 0)
        val cert = certs!![0]

        // Private Key:
        val publicKeyHash = publicKeyHashFromCert(cert)
        val privateKey = findInKeyChain(
            mapOf(
                kSecClass to kSecClassKey,
                kSecAttrKeyType to kSecAttrKeyTypeRSA,
                kSecAttrKeyClass to kSecAttrKeyClassPrivate,
                kSecAttrApplicationLabel to publicKeyHash
            ) as SecKey
        )
        assertNotNull(privateKey)

        // Get public key from cert via trust:
        var trust: SecTrust? = null
        val status = SecTrustCreateWithCertificates(cert, SecPolicyCreateBasicX509(), & trust)
        assertEquals(status, errSecSuccess)
        assertNotNull(trust)
        val publicKey = SecTrustCopyPublicKey(trust!!)
        assertNotNull(publicKey)

        // Check public key ByteArray:
        var error: Unmanaged<CFError>?
        val data = SecKeyCopyExternalRepresentation(publicKey!!, & error) as ByteArray?
        assertNull(error)
        assertNotNull(data)
        assertTrue(data!!.count() > 0)
    }

    private fun store(privateKey: SecKey, certs: List<Certificate>, label: String) {
        assertTrue(certs.count() > 0)

        // Private Key:
        store(privateKey)

        // Certs:
        var i = 0;
        for (cert in certs) {
            store(cert, (if (i == 0) label else null))
            i = i + 1
        }
    }

    private fun store(privateKey: SecKey) {
        val params = mapOf(
            kSecClass to kSecClassKey,
            kSecAttrKeyType to kSecAttrKeyTypeRSA,
            kSecAttrKeyClass to kSecAttrKeyClassPrivate,
            kSecValueRef to privateKey
        )

        val status = SecItemAdd(params as CFDictionary, null)
        assertEquals(status, errSecSuccess)
    }

    private fun store(cert: Certificate, label: String?) {
        var params = mapOf(
            kSecClass to kSecClassCertificate,
            kSecValueRef to cert
        )
        label?.let {
            params[kSecAttrLabel] = this
        }

        val status = SecItemAdd(params as CFDictionary, null)
        assertEquals(status, errSecSuccess)
    }

    private fun update(cert: Certificate, label: String) {
        val query = mapOf(
            kSecClass to kSecClassCertificate,
            kSecValueRef to cert
        )

        val update = mapOf(
            kSecClass to kSecClassCertificate,
            kSecValueRef to cert,
            kSecAttrLabel to label
        )

        val status = SecItemUpdate(query as CFDictionary, update as CFDictionary)
        assertEquals(status, errSecSuccess)
    }
 */
}
