package com.mckimquyen.reader.infrastructure.pref

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Mechanical checks of the machine translated ad and VIP strings. They catch the technical mistakes (an empty string, a lost escape, a stray placeholder,
 * text left in English, a Latin letter in a Cyrillic locale). They do not prove the wording is right: nobody who speaks these languages has read them.
 */
class AdStringsMechanicalTest {
    private val keys = listOf(
        "vip_failed_busy", "vip_failed_no_network", "ad_consent_manage_title", "ad_consent_manage_desc",
        "vip_rewarded_not_earned", "ad_consent_not_needed",
    )
    private val scriptOf = mapOf(
        "bg" to "CYRILLIC", "ru-rRU" to "CYRILLIC", "sr" to "CYRILLIC", "uk" to "CYRILLIC", "ar" to "ARABIC", "fa" to "ARABIC", "iw" to "HEBREW",
        "hi-rIN" to "DEVANAGARI", "kn" to "KANNADA", "ml" to "MALAYALAM", "my" to "MYANMAR",
        "ja" to "CJK", "zh-rCN" to "CJK", "zh-rTW" to "CJK", "lzh" to "CJK",
    )
    private val cjk = setOf("ja", "zh-rCN", "zh-rTW", "lzh")
    private val placeholder = Regex("""%\d*\$?[sd]""")

    private val res: File = listOf("src/main/res", "app/src/main/res").map(::File).first { it.exists() }

    private fun load(dir: File): Map<String, String> {
        val text = File(dir, "strings.xml").readText()
        return keys.mapNotNull { k ->
            Regex("""<string name="$k"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL).find(text)?.let { k to it.groupValues[1] }
        }.toMap()
    }

    private fun unicodeName(c: Char): String = Character.getName(c.code) ?: ""

    @Test
    fun everyLocaleHasEveryKey_andNoMechanicalFault() {
        val english = load(File(res, "values"))
        assertEquals("the English file must carry all keys", keys.toSet(), english.keys)
        val locales = res.listFiles { f -> f.isDirectory && f.name.startsWith("values-") && File(f, "strings.xml").exists() }!!.sortedBy { it.name }
        val problems = mutableListOf<String>()
        for (dir in locales) {
            val loc = dir.name.removePrefix("values-")
            val d = load(dir)
            for (k in keys) {
                val t = d[k]
                if (t == null) { problems += "$loc/$k: missing"; continue }
                if (t.isBlank()) { problems += "$loc/$k: empty"; continue }
                if (t != t.trim()) problems += "$loc/$k: leading or trailing space"
                if ('\n' in t) problems += "$loc/$k: newline"
                if (Regex("""(?<!\\)'""").containsMatchIn(t)) problems += "$loc/$k: unescaped apostrophe"
                if (Regex("""&(?!amp;|lt;|gt;)""").containsMatchIn(t)) problems += "$loc/$k: unescaped &"
                if ('<' in t.replace("&lt;", "")) problems += "$loc/$k: raw <"
                if (placeholder.findAll(t).map { it.value }.sorted().toList() != placeholder.findAll(english.getValue(k)).map { it.value }.sorted().toList()) {
                    problems += "$loc/$k: placeholders differ from English"
                }
                val plain = t.replace("\\'", "'")
                if (plain == english.getValue(k) && k != "ad_consent_manage_title") problems += "$loc/$k: identical to English"
                val ratio = plain.length.toDouble() / english.getValue(k).length.coerceAtLeast(1)
                val (lo, hi) = if (loc in cjk) 0.10 to 1.2 else 0.45 to 2.6
                if (ratio < lo || ratio > hi) problems += "$loc/$k: length ratio %.2f".format(ratio)
                val letters = plain.filter { it.isLetter() }
                val script = scriptOf[loc]
                if (script != null && letters.isNotEmpty()) {
                    val ok = letters.count { c ->
                        val n = unicodeName(c)
                        if (script == "CJK") "CJK" in n || "HIRAGANA" in n || "KATAKANA" in n else script in n
                    }
                    if (ok.toDouble() / letters.length < 0.8) problems += "$loc/$k: not written in $script"
                } else if (script == null) {
                    // by Unicode script, not by code range: Azerbaijani schwa (U+0259) and Vietnamese letters are Latin
                    val foreign = letters.filter { Character.UnicodeScript.of(it.code) !in setOf(Character.UnicodeScript.LATIN, Character.UnicodeScript.COMMON) }
                    if (foreign.isNotEmpty()) problems += "$loc/$k: foreign script in a Latin locale: ${foreign.toSet().joinToString("")}"
                }
            }
        }
        assertEquals("mechanical faults in the machine translated ad strings:\n" + problems.joinToString("\n"), emptyList<String>(), problems)
    }

    @Test
    fun theListOfCheckedLocalesIsNotEmpty() {
        val n = res.listFiles { f -> f.isDirectory && f.name.startsWith("values-") && File(f, "strings.xml").exists() }!!.size
        assertEquals("every shipped locale is checked", true, n >= 37)
    }
}
