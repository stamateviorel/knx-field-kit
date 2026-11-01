package dev.knxfieldkit.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import dev.knxfieldkit.model.ConnectionState
import dev.knxfieldkit.model.IndividualAddress
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppControllerTest {

    @Test
    fun encryptedEts6SamplePromptsAndUnlocksInController() = runBlocking {
        val tmpDir = Files.createTempDirectory("ctrl-test-").toFile()
        val controller = AppController()
        try {
            val sampleFile = File(tmpDir, "penthouse_ets6.knxproj")
            controller.generateAndPromptEncryptedSample(sampleFile, samplePassword = "fieldPass!99")

            // Wait until controller detects password protection and opens prompt
            var attempts = 0
            while (controller.uiState.value.passwordPrompt == null && attempts < 50) {
                delay(50L)
                attempts++
            }
            assertNotNull(controller.uiState.value.passwordPrompt, "Expected password prompt for ETS6 encrypted .knxproj")

            // Wrong password keeps prompt open with error message
            controller.submitFilePassword("wrong-password")
            attempts = 0
            while (controller.uiState.value.passwordPrompt?.errorMessage == null && attempts < 50) {
                delay(50L)
                attempts++
            }
            assertNotNull(controller.uiState.value.passwordPrompt?.errorMessage)

            // Correct password unlocks project and closes prompt
            controller.submitFilePassword("fieldPass!99")
            attempts = 0
            while (controller.uiState.value.passwordPrompt != null && attempts < 50) {
                delay(50L)
                attempts++
            }
            assertNull(controller.uiState.value.passwordPrompt)
            assertEquals("Encrypted Penthouse ETS6 Sample", controller.uiState.value.project?.name)
            assertTrue(controller.uiState.value.project?.passwordProtected == true)

            // Connect simulator and run Line 1.1 audit
            controller.connectSimulator()
            attempts = 0
            while (controller.uiState.value.connectionState !is ConnectionState.Connected && attempts < 40) {
                delay(50L)
                attempts++
            }
            assertTrue(controller.uiState.value.connectionState is ConnectionState.Connected)

            controller.toggleSimulatedDeviceOnline(IndividualAddress(1, 1, 20), false)
            controller.runProjectLineAudit(1, 1)
            attempts = 0
            while (controller.uiState.value.lineAuditReport == null && attempts < 60) {
                delay(50L)
                attempts++
            }
            val audit = controller.uiState.value.lineAuditReport
            assertNotNull(audit)
            assertEquals(2, audit.onlineCount)
            assertEquals(1, audit.unreachableCount)
        } finally {
            controller.close()
            tmpDir.deleteRecursively()
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun allFourComposeTabsAndDialogsRenderCleanly() = runBlocking {
        val controller = AppController()
        val tmpReportDir = Files.createTempDirectory("html-rep-").toFile()
        val screenshotDir = System.getProperty("knx.screenshot.dir")?.let(::File)?.apply { mkdirs() }
        val scene = ImageComposeScene(width = 1420, height = 880) {
            KnxFieldKitTheme {
                KnxFieldKitApp(controller)
            }
        }
        try {
            controller.runFullSiteCheck()
            var attempts = 0
            while (controller.uiState.value.siteCheckReport == null && attempts < 140) {
                delay(50L)
                attempts++
            }
            val siteReport = controller.uiState.value.siteCheckReport
            assertNotNull(siteReport, "Expected One-Click Full Site Check report to complete")
            assertEquals(5, siteReport.onlineDevices)
            assertEquals(1, siteReport.unreachableDevices.size)

            val htmlFile = File(tmpReportDir, "commissioning.html")
            controller.exportHtmlCommissioningReport(htmlFile)
            attempts = 0
            while (!htmlFile.isFile && attempts < 40) {
                delay(50L)
                attempts++
            }
            assertTrue(htmlFile.isFile)
            assertTrue(htmlFile.readText().contains("Site Readiness Score"))

            // Select a telegram in BusMonitor so the right-hand Telegram Inspector & Replay drawer renders too
            val firstTelId = controller.uiState.value.telegrams.firstOrNull()?.telegram?.id
            controller.selectMonitorTelegram(firstTelId)
            controller.pingIndividualDevice("1.1.10")
            controller.inspectDeviceDiagnostics("1.1.10")
            delay(80L)

            var clockNs = 1_000_000_000L
            for (tab in AppTab.entries) {
                controller.selectTab(tab)
                delay(40L)
                scene.render(clockNs)
                clockNs += 350_000_000L
                val image = scene.render(clockNs)
                clockNs += 50_000_000L
                assertEquals(1420, image.width)
                assertEquals(880, image.height)
                if (screenshotDir != null) {
                    val pngBytes = image.encodeToData(EncodedImageFormat.PNG)?.bytes
                    assertNotNull(pngBytes)
                    File(screenshotDir, "knx-field-kit-v2-${tab.name.lowercase()}.png").writeBytes(pngBytes)
                }
            }

            // Also render Project Explorer in Group Address Cross-Reference Inspector mode + Rooms view
            controller.selectTab(AppTab.PROJECT)
            controller.setExplorerLocationView(true)
            controller.inspectGroupInExplorer(dev.knxfieldkit.model.GroupAddress.parse("1/1/1"))
            delay(40L)
            val gaInspImg = scene.render(clockNs + 300_000_000L)
            assertEquals(1420, gaInspImg.width)

            // Render Gateway Connect Dialog as well
            controller.toggleConnectDialog(true)
            delay(40L)
            val dlgImg = scene.render(clockNs + 600_000_000L)
            assertEquals(1420, dlgImg.width)
        } finally {
            scene.close()
            controller.close()
            tmpReportDir.deleteRecursively()
        }
    }
}
