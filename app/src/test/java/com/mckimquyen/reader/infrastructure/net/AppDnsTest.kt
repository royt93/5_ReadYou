package com.mckimquyen.reader.infrastructure.net

import com.mckimquyen.reader.infrastructure.pref.DohProviderPref
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Dns
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.net.UnknownHostException

class AppDnsTest {

    private lateinit var server: MockWebServer
    private val fakeSystemDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            return if (hostname == "example.com") {
                listOf(InetAddress.getByName("93.184.216.34"))
            } else {
                throw UnknownHostException("Not found: $hostname")
            }
        }
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun lookup_whenSystemDns_delegatesDirectlyWithoutNetworkCall() {
        val appDns = AppDns(
            dohProviderSupplier = { DohProviderPref.SYSTEM },
            bootstrapClient = OkHttpClient(),
            systemDns = fakeSystemDns,
        )

        val addresses = appDns.lookup("example.com")
        assertEquals(1, addresses.size)
        assertEquals("93.184.216.34", addresses[0].hostAddress)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun lookup_whenIpLiteral_returnsAddressWithoutDnsQuery() {
        val appDns = AppDns(
            dohProviderSupplier = { DohProviderPref.CLOUDFLARE },
            bootstrapClient = OkHttpClient(),
            systemDns = fakeSystemDns,
        )

        val addresses = appDns.lookup("1.1.1.1")
        assertEquals(1, addresses.size)
        assertEquals("1.1.1.1", addresses[0].hostAddress)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun lookup_whenDohSucceeds_parsesDnsJsonResponse() {
        val dnsJsonResponse = """
            {
              "Status": 0,
              "Answer": [
                {"name": "example.com", "type": 1, "TTL": 300, "data": "104.16.1.1"}
              ]
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(dnsJsonResponse))

        val customDoh = object : DohProviderPref(1, "MockDoH", server.url("/dns-query").toString(), "127.0.0.1") {}
        val appDns = AppDns(
            dohProviderSupplier = { customDoh },
            bootstrapClient = OkHttpClient(),
            systemDns = fakeSystemDns,
        )

        val addresses = appDns.lookup("example.com")
        assertEquals(1, addresses.size)
        assertEquals("104.16.1.1", addresses[0].hostAddress)

        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("application/dns-json", recorded.headers["Accept"])
        assertTrue(recorded.path?.contains("name=example.com") == true)
    }

    @Test
    fun lookup_whenDohFailsWithHttpError_fallsBackToSystemDns() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Server Error"))

        val customDoh = object : DohProviderPref(1, "MockDoH", server.url("/dns-query").toString(), "127.0.0.1") {}
        val appDns = AppDns(
            dohProviderSupplier = { customDoh },
            bootstrapClient = OkHttpClient(),
            systemDns = fakeSystemDns,
        )

        val addresses = appDns.lookup("example.com")
        assertEquals(1, addresses.size)
        assertEquals("93.184.216.34", addresses[0].hostAddress)
    }
}
