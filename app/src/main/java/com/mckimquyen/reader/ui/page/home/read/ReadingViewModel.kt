package com.mckimquyen.reader.ui.page.home.read

import android.content.Intent
import android.util.Log
import androidx.annotation.Keep
import androidx.annotation.StringRes
import com.mckimquyen.reader.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.ItemSnapshotList
import com.mckimquyen.reader.domain.model.article.ArticleFlowItem
import com.mckimquyen.reader.domain.model.article.ArticleHighlights
import com.mckimquyen.reader.domain.model.article.ArticleMindMap
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.model.article.DeepReadMessage
import com.mckimquyen.reader.domain.model.article.DeepReadSender
import com.mckimquyen.reader.domain.model.article.DeepReadSession
import com.mckimquyen.reader.domain.sv.RssSv
import com.mckimquyen.reader.infrastructure.ai.GeminiSummaryService
import com.mckimquyen.reader.infrastructure.audio.TtsManager
import com.mckimquyen.reader.infrastructure.audio.TtsState
import com.mckimquyen.reader.infrastructure.audio.ambient.ZenAudioManager
import com.mckimquyen.reader.infrastructure.rss.RssHelper
import com.mckimquyen.reader.domain.model.article.ArticleReadPosition
import com.mckimquyen.reader.domain.model.article.ReadPositionConflictResolver
import com.mckimquyen.reader.domain.model.quote.QuoteCardSpec
import com.mckimquyen.reader.domain.model.quote.QuoteSelection
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.readposition.ReadingPositionMapper
import com.mckimquyen.reader.domain.repository.ArticleReadPositionDao
import com.mckimquyen.reader.infrastructure.di.DefaultDispatcher
import com.mckimquyen.reader.infrastructure.di.IODispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import com.mckimquyen.reader.domain.headline.ArticleSignalAnalyzer
import com.mckimquyen.reader.domain.model.bounty.BountyQuerySanitizer
import com.mckimquyen.reader.domain.steelman.ContrastingCoverageFinder
import com.mckimquyen.reader.domain.steelman.ContrastingCoverageState
import com.mckimquyen.reader.domain.steelman.TopicPhraseExtractor
import com.mckimquyen.reader.domain.model.bounty.Dossier
import com.mckimquyen.reader.domain.model.bounty.DossierBuilder
import com.mckimquyen.reader.domain.model.bounty.DossierMarkdownBuilder
import com.mckimquyen.reader.infrastructure.bounty.DossierSharer
import com.mckimquyen.reader.ui.page.headline.EchoSignalState
import com.mckimquyen.reader.domain.repository.BountyInvestigationRepository
import com.mckimquyen.reader.ui.page.home.read.bounty.BountyAiFailure
import com.mckimquyen.reader.ui.page.home.read.bounty.BountyAiState
import com.mckimquyen.reader.ui.page.home.read.bounty.BountyInvestigationPhase
import com.mckimquyen.reader.ui.page.home.read.bounty.BountyInvestigationState
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ReadingViewModel @Inject constructor(
    private val rssService: RssSv,
    private val rssHelper: RssHelper,
    private val ttsManager: TtsManager,
    private val summaryService: GeminiSummaryService,
    val zenAudioManager: ZenAudioManager,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val articleReadPositionDao: ArticleReadPositionDao? = null,
    private val articleDao: ArticleDao? = null,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val dossierSharer: DossierSharer? = null,
) : ViewModel() {

    private val _readingUiState = MutableStateFlow(ReadingUiState())
    val readingUiState: StateFlow<ReadingUiState> = _readingUiState.asStateFlow()

    private val _scrollToTopEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val scrollToTopEvent: SharedFlow<Unit> = _scrollToTopEvent.asSharedFlow()

    private val _restoreScrollPositionEvent = MutableSharedFlow<ReadPositionRestoreEvent>(extraBufferCapacity = 1)
    val restoreScrollPositionEvent: SharedFlow<ReadPositionRestoreEvent> = _restoreScrollPositionEvent.asSharedFlow()

    private var fetchJob: Job? = null
    private var summaryJob: Job? = null
    private var mindMapJob: Job? = null
    private var deepReadJob: Job? = null
    private var bountyJob: Job? = null
    private var bountyAiJob: Job? = null

    private val bountyRepository: BountyInvestigationRepository? by lazy {
        articleDao?.let { BountyInvestigationRepository(it) }
    }

    private var echoJob: Job? = null

    private var contrastJob: Job? = null
    private var contrastPhrases: List<String> = emptyList()

    private val _contrastingCoverage = MutableStateFlow<ContrastingCoverageState>(ContrastingCoverageState.Idle)
    /**
     * ECHO-02: other-source articles in the user's own archive that cover the same topic with contrarian headline
     * wording. Lexical and archive-only; it never says an article actually rebuts anything.
     */
    val contrastingCoverage: StateFlow<ContrastingCoverageState> = _contrastingCoverage.asStateFlow()

    private val _echoSignalState = MutableStateFlow<EchoSignalState>(EchoSignalState.Idle)
    /**
     * ECHO-01: local hype/tone signals for the body Reader is showing. Toggle-agnostic on purpose: the shared
     * `Headline signals` switch is a CompositionLocal, so it is enforced at render time in the UI.
     */
    val echoSignalState: StateFlow<EchoSignalState> = _echoSignalState.asStateFlow()

    private var dossierJob: Job? = null

    private val _dossier = MutableStateFlow<Dossier?>(null)
    /** BOUNTY-02: in-memory digest of the current bounty results; never persisted. */
    val dossier: StateFlow<Dossier?> = _dossier.asStateFlow()

    private val _bountyInvestigationState = MutableStateFlow(BountyInvestigationState())
    val bountyInvestigationState: StateFlow<BountyInvestigationState> = _bountyInvestigationState.asStateFlow()

    init {
        // ECHO-01: re-analyze whenever the displayed body changes (description, full content, or another article).
        // One observer keeps every render path covered; mapLatest-style cancellation drops stale work.
        viewModelScope.launch {
            _readingUiState
                .map { it.articleWithFeed?.article?.id to it.content }
                .distinctUntilChanged()
                .collect { (articleId, content) -> analyzeEchoSignals(articleId, content) }
        }
        // ECHO-02: search the local archive for other angles whenever a different article is shown. Keyed on the id
        // only, so a change of body text (description -> full content) does not repeat the search.
        viewModelScope.launch {
            _readingUiState
                .map { it.articleWithFeed }
                .distinctUntilChangedBy { it?.article?.id }
                .collect { loadContrastingCoverage(it) }
        }
        // Collect TTS state once per ViewModel lifetime.
        // Previously this was inside initData(), which created a new collector
        // on every article open — causing duplicate event processing over time.
        viewModelScope.launch {
            ttsManager.ttsState.collect { state ->
                _readingUiState.update { it.copy(ttsState = state) }
            }
        }
    }

    fun initData(
        articleId: String,
        autoTtsEnabled: Boolean = false,
        readPositionSyncEnabled: Boolean = true,
    ) {
        // Capture current TTS state before switching feed
        val wasPlaying = _readingUiState.value.ttsState == TtsState.PLAYING
        val shouldAutoPlay = autoTtsEnabled || wasPlaying

        if (wasPlaying) {
            Log.d("roy93~", "ReadingViewModel: Switching feed, stopping current TTS")
            ttsManager.stop()
        }

        // Switching to a different article: cancel any in-flight AI request for the
        // previous article and reset its result state, so a late response never
        // overwrites the state shown for the newly opened article.
        if (_readingUiState.value.articleWithFeed?.article?.id != articleId) {
            summaryJob?.cancel()
            mindMapJob?.cancel()
            deepReadJob?.cancel()
            // A stale card must never linger while the next article loads.
            contrastJob?.cancel()
            _contrastingCoverage.value = ContrastingCoverageState.Idle
            // Bounty results and the dossier belong to the previous article's context.
            dismissBountyInvestigation()
            _readingUiState.update {
                it.copy(
                    summaryState = SummaryState.Idle,
                    mindMapState = MindMapState.Idle,
                    deepReadState = DeepReadState.Idle,
                )
            }
        }

        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            showLoading()
            val articleWithFeed = rssService.get().findArticleById(articleId)
            val savedPosition = if (readPositionSyncEnabled) articleReadPositionDao?.getPosition(articleId) else null
            val persistedHighlights = articleWithFeed?.article?.aiSummary?.let { raw ->
                if (raw.isNotBlank()) com.mckimquyen.reader.infrastructure.ai.ArticleHighlightsExtractor.deserialize(raw) else null
            }
            _readingUiState.update {
                it.copy(
                    articleWithFeed = articleWithFeed,
                    savedReadPosition = savedPosition,
                    summaryState = if (persistedHighlights != null) SummaryState.Success(persistedHighlights) else it.summaryState,
                )
            }
            _readingUiState.value.articleWithFeed?.let {
                if (it.feed.isFullContent) internalRenderFullContent(shouldAutoPlay)
                else renderDescriptionContent(shouldAutoPlay)
            }
            if (savedPosition != null) {
                _restoreScrollPositionEvent.emit(ReadPositionRestoreEvent(articleId, savedPosition.progress))
            } else {
                _scrollToTopEvent.tryEmit(Unit)
            }
            hideLoading()
        }
    }

    fun saveReadPosition(articleId: String, progress: Float) {
        val dao = articleReadPositionDao ?: return
        if (progress < MIN_SAVED_PROGRESS || progress > MAX_SAVED_PROGRESS) return
        viewModelScope.launch {
            val articleWithFeed = _readingUiState.value.articleWithFeed ?: return@launch
            if (articleWithFeed.article.id != articleId) return@launch
            val existing = dao.getPosition(articleId)
            val current = ArticleReadPosition(
                articleId = articleId,
                accountId = articleWithFeed.article.accountId,
                feedUrl = articleWithFeed.feed.url,
                articleLink = articleWithFeed.article.link,
                progress = progress,
                lastReadTime = System.currentTimeMillis(),
                isSynced = false,
            )
            val resolved = ReadPositionConflictResolver.resolve(existing, current) ?: current
            dao.upsertPosition(resolved.copy(isSynced = false))
        }
    }

    fun renderDescriptionContent(autoPlay: Boolean = false) {
        _readingUiState.update {
            it.copy(
                content = it.articleWithFeed?.article?.fullContent
                    ?: it.articleWithFeed?.article?.rawDescription ?: "",
                isFullContent = false
            )
        }
        if (autoPlay) {
            playCurrentContent()
        }
    }

    fun renderFullContent() {
        val wasPlaying = _readingUiState.value.ttsState == TtsState.PLAYING
        if (wasPlaying) {
            ttsManager.stop()
        }
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            internalRenderFullContent(wasPlaying)
        }
    }

    private suspend fun internalRenderFullContent(autoPlay: Boolean = false) {
        showLoading()
        try {
            _readingUiState.update {
                it.copy(
                    content = rssHelper.parseFullContent(
                        _readingUiState.value.articleWithFeed?.article?.link ?: "",
                        _readingUiState.value.articleWithFeed?.article?.title ?: "",
                        _readingUiState.value.articleWithFeed?.feed?.sourceType
                            ?: com.mckimquyen.reader.domain.model.feed.Feed.RSS_SOURCE_TYPE,
                    ),
                    isFullContent = true
                )
            }
            if (autoPlay) {
                playCurrentContent()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.i("RLog", "renderFullContent: ${e.message}")
            _readingUiState.update { it.copy(content = e.message) }
        }
        hideLoading()
    }

    /** [startFraction] is the body fraction already read; speech starts at the nearest paragraph at or before it. */
    private fun playCurrentContent(startFraction: Float = 0f) {
        val content = _readingUiState.value.content
        val title = _readingUiState.value.articleWithFeed?.article?.title.orEmpty()
        val feedName = _readingUiState.value.articleWithFeed?.feed?.name.orEmpty()
        if (content == null) {
            Log.d("roy93~", "ReadingViewModel content is null, cannot play")
            return
        }
        viewModelScope.launch {
            Log.d("roy93~", "ReadingViewModel parsing HTML to plain text")
            val plainText = withContext(defaultDispatcher) {
                androidx.core.text.HtmlCompat.fromHtml(
                    content, androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY
                ).toString()
            }
            val resumeFrom = if (startFraction < ReadingPositionMapper.RESTART_BELOW_FRACTION ||
                startFraction >= ReadingPositionMapper.TREAT_AS_FINISHED_FRACTION
            ) {
                0
            } else {
                ReadingPositionMapper.paragraphStartOffset(plainText, startFraction)
            }
            Log.d("roy93~", "ReadingViewModel starting TTS play")
            ttsManager.play(plainText.substring(resumeFrom), title, feedName)
        }
    }

    val ttsSpeechRate: StateFlow<Float> = ttsManager.speechRate

    fun setTtsSpeechRate(rate: Float): Boolean = ttsManager.setSpeechRate(rate)

    fun togglePlayAudio(startFraction: Float = 0f) {
        Log.d("roy93~", "ReadingViewModel togglePlayAudio called. Current state: ${_readingUiState.value.ttsState}")
        if (_readingUiState.value.ttsState == TtsState.PLAYING) {
            Log.d("roy93~", "ReadingViewModel stopping TTS")
            ttsManager.stop()
        } else {
            playCurrentContent(startFraction)
        }
    }

    // ---- AI Summary (Gemini) ----

    /** Mở BottomSheet tóm tắt. Nếu bài viết đã có bản tóm tắt lưu trong DB, hiển thị ngay mà không cần gọi API lại. */
    fun openSummary() {
        Log.d("roy93~AI", "[VM.openSummary] clicked, article=${_readingUiState.value.articleWithFeed?.article?.id}")
        _readingUiState.update { it.copy(showSummarySheet = true) }
        if (_readingUiState.value.summaryState !is SummaryState.Success) {
            requestSummary()
        }
    }

    /** Mở sheet chia sẻ trích dẫn cho đoạn đang chọn; bỏ qua nếu rỗng hoặc chưa có bài. */
    fun openQuoteSheet(rawSelection: String) {
        val quote = QuoteSelection.sanitize(rawSelection) ?: return
        val loaded = _readingUiState.value.articleWithFeed ?: return
        _readingUiState.update {
            it.copy(
                showQuoteSheet = true,
                quoteSpec = QuoteCardSpec(
                    quote = quote,
                    feedName = loaded.feed.name,
                    author = loaded.article.author,
                    articleTitle = loaded.article.title,
                ),
            )
        }
    }

    fun dismissQuoteSheet() {
        _readingUiState.update { it.copy(showQuoteSheet = false, quoteSpec = null) }
    }

    /**
     * BOUNTY-01: mở sheet điều tra cho đoạn đang chọn. Chỉ tìm trong kho Room của CHÍNH tài khoản đang đọc và bỏ qua
     * bài hiện tại; phạm vi tài khoản lấy từ bài đã tải chứ không từ UI. Giữ radar tối thiểu [BOUNTY_MIN_SCAN_MILLIS]
     * để hoạt ảnh không chớp tắt khi kho nhỏ.
     */
    fun openBountyInvestigation(rawSelection: String) {
        val query = BountyQuerySanitizer.sanitize(rawSelection) ?: return
        val current = _readingUiState.value.articleWithFeed?.article ?: return
        val repository = bountyRepository ?: return

        bountyJob?.cancel()
        bountyAiJob?.cancel()
        _bountyInvestigationState.value = BountyInvestigationState(phase = BountyInvestigationPhase.SCANNING, query = query)
        _readingUiState.update { it.copy(showBountySheet = true) }

        bountyJob = viewModelScope.launch {
            val canUseAi = withContext(defaultDispatcher) { summaryService.hasCustomApiKey() }
            try {
                coroutineScope {
                    val minimumScan = launch { delay(BOUNTY_MIN_SCAN_MILLIS) }
                    val result = repository.investigate(current.accountId, current.id, query)
                    minimumScan.join()
                    _bountyInvestigationState.update {
                        it.copy(phase = BountyInvestigationPhase.RESULTS, result = result, canUseAiContext = canUseAi)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("roy93~Bounty", "[VM.openBountyInvestigation] lookup failed: $e")
                _bountyInvestigationState.update { it.copy(phase = BountyInvestigationPhase.ERROR) }
            }
        }
    }

    /** Hỏi Gemini bằng KEY RIÊNG của người dùng, dựa trên đúng các bài cục bộ đã tìm thấy; không có key thì không gọi. */
    fun requestBountyAiContext() {
        val state = _bountyInvestigationState.value
        val result = state.result ?: return
        if (!state.canUseAiContext) {
            _bountyInvestigationState.update { it.copy(ai = BountyAiState.Failed(BountyAiFailure.NO_KEY)) }
            return
        }
        if (state.ai is BountyAiState.Loading) return

        bountyAiJob?.cancel()
        _bountyInvestigationState.update { it.copy(ai = BountyAiState.Loading) }
        bountyAiJob = viewModelScope.launch {
            val ai = try {
                BountyAiState.Ready(summaryService.summarizeBountyContext(result.query, result.articles))
            } catch (e: CancellationException) {
                throw e
            } catch (e: GeminiSummaryService.SummaryException) {
                BountyAiState.Failed(
                    when (e) {
                        GeminiSummaryService.SummaryException.MissingApiKey -> BountyAiFailure.NO_KEY
                        GeminiSummaryService.SummaryException.RateLimited -> BountyAiFailure.RATE_LIMITED
                        GeminiSummaryService.SummaryException.InvalidApiKey -> BountyAiFailure.INVALID_KEY
                        GeminiSummaryService.SummaryException.Network -> BountyAiFailure.NETWORK
                        GeminiSummaryService.SummaryException.EmptyResponse,
                        GeminiSummaryService.SummaryException.EmptyContent -> BountyAiFailure.EMPTY
                        else -> BountyAiFailure.UNKNOWN
                    }
                )
            } catch (e: Exception) {
                BountyAiState.Failed(BountyAiFailure.UNKNOWN)
            }
            _bountyInvestigationState.update { it.copy(ai = ai) }
        }
    }

    private fun analyzeEchoSignals(articleId: String?, content: String?) {
        echoJob?.cancel()
        // A different article/body must never keep showing the previous article's signals.
        _echoSignalState.value = EchoSignalState.Idle
        _readingUiState.update { if (it.showEchoSheet) it.copy(showEchoSheet = false) else it }
        if (articleId == null || content.isNullOrBlank()) return

        _echoSignalState.value = EchoSignalState.Analyzing
        echoJob = viewModelScope.launch {
            val result = withContext(defaultDispatcher) { ArticleSignalAnalyzer.analyze(content) }
            _echoSignalState.value =
                if (result != null && result.isNoteworthy) EchoSignalState.Ready(result) else EchoSignalState.None
        }
    }

    /** Opens the details sheet only when there is a noteworthy result to show. */
    fun openEchoSheet() {
        if (_echoSignalState.value !is EchoSignalState.Ready) return
        _readingUiState.update { it.copy(showEchoSheet = true) }
    }

    fun dismissEchoSheet() {
        _readingUiState.update { it.copy(showEchoSheet = false) }
    }

    /** Builds the dossier from the loaded bounty results; no-op unless RESULTS has at least 2 articles. */
    fun openDossier() {
        val state = _bountyInvestigationState.value
        val result = state.result
        if (state.phase != BountyInvestigationPhase.RESULTS || result == null) return
        if (result.articles.size < DossierBuilder.MIN_ARTICLES) return
        dossierJob?.cancel()
        dossierJob = viewModelScope.launch {
            val built = withContext(defaultDispatcher) { DossierBuilder.build(result) } ?: return@launch
            _dossier.value = built
            _readingUiState.update { it.copy(showDossierSheet = true) }
        }
    }

    fun dismissDossier() {
        dossierJob?.cancel()
        dossierJob = null
        _dossier.value = null
        _readingUiState.update { it.copy(showDossierSheet = false) }
    }

    /** Writes the Markdown export and returns a chooser intent, or null (no dossier, no sharer, or I/O failure). */
    suspend fun prepareDossierShare(chooserTitle: String): Intent? {
        val current = _dossier.value ?: return null
        val sharer = dossierSharer ?: return null
        return try {
            val markdown = withContext(defaultDispatcher) { DossierMarkdownBuilder.build(current) }
            val file = sharer.write(markdown, current.query)
            sharer.buildShareIntent(file, chooserTitle)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("roy93~Bounty", "[VM.prepareDossierShare] failed: $e")
            null
        }
    }

    private fun loadContrastingCoverage(articleWithFeed: ArticleWithFeed?) {
        contrastJob?.cancel()
        contrastPhrases = emptyList()
        _contrastingCoverage.value = ContrastingCoverageState.Idle
        val item = articleWithFeed ?: return
        contrastJob = viewModelScope.launch {
            _contrastingCoverage.value = ContrastingCoverageState.Searching
            val phrases = withContext(defaultDispatcher) {
                TopicPhraseExtractor.extract(item.article.title, item.feed.name)
            }
            if (phrases.isEmpty()) {
                _contrastingCoverage.value = ContrastingCoverageState.None
                return@launch
            }
            contrastPhrases = phrases
            searchContrast(item, phrases.first(), phrases)
        }
    }

    /** Re-runs the archive search for one of the phrases extracted from the current headline. */
    fun selectContrastPhrase(phrase: String) {
        val item = _readingUiState.value.articleWithFeed ?: return
        if (phrase !in contrastPhrases) return
        val phrases = contrastPhrases
        contrastJob?.cancel()
        contrastJob = viewModelScope.launch {
            _contrastingCoverage.value = ContrastingCoverageState.Searching
            searchContrast(item, phrase, phrases)
        }
    }

    private suspend fun searchContrast(item: ArticleWithFeed, phrase: String, allPhrases: List<String>) {
        val dao = articleDao
        if (dao == null) {
            _contrastingCoverage.value = ContrastingCoverageState.None
            return
        }
        try {
            val candidates = withContext(ioDispatcher) {
                dao.queryRelatedArticleListWithFeed(
                    accountId = item.article.accountId,
                    currentArticleId = item.article.id,
                    escapedKeyword = BountyQuerySanitizer.escapeLikeLiteral(phrase),
                    limit = ContrastingCoverageFinder.CANDIDATE_LIMIT,
                )
            }
            val contrasting = withContext(defaultDispatcher) {
                ContrastingCoverageFinder.find(item.article.id, item.feed.id, phrase, candidates)
            }
            _contrastingCoverage.value =
                if (contrasting.isEmpty()) ContrastingCoverageState.None
                else ContrastingCoverageState.Ready(allPhrases, phrase, contrasting)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A failed lookup must never break the reader; the card simply does not appear.
            Log.w("roy93~Contrast", "[VM.searchContrast] failed: $e")
            _contrastingCoverage.value = ContrastingCoverageState.None
        }
    }

    fun dismissBountyInvestigation() {
        dismissDossier()
        bountyJob?.cancel()
        bountyAiJob?.cancel()
        bountyJob = null
        bountyAiJob = null
        _bountyInvestigationState.value = BountyInvestigationState()
        _readingUiState.update { it.copy(showBountySheet = false) }
    }

    fun dismissSummary() {
        Log.d("roy93~AI", "[VM.dismissSummary]")
        _readingUiState.update { it.copy(showSummarySheet = false) }
    }

    fun requestSummary(forceOffline: Boolean = false) {
        val state = _readingUiState.value
        val article = state.articleWithFeed?.article
        val requestArticleId = article?.id
        val rawContent = state.content

        _readingUiState.update { it.copy(summaryState = SummaryState.Loading) }
        summaryJob?.cancel()
        summaryJob = viewModelScope.launch {
            val plainText = withContext(defaultDispatcher) {
                rawContent?.let {
                    androidx.core.text.HtmlCompat
                        .fromHtml(it, androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY)
                        .toString()
                }.orEmpty()
            }
            Log.d("roy93~AI", "[VM.requestSummary] contentLen=${rawContent?.length ?: 0} plainTextLen=${plainText.length} forceOffline=$forceOffline")

            if (plainText.isBlank()) {
                if (_readingUiState.value.articleWithFeed?.article?.id == requestArticleId) {
                    _readingUiState.update { it.copy(summaryState = SummaryState.Error(R.string.summary_err_empty_content)) }
                }
                return@launch
            }

            try {
                val highlights = if (forceOffline) {
                    com.mckimquyen.reader.infrastructure.ai.ArticleHighlightsExtractor.extractOfflineHighlights(
                        title = article?.title.orEmpty(),
                        plainText = plainText,
                    )
                } else {
                    summaryService.extractHighlights(
                        title = article?.title.orEmpty(),
                        plainText = plainText,
                    )
                }
                Log.d("roy93~AI", "[VM.requestSummary] ✅ Success highlights=${highlights.keyTakeaways.size} offline=${highlights.isOfflineFallback}")
                // The article shown on screen may have changed while this request was
                // in flight (e.g. user swiped to the next article). Only apply the
                // result if it still belongs to the article currently displayed.
                if (_readingUiState.value.articleWithFeed?.article?.id == requestArticleId) {
                    _readingUiState.update { it.copy(summaryState = SummaryState.Success(highlights)) }
                    // Persist to Room SQLite DB so user never has to re-generate across sessions
                    requestArticleId?.let { id ->
                        val serialized = com.mckimquyen.reader.infrastructure.ai.ArticleHighlightsExtractor.serialize(highlights)
                        rssService.get().updateArticleAiSummary(id, serialized)
                        _readingUiState.value.articleWithFeed?.article?.aiSummary = serialized
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("roy93~AI", "[VM.requestSummary] ❌ Error: $e", e)
                if (_readingUiState.value.articleWithFeed?.article?.id == requestArticleId) {
                    _readingUiState.update { it.copy(summaryState = e.toSummaryErrorState()) }
                }
            }
        }
    }

    // ---- AI Concept Mind Map ----

    fun openMindMap() {
        Log.d("roy93~AI", "[VM.openMindMap] clicked, article=${_readingUiState.value.articleWithFeed?.article?.id}")
        _readingUiState.update { it.copy(showMindMapSheet = true) }
        requestMindMap()
    }

    fun dismissMindMap() {
        Log.d("roy93~AI", "[VM.dismissMindMap]")
        _readingUiState.update { it.copy(showMindMapSheet = false) }
    }

    fun requestMindMap(forceOffline: Boolean = false) {
        val state = _readingUiState.value
        val article = state.articleWithFeed?.article
        val requestArticleId = article?.id
        val rawContent = state.content

        _readingUiState.update { it.copy(mindMapState = MindMapState.Loading) }
        mindMapJob?.cancel()
        mindMapJob = viewModelScope.launch {
            val plainText = withContext(defaultDispatcher) {
                rawContent?.let {
                    androidx.core.text.HtmlCompat
                        .fromHtml(it, androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY)
                        .toString()
                }.orEmpty()
            }
            Log.d("roy93~AI", "[VM.requestMindMap] contentLen=${rawContent?.length ?: 0} plainTextLen=${plainText.length} forceOffline=$forceOffline")

            if (plainText.isBlank()) {
                if (_readingUiState.value.articleWithFeed?.article?.id == requestArticleId) {
                    _readingUiState.update { it.copy(mindMapState = MindMapState.Error(R.string.summary_err_empty_content)) }
                }
                return@launch
            }

            try {
                val mindMap = if (forceOffline) {
                    com.mckimquyen.reader.infrastructure.ai.ArticleMindMapExtractor.extractOfflineMindMap(
                        title = article?.title.orEmpty(),
                        plainText = plainText,
                    )
                } else {
                    summaryService.generateMindMap(
                        title = article?.title.orEmpty(),
                        plainText = plainText,
                    )
                }
                Log.d("roy93~AI", "[VM.requestMindMap] ✅ Success nodes=${mindMap.nodes.size} offline=${mindMap.isOfflineFallback}")
                if (_readingUiState.value.articleWithFeed?.article?.id == requestArticleId) {
                    _readingUiState.update { it.copy(mindMapState = MindMapState.Success(mindMap)) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("roy93~AI", "[VM.requestMindMap] ❌ Error: $e", e)
                if (_readingUiState.value.articleWithFeed?.article?.id == requestArticleId) {
                    _readingUiState.update { it.copy(mindMapState = e.toMindMapErrorState()) }
                }
            }
        }
    }

    // ---- AI Deep Read (Interactive Q&A) ----

    fun openDeepRead() {
        val state = _readingUiState.value
        val article = state.articleWithFeed?.article
        val articleId = article?.id.orEmpty()
        val rawContent = state.content

        Log.d("roy93~AI", "[VM.openDeepRead] clicked, articleId=$articleId")

        _readingUiState.update { it.copy(showDeepReadSheet = true) }

        val currentActive = state.deepReadState as? DeepReadState.Active
        if (currentActive == null || currentActive.session.articleId != articleId) {
            val title = article?.title.orEmpty()
            viewModelScope.launch {
                val plainText = withContext(defaultDispatcher) {
                    rawContent?.let {
                        androidx.core.text.HtmlCompat
                            .fromHtml(it, androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY)
                            .toString()
                    }.orEmpty()
                }
                val chips = withContext(defaultDispatcher) {
                    com.mckimquyen.reader.infrastructure.ai.ArticleDeepReadEngine.generateSuggestedQuestions(
                        title = title,
                        plainText = plainText,
                    )
                }
                val welcomeMessage = DeepReadMessage(
                    sender = DeepReadSender.ASSISTANT,
                    content = "", // Display default localized welcome string in UI
                    isOfflineFallback = false,
                    isGrounded = true,
                )
                val newSession = DeepReadSession(
                    articleId = articleId,
                    articleTitle = title,
                    messages = listOf(welcomeMessage),
                    suggestedChips = chips,
                )
                if (_readingUiState.value.articleWithFeed?.article?.id == articleId) {
                    _readingUiState.update { it.copy(deepReadState = DeepReadState.Active(newSession)) }
                }
            }
        }
    }

    fun dismissDeepRead() {
        Log.d("roy93~AI", "[VM.dismissDeepRead]")
        _readingUiState.update { it.copy(showDeepReadSheet = false) }
    }

    fun clearDeepReadChat() {
        val currentActive = _readingUiState.value.deepReadState as? DeepReadState.Active ?: return
        val welcomeMessage = DeepReadMessage(
            sender = DeepReadSender.ASSISTANT,
            content = "",
            isOfflineFallback = false,
            isGrounded = true,
        )
        val resetSession = currentActive.session.copy(messages = listOf(welcomeMessage))
        _readingUiState.update {
            it.copy(deepReadState = DeepReadState.Active(resetSession))
        }
    }

    fun sendDeepReadQuestion(question: String) {
        val trimmed = question.trim()
        if (trimmed.isBlank()) return
        val state = _readingUiState.value
        val active = state.deepReadState as? DeepReadState.Active ?: return
        if (active.isSending) return

        val userMessage = DeepReadMessage(
            sender = DeepReadSender.USER,
            content = trimmed,
        )
        val updatedMessages = active.session.messages + userMessage
        val updatedSession = active.session.copy(messages = updatedMessages)
        _readingUiState.update {
            it.copy(deepReadState = DeepReadState.Active(updatedSession, isSending = true))
        }

        val article = state.articleWithFeed?.article
        val requestArticleId = active.session.articleId
        val rawContent = state.content

        deepReadJob?.cancel()
        deepReadJob = viewModelScope.launch {
            val plainText = withContext(defaultDispatcher) {
                rawContent?.let {
                    androidx.core.text.HtmlCompat
                        .fromHtml(it, androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY)
                        .toString()
                }.orEmpty()
            }
            try {
                val assistantReply = summaryService.askArticleQuestion(
                    title = article?.title.orEmpty(),
                    plainText = plainText,
                    chatHistory = updatedMessages,
                    question = trimmed,
                )
                _readingUiState.update { current ->
                    val curActive = current.deepReadState as? DeepReadState.Active
                    if (curActive != null && curActive.session.articleId == requestArticleId) {
                        current.copy(
                            deepReadState = DeepReadState.Active(
                                curActive.session.copy(messages = curActive.session.messages + assistantReply),
                                isSending = false,
                            )
                        )
                    } else {
                        current
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("roy93~AI", "[VM.sendDeepReadQuestion] error: $e", e)
                val fallbackReply = com.mckimquyen.reader.infrastructure.ai.ArticleDeepReadEngine.generateOfflineAnswer(
                    title = article?.title.orEmpty(),
                    plainText = plainText,
                    question = trimmed,
                )
                _readingUiState.update { current ->
                    val curActive = current.deepReadState as? DeepReadState.Active
                    if (curActive != null && curActive.session.articleId == requestArticleId) {
                        current.copy(
                            deepReadState = DeepReadState.Active(
                                curActive.session.copy(messages = curActive.session.messages + fallbackReply),
                                isSending = false,
                            )
                        )
                    } else {
                        current
                    }
                }
            }
        }
    }

    override fun onCleared() {
        Log.d("roy93~", "ReadingViewModel onCleared: stopping TTS and ZenAudio")
        ttsManager.stop()
        zenAudioManager.stop()
        super.onCleared()
    }

    /**
     * Ghi nhận lần đọc đầu tiên của bài đang mở (readAt giữ nguyên nếu đã có). Lỗi DB chỉ log, không crash.
     * Không gọi từ markUnread/markAllAsRead/sync: chỉ khi người dùng thật sự mở bài ở Reader.
     */
    fun recordOpened(nowMillis: Long = System.currentTimeMillis()) {
        val dao = articleDao ?: return
        val article = _readingUiState.value.articleWithFeed?.article ?: return
        val accountId = article.accountId
        val articleId = article.id
        viewModelScope.launch(ioDispatcher) {
            try {
                dao.markOpened(accountId, articleId, nowMillis)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("ReadingViewModel", "markOpened failed", e)
            }
        }
    }

    fun markUnread(isUnread: Boolean) {
        val articleWithFeed = _readingUiState.value.articleWithFeed ?: return
        // Capture articleId before the coroutine launch to avoid a race condition
        // where articleWithFeed could be set to null by initData() before line 138 runs.
        val articleId = articleWithFeed.article.id
        viewModelScope.launch {
            _readingUiState.update {
                it.copy(
                    articleWithFeed = articleWithFeed.copy(
                        article = articleWithFeed.article.copy(
                            isUnread = isUnread
                        )
                    )
                )
            }
            rssService.get().markAsRead(
                groupId = null,
                feedId = null,
                articleId = articleId,
                before = null,
                isUnread = isUnread,
            )
        }
    }

    fun markStarred(isStarred: Boolean) {
        val articleWithFeed = _readingUiState.value.articleWithFeed ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _readingUiState.update {
                it.copy(
                    articleWithFeed = articleWithFeed.copy(
                        article = articleWithFeed.article.copy(
                            isStarred = isStarred
                        )
                    )
                )
            }
            rssService.get().markAsStarred(
                articleId = articleWithFeed.article.id,
                isStarred = isStarred,
            )
        }
    }

    private fun showLoading() {
        _readingUiState.update {
            it.copy(isLoading = true)
        }
    }

    private fun hideLoading() {
        _readingUiState.update {
            it.copy(isLoading = false)
        }
    }

    private companion object {
        const val MIN_SAVED_PROGRESS = 0.05f
        const val MAX_SAVED_PROGRESS = 0.95f
        const val BOUNTY_MIN_SCAN_MILLIS = 700L
    }

    fun recorderNextArticle(pagingItems: ItemSnapshotList<ArticleFlowItem>) {
        if (pagingItems.size > 0) {
            val cur = _readingUiState.value.articleWithFeed?.article
            if (cur != null) {
                var found = false
                for (item in pagingItems) {
                    val candidateIds = when (item) {
                        is ArticleFlowItem.Article -> listOf(item.articleWithFeed.article.id)
                        is ArticleFlowItem.Cluster -> listOf(item.cluster.leadArticle.article.id) + item.cluster.articles.map { it.article.id }
                        else -> emptyList()
                    }
                    if (candidateIds.contains(cur.id)) {
                        found = true
                        _readingUiState.update {
                            it.copy(nextArticleId = "")
                        }
                    } else if (found && candidateIds.isNotEmpty()) {
                        val nextId = candidateIds.first()
                        _readingUiState.update {
                            it.copy(nextArticleId = nextId)
                        }
                        break
                    }
                }
            }
        }
    }
}

data class ReadPositionRestoreEvent(
    val articleId: String,
    val progress: Float,
)

@Keep
data class ReadingUiState(
    val articleWithFeed: ArticleWithFeed? = null,
    val content: String? = null,
    val isFullContent: Boolean = false,
    val isLoading: Boolean = true,
    val nextArticleId: String = "",
    val ttsState: TtsState = TtsState.IDLE,
    val showSummarySheet: Boolean = false,
    val summaryState: SummaryState = SummaryState.Idle,
    val savedReadPosition: ArticleReadPosition? = null,
    val showMindMapSheet: Boolean = false,
    val mindMapState: MindMapState = MindMapState.Idle,
    val showDeepReadSheet: Boolean = false,
    val deepReadState: DeepReadState = DeepReadState.Idle,
    val showQuoteSheet: Boolean = false,
    val quoteSpec: QuoteCardSpec? = null,
    val showBountySheet: Boolean = false,
    val showEchoSheet: Boolean = false,
    val showDossierSheet: Boolean = false,
)

/** Trạng thái của luồng hỏi đáp tương tác AI Deep Read. */
sealed interface DeepReadState {
    object Idle : DeepReadState
    data class Active(
        val session: DeepReadSession,
        val isSending: Boolean = false,
        @StringRes val errorRes: Int? = null,
    ) : DeepReadState
}

/** Trạng thái của luồng tóm tắt AI. */
sealed interface SummaryState {
    object Idle : SummaryState
    object Loading : SummaryState
    data class Success(val highlights: ArticleHighlights) : SummaryState {
        val text: String get() = highlights.formatAsPlainText()
    }
    /** [messageRes] là string resource (đa ngôn ngữ); [arg] tuỳ chọn dùng cho format (vd HTTP code). */
    data class Error(@StringRes val messageRes: Int, val arg: Int? = null) : SummaryState
}

/** Trạng thái của luồng sơ đồ tư duy AI. */
sealed interface MindMapState {
    object Idle : MindMapState
    object Loading : MindMapState
    data class Success(val mindMap: ArticleMindMap) : MindMapState
    /** [messageRes] là string resource (đa ngôn ngữ); [arg] tuỳ chọn dùng cho format (vd HTTP code). */
    data class Error(@StringRes val messageRes: Int, val arg: Int? = null) : MindMapState
}

/** Map exception của service sang [SummaryState.Error] với string resource đa ngôn ngữ. */
private fun Throwable.toSummaryErrorState(): SummaryState.Error = when (this) {
    is GeminiSummaryService.SummaryException.EmptyContent ->
        SummaryState.Error(R.string.summary_err_empty_content)
    is GeminiSummaryService.SummaryException.InvalidApiKey ->
        SummaryState.Error(R.string.summary_err_invalid_key)
    is GeminiSummaryService.SummaryException.RateLimited ->
        SummaryState.Error(R.string.summary_err_rate_limited)
    is GeminiSummaryService.SummaryException.Http ->
        SummaryState.Error(R.string.summary_err_http, code)
    is GeminiSummaryService.SummaryException.EmptyResponse ->
        SummaryState.Error(R.string.summary_err_empty_response)
    is GeminiSummaryService.SummaryException.ParseError ->
        SummaryState.Error(R.string.summary_err_parse)
    is GeminiSummaryService.SummaryException.Network ->
        SummaryState.Error(R.string.summary_err_network)
    else -> SummaryState.Error(R.string.summary_err_unknown)
}

/** Map exception của service sang [MindMapState.Error] với string resource đa ngôn ngữ. */
private fun Throwable.toMindMapErrorState(): MindMapState.Error = when (this) {
    is GeminiSummaryService.SummaryException.EmptyContent ->
        MindMapState.Error(R.string.summary_err_empty_content)
    is GeminiSummaryService.SummaryException.InvalidApiKey ->
        MindMapState.Error(R.string.summary_err_invalid_key)
    is GeminiSummaryService.SummaryException.RateLimited ->
        MindMapState.Error(R.string.summary_err_rate_limited)
    is GeminiSummaryService.SummaryException.Http ->
        MindMapState.Error(R.string.summary_err_http, code)
    is GeminiSummaryService.SummaryException.EmptyResponse ->
        MindMapState.Error(R.string.summary_err_empty_response)
    is GeminiSummaryService.SummaryException.ParseError ->
        MindMapState.Error(R.string.summary_err_parse)
    is GeminiSummaryService.SummaryException.Network ->
        MindMapState.Error(R.string.summary_err_network)
    else -> MindMapState.Error(R.string.summary_err_unknown)
}
