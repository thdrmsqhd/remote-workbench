package workbench.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ServerProfileRepositoryTest {

    private lateinit var dbManager: DatabaseManager
    private lateinit var repository: ServerProfileRepository

    @BeforeEach
    fun setUp() {
        dbManager = DatabaseManager(":memory:")
        dbManager.init()
        repository = ServerProfileRepository(dbManager)
    }

    @org.junit.jupiter.api.AfterEach
    fun tearDown() {
        dbManager.close()
    }

    @Test
    fun testSaveAndGetProfile() {
        val profile = ServerProfile(
            name = "Test Server",
            host = "192.168.1.100",
            port = 2222,
            username = "admin",
            authType = AuthType.PASSWORD,
            password = "secret",
            remoteDir = "/var/www"
        )
        val saved = repository.saveProfile(profile)
        assertTrue(saved.id > 0)

        val retrieved = repository.getProfileById(saved.id)
        assertNotNull(retrieved)
        assertEquals("Test Server", retrieved?.name)
        assertEquals("192.168.1.100", retrieved?.host)
        assertEquals(2222, retrieved?.port)
        assertEquals("admin", retrieved?.username)
        assertEquals(AuthType.PASSWORD, retrieved?.authType)
        assertEquals("secret", retrieved?.password)
        assertEquals("/var/www", retrieved?.remoteDir)
        assertEquals("DEV", retrieved?.envTag)
    }

    @Test
    fun testUpdateProfile() {
        val profile = repository.saveProfile(
            ServerProfile(
                name = "Dev Server",
                host = "dev.local",
                port = 22,
                username = "dev"
            )
        )

        val updated = repository.saveProfile(
            profile.copy(
                name = "Production Server",
                host = "prod.local",
                username = "root"
            )
        )

        assertEquals(profile.id, updated.id)
        val retrieved = repository.getProfileById(profile.id)
        assertEquals("Production Server", retrieved?.name)
        assertEquals("prod.local", retrieved?.host)
        assertEquals("root", retrieved?.username)
    }

    @Test
    fun testDeleteProfile() {
        val profile = repository.saveProfile(
            ServerProfile(
                name = "To Delete",
                host = "del.local"
            )
        )
        assertNotNull(repository.getProfileById(profile.id))

        repository.deleteProfile(profile.id)
        assertNull(repository.getProfileById(profile.id))
    }

    @Test
    fun testEnsureDefaultProfile() {
        val defaultProfile = repository.ensureDefaultProfile(null)
        assertNotNull(defaultProfile)
        assertEquals("로컬 데모 서버", defaultProfile.name)

        val all = repository.getAllProfiles()
        assertEquals(1, all.size)

        // Second call should return existing profile, not insert duplicate
        val existing = repository.ensureDefaultProfile(null)
        assertEquals(defaultProfile.id, existing.id)
        assertEquals(1, repository.getAllProfiles().size)
    }
}
