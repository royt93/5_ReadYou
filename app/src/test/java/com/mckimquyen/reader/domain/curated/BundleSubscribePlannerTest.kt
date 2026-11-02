package com.mckimquyen.reader.domain.curated

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BundleSubscribePlannerTest {

    private fun src(name: String, url: String) = CuratedSource(name, url)

    @Test
    fun plan_treatsSchemeHostCaseAndTrailingSlashAsTheSameUrl() {
        val plan = BundleSubscribePlanner.plan(
            listOf(src("A", "https://example.com/rss/"), src("B", "https://other.example/feed")),
            setOf("HTTPS://EXAMPLE.COM/rss"),
        )

        assertEquals(listOf("B"), plan.toAdd.map { it.name })
        assertEquals(listOf("A"), plan.alreadyHave.map { it.name })
    }

    @Test
    fun plan_keepsPathCaseQueryAndFragmentSignificant() {
        val plan = BundleSubscribePlanner.plan(
            listOf(src("path", "https://a.example/Feed"), src("query", "https://a.example/feed?x=1")),
            setOf("https://a.example/feed"),
        )

        assertEquals(listOf("path", "query"), plan.toAdd.map { it.name })
        assertTrue(plan.alreadyHave.isEmpty())
    }

    @Test
    fun plan_dropsDuplicateUrlsInsideTheBundle_keepingTheFirst() {
        val plan = BundleSubscribePlanner.plan(
            listOf(src("first", "https://a.example/rss/"), src("duplicate", "https://A.example/rss")),
            emptySet(),
        )

        assertEquals(listOf("first"), plan.toAdd.map { it.name })
    }

    @Test
    fun plan_existingUrlsAreNormalizedToo() {
        val plan = BundleSubscribePlanner.plan(
            listOf(src("A", "https://a.example/rss")),
            setOf("  https://A.example/rss/  "),
        )

        assertTrue(plan.toAdd.isEmpty())
        assertEquals(1, plan.alreadyHave.size)
    }

    @Test
    fun plan_emptyBundleAndAllExistingAreSafe() {
        assertTrue(BundleSubscribePlanner.plan(emptyList(), emptySet()).toAdd.isEmpty())

        val allExisting = BundleSubscribePlanner.plan(
            listOf(src("A", "https://a.example/rss"), src("B", "https://b.example/rss")),
            setOf("https://a.example/rss", "https://b.example/rss"),
        )
        assertTrue(allExisting.toAdd.isEmpty())
        assertEquals(2, allExisting.alreadyHave.size)
    }

    @Test
    fun plan_neverSubscribesAnUnparsableUrl() {
        val plan = BundleSubscribePlanner.plan(listOf(src("bad", "not a url"), src("ok", "https://a.example/rss")), emptySet())

        assertEquals(listOf("ok"), plan.toAdd.map { it.name })
        assertEquals(listOf("bad"), plan.alreadyHave.map { it.name })
    }

    @Test
    fun normalizeUrl_keepsExplicitPortAndDistinguishesPaths() {
        assertEquals("https://a.example:8443/rss", BundleSubscribePlanner.normalizeUrl("HTTPS://A.example:8443/rss/"))
        assertNotEquals(
            BundleSubscribePlanner.normalizeUrl("https://a.example/one"),
            BundleSubscribePlanner.normalizeUrl("https://a.example/two"),
        )
    }
}
