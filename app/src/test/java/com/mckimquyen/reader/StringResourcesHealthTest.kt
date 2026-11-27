package com.mckimquyen.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The pre-release audit found 40 strings no code referenced, one of them a nonsense sentence ("1 Mark posts older than days as read") copied into 37 locales, and
 * 12 locales where over 700 of 1139 strings were still the English text. Dead keys cost translation work for text nobody sees, so they are rejected here, and every
 * translation must keep the placeholders of the source string.
 */
class StringResourcesHealthTest {
    private val root: File = listOf(File("src/main"), File("app/src/main")).first { it.exists() }
    private val nameRegex = Regex("""<string\s[^>]*?\bname="([^"]+)"([^>]*)>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)

    private fun strings(file: File): Map<String, String> =
        nameRegex.findAll(file.readText())
            .filter { "translatable=\"false\"" !in it.groupValues[2] }
            .associate { it.groupValues[1] to it.groupValues[3] }

    private val base = strings(File(root, "res/values/strings.xml"))
    private val locales = File(root, "res").listFiles { f -> f.name.startsWith("values-") && File(f, "strings.xml").exists() }.orEmpty().sortedBy { it.name }

    @Test
    fun everyDefaultStringIsReferencedByCodeOrResources() {
        val referenced = HashSet<String>()
        val ref = Regex("""R\.string\.([A-Za-z0-9_]+)|@string/([A-Za-z0-9_]+)""")
        root.walkTopDown().filter { it.isFile && it.extension in setOf("kt", "java", "xml") }.forEach { f ->
            ref.findAll(f.readText()).forEach { referenced += it.groupValues[1].ifEmpty { it.groupValues[2] } }
        }
        val dead = base.keys.filter { it.replace('.', '_') !in referenced }
        assertEquals("strings nobody references (delete them from every locale): $dead", emptyList<String>(), dead)
    }

    @Test
    fun noLocaleDefinesAKeyThatTheDefaultFileLacks() {
        // Language names are translatable="false" in the default file yet Vietnamese localises them on purpose, so count every default key, flagged or not.
        val allDefault = nameRegex.findAll(File(root, "res/values/strings.xml").readText()).map { it.groupValues[1] }.toSet()
        val offenders = locales.flatMap { l -> (strings(File(l, "strings.xml")).keys - allDefault).map { "${l.name}:$it" } }
        assertEquals("keys only in a translation: $offenders", emptyList<String>(), offenders)
    }

    @Test
    fun everyTranslationKeepsThePlaceholdersOfTheDefaultString() {
        val ph = Regex("""%(?:\d+\$)?[sdfx]""")
        val bad = ArrayList<String>()
        locales.forEach { l ->
            strings(File(l, "strings.xml")).forEach { (k, v) ->
                val b = base[k] ?: return@forEach
                // A translation may repeat a placeholder (Romanian unsubscribe_tips uses %1$s twice, which Android allows); it may not add or drop one.
                if (ph.findAll(b).map { it.value }.toSet() != ph.findAll(v).map { it.value }.toSet()) bad += "${l.name}:$k"
            }
        }
        assertTrue("placeholders changed by a translation: $bad", bad.isEmpty())
    }

    @Test
    fun noTranslationIsEmpty() {
        val empty = locales.flatMap { l -> strings(File(l, "strings.xml")).filter { it.value.isBlank() }.keys.map { "${l.name}:$it" } }
        assertEquals("empty translations: $empty", emptyList<String>(), empty)
    }

    // On a Tecno the settings list showed "Feed Autopilot" and "Second Brain" untouched in uk, pl, it and in while the other locales had them translated.
    @Test
    fun featureNamesAreTranslatedInEveryTranslatedLocale() {
        val names = listOf("feed_autopilot_title" to "Feed Autopilot", "second_brain_title" to "Second Brain")
        val checked = listOf("values-uk", "values-pl-rPL", "values-it-rIT", "values-in-rID")
        val offenders = checked.flatMap { dir ->
            val m = strings(File(root, "res/$dir/strings.xml"))
            names.filter { (key, english) -> m[key]?.trim() == english }.map { "$dir:${it.first}" }
        }
        assertEquals("feature names still in English: $offenders", emptyList<String>(), offenders)
    }

    // The Terms text is HTML held in one string. In ar and bg a <b> was never closed, and in sl a </p> was left in the middle of a sentence, so the rest of the text would
    // have been rendered bold or ended early. A translation must open and close the same tags as the English source.
    @Test
    fun everyTranslationKeepsTheHtmlTagBalanceOfTheSource() {
        val voidTags = setOf("br", "img", "hr")
        fun balance(raw: String): Map<String, Int> {
            val text = raw.replace("&lt;", "<").replace("&gt;", ">")
            val counts = HashMap<String, Int>()
            Regex("""<(/?)([a-zA-Z0-9]+)(?:\s[^>]*)?(/?)>""").findAll(text).forEach {
                val name = it.groupValues[2].lowercase()
                if (name in voidTags || it.groupValues[3] == "/") return@forEach
                counts[name] = (counts[name] ?: 0) + if (it.groupValues[1] == "/") -1 else 1
            }
            return counts.filterValues { it != 0 }
        }
        val offenders = locales.flatMap { l ->
            strings(File(l, "strings.xml")).filter { (k, v) -> k in base && balance(v) != balance(base.getValue(k)) }.keys.map { "${l.name}:$it" }
        }
        assertEquals("translations whose HTML tags are not balanced like the source: $offenders", emptyList<String>(), offenders)
    }

    // In ja the second link of the Terms text read "https:/ /www.gnu.org/...", which neither opens nor copies. Every locale must keep both source URLs intact.
    @Test
    fun theTermsTextKeepsBothSourceUrlsIntact() {
        val urls = Regex("""https?://[^\s<&]+""").findAll(base.getValue("tos_content")).map { it.value }.toList()
        assertTrue("the source Terms text should hold two URLs, found $urls", urls.size == 2)
        val offenders = locales.filter { "tos_content" in strings(File(it, "strings.xml")) }.flatMap { l ->
            val text = strings(File(l, "strings.xml")).getValue("tos_content")
            urls.filter { it !in text }.map { "${l.name}:$it" }
        }
        assertEquals("Terms text with a broken URL: $offenders", emptyList<String>(), offenders)
    }

    // The edition names were copied with a time the source does not have ("Morning Edition (07:00)"): the time is a setting the user changes, so the label was wrong
    // in 32 locales as soon as it was changed.
    @Test
    fun theEditionNamesCarryNoFixedTimeInAnyLocale() {
        val offenders = locales.flatMap { l ->
            val m = strings(File(l, "strings.xml"))
            listOf("zen_morning_edition", "zen_evening_edition").filter { k -> m[k]?.contains(Regex("""\(\d{1,2}:\d{2}\)""")) == true }.map { "${l.name}:$it" }
        }
        assertEquals("edition names with a hard-coded time: $offenders", emptyList<String>(), offenders)
    }

    // Traditional-Chinese text must not contain characters that exist only in Simplified Chinese (the notebook strings were copied from zh-CN).
    @Test
    fun traditionalChineseHasNoSimplifiedOnlyCharacters() {
        val simplifiedOnly = "笔记库删复载录导设阅选页储块览关节标项档词语识图译边级组态简缓网总现历买单认让对开应听见问题发".toSet()
        val offenders = listOf("values-zh-rTW", "values-lzh").flatMap { name ->
            strings(File(locales.first { it.name == name }, "strings.xml")).filter { (_, v) -> v.count { it in simplifiedOnly } >= 2 }.keys.map { "$name:$it" }
        }
        assertEquals("traditional-script strings written in Simplified Chinese: $offenders", emptyList<String>(), offenders)
    }

    // Hindi had 60 labels with a trailing space and Polish one, copied from the machine output; a space after a label shows as a misaligned button text.
    @Test
    fun noTranslatedLabelHasLeadingOrTrailingWhitespace() {
        val offenders = locales.flatMap { l ->
            strings(File(l, "strings.xml")).filter { (k, v) -> k != "tos_content" && !v.contains('\n') && v != v.trim() }.keys.map { "${l.name}:$it" }
        }
        assertEquals("labels padded with whitespace: $offenders", emptyList<String>(), offenders)
    }

    // The Romanian unsubscribe warning named the feed twice ("from X ... articles of X") while the source names it once.
    @Test
    fun aTranslationDoesNotRepeatAPlaceholderTheSourceUsesOnce() {
        val ph = Regex("""%\d+\$[sd]""")
        val offenders = locales.flatMap { l ->
            strings(File(l, "strings.xml")).filter { (k, v) ->
                k in base && ph.findAll(v).groupingBy { it.value }.eachCount().any { (p, n) -> n > ph.findAll(base.getValue(k)).count { it.value == p } }
            }.keys.map { "${l.name}:$it" }
        }
        assertEquals("placeholders used more often than in the source: $offenders", emptyList<String>(), offenders)
    }

    // A count shown next to a noun has to be a <plurals>, not a <string> with %1$d: Russian, Polish, Czech, Slovenian and Arabic change the noun with the number.
    // These four labels were strings ("%1$d articles") and read wrongly for 2, 5 or 21 in those languages.
    private val countedNouns = listOf("zen_sleep_timer_min", "second_brain_node_articles", "smart_collection_articles_count", "dossier_article_count")

    @Test
    fun countedNounsArePluralsInTheDefaultLanguage() {
        val text = File(root, "res/values/strings.xml").readText()
        val notPlurals = countedNouns.filter { """<plurals name="$it">""" !in text }
        assertEquals("these counts are still plain strings: $notPlurals", emptyList<String>(), notPlurals)
        val leftovers = countedNouns.filter { """<string name="$it"""" in text }
        assertEquals("a string with the same name as a plurals would not compile into R twice: $leftovers", emptyList<String>(), leftovers)
    }

    @Test
    fun everyLocaleGivesEachCountedNounTheQuantityCategoriesItsLanguageNeeds() {
        val needed = mapOf(
            "values-ru-rRU" to setOf("one", "few", "many", "other"), "values-uk" to setOf("one", "few", "many", "other"),
            "values-pl-rPL" to setOf("one", "few", "many", "other"), "values-cs-rCZ" to setOf("one", "few", "many", "other"),
            "values-ar" to setOf("zero", "one", "two", "few", "many", "other"), "values-sl" to setOf("one", "two", "few", "other"),
            "values-sr" to setOf("one", "few", "other"), "values-iw" to setOf("one", "two", "other"),
        )
        val offenders = mutableListOf<String>()
        for ((dir, cats) in needed) {
            val f = File(root, "res/$dir/strings.xml"); if (!f.exists()) continue
            val text = f.readText()
            for (name in countedNouns) {
                val block = Regex("""<plurals name="$name">(.*?)</plurals>""", RegexOption.DOT_MATCHES_ALL).find(text)?.groupValues?.get(1)
                if (block == null) { offenders += "$dir:$name missing"; continue }
                val have = Regex("""quantity="(\w+)"""").findAll(block).map { it.groupValues[1] }.toSet()
                if (!have.containsAll(cats)) offenders += "$dir:$name lacks ${cats - have}"
            }
        }
        assertEquals("plurals missing a quantity category: $offenders", emptyList<String>(), offenders)
    }
}
