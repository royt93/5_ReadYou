package com.mckimquyen.reader.ui.page.share

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShareTargetIntegrationTest {
    @Test
    fun launchesActivityWithShareIntent() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Interesting read: https://example.com/test-article")
            setClass(context, ShareTargetActivity::class.java)
        }
        val scenario = ActivityScenario.launch<ShareTargetActivity>(intent)
        assertNotNull(scenario)
        scenario.close()
    }
}
