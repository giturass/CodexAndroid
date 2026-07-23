package com.termuxcodex.client.ui

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.termuxcodex.client.AppUiState
import com.termuxcodex.client.AppThemeMode
import com.termuxcodex.client.CodexViewModel
import com.termuxcodex.client.DEFAULT_TERMUX_HOME
import com.termuxcodex.client.ConnectionStatus
import com.termuxcodex.client.MessageKind
import com.termuxcodex.client.MessageOutcome
import com.termuxcodex.client.McpServerStatus
import com.termuxcodex.client.PendingAction
import com.termuxcodex.client.PendingKind
import com.termuxcodex.client.PromptFileReference
import com.termuxcodex.client.PromptImageAttachment
import com.termuxcodex.client.PromptInput
import com.termuxcodex.client.RemoteDirectory
import com.termuxcodex.client.ThreadSummary
import com.termuxcodex.client.UiMessage
import com.termuxcodex.client.activePromptReferenceToken
import com.termuxcodex.client.inputValidationError
import com.termuxcodex.client.isValidWorkspacePath
import com.termuxcodex.client.replacePromptReferenceToken
import com.termuxcodex.client.supportsInputModality
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodexApp(viewModel: CodexViewModel) {
    val state = viewModel.uiState
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showSettings by remember { mutableStateOf(false) }
    var showWorkspacePicker by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var threadToDelete by remember { mutableStateOf<ThreadSummary?>(null) }
    var threadToRename by remember { mutableStateOf<ThreadSummary?>(null) }

    LaunchedEffect(state.connectionStatus, state.workspaceConfigured) {
        showWorkspacePicker = state.connectionStatus == ConnectionStatus.CONNECTED &&
            !state.workspaceConfigured
    }
    val onOpenSettings: () -> Unit = {
        viewModel.refreshMcpStatus()
        showSettings = true
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val dualPane = maxWidth >= 840.dp
        val drawerCallbacks = DrawerCallbacks(
            onNewThread = viewModel::newThread,
            onOpenThread = viewModel::openThread,
            onRenameThread = { threadToRename = it },
            onTogglePinned = viewModel::toggleThreadPinned,
            onDeleteThread = { threadToDelete = it },
            onRefresh = viewModel::refreshThreads,
            onSearch = { showSearch = true },
        )
        if (dualPane) {
            Row(Modifier.fillMaxSize()) {
                ConversationDrawer(
                    state = state,
                    formatTime = viewModel::formatThreadTime,
                    callbacks = drawerCallbacks,
                    permanent = true,
                    modifier = Modifier.width(320.dp),
                )
                ConversationScaffold(
                    state = state,
                    showNavigationIcon = false,
                    onOpenDrawer = {},
                    onSettings = onOpenSettings,
                    onOpenThread = viewModel::openThread,
                    onSend = viewModel::sendPromptInput,
                    onStop = viewModel::interruptTurn,
                    onConnect = viewModel::connect,
                    onChooseWorkspace = { showWorkspacePicker = true },
                    onLoadOlder = viewModel::loadOlderHistory,
                    onModelSelected = viewModel::updateComposerModel,
                    onReasoningSelected = viewModel::updateComposerReasoningEffort,
                    onSearchFiles = viewModel::searchWorkspaceFiles,
                    onCompact = viewModel::compactCurrentThread,
                    onSetGoal = viewModel::setCurrentThreadGoal,
                    onClearGoal = viewModel::clearCurrentThreadGoal,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = true,
                drawerContent = {
                    ConversationDrawer(
                        state = state,
                        formatTime = viewModel::formatThreadTime,
                        callbacks = drawerCallbacks.copy(
                            onNewThread = {
                                viewModel.newThread()
                                scope.launch { drawerState.close() }
                            },
                            onOpenThread = {
                                viewModel.openThread(it)
                                scope.launch { drawerState.close() }
                            },
                            onSearch = {
                                showSearch = true
                                scope.launch { drawerState.close() }
                            },
                        ),
                    )
                },
            ) {
                ConversationScaffold(
                    state = state,
                    showNavigationIcon = true,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onSettings = onOpenSettings,
                    onOpenThread = viewModel::openThread,
                    onSend = viewModel::sendPromptInput,
                    onStop = viewModel::interruptTurn,
                    onConnect = viewModel::connect,
                    onChooseWorkspace = { showWorkspacePicker = true },
                    onLoadOlder = viewModel::loadOlderHistory,
                    onModelSelected = viewModel::updateComposerModel,
                    onReasoningSelected = viewModel::updateComposerReasoningEffort,
                    onSearchFiles = viewModel::searchWorkspaceFiles,
                    onCompact = viewModel::compactCurrentThread,
                    onSetGoal = viewModel::setCurrentThreadGoal,
                    onClearGoal = viewModel::clearCurrentThreadGoal,
                )
            }
        }
    }

    if (showSettings) {
        ConnectionSettingsDialog(
            state = state,
            onDismiss = { showSettings = false },
            onSave = { endpoint, token, cwd ->
                val saved = viewModel.updateSettings(
                    endpoint,
                    token,
                    cwd,
                    state.model,
                    state.reasoningEffort,
                    emptySet(),
                )
                if (saved) {
                    showSettings = false
                    viewModel.connect()
                }
                saved
            },
            onReadDirectories = viewModel::readDirectories,
            onRefreshMcp = viewModel::refreshMcpStatus,
            onThemeSelected = viewModel::updateThemeMode,
        )
    }

    if (showWorkspacePicker && state.connectionStatus == ConnectionStatus.CONNECTED) {
        RemoteDirectoryPickerDialog(
            initialPath = state.cwd.ifBlank { DEFAULT_TERMUX_HOME },
            onReadDirectories = viewModel::readDirectories,
            onDismiss = { showWorkspacePicker = false },
            onSelect = { selectedPath ->
                viewModel.selectWorkspace(selectedPath)
                showWorkspacePicker = false
            },
        )
    }

    if (showSearch) {
        SearchDialog(
            state = state,
            onDismiss = { showSearch = false },
            onSearchThreads = viewModel::searchThreads,
            onThreadSelected = { threadId ->
                viewModel.openThread(threadId)
                showSearch = false
            },
        )
    }

    threadToDelete?.let { thread ->
        AlertDialog(
            onDismissRequest = { threadToDelete = null },
            icon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
            title = { Text("永久删除会话？") },
            text = {
                Text("“${thread.title}”及其派生会话将被永久删除，无法恢复。")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteThread(thread.id)
                    threadToDelete = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { threadToDelete = null }) { Text("取消") }
            },
        )
    }

    threadToRename?.let { thread ->
        RenameThreadDialog(
            thread = thread,
            onDismiss = { threadToRename = null },
            onRename = { name ->
                viewModel.renameThread(thread.id, name)
                threadToRename = null
            },
        )
    }

    state.pendingAction?.let { pending ->
        if (pending.kind == PendingKind.USER_INPUT ||
            (pending.kind == PendingKind.MCP_ELICITATION && pending.questions.isNotEmpty())
        ) {
            UserInputDialog(
                pending = pending,
                onSubmit = viewModel::answerQuestions,
                onDismiss = viewModel::dismissPendingAction,
            )
        } else {
            ApprovalDialog(
                pending = pending,
                onDecision = viewModel::resolveApproval,
                onDismiss = viewModel::dismissPendingAction,
            )
        }
    }
}

private data class DrawerCallbacks(
    val onNewThread: () -> Unit,
    val onOpenThread: (String) -> Unit,
    val onRenameThread: (ThreadSummary) -> Unit,
    val onTogglePinned: (String) -> Unit,
    val onDeleteThread: (ThreadSummary) -> Unit,
    val onRefresh: () -> Unit,
    val onSearch: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationScaffold(
    state: AppUiState,
    showNavigationIcon: Boolean,
    onOpenDrawer: () -> Unit,
    onSettings: () -> Unit,
    onOpenThread: (String) -> Unit,
    onSend: (PromptInput) -> Boolean,
    onStop: () -> Unit,
    onConnect: () -> Unit,
    onChooseWorkspace: () -> Unit,
    onLoadOlder: () -> Unit,
    onModelSelected: (String) -> Unit,
    onReasoningSelected: (String) -> Unit,
    onSearchFiles: (String, (List<PromptFileReference>, String?) -> Unit) -> Unit,
    onCompact: () -> Boolean,
    onSetGoal: (String) -> Boolean,
    onClearGoal: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = state.currentThreadTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    if (showNavigationIcon) {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(Icons.Rounded.Menu, contentDescription = "会话记录")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = "设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            if (state.connectionStatus == ConnectionStatus.CONNECTED &&
                state.workspaceConfigured
            ) {
                PromptBar(
                    state = state,
                    onSend = onSend,
                    onStop = onStop,
                    onModelSelected = onModelSelected,
                    onReasoningSelected = onReasoningSelected,
                    onSearchFiles = onSearchFiles,
                    onCompact = onCompact,
                    onSetGoal = onSetGoal,
                    onClearGoal = onClearGoal,
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            state.backgroundPendingAction?.let { pending ->
                PendingThreadBanner(
                    pending = pending,
                    onOpen = { pending.threadId?.let(onOpenThread) },
                )
            }
            key(state.currentThreadId) {
                ConversationContent(
                    state = state,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    onConnect = onConnect,
                    onChooseWorkspace = onChooseWorkspace,
                    onSuggestion = { onSend(PromptInput(it)) },
                    onLoadOlder = onLoadOlder,
                )
            }
        }
    }
}

@Composable
private fun PendingThreadBanner(
    pending: PendingAction,
    onOpen: () -> Unit,
) {
    Surface(
        onClick = onOpen,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Info, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(pending.title, style = MaterialTheme.typography.labelLarge)
                Text("另一个会话正在等待处理，点击前往", style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Rounded.ArrowUpward, contentDescription = "前往对应会话")
        }
    }
}

@Composable
private fun ConversationDrawer(
    state: AppUiState,
    formatTime: (Long) -> String,
    callbacks: DrawerCallbacks,
    modifier: Modifier = Modifier,
    permanent: Boolean = false,
) {
    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
                tonalElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp)
                    .padding(vertical = 4.dp),
            ) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(58.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.Terminal,
                                contentDescription = null,
                                modifier = Modifier.size(38.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text("Codex Android", style = MaterialTheme.typography.titleMedium)
                        Text(
                            state.endpoint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (state.connectionStatus) {
                                            ConnectionStatus.CONNECTED -> Color(0xFF2DA44E)
                                            ConnectionStatus.CONNECTING -> MaterialTheme.colorScheme.primary
                                            ConnectionStatus.DISCONNECTED -> MaterialTheme.colorScheme.error
                                        }
                                    )
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                when (state.connectionStatus) {
                                    ConnectionStatus.CONNECTED -> "已连接"
                                    ConnectionStatus.CONNECTING -> "正在连接"
                                    ConnectionStatus.DISCONNECTED -> "未连接"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(
                        onClick = callbacks.onRefresh,
                        enabled = state.connectionStatus != ConnectionStatus.CONNECTING,
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "刷新会话")
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 2.dp, top = 50.dp, end = 4.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = callbacks.onNewThread,
                    shape = CircleShape,
                    modifier = Modifier
                        .width(230.dp)
                        .height(48.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                ) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(21.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("新会话", style = MaterialTheme.typography.labelLarge)
                }
                FilledTonalIconButton(
                    onClick = callbacks.onSearch,
                    enabled = state.connectionStatus == ConnectionStatus.CONNECTED,
                    shape = CircleShape,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = "搜索会话",
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "最近会话",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    state.threads.size.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(state.threads, key = { it.id }) { thread ->
                    ThreadDrawerItem(
                        thread = thread,
                        selected = thread.id == state.currentThreadId,
                        running = thread.active ||
                            (thread.id == state.currentThreadId && state.busy),
                        pendingCount = state.pendingActions.count { it.threadId == thread.id },
                        time = formatTime(thread.updatedAt),
                        onClick = { callbacks.onOpenThread(thread.id) },
                        onRename = { callbacks.onRenameThread(thread) },
                        onTogglePinned = { callbacks.onTogglePinned(thread.id) },
                        onDelete = { callbacks.onDeleteThread(thread) },
                        deleteEnabled = !state.busy || thread.id != state.currentThreadId,
                    )
                }
            }

        }
    }
    if (permanent) {
        Surface(
            modifier = modifier.fillMaxHeight(),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 2.dp,
        ) { content() }
    } else {
        ModalDrawerSheet(modifier = modifier.widthIn(max = 336.dp)) { content() }
    }
}

@Composable
private fun ThreadDrawerItem(
    thread: ThreadSummary,
    selected: Boolean,
    running: Boolean,
    pendingCount: Int,
    time: String,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onTogglePinned: () -> Unit,
    onDelete: () -> Unit,
    deleteEnabled: Boolean,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(9.dp),
        border = if (selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.24f))
        } else {
            null
        },
        tonalElevation = if (selected) 2.dp else 0.dp,
        shadowElevation = if (selected) 1.dp else 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 11.dp, bottom = 11.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (thread.pinned) Icons.Rounded.PushPin else Icons.Rounded.History,
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    thread.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (time.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            time,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (running) {
                            DrawerStatusPill("运行中", Color(0xFF2DA44E))
                        }
                        if (pendingCount > 0) {
                            DrawerStatusPill("待处理 $pendingCount", MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "会话菜单")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("重命名") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(if (thread.pinned) "取消置顶" else "置顶") },
                        leadingIcon = { Icon(Icons.Rounded.PushPin, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onTogglePinned()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("删除会话") },
                        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                        enabled = deleteEnabled,
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerStatusPill(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.14f),
        contentColor = color,
        shape = RoundedCornerShape(5.dp),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun RenameThreadDialog(
    thread: ThreadSummary,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    var name by rememberSaveable(thread.id) { mutableStateOf(thread.title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
        title = { Text("重命名会话") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("会话名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (name.isNotBlank()) onRename(name)
                }),
            )
        },
        confirmButton = {
            Button(onClick = { onRename(name) }, enabled = name.isNotBlank()) {
                Text("保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ConversationContent(
    state: AppUiState,
    modifier: Modifier,
    onConnect: () -> Unit,
    onChooseWorkspace: () -> Unit,
    onSuggestion: (String) -> Unit,
    onLoadOlder: () -> Unit,
) {
    if (state.connectionStatus == ConnectionStatus.CONNECTED && !state.workspaceConfigured) {
        Box(modifier, contentAlignment = Alignment.Center) {
            WorkspaceSetupPanel(state.cwd, onChooseWorkspace)
        }
        return
    }
    if (state.messages.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            if (state.connectionStatus == ConnectionStatus.CONNECTED) {
                WelcomePanel(state.cwd, onSuggestion)
            } else {
                ConnectionPanel(state, onConnect)
            }
        }
        return
    }

    val listState = rememberLazyListState()
    var autoScroll by remember { mutableStateOf(true) }
    val lastLength = state.messages.lastOrNull()?.text?.length ?: 0
    val lastLengthBucket = lastLength / 256
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to listState.canScrollForward }
            .collect { (scrolling, canScrollForward) ->
                if (scrolling) autoScroll = !canScrollForward
            }
    }
    LaunchedEffect(state.messages.size, lastLengthBucket) {
        if (autoScroll && state.messages.isNotEmpty()) {
            listState.scrollToItem(state.messages.lastIndex + if (state.historyNextCursor != null) 1 else 0)
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (state.historyNextCursor != null || state.historyLoading) {
            item(key = "load-older") {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(onClick = onLoadOlder, enabled = !state.historyLoading) {
                        if (state.historyLoading) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (state.historyLoading) "正在加载" else "加载更早记录")
                    }
                }
            }
        }
        items(
            items = state.messages,
            key = { it.id },
            contentType = { it.kind },
        ) { message ->
            MessageItem(message)
        }
    }
}

@Composable
private fun WorkspaceSetupPanel(
    cwd: String,
    onChooseWorkspace: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .padding(24.dp)
            .widthIn(max = 560.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(64.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("选择工作目录", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Codex 已连接。请选择它可以读取、修改和运行代码的 Termux 目录。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                cwd,
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))
            Button(onClick = onChooseWorkspace) {
                Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("选择工作目录")
            }
        }
    }
}

@Composable
private fun WelcomePanel(cwd: String, onSuggestion: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(76.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.Terminal,
                    contentDescription = null,
                    modifier = Modifier.size(38.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("准备开始", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Codex 将在 Termux 工作区中读取、修改和运行代码。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            cwd,
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.primary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = { onSuggestion("检查这个项目，并告诉我目前的结构和可以改进的地方") },
                label = { Text("分析当前项目") },
                leadingIcon = { Icon(Icons.Rounded.Code, null, Modifier.size(18.dp)) },
            )
            AssistChip(
                onClick = { onSuggestion("运行项目测试，定位失败原因并修复") },
                label = { Text("运行测试并修复") },
                leadingIcon = { Icon(Icons.Rounded.Build, null, Modifier.size(18.dp)) },
            )
        }
    }
}

@Composable
private fun ConnectionPanel(
    state: AppUiState,
    onConnect: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val serverCommand = "codex app-server --listen ws://127.0.0.1:4500"
    ElevatedCard(
        modifier = Modifier
            .padding(24.dp)
            .widthIn(max = 560.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Terminal,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("连接 Codex CLI", style = MaterialTheme.typography.titleLarge)
                    Text(
                        state.connectionMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "在 Termux 中运行：",
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Row(
                    modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        serverCommand,
                        modifier = Modifier.weight(1f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                    )
                    IconButton(onClick = {
                        scope.launch {
                            clipboard.setClipEntry(
                                ClipEntry(ClipData.newPlainText("Codex CLI 命令", serverCommand))
                            )
                        }
                    }) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "复制命令")
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "连接地址：${state.endpoint}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onConnect,
                    enabled = state.connectionStatus != ConnectionStatus.CONNECTING,
                ) {
                    if (state.connectionStatus == ConnectionStatus.CONNECTING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    } else {
                        Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(if (state.connectionStatus == ConnectionStatus.CONNECTING) "连接中" else "连接")
                }
                val termuxIntent = remember {
                    context.packageManager.getLaunchIntentForPackage("com.termux")
                }
                if (termuxIntent != null) {
                    TextButton(onClick = {
                        termuxIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(termuxIntent)
                    }) {
                        Text("打开 Termux")
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageItem(message: UiMessage) {
    when (message.kind) {
        MessageKind.USER -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp),
                modifier = Modifier.widthIn(max = 560.dp),
            ) {
                Column(Modifier.padding(start = 17.dp, end = 8.dp, top = 6.dp, bottom = 12.dp)) {
                    CopyTextButton(message.text, "复制用户消息", Modifier.align(Alignment.End))
                    MarkdownText(
                        message.text,
                        modifier = Modifier.padding(end = 9.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (message.running) {
                        val transition = rememberInfiniteTransition(label = "response-dots")
                        val alpha by transition.animateFloat(
                            initialValue = 0.25f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(650),
                                repeatMode = RepeatMode.Reverse,
                            ),
                            label = "response-dots-alpha",
                        )
                        Text(
                            "•••",
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(top = 4.dp, end = 4.dp)
                                .graphicsLayer { this.alpha = alpha },
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                        )
                    }
                }
            }
        }

        MessageKind.ASSISTANT -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(6.dp, 22.dp, 22.dp, 22.dp),
                modifier = Modifier.widthIn(max = 720.dp),
            ) {
                Column(Modifier.padding(start = 17.dp, end = 8.dp, top = 6.dp, bottom = 14.dp)) {
                    CopyTextButton(message.text, "复制回复", Modifier.align(Alignment.End))
                    if (message.running) {
                        androidx.compose.foundation.text.selection.SelectionContainer {
                            Text(
                                message.text,
                                modifier = Modifier.padding(end = 9.dp),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    } else {
                        MarkdownText(
                            message.text,
                            modifier = Modifier.padding(end = 9.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    AnimatedVisibility(message.running) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .padding(top = 12.dp)
                                .fillMaxWidth(),
                        )
                    }
                }
            }
        }

        MessageKind.TOOL -> ProcessMessageCard(message)

        MessageKind.INFO -> if (message.title != null) {
            ProcessMessageCard(message)
        } else {
            StatusMessage(message)
        }

        MessageKind.ERROR -> StatusMessage(message)
    }
}

@Composable
private fun ProcessMessageCard(message: UiMessage) {
    val isCommandOutput = message.title?.contains("命令") == true
    val isFileChange = message.title?.contains("文件") == true ||
        message.title?.contains("补丁") == true
    val accent = when {
        isFileChange -> Color(0xFF8250DF)
        isCommandOutput -> MaterialTheme.colorScheme.primary
        message.title?.contains("计划") == true -> Color(0xFFBF8700)
        message.title?.contains("网页") == true -> Color(0xFF0969DA)
        else -> MaterialTheme.colorScheme.tertiary
    }
    var expanded by remember(message.id) { mutableStateOf(isCommandOutput) }
    var showAllOutput by remember(message.id) { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val boundedText = remember(message.text, isCommandOutput) {
        if (isCommandOutput && message.text.length > MAX_COMMAND_OUTPUT_CHARS) {
            message.text.take(MAX_COMMAND_OUTPUT_CHARS) + "\n\n[输出过长，已截断]"
        } else {
            message.text
        }
    }
    val displayText = if (isCommandOutput && !showAllOutput &&
        boundedText.length > MAX_COMMAND_PREVIEW_CHARS
    ) {
        boundedText.take(MAX_COMMAND_PREVIEW_CHARS) + "\n\n[点击“展开全部”查看完整输出]"
    } else {
        boundedText
    }
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    message.title ?: "执行过程",
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                    modifier = Modifier.weight(1f),
                )
                if (message.running) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else if (message.outcome == MessageOutcome.FAILED ||
                    message.outcome == MessageOutcome.DECLINED
                ) {
                    Icon(
                        Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(17.dp),
                    )
                } else {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF2DA44E),
                        modifier = Modifier.size(17.dp),
                    )
                }
                Spacer(Modifier.width(6.dp))
                CopyTextButton(message.text, "复制执行内容")
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "折叠执行过程" else "展开执行过程",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expanded && message.text.isNotBlank()) {
                Column {
                    if (isFileChange) {
                        DiffViewer(
                            text = message.text,
                            modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = if (showAllOutput) 560.dp else 360.dp)
                                .verticalScroll(scrollState)
                                .padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
                        ) {
                            if (isCommandOutput) {
                                androidx.compose.foundation.text.selection.SelectionContainer {
                                    Text(
                                        displayText,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            lineHeight = 18.sp,
                                        ),
                                    )
                                }
                            } else {
                                MarkdownText(
                                    message.text,
                                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                                )
                            }
                        }
                    }
                    if (isCommandOutput && boundedText.length > MAX_COMMAND_PREVIEW_CHARS) {
                        TextButton(onClick = { showAllOutput = !showAllOutput }) {
                            Text(if (showAllOutput) "收起长输出" else "展开全部")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusMessage(message: UiMessage) {
    Surface(
        color = if (message.kind == MessageKind.ERROR) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        contentColor = if (message.kind == MessageKind.ERROR) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        },
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(
                if (message.kind == MessageKind.ERROR) Icons.Rounded.ErrorOutline else Icons.Rounded.Info,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                CopyTextButton(message.text, "复制提示", Modifier.align(Alignment.End))
                MarkdownText(message.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun CopyTextButton(
    text: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    IconButton(
        onClick = {
            scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(description, text)))
            }
        },
        enabled = text.isNotEmpty(),
        modifier = modifier.size(32.dp),
    ) {
        Icon(
            Icons.Rounded.ContentCopy,
            contentDescription = description,
            modifier = Modifier.size(16.dp),
        )
    }
}

private const val MAX_COMMAND_PREVIEW_CHARS = 12_000
private const val MAX_COMMAND_OUTPUT_CHARS = 200_000

private data class PromptSlashCommand(
    val name: String,
    val description: String,
    val prompt: String,
)

private val PROMPT_SLASH_COMMANDS = listOf(
    PromptSlashCommand("review", "审查当前改动", "审查当前工作区的改动，按严重程度列出问题和建议。"),
    PromptSlashCommand("test", "运行并修复测试", "运行当前项目的测试，定位失败原因并修复。"),
    PromptSlashCommand("fix", "定位并修复问题", "检查当前项目，定位最需要修复的问题并完成修复。"),
    PromptSlashCommand("explain", "解释项目结构", "分析当前项目并说明结构、关键模块和主要工作流。"),
    PromptSlashCommand("run", "运行终端命令", "运行以下终端命令并返回输出："),
    PromptSlashCommand("init", "创建项目说明", "检查当前项目并创建或更新适合本项目的 AGENTS.md。"),
    PromptSlashCommand("plan", "制定执行计划", "先分析任务并制定分步骤执行计划，再开始实施。"),
    PromptSlashCommand("status", "总结当前状态", "总结当前任务已经完成的内容、剩余工作和潜在风险。"),
    PromptSlashCommand("diff", "检查当前差异", "检查当前工作区的文件差异并总结改动和风险。"),
    PromptSlashCommand("security", "执行安全检查", "检查当前改动中的安全风险，按严重程度给出修复建议。"),
    PromptSlashCommand("docs", "更新项目文档", "检查当前改动并更新受影响的项目文档。"),
    PromptSlashCommand("compact", "压缩会话上下文", "/compact"),
    PromptSlashCommand("goal", "设置会话目标", "/goal "),
    PromptSlashCommand("goal-clear", "清除会话目标", "/goal-clear"),
)

@Composable
private fun PromptBar(
    state: AppUiState,
    onSend: (PromptInput) -> Boolean,
    onStop: () -> Unit,
    onModelSelected: (String) -> Unit,
    onReasoningSelected: (String) -> Unit,
    onSearchFiles: (String, (List<PromptFileReference>, String?) -> Unit) -> Unit,
    onCompact: () -> Boolean,
    onSetGoal: (String) -> Boolean,
    onClearGoal: () -> Boolean,
) {
    val connected = state.connectionStatus == ConnectionStatus.CONNECTED
    var prompt by rememberSaveable(state.currentThreadId, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }
    var images by remember(state.currentThreadId) { mutableStateOf(emptyList<PromptImageAttachment>()) }
    var files by remember(state.currentThreadId) { mutableStateOf(emptyList<PromptFileReference>()) }
    var skills by remember(state.currentThreadId) { mutableStateOf(emptyList<com.termuxcodex.client.CodexSkill>()) }
    var attachmentError by remember { mutableStateOf<String?>(null) }
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var reasoningMenuExpanded by remember { mutableStateOf(false) }
    var fileSuggestions by remember { mutableStateOf(emptyList<PromptFileReference>()) }
    var fileSearchError by remember { mutableStateOf<String?>(null) }
    var fileSearchLoading by remember { mutableStateOf(false) }
    var fileSearchGeneration by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activeToken = activePromptReferenceToken(prompt.text, prompt.selection.end)
    val selectedModel = state.availableModels.firstOrNull { it.model == state.model }
    val defaultModel = state.availableModels.firstOrNull { it.model == state.configModel }
        ?: state.availableModels.firstOrNull { it.isDefault }
    val reasoningModel = selectedModel ?: defaultModel
    val reasoningOptions = reasoningModel?.supportedReasoningEfforts.orEmpty()
    val imageInputSupported = reasoningModel?.supportsInputModality("image") ?: true
    val modelLabel = selectedModel?.displayName ?: "跟随配置文件"
    val effortLabel = state.reasoningEffort.takeIf { it.isNotBlank() }
        ?.let(::reasoningLabel)
        ?: "跟随配置文件"
    val hasContent = prompt.text.isNotBlank() || images.isNotEmpty() ||
        files.isNotEmpty() || skills.isNotEmpty()

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (!imageInputSupported) {
            attachmentError = "当前模型不支持图片输入"
            return@rememberLauncherForActivityResult
        }
        val remaining = (MAX_PROMPT_IMAGES - images.size).coerceAtLeast(0)
        if (remaining == 0) {
            attachmentError = "最多添加 $MAX_PROMPT_IMAGES 张图片"
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                uris.take(remaining).map { loadPromptImage(context, it) }
            }
            val accepted = loaded.mapNotNull { it.attachment }
            images = (images + accepted).distinctBy { it.dataUrl }.take(MAX_PROMPT_IMAGES)
            attachmentError = loaded.firstNotNullOfOrNull { it.error }
        }
    }

    LaunchedEffect(imageInputSupported) {
        if (!imageInputSupported && images.isNotEmpty()) {
            images = emptyList()
            attachmentError = "当前模型不支持图片输入，已移除附件"
        }
    }

    LaunchedEffect(activeToken?.marker, activeToken?.query, state.cwd) {
        val generation = ++fileSearchGeneration
        if (activeToken?.marker != '@' || activeToken.query.isBlank()) {
            fileSuggestions = emptyList()
            fileSearchError = null
            fileSearchLoading = false
            return@LaunchedEffect
        }
        delay(FILE_SEARCH_DEBOUNCE_MS)
        fileSearchLoading = true
        fileSearchError = null
        onSearchFiles(activeToken.query) { results, error ->
            if (generation == fileSearchGeneration) {
                fileSuggestions = results
                fileSearchError = error
                fileSearchLoading = false
            }
        }
    }

    val skillSuggestions = remember(activeToken, state.availableSkills) {
        if (activeToken?.marker != '$') {
            emptyList()
        } else {
            state.availableSkills.filter { skill ->
                activeToken.query.isBlank() ||
                    skill.displayName.contains(activeToken.query, ignoreCase = true) ||
                    skill.name.contains(activeToken.query, ignoreCase = true)
            }.take(MAX_PROMPT_SUGGESTIONS)
        }
    }
    val slashCommands = remember(activeToken) {
        if (activeToken?.marker != '/') {
            emptyList()
        } else {
            PROMPT_SLASH_COMMANDS.filter { command ->
                activeToken.query.isBlank() || command.name.startsWith(activeToken.query, ignoreCase = true)
            }
        }
    }

    fun submit() {
        if (images.isNotEmpty() && !imageInputSupported) {
            attachmentError = "当前模型不支持图片输入"
            return
        }
        val slash = parsePromptSlashCommand(prompt.text)
        if (images.isEmpty() && files.isEmpty() && skills.isEmpty()) {
            when (slash?.name) {
                "compact" -> {
                    if (onCompact()) prompt = TextFieldValue()
                    return
                }
                "goal" -> {
                    if (onSetGoal(slash.argument)) prompt = TextFieldValue()
                    return
                }
                "goal-clear" -> {
                    if (onClearGoal()) prompt = TextFieldValue()
                    return
                }
            }
        }
        val input = PromptInput(
            text = expandPromptSlashCommand(prompt.text),
            files = files,
            skills = skills,
            images = images,
        )
        if (onSend(input)) {
            prompt = TextFieldValue()
            images = emptyList()
            files = emptyList()
            skills = emptyList()
            attachmentError = null
        }
    }

    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PromptReferenceSuggestions(
                token = activeToken,
                cwd = state.cwd,
                files = fileSuggestions,
                skills = skillSuggestions,
                commands = slashCommands,
                loading = fileSearchLoading,
                error = fileSearchError,
                onFileSelected = { file ->
                    val token = activePromptReferenceToken(prompt.text, prompt.selection.end)
                        ?: return@PromptReferenceSuggestions
                    val label = fileDisplayPath(file, state.cwd)
                    val (updated, cursor) = replacePromptReferenceToken(prompt.text, token, label)
                    prompt = TextFieldValue(updated, TextRange(cursor))
                    files = (files + file).distinctBy { it.path }
                },
                onSkillSelected = { skill ->
                    val token = activePromptReferenceToken(prompt.text, prompt.selection.end)
                        ?: return@PromptReferenceSuggestions
                    val (updated, cursor) = replacePromptReferenceToken(
                        prompt.text,
                        token,
                        skill.displayName,
                    )
                    prompt = TextFieldValue(updated, TextRange(cursor))
                    skills = (skills + skill).distinctBy { it.path }
                },
                onCommandSelected = { command ->
                    val token = activePromptReferenceToken(prompt.text, prompt.selection.end)
                        ?: return@PromptReferenceSuggestions
                    val updated = prompt.text.replaceRange(token.start, token.end, command.prompt)
                    prompt = TextFieldValue(
                        text = updated,
                        selection = TextRange(token.start + command.prompt.length),
                    )
                },
            )

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(start = 14.dp, top = 12.dp, end = 8.dp, bottom = 8.dp),
                ) {
                    if (images.isNotEmpty() || files.isNotEmpty() || skills.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            images.forEach { image ->
                                InputChip(
                                    selected = false,
                                    onClick = { images = images - image },
                                    label = {
                                        Text(image.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.Image, null, Modifier.size(16.dp))
                                    },
                                    trailingIcon = {
                                        Icon(Icons.Rounded.Close, "移除 ${image.name}", Modifier.size(16.dp))
                                    },
                                )
                            }
                            files.forEach { file ->
                                InputChip(
                                    selected = false,
                                    onClick = {
                                        files = files - file
                                        prompt = prompt.copy(
                                            text = prompt.text.replace("@${fileDisplayPath(file, state.cwd)}", ""),
                                        )
                                    },
                                    label = {
                                        Text(
                                            fileDisplayPath(file, state.cwd),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(Icons.AutoMirrored.Rounded.InsertDriveFile, null, Modifier.size(16.dp))
                                    },
                                    trailingIcon = {
                                        Icon(Icons.Rounded.Close, "移除文件引用", Modifier.size(16.dp))
                                    },
                                )
                            }
                            skills.forEach { skill ->
                                InputChip(
                                    selected = false,
                                    onClick = {
                                        skills = skills - skill
                                        prompt = prompt.copy(
                                            text = prompt.text.replace("\$${skill.displayName}", ""),
                                        )
                                    },
                                    label = {
                                        Text(skill.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.Build, null, Modifier.size(16.dp))
                                    },
                                    trailingIcon = {
                                        Icon(Icons.Rounded.Close, "移除 Skill 引用", Modifier.size(16.dp))
                                    },
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }

                    BasicTextField(
                        value = prompt,
                        onValueChange = { value ->
                            prompt = value
                            files = files.filter { value.text.contains("@${fileDisplayPath(it, state.cwd)}") }
                            skills = skills.filter { value.text.contains("\$${it.displayName}") }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 84.dp, max = 168.dp),
                        enabled = connected,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                        minLines = 3,
                        maxLines = 6,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                        decorationBox = { innerTextField ->
                            Box(Modifier.fillMaxWidth()) {
                                if (prompt.text.isEmpty()) {
                                    Text(
                                        when {
                                            !connected -> "连接后即可开始"
                                            state.busy -> "给当前任务追加指令…"
                                            else -> "输入任务，支持 / 命令、@ 文件和 $ Skills"
                                        },
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            IconButton(
                                onClick = { imagePicker.launch("image/*") },
                                enabled = connected && imageInputSupported &&
                                    images.size < MAX_PROMPT_IMAGES,
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = "添加图片附件")
                            }
                        }

                        Box(Modifier.weight(1f)) {
                            TextButton(
                                onClick = { modelMenuExpanded = true },
                                enabled = connected && !state.busy && state.availableModels.isNotEmpty(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                            ) {
                                Text(
                                    modelLabel,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                )
                                Icon(Icons.Rounded.ExpandMore, contentDescription = null, Modifier.size(16.dp))
                            }
                            DropdownMenu(
                                expanded = modelMenuExpanded,
                                onDismissRequest = { modelMenuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("跟随配置文件") },
                                    onClick = {
                                        onModelSelected("")
                                        modelMenuExpanded = false
                                    },
                                )
                                state.availableModels.forEach { model ->
                                    DropdownMenuItem(
                                        text = { Text(model.displayName) },
                                        onClick = {
                                            onModelSelected(model.model)
                                            modelMenuExpanded = false
                                        },
                                    )
                                }
                            }
                        }

                        Box(Modifier.weight(1f)) {
                            TextButton(
                                onClick = { reasoningMenuExpanded = true },
                                enabled = connected && !state.busy && reasoningOptions.isNotEmpty(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                            ) {
                                Text(
                                    effortLabel,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                )
                                Icon(Icons.Rounded.ExpandMore, contentDescription = null, Modifier.size(16.dp))
                            }
                            DropdownMenu(
                                expanded = reasoningMenuExpanded,
                                onDismissRequest = { reasoningMenuExpanded = false },
                                modifier = Modifier.width(164.dp),
                            ) {
                                DropdownMenuItem(
                                    text = { Text("跟随配置文件") },
                                    onClick = {
                                        onReasoningSelected("")
                                        reasoningMenuExpanded = false
                                    },
                                )
                                reasoningOptions.forEach { effort ->
                                    DropdownMenuItem(
                                        text = { Text(reasoningLabel(effort.value)) },
                                        onClick = {
                                            onReasoningSelected(effort.value)
                                            reasoningMenuExpanded = false
                                        },
                                    )
                                }
                            }
                        }

                        Box(
                            modifier = Modifier.size(40.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            FilledIconButton(
                                onClick = {
                                    if (state.busy && !hasContent) onStop() else submit()
                                },
                                enabled = connected && (state.busy || hasContent),
                                shape = CircleShape,
                                modifier = Modifier.size(40.dp),
                            ) {
                                val showStop = state.busy && !hasContent
                                Icon(
                                    if (showStop) Icons.Rounded.Stop else Icons.Rounded.ArrowUpward,
                                    contentDescription = if (showStop) {
                                        "停止任务"
                                    } else if (state.busy) {
                                        "追加指令"
                                    } else {
                                        "发送"
                                    },
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }

            attachmentError?.let { error ->
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun PromptReferenceSuggestions(
    token: com.termuxcodex.client.PromptReferenceToken?,
    cwd: String,
    files: List<PromptFileReference>,
    skills: List<com.termuxcodex.client.CodexSkill>,
    commands: List<PromptSlashCommand>,
    loading: Boolean,
    error: String?,
    onFileSelected: (PromptFileReference) -> Unit,
    onSkillSelected: (com.termuxcodex.client.CodexSkill) -> Unit,
    onCommandSelected: (PromptSlashCommand) -> Unit,
) {
    if (token == null) return
    val hasResults = files.isNotEmpty() || skills.isNotEmpty() || commands.isNotEmpty()
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 196.dp)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 6.dp),
        ) {
            when {
                token.marker == '@' && token.query.isBlank() -> PromptSuggestionHint("继续输入文件名")
                loading -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    PromptSuggestionHint("正在搜索工作区文件")
                }
                error != null -> PromptSuggestionHint("文件搜索不可用：$error")
                !hasResults -> PromptSuggestionHint(
                    when (token.marker) {
                        '@' -> "没有匹配的文件"
                        '$' -> "没有匹配的 Skills"
                        else -> "没有匹配的命令"
                    }
                )
                token.marker == '@' -> files.forEach { file ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    fileDisplayPath(file, cwd),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                        leadingIcon = {
                            Icon(Icons.AutoMirrored.Rounded.InsertDriveFile, contentDescription = null)
                        },
                        onClick = { onFileSelected(file) },
                    )
                }
                token.marker == '$' -> skills.forEach { skill ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(skill.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (skill.description.isNotBlank()) {
                                    Text(
                                        skill.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        },
                        leadingIcon = { Icon(Icons.Rounded.Build, contentDescription = null) },
                        onClick = { onSkillSelected(skill) },
                    )
                }
                else -> commands.forEach { command ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text("/${command.name}")
                                Text(
                                    command.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        leadingIcon = { Icon(Icons.Rounded.Code, contentDescription = null) },
                        onClick = { onCommandSelected(command) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PromptSuggestionHint(text: String) {
    Text(
        text,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private data class PromptImageLoadResult(
    val attachment: PromptImageAttachment? = null,
    val error: String? = null,
)

private fun loadPromptImage(context: android.content.Context, uri: Uri): PromptImageLoadResult {
    val resolver = context.contentResolver
    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: "图片"
    val mimeType = resolver.getType(uri)?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
    val stream = resolver.openInputStream(uri)
        ?: return PromptImageLoadResult(error = "无法读取 $name")
    val bytes = stream.use { input ->
        readLimitedBytes(input, MAX_PROMPT_IMAGE_BYTES)
    } ?: return PromptImageLoadResult(error = "$name 超过 8 MB，无法添加")
    if (bytes.isEmpty()) return PromptImageLoadResult(error = "$name 是空文件")
    val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
    return PromptImageLoadResult(
        attachment = PromptImageAttachment(name, "data:$mimeType;base64,$encoded")
    )
}

private fun readLimitedBytes(input: java.io.InputStream, maxBytes: Int): ByteArray? {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    var total = 0
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        total += count
        if (total > maxBytes) return null
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

private fun fileDisplayPath(file: PromptFileReference, cwd: String): String =
    file.path.removePrefix(cwd.trimEnd('/') + "/")

private fun expandPromptSlashCommand(text: String): String {
    val trimmed = text.trim()
    if (!trimmed.startsWith('/')) return text
    val name = trimmed.drop(1).substringBefore(' ')
    val command = PROMPT_SLASH_COMMANDS.firstOrNull { it.name.equals(name, ignoreCase = true) }
        ?: return text
    val argument = trimmed.drop(name.length + 1).trim()
    return if (argument.isEmpty()) command.prompt else "${command.prompt} $argument"
}

private data class ParsedPromptSlashCommand(
    val name: String,
    val argument: String,
)

private fun parsePromptSlashCommand(text: String): ParsedPromptSlashCommand? {
    val trimmed = text.trim()
    if (!trimmed.startsWith('/')) return null
    val commandText = trimmed.drop(1)
    val name = commandText.substringBefore(' ').lowercase()
    if (name.isBlank()) return null
    return ParsedPromptSlashCommand(
        name = name,
        argument = commandText.substringAfter(' ', "").trim(),
    ).takeIf { parsed -> PROMPT_SLASH_COMMANDS.any { it.name == parsed.name } }
}

private const val MAX_PROMPT_IMAGES = 4
private const val MAX_PROMPT_IMAGE_BYTES = 8 * 1024 * 1024
private const val MAX_PROMPT_SUGGESTIONS = 6
private const val FILE_SEARCH_DEBOUNCE_MS = 180L

@Composable
private fun SearchDialog(
    state: AppUiState,
    onDismiss: () -> Unit,
    onSearchThreads: (String, (List<ThreadSummary>, String?) -> Unit) -> Unit,
    onThreadSelected: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var threadResults by remember { mutableStateOf<List<ThreadSummary>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var hasSearched by remember { mutableStateOf(false) }
    var searchRequestGeneration by remember { mutableIntStateOf(0) }

    fun submitSearch() {
        val searchTerm = query.trim()
        if (searchTerm.isBlank() || loading) return
        val requestGeneration = ++searchRequestGeneration
        loading = true
        hasSearched = true
        searchError = null
        threadResults = emptyList()
        onSearchThreads(searchTerm) { results, error ->
            if (requestGeneration == searchRequestGeneration && query.trim() == searchTerm) {
                threadResults = results
                searchError = error
                loading = false
            }
        }
    }

    AlertDialog(
        modifier = Modifier.navigationBarsPadding(),
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        title = { Text("搜索会话") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        searchRequestGeneration++
                        query = it
                        threadResults = emptyList()
                        searchError = null
                        hasSearched = false
                        loading = false
                    },
                    label = { Text("会话名称") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = {
                                searchRequestGeneration++
                                query = ""
                                threadResults = emptyList()
                                searchError = null
                                hasSearched = false
                                loading = false
                            }) {
                                Icon(Icons.Rounded.Close, contentDescription = "清空搜索")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
                )
                AnimatedVisibility(visible = loading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                LazyColumn(
                    modifier = Modifier.heightIn(min = 96.dp, max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when {
                        searchError != null -> item {
                            Text(
                                searchError.orEmpty(),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        }

                        !loading && hasSearched && threadResults.isEmpty() -> item {
                            Text(
                                "未找到会话",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        }

                        !loading && !hasSearched -> item {
                            Text(
                                "输入会话名称开始搜索",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        }
                    }
                    if (!loading && threadResults.isNotEmpty()) {
                        item {
                            Text(
                                "搜索结果 · ${threadResults.size}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        items(threadResults, key = { "thread-${it.id}" }) { thread ->
                            SearchResultItem(
                                title = thread.title,
                                preview = thread.cwd,
                                enabled = !state.busy,
                                onClick = { onThreadSelected(thread.id) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { submitSearch() },
                enabled = query.isNotBlank() && !loading &&
                    state.connectionStatus == ConnectionStatus.CONNECTED,
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("搜索")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun SearchResultItem(
    title: String,
    preview: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.History,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (preview.isNotBlank()) {
                    Text(
                        preview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectionSettingsDialog(
    state: AppUiState,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Boolean,
    onReadDirectories: (String, (List<RemoteDirectory>, String?) -> Unit) -> Unit,
    onRefreshMcp: () -> Unit,
    onThemeSelected: (AppThemeMode) -> Unit,
) {
    val context = LocalContext.current
    var notificationGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notificationGranted = granted }
    var endpoint by rememberSaveable { mutableStateOf(state.endpoint) }
    var token by rememberSaveable { mutableStateOf(state.token) }
    var cwd by rememberSaveable { mutableStateOf(state.cwd) }
    var cwdError by remember { mutableStateOf<String?>(null) }
    var showDirectoryPicker by remember { mutableStateOf(false) }
    AlertDialog(
        modifier = Modifier.navigationBarsPadding(),
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
        title = { Text("设置") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                OutlinedTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it },
                    label = { Text("App Server 地址") },
                    supportingText = { Text("同机 Termux 默认使用 ws://127.0.0.1:4500") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("WebSocket Bearer Token（可选）") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = cwd,
                        onValueChange = {
                            cwd = it
                            cwdError = null
                        },
                        label = { Text("工作目录") },
                        supportingText = {
                            Text(cwdError ?: "可手动输入，或通过 App Server 浏览 Termux 文件系统")
                        },
                        isError = cwdError != null,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(6.dp))
                    IconButton(
                        onClick = { showDirectoryPicker = true },
                        enabled = state.connectionStatus == ConnectionStatus.CONNECTED,
                    ) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = "选择工作目录")
                    }
                }
                Text("主题", style = MaterialTheme.typography.labelLarge)
                val themeOptions = listOf(
                    AppThemeMode.SYSTEM to "跟随系统",
                    AppThemeMode.LIGHT to "浅色",
                    AppThemeMode.DARK to "深色",
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    themeOptions.forEachIndexed { index, (mode, label) ->
                        SegmentedButton(
                            selected = state.themeMode == mode,
                            onClick = { onThemeSelected(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index, themeOptions.size),
                            label = { Text(label, maxLines = 1) },
                        )
                    }
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("MCP 服务", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = onRefreshMcp,
                        enabled = state.connectionStatus == ConnectionStatus.CONNECTED && !state.mcpLoading,
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "刷新 MCP 状态")
                    }
                }
                when {
                    state.mcpLoading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    state.mcpError != null -> Text(
                        "读取 MCP 状态失败：${state.mcpError}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    state.mcpServers.isEmpty() -> Text(
                        "未配置或未发现 MCP 服务",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> state.mcpServers.forEachIndexed { index, server ->
                        McpStatusRow(server)
                        if (index < state.mcpServers.lastIndex) HorizontalDivider()
                    }
                }
                if (!notificationGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    OutlinedButton(
                        onClick = {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Info, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("允许后台任务与审批通知")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!isValidWorkspacePath(cwd)) {
                        cwdError = "请输入真实的绝对目录路径"
                    } else {
                        onSave(endpoint, token, cwd)
                    }
                },
                enabled = endpoint.isNotBlank() && cwd.isNotBlank() && !state.busy,
            ) {
                Text("保存并连接")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )

    if (showDirectoryPicker) {
        RemoteDirectoryPickerDialog(
            initialPath = cwd.ifBlank { DEFAULT_TERMUX_HOME },
            onReadDirectories = onReadDirectories,
            onDismiss = { showDirectoryPicker = false },
            onSelect = { selectedPath ->
                cwd = selectedPath
                cwdError = null
                showDirectoryPicker = false
            },
        )
    }
}

@Composable
private fun McpStatusRow(server: McpServerStatus) {
    val statusColor = when (server.startupStatus) {
        "ready" -> Color(0xFF2DA44E)
        "starting" -> MaterialTheme.colorScheme.primary
        "failed", "cancelled" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(statusColor),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(server.displayName, style = MaterialTheme.typography.bodyMedium)
            Text(
                buildList {
                    server.startupStatus?.let { add(mcpStartupLabel(it)) }
                    add(mcpAuthLabel(server.authStatus))
                    add("${server.toolCount} 个工具")
                    if (server.resourceCount > 0) add("${server.resourceCount} 个资源")
                }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            server.error?.takeIf { it.isNotBlank() }?.let { error ->
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun mcpStartupLabel(status: String): String = when (status) {
    "starting" -> "正在启动"
    "ready" -> "已就绪"
    "failed" -> "启动失败"
    "cancelled" -> "已取消"
    else -> "状态未知"
}

private fun mcpAuthLabel(status: String): String = when (status) {
    "notLoggedIn" -> "未登录"
    "bearerToken" -> "令牌认证"
    "oAuth" -> "OAuth"
    "unsupported" -> "无需 OAuth"
    else -> "认证状态未知"
}

@Composable
private fun RemoteDirectoryPickerDialog(
    initialPath: String,
    onReadDirectories: (String, (List<RemoteDirectory>, String?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var currentPath by remember { mutableStateOf(initialPath) }
    var directories by remember { mutableStateOf(emptyList<RemoteDirectory>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentPath) {
        val requestedPath = currentPath
        loading = true
        error = null
        onReadDirectories(requestedPath) { result, failure ->
            if (currentPath == requestedPath) {
                directories = result
                error = failure
                loading = false
            }
        }
    }

    AlertDialog(
        modifier = Modifier.navigationBarsPadding(),
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.FolderOpen, contentDescription = null) },
        title = { Text("选择 Termux 工作目录") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    currentPath,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.primary,
                )
                if (loading) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (error != null) {
                    StatusMessage(
                        UiMessage(
                            id = "directory-error",
                            kind = MessageKind.ERROR,
                            text = error.orEmpty(),
                        )
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        if (currentPath != "/") {
                            item(key = "parent") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            currentPath = currentPath.substringBeforeLast('/')
                                                .ifBlank { "/" }
                                        }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Rounded.ArrowUpward, contentDescription = null)
                                    Spacer(Modifier.width(12.dp))
                                    Text("上一级")
                                }
                            }
                        }
                        items(directories, key = { it.path }) { directory ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { currentPath = directory.path }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.Folder, contentDescription = null)
                                Spacer(Modifier.width(12.dp))
                                Text(directory.name)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSelect(currentPath) }, enabled = !loading && error == null) {
                Text("选择此目录")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun reasoningLabel(value: String): String = value.replaceFirstChar { character ->
    if (character.isLowerCase()) character.titlecase() else character.toString()
}

@Composable
private fun ApprovalDialog(
    pending: PendingAction,
    onDecision: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val negative = pending.decisions.firstOrNull {
        it.key == "decline" || it.key == "cancel" || it.key == "denied" || it.key == "abort"
    }
    val positive = pending.decisions.filterNot { it === negative }
    AlertDialog(
        modifier = Modifier.navigationBarsPadding(),
        onDismissRequest = {},
        icon = { Icon(Icons.Rounded.ErrorOutline, contentDescription = null) },
        title = { Text(pending.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.heightIn(max = 360.dp),
                ) {
                    if (pending.kind == PendingKind.FILE_CHANGE) {
                        DiffViewer(
                            pending.detail.ifBlank { "Codex 请求应用文件修改。" },
                            Modifier.padding(14.dp),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .padding(14.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            androidx.compose.foundation.text.selection.SelectionContainer {
                                Text(
                                    pending.detail.ifBlank { "Codex 请求你的批准。" },
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = if (pending.kind == PendingKind.COMMAND) {
                                            FontFamily.Monospace
                                        } else {
                                            FontFamily.Default
                                        },
                                    ),
                                )
                            }
                        }
                    }
                }
                pending.rawParams.get("url")?.takeUnless { it.isJsonNull }?.asString?.let { url ->
                    OutlinedButton(
                        onClick = {
                            val uri = url.toUri()
                            if (uri.scheme?.lowercase() in setOf("http", "https")) {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("在浏览器中打开链接")
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                positive.forEachIndexed { index, decision ->
                    if (index == positive.lastIndex) {
                        Button(onClick = { onDecision(decision.key) }) { Text(decision.label) }
                    } else {
                        TextButton(onClick = { onDecision(decision.key) }) { Text(decision.label) }
                    }
                }
            }
        },
        dismissButton = {
            negative?.let { decision ->
                TextButton(onClick = { onDecision(decision.key) }) { Text(decision.label) }
            } ?: TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun UserInputDialog(
    pending: PendingAction,
    onSubmit: (Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val answers = remember(pending.requestId.toString()) { mutableStateMapOf<String, String>() }
    AlertDialog(
        modifier = Modifier.navigationBarsPadding(),
        onDismissRequest = {},
        icon = { Icon(Icons.Rounded.Info, contentDescription = null) },
        title = { Text(pending.title) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                if (pending.detail.isNotBlank()) {
                    Text(
                        pending.detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                pending.questions.forEach { question ->
                    val answer = answers[question.id].orEmpty()
                    val validationError = inputValidationError(question, answer)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(question.header, style = MaterialTheme.typography.labelLarge)
                        Text(question.prompt, style = MaterialTheme.typography.bodyMedium)
                        if (question.options.isNotEmpty()) {
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                question.options.forEach { option ->
                                    FilterChip(
                                        selected = answers[question.id] == option.label,
                                        onClick = { answers[question.id] = option.label },
                                        label = {
                                            Column {
                                                Text(option.label)
                                                if (option.description.isNotBlank()) {
                                                    Text(
                                                        option.description,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }
                                        },
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = answer,
                            onValueChange = { answers[question.id] = it },
                            label = { Text("回答") },
                            isError = validationError != null && answer.isNotEmpty(),
                            supportingText = validationError?.takeIf { answer.isNotEmpty() }?.let { error ->
                                { Text(error) }
                            },
                            visualTransformation = if (question.secret) {
                                PasswordVisualTransformation()
                            } else {
                                androidx.compose.ui.text.input.VisualTransformation.None
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            val requiredAnswered = pending.questions.all { question ->
                inputValidationError(question, answers[question.id].orEmpty()) == null
            }
            Button(
                onClick = { onSubmit(answers.toMap()) },
                enabled = requiredAnswered,
            ) { Text("提交") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
