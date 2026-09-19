package workbench.sftp

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.SftpATTRS
import java.nio.file.Path
import java.util.Vector

data class RemoteEntry(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val size: Long,
)

class SftpClient(private val channel: ChannelSftp) {
    fun listDir(remotePath: String): List<RemoteEntry> {
        @Suppress("UNCHECKED_CAST")
        val entries = channel.ls(remotePath) as Vector<ChannelSftp.LsEntry>
        return entries
            .asSequence()
            .filter { it.filename != "." && it.filename != ".." }
            .map { e ->
                val attrs: SftpATTRS = e.attrs
                val full = remotePath.trimEnd('/') + "/" + e.filename
                RemoteEntry(
                    name = e.filename,
                    path = full,
                    isDir = attrs.isDir,
                    size = attrs.size,
                )
            }
            .sortedWith(compareBy({ !it.isDir }, { it.name.lowercase() }))
            .toList()
    }

    fun upload(local: Path, remote: String) {
        channel.put(local.toString(), remote)
    }

    fun close() {
        channel.disconnect()
    }
}
