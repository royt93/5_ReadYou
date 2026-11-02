package com.mckimquyen.reader.domain.curated

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

class CuratedBundleLoaderTest {

    private fun shipped(): InputStream = FileInputStream(File(ASSET_PATH))

    private fun stream(json: String): InputStream = ByteArrayInputStream(json.toByteArray(Charsets.UTF_8))

    private fun bundleJson(
        id: String = "b1",
        titleEn: String = "English",
        titleVi: String = "Tiếng Việt",
        verifiedOn: String = "2026-11-02",
        sources: String = """[{"name":"A","url":"https://a.example/rss"}]""",
    ) = """{"id":"$id","titleEn":"$titleEn","titleVi":"$titleVi","verifiedOn":"$verifiedOn","sources":$sources}"""

    private fun json(vararg bundles: String) = "[" + bundles.joinToString(",") + "]"

    @Test
    fun shippedAsset_hasTheFiveVerifiedBundles() {
        val bundles = CuratedBundleLoader.load(shipped())

        assertEquals(listOf("tech_ai", "finance", "vn_news", "design", "dev"), bundles.map { it.id })
        assertTrue(bundles.all { it.sources.isNotEmpty() })
        assertTrue(bundles.all { it.verifiedOn == "2026-11-02" })
    }

    @Test
    fun shippedAsset_usesOnlyHttpsUrls_andNoUrlRepeatsAcrossTheWholeFile() {
        val urls = CuratedBundleLoader.load(shipped()).flatMap { bundle -> bundle.sources.map { it.url } }

        assertTrue(urls.all { it.startsWith("https://") })
        assertEquals("một URL xuất hiện ở hai bộ", urls.size, urls.toSet().size)
    }

    @Test
    fun shippedAsset_keepsTheVerifiedSourceCounts() {
        val counts = CuratedBundleLoader.load(shipped()).associate { it.id to it.sources.size }

        assertEquals(mapOf("tech_ai" to 8, "finance" to 5, "vn_news" to 2, "design" to 8, "dev" to 9), counts)
    }

    @Test
    fun load_parsesTitlesAndSourcesOfAValidBundle() {
        val bundles = CuratedBundleLoader.load(stream(json(bundleJson())))

        val bundle = bundles.single()
        assertEquals("English", bundle.titleEn)
        assertEquals("Tiếng Việt", bundle.titleVi)
        assertEquals(listOf(CuratedSource("A", "https://a.example/rss")), bundle.sources)
    }

    @Test
    fun load_rejectsMalformedAndEmptyJson() {
        assertThrows(IllegalArgumentException::class.java) { CuratedBundleLoader.load(stream("{not json")) }
        assertThrows(IllegalArgumentException::class.java) { CuratedBundleLoader.load(stream("")) }
    }

    @Test
    fun load_rejectsDuplicateBundleIds() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            CuratedBundleLoader.load(stream(json(bundleJson(id = "same"), bundleJson(id = "same"))))
        }
        assertTrue(error.message.orEmpty().contains("same"))
    }

    @Test
    fun load_rejectsNonHttpsUrls() {
        val sources = """[{"name":"A","url":"http://a.example/rss"}]"""
        assertThrows(IllegalArgumentException::class.java) {
            CuratedBundleLoader.load(stream(json(bundleJson(sources = sources))))
        }
    }

    @Test
    fun load_rejectsDuplicateUrlsInsideOneBundle() {
        val sources = """[{"name":"A","url":"https://a.example/rss"},{"name":"B","url":"https://a.example/rss"}]"""
        assertThrows(IllegalArgumentException::class.java) {
            CuratedBundleLoader.load(stream(json(bundleJson(sources = sources))))
        }
    }

    @Test
    fun load_rejectsBlankFieldsAndEmptySources() {
        assertThrows(IllegalArgumentException::class.java) { CuratedBundleLoader.load(stream(json(bundleJson(id = " ")))) }
        assertThrows(IllegalArgumentException::class.java) { CuratedBundleLoader.load(stream(json(bundleJson(titleEn = "")))) }
        assertThrows(IllegalArgumentException::class.java) { CuratedBundleLoader.load(stream(json(bundleJson(titleVi = "")))) }
        assertThrows(IllegalArgumentException::class.java) { CuratedBundleLoader.load(stream(json(bundleJson(sources = "[]")))) }
        val blankName = """[{"name":" ","url":"https://a.example/rss"}]"""
        assertThrows(IllegalArgumentException::class.java) {
            CuratedBundleLoader.load(stream(json(bundleJson(sources = blankName))))
        }
    }

    @Test
    fun load_rejectsAVerificationDateThatIsNotIsoFormat() {
        assertThrows(IllegalArgumentException::class.java) {
            CuratedBundleLoader.load(stream(json(bundleJson(verifiedOn = "02/11/2026"))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CuratedBundleLoader.load(stream(json(bundleJson(verifiedOn = ""))))
        }
    }

    @Test
    fun load_rejectsAUrlWithoutHost() {
        val sources = """[{"name":"A","url":"https:///rss"}]"""
        assertThrows(IllegalArgumentException::class.java) {
            CuratedBundleLoader.load(stream(json(bundleJson(sources = sources))))
        }
    }

    private companion object {
        // Unit tests run with the module directory as working directory (same convention as StringResourceParityTest).
        const val ASSET_PATH = "src/main/assets/curated_bundles.json"
    }
}
