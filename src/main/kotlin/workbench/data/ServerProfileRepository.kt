package workbench.data

import workbench.config.SshConfig
import java.nio.file.Path
import java.sql.ResultSet

enum class AuthType {
    PASSWORD,
    KEY
}

data class ServerProfile(
    val id: Long = 0,
    val name: String,
    val host: String,
    val port: Int = 22,
    val username: String = "root",
    val authType: AuthType = AuthType.PASSWORD,
    val password: String? = null,
    val keyPath: String? = null,
    val remoteDir: String = "/tmp"
) {
    fun toSshConfig(): SshConfig = SshConfig(
        host = host,
        port = port,
        username = username,
        password = password,
        keyPath = keyPath?.takeIf { it.isNotBlank() }?.let { Path.of(it) },
        remoteDir = remoteDir
    )
}

class ServerProfileRepository(private val dbManager: DatabaseManager) {

    fun getAllProfiles(): List<ServerProfile> {
        val list = mutableListOf<ServerProfile>()
        dbManager.getConnection().use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT * FROM server_profiles ORDER BY id ASC")
                while (rs.next()) {
                    list.add(mapRow(rs))
                }
            }
        }
        return list
    }

    fun getProfileById(id: Long): ServerProfile? {
        dbManager.getConnection().use { conn ->
            conn.prepareStatement("SELECT * FROM server_profiles WHERE id = ?").use { stmt ->
                stmt.setLong(1, id)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    return mapRow(rs)
                }
            }
        }
        return null
    }

    fun saveProfile(profile: ServerProfile): ServerProfile {
        dbManager.getConnection().use { conn ->
            if (profile.id <= 0) {
                val sql = """
                    INSERT INTO server_profiles (name, host, port, username, auth_type, password, key_path, remote_dir)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
                conn.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS).use { stmt ->
                    stmt.setString(1, profile.name)
                    stmt.setString(2, profile.host)
                    stmt.setInt(3, profile.port)
                    stmt.setString(4, profile.username)
                    stmt.setString(5, profile.authType.name)
                    stmt.setString(6, profile.password)
                    stmt.setString(7, profile.keyPath)
                    stmt.setString(8, profile.remoteDir)
                    stmt.executeUpdate()
                    val keys = stmt.generatedKeys
                    if (keys.next()) {
                        return profile.copy(id = keys.getLong(1))
                    }
                }
            } else {
                val sql = """
                    UPDATE server_profiles 
                    SET name = ?, host = ?, port = ?, username = ?, auth_type = ?, password = ?, key_path = ?, remote_dir = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                """.trimIndent()
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, profile.name)
                    stmt.setString(2, profile.host)
                    stmt.setInt(3, profile.port)
                    stmt.setString(4, profile.username)
                    stmt.setString(5, profile.authType.name)
                    stmt.setString(6, profile.password)
                    stmt.setString(7, profile.keyPath)
                    stmt.setString(8, profile.remoteDir)
                    stmt.setLong(9, profile.id)
                    stmt.executeUpdate()
                }
            }
        }
        return profile
    }

    fun deleteProfile(id: Long) {
        dbManager.getConnection().use { conn ->
            conn.prepareStatement("DELETE FROM server_profiles WHERE id = ?").use { stmt ->
                stmt.setLong(1, id)
                stmt.executeUpdate()
            }
        }
    }

    fun ensureDefaultProfile(fallbackConfig: SshConfig?): ServerProfile {
        val existing = getAllProfiles()
        if (existing.isNotEmpty()) {
            return existing.first()
        }

        val initial = if (fallbackConfig != null && fallbackConfig.host.isNotBlank()) {
            ServerProfile(
                name = "기본 서버 (${fallbackConfig.host})",
                host = fallbackConfig.host,
                port = fallbackConfig.port,
                username = fallbackConfig.username,
                authType = if (fallbackConfig.keyPath != null) AuthType.KEY else AuthType.PASSWORD,
                password = fallbackConfig.password,
                keyPath = fallbackConfig.keyPath?.toString(),
                remoteDir = fallbackConfig.remoteDir
            )
        } else {
            ServerProfile(
                name = "로컬 데모 서버",
                host = "127.0.0.1",
                port = 22,
                username = "demo",
                authType = AuthType.PASSWORD,
                password = "password",
                remoteDir = "/tmp"
            )
        }
        return saveProfile(initial)
    }

    private fun mapRow(rs: ResultSet): ServerProfile = ServerProfile(
        id = rs.getLong("id"),
        name = rs.getString("name"),
        host = rs.getString("host"),
        port = rs.getInt("port"),
        username = rs.getString("username"),
        authType = runCatching { AuthType.valueOf(rs.getString("auth_type")) }.getOrDefault(AuthType.PASSWORD),
        password = rs.getString("password"),
        keyPath = rs.getString("key_path"),
        remoteDir = rs.getString("remote_dir") ?: "/tmp"
    )
}
