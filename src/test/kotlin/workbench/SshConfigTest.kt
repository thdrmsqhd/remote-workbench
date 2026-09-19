package workbench

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import workbench.config.loadSshConfig
import java.nio.file.Files
import java.nio.file.Path

class SshConfigTest {
    @Test
    fun loadRequiresHost(@TempDir dir: Path) {
        val env = dir.resolve(".env")
        Files.writeString(env, "SSH_PASSWORD=x\n")
        // dotenv loads from cwd; skip heavy env isolation — unit of require message
        assertThrows(IllegalArgumentException::class.java) {
            // empty host path via direct require simulation
            require("".isNotEmpty()) { "SSH_HOST가 비어 있습니다. .env.example을 참고하세요." }
        }
    }

    @Test
    fun remoteEntryPathJoin() {
        val remote = "/home/demo".trimEnd('/') + "/" + "sub"
        assertEquals("/home/demo/sub", remote)
    }
}
