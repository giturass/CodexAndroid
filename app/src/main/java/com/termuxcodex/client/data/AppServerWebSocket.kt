package com.termuxcodex.client.data

import com.termuxcodex.client.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class AppServerWebSocket(
    private val endpoint: String,
    private val bearerToken: String?,
    private val listener: Listener,
) {
    interface Listener {
        fun onOpen()
        fun onText(text: String)
        fun onClosed(reason: String)
        fun onFailure(error: Throwable)
    }

    private val terminalCallbackSent = AtomicBoolean(false)

    @Volatile
    private var webSocket: WebSocket? = null

    fun run() {
        try {
            require(bearerToken?.contains('\r') != true && bearerToken?.contains('\n') != true) {
                "传输 Token 不能包含换行符"
            }
            val request = Request.Builder()
                .url(endpoint)
                .header("User-Agent", "CodexAndroid/${BuildConfig.VERSION_NAME}")
                .apply {
                    bearerToken?.trim()?.takeIf { it.isNotEmpty() }?.let {
                        header("Authorization", "Bearer $it")
                    }
                }
                .build()
            webSocket = sharedClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    listener.onOpen()
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    listener.onText(text)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(code, reason)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (terminalCallbackSent.compareAndSet(false, true)) {
                        listener.onClosed(reason.ifBlank { "server closed ($code)" })
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (terminalCallbackSent.compareAndSet(false, true)) {
                        val suffix = response?.let { " HTTP ${it.code}" }.orEmpty()
                        listener.onFailure(IllegalStateException("${t.message.orEmpty()}$suffix", t))
                    }
                }
            })
        } catch (error: Throwable) {
            if (terminalCallbackSent.compareAndSet(false, true)) listener.onFailure(error)
        }
    }

    fun sendText(text: String) {
        check(webSocket?.send(text) == true) { "WebSocket 已关闭或发送队列已满" }
    }

    fun close(reason: String = "client closed") {
        val socket = webSocket ?: return
        if (!socket.close(1000, reason.take(120))) socket.cancel()
    }

    fun cancel() {
        webSocket?.cancel()
    }

    companion object {
        private val sharedClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
