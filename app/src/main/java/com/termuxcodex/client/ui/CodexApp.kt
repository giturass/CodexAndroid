package com.termuxcodex.client.ui

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Menu
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.termuxcodex.client.AppUiState
import com.termuxcodex.client.CodexViewModel
import com.termuxcodex.client.DEFAULT_TERMUX_HOME
import com.termuxcodex.client.ConnectionStatus
import com.termuxcodex.client.MessageKind
import com.termuxcodex.client.MessageOutcome
import com.termuxcodex.client.PendingAction
import com.termuxcodex.client.PendingKind
import com.termuxcodex.client.RemoteDirectory
import com.termuxcodex.client.ThreadSummary
import com.termuxcodex.client.UiMessage
import com.termuxcodex.client.inputValidationError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodexApp(viewModel: CodexViewModel) {
    val state = viewModel.uiState
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showSettings by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var scrollToMessageId by remember { mutableStateOf<String?>(null) }
    var threadToDelete by remember { mutableStateOf<ThreadSummary?>(null) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ConversationDrawer(
                state = state,
                formatTime = viewModel::formatThreadTime,
                onNewThread = {
                    viewModel.newThread()
                    scope.launch { drawerState.close() }
                },
                onOpenThread = {
                    viewModel.openThread(it)
                    scope.launch { drawerState.close() }
                },
                onDeleteThread = { threadToDelete = it },
                onRefresh = viewModel::refreshThreads,
                onSettings = {
                    showSettings = true
                    scope.launch { drawerState.close() }
                },
            )
        },
    ) {
        Scaffold(
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
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Rounded.Menu, contentDescription = "会话记录")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSearch = true }) {
                            Icon(Icons.Rounded.Search, contentDescription = "搜索")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
            bottomBar = {
                PromptBar(
                    connected = state.connectionStatus == ConnectionStatus.CONNECTED,
                    busy = state.busy,
                    onSend = viewModel::sendPrompt,
                    onStop = viewModel::interruptTurn,
                )
            },
        ) { padding ->
            key(state.currentThreadId) {
                ConversationContent(
                    state = state,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    onConnect = viewModel::connect,
                    onSuggestion = { viewModel.sendPrompt(it) },
                    onLoadOlder = viewModel::loadOlderHistory,
                    scrollToMessageId = scrollToMessageId,
                    onSearchScrollHandled = { scrollToMessageId = null },
                )
            }
        }
    }

    if (showSettings) {
        ConnectionSettingsDialog(
            state = state,
            onDismiss = {
                showSettings = false
                if (state.skillsCwd != state.cwd) viewModel.refreshSkills()
            },
            onSave = { endpoint, token, cwd, model, effort, skills ->
                viewModel.updateSettings(endpoint, token, cwd, model, effort, skills)
                showSettings = false
                viewModel.connect()
            },
            onRefreshModels = viewModel::refreshModels,
            onRefreshSkills = { cwd -> viewModel.refreshSkills(cwd, forceReload = true) },
            onReadDirectories = viewModel::readDirectories,
        )
    }

    if (showSearch) {
        SearchDialog(
            state = state,
            onDismiss = { showSearch = false },
            onMessageSelected = { messageId ->
                scrollToMessageId = messageId
                showSearch = false
            },
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

@Composable
private fun ConversationDrawer(
    state: AppUiState,
    formatTime: (Long) -> String,
    onNewThread: () -> Unit,
    onOpenThread: (String) -> Unit,
    onDeleteThread: (ThreadSummary) -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
) {
    ModalDrawerSheet(modifier = Modifier.widthIn(max = 336.dp)) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 12.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Terminal, contentDescription = null)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Codex Android", style = MaterialTheme.typography.titleMedium)
                    Text(
                        state.endpoint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "刷新会话")
                }
            }

            NavigationDrawerItem(
                label = { Text("新会话") },
                selected = state.currentThreadId == null,
                onClick = onNewThread,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                modifier = Modifier.padding(vertical = 4.dp),
            )

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
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(state.threads, key = { it.id }) { thread ->
                    ThreadDrawerItem(
                        thread = thread,
                        selected = thread.id == state.currentThreadId,
                        time = formatTime(thread.updatedAt),
                        onClick = { onOpenThread(thread.id) },
                        onDelete = { onDeleteThread(thread) },
                        deleteEnabled = !state.busy || thread.id != state.currentThreadId,
                    )
                }
            }

            NavigationDrawerItem(
                label = { Text("设置") },
                selected = false,
                onClick = onSettings,
                icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                modifier = Modifier.padding(vertical = 8.dp),
            )

        }
    }
}

@Composable
private fun ThreadDrawerItem(
    thread: ThreadSummary,
    selected: Boolean,
    time: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    deleteEnabled: Boolean,
) {
    NavigationDrawerItem(
        label = {
            Column {
                Text(
                    thread.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (time.isNotBlank()) {
                    Text(
                        time,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        selected = selected,
        onClick = onClick,
        icon = { Icon(Icons.Rounded.History, contentDescription = null) },
        badge = {
            IconButton(
                onClick = onDelete,
                enabled = deleteEnabled,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = "删除会话",
                    modifier = Modifier.size(18.dp),
                )
            }
        },
    )
}

@Composable
private fun ConversationContent(
    state: AppUiState,
    modifier: Modifier,
    onConnect: () -> Unit,
    onSuggestion: (String) -> Unit,
    onLoadOlder: () -> Unit,
    scrollToMessageId: String?,
    onSearchScrollHandled: () -> Unit,
) {
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
    LaunchedEffect(scrollToMessageId, state.messages.size) {
        val messageId = scrollToMessageId ?: return@LaunchedEffect
        val messageIndex = state.messages.indexOfFirst { it.id == messageId }
        if (messageIndex >= 0) {
            autoScroll = false
            val headerOffset = if (state.historyNextCursor != null || state.historyLoading) 1 else 0
            listState.scrollToItem(messageIndex + headerOffset)
        }
        onSearchScrollHandled()
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
    val serverCommand = "./start-codex-server.sh"
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
                    Text("连接 Codex Android", style = MaterialTheme.typography.titleLarge)
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
                                ClipEntry(ClipData.newPlainText("Codex App Server 命令", serverCommand))
                            )
                        }
                    }) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "复制命令")
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "客户端地址：${state.endpoint}",
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Code,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                message.title ?: "执行过程",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.tertiary,
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
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(17.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            CopyTextButton(message.text, "复制执行内容")
            Icon(
                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = if (expanded) "折叠执行过程" else "展开执行过程",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded && message.text.isNotBlank()) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = if (showAllOutput) 520.dp else 320.dp)
                        .verticalScroll(scrollState)
                        .padding(start = 14.dp, end = 14.dp, bottom = 8.dp),
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
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 18.sp,
                            ),
                        )
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

@Composable
private fun PromptBar(
    connected: Boolean,
    busy: Boolean,
    onSend: (String) -> Boolean,
    onStop: () -> Unit,
) {
    var prompt by rememberSaveable { mutableStateOf("") }
    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Column {
            AnimatedVisibility(visible = busy) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 18.dp, top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = "任务执行中，可点击停止按钮中断",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    modifier = Modifier.weight(1f),
                    enabled = connected && !busy,
                    placeholder = {
                        Text(if (connected) "给 Codex 分配任务…" else "连接后即可开始")
                    },
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (prompt.isNotBlank() && connected && !busy) {
                            if (onSend(prompt)) prompt = ""
                        }
                    }),
                )
                Spacer(Modifier.width(10.dp))
                FilledIconButton(
                    onClick = {
                        if (busy) {
                            onStop()
                        } else if (prompt.isNotBlank()) {
                            if (onSend(prompt)) prompt = ""
                        }
                    },
                    enabled = connected && (busy || prompt.isNotBlank()),
                    modifier = Modifier.size(52.dp),
                ) {
                    Icon(
                        if (busy) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.Send,
                        contentDescription = if (busy) "停止任务" else "发送",
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchDialog(
    state: AppUiState,
    onDismiss: () -> Unit,
    onMessageSelected: (String) -> Unit,
    onThreadSelected: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val normalizedQuery = query.trim()
    val messageResults by produceState(emptyList<UiMessage>(), normalizedQuery, state.messages) {
        value = if (normalizedQuery.isBlank()) {
            emptyList()
        } else {
            withContext(Dispatchers.Default) {
                state.messages.asSequence()
                    .filter { it.text.contains(normalizedQuery, ignoreCase = true) }
                    .take(MAX_SEARCH_RESULTS)
                    .toList()
            }
        }
    }
    val threadResults = remember(normalizedQuery, state.threads) {
        if (normalizedQuery.isBlank()) emptyList() else state.threads
            .asSequence()
            .filter {
                it.title.contains(normalizedQuery, ignoreCase = true) ||
                    it.cwd.contains(normalizedQuery, ignoreCase = true)
            }
            .take(MAX_SEARCH_RESULTS)
            .toList()
    }
    AlertDialog(
        modifier = Modifier.navigationBarsPadding(),
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        title = { Text("搜索") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("查找消息或会话") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = "清空搜索")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "搜索当前已加载的消息和最近会话",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (normalizedQuery.isBlank()) {
                        item { Text("输入关键词开始查找") }
                    } else if (messageResults.isEmpty() && threadResults.isEmpty()) {
                        item { Text("没有找到相关内容") }
                    }
                    if (messageResults.isNotEmpty()) {
                        item {
                            Text(
                                "当前会话 · ${messageResults.size}",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                        items(messageResults, key = { "message-${it.id}" }) { message ->
                            SearchResultItem(
                                title = messageKindLabel(message.kind),
                                preview = message.text.searchPreview(normalizedQuery),
                                onClick = { onMessageSelected(message.id) },
                            )
                        }
                    }
                    if (threadResults.isNotEmpty()) {
                        item {
                            Text(
                                "最近会话 · ${threadResults.size}",
                                style = MaterialTheme.typography.labelLarge,
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
        confirmButton = {},
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
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                preview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun messageKindLabel(kind: MessageKind): String = when (kind) {
    MessageKind.USER -> "你的消息"
    MessageKind.ASSISTANT -> "Codex 回复"
    MessageKind.TOOL -> "执行输出"
    MessageKind.INFO -> "提示"
    MessageKind.ERROR -> "错误"
}

private fun String.searchPreview(query: String): String {
    val index = indexOf(query, ignoreCase = true).coerceAtLeast(0)
    val start = (index - 60).coerceAtLeast(0)
    val end = (index + query.length + 140).coerceAtMost(length)
    val compact = substring(start, end).replace(Regex("\\s+"), " ").trim()
    return buildString {
        if (start > 0) append("…")
        append(compact)
        if (end < length) append("…")
    }
}

private const val MAX_SEARCH_RESULTS = 50

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectionSettingsDialog(
    state: AppUiState,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, Set<String>) -> Unit,
    onRefreshModels: () -> Unit,
    onRefreshSkills: (String) -> Unit,
    onReadDirectories: (String, (List<RemoteDirectory>, String?) -> Unit) -> Unit,
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
    var model by rememberSaveable { mutableStateOf(state.model) }
    var reasoningEffort by rememberSaveable { mutableStateOf(state.reasoningEffort) }
    var selectedSkillPaths by rememberSaveable { mutableStateOf(state.selectedSkillPaths) }
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var reasoningMenuExpanded by remember { mutableStateOf(false) }
    var skillsMenuExpanded by remember { mutableStateOf(false) }
    var showDirectoryPicker by remember { mutableStateOf(false) }
    val selectedModel = state.availableModels.firstOrNull { it.model == model }
    val catalogDefaultModel = state.availableModels.firstOrNull { it.isDefault }
    val configuredModel = state.availableModels.firstOrNull { it.model == state.configModel }
    val effectiveDefaultModel = configuredModel ?: catalogDefaultModel
    val reasoningModel = selectedModel ?: effectiveDefaultModel
    val reasoningOptions = reasoningModel?.supportedReasoningEfforts.orEmpty()
    val selectedReasoning = reasoningOptions.firstOrNull { it.value == reasoningEffort }
    val configuredReasoningEffort = state.configReasoningEffort.takeIf { configuredEffort ->
        configuredEffort.isNotBlank() &&
            (reasoningOptions.isEmpty() || reasoningOptions.any { it.value == configuredEffort })
    }
    val effectiveDefaultReasoningEffort = configuredReasoningEffort
        ?: reasoningModel?.defaultReasoningEffort.orEmpty()
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
                        label = { Text("Termux 工作目录") },
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ExposedDropdownMenuBox(
                        expanded = modelMenuExpanded,
                        onExpandedChange = {
                            if (!state.modelsLoading && state.availableModels.isNotEmpty()) {
                                modelMenuExpanded = !modelMenuExpanded
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        OutlinedTextField(
                            value = when {
                                model.isBlank() && state.configModel.isNotBlank() ->
                                    "Codex 配置 · ${configuredModel?.displayName ?: state.configModel}"

                                model.isBlank() && catalogDefaultModel != null ->
                                    "Codex 默认 · ${catalogDefaultModel.displayName}"

                                model.isBlank() -> "默认模型"
                                selectedModel != null -> selectedModel.displayName
                                else -> model
                            },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("模型") },
                            supportingText = {
                                Text(
                                    when {
                                        state.modelsLoading -> "正在读取可用模型…"
                                        state.modelsError != null -> state.modelsError
                                        selectedModel != null && selectedModel.description.isNotBlank() ->
                                            "${selectedModel.model} · ${selectedModel.description}"

                                        model.isNotBlank() -> model
                                        state.configModel.isNotBlank() ->
                                            "${state.configModel} · 来自 Codex 有效配置"

                                        else -> "使用 Codex 配置中的默认模型"
                                    },
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            trailingIcon = {
                                if (state.modelsLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    ExposedDropdownMenuDefaults.TrailingIcon(
                                        expanded = modelMenuExpanded,
                                    )
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                        )
                        ExposedDropdownMenu(
                            expanded = modelMenuExpanded,
                            onDismissRequest = { modelMenuExpanded = false },
                            modifier = Modifier.heightIn(max = 360.dp),
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("使用 Codex 配置")
                                        Text(
                                            state.configModel.ifBlank {
                                                catalogDefaultModel?.model ?: "由 App Server 决定"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        configuredModel?.takeIf { it.description.isNotBlank() }?.let {
                                            Text(
                                                it.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    model = ""
                                    if (reasoningEffort !in effectiveDefaultModel
                                            ?.supportedReasoningEfforts
                                            .orEmpty()
                                            .map { it.value }
                                    ) {
                                        reasoningEffort = ""
                                    }
                                    modelMenuExpanded = false
                                },
                            )
                            state.availableModels.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(option.displayName)
                                            Text(
                                                option.model,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontFamily = FontFamily.Monospace,
                                                ),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    },
                                    onClick = {
                                        model = option.model
                                        if (reasoningEffort !in option.supportedReasoningEfforts
                                                .map { it.value }
                                        ) {
                                            reasoningEffort = ""
                                        }
                                        modelMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    IconButton(
                        onClick = onRefreshModels,
                        enabled = state.connectionStatus == ConnectionStatus.CONNECTED &&
                            !state.modelsLoading,
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "刷新模型列表")
                    }
                }
                ExposedDropdownMenuBox(
                    expanded = reasoningMenuExpanded,
                    onExpandedChange = {
                        if (reasoningOptions.isNotEmpty()) {
                            reasoningMenuExpanded = !reasoningMenuExpanded
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = when {
                            reasoningEffort.isBlank() && reasoningModel != null ->
                                "Codex 配置 · ${reasoningLabel(effectiveDefaultReasoningEffort)}"

                            reasoningEffort.isBlank() -> "默认思考深度"
                            else -> reasoningLabel(reasoningEffort)
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("思考深度") },
                        supportingText = {
                            Text(
                                selectedReasoning?.description ?: when {
                                    configuredReasoningEffort != null -> "来自 Codex 有效配置"
                                    reasoningModel != null -> "使用模型目录建议的默认思考深度"
                                    else -> "由 App Server 根据 Codex 配置决定"
                                },
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(
                                expanded = reasoningMenuExpanded,
                            )
                        },
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = reasoningMenuExpanded,
                        onDismissRequest = { reasoningMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text("使用 Codex 配置")
                                    if (effectiveDefaultReasoningEffort.isNotBlank()) {
                                        Text(
                                            reasoningLabel(effectiveDefaultReasoningEffort),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            },
                            onClick = {
                                reasoningEffort = ""
                                reasoningMenuExpanded = false
                            },
                        )
                        reasoningOptions.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(reasoningLabel(option.value))
                                        if (option.description.isNotBlank()) {
                                            Text(
                                                option.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    reasoningEffort = option.value
                                    reasoningMenuExpanded = false
                                },
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ExposedDropdownMenuBox(
                        expanded = skillsMenuExpanded,
                        onExpandedChange = {
                            if (!state.skillsLoading && state.availableSkills.isNotEmpty()) {
                                skillsMenuExpanded = !skillsMenuExpanded
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        OutlinedTextField(
                            value = if (selectedSkillPaths.isEmpty()) {
                                "未指定 Skills"
                            } else {
                                "已选择 ${selectedSkillPaths.size} 个 Skills"
                            },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Skills") },
                            supportingText = {
                                Text(
                                    when {
                                        state.skillsLoading -> "正在扫描工作目录与用户 Skills…"
                                        state.skillsError != null -> state.skillsError
                                        state.skillsCwd != cwd -> "刷新以读取当前工作目录的 Skills"
                                        else -> "所选 Skill 会作为任务输入发送给 Codex"
                                    },
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            trailingIcon = {
                                if (state.skillsLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    ExposedDropdownMenuDefaults.TrailingIcon(
                                        expanded = skillsMenuExpanded,
                                    )
                                }
                            },
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                        )
                        ExposedDropdownMenu(
                            expanded = skillsMenuExpanded,
                            onDismissRequest = { skillsMenuExpanded = false },
                            modifier = Modifier.heightIn(max = 360.dp),
                        ) {
                            if (selectedSkillPaths.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("清除全部选择") },
                                    onClick = { selectedSkillPaths = emptySet() },
                                )
                            }
                            state.availableSkills.forEach { skill ->
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Checkbox(
                                            checked = skill.path in selectedSkillPaths,
                                            onCheckedChange = null,
                                        )
                                    },
                                    text = {
                                        Column {
                                            Text(skill.displayName)
                                            Text(
                                                listOf(skill.scope, skill.description)
                                                    .filter { it.isNotBlank() }
                                                    .joinToString(" · "),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedSkillPaths = if (skill.path in selectedSkillPaths) {
                                            selectedSkillPaths - skill.path
                                        } else {
                                            selectedSkillPaths + skill.path
                                        }
                                    },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    IconButton(
                        onClick = { onRefreshSkills(cwd) },
                        enabled = state.connectionStatus == ConnectionStatus.CONNECTED &&
                            cwd.isNotBlank() && !state.skillsLoading,
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "刷新 Skills")
                    }
                }
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("WebSocket Bearer Token（可选）") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
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
                    onSave(
                        endpoint,
                        token,
                        cwd,
                        model,
                        reasoningEffort,
                        selectedSkillPaths,
                    )
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
                onRefreshSkills(selectedPath)
            },
        )
    }
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

private fun reasoningLabel(value: String): String = when (value) {
    "none" -> "无"
    "minimal" -> "最少"
    "low" -> "低"
    "medium" -> "中"
    "high" -> "高"
    "xhigh" -> "极高"
    "max" -> "最高"
    "ultra" -> "Ultra（自动委派）"
    else -> value.ifBlank { "默认" }
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
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(
                            pending.detail.ifBlank { "Codex 请求你的批准。" },
                            modifier = Modifier
                                .padding(14.dp)
                                .verticalScroll(rememberScrollState()),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = if (pending.kind == PendingKind.COMMAND ||
                                    pending.kind == PendingKind.FILE_CHANGE
                                ) {
                                    FontFamily.Monospace
                                } else {
                                    FontFamily.Default
                                },
                            ),
                        )
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
