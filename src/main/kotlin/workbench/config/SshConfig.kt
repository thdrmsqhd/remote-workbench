package workbench.config

import io.github.cdimascio.dotenv.dotenv
import java.nio.file.Path

data class SshConfig(
    val host: String,
    val port: Int,
    val username: String,
    val password: String?,
    val keyPath: Path?,
    val remoteDir: String,
)

fun loadSshConfig(): SshConfig {
    val env = dotenv {
        ignoreIfMissing = true
    }
    val host = env["SSH_HOST"]?.trim().orEmpty()
    require(host.isNotEmpty()) { "SSH_HOST가 비어 있습니다. .env.example을 참고하세요." }
    val password = env["SSH_PASSWORD"]?.takeIf { it.isNotBlank() }
    val keyPath = env["SSH_KEY_PATH"]?.takeIf { it.isNotBlank() }?.let { Path.of(it) }
    require(password != null || keyPath != null) { "SSH_PASSWORD 또는 SSH_KEY_PATH가 필요합니다." }
    return SshConfig(
        host = host,
        port = env["SSH_PORT"]?.toIntOrNull() ?: 22,
        username = env["SSH_USER"]?.trim().orEmpty().ifEmpty { "demo" },
        password = password,
        keyPath = keyPath,
        remoteDir = env["SSH_REMOTE_DIR"] ?: "/tmp",
    )
}
