package workbench

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import workbench.config.loadSshConfig
import workbench.data.DatabaseManager
import workbench.data.ServerProfileRepository
import workbench.ui.WorkbenchApp

fun main() = application {
    val dbManager = DatabaseManager()
    dbManager.init()
    val repository = ServerProfileRepository(dbManager)

    // Automatically import from .env if present and database is empty
    val envConfig = runCatching { loadSshConfig() }.getOrNull()
    repository.ensureDefaultProfile(envConfig)

    Window(
        onCloseRequest = {
            dbManager.close()
            exitApplication()
        },
        title = "remote-workbench",
        state = rememberWindowState(width = 1200.dp, height = 760.dp),
    ) {
        WorkbenchApp(repository = repository, window = this.window)
    }
}
