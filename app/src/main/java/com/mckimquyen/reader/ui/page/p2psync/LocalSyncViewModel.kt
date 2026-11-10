package com.mckimquyen.reader.ui.page.p2psync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mckimquyen.reader.domain.mirror.MirrorStartResult
import com.mckimquyen.reader.domain.p2psync.FeedUrlNormalizer
import com.mckimquyen.reader.domain.p2psync.LanGate
import com.mckimquyen.reader.domain.p2psync.PairedPeer
import com.mckimquyen.reader.domain.p2psync.PairingStatus
import com.mckimquyen.reader.domain.p2psync.SessionTokenDerivation
import com.mckimquyen.reader.domain.mirror.LanAddress
import com.mckimquyen.reader.infrastructure.p2psync.PairingStore
import com.mckimquyen.reader.infrastructure.p2psync.PeerDiscovery
import com.mckimquyen.reader.infrastructure.p2psync.DiscoveredPeer
import com.mckimquyen.reader.domain.p2psync.PairingSecret
import com.mckimquyen.reader.domain.p2psync.SyncEnvelopeCrypto
import com.mckimquyen.reader.domain.p2psync.SyncPackageCodec
import com.mckimquyen.reader.domain.p2psync.SyncPackageResult
import com.mckimquyen.reader.domain.p2psync.SyncSessionTransport
import com.mckimquyen.reader.domain.p2psync.SyncSessionUrl
import com.mckimquyen.reader.infrastructure.di.DefaultDispatcher
import com.mckimquyen.reader.infrastructure.di.IODispatcher
import com.mckimquyen.reader.infrastructure.mirror.LanAddressSource
import com.mckimquyen.reader.infrastructure.mirror.SyncSessionEngine
import com.mckimquyen.reader.infrastructure.p2psync.LocalSyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class LocalSyncViewModel @Inject constructor(
    private val repository: LocalSyncRepository,
    private val transport: SyncSessionTransport,
    private val engine: SyncSessionEngine,
    private val lan: LanAddressSource,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
    private val pairingStore: PairingStore,
    private val discovery: PeerDiscovery,
    private val lanGate: LanGate,
) : ViewModel() {
    internal var clock: () -> Long = System::currentTimeMillis

    private val _pairedPeers = MutableStateFlow<List<PairedPeer>>(emptyList())
    val pairedPeers: StateFlow<List<PairedPeer>> = _pairedPeers.asStateFlow()
    val discovered: StateFlow<List<DiscoveredPeer>> = discovery.peers
    private var activePeerId: String? = null
    private val _replyPeer = MutableStateFlow<ReplyPeer?>(null)
    val replyPeer: StateFlow<ReplyPeer?> = _replyPeer.asStateFlow()

    private val _state = MutableStateFlow<LocalSyncUiState>(LocalSyncUiState.Idle)
    val state: StateFlow<LocalSyncUiState> = _state.asStateFlow()
    private val _shareUrl = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val shareUrl = _shareUrl.asSharedFlow()
    private val _shareSecret = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val shareSecret = _shareSecret.asSharedFlow()

    private var sendJob: Job? = null
    private var receiveJob: Job? = null
    private var expiryJob: Job? = null
    private var networkJob: Job? = null
    private var activePackageId: String? = null
    private var activeAccountId: Int? = null

    fun startSend(localAccountId: Int) {
        if (_state.value !is LocalSyncUiState.Idle && _state.value !is LocalSyncUiState.Error &&
            _state.value !is LocalSyncUiState.Done) return
        if (!lanGate.granted()) {
            _state.value = LocalSyncUiState.Error(LocalSyncError.LAN_PERMISSION)
            return
        }
        _replyPeer.value = null
        sendJob?.cancel()
        activeAccountId = localAccountId
        _state.value = LocalSyncUiState.Starting
        sendJob = viewModelScope.launch {
            val packageData = try {
                withContext(ioDispatcher) { repository.snapshot(localAccountId) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.value = LocalSyncUiState.Error(LocalSyncError.NOT_LOCAL)
                return@launch
            }
            val address = withContext(ioDispatcher) { lan.current() }
            if (address == null) {
                _state.value = LocalSyncUiState.Error(LocalSyncError.NO_NETWORK)
                return@launch
            }
            val secret = PairingSecret.generate()
            val encrypted = withContext(defaultDispatcher) {
                SyncEnvelopeCrypto.encrypt(SyncPackageCodec.encode(packageData), secret)
            }
            when (val started = withContext(ioDispatcher) { engine.startSync(address, encrypted) }) {
                is MirrorStartResult.Failed -> {
                    encrypted.fill(0)
                    _state.value = LocalSyncUiState.Error(LocalSyncError.SERVER)
                }
                is MirrorStartResult.Started -> {
                    val url = "http://${address.hostAddress}:${started.port}/s/${started.token.value}"
                    _state.value = LocalSyncUiState.Sending(url, secret.hex, started.token.expiresAtMillis)
                    watchSession(address, started.token.expiresAtMillis)
                }
            }
        }
    }

    fun stopSend() {
        shutdown(LocalSyncUiState.Idle)
    }

    /** A non-blank [label] also remembers this device as a CONFIRMED pairing once the envelope has authenticated. */
    fun loadPreview(localAccountId: Int, url: String, secretHex: String, label: String? = null) {
        if (_state.value !is LocalSyncUiState.Idle && _state.value !is LocalSyncUiState.Error &&
            _state.value !is LocalSyncUiState.Done) return
        if (!lanGate.granted()) {
            _state.value = LocalSyncUiState.Error(LocalSyncError.LAN_PERMISSION)
            return
        }
        val pairLabel = label?.trim()?.takeIf { it.isNotEmpty() }
        if (pairLabel != null && !PairingStore.isValidLabel(pairLabel)) {
            _state.value = LocalSyncUiState.Error(LocalSyncError.PAIRING_LABEL)
            return
        }
        _replyPeer.value = null
        receiveJob?.cancel()
        activeAccountId = localAccountId
        _state.value = LocalSyncUiState.Receiving
        receiveJob = viewModelScope.launch {
            val secret = try {
                PairingSecret.fromHex(secretHex)
            } catch (_: IllegalArgumentException) {
                _state.value = LocalSyncUiState.Error(LocalSyncError.SECRET)
                return@launch
            }
            val normalizedUrl = url.trim()
            if (SyncSessionUrl.parse(normalizedUrl) == null) {
                _state.value = LocalSyncUiState.Error(LocalSyncError.URL)
                return@launch
            }
            fetchAndPreview(localAccountId, normalizedUrl, secret, confirmPeerId = null, newPeerLabel = pairLabel)
        }
    }

    /**
     * Shared tail of both receive paths: fetch the ciphertext, authenticate and decode it, then build the preview.
     * [confirmPeerId], when set, is marked CONFIRMED only after the GCM tag has verified, never before.
     */
    private suspend fun fetchAndPreview(
        localAccountId: Int,
        normalizedUrl: String,
        secret: PairingSecret,
        confirmPeerId: String?,
        newPeerLabel: String? = null,
    ) {
        _replyPeer.value = null
        val encrypted = try {
            withContext(ioDispatcher) { transport.fetch(normalizedUrl) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _state.value = LocalSyncUiState.Error(LocalSyncError.NETWORK)
            return
        }
        val plain = try {
            withContext(defaultDispatcher) { SyncEnvelopeCrypto.decrypt(encrypted, secret) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _state.value = LocalSyncUiState.Error(LocalSyncError.PACKAGE)
            return
        } finally {
            encrypted.fill(0)
        }
        // The GCM tag has verified: this is the first moment the other side is known to hold the same secret.
        confirmPeerId?.let { withContext(ioDispatcher) { pairingStore.markConfirmed(it) }; refreshPeers() }
        if (newPeerLabel != null) {
            try {
                withContext(ioDispatcher) { pairingStore.add(newPeerLabel, secret, PairingStatus.CONFIRMED) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: IllegalArgumentException) {
                _state.value = LocalSyncUiState.Error(LocalSyncError.PAIRING_LIMIT)
                plain.fill(0)
                return
            } catch (_: Exception) {
                _state.value = LocalSyncUiState.Error(LocalSyncError.PAIRING_BROKEN)
                plain.fill(0)
                return
            }
            refreshPeers()
        }
        val incoming = try {
            when (val decoded = withContext(defaultDispatcher) { SyncPackageCodec.decodeAndValidate(plain) }) {
                is SyncPackageResult.Valid -> decoded.data
                is SyncPackageResult.Invalid -> {
                    _state.value = LocalSyncUiState.Error(LocalSyncError.PACKAGE)
                    return
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _state.value = LocalSyncUiState.Error(LocalSyncError.PACKAGE)
            return
        } finally {
            plain.fill(0)
        }
        try {
            val plan = withContext(ioDispatcher) { repository.preview(localAccountId, incoming) }
            activePackageId = packageId(incoming)
            _state.value = LocalSyncUiState.Preview(incoming, plan)
            // Only a paired receive whose envelope authenticated and whose preview was built can be answered.
            confirmPeerId?.let { _replyPeer.value = ReplyPeer(localAccountId, it) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _state.value = LocalSyncUiState.Error(LocalSyncError.NOT_LOCAL)
        }
    }

    // ---- EXC-05b: pairing and discovery ----

    fun refreshPeers() {
        _pairedPeers.value = pairingStore.list()
    }

    /** Pair a new device: store its secret as UNCONFIRMED, serve a package under a token derived from it, advertise. */
    fun startPairedSend(localAccountId: Int, label: String) {
        if (!canStartSession()) return
        if (!PairingStore.isValidLabel(label)) {
            _state.value = LocalSyncUiState.Error(LocalSyncError.PAIRING_LABEL)
            return
        }
        startPairedSession(localAccountId, showSecret = true) {
            val secret = PairingSecret.generate()
            try {
                withContext(ioDispatcher) { pairingStore.add(label, secret, PairingStatus.UNCONFIRMED) }.peerId to secret
            } catch (e: CancellationException) {
                throw e
            } catch (_: IllegalArgumentException) {
                _state.value = LocalSyncUiState.Error(LocalSyncError.PAIRING_LIMIT)
                null
            } catch (_: Exception) {
                _state.value = LocalSyncUiState.Error(LocalSyncError.PAIRING_BROKEN)
                null
            }
        }
    }

    /** Serve to a device that is already paired: reuse its stored secret, show no secret, change no entry. */
    fun servePaired(localAccountId: Int, peerId: String) {
        if (!canStartSession()) return
        startPairedSession(localAccountId, showSecret = false) {
            val secret = withContext(ioDispatcher) { pairingStore.secretOf(peerId) }
            if (secret == null) {
                _state.value = LocalSyncUiState.Error(LocalSyncError.PAIRING_BROKEN)
                null
            } else {
                peerId to secret
            }
        }
    }

    /**
     * Close the current paired send session and start listening, so the other phone can send its state back.
     * Does nothing for a manual send or when nothing is being served.
     */
    fun waitForReply() {
        val sending = _state.value as? LocalSyncUiState.Sending ?: return
        if (sending.pairedPeerId == null) return
        shutdown(LocalSyncUiState.Idle)
        startFindingPeers()
    }

    /** Send this phone's current state back to the peer the last verified receive came from, under its stored secret. */
    fun replyToPeer(localAccountId: Int) {
        if (!isIdleLike()) return
        val reply = _replyPeer.value ?: return
        if (reply.accountId != localAccountId) return
        val peer = pairingStore.list().firstOrNull { it.peerId == reply.peerId }
        if (peer == null || peer.needsRepair) {
            _replyPeer.value = null
            _state.value = LocalSyncUiState.Error(LocalSyncError.PAIRING_BROKEN)
            return
        }
        servePaired(localAccountId, peer.peerId)
    }

    private fun isIdleLike(): Boolean = _state.value is LocalSyncUiState.Idle ||
        _state.value is LocalSyncUiState.Error || _state.value is LocalSyncUiState.Done

    private fun canStartSession(): Boolean {
        if (_state.value !is LocalSyncUiState.Idle && _state.value !is LocalSyncUiState.Error &&
            _state.value !is LocalSyncUiState.Done) return false
        if (!lanGate.granted()) {
            _state.value = LocalSyncUiState.Error(LocalSyncError.LAN_PERMISSION)
            return false
        }
        return true
    }

    /** [resolve] yields the peer id and secret to serve under, or sets an error state itself and returns null. */
    private fun startPairedSession(localAccountId: Int, showSecret: Boolean, resolve: suspend () -> Pair<String, PairingSecret>?) {
        _replyPeer.value = null
        sendJob?.cancel()
        activeAccountId = localAccountId
        _state.value = LocalSyncUiState.Starting
        sendJob = viewModelScope.launch {
            val packageData = try {
                withContext(ioDispatcher) { repository.snapshot(localAccountId) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.value = LocalSyncUiState.Error(LocalSyncError.NOT_LOCAL)
                return@launch
            }
            val address = withContext(ioDispatcher) { lan.current() }
            if (address == null) {
                _state.value = LocalSyncUiState.Error(LocalSyncError.NO_NETWORK)
                return@launch
            }
            val (peerId, secret) = resolve() ?: return@launch
            refreshPeers()
            val nonce = SessionTokenDerivation.newNonce()
            val token = SessionTokenDerivation.derive(secret, nonce)
            val encrypted = withContext(defaultDispatcher) {
                SyncEnvelopeCrypto.encrypt(SyncPackageCodec.encode(packageData), secret)
            }
            when (val started = withContext(ioDispatcher) { engine.startSyncWithToken(address, encrypted, token) }) {
                is MirrorStartResult.Failed -> {
                    encrypted.fill(0)
                    _state.value = LocalSyncUiState.Error(LocalSyncError.SERVER)
                }
                is MirrorStartResult.Started -> {
                    activePeerId = peerId
                    withContext(ioDispatcher) { discovery.advertise(started.port, nonce) }
                    val url = "http://${address.hostAddress}:${started.port}/s/${started.token.value}"
                    _state.value = LocalSyncUiState.Sending(
                        url, if (showSecret) secret.hex else null, started.token.expiresAtMillis, peerId,
                    )
                    watchSession(address, started.token.expiresAtMillis)
                }
            }
        }
    }

    fun startFindingPeers() {
        if (!lanGate.granted()) {
            _state.value = LocalSyncUiState.Error(LocalSyncError.LAN_PERMISSION)
            return
        }
        discovery.startDiscovery()
    }

    fun stopFindingPeers() = discovery.stop()

    /** Receive from an already paired device: the token is computed from the stored secret and the peer's nonce. */
    fun receiveFromPaired(localAccountId: Int, peerId: String, host: String, port: Int, nonce: ByteArray) {
        if (_state.value !is LocalSyncUiState.Idle && _state.value !is LocalSyncUiState.Error &&
            _state.value !is LocalSyncUiState.Done) return
        if (!lanGate.granted()) {
            _state.value = LocalSyncUiState.Error(LocalSyncError.LAN_PERMISSION)
            return
        }
        _replyPeer.value = null
        receiveJob?.cancel()
        activeAccountId = localAccountId
        _state.value = LocalSyncUiState.Receiving
        receiveJob = viewModelScope.launch {
            val secret = withContext(ioDispatcher) { pairingStore.secretOf(peerId) }
            if (secret == null) {
                _state.value = LocalSyncUiState.Error(LocalSyncError.PAIRING_BROKEN)
                return@launch
            }
            if (!LanAddress.isPrivateIpv4(host)) {
                _state.value = LocalSyncUiState.Error(LocalSyncError.URL)
                return@launch
            }
            val url = "http://$host:$port/s/${SessionTokenDerivation.derive(secret, nonce)}"
            if (SyncSessionUrl.parse(url) == null) {
                _state.value = LocalSyncUiState.Error(LocalSyncError.URL)
                return@launch
            }
            fetchAndPreview(localAccountId, url, secret, confirmPeerId = peerId)
        }
    }

    fun confirmPeer(peerId: String) {
        pairingStore.markConfirmed(peerId)
        refreshPeers()
    }

    fun forgetPeer(peerId: String) {
        if (_replyPeer.value?.peerId == peerId) _replyPeer.value = null
        if (activePeerId == peerId) shutdown(LocalSyncUiState.Idle)
        pairingStore.forget(peerId)
        refreshPeers()
    }

    fun cancelPreview() {
        receiveJob?.cancel()
        receiveJob = null
        activePackageId = null
        _state.value = LocalSyncUiState.Idle
    }

    fun applyPreview(localAccountId: Int) {
        val preview = _state.value as? LocalSyncUiState.Preview ?: return
        if (_replyPeer.value?.accountId != localAccountId) _replyPeer.value = null
        if (activePackageId != packageId(preview.incoming) || activeAccountId != localAccountId) return
        _state.value = LocalSyncUiState.Applying
        viewModelScope.launch {
            try {
                val result = withContext(ioDispatcher) { repository.apply(localAccountId, preview.incoming) }
                activePackageId = null
                _state.value = LocalSyncUiState.Done(result)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                activePackageId = null
                _replyPeer.value = null
                _state.value = LocalSyncUiState.Error(LocalSyncError.DATABASE)
            }
        }
    }

    fun onScreenStopped() {
        when (_state.value) {
            is LocalSyncUiState.Sending -> shutdown(LocalSyncUiState.Idle)
            is LocalSyncUiState.Preview, LocalSyncUiState.Receiving -> cancelPreview()
            else -> Unit
        }
        _replyPeer.value = null
        discovery.stop()
    }

    fun shareServerUrl(url: String) {
        if (_state.value is LocalSyncUiState.Sending && (_state.value as LocalSyncUiState.Sending).url == url) {
            _shareUrl.tryEmit(url)
        }
    }

    fun sharePairingSecret(secret: String) {
        val sending = _state.value as? LocalSyncUiState.Sending ?: return
        if (sending.secretHex != null && sending.secretHex == secret) _shareSecret.tryEmit(secret)
    }

    private fun watchSession(address: InetAddress, expiresAtMillis: Long) {
        networkJob?.cancel()
        networkJob = viewModelScope.launch {
            try {
                lan.networkLosses().collect { shutdown(LocalSyncUiState.Error(LocalSyncError.NO_NETWORK)) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                shutdown(LocalSyncUiState.Error(LocalSyncError.NO_NETWORK))
            }
        }
        expiryJob?.cancel()
        expiryJob = viewModelScope.launch {
            delay((expiresAtMillis - clock()).coerceAtLeast(0))
            shutdown(LocalSyncUiState.Error(LocalSyncError.EXPIRED))
        }
    }

    private fun shutdown(finalState: LocalSyncUiState) {
        sendJob?.cancel()
        receiveJob?.cancel()
        expiryJob?.cancel()
        networkJob?.cancel()
        sendJob = null
        receiveJob = null
        expiryJob = null
        networkJob = null
        activePackageId = null
        activeAccountId = null
        activePeerId = null
        _replyPeer.value = null
        engine.stop()
        discovery.stop()
        _state.value = finalState
    }

    private fun packageId(packageData: com.mckimquyen.reader.domain.p2psync.SyncPackage): String =
        UUID.nameUUIDFromBytes(SyncPackageCodec.encode(packageData)).toString()

    internal fun callOnCleared() = onCleared()

    override fun onCleared() {
        _replyPeer.value = null
        engine.stop()
        discovery.stop()
        super.onCleared()
    }
}
