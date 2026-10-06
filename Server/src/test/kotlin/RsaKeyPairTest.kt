import core.net.packet.`in`.Login
import core.tools.RSAKeyGen
import core.tools.RsaKeyFile
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.file.Files

/**
 * AIO-03 — a generated key pair must decrypt what the client's `Buffer.rsaenc`
 * produces, and the key file must round-trip through [RsaKeyFile].
 */
class RsaKeyPairTest {

    @Test
    fun shouldRoundTripAKeyPairGeneratedByRsaKeyGen() {
        val dir = Files.createTempDirectory("rsa-key").toFile()
        try {
            RSAKeyGen.generate(dir.toPath())
            val (modulus, privateExponent) = RsaKeyFile.load(File(dir, "private.key"))
            val (publicModulus, publicExponent) = RsaKeyFile.load(File(dir, "public.key"))
            Assertions.assertEquals(modulus, publicModulus)
            Assertions.assertEquals(BigInteger("65537"), publicExponent)

            // What CreateManager.createAccount builds just before Buffer.rsaenc:
            // the encryption header byte 10 followed by the payload.
            val plaintext = ByteArray(16)
            plaintext[0] = 10
            val ciphertext = BigInteger(plaintext).modPow(publicExponent, publicModulus).toByteArray()
            val encrypted = ByteBuffer.allocate(ciphertext.size + 1)
            encrypted.put(ciphertext.size.toByte())
            encrypted.put(ciphertext)
            encrypted.flip()

            val decrypted = Login.decryptRSABuffer(encrypted, privateExponent, modulus)
            Assertions.assertEquals(10, decrypted.get().toInt())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun shouldReadModulusAndExponentFromTheKeyFile() {
        val file = File.createTempFile("rsa", ".key")
        try {
            file.writeText("# a comment\nmodulus=12345\nexponent=65537\n")
            val (modulus, exponent) = RsaKeyFile.load(file)
            Assertions.assertEquals(BigInteger("12345"), modulus)
            Assertions.assertEquals(BigInteger("65537"), exponent)
        } finally {
            file.delete()
        }
    }

    @Test
    fun shouldRejectAKeyFileWithoutAnExponent() {
        val file = File.createTempFile("rsa", ".key")
        try {
            file.writeText("modulus=12345\n")
            Assertions.assertThrows(IllegalArgumentException::class.java) {
                RsaKeyFile.load(file)
            }
        } finally {
            file.delete()
        }
    }
}
