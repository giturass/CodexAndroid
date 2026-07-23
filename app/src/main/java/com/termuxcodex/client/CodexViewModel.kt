package com.termuxcodex.client

import android.app.Application
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.edit
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.termuxcodex.client.data.CodexAppServerClient
import com.termuxcodex.client.data.CodexProtocol
import com.termuxcodex.client.data.ConnectionSecurity
import com.termuxcodex.client.data.SecretStore
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CodexViewModel(application: Application) : AndroidViewModel(application),
    CodexAppServerClient.Listener {

    private val preferences = application.getSharedPreferences("connection", 0)
    private val pinnedThreadIds = preferences.getStringSet("pinned_threads", emptySet())
        .orEmpty()
        .toMutableSet()
    private val secretStore = SecretStore(application)
    private val prettyGson = GsonBuilder().setPrettyPrinting().create()
    private val client = CodexAppServerClient(this)
    private val notifier = ApprovalNotifier(application)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingPrompt: PendingPrompt? = null
    private var appInForeground = true
    private val subscribedThreadIds = mutableSetOf<String>()
    private var manualDisconnect = false
    private var reconnectAttempt = 0
    private var resumeGeneration = 0
    private var pendingNotificationThreadId: String? = null
    private var threadListGeneration = 0
    private var threadSearchGeneration = 0
    private var modelListGeneration = 0
    private var skillsGeneration = 0
    private var mcpStatusGeneration = 0
    private var workspaceValidationGeneration = 0
    private var foregroundServiceWarningShown = false
    private val pendingDeltas = linkedMapOf<String, BufferedDelta>()
    private val fileChangePatches = mutableMapOf<String, String>()
    private val turnDiffs = mutableMapOf<String, String>()
    private var deltaFlushScheduled = false

    private data class BufferedDelta(
        val kind: MessageKind,
        val title: String?,
        val text: StringBuilder = StringBuilder(),
        var truncated: Boolean = false,
    )

    private data class PendingPrompt(val input: PromptInput, val messageId: String)

    var uiState by mutableStateOf(
        AppUiState(
            endpoint = preferences.getString("endpoint", null)
                ?: "ws://127.0.0.1:4500",
            token = loadAndMigrateToken(),
            cwd = preferences.getString("cwd", null)
                ?: DEFAULT_TERMUX_HOME,
            workspaceConfigured = preferences.getBoolean(WORKSPACE_ONBOARDING_COMPLETED, false),
            themeMode = AppThemeMode.entries.firstOrNull {
                it.name == preferences.getString("theme_mode", AppThemeMode.SYSTEM.name)
            } ?: AppThemeMode.SYSTEM,
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
    ): Boolean {
        val normalizedEndpoint = endpoint.trim()
        val normalizedCwd = cwd.trim()
        val normalizedModel = model.trim()
        val normalizedReasoningEffort = reasoningEffort.trim()
        if (!isValidWorkspacePath(normalizedCwd)) {
            addInfo("工作目录必须是非空的绝对路径。", error = true)
            return false
        }
        val cwdChanged = normalizedCwd != uiState.cwd
        val endpointChanged = normalizedEndpoint != uiState.endpoint
        val tokenChanged = token.trim() != uiState.token
        val sessionChanged = cwdChanged || endpointChanged || tokenChanged
        if (sessionChanged && !uiState.busy) unsubscribeAllThreads()
        preferences.edit {
            putString("endpoint", normalizedEndpoint)
            putString("cwd", normalizedCwd)
            putString("model", normalizedModel)
            putString("reasoning_effort", normalizedReasoningEffort)
            putStringSet("selected_skills", selectedSkillPaths)
            if (cwdChanged) putBoolean(WORKSPACE_ONBOARDING_COMPLETED, false)
        }
        if (!secretStore.setTransportToken(token.trim())) {
            addInfo("Android Keystore 无法保存 Token；本次连接仍会使用当前输入。", error = true)
        }
        preferences.edit { remove("token") }
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
            workspaceConfigured = if (cwdChanged) false else uiState.workspaceConfigured,
            model = normalizedModel,
            reasoningEffort = normalizedReasoningEffort,
            selectedSkillPaths = selectedSkillPaths.toSet(),
            currentThreadId = if (sessionChanged && !uiState.busy) null else uiState.currentThreadId,
            currentThreadTitle = if (sessionChanged && !uiState.busy) "新会话" else uiState.currentThreadTitle,
            messages = if (sessionChanged && !uiState.busy) emptyList() else uiState.messages,
            historyNextCursor = if (sessionChanged && !uiState.busy) null else uiState.historyNextCursor,
        )
        return true
    }

    fun selectWorkspace(cwd: String): Boolean {
        val normalizedCwd = cwd.trim()
        if (!isValidWorkspacePath(normalizedCwd)) {
            addInfo("工作目录必须是非空的绝对路径。", error = true)
            return false
        }
        val cwdChanged = normalizedCwd != uiState.cwd
        val resetSession = (cwdChanged || !uiState.workspaceConfigured) && !uiState.busy
        if (resetSession) unsubscribeAllThreads()
        preferences.edit {
            putString("cwd", normalizedCwd)
            putBoolean(WORKSPACE_ONBOARDING_COMPLETED, false)
            if (resetSession) {
                remove("current_thread_id")
                remove("current_thread_title")
            }
        }
        uiState = uiState.copy(
            cwd = normalizedCwd,
            workspaceConfigured = false,
            currentThreadId = if (resetSession) null else uiState.currentThreadId,
            currentThreadTitle = if (resetSession) "新会话" else uiState.currentThreadTitle,
            messages = if (resetSession) emptyList() else uiState.messages,
            historyNextCursor = if (resetSession) null else uiState.historyNextCursor,
        )
        if (client.isReady()) {
            validateWorkspace {
                preferences.edit { putBoolean(WORKSPACE_ONBOARDING_COMPLETED, true) }
                refreshConfigDefaults()
                refreshSkills(forceReload = true)
            }
        }
        return true
    }

    fun updateComposerModel(model: String) {
        val normalizedModel = model.trim()
        val selectedModel = uiState.availableModels.firstOrNull { it.model == normalizedModel }
            ?: uiState.availableModels.firstOrNull { it.model == uiState.configModel }
            ?: uiState.availableModels.firstOrNull { it.isDefault }
        val supportedEfforts = selectedModel?.supportedReasoningEfforts.orEmpty().map { it.value }
        val effort = uiState.reasoningEffort.takeIf {
            supportedEfforts.isEmpty() || it in supportedEfforts
        }.orEmpty()
        preferences.edit {
            putString("model", normalizedModel)
            putString("reasoning_effort", effort)
        }
        uiState = uiState.copy(model = normalizedModel, reasoningEffort = effort)
    }

    fun updateComposerReasoningEffort(effort: String) {
        val normalizedEffort = effort.trim()
        val model = uiState.availableModels.firstOrNull { it.model == uiState.model }
            ?: uiState.availableModels.firstOrNull { it.model == uiState.configModel }
            ?: uiState.availableModels.firstOrNull { it.isDefault }
        if (normalizedEffort.isNotEmpty() &&
            model?.supportedReasoningEfforts.orEmpty().none { it.value == normalizedEffort }
        ) {
            return
        }
        preferences.edit { putString("reasoning_effort", normalizedEffort) }
        uiState = uiState.copy(reasoningEffort = normalizedEffort)
    }

    fun updateThemeMode(mode: AppThemeMode) {
        preferences.edit { putString("theme_mode", mode.name) }
        uiState = uiState.copy(themeMode = mode)
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
        if (client.isReady()) unsubscribeAllThreads()
        uiState = uiState.copy(
            connectionStatus = ConnectionStatus.CONNECTING,
            connectionMessage = "正在连接 ${uiState.endpoint}",
            workspaceConfigured = false,
            availableModels = emptyList(),
            modelsLoading = false,
            modelsError = null,
            configModel = "",
            configReasoningEffort = "",
            availableSkills = emptyList(),
            skillsCwd = "",
            skillsLoading = false,
            skillsError = null,
            mcpServers = emptyList(),
            mcpLoading = false,
            mcpError = null,
        )
        client.connect(validation.endpoint, uiState.token.ifBlank { null })
    }

    fun disconnect() {
        manualDisconnect = true
        mainHandler.removeCallbacksAndMessages(RECONNECT_TOKEN)
        unsubscribeAllThreads()
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
        unsubscribeAllThreads()
        pendingDeltas.clear()
        fileChangePatches.clear()
        turnDiffs.clear()
        preferences.edit {
            remove("current_thread_id")
            remove("current_thread_title")
        }
        uiState = uiState.copy(
            messages = emptyList(),
            currentThreadId = null,
            currentThreadTitle = "新会话",
            activeTurnId = null,
            busy = false,
            pendingActions = emptyList(),
            historyNextCursor = null,
        )
        if (client.isReady()) refreshThreads()
    }

    fun renameThread(threadId: String, name: String) {
        val normalizedName = name.trim()
        if (threadId.isBlank() || normalizedName.isBlank() || !client.isReady()) return
        client.request(CodexProtocol.ClientRequest.THREAD_NAME_SET, JsonObject().apply {
            addProperty("threadId", threadId)
            addProperty("name", normalizedName)
        }) { response ->
            response.error?.let {
                addInfo("重命名会话失败：${rpcError(it)}", error = true)
                return@request
            }
            uiState = uiState.copy(
                currentThreadTitle = if (threadId == uiState.currentThreadId) {
                    normalizedName
                } else {
                    uiState.currentThreadTitle
                },
                threads = uiState.threads.map { thread ->
                    if (thread.id == threadId) thread.copy(title = normalizedName) else thread
                },
            )
            if (threadId == uiState.currentThreadId) {
                preferences.edit { putString("current_thread_title", normalizedName) }
            }
        }
    }

    fun toggleThreadPinned(threadId: String) {
        if (threadId in pinnedThreadIds) pinnedThreadIds.remove(threadId) else pinnedThreadIds.add(threadId)
        preferences.edit { putStringSet("pinned_threads", pinnedThreadIds.toSet()) }
        uiState = uiState.copy(
            threads = sortThreads(uiState.threads.map { thread ->
                if (thread.id == threadId) thread.copy(pinned = threadId in pinnedThreadIds) else thread
            })
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
        client.request(CodexProtocol.ClientRequest.THREAD_DELETE, JsonObject().apply {
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
            clearCurrentThreadSelection()
            uiState = uiState.copy(threads = uiState.threads.filterNot { it.id == threadId })
        } else {
            uiState = uiState.copy(threads = uiState.threads.filterNot { it.id == threadId })
        }
        if (pinnedThreadIds.remove(threadId)) {
            preferences.edit { putStringSet("pinned_threads", pinnedThreadIds.toSet()) }
        }
    }

    private fun clearCurrentThreadSelection() {
        val threadId = uiState.currentThreadId
        resumeGeneration++
        threadId?.let(::unsubscribeThread)
        CodexConnectionService.stop(getApplication())
        notifier.cancelPendingAction()
        preferences.edit {
            remove("current_thread_id")
            remove("current_thread_title")
        }
        uiState = uiState.copy(
            messages = emptyList(),
            currentThreadId = null,
            currentThreadTitle = "新会话",
            activeTurnId = null,
            busy = false,
            pendingActions = emptyList(),
            historyNextCursor = null,
            historyLoading = false,
        )
    }

    private fun unsubscribeThread(threadId: String) {
        if (threadId.isBlank()) return
        subscribedThreadIds.remove(threadId)
        if (!client.isReady()) return
        client.request(CodexProtocol.ClientRequest.THREAD_UNSUBSCRIBE, JsonObject().apply {
            addProperty("threadId", threadId)
        })
    }

    private fun unsubscribeAllThreads() {
        val threadIds = subscribedThreadIds.toList()
        threadIds.forEach(::unsubscribeThread)
        subscribedThreadIds.clear()
    }

    private fun validateWorkspace(path: String = uiState.cwd, onValid: () -> Unit = {}) {
        val normalizedPath = path.trim()
        if (!isValidWorkspacePath(normalizedPath)) {
            preferences.edit { putBoolean(WORKSPACE_ONBOARDING_COMPLETED, false) }
            uiState = uiState.copy(workspaceConfigured = false)
            addInfo("工作目录必须是非空的绝对路径。", error = true)
            return
        }
        if (!client.isReady()) return
        val generation = ++workspaceValidationGeneration
        uiState = uiState.copy(workspaceConfigured = false)
        client.request(CodexProtocol.ClientRequest.FS_GET_METADATA, JsonObject().apply {
            addProperty("path", normalizedPath)
        }, retryOnOverload = true) { response ->
            if (generation != workspaceValidationGeneration || normalizedPath != uiState.cwd) return@request
            val isDirectory = response.error == null && response.result?.bool("isDirectory") == true
            if (!isDirectory) {
                preferences.edit { putBoolean(WORKSPACE_ONBOARDING_COMPLETED, false) }
                uiState = uiState.copy(workspaceConfigured = false)
                val reason = response.error?.let(::rpcError) ?: "路径不是目录"
                addInfo("工作目录不可用：$reason。", error = true)
                return@request
            }
            preferences.edit { putBoolean(WORKSPACE_ONBOARDING_COMPLETED, true) }
            uiState = uiState.copy(workspaceConfigured = true)
            onValid()
        }
    }

    fun refreshThreads() {
        if (!client.isReady()) return
        val generation = ++threadListGeneration
        loadThreadsPage(
            cursor = null,
            accumulated = emptyList(),
            generation = generation,
            visitedCursors = emptySet(),
        )
    }

    /** Searches persisted thread names through the app-server's documented thread/list filter. */
    fun searchThreads(
        searchTerm: String,
        callback: (List<ThreadSummary>, String?) -> Unit,
    ) {
        val normalizedTerm = searchTerm.trim()
        if (normalizedTerm.isBlank()) {
            callback(emptyList(), null)
            return
        }
        if (!client.isReady()) {
            callback(emptyList(), "请先连接 App Server")
            return
        }
        val generation = ++threadSearchGeneration
        loadThreadSearchPage(
            searchTerm = normalizedTerm,
            cursor = null,
            accumulated = emptyList(),
            generation = generation,
            visitedCursors = emptySet(),
            callback = callback,
        )
    }

    private fun loadThreadSearchPage(
        searchTerm: String,
        cursor: String?,
        accumulated: List<ThreadSummary>,
        generation: Int,
        visitedCursors: Set<String>,
        callback: (List<ThreadSummary>, String?) -> Unit,
    ) {
        client.request(CodexProtocol.ClientRequest.THREAD_LIST, JsonObject().apply {
            addProperty("limit", 50)
            addProperty("sortKey", "updated_at")
            addProperty("sortDirection", "desc")
            add("sourceKinds", JsonArray().apply {
                add("cli")
                add("vscode")
                add("appServer")
            })
            addProperty("searchTerm", searchTerm)
            cursor?.let { addProperty("cursor", it) }
        }, retryOnOverload = true) { response ->
            if (generation != threadSearchGeneration) return@request
            response.error?.let {
                callback(emptyList(), rpcError(it))
                return@request
            }
            val data = response.result?.getAsJsonArray("data") ?: JsonArray()
            val threads = (accumulated + data.mapNotNull { parseThreadSummary(it.asJsonObject) })
                .distinctBy { it.id }
            val nextCursor = response.result?.string("nextCursor")
            if (nextCursor != null && nextCursor !in visitedCursors &&
                threads.size < MAX_THREAD_LIST_SIZE
            ) {
                loadThreadSearchPage(
                    searchTerm = searchTerm,
                    cursor = nextCursor,
                    accumulated = threads,
                    generation = generation,
                    visitedCursors = visitedCursors + nextCursor,
                    callback = callback,
                )
            } else {
                callback(sortThreads(threads), null)
            }
        }
    }

    private fun loadThreadsPage(
        cursor: String?,
        accumulated: List<ThreadSummary>,
        generation: Int,
        visitedCursors: Set<String>,
    ) {
        client.request(CodexProtocol.ClientRequest.THREAD_LIST, JsonObject().apply {
            addProperty("limit", 50)
            addProperty("sortKey", "updated_at")
            addProperty("sortDirection", "desc")
            add("sourceKinds", JsonArray().apply {
                add("cli")
                add("vscode")
                add("appServer")
            })
            cursor?.let { addProperty("cursor", it) }
        }, retryOnOverload = true) { response ->
            if (generation != threadListGeneration) return@request
            response.error?.let {
                addInfo("读取会话失败：${rpcError(it)}", error = true)
                return@request
            }
            val data = response.result?.getAsJsonArray("data") ?: JsonArray()
            val page = data.mapNotNull { parseThreadSummary(it.asJsonObject) }
            val threads = (accumulated + page).distinctBy { it.id }
            val nextCursor = response.result?.string("nextCursor")
            if (nextCursor != null && nextCursor !in visitedCursors &&
                threads.size < MAX_THREAD_LIST_SIZE
            ) {
                loadThreadsPage(
                    nextCursor,
                    threads,
                    generation,
                    visitedCursors + nextCursor,
                )
            } else {
                uiState = uiState.copy(threads = sortThreads(threads))
            }
        }
    }

    private fun sortThreads(threads: List<ThreadSummary>): List<ThreadSummary> =
        threads.sortedWith(
            compareByDescending<ThreadSummary> { it.pinned }
                .thenByDescending { it.updatedAt }
        )

    private fun parseThreadSummary(thread: JsonObject): ThreadSummary? {
        val id = thread.string("id") ?: return null
        val preview = thread.string("name")
            ?: thread.string("preview")
            ?: "未命名会话"
        return ThreadSummary(
            id = id,
            title = preview.lineSequence().firstOrNull()?.take(56) ?: "未命名会话",
            cwd = thread.string("cwd") ?: "",
            updatedAt = thread.long("updatedAt") ?: thread.long("createdAt") ?: 0L,
            active = thread.getAsJsonObject("status")?.string("type") == "active",
            pinned = id in pinnedThreadIds,
        )
    }

    fun refreshModels() {
        if (!client.isReady()) {
            uiState = uiState.copy(
                modelsLoading = false,
                modelsError = "连接 App Server 后才能读取模型列表",
            )
            return
        }
        val generation = ++modelListGeneration
        uiState = uiState.copy(modelsLoading = true, modelsError = null)
        loadModelsPage(
            cursor = null,
            accumulated = emptyList(),
            generation = generation,
            visitedCursors = emptySet(),
        )
    }

    private fun refreshConfigDefaults() {
        if (!client.isReady()) return
        client.request(CodexProtocol.ClientRequest.CONFIG_READ, JsonObject().apply {
            addProperty("cwd", uiState.cwd)
            addProperty("includeLayers", false)
        }, retryOnOverload = true) { response ->
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
        if (normalizedCwd.isBlank()) return
        val generation = ++skillsGeneration
        uiState = uiState.copy(skillsLoading = true, skillsError = null)
        client.request(CodexProtocol.ClientRequest.SKILLS_LIST, JsonObject().apply {
            add("cwds", JsonArray().apply { add(normalizedCwd) })
            addProperty("forceReload", forceReload)
        }, retryOnOverload = true) { response ->
            if (generation != skillsGeneration) return@request
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
            val reconcileSelection = normalizedCwd == uiState.cwd
            val retainedSkillPaths = if (reconcileSelection) {
                uiState.selectedSkillPaths.intersect(availablePaths)
            } else {
                uiState.selectedSkillPaths
            }
            if (reconcileSelection && retainedSkillPaths != uiState.selectedSkillPaths) {
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
        if (!client.isReady() || !uiState.workspaceConfigured) return
        resumeThread(threadId)
    }

    private fun resumeThread(threadId: String) {
        uiState.currentThreadId
            ?.takeIf { it != threadId }
            ?.let(::unsubscribeThread)
        val generation = ++resumeGeneration
        val previousTitle = uiState.currentThreadTitle
        uiState = uiState.copy(currentThreadTitle = "正在加载…")
        client.request(CodexProtocol.ClientRequest.THREAD_RESUME, JsonObject().apply {
            addProperty("threadId", threadId)
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
            val title = thread.string("name")
                ?: thread.string("preview")
                ?: "Codex 会话"
            val threadActive = thread.getAsJsonObject("status")?.string("type") == "active"
            val resolvedThreadId = thread.string("id") ?: threadId
            loadInitialThreadHistory(
                generation = generation,
                resolvedThreadId = resolvedThreadId,
                title = title,
                threadActive = threadActive,
                fallbackTurns = thread.getAsJsonArray("turns") ?: JsonArray(),
            )
        }
    }

    private fun loadInitialThreadHistory(
        generation: Int,
        resolvedThreadId: String,
        title: String,
        threadActive: Boolean,
        fallbackTurns: JsonArray,
    ) {
        client.request(CodexProtocol.ClientRequest.THREAD_TURNS_LIST, JsonObject().apply {
            addProperty("threadId", resolvedThreadId)
            addProperty("limit", HISTORY_PAGE_SIZE)
            addProperty("sortDirection", "desc")
            addProperty("itemsView", "full")
        }, retryOnOverload = true) { response ->
            if (generation != resumeGeneration) return@request
            val turns = if (response.error == null) {
                response.result?.getAsJsonArray("data") ?: fallbackTurns
            } else {
                addInfo("读取会话历史失败：${rpcError(response.error)}", error = true)
                fallbackTurns
            }
            val nextCursor = response.result?.string("nextCursor")
            val activeTurnId = turns
                .mapNotNull { it.takeIf(JsonElement::isJsonObject)?.asJsonObject }
                .firstOrNull { it.string("status") == "inProgress" }
                ?.string("id")
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
                    startConnectionService(true)
                } else {
                    CodexConnectionService.stop(getApplication())
                }
                subscribedThreadIds += resolvedThreadId
            }
        }
    }

    fun loadOlderHistory() {
        val threadId = uiState.currentThreadId ?: return
        val cursor = uiState.historyNextCursor ?: return
        if (uiState.historyLoading || !client.isReady()) return
        uiState = uiState.copy(historyLoading = true)
        client.request(CodexProtocol.ClientRequest.THREAD_TURNS_LIST, JsonObject().apply {
            addProperty("threadId", threadId)
            addProperty("cursor", cursor)
            addProperty("limit", HISTORY_PAGE_SIZE)
            addProperty("sortDirection", "desc")
            addProperty("itemsView", "full")
        }, retryOnOverload = true) { response ->
            if (uiState.currentThreadId != threadId) return@request
            response.error?.let {
                if (uiState.currentThreadId == threadId) {
                    uiState = uiState.copy(historyLoading = false)
                }
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

    fun sendPrompt(text: String): Boolean = sendPromptInput(PromptInput(text))

    fun sendPromptInput(input: PromptInput): Boolean {
        val normalizedInput = input.copy(
            text = input.text.trim(),
            files = input.files.distinctBy { it.path },
            skills = input.skills.distinctBy { it.path },
            images = input.images.distinctBy { it.dataUrl },
        )
        if (normalizedInput.text.isBlank() && normalizedInput.files.isEmpty() &&
            normalizedInput.skills.isEmpty() && normalizedInput.images.isEmpty()
        ) {
            return false
        }
        if (!client.isReady()) {
            addInfo("请先连接 Termux 中的 Codex App Server。", error = true)
            return false
        }
        if (!uiState.workspaceConfigured) {
            addInfo("请先选择工作目录。", error = true)
            return false
        }
        if (!isValidWorkspacePath(uiState.cwd)) {
            addInfo("工作目录必须是非空的绝对路径。", error = true)
            uiState = uiState.copy(workspaceConfigured = false)
            return false
        }
        if (normalizedInput.images.isNotEmpty() && !currentModelSupportsImages()) {
            addInfo("当前模型不支持图片输入，请切换模型后再发送。", error = true)
            return false
        }
        if (uiState.busy) return steerTurn(normalizedInput)
        val localMessageId = "local-${UUID.randomUUID()}"
        val displayText = promptDisplayText(normalizedInput)
        uiState = uiState.copy(
            messages = uiState.messages + UiMessage(
                id = localMessageId,
                kind = MessageKind.USER,
                text = displayText,
                running = true,
            ),
            busy = true,
        )
        startConnectionService(true)

        val threadId = uiState.currentThreadId
        if (threadId == null) {
            pendingPrompt = PendingPrompt(normalizedInput, localMessageId)
            startThread()
        } else {
            startTurn(threadId, normalizedInput, localMessageId)
        }
        return true
    }

    private fun steerTurn(input: PromptInput): Boolean {
        val threadId = uiState.currentThreadId ?: return false
        val turnId = uiState.activeTurnId ?: run {
            addInfo("任务仍在启动，暂时无法追加指令。")
            return false
        }
        val localMessageId = "local-steer-${UUID.randomUUID()}"
        uiState = uiState.copy(
            messages = uiState.messages + UiMessage(
                id = localMessageId,
                kind = MessageKind.USER,
                text = promptDisplayText(input),
                running = true,
            )
        )
        client.request(CodexProtocol.ClientRequest.TURN_STEER, JsonObject().apply {
            addProperty("threadId", threadId)
            addProperty("expectedTurnId", turnId)
            add("input", buildPromptItems(input))
        }) { response ->
            response.error?.let {
                removeMessage(localMessageId)
                addInfo("追加指令失败：${rpcError(it)}", error = true)
            }
        }
        return true
    }

    fun interruptTurn() {
        val threadId = uiState.currentThreadId ?: return
        val turnId = uiState.activeTurnId ?: run {
            addInfo("任务仍在启动，尚未取得可中断的 Turn ID。")
            return
        }
        client.request(CodexProtocol.ClientRequest.TURN_INTERRUPT, JsonObject().apply {
            addProperty("threadId", threadId)
            addProperty("turnId", turnId)
        }) { response ->
            response.error?.let { addInfo("中断失败：${rpcError(it)}", error = true) }
        }
    }

    fun compactCurrentThread(): Boolean {
        val threadId = uiState.currentThreadId ?: run {
            addInfo("请先开始一个会话，再压缩上下文。", error = true)
            return false
        }
        if (!client.isReady() || uiState.busy) {
            addInfo("当前无法压缩上下文，请等待任务结束。", error = true)
            return false
        }
        client.request(CodexProtocol.ClientRequest.THREAD_COMPACT_START, JsonObject().apply {
            addProperty("threadId", threadId)
        }) { response ->
            response.error?.let {
                addInfo("压缩上下文失败：${rpcError(it)}", error = true)
                return@request
            }
            addInfo("已开始压缩会话上下文，完成状态将随后更新。")
        }
        return true
    }

    fun setCurrentThreadGoal(objective: String): Boolean {
        val normalizedObjective = objective.trim()
        if (normalizedObjective.isEmpty()) {
            addInfo("请输入目标内容，例如：/goal 完成登录模块重构", error = true)
            return false
        }
        if (normalizedObjective.length > MAX_GOAL_CHARS) {
            addInfo("会话目标不能超过 $MAX_GOAL_CHARS 个字符。", error = true)
            return false
        }
        val threadId = uiState.currentThreadId ?: run {
            addInfo("请先开始一个会话，再设置目标。", error = true)
            return false
        }
        if (!client.isReady()) return false
        client.request(CodexProtocol.ClientRequest.THREAD_GOAL_SET, JsonObject().apply {
            addProperty("threadId", threadId)
            addProperty("objective", normalizedObjective)
            addProperty("status", "active")
        }) { response ->
            response.error?.let {
                addInfo("设置目标失败：${rpcError(it)}", error = true)
                return@request
            }
            addInfo("会话目标已设置：$normalizedObjective")
        }
        return true
    }

    fun clearCurrentThreadGoal(): Boolean {
        val threadId = uiState.currentThreadId ?: run {
            addInfo("当前没有可清除目标的会话。", error = true)
            return false
        }
        if (!client.isReady()) return false
        client.request(CodexProtocol.ClientRequest.THREAD_GOAL_CLEAR, JsonObject().apply {
            addProperty("threadId", threadId)
        }) { response ->
            response.error?.let {
                addInfo("清除目标失败：${rpcError(it)}", error = true)
                return@request
            }
            addInfo("会话目标已清除。")
        }
        return true
    }

    fun resolveApproval(decision: String) {
        val pending = uiState.pendingAction ?: return
        val selectedDecision = pending.decisions.firstOrNull { it.key == decision }
        when (pending.kind) {
            PendingKind.COMMAND, PendingKind.FILE_CHANGE -> {
                client.respond(pending.requestId, JsonObject().apply {
                    add(
                        "decision",
                        normalizeApprovalDecision(
                            pending.approvalProtocol,
                            selectedDecision?.payload ?: JsonPrimitive(decision),
                        ),
                    )
                })
            }

            PendingKind.PERMISSION -> {
                if (decision == "decline" || decision == "cancel") {
                    client.respond(pending.requestId, JsonObject().apply {
                        add("permissions", JsonObject())
                        addProperty("scope", "turn")
                    })
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
                    add(
                        "content",
                        if (decision == "accept") JsonObject() else JsonNull.INSTANCE,
                    )
                })
            }

            PendingKind.USER_INPUT -> Unit
        }
        removePending(pending.requestId)
    }

    fun answerQuestions(answers: Map<String, String>) {
        val pending = uiState.pendingAction ?: return
        val typedAnswers = mutableMapOf<String, JsonElement>()
        if (pending.kind == PendingKind.MCP_ELICITATION) {
            pending.questions.forEach { question ->
                val rawValue = answers[question.id].orEmpty()
                if (rawValue.isBlank() && !question.required) return@forEach
                val typed = encodeInputValue(rawValue, question.valueType)
                if (typed == null) {
                    addInfo("“${question.header}”的输入格式无效。", error = true)
                    return
                }
                typedAnswers[question.id] = typed
            }
        }
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
                        val value = typedAnswers[question.id] ?: return@forEach
                        if (answers[question.id].orEmpty().isBlank() && !question.required) return@forEach
                        add(question.id, value)
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
                    add("decision", normalizeApprovalDecision(pending.approvalProtocol, decline))
                },
            )

            PendingKind.USER_INPUT -> client.respond(pending.requestId, JsonObject().apply {
                add("answers", JsonObject())
            })

            PendingKind.MCP_ELICITATION -> client.respond(pending.requestId, JsonObject().apply {
                addProperty("action", "cancel")
                add("content", JsonNull.INSTANCE)
            })

            PendingKind.PERMISSION -> client.respond(pending.requestId, JsonObject().apply {
                add("permissions", JsonObject())
                addProperty("scope", "turn")
            })
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
        uiState = uiState.copy(
            connectionStatus = ConnectionStatus.CONNECTED,
            connectionMessage = buildString {
                append(if (platform.isBlank()) "已连接 Codex" else "已连接 · $platform")
                userAgent.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
            },
        )
        val requestedThreadId = pendingNotificationThreadId
        pendingNotificationThreadId = null
        val currentThreadId = requestedThreadId ?: uiState.currentThreadId
        refreshThreads()
        refreshModels()
        validateWorkspace {
            if (currentThreadId != null) resumeThread(currentThreadId)
            refreshConfigDefaults()
            refreshSkills()
        }
        refreshMcpStatus()
    }

    override fun onDisconnected(reason: String) {
        CodexConnectionService.stop(getApplication())
        pendingDeltas.clear()
        fileChangePatches.clear()
        turnDiffs.clear()
        subscribedThreadIds.clear()
        workspaceValidationGeneration++
        uiState = uiState.copy(
            connectionStatus = ConnectionStatus.DISCONNECTED,
            connectionMessage = readableConnectionError(reason),
            busy = false,
            activeTurnId = null,
            pendingActions = emptyList(),
            modelsLoading = false,
            skillsLoading = false,
            mcpLoading = false,
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
        if (threadScoped && eventThreadId != uiState.currentThreadId &&
            method in BACKGROUND_STATUS_EVENTS
        ) {
            val active = when (method) {
                CodexProtocol.Notification.TURN_STARTED -> true
                CodexProtocol.Notification.TURN_COMPLETED -> false
                else -> params.getAsJsonObject("status")?.string("type") == "active"
            }
            uiState = uiState.copy(
                threads = uiState.threads.map { thread ->
                    if (thread.id == eventThreadId) thread.copy(active = active) else thread
                }
            )
            if (!active) refreshThreads()
            return
        }
        if (threadScoped && eventThreadId != uiState.currentThreadId && method !in GLOBAL_THREAD_EVENTS) {
            return
        }
        when (method) {
            CodexProtocol.Notification.TURN_STARTED -> {
                val turn = params.getAsJsonObject("turn")
                uiState = uiState.copy(
                    activeTurnId = turn?.string("id") ?: uiState.activeTurnId,
                    busy = true,
                )
                startConnectionService(true)
            }

            CodexProtocol.Notification.TURN_COMPLETED -> {
                flushPendingDeltas()
                val turn = params.getAsJsonObject("turn")
                val status = turn?.string("status") ?: "completed"
                uiState = uiState.copy(
                    activeTurnId = null,
                    busy = false,
                    messages = uiState.messages.map { message ->
                        if (message.kind == MessageKind.USER && message.running) {
                            message.copy(running = false)
                        } else {
                            message
                        }
                    },
                )
                CodexConnectionService.stop(getApplication())
                if (status == "failed") {
                    addInfo("任务失败：${turn?.get("error")?.let(prettyGson::toJson) ?: "未知错误"}", true)
                }
                fileChangePatches.clear()
                turnDiffs.clear()
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

            CodexProtocol.Notification.FILE_PATCH_UPDATED -> handleFileChangePatch(params)

            CodexProtocol.Notification.TURN_DIFF_UPDATED -> {
                val turnId = params.string("turnId") ?: return
                params.string("diff")?.let { diff ->
                    turnDiffs[turnId] = boundedText(diff, MAX_PATCH_CHARS)
                    uiState = uiState.copy(
                        pendingActions = uiState.pendingActions.map { pending ->
                            if (pending.kind == PendingKind.FILE_CHANGE &&
                                pending.rawParams.string("turnId") == turnId
                            ) {
                                pending.copy(detail = fileApprovalDetail(pending.rawParams))
                            } else {
                                pending
                            }
                        },
                    )
                }
            }

            "item/commandExecution/outputDelta", "item/fileChange/outputDelta" -> {
                appendDelta(
                    id = params.string("itemId") ?: return,
                    kind = MessageKind.TOOL,
                    title = if (method.contains("command")) "正在执行命令" else "正在修改文件",
                    delta = params.string("delta") ?: "",
                )
            }

            "item/started" -> handleItem(params.getAsJsonObject("item"), running = true)
            "item/completed" -> handleItem(params.getAsJsonObject("item"), running = false)
            "skills/changed" -> refreshSkills(forceReload = true)
            CodexProtocol.Notification.MCP_SERVER_STATUS_UPDATED -> {
                val name = params.string("name") ?: return
                val status = params.string("status") ?: return
                val error = params.string("error")
                val current = uiState.mcpServers.firstOrNull { it.name == name }
                val updated = (uiState.mcpServers.filterNot { it.name == name } +
                    (current?.copy(startupStatus = status, error = error)
                        ?: McpServerStatus(
                            name = name,
                            displayName = name,
                            startupStatus = status,
                            error = error,
                        )))
                    .sortedBy { it.displayName.lowercase() }
                uiState = uiState.copy(mcpServers = updated)
            }
            "thread/status/changed" -> {
                val status = params.getAsJsonObject("status")?.string("type")
                uiState = uiState.copy(
                    busy = status == "active",
                    activeTurnId = if (status == "active") uiState.activeTurnId else null,
                )
                if (status == "active") {
                    startConnectionService(true)
                } else {
                    CodexConnectionService.stop(getApplication())
                }
            }

            "thread/deleted" -> {
                params.string("threadId")?.let(::removeDeletedThread)
            }

            "thread/archived" -> {
                val threadId = params.string("threadId")
                if (threadId != null && threadId == uiState.currentThreadId) {
                    clearCurrentThreadSelection()
                }
                refreshThreads()
            }

            "thread/unarchived" -> refreshThreads()

            CodexProtocol.Notification.SERVER_REQUEST_RESOLVED -> {
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
            CodexProtocol.ServerRequest.COMMAND_APPROVAL -> PendingAction(
                requestId = request.id,
                kind = PendingKind.COMMAND,
                title = if (request.params.get("networkApprovalContext")?.isJsonObject == true) {
                    "允许网络访问？"
                } else {
                    "允许执行命令？"
                },
                detail = commandApprovalDetail(request.params),
                rawParams = request.params,
                threadId = request.params.string("threadId"),
                decisions = parseApprovalDecisions(request.params, defaultApprovalDecisions()),
                approvalProtocol = ApprovalProtocol.CURRENT,
            )

            CodexProtocol.ServerRequest.FILE_CHANGE_APPROVAL -> PendingAction(
                requestId = request.id,
                kind = PendingKind.FILE_CHANGE,
                title = "允许修改文件？",
                detail = fileApprovalDetail(request.params),
                rawParams = request.params,
                threadId = request.params.string("threadId"),
                decisions = parseApprovalDecisions(request.params, defaultApprovalDecisions()),
                approvalProtocol = ApprovalProtocol.CURRENT,
            )

            CodexProtocol.ServerRequest.PERMISSIONS_APPROVAL -> PendingAction(
                requestId = request.id,
                kind = PendingKind.PERMISSION,
                title = "授予额外权限？",
                detail = buildList {
                    request.params.string("reason")?.let(::add)
                    request.params.string("environmentId")?.let { add("环境：$it") }
                    request.params.string("cwd")?.let { add("目录：$it") }
                    request.params.get("permissions")?.takeUnless { it.isJsonNull }?.let {
                        add("请求权限：${prettyGson.toJson(it)}")
                    }
                }.joinToString("\n\n"),
                rawParams = request.params,
                threadId = request.params.string("threadId"),
                decisions = permissionDecisions(),
            )

            CodexProtocol.ServerRequest.USER_INPUT -> PendingAction(
                requestId = request.id,
                kind = PendingKind.USER_INPUT,
                title = "Codex 需要你的选择",
                detail = "请回答后继续任务。",
                rawParams = request.params,
                questions = parseQuestions(request.params),
                threadId = request.params.string("threadId"),
                autoResolutionMs = request.params.long("autoResolutionMs"),
            )

            CodexProtocol.ServerRequest.MCP_ELICITATION -> {
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

            CodexProtocol.ServerRequest.LEGACY_PATCH_APPROVAL -> PendingAction(
                requestId = request.id,
                kind = PendingKind.FILE_CHANGE,
                title = "允许应用补丁？",
                detail = legacyFileApprovalDetail(request.params),
                rawParams = request.params,
                threadId = request.params.string("conversationId"),
                decisions = legacyApprovalDecisions(),
                approvalProtocol = ApprovalProtocol.LEGACY,
            )

            CodexProtocol.ServerRequest.LEGACY_COMMAND_APPROVAL -> PendingAction(
                requestId = request.id,
                kind = PendingKind.COMMAND,
                title = "允许执行命令？",
                detail = request.params.string("command") ?: prettyGson.toJson(request.params),
                rawParams = request.params,
                threadId = request.params.string("conversationId"),
                decisions = legacyApprovalDecisions(),
                approvalProtocol = ApprovalProtocol.LEGACY,
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
        unsubscribeAllThreads()
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
        client.request(CodexProtocol.ClientRequest.THREAD_START, params) { response ->
            response.error?.let {
                pendingPrompt?.messageId?.let(::removeMessage)
                pendingPrompt = null
                uiState = uiState.copy(busy = false)
                CodexConnectionService.stop(getApplication())
                addInfo("新建会话失败：${rpcError(it)}", error = true)
                return@request
            }
            val thread = response.result?.getAsJsonObject("thread")
            val threadId = thread?.string("id")
            if (threadId == null) {
                pendingPrompt?.messageId?.let(::removeMessage)
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
            subscribedThreadIds += threadId
            pendingPrompt?.let { pending ->
                pendingPrompt = null
                startTurn(threadId, pending.input, pending.messageId)
            }
            refreshThreads()
        }
    }

    private fun loadModelsPage(
        cursor: String?,
        accumulated: List<CodexModel>,
        generation: Int,
        visitedCursors: Set<String>,
    ) {
        client.request(CodexProtocol.ClientRequest.MODEL_LIST, JsonObject().apply {
            addProperty("limit", 100)
            addProperty("includeHidden", false)
            cursor?.let { addProperty("cursor", it) }
        }, retryOnOverload = true) { response ->
            if (generation != modelListGeneration) return@request
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
                    hidden = item.bool("hidden") == true,
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
                    inputModalities = item.get("inputModalities")
                        ?.takeIf { it.isJsonArray }
                        ?.asJsonArray
                        ?.mapNotNull { runCatching { it.asString }.getOrNull() }
                        ?: DEFAULT_INPUT_MODALITIES,
                )
            }.orEmpty()
            val models = (accumulated + page)
                .filterNot { it.hidden }
                .distinctBy { it.model }
            val nextCursor = result.string("nextCursor")
            if (nextCursor != null && nextCursor !in visitedCursors) {
                loadModelsPage(
                    nextCursor,
                    models,
                    generation,
                    visitedCursors + nextCursor,
                )
            } else {
                uiState = uiState.copy(
                    availableModels = models,
                    modelsLoading = false,
                    modelsError = if (models.isEmpty()) "App Server 未返回可用模型" else null,
                )
            }
        }
    }

    private fun startTurn(threadId: String, input: PromptInput, messageId: String) {
        client.request(CodexProtocol.ClientRequest.TURN_START, JsonObject().apply {
            addProperty("threadId", threadId)
            if (uiState.cwd.isNotBlank()) addProperty("cwd", uiState.cwd)
            if (uiState.model.isNotBlank()) {
                addProperty("model", uiState.model)
            }
            if (uiState.reasoningEffort.isNotBlank()) {
                addProperty("effort", uiState.reasoningEffort)
            }
            add("input", buildPromptItems(input))
        }) { response ->
            response.error?.let {
                uiState = uiState.copy(busy = false, activeTurnId = null)
                CodexConnectionService.stop(getApplication())
                removeMessage(messageId)
                addInfo("发送失败：${rpcError(it)}", error = true)
                return@request
            }
            val turnId = response.result?.getAsJsonObject("turn")?.string("id")
            uiState = uiState.copy(activeTurnId = turnId, busy = true)
        }
    }

    private fun currentModelSupportsImages(): Boolean {
        val model = uiState.availableModels.firstOrNull { it.model == uiState.model }
            ?: uiState.availableModels.firstOrNull { it.model == uiState.configModel }
            ?: uiState.availableModels.firstOrNull { it.isDefault }
        return model?.supportsInputModality("image") ?: true
    }

    private fun buildPromptItems(input: PromptInput): JsonArray = JsonArray().apply {
        input.text.takeIf { it.isNotBlank() }?.let { prompt ->
            add(JsonObject().apply {
                addProperty("type", "text")
                addProperty("text", prompt)
            })
        }
        input.images.forEach { image ->
            add(JsonObject().apply {
                addProperty("type", "image")
                addProperty("url", image.dataUrl)
            })
        }
        input.files.distinctBy { it.path }.forEach { file ->
            add(JsonObject().apply {
                addProperty("type", "mention")
                addProperty("name", file.name)
                addProperty("path", file.path)
            })
        }
        input.skills.distinctBy { it.path }.forEach { skill ->
            add(JsonObject().apply {
                addProperty("type", "skill")
                addProperty("name", skill.name)
                addProperty("path", skill.path)
            })
        }
    }

    private fun promptDisplayText(input: PromptInput): String = buildString {
        append(input.text)
        val attachments = buildList {
            input.images.forEach { add("图片：${it.name}") }
            input.files.forEach { add("文件：${it.name}") }
            input.skills.forEach { add("Skill：${it.displayName}") }
        }
        if (attachments.isNotEmpty()) {
            if (isNotBlank()) append("\n\n")
            append(attachments.joinToString(" · "))
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
                    text = boundedText(
                        current.text + buffered.text,
                        MAX_STREAMED_MESSAGE_CHARS,
                    ),
                    running = true,
                )
            } else {
                messages += UiMessage(
                    id = id,
                    kind = buffered.kind,
                    title = buffered.title,
                    text = boundedText(buffered.text.toString(), MAX_STREAMED_MESSAGE_CHARS),
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
                text = boundedText(item.string("text").orEmpty(), MAX_MESSAGE_CHARS),
                running = running,
                outcome = outcome,
            )

            "plan" -> UiMessage(
                id = id,
                kind = MessageKind.TOOL,
                title = "计划",
                text = boundedText(item.string("text").orEmpty(), MAX_TOOL_OUTPUT_CHARS),
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
                text = boundedText(buildString {
                    append(item.string("command") ?: "")
                    item.string("aggregatedOutput")?.takeIf { it.isNotBlank() }?.let {
                        append("\n\n").append(it)
                    }
                    item.long("exitCode")
                        ?.takeIf { it != 0L }
                        ?.let { append("\n\n退出码：").append(it) }
                }, MAX_TOOL_OUTPUT_CHARS),
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
                text = boundedText(
                    formatFileChanges(item.getAsJsonArray("changes"))
                        .ifBlank { "等待修改信息…" },
                    MAX_PATCH_CHARS,
                ),
                running = running,
                outcome = outcome,
            )

            "mcpToolCall" -> UiMessage(
                id = id,
                kind = MessageKind.TOOL,
                title = "MCP · ${item.string("server") ?: "tool"}",
                text = boundedText(buildString {
                    append(item.string("tool") ?: "工具调用")
                    item.get("arguments")?.let { append("\n\n").append(prettyGson.toJson(it)) }
                    item.get("result")?.takeUnless { it.isJsonNull }?.let {
                        append("\n\n").append(prettyGson.toJson(it))
                    }
                }, MAX_TOOL_OUTPUT_CHARS),
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

            else -> supplementalItemMessage(item, id, running, outcome) ?: return
        }
        upsertMessage(message)
    }

    private fun supplementalItemMessage(
        item: JsonObject,
        id: String,
        running: Boolean,
        outcome: MessageOutcome,
    ): UiMessage? = when (item.string("type")) {
        "reasoning" -> structuredItemText(
            item,
            listOf("summary" to null, "content" to null),
        ).takeIf { it.isNotBlank() }?.let { text ->
            UiMessage(
                id = id,
                kind = MessageKind.INFO,
                title = "思考摘要",
                text = text,
                running = running,
                outcome = outcome,
            )
        }

        "dynamicToolCall" -> UiMessage(
            id = id,
            kind = MessageKind.TOOL,
            title = "动态工具调用",
            text = structuredItemText(
                item,
                listOf(
                    "tool" to "工具",
                    "arguments" to "参数",
                    "contentItems" to "内容",
                    "success" to "成功",
                    "durationMs" to "耗时（毫秒）",
                ),
            ).ifBlank { "动态工具调用" },
            running = running,
            outcome = outcome,
        )

        "collabToolCall" -> UiMessage(
            id = id,
            kind = MessageKind.TOOL,
            title = "协作工具调用",
            text = structuredItemText(
                item,
                listOf(
                    "tool" to "工具",
                    "prompt" to "任务",
                    "senderThreadId" to "发起会话",
                    "receiverThreadId" to "目标会话",
                    "newThreadId" to "新会话",
                    "agentStatus" to "Agent 状态",
                ),
            ).ifBlank { "协作工具调用" },
            running = running,
            outcome = outcome,
        )

        "imageView" -> UiMessage(
            id = id,
            kind = MessageKind.TOOL,
            title = "查看图片",
            text = structuredItemText(
                item,
                listOf("path" to null),
            ).ifBlank { "已查看图片" },
            running = running,
            outcome = outcome,
        )

        "enteredReviewMode" -> UiMessage(
            id = id,
            kind = MessageKind.INFO,
            title = "审查模式",
            text = structuredItemText(item, listOf("review" to null))
                .ifBlank { "已进入审查模式" },
            running = running,
            outcome = outcome,
        )

        "exitedReviewMode" -> UiMessage(
            id = id,
            kind = MessageKind.INFO,
            title = "审查模式",
            text = structuredItemText(item, listOf("review" to null))
                .ifBlank { "已退出审查模式" },
            running = running,
            outcome = outcome,
        )

        "contextCompaction" -> UiMessage(
            id = id,
            kind = MessageKind.INFO,
            title = if (running) "正在压缩上下文" else "上下文压缩完成",
            text = if (running) "Codex 正在压缩会话上下文。" else "会话上下文已压缩。",
            running = running,
            outcome = outcome,
        )

        else -> null
    }

    private fun structuredItemText(
        item: JsonObject,
        fields: List<Pair<String, String?>>,
    ): String = buildList {
        fields.forEach { (field, label) ->
            val element = item.get(field)?.takeUnless { it.isJsonNull } ?: return@forEach
            if ((element.isJsonArray && element.asJsonArray.size() == 0) ||
                (element.isJsonObject && element.asJsonObject.size() == 0)
            ) {
                return@forEach
            }
            val value = if (element.isJsonPrimitive && element.asJsonPrimitive.isString) {
                element.asString
            } else {
                prettyGson.toJson(element)
            }
            if (value.isBlank()) return@forEach
            add(if (label == null) value else "$label：$value")
        }
    }.distinct().joinToString("\n\n").let { boundedText(it, MAX_TOOL_OUTPUT_CHARS) }

    private fun upsertMessage(message: UiMessage) {
        val index = uiState.messages.indexOfLast { it.id == message.id }
        val messages = if (index >= 0) {
            uiState.messages.toMutableList().also { it[index] = message }
        } else {
            uiState.messages + message
        }
        uiState = uiState.copy(messages = messages)
    }

    private fun removeMessage(id: String) {
        uiState = uiState.copy(messages = uiState.messages.filterNot { it.id == id })
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

    private fun startConnectionService(busy: Boolean) {
        if (CodexConnectionService.start(getApplication(), busy) || foregroundServiceWarningShown) {
            return
        }
        foregroundServiceWarningShown = true
        addInfo("系统限制了后台前台服务；请保持应用可见或允许后台运行。", error = true)
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
                            ?.mapNotNull(::formatUserMessageContent)
                            ?.joinToString("\n")
                            .orEmpty()
                        if (text.isNotBlank()) {
                            messages += UiMessage(
                                id,
                                MessageKind.USER,
                                boundedText(text, MAX_MESSAGE_CHARS),
                            )
                        }
                    }

                    "agentMessage" -> item.string("text")?.takeIf { it.isNotBlank() }?.let {
                        messages += UiMessage(
                            id,
                            MessageKind.ASSISTANT,
                            boundedText(it, MAX_MESSAGE_CHARS),
                        )
                    }

                    "plan" -> item.string("text")?.takeIf { it.isNotBlank() }?.let {
                        messages += UiMessage(
                            id,
                            MessageKind.TOOL,
                            boundedText(it, MAX_TOOL_OUTPUT_CHARS),
                            "计划",
                        )
                    }

                    "commandExecution" -> {
                        val exitCode = item.long("exitCode") ?: 0L
                        val status = item.string("status")
                        val outcome = when {
                            status == "failed" || exitCode != 0L -> MessageOutcome.FAILED
                            status == "declined" -> MessageOutcome.DECLINED
                            else -> MessageOutcome.SUCCESS
                        }
                        messages += UiMessage(
                            id = id,
                            kind = MessageKind.TOOL,
                            title = when (outcome) {
                                MessageOutcome.FAILED -> "命令执行失败"
                                MessageOutcome.DECLINED -> "命令已拒绝"
                                else -> "命令执行完成"
                            },
                            text = boundedText(buildString {
                                append(item.string("command") ?: "")
                                item.string("aggregatedOutput")?.takeIf { it.isNotBlank() }?.let {
                                    append("\n\n").append(it)
                                }
                                exitCode.takeIf { it != 0L }?.let {
                                    append("\n\n退出码：").append(it)
                                }
                            }, MAX_TOOL_OUTPUT_CHARS),
                            outcome = outcome,
                        )
                    }

                    "fileChange" -> {
                        val outcome = when (item.string("status")) {
                            "failed" -> MessageOutcome.FAILED
                            "declined" -> MessageOutcome.DECLINED
                            else -> MessageOutcome.SUCCESS
                        }
                        messages += UiMessage(
                            id = id,
                            kind = MessageKind.TOOL,
                            title = when (outcome) {
                                MessageOutcome.FAILED -> "文件修改失败"
                                MessageOutcome.DECLINED -> "文件修改已拒绝"
                                else -> "文件修改完成"
                            },
                            text = boundedText(
                                formatFileChanges(item.getAsJsonArray("changes")),
                                MAX_PATCH_CHARS,
                            ),
                            outcome = outcome,
                        )
                    }

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

                    else -> {
                        val outcome = when (item.string("status")) {
                            "failed" -> MessageOutcome.FAILED
                            "declined" -> MessageOutcome.DECLINED
                            else -> MessageOutcome.SUCCESS
                        }
                        supplementalItemMessage(item, id, running = false, outcome)?.let(messages::add)
                    }
                }
            }
        }
        return messages
    }

    private fun formatUserMessageContent(element: JsonElement): String? {
        val content = element.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        return when (content.string("type")) {
            "text" -> content.string("text")
            "image" -> "[图片]"
            "localImage" -> content.string("path")
                ?.takeIf { it.isNotBlank() }
                ?.let { "[本地图片] $it" }
                ?: "[本地图片]"
            else -> null
        }
    }

    private fun handleFileChangePatch(params: JsonObject) {
        val itemId = params.string("itemId") ?: return
        val patch = formatFileChanges(params.getAsJsonArray("changes"))
        if (patch.isBlank()) return
        fileChangePatches[itemId] = patch
        uiState = uiState.copy(
            pendingActions = uiState.pendingActions.map { pending ->
                if (pending.kind == PendingKind.FILE_CHANGE &&
                    pending.rawParams.string("itemId") == itemId
                ) {
                    pending.copy(detail = fileApprovalDetail(pending.rawParams))
                } else {
                    pending
                }
            },
        )
        upsertMessage(
            UiMessage(
                id = itemId,
                kind = MessageKind.TOOL,
                title = "待审批的文件修改",
                text = patch,
                running = true,
            )
        )
    }

    private fun fileApprovalDetail(params: JsonObject): String {
        val itemPatch = params.string("itemId")?.let(fileChangePatches::get)
        val turnPatch = params.string("turnId")?.let(turnDiffs::get)
        val context = buildList {
            params.string("reason")?.let(::add)
            params.string("grantRoot")?.let { add("授权根目录：$it") }
            params.get("additionalPermissions")?.takeUnless { it.isJsonNull }?.let {
                add("额外权限：${prettyGson.toJson(it)}")
            }
        }.joinToString("\n\n").ifBlank { "Codex 请求应用文件修改。" }
        return approvalDetail(context, itemPatch ?: turnPatch)
    }

    private fun commandApprovalDetail(params: JsonObject): String {
        val context = buildList {
            val networkContext = params.get("networkApprovalContext")
                ?.takeIf { it.isJsonObject }
                ?.asJsonObject
            networkContext?.string("host")?.let { add("网络主机：$it") }
            networkContext?.string("protocol")?.let { add("网络协议：$it") }
            params.string("command")?.takeIf { it.isNotBlank() }?.let {
                add(if (networkContext == null) it else "命令上下文：$it")
            }
            params.string("cwd")?.let { add("目录：$it") }
            params.string("reason")?.let { add("原因：$it") }
            params.get("additionalPermissions")?.takeUnless { it.isJsonNull }?.let {
                add("额外权限：${prettyGson.toJson(it)}")
            }
        }
        return context.joinToString("\n\n").ifBlank { prettyGson.toJson(params) }
    }

    private fun legacyFileApprovalDetail(params: JsonObject): String {
        val changes = params.get("fileChanges")
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?.entrySet()
            ?.joinToString("\n\n") { (path, changeElement) ->
                val change = changeElement.takeIf { it.isJsonObject }?.asJsonObject
                val type = change?.string("type") ?: "change"
                val content = change?.string("unified_diff")
                    ?: change?.string("content")
                    ?: prettyGson.toJson(changeElement)
                "$type · $path\n$content"
            }
        return approvalDetail(
            params.string("reason") ?: "Codex 请求应用补丁。",
            changes?.let { boundedText(it, MAX_PATCH_CHARS) },
        )
    }

    private fun formatFileChanges(changes: JsonArray?): String {
        return changes?.mapNotNull { element ->
            val change = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val path = change.string("path") ?: return@mapNotNull null
            val kind = change.string("kind") ?: "update"
            val diff = change.string("diff").orEmpty()
            "$kind · $path\n$diff"
        }.orEmpty().joinToString("\n\n").let { boundedText(it, MAX_PATCH_CHARS) }
    }

    private fun approvalDetail(reason: String, patch: String?): String {
        return if (patch.isNullOrBlank()) reason else "$reason\n\n$patch"
    }

    private fun boundedText(text: String, maxChars: Int): String {
        if (text.length <= maxChars) return text
        return text.take(maxChars) + "\n\n[内容过长，已在客户端截断]"
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
                    ?.mapNotNull { optionElement ->
                        val option = optionElement.takeIf { it.isJsonObject }?.asJsonObject
                            ?: return@mapNotNull null
                        val label = option.string("label") ?: return@mapNotNull null
                        InputOption(label, option.string("description").orEmpty())
                    }
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
        return properties.entrySet().mapNotNull { (id, element) ->
            val property = element.takeIf { it.isJsonObject }?.asJsonObject
                ?: return@mapNotNull null
            val options = property.getAsJsonArray("enum")
                ?.mapNotNull { option ->
                    runCatching { InputOption(option.asString) }.getOrNull()
                }
                ?: property.getAsJsonArray("oneOf")
                    ?.mapNotNull { optionElement ->
                        val option = optionElement.takeIf { it.isJsonObject }?.asJsonObject
                            ?: return@mapNotNull null
                        val label = option.string("const") ?: return@mapNotNull null
                        InputOption(label, option.string("description").orEmpty())
                    }
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

    private fun parseApprovalDecisions(
        params: JsonObject,
        fallback: List<PendingDecision>,
    ): List<PendingDecision> {
        val available = params.getAsJsonArray("availableDecisions")
            ?.mapNotNull(::decisionFromJson)
            .orEmpty()
        return available.ifEmpty { fallback }
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

    fun readDirectories(
        path: String,
        callback: (List<RemoteDirectory>, String?) -> Unit,
    ) {
        val normalizedPath = path.trim()
        if (!isValidWorkspacePath(normalizedPath)) {
            callback(emptyList(), "路径必须是非空的绝对路径")
            return
        }
        if (!client.isReady()) {
            callback(emptyList(), "请先连接 App Server")
            return
        }
        client.request(
            CodexProtocol.ClientRequest.FS_READ_DIRECTORY,
            JsonObject().apply { addProperty("path", normalizedPath) },
            retryOnOverload = true,
        ) { response ->
            response.error?.let {
                callback(emptyList(), rpcError(it))
                return@request
            }
            val base = normalizedPath.trimEnd('/').ifBlank { "/" }
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

    fun searchWorkspaceFiles(
        query: String,
        callback: (List<PromptFileReference>, String?) -> Unit,
    ) {
        if (!client.isReady()) {
            callback(emptyList(), "请先连接 App Server")
            return
        }
        client.request(
            CodexProtocol.ClientRequest.FUZZY_FILE_SEARCH,
            JsonObject().apply {
                addProperty("query", query.trim())
                add("roots", JsonArray().apply { add(uiState.cwd) })
            },
            retryOnOverload = true,
        ) { response ->
            response.error?.let {
                callback(emptyList(), rpcError(it))
                return@request
            }
            val files = response.result?.getAsJsonArray("files")
                ?.mapNotNull { element ->
                    val item = element.asJsonObject
                    if (item.string("match_type") == "directory") return@mapNotNull null
                    val name = item.string("file_name") ?: return@mapNotNull null
                    val rawPath = item.string("path") ?: return@mapNotNull null
                    val root = item.string("root") ?: uiState.cwd
                    val path = if (rawPath.startsWith('/')) rawPath else {
                        "${root.trimEnd('/')}/$rawPath"
                    }
                    PromptFileReference(name = name, path = path)
                }
                ?.distinctBy { it.path }
                ?.take(MAX_FILE_SEARCH_RESULTS)
                .orEmpty()
            callback(files, null)
        }
    }

    fun refreshMcpStatus() {
        if (!client.isReady()) {
            uiState = uiState.copy(mcpLoading = false, mcpError = "请先连接 App Server")
            return
        }
        val generation = ++mcpStatusGeneration
        uiState = uiState.copy(mcpLoading = true, mcpError = null)
        loadMcpStatusPage(
            cursor = null,
            accumulated = emptyList(),
            generation = generation,
            visitedCursors = emptySet(),
        )
    }

    private fun loadMcpStatusPage(
        cursor: String?,
        accumulated: List<McpServerStatus>,
        generation: Int,
        visitedCursors: Set<String>,
    ) {
        client.request(CodexProtocol.ClientRequest.MCP_SERVER_STATUS_LIST, JsonObject().apply {
            addProperty("limit", 50)
            addProperty("detail", "toolsAndAuthOnly")
            uiState.currentThreadId?.let { addProperty("threadId", it) }
            cursor?.let { addProperty("cursor", it) }
        }, retryOnOverload = true) { response ->
            if (generation != mcpStatusGeneration) return@request
            response.error?.let {
                uiState = uiState.copy(mcpLoading = false, mcpError = rpcError(it))
                return@request
            }
            val existing = uiState.mcpServers.associateBy { it.name }
            val page = response.result?.getAsJsonArray("data")
                ?.mapNotNull { element ->
                    val item = element.asJsonObject
                    val name = item.string("name") ?: return@mapNotNull null
                    val serverInfo = item.getAsJsonObject("serverInfo")
                    McpServerStatus(
                        name = name,
                        displayName = serverInfo?.string("title")
                            ?: serverInfo?.string("name")
                            ?: name,
                        startupStatus = existing[name]?.startupStatus,
                        authStatus = item.string("authStatus") ?: "unsupported",
                        toolCount = item.getAsJsonObject("tools")?.size() ?: 0,
                        resourceCount = item.getAsJsonArray("resources")?.size() ?: 0,
                        error = existing[name]?.error,
                    )
                }
                .orEmpty()
            val servers = (accumulated + page).distinctBy { it.name }
            val nextCursor = response.result?.string("nextCursor")
            if (nextCursor != null && nextCursor !in visitedCursors) {
                loadMcpStatusPage(
                    cursor = nextCursor,
                    accumulated = servers,
                    generation = generation,
                    visitedCursors = visitedCursors + nextCursor,
                )
            } else {
                val notificationOnly = uiState.mcpServers.filter { current ->
                    servers.none { it.name == current.name } && current.startupStatus != null
                }
                uiState = uiState.copy(
                    mcpServers = (servers + notificationOnly).sortedBy { it.displayName.lowercase() },
                    mcpLoading = false,
                    mcpError = null,
                )
            }
        }
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
        const val HISTORY_PAGE_SIZE = 20
        const val DELTA_FLUSH_INTERVAL_MS = 32L
        const val MAX_STREAMED_MESSAGE_CHARS = 200_000
        const val MAX_MESSAGE_CHARS = 240_000
        const val MAX_TOOL_OUTPUT_CHARS = 200_000
        const val MAX_PATCH_CHARS = 300_000
        const val MAX_GOAL_CHARS = 4_000
        const val MAX_FILE_SEARCH_RESULTS = 8
        val DEFAULT_INPUT_MODALITIES = listOf("text", "image")
        val GLOBAL_THREAD_EVENTS = setOf(
            "serverRequest/resolved",
            "thread/archived",
            "thread/deleted",
            "thread/unarchived",
            "thread/closed",
        )
        val BACKGROUND_STATUS_EVENTS = setOf(
            "turn/started",
            "turn/completed",
            "thread/status/changed",
        )
        const val WORKSPACE_ONBOARDING_COMPLETED = "workspace_onboarding_completed_v2"
        val RECONNECT_TOKEN = Any()
    }
}
