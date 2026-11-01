package dev.knxfieldkit.model

class IndividualAddress(val area: Int, val line: Int, val device: Int) : Comparable<IndividualAddress> {
    init {
        require(area in 0..15) { "Area out of range: $area" }
        require(line in 0..15) { "Line out of range: $line" }
        require(device in 0..255) { "Device out of range: $device" }
    }

    val raw: Int get() = (area shl 12) or (line shl 8) or device

    override fun toString(): String = "$area.$line.$device"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as IndividualAddress
        return raw == other.raw
    }

    override fun hashCode(): Int = raw

    override fun compareTo(other: IndividualAddress): Int = raw.compareTo(other.raw)

    companion object {
        fun fromRaw(raw: Int): IndividualAddress {
            require(raw in 0..0xFFFF) { "Raw value out of range: $raw" }
            val area = (raw ushr 12) and 0x0F
            val line = (raw ushr 8) and 0x0F
            val device = raw and 0xFF
            return IndividualAddress(area, line, device)
        }

        fun parse(text: String): IndividualAddress {
            val trimmed = text.trim()
            return if ('.' in trimmed) {
                val parts = trimmed.split('.').map { it.toInt() }
                require(parts.size == 3) { "Invalid format: $trimmed" }
                IndividualAddress(parts[0], parts[1], parts[2])
            } else {
                val raw = trimmed.toIntOrNull()
                require(raw != null && raw in 0..0xFFFF) { "Invalid raw value: $trimmed" }
                fromRaw(raw)
            }
        }

        fun parseOrNull(text: String): IndividualAddress? {
            return try {
                parse(text)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
}

enum class GroupAddressStyle { THREE_LEVEL, TWO_LEVEL, FREE }

class GroupAddress(val raw: Int, val style: GroupAddressStyle = GroupAddressStyle.THREE_LEVEL) : Comparable<GroupAddress> {
    init {
        require(raw in 0..0xFFFF) { "Group address out of 16-bit range: $raw" }
    }

    val main3: Int get() = (raw ushr 11) and 0x1F
    val middle3: Int get() = (raw ushr 8) and 0x07
    val sub3: Int get() = raw and 0xFF
    val main2: Int get() = (raw ushr 11) and 0x1F
    val sub2: Int get() = raw and 0x7FF

    fun format(overrideStyle: GroupAddressStyle = style): String {
        return when (overrideStyle) {
            GroupAddressStyle.THREE_LEVEL -> "$main3/$middle3/$sub3"
            GroupAddressStyle.TWO_LEVEL -> "$main2/$sub2"
            GroupAddressStyle.FREE -> "$raw"
        }
    }

    override fun toString(): String = format(style)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as GroupAddress
        return raw == other.raw
    }

    override fun hashCode(): Int = raw

    override fun compareTo(other: GroupAddress): Int = raw.compareTo(other.raw)

    fun withStyle(newStyle: GroupAddressStyle): GroupAddress = GroupAddress(raw, newStyle)

    companion object {
        fun fromRaw(raw: Int, style: GroupAddressStyle = GroupAddressStyle.THREE_LEVEL): GroupAddress =
            GroupAddress(raw, style)

        fun ofThreeLevel(main: Int, middle: Int, sub: Int): GroupAddress {
            require(main in 0..31) { "Main level out of range: $main" }
            require(middle in 0..7) { "Middle level out of range: $middle" }
            require(sub in 0..255) { "Sub level out of range: $sub" }
            return GroupAddress((main shl 11) or (middle shl 8) or sub, GroupAddressStyle.THREE_LEVEL)
        }

        fun ofTwoLevel(main: Int, sub: Int): GroupAddress {
            require(main in 0..31) { "Main level out of range: $main" }
            require(sub in 0..2047) { "Sub level out of range: $sub" }
            return GroupAddress((main shl 11) or sub, GroupAddressStyle.TWO_LEVEL)
        }

        fun parse(text: String): GroupAddress {
            val trimmed = text.trim()
            return if ('/' in trimmed) {
                val parts = trimmed.split('/').map { it.toInt() }
                when (parts.size) {
                    3 -> ofThreeLevel(parts[0], parts[1], parts[2])
                    2 -> ofTwoLevel(parts[0], parts[1])
                    else -> throw IllegalArgumentException("Invalid format: $trimmed")
                }
            } else {
                val raw = trimmed.toIntOrNull()
                require(raw != null && raw in 0..0xFFFF) { "Invalid raw value: $trimmed" }
                GroupAddress(raw, GroupAddressStyle.FREE)
            }
        }

        fun parseOrNull(text: String): GroupAddress? {
            return try {
                parse(text)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
}
