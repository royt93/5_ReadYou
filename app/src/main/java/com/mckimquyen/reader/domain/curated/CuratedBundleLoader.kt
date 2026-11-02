package com.mckimquyen.reader.domain.curated

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.io.InputStream
import java.net.URI

/**
 * Reads and validates the bundled `curated_bundles.json`. Validation fails loudly (IllegalArgumentException) because a
 * bad asset is a build-time mistake: every bundle needs a unique id, both titles, a `verifiedOn` date, and at least one
 * source with a unique HTTPS URL that has a host.
 */
object CuratedBundleLoader {
    private val ISO_DATE = Regex("""\d{4}-\d{2}-\d{2}""")
    private const val HTTPS_SCHEME = "https"

    fun load(input: InputStream): List<CuratedBundle> {
        val parsed = try {
            input.use { stream -> Gson().fromJson(stream.reader(Charsets.UTF_8), Array<CuratedBundle>::class.java) }
        } catch (e: JsonSyntaxException) {
            throw IllegalArgumentException("curated bundles JSON is malformed", e)
        } ?: throw IllegalArgumentException("curated bundles JSON is empty")

        val bundles = parsed.toList()
        require(bundles.isNotEmpty()) { "curated bundles JSON has no bundles" }

        val seenIds = HashSet<String>()
        bundles.forEach { bundle ->
            // Gson bypasses Kotlin null checks, so fields can be null even though the type says String.
            @Suppress("USELESS_ELVIS")
            val id = bundle.id ?: ""
            require(id.isNotBlank()) { "bundle id is blank" }
            require(seenIds.add(id)) { "duplicate bundle id: $id" }
            validate(bundle)
        }
        return bundles
    }

    @Suppress("USELESS_ELVIS", "SENSELESS_COMPARISON")
    private fun validate(bundle: CuratedBundle) {
        require((bundle.titleEn ?: "").isNotBlank()) { "bundle ${bundle.id}: titleEn is blank" }
        require((bundle.titleVi ?: "").isNotBlank()) { "bundle ${bundle.id}: titleVi is blank" }
        require(ISO_DATE.matches(bundle.verifiedOn ?: "")) { "bundle ${bundle.id}: verifiedOn must be yyyy-MM-dd" }
        val sources = bundle.sources ?: emptyList()
        require(sources.isNotEmpty()) { "bundle ${bundle.id}: has no sources" }

        val seenUrls = HashSet<String>()
        sources.forEach { source ->
            require((source.name ?: "").isNotBlank()) { "bundle ${bundle.id}: a source name is blank" }
            val url = source.url ?: ""
            require(isHttpsWithHost(url)) { "bundle ${bundle.id}: not an https URL with a host: $url" }
            require(seenUrls.add(BundleSubscribePlanner.normalizeUrl(url))) {
                "bundle ${bundle.id}: duplicate source URL: $url"
            }
        }
    }

    private fun isHttpsWithHost(url: String): Boolean = runCatching {
        val uri = URI(url.trim())
        uri.scheme.equals(HTTPS_SCHEME, ignoreCase = true) && !uri.host.isNullOrBlank()
    }.getOrDefault(false)
}
