package com.termuxcodex.client.data

/**
 * Officially documented Codex App Server method names are kept in one reviewable catalog.
 * Optional fields and decision payloads are read from each runtime request whenever available.
 */
object CodexProtocol {
    object ClientRequest {
        const val INITIALIZE = "initialize"
        const val CONFIG_READ = "config/read"
        const val FS_GET_METADATA = "fs/getMetadata"
        const val FS_READ_DIRECTORY = "fs/readDirectory"
        const val FUZZY_FILE_SEARCH = "fuzzyFileSearch"
        const val MODEL_LIST = "model/list"
        const val MCP_SERVER_STATUS_LIST = "mcpServerStatus/list"
        const val SKILLS_LIST = "skills/list"
        const val THREAD_DELETE = "thread/delete"
        const val THREAD_COMPACT_START = "thread/compact/start"
        const val THREAD_GOAL_CLEAR = "thread/goal/clear"
        const val THREAD_GOAL_SET = "thread/goal/set"
        const val THREAD_LIST = "thread/list"
        const val THREAD_RESUME = "thread/resume"
        const val THREAD_NAME_SET = "thread/name/set"
        const val THREAD_UNSUBSCRIBE = "thread/unsubscribe"
        const val THREAD_START = "thread/start"
        const val THREAD_TURNS_LIST = "thread/turns/list"
        const val TURN_INTERRUPT = "turn/interrupt"
        const val TURN_START = "turn/start"
        const val TURN_STEER = "turn/steer"
    }

    object ServerRequest {
        const val COMMAND_APPROVAL = "item/commandExecution/requestApproval"
        const val FILE_CHANGE_APPROVAL = "item/fileChange/requestApproval"
        const val PERMISSIONS_APPROVAL = "item/permissions/requestApproval"
        const val USER_INPUT = "item/tool/requestUserInput"
        const val MCP_ELICITATION = "mcpServer/elicitation/request"
        const val LEGACY_PATCH_APPROVAL = "applyPatchApproval"
        const val LEGACY_COMMAND_APPROVAL = "execCommandApproval"
        const val DYNAMIC_TOOL_CALL = "item/tool/call"
        const val AUTH_TOKEN_REFRESH = "account/chatgptAuthTokens/refresh"
        const val ATTESTATION = "attestation/generate"
    }

    object Notification {
        const val TURN_STARTED = "turn/started"
        const val TURN_COMPLETED = "turn/completed"
        const val TURN_DIFF_UPDATED = "turn/diff/updated"
        const val FILE_PATCH_UPDATED = "item/fileChange/patchUpdated"
        const val MCP_SERVER_STATUS_UPDATED = "mcpServer/startupStatus/updated"
        const val SERVER_REQUEST_RESOLVED = "serverRequest/resolved"
    }
}
