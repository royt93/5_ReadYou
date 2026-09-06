package com.mckimquyen.reader.infrastructure.ai.clustering

import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.model.cluster.StoryCluster
import com.mckimquyen.reader.domain.model.cluster.StoryClusterResult
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Thuật toán phân cụm sự kiện & gom tin tức trùng lặp (AI Story Clustering & Deduplication).
 * Phân tích độ tương đồng ngữ nghĩa, n-gram, thực thể tên riêng và thời gian phát hành
 * để gom các bài viết cùng chủ đề thành một Story Card đa góc nhìn.
 */
@Singleton
class StoryClusteringEngine @Inject constructor() {

    companion object {
        const val DEFAULT_SIMILARITY_THRESHOLD = 0.45f
        const val DEFAULT_TIME_WINDOW_HOURS = 48L

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

    /**
     * Nhận vào danh sách bài báo và phân cụm thành StoryClusterResult.
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

        // So sánh từng cặp bài báo trong ngưỡng thời gian
        for (i in 0 until n) {
            val a1 = articles[i]
            for (j in i + 1 until n) {
                val a2 = articles[j]
                val timeDiff = abs(a1.article.date.time - a2.article.date.time)
                if (timeDiff <= windowMillis) {
                    val similarity = calculateSimilarity(a1, a2)
                    if (similarity >= threshold) {
                        dsu.union(i, j)
                    }
                }
            }
        }

        // Gom các bài viết theo từng cụm
        val components = mutableMapOf<Int, MutableList<ArticleWithFeed>>()
        for (i in 0 until n) {
            val root = dsu.find(i)
            components.getOrPut(root) { mutableListOf() }.add(articles[i])
        }

        val clusters = mutableListOf<StoryCluster>()
        val leadClusterMap = mutableMapOf<String, StoryCluster>()
        val nonLeadIds = mutableSetOf<String>()

        for ((_, clusterArticles) in components) {
            if (clusterArticles.size >= 2) {
                // Chọn leadArticle: bài viết có tiêu đề + nội dung chi tiết nhất, hoặc mới nhất
                val sortedArticles = clusterArticles.sortedWith(
                    compareByDescending<ArticleWithFeed> { (it.article.title.length * 2) + it.article.shortDescription.length }
                        .thenByDescending { it.article.date.time }
                )
                val leadArticle = sortedArticles.first()
                val otherArticles = sortedArticles.drop(1)

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
                    similarityScore = 0.85f,
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
     * Tính toán độ tương đồng giữa hai bài báo dựa trên từ khóa, n-gram và thực thể.
     */
    fun calculateSimilarity(a1: ArticleWithFeed, a2: ArticleWithFeed): Float {
        val t1 = a1.article.title.trim()
        val t2 = a2.article.title.trim()
        if (t1.equals(t2, ignoreCase = true)) return 1.0f

        val tokensList1 = tokenizeList(t1)
        val tokensList2 = tokenizeList(t2)

        val tokens1 = tokensList1.toSet()
        val tokens2 = tokensList2.toSet()

        if (tokens1.isEmpty() || tokens2.isEmpty()) return 0.0f

        val wordJaccard = jaccardSimilarity(tokens1, tokens2)
        val wordOverlap = overlapCoefficient(tokens1, tokens2)
        val wordScore = (wordJaccard * 0.45f) + (wordOverlap * 0.55f)

        // N-gram similarity (bigrams) từ danh sách từ theo thứ tự
        val bigrams1 = extractBigrams(tokensList1)
        val bigrams2 = extractBigrams(tokensList2)
        val bigramScore = if (bigrams1.isNotEmpty() && bigrams2.isNotEmpty()) {
            val bigramJaccard = jaccardSimilarity(bigrams1, bigrams2)
            val bigramOverlap = overlapCoefficient(bigrams1, bigrams2)
            (bigramJaccard * 0.45f) + (bigramOverlap * 0.55f)
        } else {
            0f
        }

        // Trích xuất từ viết hoa / thực thể / con số từ tiêu đề gốc (hỗ trợ Unicode)
        val entities1 = extractEntities(t1)
        val entities2 = extractEntities(t2)
        val entityBonus = if (entities1.isNotEmpty() && entities2.isNotEmpty()) {
            overlapCoefficient(entities1, entities2)
        } else {
            0f
        }

        // Tương đồng mô tả phụ (nếu có)
        val d1List = tokenizeList(a1.article.shortDescription.take(200))
        val d2List = tokenizeList(a2.article.shortDescription.take(200))
        val descScore = if (d1List.isNotEmpty() && d2List.isNotEmpty()) {
            val d1 = d1List.toSet()
            val d2 = d2List.toSet()
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
