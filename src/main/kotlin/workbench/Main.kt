package workbench

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import workbench.config.loadSshConfig
import workbench.ui.WorkbenchApp

fun main() = application {
    val config = runCatching { loadSshConfig() }.getOrElse {
        // UI에서 오류 표시를 위해 더미 설정 — 실제 연결은 실패하고 메시지 노출
        throw it
    }
    Window(onCloseRequest = ::exitApplication, title = "remote-workbench") {
        WorkbenchApp(config)
    }
}
