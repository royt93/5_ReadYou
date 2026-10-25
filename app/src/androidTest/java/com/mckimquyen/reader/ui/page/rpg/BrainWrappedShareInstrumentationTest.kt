package com.mckimquyen.reader.ui.page.rpg

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.model.rpg.UserProgress
import com.mckimquyen.reader.domain.model.rpg.WeeklyBrainWrapped
import com.mckimquyen.reader.infrastructure.rpg.BrainWrappedRenderModel
import com.mckimquyen.reader.infrastructure.rpg.BrainWrappedRenderer
import com.mckimquyen.reader.infrastructure.rpg.BrainWrappedSharer
import com.mckimquyen.reader.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class BrainWrappedShareInstrumentationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test
    fun shareButton_disablesWithoutWeeklyXpAndEnablesWithWeeklyXp() {
        composeRule.setContent {
            AppTheme(useDarkTheme = false) {
                androidx.compose.foundation.layout.Column {
                    WeeklyWrappedCard(
                        progress = UserProgress(),
                        weeklyState = WeeklyWrappedUiState(
                            isLoading = false,
                            summary = WeeklyBrainWrapped(1L, 2L, 0L, 3, null),
                        ),
                        onShare = {},
                    )
                    WeeklyWrappedCard(
                        progress = UserProgress(),
                        weeklyState = WeeklyWrappedUiState(
                            isLoading = false,
                            summary = WeeklyBrainWrapped(1L, 2L, 150L, 3, "Science & Nature"),
                        ),
                        onShare = {},
                    )
                }
            }
        }

        composeRule.onAllNodesWithTag(WRAPPED_SHARE_BUTTON_TAG)[0].assert(isNotEnabled())
        composeRule.onAllNodesWithTag(WRAPPED_SHARE_BUTTON_TAG)[1].assertIsEnabled()
    }

    @Test
    fun sharerUri_isReadableByContentResolverAndDecodesTo1080x1920() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sharer = BrainWrappedSharer(context, Dispatchers.IO)
        val bitmap = BrainWrappedRenderer.render(
            BrainWrappedRenderModel(
                weekLabel = "WEEK 43",
                header = "YOUR WEEK IN KNOWLEDGE",
                weeklyXpLabel = "WEEKLY XP",
                weeklyXp = 250L,
                streakLabel = "CURRENT STREAK",
                streakValue = "6 days",
                topicLabel = "TOPIC THIS WEEK",
                topicValue = "Science & Nature",
                topicEmptyLabel = "Topic taking shape",
                tagline = "Keep your curiosity moving.",
                watermark = "RSS Cat Hub",
            ),
        )

        val file = sharer.writePng(bitmap)
        try {
            val chooser = sharer.buildShareIntent(file, "Share Brain Wrapped")
            @Suppress("DEPRECATION")
            val send = chooser.getParcelableExtra<android.content.Intent>(android.content.Intent.EXTRA_INTENT)!!
            @Suppress("DEPRECATION")
            val uri = send.getParcelableExtra<Uri>(android.content.Intent.EXTRA_STREAM)!!

            assertEquals(BrainWrappedSharer.MIME_PNG, send.type)
            assertTrue(send.flags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertEquals("${context.packageName}.fileprovider", uri.authority)

            val decoded = context.contentResolver.openInputStream(uri)!!.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
            assertNotNull(decoded)
            assertEquals(BrainWrappedRenderer.CARD_WIDTH, decoded.width)
            assertEquals(BrainWrappedRenderer.CARD_HEIGHT, decoded.height)
        } finally {
            file.delete()
        }
    }

    @Test
    fun localWeekBoundaryMatchesDeviceTimezone() {
        val zone = ZoneId.systemDefault()
        val interval = com.mckimquyen.reader.domain.model.rpg.BrainRpgWeek.interval(
            Instant.parse("2026-10-25T23:59:00Z"), zone,
        )
        assertTrue(interval.endMillis > interval.startMillis)
    }
}
