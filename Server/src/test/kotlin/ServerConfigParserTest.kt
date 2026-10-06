import core.ServerConstants
import core.game.system.config.ServerConfigParser
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * AIO-02: the public profile must turn on real auth and persistence and expose
 * the AI-only registration knobs, without changing the defaults that
 * `default.conf` relies on.
 */
class ServerConfigParserTest {
    companion object {
        init {
            ServerConfigParser.parse("worldprops/public.conf.example")
        }

        @JvmStatic
        @AfterAll
        fun leaveTheWorldUnparsed() {
            // TestUtils.preTestSetup() only bootstraps once, guarded on DATA_PATH.
            // Clearing it lets the next test class parse test.conf as usual.
            ServerConstants.DATA_PATH = null
        }
    }

    @Test
    fun shouldRequireAuthAndPersistenceOnThePublicProfile() {
        Assertions.assertTrue(ServerConstants.USE_AUTH)
        Assertions.assertTrue(ServerConstants.PERSIST_ACCOUNTS)
        Assertions.assertFalse(ServerConstants.NOAUTH_DEFAULT_ADMIN)
    }

    @Test
    fun shouldReadTheAgentRegistrationSettings() {
        Assertions.assertTrue(ServerConstants.REGISTRATION_OPEN)
        Assertions.assertEquals(3, ServerConstants.REGISTRATION_PER_IP_HOUR)
        Assertions.assertEquals(30, ServerConstants.NAME_CHECKS_PER_IP_MINUTE)
        Assertions.assertTrue(ServerConstants.AGENT_TOKENS_ONLY)
    }

    @Test
    fun shouldLeaveTheRsaKeyPathUnsetOnTheExample() {
        Assertions.assertEquals("", ServerConstants.RSA_KEY_PATH)
    }
}
