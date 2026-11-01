package dev.knxfieldkit.app

import dev.knxfieldkit.model.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.Properties

data class SavedPreferences(val recentFiles: List<File> = emptyList(), val lastGatewayHost: String = "192.168.1.10", val lastGatewayPort: Int = 3671, val lastUseNat: Boolean = false)

object UserPrefsStore {
    private val prefsFile = File(System.getProperty("user.home"), ".knx-field-kit/prefs.properties")
    private val properties = Properties()

    init {
        runCatching {
            if (!prefsFile.parentFile.exists()) prefsFile.parentFile.mkdirs()
            if (prefsFile.exists()) {
                FileInputStream(prefsFile).use { properties.load(InputStreamReader(it, StandardCharsets.UTF_8)) }
            }
        }
    }

    @Synchronized
    fun load(): SavedPreferences {
        val recentFiles = properties.getProperty("recent.files", "")
            .split("|")
            .filter { it.isNotBlank() }
            .map { File(it) }
            .filter { it.isFile }
            .take(8)
        val lastGatewayHost = properties.getProperty("last.gateway.host", "192.168.1.10")
        val lastGatewayPort = properties.getProperty("last.gateway.port", "3671").toIntOrNull() ?: 3671
        val lastUseNat = properties.getProperty("last.use.nat", "false").toBoolean()
        return SavedPreferences(recentFiles, lastGatewayHost, lastGatewayPort, lastUseNat)
    }

    @Synchronized
    fun recordRecentFile(file: File): SavedPreferences {
        val current = load()
        val recentFiles = (listOf(file.absoluteFile) + current.recentFiles.map { it.absoluteFile })
            .distinctBy { it.absolutePath }
            .take(8)
        properties.setProperty("recent.files", recentFiles.joinToString("|") { it.absolutePath })
        saveProperties()
        return current.copy(recentFiles = recentFiles)
    }

    @Synchronized
    fun saveGateway(host: String, port: Int, useNat: Boolean): SavedPreferences {
        val current = load()
        properties.setProperty("last.gateway.host", host)
        properties.setProperty("last.gateway.port", port.toString())
        properties.setProperty("last.use.nat", useNat.toString())
        saveProperties()
        return current.copy(lastGatewayHost = host, lastGatewayPort = port, lastUseNat = useNat)
    }

    private fun saveProperties() {
        runCatching {
            if (!prefsFile.parentFile.exists()) prefsFile.parentFile.mkdirs()
            FileOutputStream(prefsFile).use {
                OutputStreamWriter(it, StandardCharsets.UTF_8).use { writer -> properties.store(writer, null) }
            }
        }
    }
}

object HtmlReportExporter {
    private fun esc(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    fun exportCommissioningHtml(
        targetFile: File,
        project: KnxProject?,
        siteCheck: SiteCheckReport?,
        deviceStatuses: Map<IndividualAddress, DevicePingResult>,
        groupLiveValues: Map<GroupAddress, GroupValueEntry>,
    ) {
        val html = buildString {
            append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"><title>KNX Field Kit Commissioning Report</title><style>")
            append(
                """
                body { font-family: Segoe UI, system-ui, sans-serif; background: #0f172a; color: #e2e8f0; padding: 24px; }
                h1, h2 { color: #38bdf8; }
                .score { display: inline-block; padding: 8px 16px; border-radius: 8px; background: #1e293b; font-size: 20px; font-weight: bold; color: #2ecc71; }
                table { width: 100%; border-collapse: collapse; margin-bottom: 24px; background: #1e293b; }
                th, td { border: 1px solid #334155; padding: 8px 10px; text-align: left; font-size: 13px; }
                th { background: #334155; color: #f8fafc; }
                .ok { color: #2ecc71; font-weight: bold; }
                .err { color: #ef4444; font-weight: bold; }
                .muted { color: #94a3b8; }
                @media print { body { background: #fff; color: #111; } table, th, td { border-color: #ccc; background: #fff; color: #111; } }
                """.trimIndent()
            )
            append("</style></head><body><h1>KNX Field Kit — Site Commissioning Report</h1>")
            if (project != null) {
                append("<p><strong>Project:</strong> ${esc(project.name)} (${esc(project.projectId)}) &bull; <strong>ETS:</strong> ${esc(project.etsVersion.ifBlank { "N/A" })} &bull; <strong>Protected Archive:</strong> ${project.passwordProtected}</p>")
            }
            if (siteCheck != null) {
                append("<div class=\"score\">Site Readiness Score: ${siteCheck.readinessScorePercent}% (${siteCheck.onlineDevices}/${siteCheck.totalDevicesExpected} Devices Online)</div><ul>")
                siteCheck.findings.forEach { append("<li>${esc(it)}</li>") }
                append("</ul>")
            }
            append("<h2>Topology &amp; Live Device Verification</h2><table><tr><th>Address</th><th>Device Name</th><th>Room / Location</th><th>Manufacturer &amp; Product</th><th>Expected Mask</th><th>Bus Status</th><th>Live Mask</th><th>Latency</th></tr>")
            val devices = project?.allDevices?.values?.sortedBy { it.address }.orEmpty()
            for (dev in devices) {
                val st = deviceStatuses[dev.address]
                val loc = project?.findDeviceLocationPath(dev.address) ?: "-"
                val statusHtml = when {
                    st == null -> "<span class=\"muted\">NOT TESTED</span>"
                    st.reachable -> "<span class=\"ok\">ONLINE</span>"
                    else -> "<span class=\"err\">UNREACHABLE</span>"
                }
                append("<tr><td><code>${dev.address}</code></td><td>${esc(dev.name)}</td><td>${esc(loc)}</td><td>${esc("${dev.manufacturerName} ${dev.productName} ${dev.orderNumber}".trim())}</td><td><code>${esc(dev.maskVersion ?: "-")}</code></td><td>$statusHtml</td><td><code>${st?.maskVersionHex ?: "-"}</code></td><td>${st?.let { "${it.latencyMs} ms" } ?: "-"}</td></tr>")
            }
            append("</table>")
            append("<h2>Group Addresses &amp; Live Bus Values</h2><table><tr><th>Group Address</th><th>Name</th><th>DPT</th><th>Linked ComObjects</th><th>Last Bus Value</th></tr>")
            for (ga in project?.groupAddresses?.values?.sortedBy { it.address }.orEmpty()) {
                val lv = groupLiveValues[ga.address]?.valueText ?: "-"
                append("<tr><td><code>${ga.address}</code></td><td>${esc(ga.name)}</td><td><code>${esc(ga.dpt ?: "raw")}</code></td><td>${ga.linkedObjects.size}</td><td><code>${esc(lv)}</code></td></tr>")
            }
            append("</table></body></html>")
        }
        targetFile.writeText(html, StandardCharsets.UTF_8)
    }
}
