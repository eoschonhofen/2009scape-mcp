import core.ServerConstants
import core.game.system.config.ServerConfigParser
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File

/**
 * AIO-02: the public profile must turn on real auth and persistence and expose
 * the AI-only registration knobs, without changing the defaults that
 * `default.conf` relies on.
 */
class ServerConfigParserTest {
    companion object {
        private val EXAMPLE = File("worldprops/public.conf.example")

        init {
            // The shipped example points rsa_key_path at a key file that only the
            // operator has, and the parser exits the process when it cannot read
            // one. Parse a copy with that line blanked out instead; the shipped
            // value itself is asserted separately below.
            // A path under the working directory: ServerConfigParser.parse(String)
            // runs the path through parsePath(), which mangles absolute paths.
            val copy = File("target/public-test.conf")
            copy.parentFile.mkdirs()
            copy.deleteOnExit()
            copy.writeText(EXAMPLE.readText().replace(Regex("(?m)^rsa_key_path = .*$"), "rsa_key_path = \"\""))
            ServerConfigParser.parse(copy.path)
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
        Assertions.assertEquals(20, ServerConstants.REGISTRATION_ATTEMPTS_PER_IP_MINUTE)
        Assertions.assertTrue(ServerConstants.AGENT_TOKENS_ONLY)
    }

    @Test
    fun shouldPointTheExampleAtAPrivateKeyFile() {
        // AIO-03: an operator who copies the example must not silently fall back
        // to the development pair, whose private half is in public source.
        Assertions.assertTrue(
            EXAMPLE.readText().contains("""rsa_key_path = "data/rsa/private.key""""),
            "public.conf.example should ship a real rsa_key_path"
        )
    }
}
