package dev.mosaicobot

import kotlinx.coroutines.runBlocking
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class NetworkTest {
    @Test fun noProxyUsesOriginalClient() {
        val client=networkClient()
        assertSame(client,modelClient(client,AgentConfig()))
    }
    @Test fun debugProxyIsLoopbackAndKeepsTlsChecks() {
        val client=networkClient()
        val routed=modelClient(client,AgentConfig(debugProxyPort=18787))
        assertEquals(java.net.Proxy.Type.HTTP,routed.proxy!!.type())
        assertEquals(java.net.InetSocketAddress("127.0.0.1",18787),routed.proxy!!.address())
        assertSame(client.hostnameVerifier,routed.hostnameVerifier)
        assertSame(client.sslSocketFactory,routed.sslSocketFactory)
        assertNull(client.proxy)
    }
    @Test fun debugProxyRejectsUnapprovedServiceAndInvalidPort() {
        assertTrue(runCatching { modelClient(networkClient(),AgentConfig(baseUrl="https://example.com/v1",debugProxyPort=18787)) }.isFailure)
        assertTrue(runCatching { modelClient(networkClient(),AgentConfig(debugProxyPort=70000)) }.isFailure)
    }
    @Test fun responseSizeLimitIsEnforced() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("x".repeat(100)))
            val result=runCatching { networkClient().fetch(Request.Builder().url(server.url("/")).build(),32) }
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        }
    }
    @Test fun arbitraryErrorPayloadIsNotShown() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(403).setBody("sensitive-error-payload"))
            val result=runCatching { networkClient().fetch(Request.Builder().url(server.url("/")).build()) }
            assertTrue(result.exceptionOrNull()?.message?.contains("403") == true)
            assertFalse(result.exceptionOrNull()?.message?.contains("sensitive-error-payload") == true)
        }
    }
    @Test fun redirectIsNotFollowed() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location",server.url("/other")))
            val result=runCatching { networkClient().fetch(Request.Builder().url(server.url("/")).build()) }
            assertTrue(result.isFailure)
            assertEquals(1,server.requestCount)
        }
    }
    @Test fun timeoutIsClassifiedWithoutRequestDetails() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeadersDelay(2,TimeUnit.SECONDS).setBody("OK"))
            val client=networkClient().newBuilder().readTimeout(100,TimeUnit.MILLISECONDS).build()
            val result=runCatching { client.fetch(Request.Builder().url(server.url("/private-path")).build()) }
            val message=result.exceptionOrNull()?.message.orEmpty()
            assertTrue(message.contains("超时"))
            assertFalse(message.contains("private-path"))
        }
    }
}
