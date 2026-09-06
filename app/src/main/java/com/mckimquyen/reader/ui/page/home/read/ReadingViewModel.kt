package com.mckimquyen.reader.ui.page.home.read

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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReadingViewModel @Inject constructor(
    private val rssService: RssSv,
    private val rssHelper: RssHelper,
    private val ttsManager: TtsManager,
    private val summaryService: GeminiSummaryService,
    val zenAudioManager: ZenAudioManager,
) : ViewModel() {

    private val _readingUiState = MutableStateFlow(ReadingUiState())
    val readingUiState: StateFlow<ReadingUiState> = _readingUiState.asStateFlow()

    private val _scrollToTopEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val scrollToTopEvent: SharedFlow<Unit> = _scrollToTopEvent.asSharedFlow()

    private var fetchJob: kotlinx.coroutines.Job? = null

    init {
        // Collect TTS state once per ViewModel lifetime.
        // Previously this was inside initData(), which created a new collector
        // on every article open — causing duplicate event processing over time.
        viewModelScope.launch {
            ttsManager.ttsState.collect { state ->
                _readingUiState.update { it.copy(ttsState = state) }
            }
        }
    }

    fun initData(articleId: String, autoTtsEnabled: Boolean = false) {
        // Capture current TTS state before switching feed
        val wasPlaying = _readingUiState.value.ttsState == TtsState.PLAYING
        val shouldAutoPlay = autoTtsEnabled || wasPlaying
        
        if (wasPlaying) {
            Log.d("roy93~", "ReadingViewModel: Switching feed, stopping current TTS")
            ttsManager.stop()
        }

        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            showLoading()
            _readingUiState.update {
                it.copy(articleWithFeed = rssService.get().findArticleById(articleId))
            }
            _readingUiState.value.articleWithFeed?.let {
                if (it.feed.isFullContent) internalRenderFullContent(shouldAutoPlay)
                else renderDescriptionContent(shouldAutoPlay)
            }
            _scrollToTopEvent.tryEmit(Unit)
            hideLoading()
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
                        _readingUiState.value.articleWithFeed?.article?.title ?: ""
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

    private fun playCurrentContent() {
        val content = _readingUiState.value.content
        if (content == null) {
            Log.d("roy93~", "ReadingViewModel content is null, cannot play")
            return
        }
        Log.d("roy93~", "ReadingViewModel parsing HTML to plain text")
        // Use Regex to remove HTML tags or parse it
        val plainText = androidx.core.text.HtmlCompat.fromHtml(
            content, androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY
        ).toString()
        Log.d("roy93~", "ReadingViewModel starting TTS play")
        ttsManager.play(plainText)
    }

    fun togglePlayAudio() {
        Log.d("roy93~", "ReadingViewModel togglePlayAudio called. Current state: ${_readingUiState.value.ttsState}")
        if (_readingUiState.value.ttsState == TtsState.PLAYING) {
            Log.d("roy93~", "ReadingViewModel stopping TTS")
            ttsManager.stop()
        } else {
            playCurrentContent()
        }
    }

    // ---- AI Summary (Gemini) ----

    /** Mở BottomSheet tóm tắt rồi gọi Gemini ngay. Nút ✨ chỉ hiện khi đã có key dev nhúng,
     *  nên không cần hỏi key người dùng nữa. */
    fun openSummary() {
        Log.d("roy93~AI", "[VM.openSummary] clicked, article=${_readingUiState.value.articleWithFeed?.article?.id}")
        _readingUiState.update { it.copy(showSummarySheet = true) }
        requestSummary()
    }

    fun dismissSummary() {
        Log.d("roy93~AI", "[VM.dismissSummary]")
        _readingUiState.update { it.copy(showSummarySheet = false) }
    }

    fun requestSummary(forceOffline: Boolean = false) {
        val state = _readingUiState.value
        val article = state.articleWithFeed?.article
        val plainText = state.content?.let {
            androidx.core.text.HtmlCompat
                .fromHtml(it, androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY)
                .toString()
        }.orEmpty()
        Log.d("roy93~AI", "[VM.requestSummary] contentLen=${state.content?.length ?: 0} plainTextLen=${plainText.length} forceOffline=$forceOffline")

        if (plainText.isBlank()) {
            _readingUiState.update { it.copy(summaryState = SummaryState.Error(R.string.summary_err_empty_content)) }
            return
        }

        _readingUiState.update { it.copy(summaryState = SummaryState.Loading) }
        viewModelScope.launch {
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
                _readingUiState.update { it.copy(summaryState = SummaryState.Success(highlights)) }
            } catch (e: Exception) {
                Log.e("roy93~AI", "[VM.requestSummary] ❌ Error: $e", e)
                _readingUiState.update { it.copy(summaryState = e.toSummaryErrorState()) }
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
        val plainText = state.content?.let {
            androidx.core.text.HtmlCompat
                .fromHtml(it, androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY)
                .toString()
        }.orEmpty()
        Log.d("roy93~AI", "[VM.requestMindMap] contentLen=${state.content?.length ?: 0} plainTextLen=${plainText.length} forceOffline=$forceOffline")

        if (plainText.isBlank()) {
            _readingUiState.update { it.copy(mindMapState = MindMapState.Error(R.string.summary_err_empty_content)) }
            return
        }

        _readingUiState.update { it.copy(mindMapState = MindMapState.Loading) }
        viewModelScope.launch {
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
                _readingUiState.update { it.copy(mindMapState = MindMapState.Success(mindMap)) }
            } catch (e: Exception) {
                Log.e("roy93~AI", "[VM.requestMindMap] ❌ Error: $e", e)
                _readingUiState.update { it.copy(mindMapState = e.toMindMapErrorState()) }
            }
        }
    }

    // ---- AI Deep Read (Interactive Q&A) ----

    fun openDeepRead() {
        val state = _readingUiState.value
        val article = state.articleWithFeed?.article
        val articleId = article?.id.orEmpty()
        val plainText = state.content?.let {
            androidx.core.text.HtmlCompat
                .fromHtml(it, androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY)
                .toString()
        }.orEmpty()

        Log.d("roy93~AI", "[VM.openDeepRead] clicked, articleId=$articleId plainTextLen=${plainText.length}")

        val currentActive = state.deepReadState as? DeepReadState.Active
        if (currentActive == null || currentActive.session.articleId != articleId) {
            val title = article?.title.orEmpty()
            val chips = com.mckimquyen.reader.infrastructure.ai.ArticleDeepReadEngine.generateSuggestedQuestions(
                title = title,
                plainText = plainText,
            )
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
            _readingUiState.update {
                it.copy(
                    showDeepReadSheet = true,
                    deepReadState = DeepReadState.Active(newSession)
                )
            }
        } else {
            _readingUiState.update { it.copy(showDeepReadSheet = true) }
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
        val plainText = state.content?.let {
            androidx.core.text.HtmlCompat
                .fromHtml(it, androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY)
                .toString()
        }.orEmpty()

        viewModelScope.launch {
            try {
                val assistantReply = summaryService.askArticleQuestion(
                    title = article?.title.orEmpty(),
                    plainText = plainText,
                    chatHistory = updatedMessages,
                    question = trimmed,
                )
                _readingUiState.update { current ->
                    val curActive = current.deepReadState as? DeepReadState.Active
                    if (curActive != null) {
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
            } catch (e: Exception) {
                Log.e("roy93~AI", "[VM.sendDeepReadQuestion] error: $e", e)
                val fallbackReply = com.mckimquyen.reader.infrastructure.ai.ArticleDeepReadEngine.generateOfflineAnswer(
                    title = article?.title.orEmpty(),
                    plainText = plainText,
                    question = trimmed,
                )
                _readingUiState.update { current ->
                    val curActive = current.deepReadState as? DeepReadState.Active
                    if (curActive != null) {
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

    fun recorderNextArticle(pagingItems: ItemSnapshotList<ArticleFlowItem>) {
        if (pagingItems.size > 0) {
            val cur = _readingUiState.value.articleWithFeed?.article
            if (cur != null) {
                var found = false
                for (item in pagingItems) {
                    if (item is ArticleFlowItem.Article) {
                        val itemId = item.articleWithFeed.article.id
                        if (itemId == cur.id) {
                            found = true
                            _readingUiState.update {
                                it.copy(nextArticleId = "")
                            }
                        } else if (found) {
                            _readingUiState.update {
                                it.copy(nextArticleId = itemId)
                            }
                            break
                        }
                    }
                }
            }
        }
    }
}

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
    val showMindMapSheet: Boolean = false,
    val mindMapState: MindMapState = MindMapState.Idle,
    val showDeepReadSheet: Boolean = false,
    val deepReadState: DeepReadState = DeepReadState.Idle,
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
