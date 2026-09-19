package workbench.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import workbench.config.SshConfig
import workbench.sftp.RemoteEntry
import workbench.sftp.SftpClient
import workbench.ssh.SshSession
import java.nio.file.Path

data class SessionState(
    val id: Int,
    val title: String,
    val session: SshSession,
    val sftp: SftpClient,
)

@Composable
fun WorkbenchApp(config: SshConfig) {
    val sessions = remember { mutableStateListOf<SessionState>() }
    var selected by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    fun addSession() {
        try {
            val ssh = SshSession(config)
            ssh.connect()
            val sftp = SftpClient(ssh.openSftp())
            val id = (sessions.maxOfOrNull { it.id } ?: 0) + 1
            sessions.add(SessionState(id, "세션 $id", ssh, sftp))
            selected = sessions.lastIndex
            error = null
        } catch (e: Exception) {
            error = e.message
        }
    }

    LaunchedEffect(Unit) {
        addSession()
        addSession()
    }

    DisposableEffect(Unit) {
        onDispose {
            sessions.forEach {
                it.sftp.close()
                it.session.close()
            }
        }
    }

    MaterialTheme {
        Column(Modifier.fillMaxSize().padding(8.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text("remote-workbench", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Button(onClick = { addSession() }) { Text("새 탭") }
            }
            error?.let { Text("오류: $it", color = Color.Red) }
            if (sessions.isNotEmpty()) {
                ScrollableTabRow(selectedTabIndex = selected) {
                    sessions.forEachIndexed { index, s ->
                        Tab(
                            selected = selected == index,
                            onClick = { selected = index },
                            text = { Text(s.title) },
                        )
                    }
                }
                val current = sessions[selected]
                SessionPane(config, current)
            } else {
                Text("세션을 여는 중…")
            }
        }
    }
}

@Composable
private fun SessionPane(config: SshConfig, state: SessionState) {
    var cwd by remember(state.id) { mutableStateOf(config.remoteDir) }
    var entries by remember(state.id) { mutableStateOf(listOf<RemoteEntry>()) }
    var uploadPath by remember { mutableStateOf("") }
    var shellOut by remember(state.id) { mutableStateOf("") }
    var shellIn by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }

    fun refresh() {
        try {
            entries = state.sftp.listDir(cwd)
            status = ""
        } catch (e: Exception) {
            status = "목록 실패: ${e.message}"
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
                shellOut += String(buf, 0, n)
            } else {
                delay(80)
            }
        }
    }

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(280.dp).fillMaxHeight().padding(end = 8.dp)) {
            Text("원격: $cwd", style = MaterialTheme.typography.labelMedium)
            LazyColumn(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF1E1E1E))) {
                item {
                    Text(
                        "..",
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth().clickable {
                            cwd = Path.of(cwd).parent?.toString() ?: "/"
                        }.padding(4.dp),
                    )
                }
                items(entries) { e ->
                    Text(
                        if (e.isDir) "${e.name}/" else e.name,
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth().clickable {
                            if (e.isDir) cwd = e.path
                        }.padding(4.dp),
                    )
                }
            }
            TextField(
                value = uploadPath,
                onValueChange = { uploadPath = it },
                label = { Text("로컬 파일 경로") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = {
                try {
                    val local = Path.of(uploadPath)
                    val remote = cwd.trimEnd('/') + "/" + local.fileName
                    state.sftp.upload(local, remote)
                    refresh()
                    status = "업로드됨: $remote"
                } catch (e: Exception) {
                    status = "업로드 실패: ${e.message}"
                }
            }) { Text("업로드") }
            if (status.isNotEmpty()) Text(status)
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            BasicTextField(
                value = shellOut,
                onValueChange = {},
                readOnly = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    color = Color.White,
                ),
                modifier = Modifier.weight(1f).fillMaxWidth().background(Color.Black).padding(8.dp),
            )
            Row {
                TextField(
                    value = shellIn,
                    onValueChange = { shellIn = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("명령 입력 후 Enter 전송") },
                    singleLine = true,
                )
                Button(onClick = {
                    try {
                        val out = state.session.outputStream()
                        out.write((shellIn + "\n").toByteArray())
                        out.flush()
                        shellIn = ""
                    } catch (e: Exception) {
                        status = "전송 실패: ${e.message}"
                    }
                }) { Text("전송") }
            }
        }
    }
}
