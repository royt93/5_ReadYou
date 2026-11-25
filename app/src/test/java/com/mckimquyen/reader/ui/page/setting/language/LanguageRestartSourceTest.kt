package com.mckimquyen.reader.ui.page.setting.language

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Choosing a language wrote the locale, which makes Android recreate the activity, and the process restart that follows ran inside `rememberCoroutineScope()` after
 * `delay(300)`. The recreate cancelled that scope, so the rebirth never ran: on the Pixel (2026-11-25) the process id stayed the same after the switch, no new process
 * started, and strings cached in the old process, such as the "Today" header, kept the old language.
 */
class LanguageRestartSourceTest {
    private val page = listOf(File("src/main/java"), File("app/src/main/java")).first { it.exists() }
        .resolve("com/mckimquyen/reader/ui/page/setting/language/LanguagesPage.kt").readText()

    private val confirm = page.substringAfter("val target = pendingLanguage").substringBefore("dismissButton")

    @Test fun theRestartDoesNotWaitInsideACompositionScope() {
        assertTrue("triggerRebirth is still called in the dialog confirm handler", "ProcessPhoenix.triggerRebirth" in confirm)
        assertTrue("a delay before the rebirth can be cancelled by the activity recreate", "delay(" !in confirm)
        assertTrue("the rebirth must not run inside scope.launch", "scope.launch" !in confirm)
    }

    @Test fun theRebirthUsesTheApplicationContext() {
        assertTrue("an activity context is gone after the recreate", "triggerRebirth(context.applicationContext)" in confirm)
    }
}
