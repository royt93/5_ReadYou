package com.mckimquyen.reader.ui.page.home.read.mirror

import android.util.Log
import androidx.core.text.HtmlCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mckimquyen.reader.domain.mirror.MirrorSnapshot
import com.mckimquyen.reader.domain.mirror.MirrorSnapshotBuilder
import com.mckimquyen.reader.domain.mirror.MirrorStartResult
import com.mckimquyen.reader.infrastructure.di.DefaultDispatcher
import com.mckimquyen.reader.infrastructure.mirror.LanAddressSource
import com.mckimquyen.reader.infrastructure.mirror.MirrorEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Owns one Second Screen session: starts and stops the LAN server, keeps the newest article ready for it and stops it
 * again when the session expires, the network goes away, the app leaves the foreground or this ViewModel is cleared.
 */
@HiltViewModel
class SecondScreenViewModel @Inject constructor(
    private val engine: MirrorEngine,
    private val lan: LanAddressSource,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    /** Replaced in tests; production reads the wall clock (the same clock the token's expiry is measured against). */
    internal var clock: () -> Long = System::currentTimeMillis

    private val _state = MutableStateFlow<MirrorUiState>(MirrorUiState.Off)
    val state: StateFlow<MirrorUiState> = _state.asStateFlow()

    private var latest: MirrorSnapshot? = null
    private var version = 0L

    private var startJob: Job? = null
    private var clientsJob: Job? = null
    private var lossJob: Job? = null
    private var expiryJob: Job? = null

    fun start() {
        when (_state.value) {
            MirrorUiState.Off, MirrorUiState.NoNetwork, MirrorUiState.Expired, is MirrorUiState.Error -> Unit
            MirrorUiState.Starting, is MirrorUiState.Running -> return
        }
        _state.value = MirrorUiState.Starting
        startJob?.cancel()
        startJob = viewModelScope.launch {
            val address = withContext(defaultDispatcher) { lan.current() }
            if (address == null) {
                _state.value = MirrorUiState.NoNetwork
                return@launch
            }
            when (val result = withContext(defaultDispatcher) { engine.start(address) }) {
                is MirrorStartResult.Failed -> _state.value = MirrorUiState.Error(result.reason)
                is MirrorStartResult.Started -> {
                    val url = "http://${address.hostAddress}:${result.port}/m/${result.token.value}"
                    _state.value = MirrorUiState.Running(url, engine.clients.value, result.token.expiresAtMillis)
                    latest?.let(engine::publish)
                    watch(result.token.expiresAtMillis)
                }
            }
        }
    }

    fun stop() = shutdown(MirrorUiState.Off)

    fun onAppBackgrounded() = shutdown(MirrorUiState.Off)

    /** Remembers the newest article and, when a session is running, sends it to every open browser. */
    fun updateArticle(title: String, source: String, dateMillis: Long, html: String?) {
        viewModelScope.launch {
            val snapshot = withContext(defaultDispatcher) {
                val plain = if (html.isNullOrBlank()) "" else HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
                MirrorSnapshotBuilder.fromPlainText(title, source, dateMillis, plain, ++version)
            }
            latest = snapshot
            if (_state.value is MirrorUiState.Running) engine.publish(snapshot)
        }
    }

    private fun watch(expiresAtMillis: Long) {
        clientsJob?.cancel()
        clientsJob = viewModelScope.launch {
            engine.clients.collect { count ->
                _state.update { if (it is MirrorUiState.Running) it.copy(clients = count) else it }
            }
        }
        lossJob?.cancel()
        lossJob = viewModelScope.launch {
            try {
                lan.networkLosses().collect { shutdown(MirrorUiState.NoNetwork) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "network watch failed: ${e.javaClass.simpleName}")
            }
        }
        expiryJob?.cancel()
        expiryJob = viewModelScope.launch {
            delay((expiresAtMillis - clock()).coerceAtLeast(0))
            shutdown(MirrorUiState.Expired)
        }
    }

    private fun shutdown(finalState: MirrorUiState) {
        startJob?.cancel()
        clientsJob?.cancel()
        lossJob?.cancel()
        expiryJob?.cancel()
        startJob = null
        clientsJob = null
        lossJob = null
        expiryJob = null
        engine.stop()
        _state.value = finalState
    }

    internal fun callOnCleared() = onCleared()

    override fun onCleared() {
        engine.stop()
        super.onCleared()
    }

    private companion object {
        const val TAG = "SecondScreen"
    }
}
