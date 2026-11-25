package com.mckimquyen.reader.ui.page.common

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A Google Reader / FreshRSS item id looks like `1$tag:google.com,2005:reader/item/000123`: it holds slashes, a dollar sign and a comma. The reading route
 * is `reading/{articleId}`, one path segment, so an unencoded id never matched and opening the article failed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReadingRouteIdTest {
    @get:Rule
    val rule = createComposeRule()

    private fun opened(id: String, route: (String) -> String = ::readingRoute, pattern: String = "${RouteName.READING}/{articleId}"): String? {
        var received: String? = null
        var nav: NavHostController? = null
        rule.setContent {
            val controller = rememberNavController()
            nav = controller
            NavHost(controller, startDestination = "home") {
                composable("home") { Box {} }
                composable(pattern) { entry ->
                    received = entry.arguments?.getString("articleId")
                    Box {}
                }
            }
            LaunchedEffect(Unit) { controller.navigate(route(id)) }
        }
        rule.waitForIdle()
        return received
    }

    @Test fun aFreshRssItemIdWithSlashesReachesTheReadingPageUnchanged() {
        val id = "1\$tag:google.com,2005:reader/item/000123"
        assertEquals(id, opened(id))
    }

    @Test fun anIdWithAQueryCharacterAndSpaceReachesTheReadingPageUnchanged() {
        val id = "a b?c=1&d#e%41"
        assertEquals(id, opened(id))
    }

    @Test fun theArticleDiffRouteAlsoSurvivesAFreshRssItemId() {
        val id = "1\$tag:google.com,2005:reader/item/000123"
        assertEquals(id, opened(id, ::articleDiffRoute, RouteName.ARTICLE_DIFF))
    }

    @Test fun aPlainIdIsUnchanged() {
        assertEquals("art-7", opened("art-7"))
    }
}
