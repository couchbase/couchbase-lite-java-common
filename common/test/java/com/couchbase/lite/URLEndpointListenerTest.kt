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

import java.net.URL


private const val WS_PORT = 4984
private const val WSS_PORT = 4985
private const val CLIENT_CERT_LABEL = "CBL-Client-Cert"

fun URLEndpointListener.localURLEndpoint() = URL(
    if (config.isTlsDisabled) "ws" else "wss",
    "localhost",
    port,
    config.database.name
)

class URLEndpointListenerTest : BaseReplicatorTest() {
    private var listener: URLEndpointListener? = null

    /*
    @After
    fun tearDown() {
        val localListener = listener
        localListener?.stop()
        localListener?.config?.tlsIdentity?.deleteFromKeyChain()
    }

    @Test
    fun testPasswordAuthenticator() {
        // Listener:
        val listenerAuth = ListenerPasswordAuthenticator { username, password ->
            "daniel" == username  && ("123" == String(password))
        }
        val listener:URLEndpointListener = listen(false, listenerAuth)

        // Replicator - No Authenticator:
        run(listener.localURLEndpoint(), getReplicatorType(true, true), false, null, null, CBLErrorHTTPAuthRequired)

        // Replicator - Wrong Credentials:
        var auth = BasicAuthenticator("daniel", "456")
        run(listener.localURLEndpoint(), getReplicatorType(true, true), false, auth, null, CBLErrorHTTPAuthRequired)

        // Replicator - Success:
        auth = BasicAuthenticator("daniel", "123")
        run(listener.localURLEndpoint(), getReplicatorType(true, true), false, auth)

        listener.stop()
    }

    @Test
    fun testClientCertAuthenticatorWithClosure() {
        // Listener:
        val listenerAuth = ListenerCertificateAuthenticator { certs ->
            assertEquals(1, certs.count())
            var commonName: String? = null
            val status = SecCertificateCopyCommonName(certs[0], &commonName)
            assertEquals(status, errSecSuccess)
            assertNotNull(commonName)
            assertEquals((commonName as String), "daniel")
            true
        }
        val listener = listen(true, listenerAuth)
        assertNotNull(listener.config.tlsIdentity)
        assertEquals(1, listener.config.tlsIdentity?.certs?.count())

        // Cleanup:
        TLSIdentity.deleteIdentity(CLIENT_CERT_LABEL)

        // Create client identity:
        val attrs = mapOf(certAttrCommonName to "daniel")
        val identity = TLSIdentity.createIdentity(false, attrs, null, CLIENT_CERT_LABEL)

        // Replicator:
        val auth = ClientCertificateAuthenticator(identity)
        val serverCert = listener.config.tlsIdentity?.certs?.get(0)
        run(listener.localURLEndpoint(), getReplicatorType(true, true), false, auth, serverCert)

        // Cleanup:
        TLSIdentity.deleteIdentity(CLIENT_CERT_LABEL)
    }

    @Test
    fun testClientCertAuthenticatorWithRootCerts() {

        // Root Cert:
        val rootCertData = dataFromResource("identity/client-ca", "der")
        val rootCert: Certificate = SecCertificateCreateWithData(kCFAllocatorDefault, rootCertData as CFData)

        // Listener:
        val listenerAuth = ListenerCertificateAuthenticator(listOf(rootCert))
        val listener = listen(true, listenerAuth)

        // Cleanup:
        TLSIdentity.deleteIdentity(CLIENT_CERT_LABEL)

        // Create client identity:
        val clientCertData = dataFromResource("identity/client", "p12")
        val identity = TLSIdentity.importIdentity(clientCertData, "123", CLIENT_CERT_LABEL)

        // Replicator:
        val auth = ClientCertificateAuthenticator(identity)
        val serverCert = listener.config.tlsIdentity?.certs?.get(0)

        run(listener.localURLEndpoint(), getReplicatorType(true, true), false, auth, serverCert)

        // Cleanup:
        TLSIdentity.deleteIdentity(CLIENT_CERT_LABEL)
    }
    */

    fun listen(): URLEndpointListener = listen(true, null)

    fun listen(tls: Boolean): URLEndpointListener = listen(tls, null)

    fun listen(tls: Boolean, auth: ListenerAuthenticator?): URLEndpointListener {
        var localListener = listener;

        // Stop:
        listener?.stop()

        // Listener:
        val config = URLEndpointListenerConfiguration(otherDB)
        config.port = if (tls) WSS_PORT else WS_PORT
        config.isTlsDisabled = !tls
        config.authenticator = auth
        localListener = URLEndpointListener(config)

        // Start:
        localListener.start()

        return localListener
    }
}
