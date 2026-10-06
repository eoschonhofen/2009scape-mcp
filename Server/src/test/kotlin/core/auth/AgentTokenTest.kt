package core.auth

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/** AIO-04 — token format and uniqueness. */
class AgentTokenTest {

    @Test
    fun shouldGenerateValidDistinctTokens() {
        val tokens = (1..1000).map { AgentToken.generate() }
        Assertions.assertTrue(tokens.all { AgentToken.isValid(it) })
        Assertions.assertEquals(1000, tokens.toSet().size)
    }

    @Test
    fun shouldRejectWrongLengths() {
        Assertions.assertFalse(AgentToken.isValid("a".repeat(AgentToken.LENGTH - 1)))
        Assertions.assertFalse(AgentToken.isValid("a".repeat(AgentToken.LENGTH + 1)))
        Assertions.assertFalse(AgentToken.isValid(null))
    }

    @Test
    fun shouldRejectUppercaseAndUnderscore() {
        Assertions.assertFalse(AgentToken.isValid("ABCDEFGHIJKLMNOPQRST"))
        Assertions.assertFalse(AgentToken.isValid("abc_defghijklmnopqr"))
        Assertions.assertFalse(AgentToken.isValid("hunter22"))
    }

    @Test
    fun shouldAcceptEveryAllowedCharacter() {
        Assertions.assertTrue(AgentToken.isValid("abcdefghijklmnopqrst"))
        Assertions.assertTrue(AgentToken.isValid("01234567890123456789"))
    }
}
