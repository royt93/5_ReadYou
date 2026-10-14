package com.mckimquyen.reader.infrastructure.audio

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaSearchManifestIntegrationTest {
    @Test fun mediaPlayFromSearch_resolvesOnlyToTheExportedCommuteMediaSessionService() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent("android.media.action.MEDIA_PLAY_FROM_SEARCH").apply { setPackage(context.packageName) }
        val resolved = context.packageManager.queryIntentServices(intent, 0)
        assertEquals(1, resolved.size)
        assertEquals(CommuteMediaSessionService::class.java.name, resolved.single().serviceInfo.name)
        assertEquals(context.packageName, resolved.single().serviceInfo.packageName)
    }
}
