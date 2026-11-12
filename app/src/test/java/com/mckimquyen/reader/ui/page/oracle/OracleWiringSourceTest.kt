package com.mckimquyen.reader.ui.page.oracle

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OracleWiringSourceTest {

    private val root = "src/main/java/com/mckimquyen/reader"
    private val item = File("$root/ui/page/home/flow/ArticleItem.kt").readText()
    private val content = File("$root/ui/page/home/read/Content.kt").readText()
    private val home = File("$root/ui/page/common/HomeEntry.kt").readText()
    private val sheet = File("$root/ui/component/oracle/OracleClaimSheet.kt").readText()

    @Test fun articleItemShowsTheBadgeFromTheLocalAndOpensTheCard() {
        assertTrue(item.contains("LocalOracleClaims.current[articleWithFeed.article.id]"))
        assertTrue(item.contains("OracleBadge("))
        assertTrue(item.contains("openOracleClaim(articleWithFeed.article.id)"))
    }

    @Test fun readingContentShowsTheBadgeForTheOpenArticle() {
        assertTrue(content.contains("LocalOracleClaims.current[articleId]"))
        assertTrue(content.contains("OracleBadge("))
        assertTrue(content.contains("openOracleClaim(articleId)"))
    }

    @Test fun homeEntryProvidesBothLocalsAndHostsTheSheet() {
        assertTrue(home.contains("val oracleClaimsViewModel: OracleClaimsViewModel = hiltViewModel()"))
        assertTrue(home.contains("LocalOracleClaims provides oracleClaims"))
        assertTrue(home.contains("LocalOpenOracleClaim provides"))
        assertTrue(home.contains("OracleClaimSheet("))
    }

    @Test fun theSheetHasNoLinkSourceOrBettingWords() {
        // Comments may say that betting comes later; only the code itself is checked.
        val code = sheet.lines().filterNot { it.trimStart().startsWith("*") || it.trimStart().startsWith("/*") || it.trimStart().startsWith("//") }
            .joinToString("\n")
        listOf("link", "feed", "bet", "wallet", "points", "reward").forEach { word ->
            assertFalse("sheet must not mention '$word'", code.contains(word, ignoreCase = true))
        }
        assertFalse("no ads", Regex("""AdManager|showRewarded""").containsMatchIn(code))
    }
}
