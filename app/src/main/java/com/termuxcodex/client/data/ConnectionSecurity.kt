package com.termuxcodex.client.data

import java.net.URI

object ConnectionSecurity {
    data class Validation(val endpoint: String, val error: String? = null)

    fun validate(endpoint: String, token: String): Validation {
        val normalized = endpoint.trim()
        if ('\r' in token || '\n' in token) {
            return Validation(normalized, "传输 Token 不能包含换行符")
        }
        val uri = try {
            URI(normalized)
        } catch (_: Throwable) {
            return Validation(normalized, "App Server 地址格式无效")
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "ws" && scheme != "wss") {
            return Validation(normalized, "地址必须以 ws:// 或 wss:// 开头")
        }
        val host = uri.host ?: return Validation(normalized, "App Server 地址缺少主机名")
        val loopback = isLoopbackHost(host)
        if (scheme == "ws" && !loopback) {
            return Validation(normalized, "非本机连接必须使用 wss://，禁止明文传输")
        }
        return Validation(normalized)
    }

    fun isLoopbackHost(host: String): Boolean {
        if (host.equals("localhost", ignoreCase = true)) return true
        val normalized = host.removePrefix("[").removeSuffix("]").lowercase()
        return normalized == "::1" || normalized.startsWith("127.")
    }
}
