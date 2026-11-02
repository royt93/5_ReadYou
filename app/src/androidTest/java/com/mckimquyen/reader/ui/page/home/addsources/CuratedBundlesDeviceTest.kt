package com.mckimquyen.reader.ui.page.home.addsources

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.reader.domain.curated.CuratedBundle
import com.mckimquyen.reader.domain.curated.CuratedBundleLoader
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CuratedBundlesDeviceTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** Reads the asset packaged in the real APK, so a missing or broken `curated_bundles.json` fails here. */
    private fun shippedBundles(): List<CuratedBundle> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return CuratedBundleLoader.load(context.assets.open("curated_bundles.json"))
    }

    @Test
    fun packagedAsset_hasFiveBundlesAndTheSubscribeButtonReportsTheTappedBundle() {
        val bundles = shippedBundles()
        assertEquals(5, bundles.size)
        var subscribed: String? = null
        compose.setContent {
            CuratedBundlesSection(
                state = AddSourcesUiState(bundles = bundles, canSubscribeBundles = true),
                language = "en",
                onSubscribe = { subscribed = it.id },
                onDismissResult = {},
            )
        }

        compose.onNodeWithText("Technology & AI").assertIsDisplayed()
        compose.onNodeWithTag(CURATED_BUNDLE_SUBSCRIBE_TAG_PREFIX + "tech_ai").performClick()

        assertEquals("tech_ai", subscribed)
    }
}
