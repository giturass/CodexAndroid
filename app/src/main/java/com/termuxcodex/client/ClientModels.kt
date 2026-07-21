@file:android.annotation.SuppressLint("SdCardPath")

package com.termuxcodex.client

import androidx.compose.runtime.Immutable
import com.google.gson.JsonElement
import com.google.gson.JsonObject

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
    val active: Boolean = false,
    val pinned: Boolean = false,
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

data class RemoteDirectory(val name: String, val path: String)

data class CodexSkill(
    val name: String,
    val displayName: String,
    val path: String,
    val description: String,
    val scope: String,
)

enum class PendingKind { COMMAND, FILE_CHANGE, PERMISSION, USER_INPUT, MCP_ELICITATION }

data class InputOption(val label: String, val description: String = "")

data class InputQuestion(
    val id: String,
    val header: String,
    val prompt: String,
    val options: List<InputOption>,
    val secret: Boolean,
    val valueType: String = "string",
    val required: Boolean = false,
)

data class PendingDecision(val key: String, val label: String, val payload: JsonElement)

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
    val approvalProtocol: ApprovalProtocol? = null,
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
        get() = pendingActions.firstOrNull {
            it.threadId == null || it.threadId == currentThreadId
        }

    val backgroundPendingAction: PendingAction?
        get() = pendingActions.firstOrNull {
            it.threadId != null && it.threadId != currentThreadId
        }
}
