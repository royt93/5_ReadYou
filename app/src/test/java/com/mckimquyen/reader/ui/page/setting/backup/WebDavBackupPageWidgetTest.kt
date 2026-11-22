package com.mckimquyen.reader.ui.page.setting.backup

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.sv.OpmlSv
import com.mckimquyen.reader.infrastructure.backup.WebDavBackupManager
import io.mockk.mockk
import io.mockk.coEvery
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WebDavBackupPageWidgetTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun webDavBackupPage_rendersHeadersAndFields() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val opmlSv = mockk<OpmlSv>(relaxed = true)
        val manager = WebDavBackupManager(context, opmlSv, mockk(relaxed = true), Dispatchers.Unconfined)
        val viewModel = WebDavBackupViewModel(context, manager)

        composeRule.setContent {
            WebDavBackupPage(
                navController = mockk(relaxed = true),
                activity = mockk(relaxed = true),
                viewModel = viewModel,
            )
        }

        // Note: "Backup Now"/"Restore from Cloud" sit below the fold inside a LazyColumn and are
        // not composed at Robolectric's default small test-window height, so only the always-visible
        // header/fields are asserted here.
        composeRule.onNodeWithText("WebDAV OPML Cloud Backup").assertIsDisplayed()
        composeRule.onNodeWithText("WebDAV Server URL").assertIsDisplayed()
        composeRule.onNodeWithText("Username").assertIsDisplayed()
    }

    // --- The restore button asks first (the server is not trusted, REL-01). Only "Restore" in the dialog may start it. ---

    private fun configuredPage(restores: IntArray): Unit {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val manager = io.mockk.mockk<WebDavBackupManager>(relaxed = true)
        io.mockk.every { manager.getConfig() } returns com.mckimquyen.reader.infrastructure.backup.WebDavConfig(
            serverUrl = "https://dav.test/", username = "u", password = "p", lastBackupTime = null,
        )
        coEvery { manager.restore() } coAnswers { restores[0]++; Result.success(Unit) }
        val viewModel = WebDavBackupViewModel(context, manager)
        composeRule.setContent {
            WebDavBackupPage(navController = mockk(relaxed = true), activity = mockk(relaxed = true), viewModel = viewModel)
        }
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(context.getString(com.mckimquyen.reader.R.string.webdav_restore_now)))
    }

    @Test
    fun tappingRestore_opensTheDialog_andDoesNotRestoreYet() {
        val restores = intArrayOf(0)
        configuredPage(restores)
        val context = ApplicationProvider.getApplicationContext<Application>()
        composeRule.onNodeWithText(context.getString(com.mckimquyen.reader.R.string.webdav_restore_now)).performClick()
        composeRule.onNodeWithText(context.getString(com.mckimquyen.reader.R.string.webdav_restore_confirm_title)).assertIsDisplayed()
        composeRule.waitForIdle()
        assertEquals("a tap on the button alone must not restore", 0, restores[0])
    }

    @Test
    fun cancellingTheDialog_neverRestores() {
        val restores = intArrayOf(0)
        configuredPage(restores)
        val context = ApplicationProvider.getApplicationContext<Application>()
        composeRule.onNodeWithText(context.getString(com.mckimquyen.reader.R.string.webdav_restore_now)).performClick()
        composeRule.onNodeWithText(context.getString(com.mckimquyen.reader.R.string.cancel)).performClick()
        composeRule.onNodeWithText(context.getString(com.mckimquyen.reader.R.string.webdav_restore_confirm_title)).assertDoesNotExist()
        composeRule.waitForIdle()
        assertEquals(0, restores[0])
    }

    @Test
    fun confirmingTheDialog_restoresExactlyOnce() {
        val restores = intArrayOf(0)
        configuredPage(restores)
        val context = ApplicationProvider.getApplicationContext<Application>()
        composeRule.onNodeWithText(context.getString(com.mckimquyen.reader.R.string.webdav_restore_now)).performClick()
        composeRule.onNodeWithText(context.getString(com.mckimquyen.reader.R.string.webdav_restore_confirm_action)).performClick()
        composeRule.waitForIdle()
        assertEquals(1, restores[0])
    }
}
