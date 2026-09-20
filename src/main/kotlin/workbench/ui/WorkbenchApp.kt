package workbench.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import workbench.config.SshConfig
import workbench.data.AuthType
import workbench.data.ServerProfile
import workbench.data.ServerProfileRepository
import workbench.sftp.RemoteEntry
import workbench.sftp.SftpClient
import workbench.sftp.formatFileSize
import workbench.ssh.SshSession
import java.awt.Toolkit
import java.awt.Window
import java.awt.datatransfer.StringSelection
import java.io.File
import java.nio.file.Path

private val R = RoundedCornerShape(10.dp)

enum class ViewMode {
    SINGLE,
    SPLIT
}

data class SessionState(
    val id: Int,
    val title: String,
    val profile: ServerProfile,
    val session: SshSession,
    val sftp: SftpClient,
)

@Composable
fun WorkbenchApp(
    repository: ServerProfileRepository,
    window: Window? = null
) {
    val profiles = remember { mutableStateListOf<ServerProfile>() }
    var activeProfile by remember { mutableStateOf<ServerProfile?>(null) }
    var showProfileDialog by remember { mutableStateOf(false) }

    fun refreshProfiles() {
        profiles.clear()
        profiles.addAll(repository.getAllProfiles())
        if (activeProfile == null || profiles.none { it.id == activeProfile?.id }) {
            activeProfile = profiles.firstOrNull()
        }
    }

    LaunchedEffect(Unit) {
        refreshProfiles()
    }

    val sessions = remember { mutableStateListOf<SessionState>() }
    var selectedPane1 by remember { mutableStateOf(0) }
    var selectedPane2 by remember { mutableStateOf(0) }
    var activeFocusedPane by remember { mutableStateOf(1) }
    var splitRatio by remember { mutableStateOf(0.5f) }
    var viewMode by remember { mutableStateOf(ViewMode.SINGLE) }
    var error by remember { mutableStateOf<String?>(null) }
    var isDraggingGlobal by remember { mutableStateOf(false) }

    fun addSession(profile: ServerProfile? = activeProfile) {
        val targetProfile = profile ?: return
        try {
            val sshConfig = targetProfile.toSshConfig()
            val ssh = SshSession(sshConfig)
            ssh.connect()
            val sftp = SftpClient(ssh.openSftp())
            val id = (sessions.maxOfOrNull { it.id } ?: 0) + 1
            sessions.add(SessionState(id, "세션 $id (${targetProfile.name})", targetProfile, ssh, sftp))
            selectedPane1 = sessions.lastIndex
            if (sessions.size > 1) {
                selectedPane2 = sessions.lastIndex
            }
            error = null
        } catch (e: Exception) {
            error = "${targetProfile.name} 연결 실패: ${e.message}"
        }
    }

    LaunchedEffect(activeProfile?.id) {
        if (sessions.isEmpty() && activeProfile != null) {
            addSession(activeProfile)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            sessions.forEach {
                it.sftp.close()
                it.session.close()
            }
        }
    }

    val connected = sessions.isNotEmpty()
    val isProd = activeProfile?.envTag == "PROD"
    val barColor = when (activeProfile?.envTag) {
        "PROD" -> TagProd.copy(alpha = 0.9f)
        "STAGING" -> TagStaging.copy(alpha = 0.9f)
        else -> Accent.copy(alpha = 0.85f)
    }

    WorkbenchTheme {
        Surface(Modifier.fillMaxSize(), color = AppBg) {
            Column(Modifier.fillMaxSize()) {
                Toolbar(
                    activeProfile = activeProfile,
                    connected = connected,
                    viewMode = viewMode,
                    onToggleViewMode = {
                        viewMode = if (viewMode == ViewMode.SINGLE) ViewMode.SPLIT else ViewMode.SINGLE
                        if (sessions.size >= 2) {
                            selectedPane2 = if (selectedPane1 == 0) 1 else 0
                        }
                    },
                    onOpenProfileManager = { showProfileDialog = true },
                    onNewTab = { addSession() }
                )

                if (viewMode == ViewMode.SINGLE && sessions.isNotEmpty()) {
                    SessionTabs(
                        sessions = sessions,
                        selected = selectedPane1.coerceIn(0, sessions.lastIndex),
                        onSelect = { selectedPane1 = it },
                        onCloseTab = { index ->
                            val s = sessions.removeAt(index)
                            s.sftp.close()
                            s.session.close()
                            selectedPane1 = selectedPane1.coerceIn(0, (sessions.size - 1).coerceAtLeast(0))
                        }
                    )
                }

                error?.let { ErrorBanner(it) }

                Box(Modifier.weight(1f).fillMaxWidth().padding(8.dp)) {
                    if (connected) {
                        when (viewMode) {
                            ViewMode.SINGLE -> {
                                val s = sessions[selectedPane1.coerceIn(0, sessions.lastIndex)]
                                SessionPane(
                                    state = s,
                                    window = window,
                                    isGlobalDragging = isDraggingGlobal,
                                    onGlobalDragChange = { isDraggingGlobal = it },
                                    isFocused = true,
                                    onFocus = { activeFocusedPane = 1 }
                                )
                            }
                            ViewMode.SPLIT -> {
                                Box(Modifier.fillMaxSize()) {
                                    val idx1 = selectedPane1.coerceIn(0, sessions.lastIndex)
                                    val idx2 = selectedPane2.coerceIn(0, sessions.lastIndex)

                                    Row(Modifier.fillMaxSize()) {
                                        // Left Pane
                                        Box(Modifier.weight(splitRatio.coerceIn(0.2f, 0.8f)).fillMaxHeight()) {
                                            Column(Modifier.fillMaxSize()) {
                                                SplitPaneHeader("패널 1", sessions, idx1) { selectedPane1 = it }
                                                Spacer(Modifier.height(4.dp))
                                                SessionPane(
                                                    state = sessions[idx1],
                                                    window = window,
                                                    isGlobalDragging = isDraggingGlobal,
                                                    onGlobalDragChange = { isDraggingGlobal = it },
                                                    isFocused = activeFocusedPane == 1,
                                                    onFocus = { activeFocusedPane = 1 }
                                                )
                                            }
                                        }

                                        // Draggable Split Divider
                                        Box(
                                            Modifier
                                                .width(10.dp)
                                                .fillMaxHeight()
                                                .pointerInput(Unit) {
                                                    detectDragGestures { change, dragAmount ->
                                                        change.consume()
                                                        val delta = dragAmount.x
                                                        // adjust ratio
                                                        splitRatio = (splitRatio + delta / 1200f).coerceIn(0.2f, 0.8f)
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Box(
                                                Modifier
                                                    .width(3.dp)
                                                    .fillMaxHeight()
                                                    .clip(RoundedCornerShape(2.dp))
                                                    .background(if (activeFocusedPane == 1) Accent else Hairline)
                                            )
                                        }

                                        // Right Pane
                                        Box(Modifier.weight((1f - splitRatio).coerceIn(0.2f, 0.8f)).fillMaxHeight()) {
                                            Column(Modifier.fillMaxSize()) {
                                                SplitPaneHeader("패널 2", sessions, idx2) { selectedPane2 = it }
                                                Spacer(Modifier.height(4.dp))
                                                SessionPane(
                                                    state = sessions[idx2],
                                                    window = window,
                                                    isGlobalDragging = isDraggingGlobal,
                                                    onGlobalDragChange = { isDraggingGlobal = it },
                                                    isFocused = activeFocusedPane == 2,
                                                    onFocus = { activeFocusedPane = 2 }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        DisconnectedChrome(onConnectClick = { showProfileDialog = true })
                    }
                }

                StatusBar(
                    profile = activeProfile,
                    connected = connected,
                    viewMode = viewMode,
                    barColor = barColor,
                    hint = error?.let { "오류 발생" } ?: if (connected) "세션 ${sessions.size}개 준비됨" else "오프라인",
                )
            }

            if (showProfileDialog) {
                ProfileManagerDialog(
                    profiles = profiles,
                    activeProfile = activeProfile,
                    onSelectProfile = { selected ->
                        activeProfile = selected
                        showProfileDialog = false
                        addSession(selected)
                    },
                    onSaveProfile = { profile ->
                        val saved = repository.saveProfile(profile)
                        refreshProfiles()
                        activeProfile = saved
                    },
                    onDeleteProfile = { id ->
                        repository.deleteProfile(id)
                        refreshProfiles()
                    },
                    onDismiss = { showProfileDialog = false }
                )
            }
        }
    }
}

@Composable
private fun Toolbar(
    activeProfile: ServerProfile?,
    connected: Boolean,
    viewMode: ViewMode,
    onToggleViewMode: () -> Unit,
    onOpenProfileManager: () -> Unit,
    onNewTab: () -> Unit,
) {
    val isProd = activeProfile?.envTag == "PROD"

    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(Chrome)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (connected) Ok else Danger))
        Text("Remote Workbench", style = MaterialTheme.typography.titleMedium)

        activeProfile?.let {
            val tagColor = when (it.envTag) {
                "PROD" -> TagProd
                "STAGING" -> TagStaging
                else -> TagDev
            }
            Button(
                onClick = onOpenProfileManager,
                modifier = Modifier.height(32.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isProd) TagProd.copy(alpha = 0.2f) else Panel,
                    contentColor = if (isProd) TagProd else TextPrimary
                ),
                shape = RoundedCornerShape(8.dp),
                border = if (isProd) androidx.compose.foundation.BorderStroke(1.dp, TagProd) else null
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("[${it.envTag}]", style = MaterialTheme.typography.labelSmall, color = tagColor)
                    Text("${it.name} (${it.username}@${it.host}:${it.port})", style = MaterialTheme.typography.labelMedium)
                }
            }
        } ?: run {
            Button(
                onClick = onOpenProfileManager,
                modifier = Modifier.height(32.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = TextMuted),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("서버 선택", style = MaterialTheme.typography.labelMedium)
            }
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onOpenProfileManager,
            modifier = Modifier.height(32.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = TextPrimary),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text("⚙️ 프로필 관리 (SQLite)", style = MaterialTheme.typography.labelMedium)
        }

        Button(
            onClick = onToggleViewMode,
            modifier = Modifier.height(32.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (viewMode == ViewMode.SPLIT) Accent else Panel,
                contentColor = if (viewMode == ViewMode.SPLIT) OnAccent else TextPrimary
            ),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(if (viewMode == ViewMode.SPLIT) "◫ 2분할 뷰 활성" else "◻ 단일 뷰", style = MaterialTheme.typography.labelMedium)
        }

        Button(
            onClick = onNewTab,
            modifier = Modifier.height(32.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (isProd) TagProd else Accent, contentColor = OnAccent),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text("+ 새 세션", style = MaterialTheme.typography.labelMedium.copy(color = OnAccent))
        }
    }
}

@Composable
private fun SessionTabs(
    sessions: List<SessionState>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onCloseTab: (Int) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Chrome)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        sessions.forEachIndexed { index, s ->
            val on = index == selected
            val isProd = s.profile.envTag == "PROD"
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) Panel else Color.Transparent)
                    .border(
                        1.dp,
                        if (on) (if (isProd) TagProd else Accent) else Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { onSelect(index) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isProd) {
                    Text("⚠️", fontSize = 11.sp)
                }
                Text(
                    s.title,
                    color = if (on) TextPrimary else TextMuted,
                    style = MaterialTheme.typography.labelMedium,
                )
                if (sessions.size > 1) {
                    Text(
                        "✕",
                        color = TextMuted,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onCloseTab(index) }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SplitPaneHeader(
    label: String,
    sessions: List<SessionState>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Chrome)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(label, color = Accent, style = MaterialTheme.typography.labelSmall)
        sessions.forEachIndexed { index, s ->
            val on = index == selected
            val isProd = s.profile.envTag == "PROD"
            Text(
                text = if (isProd) "⚠️ ${s.title}" else s.title,
                color = if (on) TextPrimary else TextMuted,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (on) Panel else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Text(
        message,
        color = Danger,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x33FF6B6B))
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

@Composable
private fun StatusBar(
    profile: ServerProfile?,
    connected: Boolean,
    viewMode: ViewMode,
    barColor: Color,
    hint: String
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(26.dp)
            .background(barColor)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(if (connected) "SSH" else "OFF", color = OnAccent, style = MaterialTheme.typography.labelSmall)
        profile?.let {
            Text("[${it.envTag}] ${it.name}", color = OnAccent, style = MaterialTheme.typography.labelSmall)
            Text("${it.username}@${it.host}:${it.port}", color = OnAccent, style = MaterialTheme.typography.labelSmall)
        }
        Text(if (viewMode == ViewMode.SPLIT) "모드: 2분할" else "모드: 단일", color = OnAccent, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.weight(1f))
        Text(hint, color = OnAccent, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun DisconnectedChrome(onConnectClick: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("연결된 SSH 세션이 없습니다.", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = onConnectClick,
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent)
            ) {
                Text("서버 프로필 선택 및 연결")
            }
        }
    }
}

@Composable
private fun SessionPane(
    state: SessionState,
    window: Window?,
    isGlobalDragging: Boolean,
    onGlobalDragChange: (Boolean) -> Unit,
    isFocused: Boolean,
    onFocus: () -> Unit
) {
    var cwd by remember(state.id) { mutableStateOf(state.profile.remoteDir) }
    var entries by remember(state.id) { mutableStateOf(listOf<RemoteEntry>()) }
    var uploadPath by remember { mutableStateOf("") }
    var shellOut by remember(state.id) { mutableStateOf("") }
    var shellIn by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var explorerCollapsed by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val terminalScrollState = rememberScrollState()

    // Command History
    val history = remember(state.id) { mutableStateListOf<String>() }
    var historyIndex by remember(state.id) { mutableStateOf(-1) }

    fun refresh() {
        try {
            entries = state.sftp.listDir(cwd)
            status = ""
        } catch (e: Exception) {
            status = "목록 실패: ${e.message}"
        }
    }

    fun sendCommand() {
        if (shellIn.isBlank()) return
        val cmd = shellIn
        history.add(cmd)
        historyIndex = -1
        try {
            val out = state.session.outputStream()
            out.write((cmd + "\n").toByteArray())
            out.flush()
            shellIn = ""
        } catch (e: Exception) {
            status = "전송 실패: ${e.message}"
        }
    }

    fun sendInterrupt() {
        try {
            val out = state.session.outputStream()
            out.write(byteArrayOf(0x03)) // Ctrl+C
            out.flush()
            status = "인터럽트(Ctrl+C) 전송됨"
        } catch (e: Exception) {
            status = "인터럽트 실패: ${e.message}"
        }
    }

    LaunchedEffect(state.id, cwd) { refresh() }

    LaunchedEffect(state.id) {
        val buf = ByteArray(4096)
        while (isActive && state.session.connected) {
            val n = withContext(Dispatchers.IO) {
                val available = state.session.inputStream().available()
                if (available > 0) state.session.inputStream().read(buf, 0, minOf(available, buf.size)) else 0
            }
            if (n > 0) {
                val chunk = String(buf, 0, n)
                shellOut = if (shellOut.length > 50000) shellOut.takeLast(40000) + chunk else shellOut + chunk
                // Auto scroll to bottom
                delay(20)
                terminalScrollState.animateScrollTo(terminalScrollState.maxValue)
            } else {
                delay(80)
            }
        }
    }

    // Drag & Drop Handler
    FileDropHandler(
        window = window,
        enabled = true,
        onDragStateChanged = onGlobalDragChange,
        onFilesDropped = { paths ->
            scope.launch {
                try {
                    status = "업로드 중 (${paths.size}개 파일)..."
                    withContext(Dispatchers.IO) {
                        for (path in paths) {
                            val remote = cwd.trimEnd('/') + "/" + path.fileName
                            state.sftp.upload(path, remote)
                        }
                    }
                    refresh()
                    status = "드롭 업로드 완료: ${paths.joinToString { it.fileName.toString() }}"
                } catch (e: Exception) {
                    status = "드롭 업로드 실패: ${e.message}"
                }
            }
        }
    )

    val isProd = state.profile.envTag == "PROD"
    val paneBorderColor = if (isFocused) (if (isProd) TagProd else Accent) else Hairline

    Row(
        Modifier
            .fillMaxSize()
            .clickable(onClick = onFocus),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // SFTP Explorer Column
        if (!explorerCollapsed) {
            Column(
                Modifier
                    .width(290.dp)
                    .fillMaxHeight()
                    .clip(R)
                    .background(Chrome)
                    .border(1.dp, if (isGlobalDragging) Accent else paneBorderColor, R)
                    .padding(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📁 SFTP 탐색기", style = MaterialTheme.typography.labelSmall, color = Accent)
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = { showNewFolderDialog = true },
                        modifier = Modifier.height(24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = TextPrimary),
                        shape = RoundedCornerShape(4.dp)
                    ) { Text("+ 폴더", fontSize = 10.sp) }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "◀ 접기",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        modifier = Modifier
                            .clickable { explorerCollapsed = true }
                            .padding(4.dp)
                    )
                }

                Spacer(Modifier.height(4.dp))

                // Clickable Breadcrumb Bar
                BreadcrumbBar(cwd = cwd, onNavigate = { cwd = it })

                Spacer(Modifier.height(6.dp))

                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Panel),
                ) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        item {
                            FileRow(
                                entry = RemoteEntry("..", Path.of(cwd).parent?.toString()?.replace('\\', '/') ?: "/", true, 0),
                                isParent = true,
                                onOpen = {
                                    cwd = Path.of(cwd).parent?.toString()?.replace('\\', '/') ?: "/"
                                },
                                onDownload = {},
                                onDelete = {},
                                onCopyPath = {}
                            )
                        }
                        items(entries, key = { it.path }) { e ->
                            FileRow(
                                entry = e,
                                isParent = false,
                                onOpen = {
                                    if (e.isDir) cwd = e.path
                                },
                                onDownload = {
                                    scope.launch {
                                        try {
                                            val downloadsDir = File(System.getProperty("user.home"), "Downloads")
                                            if (!downloadsDir.exists()) downloadsDir.mkdirs()
                                            val target = File(downloadsDir, e.name).toPath()
                                            status = "다운로드 중: ${e.name}..."
                                            withContext(Dispatchers.IO) {
                                                state.sftp.download(e.path, target)
                                            }
                                            status = "다운로드 완료: Downloads/${e.name}"
                                        } catch (err: Exception) {
                                            status = "다운로드 실패: ${err.message}"
                                        }
                                    }
                                },
                                onDelete = {
                                    scope.launch {
                                        try {
                                            withContext(Dispatchers.IO) {
                                                state.sftp.delete(e.path, e.isDir)
                                            }
                                            refresh()
                                            status = "삭제됨: ${e.name}"
                                        } catch (err: Exception) {
                                            status = "삭제 실패: ${err.message}"
                                        }
                                    }
                                },
                                onCopyPath = {
                                    val sel = StringSelection(e.path)
                                    Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)
                                    status = "경로 복사됨: ${e.path}"
                                }
                            )
                        }
                    }

                    if (isGlobalDragging) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color(0xD91B1B1F))
                                .border(2.dp, Accent, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "📥 여기에 드롭하여\n$cwd 에 업로드",
                                color = Accent,
                                style = MaterialTheme.typography.titleMedium,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))
                CommandField(uploadPath, { uploadPath = it }, "로컬 파일 경로 (또는 드래그 앤 드롭)", enabled = true)
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = {
                        try {
                            val local = Path.of(uploadPath)
                            val remote = cwd.trimEnd('/') + "/" + local.fileName
                            state.sftp.upload(local, remote)
                            refresh()
                            status = "업로드됨: $remote"
                            uploadPath = ""
                        } catch (e: Exception) {
                            status = "업로드 실패: ${e.message}"
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(32.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = TextPrimary),
                    shape = RoundedCornerShape(8.dp),
                ) { Text("파일 업로드") }

                if (status.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(status, color = if (status.contains("실패")) Danger else TextMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
        } else {
            // Collapsed Explorer Bar
            Column(
                Modifier
                    .width(36.dp)
                    .fillMaxHeight()
                    .clip(R)
                    .background(Chrome)
                    .border(1.dp, paneBorderColor, R)
                    .clickable { explorerCollapsed = false }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("▶", color = Accent, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(12.dp))
                Text(
                    "탐\n색\n기",
                    color = TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                    lineHeight = 16.sp
                )
            }
        }

        // Terminal Column
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(R)
                .background(Chrome)
                .border(1.dp, paneBorderColor, R)
                .padding(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⚡ TERMINAL", style = MaterialTheme.typography.labelSmall, color = if (isProd) TagProd else Accent)
                Spacer(Modifier.width(8.dp))
                Text(state.title, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                Spacer(Modifier.weight(1f))

                Button(
                    onClick = { sendInterrupt() },
                    modifier = Modifier.height(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Danger),
                    shape = RoundedCornerShape(4.dp)
                ) { Text("Ctrl+C", fontSize = 10.sp) }

                Spacer(Modifier.width(6.dp))

                Button(
                    onClick = { shellOut = "" },
                    modifier = Modifier.height(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = TextMuted),
                    shape = RoundedCornerShape(4.dp)
                ) { Text("Clear", fontSize = 10.sp) }
            }

            Spacer(Modifier.height(4.dp))

            // ANSI Colored Terminal Output Box
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(TermBg)
                    .verticalScroll(terminalScrollState)
                    .padding(10.dp),
            ) {
                SelectionContainer {
                    val parsed = remember(shellOut) { parseAnsi(shellOut.ifEmpty { "$ " }, defaultColor = TermFg) }
                    Text(
                        text = parsed,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // Command Input with Arrow Key History & Enter
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    CommandFieldWithHistory(
                        value = shellIn,
                        onValueChange = { shellIn = it },
                        placeholder = "명령 입력 · Enter 전송 (↑/↓ 이전 명령)",
                        onSubmit = { sendCommand() },
                        onHistoryUp = {
                            if (history.isNotEmpty()) {
                                if (historyIndex < history.size - 1) {
                                    historyIndex++
                                    shellIn = history[history.size - 1 - historyIndex]
                                }
                            }
                        },
                        onHistoryDown = {
                            if (history.isNotEmpty()) {
                                if (historyIndex > 0) {
                                    historyIndex--
                                    shellIn = history[history.size - 1 - historyIndex]
                                } else if (historyIndex == 0) {
                                    historyIndex = -1
                                    shellIn = ""
                                }
                            }
                        }
                    )
                }

                Button(
                    onClick = { sendCommand() },
                    modifier = Modifier.height(36.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (isProd) TagProd else Accent, contentColor = OnAccent),
                    shape = RoundedCornerShape(8.dp),
                ) { Text("실행") }
            }
        }
    }

    if (showNewFolderDialog) {
        Dialog(onDismissRequest = { showNewFolderDialog = false }) {
            Surface(
                modifier = Modifier.width(360.dp).clip(RoundedCornerShape(10.dp)),
                color = Chrome
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("새 폴더 생성", style = MaterialTheme.typography.titleMedium)
                    ProfileTextField("폴더 이름", newFolderName, { newFolderName = it })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (newFolderName.isNotBlank()) {
                                    try {
                                        val target = cwd.trimEnd('/') + "/" + newFolderName.trim()
                                        state.sftp.mkdir(target)
                                        refresh()
                                        status = "폴더 생성됨: $newFolderName"
                                        newFolderName = ""
                                        showNewFolderDialog = false
                                    } catch (e: Exception) {
                                        status = "폴더 생성 실패: ${e.message}"
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
                            shape = RoundedCornerShape(6.dp)
                        ) { Text("생성") }

                        OutlinedButton(
                            onClick = { showNewFolderDialog = false },
                            shape = RoundedCornerShape(6.dp)
                        ) { Text("취소") }
                    }
                }
            }
        }
    }
}

@Composable
private fun BreadcrumbBar(cwd: String, onNavigate: (String) -> Unit) {
    val segments = cwd.split("/").filter { it.isNotEmpty() }
    Row(
        Modifier
            .fillMaxWidth()
            .background(Panel)
            .clip(RoundedCornerShape(6.dp))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            "/",
            color = Accent,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.clickable { onNavigate("/") }.padding(horizontal = 4.dp, vertical = 2.dp)
        )
        var acc = ""
        segments.forEach { seg ->
            acc += "/$seg"
            val target = acc
            Text(">", color = TextMuted, fontSize = 9.sp)
            Text(
                seg,
                color = TextPrimary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onNavigate(target) }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

fun fileIcon(entry: RemoteEntry): String {
    if (entry.isDir) return "📁"
    val lower = entry.name.lowercase()
    return when {
        lower.endsWith(".sh") || lower.endsWith(".bash") || lower.endsWith(".zsh") -> "⚡"
        lower.endsWith(".conf") || lower.endsWith(".yml") || lower.endsWith(".yaml") || lower.endsWith(".json") || lower.endsWith(".properties") || lower.endsWith(".env") || lower.endsWith(".xml") -> "⚙️"
        lower.endsWith(".log") || lower.endsWith(".out") || lower.endsWith(".err") || lower.endsWith(".txt") -> "📄"
        lower.endsWith(".tar") || lower.endsWith(".gz") || lower.endsWith(".zip") || lower.endsWith(".jar") || lower.endsWith(".7z") -> "📦"
        lower.endsWith(".kt") || lower.endsWith(".java") || lower.endsWith(".py") || lower.endsWith(".go") || lower.endsWith(".rs") || lower.endsWith(".c") || lower.endsWith(".cpp") || lower.endsWith(".ts") || lower.endsWith(".js") -> "💻"
        else -> "📝"
    }
}

@Composable
private fun FileRow(
    entry: RemoteEntry,
    isParent: Boolean,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onCopyPath: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val icon = if (isParent) "⤴️" else fileIcon(entry)
        Text(icon, fontSize = 13.sp)
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (entry.isDir && !isParent) "${entry.name}/" else entry.name,
            color = if (entry.isDir) DirColor else FileColor,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
        if (!entry.isDir && !isParent) {
            Text(
                formatFileSize(entry.size),
                color = TextMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.width(6.dp))
            // Quick action icons
            Text(
                "⬇",
                color = Accent,
                fontSize = 11.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onDownload() }
                    .padding(2.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "🗑️",
                fontSize = 11.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onDelete() }
                    .padding(2.dp)
            )
        }
    }
}

@Composable
private fun CommandFieldWithHistory(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean = true,
    onSubmit: () -> Unit,
    onHistoryUp: () -> Unit,
    onHistoryDown: () -> Unit,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
        cursorBrush = SolidColor(Accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(TermBg)
            .border(1.dp, Hairline, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .onPreviewKeyEvent { event ->
                if (event.key == Key.DirectionUp) {
                    onHistoryUp()
                    true
                } else if (event.key == Key.DirectionDown) {
                    onHistoryDown()
                    true
                } else {
                    false
                }
            },
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, color = TextMuted, style = MaterialTheme.typography.labelMedium)
                inner()
            }
        },
    )
}

@Composable
private fun CommandField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean = true,
    onSubmit: (() -> Unit)? = null,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
        cursorBrush = SolidColor(Accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSubmit?.invoke() }),
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(TermBg)
            .border(1.dp, Hairline, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, color = TextMuted, style = MaterialTheme.typography.labelMedium)
                inner()
            }
        },
    )
}

@Composable
private fun ProfileManagerDialog(
    profiles: List<ServerProfile>,
    activeProfile: ServerProfile?,
    onSelectProfile: (ServerProfile) -> Unit,
    onSaveProfile: (ServerProfile) -> Unit,
    onDeleteProfile: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var editingProfile by remember { mutableStateOf<ServerProfile?>(null) }
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("22") }
    var username by remember { mutableStateOf("root") }
    var authType by remember { mutableStateOf(AuthType.PASSWORD) }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var keyPath by remember { mutableStateOf("") }
    var remoteDir by remember { mutableStateOf("/tmp") }
    var envTag by remember { mutableStateOf("DEV") }
    var formError by remember { mutableStateOf<String?>(null) }
    var testStatus by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun startNewForm() {
        editingProfile = ServerProfile(name = "", host = "", port = 22, username = "root", authType = AuthType.PASSWORD, remoteDir = "/tmp", envTag = "DEV")
        name = ""
        host = ""
        port = "22"
        username = "root"
        authType = AuthType.PASSWORD
        password = ""
        showPassword = false
        keyPath = ""
        remoteDir = "/tmp"
        envTag = "DEV"
        formError = null
        testStatus = null
    }

    fun startEditForm(p: ServerProfile) {
        editingProfile = p
        name = p.name
        host = p.host
        port = p.port.toString()
        username = p.username
        authType = p.authType
        password = p.password.orEmpty()
        showPassword = false
        keyPath = p.keyPath.orEmpty()
        remoteDir = p.remoteDir
        envTag = p.envTag
        formError = null
        testStatus = null
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(660.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Chrome)
                .border(1.dp, Hairline, RoundedCornerShape(12.dp)),
            color = Chrome
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("서버 프로필 관리 (SQLite)", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = { startNewForm() },
                        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("+ 새 서버 등록")
                    }
                }

                HorizontalDivider(color = Hairline)

                // List of profiles
                Text("저장된 서버 목록 (${profiles.size}개)", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Panel)
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(profiles, key = { it.id }) { p ->
                        val isSelected = p.id == activeProfile?.id
                        val tagColor = when (p.envTag) {
                            "PROD" -> TagProd
                            "STAGING" -> TagStaging
                            else -> TagDev
                        }

                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) Chrome else AppBg)
                                .border(1.dp, if (isSelected) (if (p.envTag == "PROD") TagProd else Accent) else Hairline, RoundedCornerShape(6.dp))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(tagColor.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(p.envTag, style = MaterialTheme.typography.labelSmall, color = tagColor)
                            }

                            Column(Modifier.weight(1f)) {
                                Text(p.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                Text(
                                    "${p.username}@${p.host}:${p.port} · ${p.authType} · ${p.remoteDir}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                            }

                            Button(
                                onClick = { onSelectProfile(p) },
                                modifier = Modifier.height(28.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = if (p.envTag == "PROD") TagProd else Accent, contentColor = OnAccent),
                                shape = RoundedCornerShape(6.dp)
                            ) { Text("선택", style = MaterialTheme.typography.labelSmall) }

                            OutlinedButton(
                                onClick = { startEditForm(p) },
                                modifier = Modifier.height(28.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) { Text("수정", style = MaterialTheme.typography.labelSmall) }

                            if (profiles.size > 1) {
                                OutlinedButton(
                                    onClick = { onDeleteProfile(p.id) },
                                    modifier = Modifier.height(28.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger)
                                ) { Text("삭제", style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                    }
                }

                // Add / Edit form
                editingProfile?.let { ep ->
                    HorizontalDivider(color = Hairline)
                    Text(if (ep.id > 0) "프로필 수정: ${ep.name}" else "새 서버 프로필 등록", style = MaterialTheme.typography.titleMedium)

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1.2f)) {
                            ProfileTextField("프로필 이름", name, { name = it })
                        }
                        Column(Modifier.weight(0.8f)) {
                            Text("환경 태그", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("DEV", "STAGING", "PROD").forEach { tag ->
                                    val isCurrent = envTag == tag
                                    val tagCol = when (tag) {
                                        "PROD" -> TagProd
                                        "STAGING" -> TagStaging
                                        else -> TagDev
                                    }
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isCurrent) tagCol else Panel)
                                            .clickable { envTag = tag }
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            tag,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isCurrent) OnAccent else TextMuted
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) {
                            ProfileTextField("호스트 (IP/도메인)", host, { host = it })
                        }
                        Column(Modifier.weight(0.4f)) {
                            ProfileTextField("포트", port, { port = it })
                        }
                        Column(Modifier.weight(0.6f)) {
                            ProfileTextField("계정명", username, { username = it })
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("인증 방식:", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = authType == AuthType.PASSWORD,
                                onClick = { authType = AuthType.PASSWORD },
                                colors = RadioButtonDefaults.colors(selectedColor = Accent)
                            )
                            Text("비밀번호", style = MaterialTheme.typography.labelMedium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = authType == AuthType.KEY,
                                onClick = { authType = AuthType.KEY },
                                colors = RadioButtonDefaults.colors(selectedColor = Accent)
                            )
                            Text("SSH 키 파일", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    if (authType == AuthType.PASSWORD) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("비밀번호", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Spacer(Modifier.weight(1f))
                                Text(
                                    if (showPassword) "숨기기" else "보기",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Accent,
                                    modifier = Modifier.clickable { showPassword = !showPassword }
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            BasicTextField(
                                value = password,
                                onValueChange = { password = it },
                                singleLine = true,
                                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                                cursorBrush = SolidColor(Accent),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(TermBg)
                                    .border(1.dp, Hairline, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                            )
                        }
                    } else {
                        ProfileTextField("키 파일 절대 경로 (예: C:/Users/.../id_rsa)", keyPath, { keyPath = it })
                    }

                    ProfileTextField("기본 원격 작업 디렉터리", remoteDir, { remoteDir = it })

                    testStatus?.let {
                        Text(
                            it,
                            color = if (it.contains("성공")) Ok else Danger,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    formError?.let {
                        Text(it, color = Danger, style = MaterialTheme.typography.labelSmall)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (name.isBlank() || host.isBlank()) {
                                    formError = "프로필 이름과 호스트는 필수입니다."
                                    return@Button
                                }
                                val toSave = ServerProfile(
                                    id = ep.id,
                                    name = name.trim(),
                                    host = host.trim(),
                                    port = port.toIntOrNull() ?: 22,
                                    username = username.trim().ifEmpty { "root" },
                                    authType = authType,
                                    password = password.takeIf { it.isNotBlank() },
                                    keyPath = keyPath.takeIf { it.isNotBlank() },
                                    remoteDir = remoteDir.trim().ifEmpty { "/tmp" },
                                    envTag = envTag
                                )
                                onSaveProfile(toSave)
                                editingProfile = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
                            shape = RoundedCornerShape(8.dp)
                        ) { Text("저장") }

                        Button(
                            onClick = {
                                scope.launch {
                                    testStatus = "연결 시도 중..."
                                    val testConfig = SshConfig(
                                        host = host.trim(),
                                        port = port.toIntOrNull() ?: 22,
                                        username = username.trim().ifEmpty { "root" },
                                        password = password.takeIf { it.isNotBlank() },
                                        keyPath = keyPath.takeIf { it.isNotBlank() }?.let { Path.of(it) },
                                        remoteDir = remoteDir.trim().ifEmpty { "/tmp" }
                                    )
                                    val res = withContext(Dispatchers.IO) {
                                        runCatching {
                                            val s = SshSession(testConfig)
                                            s.connect()
                                            s.close()
                                            "✅ 연결 성공! (SSH 정상 응답)"
                                        }.getOrElse { "❌ 실패: ${it.message}" }
                                    }
                                    testStatus = res
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = TextPrimary),
                            shape = RoundedCornerShape(8.dp)
                        ) { Text("연결 테스트") }

                        OutlinedButton(
                            onClick = { editingProfile = null },
                            shape = RoundedCornerShape(8.dp)
                        ) { Text("취소") }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = TextPrimary),
                        shape = RoundedCornerShape(8.dp)
                    ) { Text("닫기") }
                }
            }
        }
    }
}

@Composable
private fun ProfileTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Spacer(Modifier.height(4.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
            cursorBrush = SolidColor(Accent),
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(TermBg)
                .border(1.dp, Hairline, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}
