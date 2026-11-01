package dev.knxfieldkit.knxproj

import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.min

/**
 * Cryptographic primitives specified by KNX Association for ETS project archives (.knxproj)
 * and KNX Secure keyring exports (.knxkeys).
 */
object EtsCrypto {
    private val ETS6_PROJECT_SALT = "21.project.ets.knx.org".toByteArray(Charsets.US_ASCII)
    private val ETS_KEYRING_SALT = "1.keyring.ets.knx.org".toByteArray(Charsets.US_ASCII)
    private const val PBKDF2_ITERATIONS = 65_536

    /**
     * Derives the inner P-xxxx.zip AES-256 archive password used by ETS6.
     *
     * ETS6 hashes the UTF-16LE byte representation of [projectPassword] with PBKDF2-HMAC-SHA256
     * (salt = "21.project.ets.knx.org", 65536 iterations, 32-byte key) and Base64-encodes the result.
     */
    fun deriveEts6ZipPassword(projectPassword: String): CharArray {
        val passwordBytes = projectPassword.toByteArray(Charsets.UTF_16LE)
        val derivedKey = pbkdf2HmacSha256(
            passwordBytes = passwordBytes,
            salt = ETS6_PROJECT_SALT,
            iterations = PBKDF2_ITERATIONS,
            dkLenBytes = 32,
        )
        return Base64.getEncoder().encodeToString(derivedKey).toCharArray()
    }

    /**
     * Derives the 128-bit AES key used to decrypt attributes inside an ETS `.knxkeys` XML file.
     *
     * Keyring files hash the UTF-8 byte representation of [keyringPassword] with PBKDF2-HMAC-SHA256
     * (salt = "1.keyring.ets.knx.org", 65536 iterations, 16-byte key).
     */
    fun deriveKeyringAesKey(keyringPassword: String): ByteArray {
        val passwordBytes = keyringPassword.toByteArray(Charsets.UTF_8)
        return pbkdf2HmacSha256(
            passwordBytes = passwordBytes,
            salt = ETS_KEYRING_SALT,
            iterations = PBKDF2_ITERATIONS,
            dkLenBytes = 16,
        )
    }

    /**
     * Computes the 128-bit AES-CBC Initialization Vector for `.knxkeys` attributes from the
     * `<Keyring Created="..."/>` timestamp string (first 16 bytes of SHA-256(Created)).
     */
    fun deriveKeyringIv(createdTimestamp: String): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(createdTimestamp.toByteArray(Charsets.UTF_8))
        return digest.copyOfRange(0, 16)
    }

    /**
     * Decrypts a Base64-encoded encrypted key or password attribute from a `.knxkeys` XML element.
     */
    fun decryptKeyringAttribute(base64Ciphertext: String, aesKey16: ByteArray, iv16: ByteArray): ByteArray {
        val cipherBytes = Base64.getDecoder().decode(base64Ciphertext.trim())
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey16, "AES"), IvParameterSpec(iv16))
        return cipher.doFinal(cipherBytes)
    }

    /**
     * Decrypts a `.knxkeys` text password/auth-code attribute (which ETS pads with PKCS#7 / trailing control bytes).
     */
    fun decryptKeyringPasswordString(base64Ciphertext: String, aesKey16: ByteArray, iv16: ByteArray): String {
        val raw = decryptKeyringAttribute(base64Ciphertext, aesKey16, iv16)
        if (raw.isEmpty()) return ""
        val padLen = raw.last().toInt() and 0xFF
        val unpadded = if (padLen in 1..16 && raw.size >= padLen && raw.takeLast(padLen).all { (it.toInt() and 0xFF) == padLen }) {
            raw.copyOfRange(0, raw.size - padLen)
        } else {
            raw.dropLastWhile { it == 0.toByte() }.toByteArray()
        }
        return String(unpadded, Charsets.UTF_8)
    }

    /**
     * Pure JVM RFC 2898 PBKDF2-HMAC-SHA256 implementation accepting arbitrary raw password bytes
     * (avoiding `PBEKeySpec`'s forced UTF-8 `char[]` conversion).
     */
    fun pbkdf2HmacSha256(
        passwordBytes: ByteArray,
        salt: ByteArray,
        iterations: Int,
        dkLenBytes: Int,
    ): ByteArray {
        require(iterations >= 1) { "Iterations must be >= 1" }
        require(dkLenBytes >= 1) { "Derived key length must be >= 1" }

        val mac = Mac.getInstance("HmacSHA256")
        // Note: SecretKeySpec rejects empty byte arrays; HMAC with an empty key is equivalent to a single 0x00 byte.
        val effectiveKey = if (passwordBytes.isEmpty()) ByteArray(1) else passwordBytes
        mac.init(SecretKeySpec(effectiveKey, "HmacSHA256"))

        val hLen = 32
        val blocks = (dkLenBytes + hLen - 1) / hLen
        val result = ByteArray(dkLenBytes)
        val saltPlusBlock = ByteArray(salt.size + 4)
        System.arraycopy(salt, 0, saltPlusBlock, 0, salt.size)

        var offset = 0
        for (blockIndex in 1..blocks) {
            saltPlusBlock[salt.size] = ((blockIndex ushr 24) and 0xFF).toByte()
            saltPlusBlock[salt.size + 1] = ((blockIndex ushr 16) and 0xFF).toByte()
            saltPlusBlock[salt.size + 2] = ((blockIndex ushr 8) and 0xFF).toByte()
            saltPlusBlock[salt.size + 3] = (blockIndex and 0xFF).toByte()

            var u = mac.doFinal(saltPlusBlock)
            val t = u.clone()

            for (iter in 2..iterations) {
                u = mac.doFinal(u)
                for (i in 0 until hLen) {
                    t[i] = (t[i].toInt() xor u[i].toInt()).toByte()
                }
            }

            val copyLen = min(hLen, dkLenBytes - offset)
            System.arraycopy(t, 0, result, offset, copyLen)
            offset += copyLen
        }
        return result
    }
}
