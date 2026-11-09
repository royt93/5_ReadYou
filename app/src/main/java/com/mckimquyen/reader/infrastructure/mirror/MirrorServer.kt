package com.mckimquyen.reader.infrastructure.mirror

import com.mckimquyen.reader.domain.mirror.MirrorFailure
import com.mckimquyen.reader.domain.mirror.MirrorLimits
import com.mckimquyen.reader.domain.mirror.MirrorParseResult
import com.mckimquyen.reader.domain.mirror.MirrorRateLimiter
import com.mckimquyen.reader.domain.mirror.MirrorRequestParser
import com.mckimquyen.reader.domain.mirror.MirrorResponse
import com.mckimquyen.reader.domain.mirror.MirrorRouter
import com.mckimquyen.reader.domain.mirror.MirrorSnapshot
import com.mckimquyen.reader.domain.mirror.MirrorStartResult
import com.mckimquyen.reader.domain.mirror.MirrorToken
import com.mckimquyen.reader.domain.mirror.SyncSessionResponse
import com.mckimquyen.reader.domain.mirror.SyncSessionRouter
import com.mckimquyen.reader.domain.p2psync.SyncEnvelopeCrypto
import com.mckimquyen.reader.domain.p2psync.SyncLimits
import java.security.SecureRandom
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A tiny read-only HTTP server on a plain JDK [ServerSocket]. It binds exactly the address it is given (never the
 * wildcard), answers GET for three token-protected paths and pushes the current article to open event streams.
 * Every socket has a timeout, every request head is bounded, and [stop] closes everything.
 */
class MirrorServer(
    private val clock: () -> Long = System::currentTimeMillis,
    private val socketTimeoutMillis: Int = MirrorLimits.SOCKET_TIMEOUT_MILLIS,
) : MirrorEngine, SyncSessionEngine {
    private val lock = Any()
    private var session: Session? = null
    private val _clients = MutableStateFlow(0)
    override val clients: StateFlow<Int> = _clients.asStateFlow()

    override val isRunning: Boolean get() = synchronized(lock) { session != null }

    /** Address the socket is bound to, exposed so tests can prove it is never the wildcard address. */
    val boundAddress: InetAddress? get() = synchronized(lock) { session?.server?.inetAddress }

    override fun startSync(bindAddress: InetAddress, ciphertext: ByteArray): MirrorStartResult =
        startSync(bindAddress, ciphertext, SecureRandom())

    internal fun startSync(
        bindAddress: InetAddress,
        ciphertext: ByteArray,
        random: SecureRandom,
    ): MirrorStartResult = startSyncSession(bindAddress, ciphertext) {
        val tokenBytes = ByteArray(MirrorLimits.TOKEN_BYTES).also(random::nextBytes)
        try {
            tokenBytes.toLowerHex()
        } finally {
            tokenBytes.fill(0)
        }
    }

    override fun startSyncWithToken(bindAddress: InetAddress, ciphertext: ByteArray, tokenValue: String): MirrorStartResult {
        // Checked before anything is bound so a bad value can never leave a half-open server behind.
        require(tokenValue.length == SESSION_TOKEN_CHARS && tokenValue.all { it in HEX_DIGITS }) { "Invalid session token" }
        return startSyncSession(bindAddress, ciphertext) { tokenValue }
    }

    private fun startSyncSession(
        bindAddress: InetAddress,
        ciphertext: ByteArray,
        newTokenValue: () -> String,
    ): MirrorStartResult = synchronized(lock) {
        if (session != null) return MirrorStartResult.Failed(MirrorFailure.ALREADY_RUNNING)
        require(ciphertext.size in SyncEnvelopeCrypto.OVERHEAD_BYTES..SyncLimits.MAX_ENVELOPE_BYTES)
        val server = try {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(bindAddress, 0), ACCEPT_BACKLOG)
            }
        } catch (e: IOException) {
            return MirrorStartResult.Failed(MirrorFailure.BIND)
        } catch (e: SecurityException) {
            return MirrorStartResult.Failed(MirrorFailure.BIND)
        }
        val token = MirrorToken(newTokenValue(), clock() + SyncLimits.SESSION_TTL_MILLIS)
        val created = Session(server, token, ciphertext.copyOf())
        session = created
        created.begin()
        MirrorStartResult.Started(server.localPort, token)
    }

    override fun start(bindAddress: InetAddress): MirrorStartResult = synchronized(lock) {
        if (session != null) return MirrorStartResult.Failed(MirrorFailure.ALREADY_RUNNING)
        val server = try {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(bindAddress, 0), ACCEPT_BACKLOG)
            }
        } catch (e: IOException) {
            return MirrorStartResult.Failed(MirrorFailure.BIND)
        } catch (e: SecurityException) {
            return MirrorStartResult.Failed(MirrorFailure.BIND)
        }
        val token = MirrorToken.generate(clock())
        val created = Session(server, token)
        session = created
        created.begin()
        MirrorStartResult.Started(server.localPort, token)
    }

    override fun stop() {
        val ending = synchronized(lock) { session.also { session = null } } ?: return
        ending.shutdown()
        _clients.value = 0
    }

    override fun publish(snapshot: MirrorSnapshot) {
        val current = synchronized(lock) { session } ?: return
        current.publish(snapshot)
    }

    private inner class Session(
        val server: ServerSocket,
        val token: MirrorToken,
        syncCiphertext: ByteArray? = null,
    ) {
        private val closed = AtomicBoolean(false)
        private val limiter = MirrorRateLimiter(clock)
        private val syncOnly = syncCiphertext != null
        @Volatile private var ciphertext: ByteArray? = syncCiphertext
        private val router = MirrorRouter({ token }, { latest })
        private val syncRouter = SyncSessionRouter({ token }, { ciphertext?.copyOf() })
        private val pool: ExecutorService = Executors.newFixedThreadPool(MirrorLimits.WORKER_THREADS) { runnable ->
            Thread(runnable, "mirror-worker").apply { isDaemon = true }
        }
        private val open = HashSet<Socket>()
        private val streams = HashSet<Stream>()

        @Volatile private var latest: MirrorSnapshot? = null

        fun begin() {
            Thread({ acceptLoop() }, "mirror-accept").apply { isDaemon = true }.start()
        }

        private fun acceptLoop() {
            while (!closed.get()) {
                val socket = try {
                    server.accept()
                } catch (e: IOException) {
                    return
                }
                try {
                    pool.execute { serve(socket) }
                } catch (e: RejectedExecutionException) {
                    MirrorHttp.closeQuietly(socket)
                }
            }
        }

        private fun serve(socket: Socket) {
            track(socket)
            try {
                socket.soTimeout = socketTimeoutMillis
                handle(socket)
            } catch (e: Throwable) {
                // A bad client must never take a worker down; the socket is closed below.
            } finally {
                untrack(socket)
                MirrorHttp.closeQuietly(socket)
            }
        }

        private fun handle(socket: Socket) {
            val out = socket.getOutputStream()
            val head = when (val read = MirrorHttp.readHead(socket.getInputStream())) {
                is MirrorHttp.Head.Text -> read.value
                MirrorHttp.Head.TooLarge -> {
                    plain(out, 431)
                    // Unread request bytes would turn the close into a TCP reset that destroys the reply, so drain a bounded amount.
                    MirrorHttp.drain(socket)
                    return
                }
                MirrorHttp.Head.Incomplete -> return
            }
            val ip = socket.inetAddress?.hostAddress.orEmpty()
            if (!limiter.tryRequest(ip)) return plain(out, 429, mapOf("Retry-After" to "60"))
            val request = when (val parsed = MirrorRequestParser.parse(head)) {
                is MirrorParseResult.Ok -> parsed.request
                is MirrorParseResult.Rejected -> return plain(out, parsed.status)
            }
            if (syncOnly) {
                when (val response = syncRouter.route(request, clock())) {
                    SyncSessionResponse.NotFound -> plain(out, 404)
                    is SyncSessionResponse.Ciphertext ->
                        MirrorHttp.reply(out, 200, "application/octet-stream", response.bytes)
                }
                return
            }
            when (val response = router.route(request, clock())) {
                MirrorResponse.NotFound -> plain(out, 404)
                is MirrorResponse.Page -> MirrorHttp.reply(out, 200, "text/html; charset=utf-8", response.html, isPage = true)
                is MirrorResponse.State -> MirrorHttp.reply(out, 200, "application/json; charset=utf-8", response.json)
                MirrorResponse.Events -> stream(socket, out)
            }
        }

        private fun plain(out: java.io.OutputStream, status: Int, headers: Map<String, String> = emptyMap()) {
            val body = if (status == 404) MirrorResponse.NOT_FOUND_BODY else "Error $status"
            MirrorHttp.reply(out, status, "text/plain; charset=utf-8", body, headers)
        }

        private fun stream(socket: Socket, out: java.io.OutputStream) {
            if (!limiter.tryOpenStream()) return plain(out, 503)
            val stream = Stream(out)
            synchronized(streams) { streams += stream }
            _clients.value = streams.size
            try {
                socket.soTimeout = 0
                MirrorHttp.openEventStream(out)
                latest?.let { stream.send("article", it.toJson()) }
                while (!closed.get() && stream.alive) {
                    if (clock() >= token.expiresAtMillis) {
                        stream.send("ended", "{}")
                        break
                    }
                    stream.waitForNext(MirrorLimits.KEEPALIVE_MILLIS)
                    if (stream.alive && !closed.get()) stream.flush()
                }
            } finally {
                synchronized(streams) { streams -= stream }
                limiter.closeStream()
                _clients.value = synchronized(streams) { streams.size }
            }
        }

        fun publish(snapshot: MirrorSnapshot) {
            latest = snapshot
            val targets = synchronized(streams) { streams.toList() }
            targets.forEach { it.queue(snapshot.toJson()) }
        }

        fun shutdown() {
            if (!closed.compareAndSet(false, true)) return
            runCatching { server.close() }
            synchronized(streams) { streams.forEach { it.kill() }; streams.clear() }
            synchronized(open) { open.toList().forEach(MirrorHttp::closeQuietly); open.clear() }
            pool.shutdownNow()
            ciphertext?.fill(0)
            ciphertext = null
        }

        private fun track(socket: Socket) {
            synchronized(open) { if (closed.get()) MirrorHttp.closeQuietly(socket) else open += socket }
        }

        private fun untrack(socket: Socket) {
            synchronized(open) { open -= socket }
        }
    }

    /** One open browser: a mailbox for the newest article plus the keep-alive timer, all on the worker's thread. */
    private class Stream(private val out: java.io.OutputStream) {
        private val monitor = Object()
        private var pending: String? = null

        @Volatile var alive = true

        fun send(name: String, data: String) {
            try {
                MirrorHttp.event(out, name, data)
            } catch (e: IOException) {
                alive = false
            }
        }

        fun queue(json: String) = synchronized(monitor) { pending = json; monitor.notifyAll() }

        fun kill() = synchronized(monitor) { alive = false; monitor.notifyAll() }

        /** Sleeps until a new article arrives, the stream is killed or [timeoutMillis] passes. */
        fun waitForNext(timeoutMillis: Long) {
            val json = synchronized(monitor) {
                if (pending == null && alive) monitor.wait(timeoutMillis)
                pending.also { pending = null }
            }
            if (json != null) send("article", json)
        }

        fun flush() {
            try {
                MirrorHttp.keepAlive(out)
            } catch (e: IOException) {
                alive = false
            }
        }
    }

    private companion object {
        const val ACCEPT_BACKLOG = 50
        const val SESSION_TOKEN_CHARS = 32
        private const val HEX_DIGITS = "0123456789abcdef"

        fun ByteArray.toLowerHex(): String {
            val out = StringBuilder(size * 2)
            for (byte in this) {
                val value = byte.toInt() and 0xff
                out.append(HEX_DIGITS[value ushr 4]).append(HEX_DIGITS[value and 0x0f])
            }
            return out.toString()
        }
    }
}
