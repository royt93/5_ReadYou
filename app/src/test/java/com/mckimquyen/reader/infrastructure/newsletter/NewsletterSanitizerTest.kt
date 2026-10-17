package com.mckimquyen.reader.infrastructure.newsletter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsletterSanitizerTest {

    @Test
    fun cleanArticleHtml_removesTrackingPixels() {
        val rawHtml = """
            <article>
                <p>Hello world from Substack!</p>
                <img src="https://open.substack.com/sample.gif" width="1" height="1" />
                <img src="https://email.ghost.org/track/open.png" style="display:none;" />
                <img src="https://newsletter.beehiiv.com/track/pixel.gif" />
                <img src="https://example.com/good-photo.jpg" width="800" height="600" alt="Main photo" />
            </article>
        """.trimIndent()

        val cleaned = NewsletterSanitizer.cleanArticleHtml(rawHtml, NewsletterPlatform.SUBSTACK)

        assertTrue(cleaned.contains("Hello world from Substack!"))
        assertTrue(cleaned.contains("good-photo.jpg"))
        assertFalse(cleaned.contains("open.substack.com"))
        assertFalse(cleaned.contains("email.ghost.org"))
        assertFalse(cleaned.contains("beehiiv.com/track"))
    }

    @Test
    fun cleanArticleHtml_removesSubstackSubscriptionWidgets() {
        val rawHtml = """
            <div>
                <p>Leading analysis on software engineering.</p>
                <div class="subscription-widget-wrap">
                    <form class="subscribe-form">
                        <input type="email" placeholder="Type your email..." />
                        <button type="submit">Subscribe</button>
                    </form>
                </div>
                <div class="side-comment-widget">Leave a comment</div>
                <p>More deep technical thoughts below.</p>
                <div class="button-wrapper">
                    <a href="https://danluu.substack.com/subscribe">Subscribe now</a>
                </div>
            </div>
        """.trimIndent()

        val cleaned = NewsletterSanitizer.cleanArticleHtml(rawHtml, NewsletterPlatform.SUBSTACK)

        assertTrue(cleaned.contains("Leading analysis on software engineering."))
        assertTrue(cleaned.contains("More deep technical thoughts below."))
        assertFalse(cleaned.contains("subscription-widget-wrap"))
        assertFalse(cleaned.contains("subscribe-form"))
        assertFalse(cleaned.contains("Type your email..."))
        assertFalse(cleaned.contains("Leave a comment"))
        assertFalse(cleaned.contains("Subscribe now"))
    }

    @Test
    fun cleanArticleHtml_removesGhostAndBeehiivCta() {
        val rawHtml = """
            <div class="gh-content">
                <p>Ghost publication article content.</p>
                <div class="gh-post-upgrade-cta">
                    <h2>This post is for paying subscribers</h2>
                    <a class="gh-btn" href="#/portal/signup">Upgrade now</a>
                </div>
                <div class="subscribe_form">
                    <input type="email" />
                </div>
                <p>Conclusion of the newsletter.</p>
            </div>
        """.trimIndent()

        val cleaned = NewsletterSanitizer.cleanArticleHtml(rawHtml, NewsletterPlatform.GHOST)

        assertTrue(cleaned.contains("Ghost publication article content."))
        assertTrue(cleaned.contains("Conclusion of the newsletter."))
        assertFalse(cleaned.contains("gh-post-upgrade-cta"))
        assertFalse(cleaned.contains("Upgrade now"))
        assertFalse(cleaned.contains("subscribe_form"))
    }

    @Test
    fun cleanArticleHtml_removesUnsubscribeAndFooterBoilerplate() {
        val rawHtml = """
            <div>
                <p>Key takeaway of this week edition.</p>
                <p>You're reading this because you subscribed to Tech Insights.</p>
                <p><a href="https://substack.com/account/unsubscribe?token=123">Unsubscribe</a> from this list.</p>
                <p><a href="https://example.com/manage-subscription">Update your preferences</a></p>
                <p>Powered by Substack. 548 Market Street, San Francisco.</p>
            </div>
        """.trimIndent()

        val cleaned = NewsletterSanitizer.cleanArticleHtml(rawHtml, NewsletterPlatform.SUBSTACK)

        assertTrue(cleaned.contains("Key takeaway of this week edition."))
        assertFalse(cleaned.contains("You're reading this because you subscribed"))
        assertFalse(cleaned.contains("Unsubscribe"))
        assertFalse(cleaned.contains("Update your preferences"))
        assertFalse(cleaned.contains("Powered by Substack"))
    }

    @Test
    fun cleanArticleHtml_preservesLegitimateCodeAndFormatting() {
        val rawHtml = """
            <div>
                <h1>Deep Dive</h1>
                <p>Here is an inline <code>val x = 42</code> example.</p>
                <pre><code>fun greet() = println("Hello")</code></pre>
                <blockquote>Important quote from Dijkstra.</blockquote>
                <ul>
                    <li>Point 1</li>
                    <li>Point 2</li>
                </ul>
            </div>
        """.trimIndent()

        val cleaned = NewsletterSanitizer.cleanArticleHtml(rawHtml, NewsletterPlatform.GENERIC)

        assertTrue(cleaned.contains("<h1>Deep Dive</h1>") || cleaned.contains("Deep Dive"))
        assertTrue(cleaned.contains("val x = 42"))
        assertTrue(cleaned.contains("fun greet() = println(\"Hello\")"))
        assertTrue(cleaned.contains("<blockquote>Important quote from Dijkstra.</blockquote>") || cleaned.contains("Important quote from Dijkstra."))
        assertTrue(cleaned.contains("Point 1"))
        assertTrue(cleaned.contains("Point 2"))
    }

    @Test
    fun cleanArticleHtml_handlesBlankOrNullGracefully() {
        assertEquals("", NewsletterSanitizer.cleanArticleHtml("", NewsletterPlatform.GENERIC))
        assertEquals("", NewsletterSanitizer.cleanArticleHtml("   ", NewsletterPlatform.SUBSTACK))
    }
}
