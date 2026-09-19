package workbench.ssh

import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import workbench.config.SshConfig
import java.io.InputStream
import java.io.OutputStream
import java.util.Properties

class SshSession(private val config: SshConfig) {
    private var session: Session? = null
    private var channel: ChannelShell? = null

    val connected: Boolean
        get() = session?.isConnected == true && channel?.isConnected == true

    fun connect() {
        val jsch = JSch()
        config.keyPath?.let { jsch.addIdentity(it.toString()) }
        val s = jsch.getSession(config.username, config.host, config.port)
        if (config.password != null) {
            s.setPassword(config.password)
        }
        val props = Properties()
        props["StrictHostKeyChecking"] = "no"
        s.setConfig(props)
        s.connect(10_000)
        val ch = s.openChannel("shell") as ChannelShell
        ch.setPtyType("xterm")
        ch.connect(5_000)
        session = s
        channel = ch
    }

    fun inputStream(): InputStream =
        channel?.inputStream ?: error("SSH 세션이 연결되어 있지 않습니다.")

    fun outputStream(): OutputStream =
        channel?.outputStream ?: error("SSH 세션이 연결되어 있지 않습니다.")

    fun openSftp(): com.jcraft.jsch.ChannelSftp {
        val s = session ?: error("SSH 세션이 연결되어 있지 않습니다.")
        val ch = s.openChannel("sftp") as com.jcraft.jsch.ChannelSftp
        ch.connect(5_000)
        return ch
    }

    fun close() {
        channel?.disconnect()
        channel = null
        session?.disconnect()
        session = null
    }
}
