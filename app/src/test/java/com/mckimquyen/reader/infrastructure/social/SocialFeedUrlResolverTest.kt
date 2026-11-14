package com.mckimquyen.reader.infrastructure.social

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialFeedUrlResolverTest {

    private fun ok(input: String, expected: String) = assertEquals(input, expected, SocialFeedUrlResolver.resolve(input))
    private fun no(input: String) = assertNull("'$input' must not be recognised", SocialFeedUrlResolver.resolve(input))

    // --- Mastodon, @name@server
    @Test fun mastodonHandle() = ok("@Mastodon@mastodon.social", "https://mastodon.social/@Mastodon.rss")
    @Test fun mastodonHandle_serverIsLowerCased() = ok("@user@Mastodon.Social", "https://mastodon.social/@user.rss")
    @Test fun mastodonHandle_nameKeepsItsCase() = ok("@MixedCase@example.org", "https://example.org/@MixedCase.rss")
    @Test fun mastodonHandle_dotsAndDashesInName() = ok("@a.b-c_d@fosstodon.org", "https://fosstodon.org/@a.b-c_d.rss")
    @Test fun mastodonHandle_surroundingSpacesAreTrimmed() = ok("  @Mastodon@mastodon.social \n", "https://mastodon.social/@Mastodon.rss")

    // --- Mastodon, profile link
    @Test fun mastodonLink() = ok("https://mastodon.social/@Mastodon", "https://mastodon.social/@Mastodon.rss")
    @Test fun mastodonLink_trailingSlash() = ok("https://mastodon.social/@Mastodon/", "https://mastodon.social/@Mastodon.rss")
    @Test fun mastodonLink_queryAndFragmentAreIgnored() = ok("https://mastodon.social/@Mastodon?foo=1#top", "https://mastodon.social/@Mastodon.rss")
    @Test fun mastodonLink_uppercaseHost() = ok("https://Mastodon.Social/@Mastodon", "https://mastodon.social/@Mastodon.rss")
    @Test fun mastodonLink_anyOtherServer() = ok("https://fosstodon.org/@someone", "https://fosstodon.org/@someone.rss")

    // --- Bluesky, @handle
    @Test fun blueskyHandle() = ok("@bsky.app", "https://bsky.app/profile/bsky.app/rss")
    @Test fun blueskyHandle_withBskySocial() = ok("@alice.bsky.social", "https://bsky.app/profile/alice.bsky.social/rss")
    @Test fun blueskyHandle_isLowerCased() = ok("@Alice.BSKY.Social", "https://bsky.app/profile/alice.bsky.social/rss")
    @Test fun blueskyHandle_customDomain() = ok("@benharri.org", "https://bsky.app/profile/benharri.org/rss")

    // --- Bluesky, profile link
    @Test fun blueskyLink() = ok("https://bsky.app/profile/bsky.app", "https://bsky.app/profile/bsky.app/rss")
    @Test fun blueskyLink_trailingSlash() = ok("https://bsky.app/profile/bsky.app/", "https://bsky.app/profile/bsky.app/rss")
    @Test fun blueskyLink_alreadyRss() = ok("https://bsky.app/profile/bsky.app/rss", "https://bsky.app/profile/bsky.app/rss")
    @Test fun blueskyLink_didPlc() = ok("https://bsky.app/profile/did:plc:z72i7hdynmk6r22z27h6tvur", "https://bsky.app/profile/did:plc:z72i7hdynmk6r22z27h6tvur/rss")
    @Test fun blueskyLink_queryIgnored() = ok("https://bsky.app/profile/bsky.app?x=1", "https://bsky.app/profile/bsky.app/rss")
    @Test fun blueskyLink_uppercaseHost() = ok("https://BSKY.APP/profile/bsky.app", "https://bsky.app/profile/bsky.app/rss")

    // --- things that must NOT be taken over (the old flow keeps them)
    @Test fun empty_and_blank() { no(""); no("   ") }
    @Test fun plainWordsAndSites() { no("hello"); no("example.com"); no("https://example.com"); no("https://example.com/feed.xml") }
    @Test fun bareBlueskyDomain_withoutAt_isNotBluesky() { no("bsky.app"); no("alice.bsky.social") }
    @Test fun email_isNotAHandle() { no("a@b.com"); no("someone@mastodon.social") }
    @Test fun atWithoutADot_isNotBluesky() { no("@abc"); no("@alice") }
    @Test fun atWithEmptyParts() { no("@"); no("@@"); no("@a@"); no("@@mastodon.social"); no("@user@") }
    @Test fun tooManyAts() { no("@a@b.com@c.com"); no("@a@@b.com") }
    @Test fun serverWithoutADot_isRejected() { no("@user@localhost"); no("https://localhost/@user") }
    @Test fun plainHttp_isRejected_neverDowngradeToHttp() { no("http://mastodon.social/@Mastodon"); no("http://bsky.app/profile/bsky.app") }
    @Test fun otherSchemes_areRejected() { no("ftp://mastodon.social/@Mastodon"); no("file:///@Mastodon"); no("javascript:alert(1)") }
    @Test fun credentialsInTheLink_areRejected() { no("https://user:pass@mastodon.social/@Mastodon"); no("https://user@bsky.app/profile/bsky.app") }
    @Test fun customPort_isRejected() { no("https://mastodon.social:8443/@Mastodon"); no("https://bsky.app:444/profile/bsky.app") }
    @Test fun extraPathSegments_areRejected() {
        no("https://mastodon.social/@Mastodon/117466985005297414")
        no("https://mastodon.social/@Mastodon/with_replies")
        no("https://bsky.app/profile/bsky.app/post/3n27yge3hys24")
        no("https://bsky.app/profile")
        no("https://bsky.app/")
    }
    @Test fun blueskyLinksThatAreNotProfiles_areRejected() {
        no("https://bsky.app/other/bsky.app")
        no("https://bsky.app/feed/xyz")
        no("https://bsky.app/@bsky.app/rss")
        no("https://bsky.app/search/rss")
    }
    @Test fun aDid_isKeptExactlyAsGiven_becauseItsCaseMatters() {
        ok("https://bsky.app/profile/did:plc:ABCdef123", "https://bsky.app/profile/did:plc:ABCdef123/rss")
        ok("https://bsky.app/profile/did:web:Example.Com", "https://bsky.app/profile/did:web:Example.Com/rss")
    }
    @Test fun anUnknownDidMethod_isRejected() { no("https://bsky.app/profile/did:key:abc"); no("https://bsky.app/profile/did:") }
    @Test fun hashtags_andLists_areNotAccounts() { no("https://mastodon.social/tags/rss"); no("https://mastodon.social/@Mastodon/media") }
    @Test fun illegalCharactersInNames() {
        no("@bad name@mastodon.social"); no("@bad/name@mastodon.social"); no("@bad%20@mastodon.social")
        no("https://mastodon.social/@bad name"); no("https://mastodon.social/@bad%2Fname")
        no("@evil.com/path"); no("@evil.com?x=1"); no("@evil.com#x")
    }
    @Test fun invalidDomainLabels() { no("@user@-bad.example.com"); no("@user@bad-.example.com"); no("@user@a..b.com"); no("@.bsky.app"); no("@bsky.app.") }

    // --- other platforms that also use /@name and must keep their own handling
    @Test fun otherPlatformsThatUseAtPaths_areLeftAlone() {
        no("https://medium.com/@someone")
        no("https://someone.medium.com/@x")
        no("https://substack.com/@someone")
        no("https://www.youtube.com/@channel")
        no("https://youtu.be/@x")
        no("https://www.tiktok.com/@user")
        no("https://www.instagram.com/@user")
        no("https://x.com/@user")
        no("https://twitter.com/@user")
        no("https://www.threads.net/@user")
    }
    @Test fun excludedList_matchesWholeDomainsOnly_notLookalikes() {
        // "notmedium.com" is a different site and a possible Mastodon server; only the real suffix is excluded.
        ok("https://notmedium.com/@someone", "https://notmedium.com/@someone.rss")
        ok("https://example.com/@someone", "https://example.com/@someone.rss")
    }
    @Test fun excludedServer_inHandleForm_isAlsoLeftAlone() { no("@someone@medium.com"); no("@someone@youtube.com") }

    // --- safety of the result
    @Test fun theResultIsAlwaysHttps_andHasNoUserInfoOrQuery() {
        val inputs = listOf("@Mastodon@mastodon.social", "https://mastodon.social/@Mastodon?x=1#y", "@bsky.app", "https://bsky.app/profile/bsky.app?x=1")
        for (i in inputs) {
            val out = SocialFeedUrlResolver.resolve(i)!!
            assertTrue(out, out.startsWith("https://"))
            assertTrue(out, '?' !in out && '#' !in out && '@' !in out.removePrefix("https://").substringBefore('/'))
        }
    }
    @Test fun resolvingTheResultAgain_isStable_orNull() {
        // A result is an RSS address; feeding it back must not produce a different, wrong address.
        val rss = SocialFeedUrlResolver.resolve("@Mastodon@mastodon.social")!!
        assertNull(rss, SocialFeedUrlResolver.resolve(rss))   // the name "Mastodon.rss" ends in .rss, so it is not taken as a profile
        val b = SocialFeedUrlResolver.resolve("@bsky.app")!!
        assertEquals(b, SocialFeedUrlResolver.resolve(b))            // Bluesky RSS link is idempotent
    }
}
