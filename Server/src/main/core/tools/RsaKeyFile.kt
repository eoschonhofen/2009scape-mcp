package core.tools

import java.io.File
import java.math.BigInteger

/**
 * AIO-03 — reads the `modulus=` / `exponent=` files that [RSAKeyGen] writes.
 *
 * This is deliberately pure so it can be tested without a running server, and
 * so it never logs what it reads: the private exponent must not reach a log.
 */
object RsaKeyFile {
    /** @return the modulus and the exponent, in that order. */
    fun load(file: File): Pair<BigInteger, BigInteger> {
        var modulus: BigInteger? = null
        var exponent: BigInteger? = null
        file.forEachLine { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachLine
            val separator = line.indexOf('=')
            if (separator <= 0) return@forEachLine
            val value = line.substring(separator + 1).trim()
            when (line.substring(0, separator).trim().lowercase()) {
                "modulus" -> modulus = BigInteger(value)
                "exponent" -> exponent = BigInteger(value)
            }
        }
        return Pair(
            modulus ?: throw IllegalArgumentException("no modulus= line in ${file.path}"),
            exponent ?: throw IllegalArgumentException("no exponent= line in ${file.path}")
        )
    }

    fun load(path: String): Pair<BigInteger, BigInteger> = load(File(path))
}
