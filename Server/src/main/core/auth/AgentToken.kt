package core.auth

import java.security.SecureRandom

/**
 * AIO-04 — the credential an agent logs in with.
 *
 * Twenty characters of `[a-z0-9]` drawn from [SecureRandom], about 103 bits. The
 * client generates the token (AIO-08) and sends it as the registration password;
 * the server stores only the bcrypt hash of it.
 */
object AgentToken {
    const val LENGTH = 20

    private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"
    private val random = SecureRandom()

    /** True when [token] is exactly [LENGTH] characters of `[a-z0-9]`. */
    @JvmStatic
    fun isValid(token: String?): Boolean {
        if (token == null || token.length != LENGTH) {
            return false
        }
        return token.all { it in ALPHABET }
    }

    /** A fresh token. Never log the result. */
    @JvmStatic
    fun generate(): String {
        val chars = CharArray(LENGTH)
        for (i in 0 until LENGTH) {
            chars[i] = ALPHABET[random.nextInt(ALPHABET.length)]
        }
        return String(chars)
    }
}
