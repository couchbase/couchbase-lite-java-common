//
// Copyright (c) 2020 Couchbase. All rights reserved.
// COUCHBASE CONFIDENTIAL - part of Couchbase Lite Enterprise Edition
//
package com.couchbase.lite

import com.couchbase.lite.internal.sockets.OkHttpSocket
import com.couchbase.lite.internal.utils.SlowTest
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.junit.Assert
import org.junit.Test
import java.net.URI

class CookieStoreTest : BaseDbTest() {
    @SlowTest
    @Test
    fun testSetGetCookies() {
        val cookieStore = AbstractReplicator.ReplicatorCookieStore(testDatabase)

        // Session cookie
        val c1 = makeCookie("sessionid", "s1234", -1, "localhost")
        // Cookie with max-age
        val c2 = makeCookie("geoid", "g1000", 30, "localhost")
        // Cookie with short max-age for testing expiration
        val c3 = makeCookie("fav", "choco", 1, "localhost")
        // Cookie with a specific domain
        val c4 = makeCookie("comp", "couchbase", 30, "www.couchbase.com")


        // Save each cookie in the store
        val uri = URI.create("http://localhost:4984/" + testDatabase.name)
        cookieStore.setCookies(uri, listOf(c1.toString(), c2.toString(), c3.toString(), c4.toString()), false)

        // Save couchbase domain cookie
        val cbUri = URI.create("http://www.couchbase.com/")
        cookieStore.setCookies(cbUri, listOf(c4.toString()), false)

        // Sleep for 2 seconds for the c3 cookie to expire
        Thread.sleep(2000)

        // Get localhost cookie
        var cookieHeader = cookieStore.getCookies(uri)
        Assert.assertNotNull(cookieHeader)
        var cookies = OkHttpSocket.parseCookies(uri.toHttpUrlOrNull()!!, cookieHeader!!)
        Assert.assertEquals(2, cookies.size)
        Assert.assertTrue(containsCookie(cookies, c1))
        Assert.assertTrue(containsCookie(cookies, c2))
        Assert.assertFalse(containsCookie(cookies, c3)) // Expired
        Assert.assertFalse(containsCookie(cookies, c4)) // Mismatched domain

        // Get couchbase domain cookie
        cookieHeader = cookieStore.getCookies(cbUri)
        Assert.assertNotNull(cookieHeader)
        cookies = OkHttpSocket.parseCookies(cbUri.toHttpUrlOrNull()!!, cookieHeader!!)
        Assert.assertEquals(1, cookies.size)
        Assert.assertTrue(containsCookie(cookies, c4))

        // After reopen db, the c1 cookie should be gone as it is a session cookie
        reopenTestDb()
        cookieHeader = AbstractReplicator.ReplicatorCookieStore(testDatabase).getCookies(uri)
        Assert.assertNotNull(cookies)
        cookies = OkHttpSocket.parseCookies(uri.toHttpUrlOrNull()!!, cookieHeader!!)
        Assert.assertEquals(1, cookies.size)
        Assert.assertTrue(containsCookie(cookies, c2))
    }

    @Test
    fun testParseCookies() {
        val uri = URI.create("http://www.couchbase.com/")

        // Empty
        var cookies = OkHttpSocket.parseCookies(uri.toHttpUrlOrNull()!!, "")
        Assert.assertNotNull(cookies)
        Assert.assertEquals(0, cookies.size)

        // Invalid
        cookies = OkHttpSocket.parseCookies(uri.toHttpUrlOrNull()!!, "cookie")
        Assert.assertNotNull(cookies)
        Assert.assertEquals(0, cookies.size)

        // One cookie
        cookies = OkHttpSocket.parseCookies(uri.toHttpUrlOrNull()!!, "a1=b1")
        Assert.assertNotNull(cookies)
        Assert.assertEquals(1, cookies.size)
        Assert.assertEquals("a1", cookies[0].name)
        Assert.assertEquals("b1", cookies[0].value)

        // Multiple cookies
        cookies = OkHttpSocket.parseCookies(uri.toHttpUrlOrNull()!!, "a1=b1; a2=b2")
        Assert.assertNotNull(cookies)
        Assert.assertEquals(2, cookies.size)
        Assert.assertEquals("a1", cookies[0].name)
        Assert.assertEquals("b1", cookies[0].value)
        Assert.assertEquals("a2", cookies[1].name)
        Assert.assertEquals("b2", cookies[1].value)
    }

    private fun makeCookie(name: String, value: String, maxAge: Long, domain: String?): Cookie {
        val builder = Cookie.Builder().name(name).value(value)
        if (maxAge >= 0) {
            builder.expiresAt(System.currentTimeMillis() + (maxAge * 1000))
        }
        if (domain != null) {
            builder.domain(domain)
        }
        return builder.build()
    }

    private fun containsCookie(list: List<Cookie>, cookie: Cookie): Boolean {
        return null != list.firstOrNull { (it.name == cookie.name) && (it.value == cookie.value) }
    }
}