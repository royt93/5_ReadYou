package com.mckimquyen.reader.ui.page.setting.backup

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.sv.OpmlSv
import com.mckimquyen.reader.infrastructure.backup.WebDavBackupManager
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
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
}
