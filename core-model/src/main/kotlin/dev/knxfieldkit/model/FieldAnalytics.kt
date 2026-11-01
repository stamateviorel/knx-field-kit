package dev.knxfieldkit.model

enum class BusLoadLevel(val label: String) {
    IDLE("Idle"),
    HEALTHY("Healthy"),
    ELEVATED("Elevated"),
    STORM("Telegram Storm!")
}

data class TalkerStat(val address: String, val label: String, val telegramCount: Int)

data class BusHealthStats(
    val telegramsPerSecond: Double,
    val estimatedTp1LoadPercent: Double,
    val loadLevel: BusLoadLevel,
    val totalTelegrams: Int,
    val ghostGroupCount: Int,
    val repeatedCount: Int,
    val unansweredReadAddresses: List<GroupAddress>,
    val topSourceDevices: List<TalkerStat>,
    val topGroupAddresses: List<TalkerStat>
)

data class SiteCheckReport(
    val completedAtEpochMs: Long,
    val readinessScorePercent: Int,
    val totalDevicesExpected: Int,
    val onlineDevices: Int,
    val unreachableDevices: List<ProjectDevice>,
    val maskMismatchDevices: List<ProjectDevice>,
    val polledGroupsTotal: Int,
    val polledGroupsResponded: Int,
    val activeProgrammingLeds: List<IndividualAddress>,
    val ghostGroupAddresses: List<GroupAddress>,
    val findings: List<String>
)

data class IndividualAddressWriteResult(val success: Boolean, val newAddress: IndividualAddress, val message: String)

object BusHealthAnalyzer {
    fun analyze(telegrams: List<EnrichedTelegram>, nowEpochMs: Long = System.currentTimeMillis(), windowMs: Long = 10_000L): BusHealthStats {
        val recent = telegrams.filter { nowEpochMs - it.telegram.timestampEpochMs in 0..windowMs }
        val telegramsPerSecond = recent.size * 1000.0 / windowMs.coerceAtLeast(1000L)
        val estimatedTp1LoadPercent = (telegramsPerSecond / 43.0 * 100.0).coerceIn(0.0, 100.0)
        val loadLevel = when {
            estimatedTp1LoadPercent <= 0.0 -> BusLoadLevel.IDLE
            estimatedTp1LoadPercent < 35.0 -> BusLoadLevel.HEALTHY
            estimatedTp1LoadPercent < 65.0 -> BusLoadLevel.ELEVATED
            else -> BusLoadLevel.STORM
        }
        val ghostGroupCount = telegrams.filter { it.isUnmappedGroup }.mapNotNull { it.telegram.destinationGroup }.distinct().size
        val repeatedCount = telegrams.count { it.telegram.repeated }
        val unansweredReadAddresses = telegrams
            .filter { it.telegram.apci == ApciType.GROUP_READ && it.telegram.destinationGroup != null }
            .mapNotNull { readRow ->
                val ga = readRow.telegram.destinationGroup ?: return@mapNotNull null
                val readTs = readRow.telegram.timestampEpochMs
                val answered = telegrams.any { other ->
                    other.telegram.destinationGroup == ga &&
                        other.telegram.timestampEpochMs >= readTs &&
                        (other.telegram.apci == ApciType.GROUP_RESPONSE || other.telegram.apci == ApciType.GROUP_WRITE)
                }
                if (!answered && nowEpochMs - readTs >= 1500L) ga else null
            }
            .distinct()
        val topSourceDevices = telegrams
            .groupBy { it.telegram.source.toString() }
            .map { (address, rows) -> TalkerStat(address, rows.firstNotNullOfOrNull { it.sourceDeviceName } ?: address, rows.size) }
            .sortedByDescending { it.telegramCount }
            .take(5)
        val topGroupAddresses = telegrams
            .filter { it.telegram.destinationGroup != null }
            .groupBy { it.telegram.destinationGroup.toString() }
            .map { (address, rows) -> TalkerStat(address, rows.firstNotNullOfOrNull { it.destinationName } ?: address, rows.size) }
            .sortedByDescending { it.telegramCount }
            .take(5)
        return BusHealthStats(
            telegramsPerSecond,
            estimatedTp1LoadPercent,
            loadLevel,
            telegrams.size,
            ghostGroupCount,
            repeatedCount,
            unansweredReadAddresses,
            topSourceDevices,
            topGroupAddresses
        )
    }

    fun buildSiteCheckReport(project: KnxProject?, deviceStatuses: Map<IndividualAddress, DevicePingResult>, polledTotal: Int, polledResponded: Int, activeProgLeds: List<IndividualAddress>, telegrams: List<EnrichedTelegram>): SiteCheckReport {
        val allDevices = project?.allDevices?.values.orEmpty().toList()
        val unreachableDevices = allDevices.filter { deviceStatuses[it.address]?.reachable != true }
        val maskMismatchDevices = allDevices.filter { dev ->
            val ping = deviceStatuses[dev.address]
            val expMask = dev.maskVersion?.uppercase()?.removePrefix("MV-")?.trim()?.takeIf { it.isNotEmpty() }
            ping?.reachable == true && expMask != null && ping.maskVersionHex != null && expMask != ping.maskVersionHex
        }
        val onlineDevices = allDevices.count { deviceStatuses[it.address]?.reachable == true }
        val ghostGroupAddresses = telegrams.filter { it.isUnmappedGroup }.mapNotNull { it.telegram.destinationGroup }.distinct()
        val offlinePenalty = if (allDevices.isNotEmpty()) ((unreachableDevices.size * 100.0) / allDevices.size * 0.7).toInt() else 0
        val readinessScorePercent = 100 - offlinePenalty - (maskMismatchDevices.size * 5) -
            (if (activeProgLeds.isNotEmpty()) 10 else 0) - (if (ghostGroupAddresses.isNotEmpty()) 5 else 0)
        val findings = mutableListOf<String>()
        if (unreachableDevices.isNotEmpty()) findings.add("Offline devices: ${unreachableDevices.joinToString(", ") { it.name }}")
        if (maskMismatchDevices.isNotEmpty()) findings.add("Mask mismatches: ${maskMismatchDevices.joinToString(", ") { it.name }}")
        if (activeProgLeds.isNotEmpty()) findings.add("Active Programming LEDs: ${activeProgLeds.joinToString(", ") { it.toString() }}")
        if (ghostGroupAddresses.isNotEmpty()) findings.add("Ghost Group Addresses: ${ghostGroupAddresses.joinToString(", ") { it.toString() }}")
        if (findings.isEmpty()) findings.add("All checked KNX devices and bus checks passed.")
        return SiteCheckReport(
            completedAtEpochMs = System.currentTimeMillis(),
            readinessScorePercent = readinessScorePercent.coerceIn(0, 100),
            totalDevicesExpected = allDevices.size,
            onlineDevices = onlineDevices,
            unreachableDevices = unreachableDevices,
            maskMismatchDevices = maskMismatchDevices,
            polledGroupsTotal = polledTotal,
            polledGroupsResponded = polledResponded,
            activeProgrammingLeds = activeProgLeds,
            ghostGroupAddresses = ghostGroupAddresses,
            findings = findings
        )
    }
}
