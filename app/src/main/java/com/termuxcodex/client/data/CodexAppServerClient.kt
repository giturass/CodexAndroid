package com.termuxcodex.client.data

import android.os.Handler
import android.os.Looper
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.termuxcodex.client.BuildConfig
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min
import kotlin.random.Random

class CodexAppServerClient(private val listener: Listener) {
    interface Listener {
        fun onReady(serverInfo: JsonObject)
        fun onDisconnected(reason: String)
        fun onNotification(method: String, params: JsonObject)
        fun onServerRequest(request: ServerRequest)
        fun onProtocolError(message: String)
    }

    data class ServerRequest(
        val id: JsonElement,
        val method: String,
        val params: JsonObject,
    )

    data class RpcResult(
        val result: JsonObject? = null,
        val error: JsonObject? = null,
    )

    private data class PendingRpc(
        val method: String,
        val params: JsonObject,
        val callback: (RpcResult) -> Unit,
        val attempt: Int,
        val timeoutMs: Long,
        val retryOnOverload: Boolean,
        val connectionGeneration: Long,
        val timeout: Runnable,
    )

    private class ScheduledRetry(
        val callback: (RpcResult) -> Unit,
        val runnable: Runnable,
    )

    private val gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val connectionExecutor = Executors.newCachedThreadPool()
    private val writerExecutor = Executors.newSingleThreadExecutor()
    private val nextId = AtomicLong(1)
    private val connectionGeneration = AtomicLong(0)
    private val pending = ConcurrentHashMap<String, PendingRpc>()
    private val scheduledRetries = mutableSetOf<ScheduledRetry>()

    @Volatile
    private var webSocket: AppServerWebSocket? = null

    @Volatile
    private var ready = false

    @Volatile
    private var closed = false

    fun connect(endpoint: String, token: String?) {
        if (closed) return
        disconnectInternal("reconnect", notify = false)
        ready = false

        lateinit var candidate: AppServerWebSocket
        candidate = AppServerWebSocket(endpoint.trim(), token, object : AppServerWebSocket.Listener {
            override fun onOpen() {
                if (webSocket !== candidate) return
                val params = JsonObject().apply {
                    add("clientInfo", JsonObject().apply {
                        addProperty("name", "termux_codex_android")
                        addProperty("title", "Codex Android")
                        addProperty("version", BuildConfig.VERSION_NAME)
                    })
                    add("capabilities", JsonObject().apply {
                        addProperty("experimentalApi", false)
                        addProperty("mcpServerOpenaiFormElicitation", true)
                        addProperty("requestAttestation", false)
                    })
                }
                requestInternal(
                    CodexProtocol.ClientRequest.INITIALIZE,
                    params,
                    attempt = 0,
                    timeoutMs = INITIALIZE_TIMEOUT_MS,
                    retryOnOverload = true,
                ) { response ->
                    if (webSocket !== candidate) return@requestInternal
                    if (response.error != null) {
                        failConnection(candidate, "初始化失败：${errorMessage(response.error)}")
                        return@requestInternal
                    }
                    notification("initialized", JsonObject())
                    ready = true
                    listener.onReady(response.result ?: JsonObject())
                }
            }

            override fun onText(text: String) {
                if (webSocket !== candidate) return
                try {
                    val message = JsonParser.parseString(text).asJsonObject
                    mainHandler.post {
                        if (webSocket === candidate) {
                            try {
                                handleMessage(message)
                            } catch (error: Throwable) {
                                listener.onProtocolError(
                                    "处理服务端消息失败：${error.message ?: error.javaClass.simpleName}"
                                )
                            }
                        }
                    }
                } catch (error: Throwable) {
                    mainHandler.post {
                        if (webSocket === candidate) {
                            listener.onProtocolError("无法解析服务端消息：${error.message}")
                        }
                    }
                }
            }

            override fun onClosed(reason: String) {
                mainHandler.post {
                    if (webSocket === candidate) handleTransportClosed(reason)
                }
            }

            override fun onFailure(error: Throwable) {
                mainHandler.post {
                    if (webSocket === candidate) {
                        handleTransportClosed(error.message ?: error.javaClass.simpleName)
                    }
                }
            }
        })
        webSocket = candidate
        connectionExecutor.execute(candidate::run)
    }

    fun disconnect(reason: String = "client closed") {
        disconnectInternal(reason, notify = false)
    }

    fun shutdown() {
        if (closed) return
        closed = true
        val previous = webSocket
        webSocket = null
        ready = false
        failAllPending("客户端已关闭")
        previous?.cancel()
        connectionExecutor.shutdownNow()
        writerExecutor.shutdownNow()
        mainHandler.removeCallbacksAndMessages(null)
    }

    fun isReady(): Boolean = ready

    fun request(
        method: String,
        params: JsonObject = JsonObject(),
        timeoutMs: Long = REQUEST_TIMEOUT_MS,
        retryOnOverload: Boolean = false,
        callback: (RpcResult) -> Unit = {},
    ): Long = requestInternal(
        method,
        params.deepCopy(),
        attempt = 0,
        timeoutMs,
        retryOnOverload,
        callback,
    )

    fun notification(method: String, params: JsonObject = JsonObject()) {
        send(JsonObject().apply {
            addProperty("method", method)
            add("params", params)
        })
    }

    fun respond(id: JsonElement, result: JsonObject) {
        send(JsonObject().apply {
            add("id", id.deepCopy())
            add("result", result)
        })
    }

    fun respondError(id: JsonElement, code: Int, message: String) {
        send(JsonObject().apply {
            add("id", id.deepCopy())
            add("error", rpcError(code, message))
        })
    }

    private fun requestInternal(
        method: String,
        params: JsonObject,
        attempt: Int,
        timeoutMs: Long,
        retryOnOverload: Boolean,
        callback: (RpcResult) -> Unit,
    ): Long {
        val id = nextId.getAndIncrement()
        val generation = connectionGeneration.get()
        val timeout = Runnable {
            val removed = pending.remove(id.toString()) ?: return@Runnable
            removed.callback(RpcResult(error = rpcError(-32098, "请求超时：${removed.method}")))
        }
        pending[id.toString()] = PendingRpc(
            method,
            params.deepCopy(),
            callback,
            attempt,
            timeoutMs,
            retryOnOverload,
            generation,
            timeout,
        )
        mainHandler.postDelayed(timeout, timeoutMs)
        val accepted = send(JsonObject().apply {
            addProperty("method", method)
            addProperty("id", id)
            add("params", params)
        })
        if (!accepted) {
            mainHandler.removeCallbacks(timeout)
            pending.remove(id.toString())
            mainHandler.post {
                callback(RpcResult(error = rpcError(-32097, "尚未连接 Codex App Server")))
            }
        }
        return id
    }

    private fun send(message: JsonObject): Boolean {
        val target = webSocket ?: return false
        val json = gson.toJson(message)
        return try {
            writerExecutor.execute {
                try {
                    if (webSocket === target) target.sendText(json)
                } catch (error: Throwable) {
                    mainHandler.post {
                        if (webSocket === target) failConnection(target, "发送消息失败：${error.message}")
                    }
                }
            }
            true
        } catch (_: RejectedExecutionException) {
            false
        }
    }

    private fun handleMessage(message: JsonObject) {
        val id = message.get("id")
        val method = message.get("method")?.takeUnless { it.isJsonNull }?.asString

        if (id != null && method == null) {
            val request = pending.remove(id.toString()) ?: return
            mainHandler.removeCallbacks(request.timeout)
            val error = message.objectOrNull("error")
            if (error?.get("code")?.asInt == SERVER_OVERLOADED && request.retryOnOverload &&
                request.attempt < MAX_RETRIES
            ) {
                val delay = retryDelay(request.attempt)
                lateinit var scheduled: ScheduledRetry
                val runnable = Runnable {
                    scheduledRetries.remove(scheduled)
                    if (webSocket != null &&
                        connectionGeneration.get() == request.connectionGeneration
                    ) {
                        requestInternal(
                            request.method,
                            request.params,
                            request.attempt + 1,
                            request.timeoutMs,
                            request.retryOnOverload,
                            request.callback,
                        )
                    } else {
                        request.callback(RpcResult(error = rpcError(-32097, "连接已断开")))
                    }
                }
                scheduled = ScheduledRetry(request.callback, runnable)
                scheduledRetries += scheduled
                mainHandler.postDelayed(runnable, delay)
                return
            }
            request.callback(
                RpcResult(
                    result = message.objectOrNull("result"),
                    error = error,
                )
            )
            return
        }

        val params = message.objectOrNull("params") ?: JsonObject()
        if (id != null && method != null) {
            listener.onServerRequest(ServerRequest(id.deepCopy(), method, params))
        } else if (method != null) {
            listener.onNotification(method, params)
        }
    }

    private fun failConnection(candidate: AppServerWebSocket, reason: String) {
        if (webSocket !== candidate) return
        webSocket = null
        ready = false
        candidate.cancel()
        failAllPending(reason)
        listener.onDisconnected(reason)
    }

    private fun handleTransportClosed(reason: String) {
        webSocket = null
        ready = false
        failAllPending(reason)
        listener.onDisconnected(reason)
    }

    private fun disconnectInternal(reason: String, notify: Boolean) {
        connectionGeneration.incrementAndGet()
        val previous = webSocket
        webSocket = null
        ready = false
        failAllPending(reason)
        previous?.close(reason)
        if (notify) mainHandler.post { listener.onDisconnected(reason) }
    }

    private fun failAllPending(reason: String) {
        val requests = pending.values.toList()
        pending.clear()
        requests.forEach {
            mainHandler.removeCallbacks(it.timeout)
            mainHandler.post { it.callback(RpcResult(error = rpcError(-32097, reason))) }
        }
        val retries = scheduledRetries.toList()
        scheduledRetries.clear()
        retries.forEach {
            mainHandler.removeCallbacks(it.runnable)
            mainHandler.post { it.callback(RpcResult(error = rpcError(-32097, reason))) }
        }
    }

    private fun retryDelay(attempt: Int): Long {
        val base = min(4_000L, 250L shl attempt)
        return base + Random.nextLong(0L, base / 2 + 1)
    }

    private fun errorMessage(error: JsonObject): String {
        val code = error.get("code")?.asInt
        val message = error.get("message")?.asString ?: "未知协议错误"
        return if (code != null) "$message ($code)" else message
    }

    private fun rpcError(code: Int, message: String) = JsonObject().apply {
        addProperty("code", code)
        addProperty("message", message)
    }

    private fun JsonObject.objectOrNull(name: String): JsonObject? {
        val value = get(name) ?: return null
        return if (value.isJsonObject) value.asJsonObject else null
    }

    private companion object {
        const val SERVER_OVERLOADED = -32001
        const val MAX_RETRIES = 4
        const val INITIALIZE_TIMEOUT_MS = 15_000L
        const val REQUEST_TIMEOUT_MS = 30_000L
    }
}
