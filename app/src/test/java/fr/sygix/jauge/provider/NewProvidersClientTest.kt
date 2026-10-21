/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.jauge.provider

import fr.sygix.jauge.auth.Pkce
import fr.sygix.jauge.model.Credentials
import fr.sygix.jauge.model.UsageError
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class NewProvidersClientTest {
    private val server = MockWebServer()
    private val now = 1_792_584_000_000L
    private val key = Credentials("test-key")
    private val quota = """{"product":"go","renewalProduct":"go-plus","access":{"meters":{"fiveHour":{"usedMicroCents":"25","limitMicroCents":"100"}}}}"""

    @After
    fun close() = server.shutdown()

    private fun body(value: String, status: Int = 200) = MockResponse().setResponseCode(status).setBody(value).setHeader("Content-Type", "application/json")

    @Test
    fun openRouterOptionalCreditsDoNotBlockKeyUsage() = runBlocking {
        server.enqueue(body("""{"data":{"usage":5,"limit":10,"limit_remaining":5}}"""))
        server.enqueue(body("{}", 403))
        val snapshot = OpenRouterClient(server.url("/").toString().trimEnd('/')).fetch(key, now)
        assertEquals(50.0, snapshot.primary!!.usedPercent, 0.001)
        assertNull(snapshot.spend!!.balance)
        val request = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("/api/v1/key", request.path)
        assertEquals("Bearer test-key", request.getHeader("Authorization"))
        assertFalse(request.path!!.contains(key.accessToken))
    }

    @Test
    fun openRouterBrowserLoginBindsPkceAndState() {
        val pkce = Pkce.create()
        val url = OpenRouterAuth.authorizeUrl(pkce, "test-state", OpenRouterAuth.loopbackRedirectUri(45123)).toHttpUrl()
        assertEquals("https", url.scheme)
        assertEquals("openrouter.ai", url.host)
        assertEquals(pkce.challenge, url.queryParameter("code_challenge"))
        assertEquals("S256", url.queryParameter("code_challenge_method"))
        assertEquals("test-state", url.queryParameter("state"))
        assertEquals("http://localhost:45123/callback", url.queryParameter("callback_url"))
        assertFalse(url.toString().contains(pkce.verifier))
    }

    @Test
    fun openCodeGoKeyUsesTheQuotaApiWithoutBrowserCookies() = runBlocking {
        server.enqueue(body("""{"product":"go-plus","usage":{"rolling":{"percent":0.5}}}"""))
        val snapshot = OpenCodeClient(server.url("/").toString().trimEnd('/')).fetch(key, now)
        assertEquals("Go Plus", snapshot.plan)
        assertEquals(0.5, snapshot.session!!.usedPercent, 0.001)
        assertEquals(1, server.requestCount)
        val request = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("/zen/go/v1/usage", request.path)
        assertEquals("Bearer test-key", request.getHeader("Authorization"))
        assertNull(request.getHeader("Cookie"))
    }

    @Test
    fun openCodeBrowserSessionScopesQuotaAndBalanceToTheAuthorizedWorkspace() = runBlocking {
        server.enqueue(body(quota))
        server.enqueue(body("{}", 403))
        val credentials = Credentials("test-access", refreshToken = "test-refresh", accountId = "org_test")
        val snapshot = OpenCodeClient(server.url("/").toString().trimEnd('/')).fetch(credentials, now)
        assertEquals("Go", snapshot.plan)
        assertEquals(25.0, snapshot.session!!.usedPercent, 0.001)
        assertEquals(2, server.requestCount)
        repeat(2) {
            val request = server.takeRequest(1, TimeUnit.SECONDS)!!
            assertEquals("org_test", request.getHeader("x-org-id"))
            assertEquals("Bearer test-access", request.getHeader("Authorization"))
        }
    }

    @Test
    fun openCodeConsoleKeyFallsBackToScopedThirtyDaySpend() = runBlocking {
        server.enqueue(body("{}", 401))
        server.enqueue(body("""{"id":"org_test"}"""))
        server.enqueue(body("null"))
        server.enqueue(body("""{"mode":"pay-as-you-go","balanceMicroCents":"250000000"}"""))
        server.enqueue(body("""{"totalCostMicroCents":"1000000000"}"""))
        val snapshot = OpenCodeClient(server.url("/").toString().trimEnd('/')).fetch(key, now)
        assertEquals("Zen", snapshot.plan)
        assertEquals(10.0, snapshot.spend!!.used!!.amount, 0.001)
        assertTrue(snapshot.windows.isEmpty())
        val requests = (1..5).map { server.takeRequest(1, TimeUnit.SECONDS)!! }
        assertEquals("/console/api/usage/summary?range=30d", requests.last().path)
        assertEquals("org_test", requests.last().getHeader("x-org-id"))
    }

    @Test
    fun openCodePermissionFailureDoesNotMasqueradeAsAnExpiredSession() = runBlocking {
        server.enqueue(body("{}", 403))
        val credentials = Credentials("test-access", refreshToken = "test-refresh", accountId = "org_test")
        try {
            OpenCodeClient(server.url("/").toString().trimEnd('/')).fetch(credentials, now)
            fail("Expected a permission failure")
        } catch (e: IOException) {
            assertEquals(UsageError.PERMISSION, e.message)
        }
    }
}
