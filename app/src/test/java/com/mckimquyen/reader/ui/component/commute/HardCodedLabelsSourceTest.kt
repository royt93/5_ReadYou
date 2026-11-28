package com.mckimquyen.reader.ui.component.commute

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * English labels typed into the code showed in English in every language ("Mark Read", "Paste", "Loading...", "Back", "Add Source", the CommuteCast skip buttons).
 * Each one already had a translated string resource; the code uses it now.
 */
class HardCodedLabelsSourceTest {
    private val root: File = listOf(File("src/main/java"), File("app/src/main/java")).first { it.exists() }
        .resolve("com/mckimquyen/reader")

    private fun src(path: String) = root.resolve(path).readText()

    private val banned = mapOf(
        "ui/page/home/addsources/AddSourceDetailPage.kt" to listOf("contentDescription = \"Back\"", "contentDescription = \"Add Source\""),
        "ui/page/home/flow/ArticleItem.kt" to listOf("text = \"Mark Read\""),
        "ui/page/setting/ai/AiSettingsPage.kt" to listOf("Text(text = \"Paste\")"),
        "infrastructure/android/SplashActivity.kt" to listOf("text = \"Loading...\""),
        "ui/component/commute/CommuteCastSheet.kt" to listOf("contentDescription = \"Previous\"", "contentDescription = \"Next\""),
    )

    @Test fun noScreenTypesTheseEnglishLabelsIntoCode() {
        banned.forEach { (path, literals) ->
            val s = src(path)
            literals.forEach { assertTrue("$path still has $it", it !in s) }
        }
    }

    @Test fun theReplacementsAreRealResources() {
        val s = src("infrastructure/android/SplashActivity.kt")
        assertTrue("import androidx.compose.ui.res.stringResource" in s)
        assertTrue("stringResource(R.string.loading)" in s)
        assertTrue("stringResource(R.string.widget_article_mark_read)" in src("ui/page/home/flow/ArticleItem.kt"))
        assertTrue("stringResource(R.string.paste)" in src("ui/page/setting/ai/AiSettingsPage.kt"))
        val cc = src("ui/component/commute/CommuteCastSheet.kt")
        assertTrue("stringResource(R.string.commute_media_action_prev)" in cc && "stringResource(R.string.commute_media_action_next)" in cc)
    }
}
