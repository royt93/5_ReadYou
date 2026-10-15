package com.mckimquyen.reader.infrastructure.rss.scraper

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Test

class WebFeedUrlPolicyTest {
    @Test
    fun acceptsPublicHttpsHostWithPublicDnsAnswers() {
        assertEquals(
            WebFeedUrlValidation.Valid,
            WebFeedUrlPolicy.validate("https://news.example/articles", listOf(address("93.184.216.34"))),
        )
    }

    @Test
    fun rejectsNonHttpsSchemesAndUrlCredentials() {
        listOf(
            "http://news.example/articles",
            "file://news.example/articles",
            "ftp://news.example/articles",
            "https://user:secret@news.example/articles",
            "https:///articles",
        ).forEach { url ->
            assertEquals(url, WebFeedUrlValidation.InvalidUrl, WebFeedUrlPolicy.validate(url, emptyList()))
        }
    }

    @Test
    fun rejectsLocalHostNamesAndLiteralPrivateAddresses() {
        listOf(
            "https://localhost/articles",
            "https://router.local/articles",
            "https://127.0.0.1/articles",
            "https://10.0.0.8/articles",
            "https://172.16.2.3/articles",
            "https://192.168.1.8/articles",
            "https://169.254.2.3/articles",
            "https://224.0.0.1/articles",
            "https://[::1]/articles",
            "https://[fc00::1]/articles",
            "https://[fe80::1]/articles",
            "https://[ff02::1]/articles",
        ).forEach { url ->
            assertEquals(url, WebFeedUrlValidation.UnsafeAddress, WebFeedUrlPolicy.validate(url, emptyList()))
        }
    }

    @Test
    fun rejectsNonPublicReservedIpv4AndIpv6Ranges() {
        listOf(
            "0.1.2.3",
            "192.88.99.1",
            "2001:db8::1",
            "2001:0::1",
            "2001:1::1",
            "2001:2::1",
            "2001:10::1",
            "2001:20::1",
            "2001:30::1",
            "2002::1",
            "2002:c0a8:0101::1",
            "64:ff9b::192.168.1.1",
            "64:ff9b:1::192.168.1.1",
            "64:ff9b:1:c0a8:0101::",
            "64:ff9b:1:0:c0a8:0101:0:0",
            "64:ff9b:1:0:0:c0a8:0101:0",
            "64:ff9b:1:0:0:0:c0a8:0101",
            "::ffff:192.168.1.1",
        ).forEach { ip ->
            assertEquals(ip, WebFeedUrlValidation.UnsafeAddress, WebFeedUrlPolicy.validate("https://news.example", listOf(address(ip))))
        }
    }

    @Test
    fun rejectsLocalhostSubdomainsAndTrailingDot() {
        listOf("localhost.", "feed.localhost", "router.home.arpa").forEach { host ->
            assertEquals(host, WebFeedUrlValidation.UnsafeAddress, WebFeedUrlPolicy.validate("https://$host/rss", emptyList()))
        }
    }

    @Test
    fun rejectsReservedIpv4Ranges() {
        listOf("100.64.0.1", "192.0.2.1", "198.51.100.1", "203.0.113.1", "240.0.0.1").forEach { ip ->
            assertEquals(ip, WebFeedUrlValidation.UnsafeAddress, WebFeedUrlPolicy.validate("https://news.example", listOf(address(ip))))
        }
    }

    @Test
    fun rejectsDnsAnswerSetContainingAnyUnsafeAddress() {
        assertEquals(
            WebFeedUrlValidation.UnsafeAddress,
            WebFeedUrlPolicy.validate(
                "https://news.example/articles",
                listOf(address("93.184.216.34"), address("10.2.3.4")),
            ),
        )
    }

    @Test
    fun rejectsMissingDnsAnswers() {
        assertEquals(
            WebFeedUrlValidation.DnsFailure,
            WebFeedUrlPolicy.validate("https://news.example/articles", emptyList()),
        )
    }

    private fun address(value: String): InetAddress = InetAddress.getByName(value)
}
