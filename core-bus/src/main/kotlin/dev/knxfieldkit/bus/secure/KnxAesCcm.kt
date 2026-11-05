package dev.knxfieldkit.bus.secure

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Standard RFC 3610 / NIST SP 800-38C AES-128-CCM (Counter with CBC-MAC) implementation.
 *
 * Implemented using pure Kotlin and standard "AES/ECB/NoPadding" block cipher primitive
 * to ensure 100% deterministic operation across all JVM (17+) and Android (API 26+) runtimes
 * without missing crypto provider errors or third-party dependencies.
 */
object KnxAesCcm {

    data class AesCcmResult(
        val ciphertext: ByteArray,
        val macTag: ByteArray,
    ) {
        val fullPayload: ByteArray get() = ciphertext + macTag

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as AesCcmResult
            return ciphertext.contentEquals(other.ciphertext) && macTag.contentEquals(other.macTag)
        }

        override fun hashCode(): Int {
            var result = ciphertext.contentHashCode()
            result = 31 * result + macTag.contentHashCode()
            return result
        }
    }

    /**
     * Encrypts [plaintext] and computes MAC tag using AES-128-CCM (RFC 3610).
     *
     * @param key 16-byte (128-bit) secret key.
     * @param nonce Nonce of length 7..13 bytes (typically 13 bytes for KNX Data Secure / IP Secure).
     * @param plaintext Plaintext payload to encrypt (may be empty).
     * @param aad Additional Authenticated Data (authenticated but unencrypted).
     * @param macLength Length of authentication tag in bytes (4 for Data Secure, 8 or 16 for IP Secure).
     */
    fun encrypt(
        key: ByteArray,
        nonce: ByteArray,
        plaintext: ByteArray,
        aad: ByteArray = ByteArray(0),
        macLength: Int = 4,
    ): AesCcmResult {
        require(key.size == 16) { "AES-128 key must be 16 bytes, got ${key.size}" }
        require(nonce.size in 7..13) { "Nonce length must be between 7 and 13 bytes, got ${nonce.size}" }
        require(macLength in listOf(4, 6, 8, 10, 12, 14, 16)) { "Invalid MAC length: $macLength" }

        val q = 15 - nonce.size // L in RFC 3610
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))

        // --- Step 1: CBC-MAC calculation ---
        val aDataBit = if (aad.isNotEmpty()) 0x40 else 0x00
        val mBits = ((macLength - 2) / 2) shl 3
        val lBits = q - 1

        val b0 = ByteArray(16)
        b0[0] = (aDataBit or mBits or lBits).toByte()
        System.arraycopy(nonce, 0, b0, 1, nonce.size)
        // Store plaintext length in last q bytes
        var pLen = plaintext.size
        for (i in 0 until q) {
            b0[15 - i] = (pLen and 0xFF).toByte()
            pLen = pLen ushr 8
        }

        var x = cipher.doFinal(b0)

        // Process AAD if present
        if (aad.isNotEmpty()) {
            val aBlocks = ByteArrayOutputStream()
            val aLen = aad.size
            if (aLen < 65280) {
                aBlocks.write((aLen ushr 8) and 0xFF)
                aBlocks.write(aLen and 0xFF)
            } else {
                aBlocks.write(0xFF)
                aBlocks.write(0xFE)
                aBlocks.write((aLen ushr 24) and 0xFF)
                aBlocks.write((aLen ushr 16) and 0xFF)
                aBlocks.write((aLen ushr 8) and 0xFF)
                aBlocks.write(aLen and 0xFF)
            }
            aBlocks.write(aad)
            while (aBlocks.size() % 16 != 0) {
                aBlocks.write(0)
            }

            val aBytes = aBlocks.toByteArray()
            for (i in aBytes.indices step 16) {
                val block = ByteArray(16)
                System.arraycopy(aBytes, i, block, 0, 16)
                for (j in 0 until 16) {
                    block[j] = (block[j].toInt() xor x[j].toInt()).toByte()
                }
                x = cipher.doFinal(block)
            }
        }

        // Process Plaintext blocks for CBC-MAC
        for (i in plaintext.indices step 16) {
            val end = minOf(i + 16, plaintext.size)
            val block = ByteArray(16)
            System.arraycopy(plaintext, i, block, 0, end - i)
            for (j in 0 until 16) {
                block[j] = (block[j].toInt() xor x[j].toInt()).toByte()
            }
            x = cipher.doFinal(block)
        }

        val unencryptedMac = x.copyOfRange(0, macLength)

        // --- Step 2: CTR keystream encryption ---
        // A0 for MAC encryption
        val a0 = ByteArray(16)
        a0[0] = (q - 1).toByte()
        System.arraycopy(nonce, 0, a0, 1, nonce.size)
        val s0 = cipher.doFinal(a0)

        val encryptedMac = ByteArray(macLength)
        for (j in 0 until macLength) {
            encryptedMac[j] = (unencryptedMac[j].toInt() xor s0[j].toInt()).toByte()
        }

        // Plaintext payload encryption (A1, A2, ...)
        val ciphertext = ByteArray(plaintext.size)
        var counter = 1
        for (i in plaintext.indices step 16) {
            val end = minOf(i + 16, plaintext.size)
            val ai = ByteArray(16)
            ai[0] = (q - 1).toByte()
            System.arraycopy(nonce, 0, ai, 1, nonce.size)
            var cVal = counter
            for (b in 0 until q) {
                ai[15 - b] = (cVal and 0xFF).toByte()
                cVal = cVal ushr 8
            }
            val si = cipher.doFinal(ai)
            for (j in 0 until (end - i)) {
                ciphertext[i + j] = (plaintext[i + j].toInt() xor si[j].toInt()).toByte()
            }
            counter++
        }

        return AesCcmResult(ciphertext, encryptedMac)
    }

    /**
     * Decrypts [ciphertext] and verifies [macTag] using AES-128-CCM (RFC 3610).
     *
     * @return Decrypted plaintext ByteArray.
     * @throws SecurityException If MAC verification fails or parameters are invalid.
     */
    fun decrypt(
        key: ByteArray,
        nonce: ByteArray,
        ciphertext: ByteArray,
        macTag: ByteArray,
        aad: ByteArray = ByteArray(0),
        macLength: Int = macTag.size,
    ): ByteArray {
        require(key.size == 16) { "AES-128 key must be 16 bytes, got ${key.size}" }
        require(nonce.size in 7..13) { "Nonce length must be between 7 and 13 bytes, got ${nonce.size}" }
        require(macTag.size == macLength) { "MAC tag length ${macTag.size} does not match expected $macLength" }

        val q = 15 - nonce.size
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))

        // --- Step 1: CTR keystream decryption of ciphertext ---
        val plaintext = ByteArray(ciphertext.size)
        var counter = 1
        for (i in ciphertext.indices step 16) {
            val end = minOf(i + 16, ciphertext.size)
            val ai = ByteArray(16)
            ai[0] = (q - 1).toByte()
            System.arraycopy(nonce, 0, ai, 1, nonce.size)
            var cVal = counter
            for (b in 0 until q) {
                ai[15 - b] = (cVal and 0xFF).toByte()
                cVal = cVal ushr 8
            }
            val si = cipher.doFinal(ai)
            for (j in 0 until (end - i)) {
                plaintext[i + j] = (ciphertext[i + j].toInt() xor si[j].toInt()).toByte()
            }
            counter++
        }

        // --- Step 2: Recover expected MAC from S0 ---
        val a0 = ByteArray(16)
        a0[0] = (q - 1).toByte()
        System.arraycopy(nonce, 0, a0, 1, nonce.size)
        val s0 = cipher.doFinal(a0)

        val expectedMac = ByteArray(macLength)
        for (j in 0 until macLength) {
            expectedMac[j] = (macTag[j].toInt() xor s0[j].toInt()).toByte()
        }

        // --- Step 3: Compute CBC-MAC over (b0, aad, plaintext) ---
        val aDataBit = if (aad.isNotEmpty()) 0x40 else 0x00
        val mBits = ((macLength - 2) / 2) shl 3
        val lBits = q - 1

        val b0 = ByteArray(16)
        b0[0] = (aDataBit or mBits or lBits).toByte()
        System.arraycopy(nonce, 0, b0, 1, nonce.size)
        var pLen = plaintext.size
        for (i in 0 until q) {
            b0[15 - i] = (pLen and 0xFF).toByte()
            pLen = pLen ushr 8
        }

        var x = cipher.doFinal(b0)

        if (aad.isNotEmpty()) {
            val aBlocks = ByteArrayOutputStream()
            val aLen = aad.size
            if (aLen < 65280) {
                aBlocks.write((aLen ushr 8) and 0xFF)
                aBlocks.write(aLen and 0xFF)
            } else {
                aBlocks.write(0xFF)
                aBlocks.write(0xFE)
                aBlocks.write((aLen ushr 24) and 0xFF)
                aBlocks.write((aLen ushr 16) and 0xFF)
                aBlocks.write((aLen ushr 8) and 0xFF)
                aBlocks.write(aLen and 0xFF)
            }
            aBlocks.write(aad)
            while (aBlocks.size() % 16 != 0) {
                aBlocks.write(0)
            }

            val aBytes = aBlocks.toByteArray()
            for (i in aBytes.indices step 16) {
                val block = ByteArray(16)
                System.arraycopy(aBytes, i, block, 0, 16)
                for (j in 0 until 16) {
                    block[j] = (block[j].toInt() xor x[j].toInt()).toByte()
                }
                x = cipher.doFinal(block)
            }
        }

        for (i in plaintext.indices step 16) {
            val end = minOf(i + 16, plaintext.size)
            val block = ByteArray(16)
            System.arraycopy(plaintext, i, block, 0, end - i)
            for (j in 0 until 16) {
                block[j] = (block[j].toInt() xor x[j].toInt()).toByte()
            }
            x = cipher.doFinal(block)
        }

        val computedMac = x.copyOfRange(0, macLength)

        if (!MessageDigest.isEqual(expectedMac, computedMac)) {
            throw SecurityException("KNX Secure AES-CCM MAC authentication failed")
        }

        return plaintext
    }
}
