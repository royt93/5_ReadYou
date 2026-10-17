package com.mckimquyen.reader.infrastructure.newsletter

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

object NewsletterSanitizer {

    private val TRACKING_PATTERNS = listOf(
        "open.substack.com",
        "email.ghost.org",
        "beehiiv.com/track",
        "/track/",
        "/pixel",
        "1x1",
        "mailtrack",
        "doubleclick.net"
    )

    private val JUNK_SELECTORS = listOf(
        // Substack widgets
        ".subscription-widget-wrap",
        ".subscribe-widget",
        "div[class*=\"subscribe-form\"]",
        ".side-comment-widget",
        ".button-wrapper:has(a[href*=\"subscribe\"])",
        "div:has(> a[href*=\"substack.com/subscribe\"])",
        // Ghost widgets
        ".gh-post-upgrade-cta",
        ".gh-footer-signup",
        ".gh-portal-close-button",
        "form[action*=\"members/api\"]",
        // Beehiiv widgets
        ".subscribe_form",
        "div[class*=\"subscribe_form\"]",
        "form[action*=\"beehiiv.com\"]",
        // Generic email signup forms
        "form:has(input[type=\"email\"])",
        "div[class*=\"email-form\"]",
        "div[class*=\"newsletter-signup\"]"
    )

    private val UNWANTED_TEXT_PATTERNS = listOf(
        "you're reading this because",
        "you are reading this because",
        "unsubscribe from this list",
        "update your preferences",
        "manage your subscription",
        "manage subscription",
        "powered by substack",
        "view this post in your browser",
        "view in browser",
        "leave a comment",
        "restack"
    )

    private const val MAX_TRACKING_PIXEL_DIMENSION = 2

    fun cleanArticleHtml(html: String?, platform: NewsletterPlatform = NewsletterPlatform.GENERIC): String {
        if (html.isNullOrBlank()) return ""

        val doc: Document = Jsoup.parseBodyFragment(html)
        val body = doc.body()

        removeTrackingPixels(body)
        removeJunkWidgets(body)
        removeUnsubscribeAndFooters(body)

        return body.html().trim()
    }

    private fun removeTrackingPixels(root: Element) {
        val images = root.select("img")
        for (img in images) {
            val src = img.attr("src").lowercase()
            val widthAttr = img.attr("width").toIntOrNull()
            val heightAttr = img.attr("height").toIntOrNull()

            val isTinyPixel = (widthAttr != null && widthAttr <= MAX_TRACKING_PIXEL_DIMENSION) ||
                    (heightAttr != null && heightAttr <= MAX_TRACKING_PIXEL_DIMENSION)

            val matchesTrackingUrl = TRACKING_PATTERNS.any { pattern -> src.contains(pattern) }

            val style = img.attr("style").lowercase()
            val isHidden = style.contains("display:none") || style.contains("display: none") ||
                    style.contains("visibility:hidden") || style.contains("visibility: hidden")

            if (isTinyPixel || matchesTrackingUrl || (isHidden && (src.contains("track") || src.contains("pixel")))) {
                img.remove()
            }
        }
    }

    private fun removeJunkWidgets(root: Element) {
        for (selector in JUNK_SELECTORS) {
            root.select(selector).remove()
        }
    }

    private fun removeUnsubscribeAndFooters(root: Element) {
        // Remove explicit unsubscribe links and their direct parents if short
        val unsubLinks = root.select("a[href*=\"unsubscribe\"], a[href*=\"manage-subscription\"], a[href*=\"email-preferences\"]")
        for (link in unsubLinks) {
            val parent = link.parent()
            if (parent != null && parent != root && parent.text().length < 150) {
                parent.remove()
            } else {
                link.remove()
            }
        }

        // Remove paragraphs or footers matching boilerplate text
        val candidateBlocks = root.select("p, small, footer, div, section")
        for (block in candidateBlocks) {
            // Do not remove large container blocks that contain multiple paragraphs or sub-elements
            if (block.select("p, h1, h2, h3, h4, h5, h6, blockquote, pre, ul, ol").isNotEmpty() && block.tagName() in listOf("div", "section")) {
                continue
            }
            val text = block.text().lowercase().trim()
            if (text.isNotBlank() && text.length < 300) {
                if (UNWANTED_TEXT_PATTERNS.any { text.contains(it) }) {
                    block.remove()
                }
            }
        }
    }
}
