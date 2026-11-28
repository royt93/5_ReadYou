package com.mckimquyen.reader.ui.component.commute

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Icon descriptions, the Deep Dive subtitle, the notebook note prefix and the "+XP" texts were English typed into the code, so every language showed (or read out) English.
 * They are string resources now; every locale must define each key and the XP text must keep its number.
 */
class RemainingHardCodedLabelsTest {
    private val main: File = listOf(File("src/main"), File("app/src/main")).first { it.exists() }
    private val root = main.resolve("java/com/mckimquyen/reader")

    private val banned = mapOf(
        "ui/page/home/addsources/AddSourcesPage.kt" to listOf("contentDescription = \"Country\"", "contentDescription = \"Navigate\""),
        "ui/page/home/addsources/AddSourceDetailPage.kt" to listOf("contentDescription = \"RSS Source\"", "contentDescription = \"URL\"", "contentDescription = \"Already Added\""),
        "ui/page/setting/color/ColorAndStylePage.kt" to listOf("contentDescription = \"Checked\""),
        "ui/component/ambient/ZenSoundSheet.kt" to listOf("contentDescription = \"Volume\""),
        "ui/component/commute/CommuteCastSheet.kt" to listOf("\"🎙️ Deep Dive Edition\""),
        "ui/page/notebook/NotebookPage.kt" to listOf("\"💡 Note:\""),
        "ui/page/home/read/Content.kt" to listOf("\"+\$awarded XP! 🎉\""),
        "ui/component/rpg/BrainQuizCard.kt" to listOf("\"+300 XP! 🎉\"", "if (isDoubled) \"+300 XP\""),
    )

    private val keys = listOf(
        "a11y_country", "a11y_navigate", "a11y_rss_source", "a11y_url", "a11y_already_added", "a11y_volume",
        "commute_deepdive_edition", "notebook_note_prefix", "rpg_xp_gained",
    )

    private fun localeFiles() = main.resolve("res").listFiles { f -> f.name.startsWith("values") && f.resolve("strings.xml").exists() }!!.map { it.resolve("strings.xml") }

    @Test fun noScreenTypesTheseLabelsIntoCode() {
        banned.forEach { (path, literals) ->
            val s = root.resolve(path).readText()
            literals.forEach { assertTrue("$path still has $it", it !in s) }
        }
    }

    @Test fun everyLocaleDefinesEveryKey() {
        val files = localeFiles()
        assertTrue(files.size > 30)
        files.forEach { f -> keys.forEach { k -> assertTrue("${f.parentFile.name} lacks $k", "name=\"$k\"" in f.readText()) } }
    }

    @Test fun theXpTextKeepsItsNumberInEveryLocale() {
        val re = Regex("""name="rpg_xp_gained">([^<]*)<""")
        localeFiles().forEach { f ->
            val v = re.find(f.readText())?.groupValues?.get(1) ?: return@forEach
            assertEquals("${f.parentFile.name} must use exactly one %1\$d", 1, Regex("%1\\\$d").findAll(v).count())
        }
    }
}
