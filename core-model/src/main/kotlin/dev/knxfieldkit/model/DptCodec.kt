package dev.knxfieldkit.model

import java.nio.ByteBuffer
import java.nio.charset.Charset

object HexBytes {
    fun toHex(bytes: ByteArray, separator: String = " "): String =
        bytes.joinToString(separator) { "%02X".format(it.toInt() and 0xFF) }

    fun fromHex(hex: String): ByteArray {
        val cleaned = hex.replace(Regex("(?i)0x|[\\s:\\-]"), "")
        require(cleaned.length % 2 == 0) { "Invalid hex string length" }
        if (cleaned.isEmpty()) return ByteArray(0)
        return cleaned.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}

enum class HvacMode(val code: Int, val label: String) {
    AUTO(0, "Auto"),
    COMFORT(1, "Comfort"),
    STANDBY(2, "Standby"),
    ECONOMY(3, "Economy / Night"),
    BUILDING_PROTECTION(4, "Building Protection");

    companion object {
        fun fromCode(code: Int): HvacMode? = entries.firstOrNull { it.code == code }
    }
}

enum class MainDpt(val defaultId: String, val label: String, val bitLength: Int) {
    DPT_1_BOOL("1.001", "Switch (On/Off)", 1),
    DPT_3_DIM_STEP("3.007", "Relative Dimming / Blinds Step", 4),
    DPT_5_PERCENT("5.001", "Scaling (0..100 %)", 8),
    DPT_5_UNSIGNED("5.010", "1-Byte Unsigned (0..255)", 8),
    DPT_9_FLOAT("9.001", "2-Byte Float (°C / Lux)", 16),
    DPT_13_INT32("13.001", "4-Byte Signed Int", 32),
    DPT_14_FLOAT32("14.056", "4-Byte IEEE Float", 32),
    DPT_16_STRING("16.001", "14-Byte Text (ISO-8859-1)", 112),
    DPT_17_SCENE("17.001", "Scene Number (1..64)", 8),
    DPT_20_HVAC("20.102", "HVAC Operating Mode", 8),
    RAW_BYTES("raw", "Raw Hex Payload", -1)
}

object DptCodec {
    fun normalizeDptId(rawDpt: String?): String? {
        val trimmed = rawDpt?.trim()
        return when {
            trimmed.isNullOrBlank() -> null
            trimmed.startsWith("DPST-", ignoreCase = true) -> {
                val parts = trimmed.substring(5).split("-")
                if (parts.size >= 2) "${parts[0]}.${parts[1].padStart(3, '0')}" else trimmed
            }
            trimmed.startsWith("DPT-", ignoreCase = true) -> {
                val parts = trimmed.substring(4).split("-")
                when (parts[0]) {
                    "5" -> "5.001"
                    "14" -> "14.056"
                    "20" -> "20.102"
                    else -> "${parts[0]}.001"
                }
            }
            '.' in trimmed -> {
                val parts = trimmed.split('.')
                if (parts.size == 2 && parts[0].all { it.isDigit() } && parts[1].all { it.isDigit() }) {
                    "${parts[0]}.${parts[1].padStart(3, '0')}"
                } else {
                    trimmed
                }
            }
            else -> trimmed
        }
    }

    fun resolveMainDpt(rawDpt: String?): MainDpt {
        val normalized = normalizeDptId(rawDpt) ?: return MainDpt.RAW_BYTES
        val parts = normalized.split(".")
        val main = parts.firstOrNull()?.toIntOrNull() ?: return MainDpt.RAW_BYTES
        val sub = parts.getOrNull(1) ?: "001"
        return when (main) {
            1 -> MainDpt.DPT_1_BOOL
            3 -> MainDpt.DPT_3_DIM_STEP
            5 -> if (sub in listOf("001", "004")) MainDpt.DPT_5_PERCENT else MainDpt.DPT_5_UNSIGNED
            9 -> MainDpt.DPT_9_FLOAT
            13 -> MainDpt.DPT_13_INT32
            14 -> MainDpt.DPT_14_FLOAT32
            16 -> MainDpt.DPT_16_STRING
            17, 18 -> MainDpt.DPT_17_SCENE
            20 -> MainDpt.DPT_20_HVAC
            else -> MainDpt.RAW_BYTES
        }
    }

    fun unitForDpt(normalizedDpt: String?): String {
        return when (normalizedDpt) {
            "5.001" -> "%"
            "9.001" -> "°C"
            "9.004" -> "lx"
            "9.005" -> "m/s"
            "9.007" -> "%"
            "9.008" -> "ppm"
            "14.056" -> "W"
            else -> ""
        }
    }

    fun encodeBool(value: Boolean): ByteArray = byteArrayOf(if (value) 1 else 0)
    fun decodeBool(bytes: ByteArray): Boolean {
        require(bytes.isNotEmpty())
        return (bytes.last().toInt() and 0x01) != 0
    }

    fun encodeDimmingStep(increase: Boolean, stepCode: Int = 1): ByteArray {
        val step = stepCode.coerceIn(0, 7)
        val cBit = if (increase) 0x08 else 0x00
        return byteArrayOf((cBit or step).toByte())
    }

    fun decodeDimmingStep(bytes: ByteArray): String {
        require(bytes.isNotEmpty())
        val nibble = bytes.last().toInt() and 0x0F
        val step = nibble and 0x07
        if (step == 0) return "STOP"
        val dir = if ((nibble and 0x08) != 0) "UP" else "DOWN"
        val pct = 100.0 / (1 shl (step - 1))
        return String.format(java.util.Locale.US, "%s %.1f%%", dir, pct)
    }

    fun encodePercent(percent: Double): ByteArray {
        val clamped = percent.coerceIn(0.0, 100.0)
        return byteArrayOf((Math.round(clamped * 255.0 / 100.0).toInt().coerceIn(0, 255)).toByte())
    }

    fun decodePercent(bytes: ByteArray): Double {
        require(bytes.isNotEmpty())
        return (bytes.last().toInt() and 0xFF) * 100.0 / 255.0
    }

    fun encodeUnsigned8(value: Int): ByteArray {
        require(value in 0..255)
        return byteArrayOf(value.toByte())
    }

    fun decodeUnsigned8(bytes: ByteArray): Int {
        require(bytes.isNotEmpty())
        return bytes.last().toInt() and 0xFF
    }

    fun encodeFloat16(value: Double): ByteArray {
        var v = value * 100.0
        var exp = 0
        while ((v < -2048.0 || v > 2047.0) && exp < 15) {
            v /= 2.0
            exp++
        }
        val mantissa = Math.round(v).toInt().coerceIn(-2048, 2047)
        val sign = if (mantissa < 0) 0x8000 else 0
        val mBits = if (mantissa < 0) (mantissa + 2048) and 0x07FF else mantissa and 0x07FF
        val word = sign or (exp shl 11) or mBits
        return byteArrayOf(((word ushr 8) and 0xFF).toByte(), (word and 0xFF).toByte())
    }

    fun decodeFloat16(bytes: ByteArray): Double {
        require(bytes.size >= 2)
        val word = ((bytes[bytes.size - 2].toInt() and 0xFF) shl 8) or (bytes[bytes.size - 1].toInt() and 0xFF)
        val sign = (word ushr 15) and 0x01
        val exp = (word ushr 11) and 0x0F
        val rawMantissa = word and 0x07FF
        val signedMantissa = if (sign == 1) rawMantissa - 2048 else rawMantissa
        return (0.01 * signedMantissa) * (1 shl exp).toDouble()
    }

    fun encodeInt32(value: Int): ByteArray {
        require(value in Int.MIN_VALUE..Int.MAX_VALUE)
        return ByteBuffer.allocate(4).putInt(value).array()
    }

    fun decodeInt32(bytes: ByteArray): Int {
        require(bytes.size >= 4)
        return ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.BIG_ENDIAN).getInt()
    }

    fun encodeFloat32(value: Float): ByteArray {
        return ByteBuffer.allocate(4).putFloat(value).array()
    }

    fun decodeFloat32(bytes: ByteArray): Float {
        require(bytes.size >= 4)
        return ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.BIG_ENDIAN).getFloat()
    }

    fun encodeString14(text: String): ByteArray {
        val encoded = text.toByteArray(Charsets.ISO_8859_1)
        return ByteArray(14).also { out ->
            encoded.copyInto(out, destinationOffset = 0, startIndex = 0, endIndex = minOf(14, encoded.size))
        }
    }

    fun decodeString14(bytes: ByteArray): String {
        val trimmed = bytes.dropLastWhile { it == 0.toByte() }.toByteArray()
        return String(trimmed, Charsets.ISO_8859_1)
    }

    fun encodeScene(sceneNumber1Based: Int): ByteArray {
        require(sceneNumber1Based in 1..64)
        return byteArrayOf(((sceneNumber1Based - 1) and 0x3F).toByte())
    }

    fun decodeScene(bytes: ByteArray): Int {
        require(bytes.isNotEmpty())
        return ((bytes.last().toInt() and 0x3F) + 1)
    }

    fun encodeHvacMode(mode: HvacMode): ByteArray = byteArrayOf(mode.code.toByte())
    fun decodeHvacMode(bytes: ByteArray): HvacMode? = HvacMode.fromCode(bytes.last().toInt() and 0xFF)

    fun decode(rawDpt: String?, payload: ByteArray): String {
        if (payload.isEmpty()) return ""
        return runCatching {
            when (resolveMainDpt(rawDpt)) {
                MainDpt.DPT_1_BOOL -> if (decodeBool(payload)) "ON" else "OFF"
                MainDpt.DPT_3_DIM_STEP -> decodeDimmingStep(payload)
                MainDpt.DPT_5_PERCENT -> String.format(java.util.Locale.US, "%.1f", decodePercent(payload)) + " %"
                MainDpt.DPT_5_UNSIGNED -> decodeUnsigned8(payload).toString()
                MainDpt.DPT_9_FLOAT -> {
                    val u = unitForDpt(normalizeDptId(rawDpt))
                    val num = String.format(java.util.Locale.US, "%.2f", decodeFloat16(payload))
                    if (u.isNotEmpty()) "$num $u" else num
                }
                MainDpt.DPT_13_INT32 -> decodeInt32(payload).toString()
                MainDpt.DPT_14_FLOAT32 -> {
                    val u = unitForDpt(normalizeDptId(rawDpt))
                    val num = String.format(java.util.Locale.US, "%.2f", decodeFloat32(payload))
                    if (u.isNotEmpty()) "$num $u" else num
                }
                MainDpt.DPT_16_STRING -> "\"${decodeString14(payload)}\""
                MainDpt.DPT_17_SCENE -> "Scene ${decodeScene(payload)}"
                MainDpt.DPT_20_HVAC -> decodeHvacMode(payload)?.label ?: "HVAC(${decodeUnsigned8(payload)})"
                MainDpt.RAW_BYTES -> "0x${HexBytes.toHex(payload, " ")}"
            }
        }.getOrElse { "0x${HexBytes.toHex(payload, " ")}" }
    }
}
