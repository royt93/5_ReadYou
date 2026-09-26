package com.mckimquyen.reader.infrastructure.ai.search

import com.mckimquyen.reader.domain.model.article.ArticleEmbeddingRecord
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.repository.ArticleEmbeddingDao
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Offline 64-dimensional semantic search with a persistent per-article embedding index. */
@Singleton
class SemanticSearchEngine @Inject constructor(
    private val articleEmbeddingDao: ArticleEmbeddingDao,
) {

    companion object {
        const val EMBEDDING_DIM = 64
        const val DEFAULT_MIN_SCORE_THRESHOLD = 0.22f

        private val CONCEPT_CLUSTERS: Map<String, Set<String>> = mapOf(
            "CLEAN_ENERGY" to setOf(
                "năng lượng sạch", "năng lượng tái tạo", "pin mặt trời", "quang điện", "tuabin gió",
                "điện gió", "nhiên liệu hydro", "hydrogen", "carbon neutral", "solar panel",
                "wind turbine", "clean energy", "renewable", "green power", "photovoltaic", "net zero"
            ),
            "ARTIFICIAL_INTELLIGENCE" to setOf(
                "trí tuệ nhân tạo", "học máy", "mô hình ngôn ngữ", "mạng nơron", "ai",
                "artificial intelligence", "machine learning", "deep learning", "llm", "neural network",
                "transformer", "chatgpt", "gemini", "claude", "gpt", "generative ai"
            ),
            "SEMICONDUCTOR" to setOf(
                "chíp bán dẫn", "bán dẫn", "vi mạch", "tấm bán dẫn", "semiconductor",
                "microchip", "wafer", "fab", "tsmc", "intel", "nvidia", "asml", "lithography"
            ),
            "FINANCE_MARKETS" to setOf(
                "chứng khoán", "lạm phát", "lãi suất", "ngân hàng trung ương", "cổ phiếu",
                "trái phiếu", "tiền tệ", "kinh tế", "stock market", "inflation", "interest rate",
                "central bank", "federal reserve", "equity", "recession", "gdp", "kinh doanh"
            ),
            "CRYPTOCURRENCY" to setOf(
                "tiền mã hóa", "tiền ảo", "chuỗi khối", "đào coin", "crypto",
                "cryptocurrency", "bitcoin", "ethereum", "blockchain", "web3", "defi", "token"
            ),
            "ELECTRIC_VEHICLES" to setOf(
                "xe điện", "pin lithium", "trạm sạc", "xe tự hành", "tự lái", "ô tô điện",
                "electric vehicle", "ev", "battery", "supercharger", "autonomous driving",
                "autopilot", "tesla", "vinfast", "byd"
            ),
            "HEALTH_BIOTECH" to setOf(
                "y tế", "dược phẩm", "vắc xin", "kháng thể", "gen", "thử nghiệm lâm sàng",
                "ung thư", "bệnh viện", "healthcare", "biotech", "vaccine", "antibody",
                "crispr", "genetics", "clinical trial", "pharma", "mrna"
            ),
            "SPACE_AEROSPACE" to setOf(
                "vũ trụ", "tên lửa", "vệ tinh", "thám hiểm không gian", "quỹ đạo", "sao hỏa",
                "mặt trăng", "space", "rocket", "satellite", "orbit", "mars", "moon", "nasa", "spacex"
            ),
            "CYBERSECURITY" to setOf(
                "an ninh mạng", "mã độc", "tống tiền", "rò rỉ dữ liệu", "tấn công mạng",
                "lỗ hổng", "tường lửa", "cybersecurity", "malware", "ransomware", "data breach",
                "firewall", "zero day", "hacker", "phishing"
            ),
            "DEFENSE_GEOPOLITICS" to setOf(
                "quân sự", "quốc phòng", "chiến sự", "vũ khí", "hiệp ước", "ngoại giao",
                "địa chính trị", "military", "defense", "geopolitics", "warfare", "treaty",
                "diplomacy", "nato"
            )
        )

        private val STOP_WORDS = setOf(
            "và", "của", "là", "có", "được", "trong", "một", "cho", "các", "này", "những",
            "về", "để", "với", "tại", "người", "đã", "theo", "ra", "lại", "khi", "từ",
            "the", "and", "or", "to", "of", "in", "for", "with", "on", "at", "from", "by",
            "about", "as", "into", "like", "through", "after", "over", "is", "are", "was"
        )
    }

    /** Serializes cache inspection, embedding computation and write to avoid duplicate work. */
    private val cacheLock = Mutex()

    suspend fun cacheEmbeddings(articles: List<ArticleWithFeed>): CacheUpdateStats =
        resolveEmbeddings(articles).stats

    /**
     * Returns every article's vector plus cache statistics, doing exactly one bulk read, one hash
     * pass and at most one write. [rank] reuses the returned vectors instead of re-querying.
     */
    private suspend fun resolveEmbeddings(articles: List<ArticleWithFeed>): ResolvedEmbeddings {
        if (articles.isEmpty()) return ResolvedEmbeddings(emptyMap(), CacheUpdateStats.EMPTY)
        // Deduplicate first: a repeated id would otherwise be embedded twice and overcount `written`,
        // since REPLACE collapses same-PK rows into one.
        val distinctArticles = articles.distinctBy { it.article.id }
        return cacheLock.withLock {
            val cachedById = articleEmbeddingDao.getByArticleIds(distinctArticles.map { it.article.id })
                .associateBy { it.articleId }
            val vectors = HashMap<String, FloatArray>(distinctArticles.size)
            val records = mutableListOf<ArticleEmbeddingRecord>()
            var hits = 0
            var staleOrCorrupt = 0
            distinctArticles.forEach { articleWithFeed ->
                val article = articleWithFeed.article
                val hash = ArticleEmbeddingRecord.computeContentHash(article.title, article.shortDescription)
                val cached = cachedById[article.id]
                val validVector = cached?.takeIf { it.contentHash == hash }?.toFloatArray(EMBEDDING_DIM)
                if (validVector != null) {
                    hits++
                    vectors[article.id] = validVector
                } else {
                    if (cached != null) staleOrCorrupt++
                    val vector = embed(
                        ArticleEmbeddingRecord.documentText(article.title, article.shortDescription)
                    )
                    vectors[article.id] = vector
                    records += ArticleEmbeddingRecord.fromFloatArray(article.id, hash, vector)
                }
            }
            // An article deleted between the candidate read and this write would break the
            // foreign key; the in-memory vectors stay valid, so ranking must not crash.
            val persisted = records.isEmpty() || runCatching {
                articleEmbeddingDao.insertOrUpdateAll(records)
            }.isSuccess
            ResolvedEmbeddings(
                vectors = vectors,
                stats = CacheUpdateStats(
                    requested = distinctArticles.size,
                    hits = hits,
                    written = if (persisted) records.size else 0,
                    staleOrCorrupt = staleOrCorrupt,
                ),
            )
        }
    }

    suspend fun rank(
        query: String,
        articles: List<ArticleWithFeed>,
        minScoreThreshold: Float = DEFAULT_MIN_SCORE_THRESHOLD,
        limit: Int = 50,
    ): List<SemanticSearchResult> {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank() || articles.isEmpty() || limit <= 0) return emptyList()

        val queryVector = embed(cleanQuery)
        val queryConcepts = detectConcepts(cleanQuery)
        val queryTokens = tokenize(cleanQuery)
        // One pass builds/loads every vector; no second query and no second hash pass per keystroke.
        val vectorsByArticleId = resolveEmbeddings(articles).vectors

        return articles.mapNotNull { articleWithFeed ->
            val article = articleWithFeed.article
            val docText = ArticleEmbeddingRecord.documentText(article.title, article.shortDescription)
            val docVector = vectorsByArticleId[article.id] ?: embed(docText)
            val docConcepts = detectConcepts(docText)
            val docTokens = tokenize(docText)
            val commonConcepts = queryConcepts.intersect(docConcepts)
            val conceptBonus = if (queryConcepts.isNotEmpty() && docConcepts.isNotEmpty()) {
                commonConcepts.size.toFloat() / max(1, queryConcepts.size)
            } else {
                0f
            }
            val tokenOverlap = if (queryTokens.isNotEmpty() && docTokens.isNotEmpty()) {
                queryTokens.intersect(docTokens).size.toFloat() / queryTokens.size
            } else {
                0f
            }
            val score = min(
                1f,
                (cosineSimilarity(queryVector, docVector) * 0.45f) +
                    (conceptBonus * 0.35f) +
                    (tokenOverlap * 0.20f),
            )
            if (score < minScoreThreshold) null else SemanticSearchResult(
                articleWithFeed = articleWithFeed,
                score = score,
                matchedConcepts = commonConcepts.toList(),
            )
        }.sortedByDescending { it.score }.take(limit)
    }

    fun embed(text: String): FloatArray {
        val vector = FloatArray(EMBEDDING_DIM)
        val lower = text.lowercase(Locale.ROOT)
        CONCEPT_CLUSTERS.values.forEachIndexed { clusterIndex, keywords ->
            val baseDimension = (clusterIndex * 3) % 30
            keywords.forEach { keyword ->
                if (lower.contains(keyword)) {
                    vector[baseDimension] += 1.2f
                    vector[baseDimension + 1] += 0.8f
                    vector[baseDimension + 2] += 0.5f
                }
            }
        }
        tokenize(lower).forEach { token ->
            if (token.length >= 3) {
                for (index in 0..token.length - 3) {
                    val hash = token.substring(index, index + 3).hashCode()
                    val positiveHash = hash.toLong().and(0x7fffffffL).toInt()
                    vector[30 + (positiveHash % 34)] += 0.4f
                }
            }
        }
        return l2Normalize(vector)
    }

    fun cosineSimilarity(first: FloatArray, second: FloatArray): Float {
        var dotProduct = 0f
        for (index in 0 until min(first.size, second.size)) {
            dotProduct += first[index] * second[index]
        }
        return max(0f, min(1f, dotProduct))
    }

    fun detectConcepts(text: String): Set<String> {
        val lower = text.lowercase(Locale.ROOT)
        return CONCEPT_CLUSTERS.mapNotNullTo(mutableSetOf()) { (concept, keywords) ->
            concept.takeIf { keywords.any(lower::contains) }
        }
    }

    fun tokenize(text: String): Set<String> = text.lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{Nd}\\s]"), " ")
        .split(Regex("\\s+"))
        .filter { it.length >= 2 && it !in STOP_WORDS }
        .toSet()

    private fun l2Normalize(vector: FloatArray): FloatArray {
        var sumSquares = 0f
        vector.forEach { sumSquares += it * it }
        val norm = sqrt(sumSquares)
        if (norm <= 1e-6f) return vector
        return FloatArray(vector.size) { index -> vector[index] / norm }
    }
}

private data class ResolvedEmbeddings(
    val vectors: Map<String, FloatArray>,
    val stats: CacheUpdateStats,
)

data class CacheUpdateStats(
    val requested: Int,
    val hits: Int,
    val written: Int,
    val staleOrCorrupt: Int,
) {
    companion object {
        val EMPTY = CacheUpdateStats(requested = 0, hits = 0, written = 0, staleOrCorrupt = 0)
    }
}
