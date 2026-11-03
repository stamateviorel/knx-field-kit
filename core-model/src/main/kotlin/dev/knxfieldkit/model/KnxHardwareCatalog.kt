package dev.knxfieldkit.model

/**
 * Official KNX Association Manufacturer IDs and Device Descriptor 0 (Mask Version) Catalog.
 * Used across ETS Diagnostics to translate raw hex numbers into friendly manufacturer names and hardware profiles.
 */
object KnxHardwareCatalog {

    private val MANUFACTURERS = mapOf(
        0x0001 to "ABB",
        0x0002 to "Siemens",
        0x0004 to "Berker",
        0x0005 to "Jung",
        0x0007 to "Gira",
        0x0014 to "Hager",
        0x0016 to "Schneider Electric / Merten",
        0x0083 to "MDT Technologies",
        0x00C5 to "Zennio",
        0x00C8 to "Theben",
        0x00F0 to "Weinzierl",
        0x00FA to "GVS",
        0x0113 to "Basalte",
        0x0130 to "Elsner Elektronik",
        0x01F5 to "Enertex",
        0x0276 to "Ekinex",
        0x02E3 to "Bleu Comm Azur",
        0x03FF to "KNX Association",
    )

    private val MASKS = mapOf(
        "0010" to "BCU 1 (System 1 - TP)",
        "0011" to "BCU 1 (System 1 - PL110)",
        "0012" to "BCU 1 (System 1 - RF)",
        "0020" to "BCU 2 (System 2 - TP)",
        "0021" to "BCU 2 (System 2 - BIM M111)",
        "0025" to "BCU 2 (System 2 - TP)",
        "0300" to "TP Media Coupler / Repeater",
        "0570" to "KNX IP Interface (ISO 22510)",
        "057B" to "KNX IP Router (ISO 22510)",
        "0701" to "System 7 (BIM M112)",
        "0705" to "System 7 (BIM M112)",
        "07B0" to "System 7 (BIM M112)",
        "0910" to "System B (System 9 / Cortex-M)",
        "091A" to "System B (System 9 / Cortex-M)",
        "1012" to "System 1 - RF S-Mode",
        "2010" to "LTE / IoT Gateway",
    )

    fun resolveManufacturer(id: Int?): String? = id?.let { MANUFACTURERS[it] }

    fun resolveMask(maskHex: String?): String? {
        if (maskHex == null) return null
        val clean = maskHex.trim().removePrefix("0x").removeSuffix("h").removeSuffix("H").uppercase()
        return MASKS[clean] ?: MASKS[clean.padStart(4, '0')]
    }
}
