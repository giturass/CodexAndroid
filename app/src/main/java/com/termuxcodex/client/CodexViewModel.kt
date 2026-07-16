@file:android.annotation.SuppressLint("SdCardPath")

package com.termuxcodex.client

import android.app.Application
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.edit
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.termuxcodex.client.data.CodexAppServerClient
import com.termuxcodex.client.data.ConnectionSecurity
import com.termuxcodex.client.data.SecretStore
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ConnectionStatus { DISCONNECTED, CONNECTING, CONNECTED }

const val DEFAULT_TERMUX_HOME = "/data/data/com.termux/files/home"

enum class MessageKind { USER, ASSISTANT, TOOL, INFO, ERROR }

enum class MessageOutcome { NEUTRAL, SUCCESS, FAILED, DECLINED }

@Immutable
data class UiMessage(
    val id: String,
    val kind: MessageKind,
    val text: String,
    val title: String? = null,
    val running: Boolean = false,
    val outcome: MessageOutcome = MessageOutcome.NEUTRAL,
)

data class ThreadSummary(
    val id: String,
    val title: String,
    val cwd: String,
    val updatedAt: Long,
)

data class CodexModel(
    val id: String,
    val model: String,
    val displayName: String,
    val description: String,
    val isDefault: Boolean,
    val defaultReasoningEffort: String,
    val supportedReasoningEfforts: List<ReasoningEffortOption>,
)

data class ReasoningEffortOption(
    val value: String,
    val description: String,
)

data class RemoteDirectory(
    val name: String,
    val path: String,
)

data class CodexSkill(
    val name: String,
    val displayName: String,
    val path: String,
    val description: String,
    val scope: String,
)

enum class PendingKind { COMMAND, FILE_CHANGE, PERMISSION, USER_INPUT, MCP_ELICITATION }

data class InputQuestion(
    val id: String,
    val header: String,
    val prompt: String,
    val options: List<String>,
    val secret: Boolean,
    val valueType: String = "string",
    val required: Boolean = false,
)

data class PendingDecision(
    val key: String,
    val label: String,
    val payload: JsonElement,
)

data class PendingAction(
    val requestId: JsonElement,
    val kind: PendingKind,
    val title: String,
    val detail: String,
    val rawParams: JsonObject,
    val questions: List<InputQuestion> = emptyList(),
    val threadId: String? = null,
    val decisions: List<PendingDecision> = emptyList(),
    val autoResolutionMs: Long? = null,
)

data class AppUiState(
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val connectionMessage: String = "尚未连接",
    val endpoint: String = "ws://127.0.0.1:4500",
    val token: String = "",
    val cwd: String = DEFAULT_TERMUX_HOME,
    val model: String = "",
    val reasoningEffort: String = "",
    val configModel: String = "",
    val configReasoningEffort: String = "",
    val selectedSkillPaths: Set<String> = emptySet(),
    val availableModels: List<CodexModel> = emptyList(),
    val modelsLoading: Boolean = false,
    val modelsError: String? = null,
    val availableSkills: List<CodexSkill> = emptyList(),
    val skillsCwd: String = "",
    val skillsLoading: Boolean = false,
    val skillsError: String? = null,
    val messages: List<UiMessage> = emptyList(),
    val threads: List<ThreadSummary> = emptyList(),
    val currentThreadId: String? = null,
    val currentThreadTitle: String = "新会话",
    val activeTurnId: String? = null,
    val busy: Boolean = false,
    val pendingActions: List<PendingAction> = emptyList(),
    val historyNextCursor: String? = null,
    val historyLoading: Boolean = false,
) {
    val pendingAction: PendingAction?
        get() = pendingActions.firstOrNull()
}

class CodexViewModel(application: Application) : AndroidViewModel(application),
    CodexAppServerClient.Listener {

    private val preferences = application.getSharedPreferences("connection", 0)
    private val secretStore = SecretStore(application)
    private val prettyGson = GsonBuilder().setPrettyPrinting().create()
    private val client = CodexAppServerClient(this)
    private val notifier = ApprovalNotifier(application)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingPrompt: String? = null
    private var appInForeground = true
    private var subscribedThreadId: String? = null
    private var manualDisconnect = false
    private var reconnectAttempt = 0
    private var resumeGeneration = 0
    private var pendingNotificationThreadId: String? = null
    private val pendingDeltas = linkedMapOf<String, BufferedDelta>()
    private var deltaFlushScheduled = false

    private data class BufferedDelta(
        val kind: MessageKind,
        val title: String?,
        val text: StringBuilder = StringBuilder(),
        var truncated: Boolean = false,
    )

    var uiState by mutableStateOf(
        AppUiState(
            endpoint = preferences.getString("endpoint", null)
                ?: "ws://127.0.0.1:4500",
            token = loadAndMigrateToken(),
            cwd = preferences.getString("cwd", null)
                ?: DEFAULT_TERMUX_HOME,
            currentThreadId = preferences.getString("current_thread_id", null),
            currentThreadTitle = preferences.getString("current_thread_title", null)
                ?: "新会话",
            model = preferences.getString("model", null) ?: "",
            reasoningEffort = preferences.getString("reasoning_effort", null) ?: "",
            selectedSkillPaths = preferences.getStringSet("selected_skills", emptySet())
                .orEmpty()
                .toSet(),
        )
    )
        private set

    init {
        connect()
    }

    fun updateSettings(
        endpoint: String,
        token: String,
        cwd: String,
        model: String,
        reasoningEffort: String,
        selectedSkillPaths: Set<String>,
    ) {
        val normalizedEndpoint = endpoint.trim()
        val normalizedCwd = cwd.trim()
        val normalizedModel = model.trim()
        val normalizedReasoningEffort = reasoningEffort.trim()
        val cwdChanged = normalizedCwd != uiState.cwd
        val endpointChanged = normalizedEndpoint != uiState.endpoint
        val tokenChanged = token.trim() != uiState.token
        val sessionChanged = cwdChanged || endpointChanged || tokenChanged
        preferences.edit {
            putString("endpoint", normalizedEndpoint)
            putString("cwd", normalizedCwd)
            putString("model", normalizedModel)
            putString("reasoning_effort", normalizedReasoningEffort)
            putStringSet("selected_skills", selectedSkillPaths)
        }
        secretStore.setTransportToken(token.trim())
        preferences.edit { remove("token") }
        if (sessionChanged && uiState.currentThreadId != null && !uiState.busy) {
            unsubscribe(uiState.currentThreadId)
            subscribedThreadId = null
        }
        if (sessionChanged && !uiState.busy) {
            preferences.edit {
                remove("current_thread_id")
                remove("current_thread_title")
            }
        }
        uiState = uiState.copy(
            endpoint = normalizedEndpoint,
            token = token.trim(),
            cwd = normalizedCwd,
            model = normalizedModel,
            reasoningEffort = normalizedReasoningEffort,
            selectedSkillPaths = selectedSkillPaths.toSet(),
            currentThreadId = if (sessionChanged && !uiState.busy) null else uiState.currentThreadId,
            currentThreadTitle = if (sessionChanged && !uiState.busy) "新会话" else uiState.currentThreadTitle,
            messages = if (sessionChanged && !uiState.busy) emptyList() else uiState.messages,
            historyNextCursor = if (sessionChanged && !uiState.busy) null else uiState.historyNextCursor,
        )
    }

    fun setAppInForeground(foreground: Boolean) {
        appInForeground = foreground
        if (foreground) {
            notifier.cancelPendingAction()
        } else {
            uiState.pendingAction?.let(notifier::showPendingAction)
        }
    }

    fun handleNotificationIntent(intent: Intent?) {
        if (intent?.action != ApprovalNotificationIntent.ACTION_OPEN_PENDING) return
        val threadId = intent.getStringExtra(ApprovalNotificationIntent.EXTRA_THREAD_ID)
            ?.takeIf { it.isNotBlank() }
            ?: return
        pendingNotificationThreadId = threadId
        if (client.isReady() && !uiState.busy) {
            pendingNotificationThreadId = null
            if (uiState.currentThreadId != threadId) openThread(threadId)
        }
    }

    fun connect() {
        manualDisconnect = false
        reconnectAttempt = 0
        connectNow()
    }

    private fun connectNow() {
        val validation = ConnectionSecurity.validate(uiState.endpoint, uiState.token)
        if (validation.error != null) {
            uiState = uiState.copy(
                connectionStatus = ConnectionStatus.DISCONNECTED,
                connectionMessage = validation.error,
            )
            return
        }
        uiState = uiState.copy(
            connectionStatus = ConnectionStatus.CONNECTING,
            connectionMessage = "正在连接 ${uiState.endpoint}",
            availableModels = emptyList(),
            modelsLoading = false,
            modelsError = null,
            configModel = "",
            configReasoningEffort = "",
            availableSkills = emptyList(),
            skillsCwd = "",
            skillsLoading = false,
            skillsError = null,
        )
        client.connect(validation.endpoint, uiState.token.ifBlank { null })
    }

    fun disconnect() {
        manualDisconnect = true
        mainHandler.removeCallbacksAndMessages(RECONNECT_TOKEN)
        client.disconnect()
        CodexConnectionService.stop(getApplication())
        uiState = uiState.copy(
            connectionStatus = ConnectionStatus.DISCONNECTED,
            connectionMessage = "已断开连接",
            busy = false,
            activeTurnId = null,
        )
    }

    fun newThread() {
        if (uiState.busy) return
        unsubscribe(uiState.currentThreadId)
        subscribedThreadId = null
        preferences.edit {
            remove("current_thread_id")
            remove("current_thread_title")
        }
        uiState = uiState.copy(
            messages = emptyList(),
            currentThreadId = null,
            currentThreadTitle = "新会话",
            activeTurnId = null,
            pendingActions = emptyList(),
            historyNextCursor = null,
        )
    }

    fun deleteThread(threadId: String) {
        if (threadId.isBlank()) return
        if (!client.isReady()) {
            addInfo("请先连接 Codex App Server。", error = true)
            return
        }
        if (threadId == uiState.currentThreadId && uiState.busy) {
            addInfo("当前会话正在执行任务，请先停止任务后再删除。", error = true)
            return
        }
        client.request("thread/delete", JsonObject().apply {
            addProperty("threadId", threadId)
        }) { response ->
            response.error?.let {
                addInfo("删除会话失败：${rpcError(it)}", error = true)
                return@request
            }
            removeDeletedThread(threadId)
        }
    }

    private fun removeDeletedThread(threadId: String) {
        if (uiState.currentThreadId == threadId) {
            resumeGeneration++
            subscribedThreadId = null
            CodexConnectionService.stop(getApplication())
            notifier.cancelPendingAction()
            preferences.edit {
                remove("current_thread_id")
                remove("current_thread_title")
            }
            uiState = uiState.copy(
                threads = uiState.threads.filterNot { it.id == threadId },
                messages = emptyList(),
                currentThreadId = null,
                currentThreadTitle = "新会话",
                activeTurnId = null,
                busy = false,
                pendingActions = emptyList(),
                historyNextCursor = null,
                historyLoading = false,
            )
        } else {
            uiState = uiState.copy(threads = uiState.threads.filterNot { it.id == threadId })
        }
    }

    fun refreshThreads() {
        if (!client.isReady()) return
        loadThreadsPage(cursor = null, accumulated = emptyList())
    }

    private fun loadThreadsPage(cursor: String?, accumulated: List<ThreadSummary>) {
        client.request("thread/list", JsonObject().apply {
            addProperty("limit", 50)
            addProperty("sortKey", "updated_at")
            addProperty("sortDirection", "desc")
            cursor?.let { addProperty("cursor", it) }
        }) { response ->
            response.error?.let {
                addInfo("读取会话失败：${rpcError(it)}", error = true)
                return@request
            }
            val data = response.result?.getAsJsonArray("data") ?: JsonArray()
            val page = data.mapNotNull { element ->
                val thread = element.asJsonObject
                val id = thread.string("id") ?: return@mapNotNull null
                val preview = thread.string("name")
                    ?: thread.string("preview")
                    ?: "未命名会话"
                ThreadSummary(
                    id = id,
                    title = preview.lineSequence().firstOrNull()?.take(56) ?: "未命名会话",
                    cwd = thread.string("cwd") ?: "",
                    updatedAt = thread.long("updatedAt") ?: thread.long("createdAt") ?: 0L,
                )
            }
            val threads = (accumulated + page).distinctBy { it.id }
            val nextCursor = response.result?.string("nextCursor")
            if (nextCursor != null && threads.size < MAX_THREAD_LIST_SIZE) {
                loadThreadsPage(nextCursor, threads)
            } else {
                uiState = uiState.copy(threads = threads)
            }
        }
    }

    fun refreshModels() {
        if (!client.isReady()) {
            uiState = uiState.copy(
                modelsLoading = false,
                modelsError = "连接 App Server 后才能读取模型列表",
            )
            return
        }
        if (uiState.modelsLoading) return
        uiState = uiState.copy(modelsLoading = true, modelsError = null)
        loadModelsPage(cursor = null, accumulated = emptyList())
    }

    private fun refreshConfigDefaults() {
        if (!client.isReady()) return
        client.request("config/read", JsonObject().apply {
            addProperty("cwd", uiState.cwd)
            addProperty("includeLayers", false)
        }) { response ->
            if (response.error != null) return@request
            val config = response.result?.getAsJsonObject("config") ?: return@request
            uiState = uiState.copy(
                configModel = config.string("model") ?: "",
                configReasoningEffort = config.string("model_reasoning_effort") ?: "",
            )
        }
    }

    fun refreshSkills(cwd: String = uiState.cwd, forceReload: Boolean = false) {
        val normalizedCwd = cwd.trim()
        if (!client.isReady()) {
            uiState = uiState.copy(
                skillsLoading = false,
                skillsError = "连接 App Server 后才能读取 Skills",
            )
            return
        }
        if (normalizedCwd.isBlank() || uiState.skillsLoading) return
        uiState = uiState.copy(skillsLoading = true, skillsError = null)
        client.request("skills/list", JsonObject().apply {
            add("cwds", JsonArray().apply { add(normalizedCwd) })
            addProperty("forceReload", forceReload)
        }) { response ->
            response.error?.let {
                uiState = uiState.copy(
                    skillsLoading = false,
                    skillsError = "读取 Skills 失败：${rpcError(it)}",
                )
                return@request
            }
            val skills = response.result?.getAsJsonArray("data")
                ?.flatMap { entryElement ->
                    entryElement.asJsonObject.getAsJsonArray("skills")?.mapNotNull { skillElement ->
                        val skill = skillElement.asJsonObject
                        if (skill.bool("enabled") != true) return@mapNotNull null
                        val name = skill.string("name") ?: return@mapNotNull null
                        val path = skill.string("path") ?: return@mapNotNull null
                        val skillInterface = skill.get("interface")
                            ?.takeUnless { it.isJsonNull }
                            ?.asJsonObject
                        CodexSkill(
                            name = name,
                            displayName = skillInterface?.string("displayName") ?: name,
                            path = path,
                            description = skillInterface?.string("shortDescription")
                                ?: skill.string("shortDescription")
                                ?: skill.string("description")
                                ?: "",
                            scope = skill.string("scope") ?: "",
                        )
                    }.orEmpty()
                }
                .orEmpty()
                .distinctBy { it.path }
            val availablePaths = skills.mapTo(mutableSetOf()) { it.path }
            val retainedSkillPaths = uiState.selectedSkillPaths.intersect(availablePaths)
            if (retainedSkillPaths != uiState.selectedSkillPaths) {
                preferences.edit { putStringSet("selected_skills", retainedSkillPaths) }
            }
            uiState = uiState.copy(
                availableSkills = skills,
                selectedSkillPaths = retainedSkillPaths,
                skillsCwd = normalizedCwd,
                skillsLoading = false,
                skillsError = if (skills.isEmpty()) "当前工作目录没有可用 Skills" else null,
            )
        }
    }

    fun openThread(threadId: String) {
        if (!client.isReady() || uiState.busy) return
        resumeThread(threadId)
    }

    private fun resumeThread(threadId: String) {
        val generation = ++resumeGeneration
        val previousTitle = uiState.currentThreadTitle
        uiState = uiState.copy(currentThreadTitle = "正在加载…")
        client.request("thread/resume", JsonObject().apply {
            addProperty("threadId", threadId)
            addProperty("excludeTurns", true)
            add("initialTurnsPage", JsonObject().apply {
                addProperty("limit", HISTORY_PAGE_SIZE)
                addProperty("sortDirection", "desc")
                addProperty("itemsView", "full")
            })
        }) { response ->
            response.error?.let {
                if (generation != resumeGeneration) return@request
                uiState = uiState.copy(currentThreadTitle = previousTitle, historyLoading = false)
                addInfo("恢复会话失败：${rpcError(it)}", error = true)
                return@request
            }
            val result = response.result
            val thread = result?.getAsJsonObject("thread")
            if (result == null || thread == null) {
                if (generation != resumeGeneration) return@request
                uiState = uiState.copy(
                    currentThreadTitle = previousTitle,
                    historyLoading = false,
                )
                addInfo("恢复会话失败：App Server 返回了无效的会话数据。", error = true)
                return@request
            }
            val turnsPage = result.getAsJsonObject("initialTurnsPage")
            val turns = turnsPage?.getAsJsonArray("data")
                ?: thread.getAsJsonArray("turns")
                ?: JsonArray()
            val title = thread.string("name")
                ?: thread.string("preview")
                ?: "Codex 会话"
            val activeTurnId = turns
                .map { it.asJsonObject }
                .firstOrNull { it.string("status") == "inProgress" }
                ?.string("id")
            val threadActive = thread.getAsJsonObject("status")?.string("type") == "active"
            val previousThreadId = subscribedThreadId
            val resolvedThreadId = thread.string("id") ?: threadId
            val nextCursor = turnsPage?.string("nextCursor")
            viewModelScope.launch {
                val parsedMessages = withContext(Dispatchers.Default) {
                    parseTurnsMessages(turns.deepCopy(), newestFirst = true)
                }
                if (generation != resumeGeneration) return@launch
                uiState = uiState.copy(
                    currentThreadId = resolvedThreadId,
                    currentThreadTitle = title.lineSequence().firstOrNull()?.take(56) ?: "Codex 会话",
                    messages = parsedMessages,
                    activeTurnId = activeTurnId,
                    busy = activeTurnId != null || threadActive,
                    historyNextCursor = nextCursor,
                    historyLoading = false,
                )
                preferences.edit {
                    putString("current_thread_id", resolvedThreadId)
                    putString("current_thread_title", uiState.currentThreadTitle)
                }
                if (uiState.busy) {
                    CodexConnectionService.start(getApplication(), true)
                } else {
                    CodexConnectionService.stop(getApplication())
                }
                subscribedThreadId = resolvedThreadId
                if (previousThreadId != null && previousThreadId != subscribedThreadId) {
                    unsubscribe(previousThreadId)
                }
            }
        }
    }

    fun loadOlderHistory() {
        val threadId = uiState.currentThreadId ?: return
        val cursor = uiState.historyNextCursor ?: return
        if (uiState.historyLoading || !client.isReady()) return
        uiState = uiState.copy(historyLoading = true)
        client.request("thread/turns/list", JsonObject().apply {
            addProperty("threadId", threadId)
            addProperty("cursor", cursor)
            addProperty("limit", HISTORY_PAGE_SIZE)
            addProperty("sortDirection", "desc")
            addProperty("itemsView", "full")
        }) { response ->
            response.error?.let {
                uiState = uiState.copy(historyLoading = false)
                addInfo("读取更早记录失败：${rpcError(it)}", error = true)
                return@request
            }
            val turns = response.result?.getAsJsonArray("data") ?: JsonArray()
            val nextCursor = response.result?.string("nextCursor")
            viewModelScope.launch {
                val olderMessages = withContext(Dispatchers.Default) {
                    parseTurnsMessages(turns.deepCopy(), newestFirst = true)
                }
                if (uiState.currentThreadId != threadId) return@launch
                uiState = uiState.copy(
                    messages = (olderMessages + uiState.messages).distinctBy { it.id },
                    historyNextCursor = nextCursor,
                    historyLoading = false,
                )
            }
        }
    }

    fun sendPrompt(text: String) {
        val prompt = text.trim()
        if (prompt.isBlank() || uiState.busy) return
        if (!client.isReady()) {
            addInfo("请先连接 Termux 中的 Codex App Server。", error = true)
            return
        }
        if (uiState.selectedSkillPaths.isNotEmpty()) {
            val loadedSkillPaths = uiState.availableSkills.mapTo(mutableSetOf()) { it.path }
            val missingSkillPaths = uiState.selectedSkillPaths - loadedSkillPaths
            if (uiState.skillsLoading || missingSkillPaths.isNotEmpty()) {
                addInfo("正在读取所选 Skills，请稍后再发送。")
                if (!uiState.skillsLoading) refreshSkills(forceReload = true)
                return
            }
        }

        uiState = uiState.copy(
            messages = uiState.messages + UiMessage(
                id = "local-${UUID.randomUUID()}",
                kind = MessageKind.USER,
                text = prompt,
            ),
            busy = true,
        )
        CodexConnectionService.start(getApplication(), true)

        val threadId = uiState.currentThreadId
        if (threadId == null) {
            pendingPrompt = prompt
            startThread()
        } else {
            startTurn(threadId, prompt)
        }
    }

    fun interruptTurn() {
        val threadId = uiState.currentThreadId ?: return
        val turnId = uiState.activeTurnId ?: return
        client.request("turn/interrupt", JsonObject().apply {
            addProperty("threadId", threadId)
            addProperty("turnId", turnId)
        }) { response ->
            response.error?.let { addInfo("中断失败：${rpcError(it)}", error = true) }
        }
    }

    fun resolveApproval(decision: String) {
        val pending = uiState.pendingAction ?: return
        val selectedDecision = pending.decisions.firstOrNull { it.key == decision }
        when (pending.kind) {
            PendingKind.COMMAND, PendingKind.FILE_CHANGE -> {
                client.respond(pending.requestId, JsonObject().apply {
                    add("decision", selectedDecision?.payload?.deepCopy() ?: JsonPrimitive(decision))
                })
            }

            PendingKind.PERMISSION -> {
                if (decision == "decline" || decision == "cancel") {
                    client.respondError(pending.requestId, -32000, "User denied permission request")
                } else {
                    val requested = pending.rawParams.getAsJsonObject("permissions") ?: JsonObject()
                    client.respond(pending.requestId, JsonObject().apply {
                        add("permissions", requested.deepCopy())
                        addProperty("scope", if (decision == "acceptForSession") "session" else "turn")
                    })
                }
            }

            PendingKind.MCP_ELICITATION -> {
                client.respond(pending.requestId, JsonObject().apply {
                    addProperty("action", decision)
                })
            }

            PendingKind.USER_INPUT -> Unit
        }
        removePending(pending.requestId)
    }

    fun answerQuestions(answers: Map<String, String>) {
        val pending = uiState.pendingAction ?: return
        when (pending.kind) {
            PendingKind.USER_INPUT -> client.respond(pending.requestId, JsonObject().apply {
                add("answers", JsonObject().apply {
                    pending.questions.forEach { question ->
                        add(question.id, JsonObject().apply {
                            add("answers", JsonArray().apply {
                                answers[question.id]?.takeIf { it.isNotBlank() }?.let(::add)
                            })
                        })
                    }
                })
            })

            PendingKind.MCP_ELICITATION -> client.respond(pending.requestId, JsonObject().apply {
                addProperty("action", "accept")
                add("content", JsonObject().apply {
                    pending.questions.forEach { question ->
                        val value = answers[question.id].orEmpty()
                        if (value.isBlank() && !question.required) return@forEach
                        add(question.id, typedAnswer(value, question.valueType))
                    }
                })
            })

            else -> return
        }
        removePending(pending.requestId)
    }

    fun dismissPendingAction() {
        val pending = uiState.pendingAction ?: return
        when (pending.kind) {
            PendingKind.COMMAND, PendingKind.FILE_CHANGE -> client.respond(
                pending.requestId,
                JsonObject().apply {
                    val decline = pending.decisions.firstOrNull {
                        it.key in setOf("decline", "denied", "cancel", "abort")
                    }?.payload ?: JsonPrimitive("decline")
                    add("decision", decline.deepCopy())
                },
            )

            PendingKind.USER_INPUT -> client.respond(pending.requestId, JsonObject().apply {
                add("answers", JsonObject())
            })

            PendingKind.MCP_ELICITATION -> client.respond(pending.requestId, JsonObject().apply {
                addProperty("action", "cancel")
            })

            PendingKind.PERMISSION -> client.respondError(
                pending.requestId,
                -32000,
                "User dismissed permission request",
            )
        }
        removePending(pending.requestId)
    }

    override fun onReady(serverInfo: JsonObject) {
        reconnectAttempt = 0
        mainHandler.removeCallbacksAndMessages(RECONNECT_TOKEN)
        val platform = listOfNotNull(
            serverInfo.string("platformFamily"),
            serverInfo.string("platformOs"),
        ).joinToString(" / ")
        val userAgent = serverInfo.string("userAgent").orEmpty()
        val versionWarning = userAgent.isNotBlank() && !userAgent.contains(TESTED_CODEX_VERSION)
        uiState = uiState.copy(
            connectionStatus = ConnectionStatus.CONNECTED,
            connectionMessage = buildString {
                append(if (platform.isBlank()) "已连接 Codex" else "已连接 · $platform")
                if (versionWarning) append(" · CLI 协议版本未验证")
            },
        )
        val requestedThreadId = pendingNotificationThreadId
        pendingNotificationThreadId = null
        val currentThreadId = requestedThreadId ?: uiState.currentThreadId
        if (currentThreadId != null) resumeThread(currentThreadId)
        refreshThreads()
        refreshConfigDefaults()
        refreshModels()
        refreshSkills()
    }

    override fun onDisconnected(reason: String) {
        CodexConnectionService.stop(getApplication())
        uiState = uiState.copy(
            connectionStatus = ConnectionStatus.DISCONNECTED,
            connectionMessage = readableConnectionError(reason),
            busy = false,
            activeTurnId = null,
            pendingActions = emptyList(),
            modelsLoading = false,
            skillsLoading = false,
        )
        notifier.cancelPendingAction()
        if (!manualDisconnect) {
            val delay = minOf(30_000L, 1_000L shl reconnectAttempt.coerceAtMost(5))
            reconnectAttempt++
            mainHandler.postAtTime(
                { if (uiState.connectionStatus == ConnectionStatus.DISCONNECTED) connectNow() },
                RECONNECT_TOKEN,
                android.os.SystemClock.uptimeMillis() + delay,
            )
        }
    }

    override fun onNotification(method: String, params: JsonObject) {
        val eventThreadId = params.string("threadId")
        val threadScoped = eventThreadId != null
        if (threadScoped && eventThreadId != uiState.currentThreadId && method !in GLOBAL_THREAD_EVENTS) {
            return
        }
        when (method) {
            "turn/started" -> {
                val turn = params.getAsJsonObject("turn")
                uiState = uiState.copy(
                    activeTurnId = turn?.string("id") ?: uiState.activeTurnId,
                    busy = true,
                )
                CodexConnectionService.start(getApplication(), true)
            }

            "turn/completed" -> {
                flushPendingDeltas()
                val turn = params.getAsJsonObject("turn")
                val status = turn?.string("status") ?: "completed"
                uiState = uiState.copy(
                    activeTurnId = null,
                    busy = false,
                )
                CodexConnectionService.stop(getApplication())
                if (status == "failed") {
                    addInfo("任务失败：${turn?.get("error")?.let(prettyGson::toJson) ?: "未知错误"}", true)
                }
                refreshThreads()
            }

            "item/agentMessage/delta" -> {
                appendDelta(
                    id = params.string("itemId") ?: return,
                    kind = MessageKind.ASSISTANT,
                    title = null,
                    delta = params.string("delta") ?: "",
                )
            }

            "item/plan/delta" -> {
                appendDelta(
                    id = params.string("itemId") ?: return,
                    kind = MessageKind.TOOL,
                    title = "计划",
                    delta = params.string("delta") ?: "",
                )
            }

            "item/reasoning/summaryTextDelta" -> {
                appendDelta(
                    id = params.string("itemId") ?: return,
                    kind = MessageKind.INFO,
                    title = "思考摘要",
                    delta = params.string("delta") ?: "",
                )
            }

            "item/commandExecution/outputDelta", "item/fileChange/outputDelta" -> {
                appendDelta(
                    id = params.string("itemId") ?: return,
                    kind = MessageKind.TOOL,
                    title = if (method.contains("command")) "命令" else "文件修改",
                    delta = params.string("delta") ?: "",
                )
            }

            "item/started" -> handleItem(params.getAsJsonObject("item"), running = true)
            "item/completed" -> handleItem(params.getAsJsonObject("item"), running = false)
            "skills/changed" -> refreshSkills(forceReload = true)
            "thread/status/changed" -> {
                val status = params.getAsJsonObject("status")?.string("type")
                uiState = uiState.copy(
                    busy = status == "active",
                    activeTurnId = if (status == "active") uiState.activeTurnId else null,
                )
                if (status == "active") {
                    CodexConnectionService.start(getApplication(), true)
                } else {
                    CodexConnectionService.stop(getApplication())
                }
            }

            "thread/deleted" -> {
                params.string("threadId")?.let(::removeDeletedThread)
            }

            "serverRequest/resolved" -> {
                params.get("requestId")?.let(::removePending)
            }

            "thread/name/updated" -> {
                val threadId = params.string("threadId")
                val name = params.string("name")
                if (threadId == uiState.currentThreadId && !name.isNullOrBlank()) {
                    uiState = uiState.copy(currentThreadTitle = name)
                }
                refreshThreads()
            }

            "error" -> {
                val willRetry = params.bool("willRetry") == true
                val message = params.get("error")?.let(prettyGson::toJson) ?: "Codex 发生错误"
                addInfo(if (willRetry) "$message\n正在重试…" else message, error = !willRetry)
            }

            "warning", "guardianWarning", "deprecationNotice", "configWarning" -> {
                addInfo(
                    params.string("message") ?: params.string("reason") ?: prettyGson.toJson(params),
                    error = method == "guardianWarning",
                )
            }

            "model/rerouted" -> {
                val target = params.string("toModel") ?: params.string("model") ?: "其他模型"
                addInfo("当前任务已由 App Server 切换到 $target")
            }
        }
    }

    override fun onServerRequest(request: CodexAppServerClient.ServerRequest) {
        val pending = when (request.method) {
            "item/commandExecution/requestApproval" -> PendingAction(
                requestId = request.id,
                kind = PendingKind.COMMAND,
                title = "允许执行命令？",
                detail = buildList {
                    request.params.string("command")?.let(::add)
                    request.params.string("cwd")?.let { add("目录：$it") }
                    request.params.string("reason")?.let { add("原因：$it") }
                }.joinToString("\n\n"),
                rawParams = request.params,
                threadId = request.params.string("threadId"),
                decisions = parseCommandDecisions(request.params),
            )

            "item/fileChange/requestApproval" -> PendingAction(
                requestId = request.id,
                kind = PendingKind.FILE_CHANGE,
                title = "允许修改文件？",
                detail = request.params.string("reason") ?: "Codex 请求应用文件修改。",
                rawParams = request.params,
                threadId = request.params.string("threadId"),
                decisions = defaultApprovalDecisions(),
            )

            "item/permissions/requestApproval" -> PendingAction(
                requestId = request.id,
                kind = PendingKind.PERMISSION,
                title = "授予额外权限？",
                detail = buildString {
                    request.params.string("reason")?.let { append(it).append("\n\n") }
                    append(prettyGson.toJson(request.params.get("permissions")))
                },
                rawParams = request.params,
                threadId = request.params.string("threadId"),
                decisions = permissionDecisions(),
            )

            "item/tool/requestUserInput" -> PendingAction(
                requestId = request.id,
                kind = PendingKind.USER_INPUT,
                title = "Codex 需要你的选择",
                detail = "请回答后继续任务。",
                rawParams = request.params,
                questions = parseQuestions(request.params),
                threadId = request.params.string("threadId"),
                autoResolutionMs = request.params.long("autoResolutionMs"),
            )

            "mcpServer/elicitation/request" -> {
                val mode = request.params.string("mode") ?: "form"
                val questions = if (mode.endsWith("form")) parseMcpQuestions(request.params) else emptyList()
                PendingAction(
                    requestId = request.id,
                    kind = PendingKind.MCP_ELICITATION,
                    title = "MCP · ${request.params.string("serverName") ?: "服务"}",
                    detail = buildString {
                        append(request.params.string("message") ?: "MCP 服务需要你的确认。")
                        request.params.string("url")?.let { append("\n\n").append(it) }
                    },
                    rawParams = request.params,
                    questions = questions,
                    threadId = request.params.string("threadId"),
                    decisions = if (questions.isEmpty()) mcpDecisions() else emptyList(),
                )
            }

            "currentTime/read" -> {
                client.respond(request.id, JsonObject().apply {
                    addProperty("currentTimeAt", System.currentTimeMillis() / 1000L)
                })
                null
            }

            "applyPatchApproval" -> PendingAction(
                requestId = request.id,
                kind = PendingKind.FILE_CHANGE,
                title = "允许应用补丁？",
                detail = request.params.string("reason") ?: "Codex 请求应用补丁。",
                rawParams = request.params,
                threadId = request.params.string("conversationId"),
                decisions = legacyApprovalDecisions(),
            )

            "execCommandApproval" -> PendingAction(
                requestId = request.id,
                kind = PendingKind.COMMAND,
                title = "允许执行命令？",
                detail = request.params.string("command") ?: prettyGson.toJson(request.params),
                rawParams = request.params,
                threadId = request.params.string("conversationId"),
                decisions = legacyApprovalDecisions(),
            )

            else -> {
                client.respondError(request.id, -32601, "Unsupported client request: ${request.method}")
                addInfo("客户端暂不支持服务端请求：${request.method}", error = true)
                null
            }
        }
        if (pending != null) {
            enqueuePending(pending)
        }
    }

    override fun onProtocolError(message: String) {
        addInfo(message, error = true)
    }

    override fun onCleared() {
        flushPendingDeltas()
        mainHandler.removeCallbacksAndMessages(null)
        notifier.cancelPendingAction()
        CodexConnectionService.stop(getApplication())
        client.shutdown()
        super.onCleared()
    }

    private fun startThread() {
        val params = JsonObject().apply {
            if (uiState.cwd.isNotBlank()) addProperty("cwd", uiState.cwd)
            if (uiState.model.isNotBlank()) addProperty("model", uiState.model)
        }
        client.request("thread/start", params) { response ->
            response.error?.let {
                pendingPrompt = null
                uiState = uiState.copy(busy = false)
                CodexConnectionService.stop(getApplication())
                addInfo("新建会话失败：${rpcError(it)}", error = true)
                return@request
            }
            val thread = response.result?.getAsJsonObject("thread")
            val threadId = thread?.string("id")
            if (threadId == null) {
                pendingPrompt = null
                uiState = uiState.copy(busy = false)
                CodexConnectionService.stop(getApplication())
                addInfo("App Server 未返回会话 ID。", error = true)
                return@request
            }
            uiState = uiState.copy(
                currentThreadId = threadId,
                currentThreadTitle = thread.string("name")
                    ?: thread.string("preview")?.takeIf { it.isNotBlank() }
                    ?: "新会话",
            )
            preferences.edit {
                putString("current_thread_id", threadId)
                putString("current_thread_title", uiState.currentThreadTitle)
            }
            subscribedThreadId = threadId
            pendingPrompt?.let { prompt ->
                pendingPrompt = null
                startTurn(threadId, prompt)
            }
            refreshThreads()
        }
    }

    private fun loadModelsPage(cursor: String?, accumulated: List<CodexModel>) {
        client.request("model/list", JsonObject().apply {
            addProperty("limit", 100)
            addProperty("includeHidden", false)
            cursor?.let { addProperty("cursor", it) }
        }) { response ->
            response.error?.let {
                uiState = uiState.copy(
                    modelsLoading = false,
                    modelsError = "读取模型失败：${rpcError(it)}",
                )
                return@request
            }
            val result = response.result ?: JsonObject()
            val page = result.getAsJsonArray("data")?.mapNotNull { element ->
                val item = element.asJsonObject
                val model = item.string("model") ?: return@mapNotNull null
                CodexModel(
                    id = item.string("id") ?: model,
                    model = model,
                    displayName = item.string("displayName") ?: model,
                    description = item.string("description") ?: "",
                    isDefault = item.bool("isDefault") == true,
                    defaultReasoningEffort = item.string("defaultReasoningEffort") ?: "",
                    supportedReasoningEfforts = item.getAsJsonArray("supportedReasoningEfforts")
                        ?.mapNotNull { effortElement ->
                            val effort = effortElement.asJsonObject
                            val value = effort.string("reasoningEffort")
                                ?: return@mapNotNull null
                            ReasoningEffortOption(
                                value = value,
                                description = effort.string("description") ?: "",
                            )
                        }
                        .orEmpty(),
                )
            }.orEmpty()
            val models = (accumulated + page).distinctBy { it.model }
            val nextCursor = result.string("nextCursor")
            if (nextCursor != null) {
                loadModelsPage(nextCursor, models)
            } else {
                uiState = uiState.copy(
                    availableModels = models,
                    modelsLoading = false,
                    modelsError = if (models.isEmpty()) "App Server 未返回可用模型" else null,
                )
            }
        }
    }

    private fun startTurn(threadId: String, prompt: String) {
        val selectedSkills = uiState.availableSkills
            .filter { it.path in uiState.selectedSkillPaths }
        client.request("turn/start", JsonObject().apply {
            addProperty("threadId", threadId)
            if (uiState.cwd.isNotBlank()) addProperty("cwd", uiState.cwd)
            if (uiState.model.isNotBlank()) {
                addProperty("model", uiState.model)
            }
            if (uiState.reasoningEffort.isNotBlank()) {
                addProperty("effort", uiState.reasoningEffort)
            }
            add("input", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("type", "text")
                    addProperty("text", prompt)
                })
                selectedSkills.forEach { skill ->
                    add(JsonObject().apply {
                        addProperty("type", "skill")
                        addProperty("name", skill.name)
                        addProperty("path", skill.path)
                    })
                }
            })
        }) { response ->
            response.error?.let {
                uiState = uiState.copy(busy = false, activeTurnId = null)
                CodexConnectionService.stop(getApplication())
                addInfo("发送失败：${rpcError(it)}", error = true)
                return@request
            }
            val turnId = response.result?.getAsJsonObject("turn")?.string("id")
            uiState = uiState.copy(activeTurnId = turnId, busy = true)
        }
    }

    private fun appendDelta(
        id: String,
        kind: MessageKind,
        title: String?,
        delta: String,
    ) {
        if (delta.isEmpty()) return
        val buffered = pendingDeltas.getOrPut(id) { BufferedDelta(kind, title) }
        if (!buffered.truncated) {
            val remaining = MAX_STREAMED_MESSAGE_CHARS - buffered.text.length
            if (remaining > 0) {
                buffered.text.append(delta.take(remaining))
            }
            if (delta.length > remaining) {
                buffered.text.append("\n\n[输出过长，已截断]")
                buffered.truncated = true
            }
        }
        if (!deltaFlushScheduled) {
            deltaFlushScheduled = true
            mainHandler.postDelayed(::flushPendingDeltas, DELTA_FLUSH_INTERVAL_MS)
        }
    }

    private fun flushPendingDeltas() {
        deltaFlushScheduled = false
        if (pendingDeltas.isEmpty()) return
        val messages = uiState.messages.toMutableList()
        pendingDeltas.forEach { (id, buffered) ->
            val index = messages.indexOfLast { it.id == id }
            if (index >= 0) {
                val current = messages[index]
                messages[index] = current.copy(
                    text = current.text + buffered.text,
                    running = true,
                )
            } else {
                messages += UiMessage(
                    id = id,
                    kind = buffered.kind,
                    title = buffered.title,
                    text = buffered.text.toString(),
                    running = true,
                )
            }
        }
        pendingDeltas.clear()
        uiState = uiState.copy(messages = messages)
    }

    private fun handleItem(item: JsonObject?, running: Boolean) {
        item ?: return
        val id = item.string("id") ?: return
        val type = item.string("type") ?: return
        if (!running) pendingDeltas.remove(id)
        val itemStatus = item.string("status")
        val outcome = when {
            itemStatus == "failed" -> MessageOutcome.FAILED
            itemStatus == "declined" -> MessageOutcome.DECLINED
            type == "commandExecution" && (item.long("exitCode") ?: 0L) != 0L -> MessageOutcome.FAILED
            itemStatus == "completed" -> MessageOutcome.SUCCESS
            else -> if (running) MessageOutcome.NEUTRAL else MessageOutcome.SUCCESS
        }
        val message = when (type) {
            "agentMessage" -> UiMessage(
                id = id,
                kind = MessageKind.ASSISTANT,
                text = item.string("text") ?: "",
                running = running,
                outcome = outcome,
            )

            "plan" -> UiMessage(
                id = id,
                kind = MessageKind.TOOL,
                title = "计划",
                text = item.string("text") ?: "",
                running = running,
                outcome = outcome,
            )

            "commandExecution" -> UiMessage(
                id = id,
                kind = MessageKind.TOOL,
                title = when {
                    running -> "正在执行命令"
                    outcome == MessageOutcome.FAILED -> "命令执行失败"
                    outcome == MessageOutcome.DECLINED -> "命令已拒绝"
                    else -> "命令执行完成"
                },
                text = buildString {
                    append(item.string("command") ?: "")
                    item.string("aggregatedOutput")?.takeIf { it.isNotBlank() }?.let {
                        append("\n\n").append(it)
                    }
                    item.long("exitCode")
                        ?.takeIf { it != 0L }
                        ?.let { append("\n\n退出码：").append(it) }
                },
                running = running,
                outcome = outcome,
            )

            "fileChange" -> UiMessage(
                id = id,
                kind = MessageKind.TOOL,
                title = when {
                    running -> "正在修改文件"
                    outcome == MessageOutcome.FAILED -> "文件修改失败"
                    outcome == MessageOutcome.DECLINED -> "文件修改已拒绝"
                    else -> "文件修改完成"
                },
                text = item.get("changes")?.let(prettyGson::toJson) ?: "等待修改信息…",
                running = running,
                outcome = outcome,
            )

            "mcpToolCall" -> UiMessage(
                id = id,
                kind = MessageKind.TOOL,
                title = "MCP · ${item.string("server") ?: "tool"}",
                text = buildString {
                    append(item.string("tool") ?: "工具调用")
                    item.get("arguments")?.let { append("\n\n").append(prettyGson.toJson(it)) }
                    item.get("result")?.takeUnless { it.isJsonNull }?.let {
                        append("\n\n").append(prettyGson.toJson(it))
                    }
                },
                running = running,
                outcome = outcome,
            )

            "webSearch" -> UiMessage(
                id = id,
                kind = MessageKind.TOOL,
                title = "网页搜索",
                text = item.string("query") ?: "",
                running = running,
                outcome = outcome,
            )

            else -> return
        }
        upsertMessage(message)
    }

    private fun upsertMessage(message: UiMessage) {
        val index = uiState.messages.indexOfLast { it.id == message.id }
        val messages = if (index >= 0) {
            uiState.messages.toMutableList().also { it[index] = message }
        } else {
            uiState.messages + message
        }
        uiState = uiState.copy(messages = messages)
    }

    private fun addInfo(text: String, error: Boolean = false) {
        uiState = uiState.copy(
            messages = uiState.messages + UiMessage(
                id = "info-${UUID.randomUUID()}",
                kind = if (error) MessageKind.ERROR else MessageKind.INFO,
                text = text,
            )
        )
    }

    private fun parseTurnsMessages(turnsInput: JsonArray, newestFirst: Boolean): List<UiMessage> {
        val messages = mutableListOf<UiMessage>()
        val turns = if (newestFirst) turnsInput.reversed() else turnsInput.toList()
        turns.forEach { turnElement ->
            val items = turnElement.asJsonObject.getAsJsonArray("items") ?: JsonArray()
            items.forEach { itemElement ->
                val item = itemElement.asJsonObject
                val id = item.string("id") ?: "history-${UUID.randomUUID()}"
                when (item.string("type")) {
                    "userMessage" -> {
                        val text = item.getAsJsonArray("content")
                            ?.mapNotNull { it.asJsonObject.string("text") }
                            ?.joinToString("\n")
                            .orEmpty()
                        if (text.isNotBlank()) messages += UiMessage(id, MessageKind.USER, text)
                    }

                    "agentMessage" -> item.string("text")?.takeIf { it.isNotBlank() }?.let {
                        messages += UiMessage(id, MessageKind.ASSISTANT, it)
                    }

                    "plan" -> item.string("text")?.takeIf { it.isNotBlank() }?.let {
                        messages += UiMessage(id, MessageKind.TOOL, it, "计划")
                    }

                    "commandExecution" -> {
                        val exitCode = item.long("exitCode") ?: 0L
                        val status = item.string("status")
                        messages += UiMessage(
                        id = id,
                        kind = MessageKind.TOOL,
                        title = if (status == "failed" || exitCode != 0L) "命令执行失败" else "命令",
                        text = buildString {
                            append(item.string("command") ?: "")
                            item.string("aggregatedOutput")?.takeIf { it.isNotBlank() }?.let {
                                append("\n\n").append(it)
                            }
                        },
                        outcome = if (status == "failed" || exitCode != 0L) {
                            MessageOutcome.FAILED
                        } else {
                            MessageOutcome.SUCCESS
                        },
                    )
                    }

                    "fileChange" -> messages += UiMessage(
                        id = id,
                        kind = MessageKind.TOOL,
                        title = "文件修改",
                        text = item.get("changes")?.let(prettyGson::toJson) ?: "",
                        outcome = if (item.string("status") == "failed") {
                            MessageOutcome.FAILED
                        } else {
                            MessageOutcome.SUCCESS
                        },
                    )

                    "mcpToolCall" -> messages += UiMessage(
                        id = id,
                        kind = MessageKind.TOOL,
                        title = "MCP · ${item.string("server") ?: "tool"}",
                        text = item.string("tool") ?: "工具调用",
                        outcome = if (item.string("status") == "failed") {
                            MessageOutcome.FAILED
                        } else {
                            MessageOutcome.SUCCESS
                        },
                    )

                    "webSearch" -> messages += UiMessage(
                        id = id,
                        kind = MessageKind.TOOL,
                        title = "网页搜索",
                        text = item.string("query") ?: "",
                        outcome = MessageOutcome.SUCCESS,
                    )
                }
            }
        }
        return messages
    }

    private fun parseQuestions(params: JsonObject): List<InputQuestion> {
        return params.getAsJsonArray("questions")?.mapNotNull { element ->
            val question = element.asJsonObject
            val id = question.string("id") ?: return@mapNotNull null
            InputQuestion(
                id = id,
                header = question.string("header") ?: "问题",
                prompt = question.string("question") ?: "请输入回答",
                options = question.getAsJsonArray("options")
                    ?.mapNotNull { it.asJsonObject.string("label") }
                    .orEmpty(),
                secret = question.bool("isSecret") == true,
                required = true,
            )
        }.orEmpty()
    }

    private fun parseMcpQuestions(params: JsonObject): List<InputQuestion> {
        val schema = params.getAsJsonObject("requestedSchema") ?: return emptyList()
        val required = schema.getAsJsonArray("required")
            ?.mapNotNull { runCatching { it.asString }.getOrNull() }
            ?.toSet()
            .orEmpty()
        val properties = schema.getAsJsonObject("properties") ?: return emptyList()
        return properties.entrySet().map { (id, element) ->
            val property = element.asJsonObject
            val options = property.getAsJsonArray("enum")
                ?.mapNotNull { runCatching { it.asString }.getOrNull() }
                ?: property.getAsJsonArray("oneOf")
                    ?.mapNotNull { it.asJsonObject.string("const") }
                    .orEmpty()
            InputQuestion(
                id = id,
                header = property.string("title") ?: id,
                prompt = property.string("description") ?: "请输入 $id",
                options = options,
                secret = property.string("format") == "password",
                valueType = property.string("type") ?: "string",
                required = id in required,
            )
        }
    }

    private fun parseCommandDecisions(params: JsonObject): List<PendingDecision> {
        val available = params.getAsJsonArray("availableDecisions")
            ?.mapNotNull(::decisionFromJson)
            .orEmpty()
        return available.ifEmpty { defaultApprovalDecisions() }
    }

    private fun decisionFromJson(element: JsonElement): PendingDecision? {
        val key = when {
            element.isJsonPrimitive -> element.asString
            element.isJsonObject -> element.asJsonObject.entrySet().firstOrNull()?.key
            else -> null
        } ?: return null
        return PendingDecision(key, decisionLabel(key), element.deepCopy())
    }

    private fun defaultApprovalDecisions() = listOf(
        PendingDecision("decline", "拒绝", JsonPrimitive("decline")),
        PendingDecision("acceptForSession", "本次会话允许", JsonPrimitive("acceptForSession")),
        PendingDecision("accept", "允许一次", JsonPrimitive("accept")),
    )

    private fun permissionDecisions() = listOf(
        PendingDecision("decline", "拒绝", JsonPrimitive("decline")),
        PendingDecision("acceptForSession", "本次会话授予", JsonPrimitive("acceptForSession")),
        PendingDecision("accept", "本次任务授予", JsonPrimitive("accept")),
    )

    private fun legacyApprovalDecisions() = listOf(
        PendingDecision("denied", "拒绝", JsonPrimitive("denied")),
        PendingDecision("approved_for_session", "本次会话允许", JsonPrimitive("approved_for_session")),
        PendingDecision("approved", "允许一次", JsonPrimitive("approved")),
    )

    private fun mcpDecisions() = listOf(
        PendingDecision("decline", "拒绝", JsonPrimitive("decline")),
        PendingDecision("accept", "允许", JsonPrimitive("accept")),
    )

    private fun decisionLabel(key: String): String = when (key) {
        "accept" -> "允许一次"
        "acceptForSession" -> "本次会话允许"
        "decline" -> "拒绝"
        "cancel" -> "拒绝并停止"
        "acceptWithExecpolicyAmendment" -> "允许并记住命令规则"
        "applyNetworkPolicyAmendment" -> "应用网络规则"
        else -> key
    }

    private fun enqueuePending(action: PendingAction) {
        if (uiState.pendingActions.any { it.requestId.toString() == action.requestId.toString() }) return
        val wasEmpty = uiState.pendingActions.isEmpty()
        uiState = uiState.copy(pendingActions = uiState.pendingActions + action)
        if (wasEmpty && !appInForeground) notifier.showPendingAction(action)
    }

    private fun removePending(requestId: JsonElement) {
        val remaining = uiState.pendingActions.filterNot {
            it.requestId.toString() == requestId.toString()
        }
        uiState = uiState.copy(pendingActions = remaining)
        notifier.cancelPendingAction()
        if (!appInForeground) remaining.firstOrNull()?.let(notifier::showPendingAction)
    }

    private fun typedAnswer(value: String, type: String): JsonElement = when (type) {
        "boolean" -> JsonPrimitive(value.equals("true", true) || value == "1")
        "integer" -> JsonPrimitive(value.toLongOrNull() ?: 0L)
        "number" -> JsonPrimitive(value.toDoubleOrNull() ?: 0.0)
        "array" -> JsonArray().apply {
            value.split(',').map(String::trim).filter(String::isNotEmpty).forEach(::add)
        }
        else -> JsonPrimitive(value)
    }

    fun readDirectories(
        path: String,
        callback: (List<RemoteDirectory>, String?) -> Unit,
    ) {
        if (!client.isReady()) {
            callback(emptyList(), "请先连接 App Server")
            return
        }
        client.request("fs/readDirectory", JsonObject().apply { addProperty("path", path) }) { response ->
            response.error?.let {
                callback(emptyList(), rpcError(it))
                return@request
            }
            val base = path.trimEnd('/').ifBlank { "/" }
            val directories = response.result?.getAsJsonArray("entries")
                ?.mapNotNull { element ->
                    val entry = element.asJsonObject
                    if (entry.bool("isDirectory") != true) return@mapNotNull null
                    val name = entry.string("fileName") ?: return@mapNotNull null
                    RemoteDirectory(name, if (base == "/") "/$name" else "$base/$name")
                }
                ?.sortedBy { it.name.lowercase() }
                .orEmpty()
            callback(directories, null)
        }
    }

    private fun unsubscribe(threadId: String?) {
        threadId ?: return
        if (!client.isReady()) return
        client.request("thread/unsubscribe", JsonObject().apply { addProperty("threadId", threadId) })
    }

    private fun loadAndMigrateToken(): String {
        val encrypted = secretStore.getTransportToken()
        if (encrypted.isNotBlank()) return encrypted
        val legacy = preferences.getString("token", null).orEmpty()
        if (legacy.isNotBlank()) secretStore.setTransportToken(legacy)
        preferences.edit { remove("token") }
        return legacy
    }

    private fun rpcError(error: JsonObject): String {
        val message = error.string("message") ?: "未知错误"
        val code = error.long("code")
        return if (code != null) "$message ($code)" else message
    }

    private fun readableConnectionError(reason: String): String {
        return when {
            reason.contains("Connection refused", ignoreCase = true) ->
                "连接被拒绝，请先在 Termux 启动 App Server"

            reason == "reconnect" -> "正在重新连接"
            else -> "连接断开：$reason"
        }
    }

    fun formatThreadTime(epochSeconds: Long): String {
        if (epochSeconds <= 0) return ""
        return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(epochSeconds * 1000))
    }

    private companion object {
        const val MAX_THREAD_LIST_SIZE = 250
        const val HISTORY_PAGE_SIZE = 40
        const val DELTA_FLUSH_INTERVAL_MS = 32L
        const val MAX_STREAMED_MESSAGE_CHARS = 200_000
        const val TESTED_CODEX_VERSION = "0.144.1"
        val GLOBAL_THREAD_EVENTS = setOf(
            "serverRequest/resolved",
            "thread/archived",
            "thread/deleted",
            "thread/unarchived",
            "thread/closed",
        )
        val RECONNECT_TOKEN = Any()
    }
}

private fun JsonObject.string(name: String): String? = try {
    get(name)?.takeUnless { it.isJsonNull }?.asString
} catch (_: Throwable) {
    null
}

private fun JsonObject.long(name: String): Long? = try {
    get(name)?.takeUnless { it.isJsonNull }?.asLong
} catch (_: Throwable) {
    null
}

private fun JsonObject.bool(name: String): Boolean? = try {
    get(name)?.takeUnless { it.isJsonNull }?.asBoolean
} catch (_: Throwable) {
    null
}
