package com.mckimquyen.reader.infrastructure.ai.clustering

import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.model.cluster.StoryCluster
import com.mckimquyen.reader.domain.model.cluster.StoryClusterResult
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.min

/**
 * Trích xuất đặc trưng bài báo đã tokenize & phân tích cấu trúc từ vựng,
 * được cache trong LruCache để tránh lặp lại công việc tokenize tốn kém.
 */
data class ArticleFeatures(
    val fingerprint: String,
    val tokensList: List<String>,
    val tokens: Set<String>,
    val bigrams: Set<String>,
    val entities: Set<String>,
    val descTokensList: List<String>,
    val descTokens: Set<String>,
    val blockingTokens: Set<String>,
)

/**
 * Thuật toán phân cụm sự kiện & gom tin tức trùng lặp (AI Story Clustering & Deduplication).
 * Phân tích độ tương đồng ngữ nghĩa, n-gram, thực thể tên riêng và thời gian phát hành
 * để gom các bài viết cùng chủ đề thành một Story Card đa góc nhìn.
 *
 * Tối ưu hóa hiệu năng [KNOW-04]:
 * 1. LruCache Feature Cache (500 bài) theo fingerprint nội dung.
 * 2. Inverted Index Token Blocking: giảm số cặp cần so sánh từ O(n²) xuống các bài có chung token/thực thể/bigram.
 * 3. Điểm similarityScore thực tế: lấy điểm tương đồng nhỏ nhất giữa bài chính (leadArticle)
 *    và các bài viết thành viên trong cụm (min similarity), loại bỏ hoàn toàn giá trị hardcode 0.85f.
 */
@Singleton
class StoryClusteringEngine @Inject constructor() {

    companion object {
        const val DEFAULT_SIMILARITY_THRESHOLD = 0.45f
        const val DEFAULT_TIME_WINDOW_HOURS = 48L
        private const val FEATURE_CACHE_SIZE = 500

        private val STOP_WORDS = setOf(
            // English
            "the", "and", "or", "to", "of", "in", "for", "with", "on", "at", "from", "by",
            "about", "as", "into", "like", "through", "after", "over", "between", "out",
            "against", "during", "without", "before", "under", "around", "among", "this",
            "that", "these", "those", "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "do", "does", "did", "can", "could", "will", "would",
            "should", "may", "might", "must", "a", "an", "not", "no", "just", "it", "its",
            "what", "which", "who", "whom", "when", "where", "why", "how", "all", "any",
            "both", "each", "few", "more", "most", "other", "some", "such", "than", "too",
            "very", "says", "said", "new", "news",

            // Vietnamese
            "và", "của", "là", "có", "được", "trong", "một", "cho", "các", "này", "những",
            "về", "để", "với", "tại", "người", "đã", "theo", "ra", "lại", "khi", "từ",
            "sau", "như", "lên", "đến", "hơn", "nhiều", "vào", "do", "đó", "cũng", "nhưng",
            "bởi", "rất", "năm", "ngày", "tháng", "qua", "thì", "sẽ", "mà", "vì", "trên",
            "chưa", "bị", "còn", "nên", "hay", "nếu", "ai", "gì", "nào", "đâu", "thế",
            "tin", "báo", "vừa", "mới", "hôm", "nay", "chiều", "sáng", "tối"
        )
    }

    // In-memory LRU Cache thread-safe cho các đặc trưng bài viết đã trích xuất
    private val featureCache: MutableMap<String, ArticleFeatures> = Collections.synchronizedMap(
        object : LinkedHashMap<String, ArticleFeatures>(FEATURE_CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ArticleFeatures>?): Boolean {
                return size > FEATURE_CACHE_SIZE
            }
        }
    )

    /**
     * Tạo chuỗi fingerprint độc nhất dựa trên nội dung thực sự ảnh hưởng đến độ tương đồng.
     */
    fun computeArticleFingerprint(a: ArticleWithFeed): String {
        val art = a.article
        val title = art.title.trim()
        val descSnippet = art.shortDescription.take(200).trim()
        val time = art.date.time
        return "${art.id}|${title.hashCode()}|${descSnippet.hashCode()}|$time"
    }

    /**
     * Trích xuất các đặc trưng phân tích của bài báo hoặc lấy lại từ LRU Cache.
     */
    fun getOrExtractFeatures(a: ArticleWithFeed): ArticleFeatures {
        val cacheKey = a.article.id
        val fingerprint = computeArticleFingerprint(a)

        synchronized(featureCache) {
            val cached = featureCache[cacheKey]
            if (cached != null && cached.fingerprint == fingerprint) {
                return cached
            }
        }

        val title = a.article.title.trim()
        val tokensList = tokenizeList(title)
        val tokens = tokensList.toSet()
        val bigrams = extractBigrams(tokensList)
        val entities = extractEntities(title)

        val descTokensList = tokenizeList(a.article.shortDescription.take(200))
        val descTokens = descTokensList.toSet()

        // Các token dùng để tạo inverted index blocking
        val blockingTokens = mutableSetOf<String>().apply {
            addAll(tokens)
            addAll(bigrams)
            addAll(entities)
            addAll(descTokens)
        }

        val features = ArticleFeatures(
            fingerprint = fingerprint,
            tokensList = tokensList,
            tokens = tokens,
            bigrams = bigrams,
            entities = entities,
            descTokensList = descTokensList,
            descTokens = descTokens,
            blockingTokens = blockingTokens,
        )

        synchronized(featureCache) {
            featureCache[cacheKey] = features
        }

        return features
    }

    /**
     * Xóa cache đặc trưng (dùng khi kiểm thử hoặc giải phóng tài nguyên).
     */
    fun clearCache() {
        synchronized(featureCache) {
            featureCache.clear()
        }
    }

    /**
     * Lấy kích thước hiện tại của cache đặc trưng.
     */
    fun cacheSize(): Int {
        synchronized(featureCache) {
            return featureCache.size
        }
    }

    /**
     * Nhận vào danh sách bài báo và phân cụm thành StoryClusterResult.
     * Áp dụng Inverted Index Blocking + Tính similarityScore thực tế từ calculateSimilarity().
     */
    fun cluster(
        articles: List<ArticleWithFeed>,
        threshold: Float = DEFAULT_SIMILARITY_THRESHOLD,
        timeWindowHours: Long = DEFAULT_TIME_WINDOW_HOURS,
    ): StoryClusterResult {
        if (articles.size < 2) {
            return StoryClusterResult.EMPTY
        }

        val windowMillis = TimeUnit.HOURS.toMillis(timeWindowHours)
        val n = articles.size
        val dsu = DisjointSetUnion(n)

        // 1. Trích xuất đặc trưng (có cache) cho tất cả bài viết
        val featuresList = ArrayList<ArticleFeatures>(n)
        for (i in 0 until n) {
            featuresList.add(getOrExtractFeatures(articles[i]))
        }

        // 2. Xây dựng Inverted Index Blocking: token -> list of article indices
        val tokenToArticles = mutableMapOf<String, MutableList<Int>>()
        for (i in 0 until n) {
            for (token in featuresList[i].blockingTokens) {
                tokenToArticles.getOrPut(token) { mutableListOf() }.add(i)
            }
        }

        // 3. Tìm các cặp bài viết tiềm năng (Candidate Pairs) có ít nhất 1 blocking token chung
        // và nằm trong khung thời gian hợp lệ
        // Key mã hóa cặp (i, j) với i < j: (i.toLong() shl 32) or (j.toLong() and 0xFFFFFFFFL)
        val candidatePairs = mutableSetOf<Long>()
        for ((_, docIndices) in tokenToArticles) {
            val docCount = docIndices.size
            if (docCount < 2) continue
            // Nếu một token quá phổ biến (xuất hiện ở > 70% số bài viết), bỏ qua để tránh O(n^2) worst case
            if (docCount > (n * 0.7f).toInt().coerceAtLeast(10)) continue

            for (idxA in 0 until docCount) {
                val i = docIndices[idxA]
                val tI = articles[i].article.date.time
                for (idxB in idxA + 1 until docCount) {
                    val j = docIndices[idxB]
                    val tJ = articles[j].article.date.time
                    if (abs(tI - tJ) <= windowMillis) {
                        val minIdx = if (i < j) i else j
                        val maxIdx = if (i < j) j else i
                        val pairKey = (minIdx.toLong() shl 32) or (maxIdx.toLong() and 0xFFFFFFFFL)
                        candidatePairs.add(pairKey)
                    }
                }
            }
        }

        // 4. Fallback an toàn: So sánh trực tiếp các bài có tiêu đề giống nhau hệt nhau
        // (đề phòng trường hợp tiêu đề cực ngắn hoặc không sinh blocking token nào lọt stop words)
        val titleMap = mutableMapOf<String, MutableList<Int>>()
        for (i in 0 until n) {
            val normalizedTitle = articles[i].article.title.trim().lowercase()
            if (normalizedTitle.isNotBlank()) {
                titleMap.getOrPut(normalizedTitle) { mutableListOf() }.add(i)
            }
        }
        for ((_, indices) in titleMap) {
            if (indices.size >= 2) {
                for (x in 0 until indices.size) {
                    for (y in x + 1 until indices.size) {
                        val i = indices[x]
                        val j = indices[y]
                        if (abs(articles[i].article.date.time - articles[j].article.date.time) <= windowMillis) {
                            val minIdx = if (i < j) i else j
                            val maxIdx = if (i < j) j else i
                            val pairKey = (minIdx.toLong() shl 32) or (maxIdx.toLong() and 0xFFFFFFFFL)
                            candidatePairs.add(pairKey)
                        }
                    }
                }
            }
        }

        // 5. Tính toán similarity cho các candidate pairs và hợp nhất vào DSU
        val pairSimilarityMap = mutableMapOf<Long, Float>()
        for (pairKey in candidatePairs) {
            val i = (pairKey ushr 32).toInt()
            val j = (pairKey and 0xFFFFFFFFL).toInt()

            val similarity = calculateSimilarityWithFeatures(
                a1 = articles[i],
                f1 = featuresList[i],
                a2 = articles[j],
                f2 = featuresList[j]
            )
            pairSimilarityMap[pairKey] = similarity

            if (similarity >= threshold) {
                dsu.union(i, j)
            }
        }

        // 6. Gom các bài viết theo từng cụm
        val components = mutableMapOf<Int, MutableList<ArticleWithFeed>>()
        for (i in 0 until n) {
            val root = dsu.find(i)
            components.getOrPut(root) { mutableListOf() }.add(articles[i])
        }

        val clusters = mutableListOf<StoryCluster>()
        val leadClusterMap = mutableMapOf<String, StoryCluster>()
        val nonLeadIds = mutableSetOf<String>()

        // Map ngược articleId -> index trong articles để tra cứu pairSimilarityMap nhanh chóng
        val articleIndexMap = HashMap<String, Int>(n)
        for (i in 0 until n) {
            articleIndexMap[articles[i].article.id] = i
        }

        for ((_, clusterArticles) in components) {
            if (clusterArticles.size >= 2) {
                // Chọn leadArticle: bài viết có tiêu đề + nội dung chi tiết nhất, hoặc mới nhất
                val sortedArticles = clusterArticles.sortedWith(
                    compareByDescending<ArticleWithFeed> { (it.article.title.length * 2) + it.article.shortDescription.length }
                        .thenByDescending { it.article.date.time }
                )
                val leadArticle = sortedArticles.first()
                val otherArticles = sortedArticles.drop(1)
                val leadIdx = articleIndexMap[leadArticle.article.id] ?: 0

                // 7. Tính similarityScore thực tế: min similarity giữa leadArticle và các bài khác trong cụm
                var minSimilarity = 1.0f
                for (other in otherArticles) {
                    val otherIdx = articleIndexMap[other.article.id] ?: 0
                    val minI = if (leadIdx < otherIdx) leadIdx else otherIdx
                    val maxI = if (leadIdx < otherIdx) otherIdx else leadIdx
                    val pairKey = (minI.toLong() shl 32) or (maxI.toLong() and 0xFFFFFFFFL)

                    val sim = pairSimilarityMap[pairKey] ?: calculateSimilarityWithFeatures(
                        a1 = leadArticle,
                        f1 = featuresList[leadIdx],
                        a2 = other,
                        f2 = featuresList[otherIdx]
                    )
                    if (sim < minSimilarity) {
                        minSimilarity = sim
                    }
                }

                val keywords = extractKeywords(clusterArticles)
                val clusterId = "cluster_${leadArticle.article.id}"

                val cluster = StoryCluster(
                    id = clusterId,
                    title = leadArticle.article.title,
                    leadArticle = leadArticle,
                    articles = sortedArticles,
                    keywords = keywords,
                    sourceCount = clusterArticles.map { it.feed.id }.distinct().size,
                    articleCount = clusterArticles.size,
                    date = clusterArticles.maxOfOrNull { it.article.date } ?: leadArticle.article.date,
                    similarityScore = minSimilarity,
                )

                clusters.add(cluster)
                leadClusterMap[leadArticle.article.id] = cluster
                otherArticles.forEach { nonLeadIds.add(it.article.id) }
            }
        }

        return StoryClusterResult(
            clusters = clusters,
            leadClusterMap = leadClusterMap,
            nonLeadIds = nonLeadIds,
        )
    }

    /**
     * Tính toán độ tương đồng giữa hai bài báo dựa trên đặc trưng đã trích xuất sẵn.
     */
    fun calculateSimilarityWithFeatures(
        a1: ArticleWithFeed,
        f1: ArticleFeatures,
        a2: ArticleWithFeed,
        f2: ArticleFeatures,
    ): Float {
        val t1 = a1.article.title.trim()
        val t2 = a2.article.title.trim()
        if (t1.equals(t2, ignoreCase = true)) return 1.0f

        val tokens1 = f1.tokens
        val tokens2 = f2.tokens

        if (tokens1.isEmpty() || tokens2.isEmpty()) return 0.0f

        val wordJaccard = jaccardSimilarity(tokens1, tokens2)
        val wordOverlap = overlapCoefficient(tokens1, tokens2)
        val wordScore = (wordJaccard * 0.45f) + (wordOverlap * 0.55f)

        // N-gram similarity (bigrams)
        val bigrams1 = f1.bigrams
        val bigrams2 = f2.bigrams
        val bigramScore = if (bigrams1.isNotEmpty() && bigrams2.isNotEmpty()) {
            val bigramJaccard = jaccardSimilarity(bigrams1, bigrams2)
            val bigramOverlap = overlapCoefficient(bigrams1, bigrams2)
            (bigramJaccard * 0.45f) + (bigramOverlap * 0.55f)
        } else {
            0f
        }

        // Thực thể tên riêng
        val entities1 = f1.entities
        val entities2 = f2.entities
        val entityBonus = if (entities1.isNotEmpty() && entities2.isNotEmpty()) {
            overlapCoefficient(entities1, entities2)
        } else {
            0f
        }

        // Tương đồng mô tả phụ (nếu có)
        val d1 = f1.descTokens
        val d2 = f2.descTokens
        val descScore = if (d1.isNotEmpty() && d2.isNotEmpty()) {
            val jaccard = jaccardSimilarity(d1, d2)
            val overlap = overlapCoefficient(d1, d2)
            (jaccard * 0.4f) + (overlap * 0.6f)
        } else {
            0f
        }

        // Trọng số tổng hợp
        val combinedScore = if (descScore > 0f) {
            (wordScore * 0.40f) + (bigramScore * 0.25f) + (entityBonus * 0.20f) + (descScore * 0.15f)
        } else {
            (wordScore * 0.50f) + (bigramScore * 0.30f) + (entityBonus * 0.20f)
        }

        return min(1.0f, combinedScore)
    }

    /**
     * Tính toán độ tương đồng giữa hai bài báo dựa trên từ khóa, n-gram và thực thể.
     */
    fun calculateSimilarity(a1: ArticleWithFeed, a2: ArticleWithFeed): Float {
        val f1 = getOrExtractFeatures(a1)
        val f2 = getOrExtractFeatures(a2)
        return calculateSimilarityWithFeatures(a1, f1, a2, f2)
    }

    /**
     * Tách từ theo danh sách có thứ tự.
     */
    fun tokenizeList(text: String): List<String> {
        val clean = text.lowercase()
            .replace(Regex("[^\\p{L}\\p{Nd}\\s]"), " ")
        return clean.split(Regex("\\s+"))
            .filter { it.length >= 2 && !STOP_WORDS.contains(it) }
    }

    /**
     * Tách từ, loại bỏ dấu câu, chuyển về chữ thường và lọc stop words.
     */
    fun tokenize(text: String): Set<String> {
        return tokenizeList(text).toSet()
    }

    private fun extractBigrams(tokens: List<String>): Set<String> {
        if (tokens.size < 2) return emptySet()
        val bigrams = mutableSetOf<String>()
        for (i in 0 until tokens.size - 1) {
            bigrams.add("${tokens[i]}_${tokens[i + 1]}")
        }
        return bigrams
    }

    private fun extractEntities(rawText: String): Set<String> {
        // Tìm các từ viết hoa hoặc chuỗi số/tên model (vd: iPhone, OpenAI, 7.2, Vingroup, Bắc Nam)
        val regex = Regex("[\\p{Lu}\\p{Nd}][\\p{L}\\p{Nd}._-]{1,}")
        return regex.findAll(rawText)
            .map { it.value.lowercase() }
            .filter { !STOP_WORDS.contains(it) && it.length >= 2 }
            .toSet()
    }

    private fun <T> jaccardSimilarity(s1: Set<T>, s2: Set<T>): Float {
        val intersection = s1.intersect(s2).size
        val union = s1.union(s2).size
        if (union == 0) return 0f
        return intersection.toFloat() / union.toFloat()
    }

    private fun <T> overlapCoefficient(s1: Set<T>, s2: Set<T>): Float {
        val intersection = s1.intersect(s2).size
        val minSize = min(s1.size, s2.size)
        if (minSize == 0) return 0f
        return intersection.toFloat() / minSize.toFloat()
    }

    /**
     * Trích xuất các từ khóa chủ đề nổi bật của cụm bài báo.
     */
    fun extractKeywords(articles: List<ArticleWithFeed>, topK: Int = 4): List<String> {
        val frequencyMap = mutableMapOf<String, Int>()
        for (a in articles) {
            val tokens = tokenize(a.article.title) + extractEntities(a.article.title)
            for (token in tokens) {
                frequencyMap[token] = frequencyMap.getOrDefault(token, 0) + 1
            }
        }
        return frequencyMap.entries
            .sortedByDescending { it.value * (if (it.key.length >= 4) 2 else 1) }
            .take(topK)
            .map { it.key.replaceFirstChar { c -> c.uppercase() } }
    }

    /**
     * Disjoint-Set Union (DSU) với Union by Rank và Path Compression.
     */
    private class DisjointSetUnion(size: Int) {
        private val parent = IntArray(size) { it }
        private val rank = IntArray(size) { 0 }

        fun find(i: Int): Int {
            if (parent[i] != i) {
                parent[i] = find(parent[i])
            }
            return parent[i]
        }

        fun union(i: Int, j: Int) {
            val rootI = find(i)
            val rootJ = find(j)
            if (rootI != rootJ) {
                if (rank[rootI] < rank[rootJ]) {
                    parent[rootI] = rootJ
                } else if (rank[rootI] > rank[rootJ]) {
                    parent[rootJ] = rootI
                } else {
                    parent[rootJ] = rootI
                    rank[rootI]++
                }
            }
        }
    }
}
