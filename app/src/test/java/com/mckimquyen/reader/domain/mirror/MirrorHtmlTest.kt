package com.mckimquyen.reader.domain.mirror

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MirrorHtmlTest {
    private val page = MirrorHtml.PAGE

    @Test fun buildsTheDomFromTextContentOnly() {
        assertTrue(page.contains("textContent"))
        listOf("innerHTML", "outerHTML", "insertAdjacentHTML", "document.write", "eval(", "new Function", "srcdoc").forEach {
            assertFalse(it, page.contains(it))
        }
    }

    @Test fun referencesNoExternalResource() {
        assertFalse(page.contains("http://"))
        assertFalse(page.contains("https://"))
        assertFalse(page.contains("//cdn", ignoreCase = true))
        assertFalse(Regex("""<(script|link|img|iframe|object|embed)[^>]+(src|href)\s*=""", RegexOption.IGNORE_CASE).containsMatchIn(page))
    }

    @Test fun usesTheCurrentPathSoTheTokenStaysInTheAddress() {
        assertTrue(page.contains("location.pathname"))
        assertTrue(page.contains("EventSource"))
        assertTrue(page.contains("'/state'") && page.contains("'/events'"))
    }

    @Test fun supportsLightAndDarkAndDeclaresTheDocumentLanguageAndViewport() {
        assertTrue(page.contains("prefers-color-scheme"))
        assertTrue(page.contains("<meta name=\"viewport\""))
        assertTrue(page.contains("<html lang=\"en\">"))
    }
}
