package workbench.data

import java.io.File
import java.sql.Connection
import java.sql.DriverManager

class DatabaseManager(private val dbPath: String = defaultDbPath()) {

    private var keepAliveConn: Connection? = null

    companion object {
        fun defaultDbPath(): String {
            val userHome = System.getProperty("user.home")
            val dir = File(userHome, ".remote-workbench")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return File(dir, "workbench.db").absolutePath
        }
    }

    fun getConnection(): Connection {
        val url = if (dbPath == ":memory:") {
            if (keepAliveConn == null || keepAliveConn?.isClosed == true) {
                keepAliveConn = DriverManager.getConnection("jdbc:sqlite:file:memdb?mode=memory&cache=shared")
            }
            "jdbc:sqlite:file:memdb?mode=memory&cache=shared"
        } else {
            "jdbc:sqlite:$dbPath"
        }
        return DriverManager.getConnection(url)
    }

    fun init() {
        getConnection().use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS server_profiles (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL,
                        host TEXT NOT NULL,
                        port INTEGER NOT NULL DEFAULT 22,
                        username TEXT NOT NULL DEFAULT 'root',
                        auth_type TEXT NOT NULL DEFAULT 'PASSWORD',
                        password TEXT,
                        key_path TEXT,
                        remote_dir TEXT NOT NULL DEFAULT '/tmp',
                        env_tag TEXT NOT NULL DEFAULT 'DEV',
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    );
                    """.trimIndent()
                )
                // In case the table already existed without env_tag
                runCatching {
                    stmt.execute("ALTER TABLE server_profiles ADD COLUMN env_tag TEXT DEFAULT 'DEV';")
                }
            }
        }
    }

    fun close() {
        keepAliveConn?.close()
        keepAliveConn = null
    }
}
