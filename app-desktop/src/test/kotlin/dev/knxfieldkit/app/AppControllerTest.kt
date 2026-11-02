package dev.knxfieldkit.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import dev.knxfieldkit.mobile.MobileFieldApp
import dev.knxfieldkit.model.ConnectionState
import dev.knxfieldkit.model.GroupAddress
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
    fun initialControllerStateIsCleanWithNoProjectLoaded() = runBlocking {
        val controller = AppController()
        try {
            val state = controller.uiState.value
            assertNull(state.project, "Project must be null on launch")
            assertEquals("No Project Loaded", state.projectSourceLabel)
            assertNull(state.selectedDeviceAddress)
            assertEquals("", state.selectedGroupAddressInput)
            assertEquals(ConnectionState.Disconnected, state.connectionState)
            assertTrue(state.statusMessage.contains("Ready"))
        } finally {
            controller.close()
        }
    }

    @Test
    fun themeToggleCyclesSystemDarkLightSystem() = runBlocking {
        val controller = AppController()
        try {
            controller.setThemeMode(ThemeMode.SYSTEM)
            assertEquals(ThemeMode.SYSTEM, controller.uiState.value.themeMode)

            controller.toggleThemeMode()
            assertEquals(ThemeMode.DARK, controller.uiState.value.themeMode)

            controller.toggleThemeMode()
            assertEquals(ThemeMode.LIGHT, controller.uiState.value.themeMode)

            controller.toggleThemeMode()
            assertEquals(ThemeMode.SYSTEM, controller.uiState.value.themeMode)
        } finally {
            controller.close()
        }
    }

    @Test
    fun encryptedEts6SamplePromptsAndUnlocksInController() = runBlocking {
        val tmpDir = Files.createTempDirectory("ctrl-test-").toFile()
        val controller = AppController()
        try {
            val sampleFile = File(tmpDir, "penthouse_ets6.knxproj")
            controller.generateAndPromptEncryptedSample(sampleFile, samplePassword = "fieldPass!99")

            var attempts = 0
            while (controller.uiState.value.passwordPrompt == null && attempts < 50) {
                delay(50L)
                attempts++
            }
            assertNotNull(controller.uiState.value.passwordPrompt, "Expected password prompt for ETS6 encrypted .knxproj")

            controller.submitFilePassword("wrong-password")
            attempts = 0
            while (controller.uiState.value.passwordPrompt?.errorMessage == null && attempts < 50) {
                delay(50L)
                attempts++
            }
            assertNotNull(controller.uiState.value.passwordPrompt?.errorMessage)

            controller.submitFilePassword("fieldPass!99")
            attempts = 0
            while (controller.uiState.value.passwordPrompt != null && attempts < 50) {
                delay(50L)
                attempts++
            }
            assertNull(controller.uiState.value.passwordPrompt)
            assertEquals("Encrypted Penthouse ETS6 Sample", controller.uiState.value.project?.name)
            assertTrue(controller.uiState.value.project?.passwordProtected == true)
            assertTrue(controller.uiState.value.project?.effectiveGroupRanges?.isNotEmpty() == true)

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
    fun allFourComposeTabsAndEtsExplorerModesRenderCleanly() = runBlocking {
        val controller = AppController()
        val tmpReportDir = Files.createTempDirectory("html-rep-").toFile()
        val screenshotDir = System.getProperty("knx.screenshot.dir")?.let(::File)?.apply { mkdirs() }
        val scene = ImageComposeScene(width = 1480, height = 920) {
            KnxFieldKitTheme {
                KnxFieldKitApp(controller)
            }
        }
        try {
            controller.loadDemoProject()
            controller.connectSimulator()
            var simAttempts = 0
            while (controller.uiState.value.connectionState !is ConnectionState.Connected && simAttempts < 50) {
                delay(50L)
                simAttempts++
            }
            assertTrue(controller.uiState.value.connectionState is ConnectionState.Connected)

            controller.runFullSiteCheck()
            var attempts = 0
            while (controller.uiState.value.siteCheckReport == null && attempts < 180) {
                delay(50L)
                attempts++
            }
            val siteReport = controller.uiState.value.siteCheckReport
            assertNotNull(siteReport, "Expected One-Click Full Site Check report to complete")
            assertEquals(9, siteReport.onlineDevices)
            assertEquals(1, siteReport.unreachableDevices.size)

            // Also run Line 1.1 audit so Line 1.1 shows fault entry in Diagnostics
            controller.runProjectLineAudit(1, 1)
            attempts = 0
            while (controller.uiState.value.isAuditingLine && attempts < 60) {
                delay(40L)
                attempts++
            }

            val htmlFile = File(tmpReportDir, "commissioning.html")
            controller.exportHtmlCommissioningReport(htmlFile)
            attempts = 0
            while (!htmlFile.isFile && attempts < 40) {
                delay(50L)
                attempts++
            }
            assertTrue(htmlFile.isFile)
            assertTrue(htmlFile.readText().contains("Site Readiness Score"))

            val firstTelId = controller.uiState.value.telegrams.firstOrNull()?.telegram?.id
            controller.selectMonitorTelegram(firstTelId)
            controller.pingIndividualDevice("1.1.10")
            controller.inspectDeviceDiagnostics("1.1.10")
            delay(80L)

            var clockNs = 1_000_000_000L

            fun renderAndSave(fileName: String) {
                scene.render(clockNs)
                clockNs += 300_000_000L
                val image = scene.render(clockNs)
                clockNs += 50_000_000L
                assertEquals(1480, image.width)
                assertEquals(920, image.height)
                if (screenshotDir != null) {
                    val pngBytes = image.encodeToData(EncodedImageFormat.PNG)?.bytes
                    assertNotNull(pngBytes)
                    File(screenshotDir, fileName).writeBytes(pngBytes)
                }
            }

            // 1. ETS Explorer — Topology Devices Grid + Device Inspector + Docked Live Monitor
            controller.selectTab(AppTab.PROJECT)
            controller.selectExplorerNode(ExplorerSelection.TopologyRoot)
            controller.inspectDeviceInExplorer(IndividualAddress(1, 1, 10))
            delay(50L)
            renderAndSave("knx-v3-explorer-topology.png")

            // 2. ETS Explorer — Device Group Objects (C R W T U Flags) Grid!
            controller.openDeviceObjectsInExplorer(IndividualAddress(1, 1, 20))
            delay(50L)
            renderAndSave("knx-v3-explorer-device-objects.png")

            // 3. ETS Explorer — Group Range Folder + Group Address Cross-Reference Inspector!
            controller.selectExplorerNode(ExplorerSelection.GroupRange("gr-lighting"))
            controller.inspectGroupInExplorer(GroupAddress.parse("1/1/1"))
            delay(50L)
            renderAndSave("knx-v3-explorer-groups.png")

            // 4. Bus Monitor, Sender, & Field Diagnostics
            controller.selectTab(AppTab.MONITOR)
            delay(40L)
            renderAndSave("knx-v3-monitor.png")

            controller.selectTab(AppTab.SENDER)
            delay(40L)
            renderAndSave("knx-v3-sender.png")

            controller.selectTab(AppTab.DIAGNOSTICS)
            delay(40L)
            renderAndSave("knx-v3-diagnostics.png")

            // 5. Building Rooms Tree + Connect Gateway Dialog smoke render
            controller.selectTab(AppTab.PROJECT)
            controller.setExplorerLocationView(true)
            delay(40L)
            scene.render(clockNs + 200_000_000L)

            controller.toggleConnectDialog(true)
            delay(40L)
            val dlgImg = scene.render(clockNs + 500_000_000L)
            assertEquals(1480, dlgImg.width)

            // 6. Light Mode Desktop Views
            controller.toggleConnectDialog(false)
            controller.setThemeMode(ThemeMode.LIGHT)
            controller.selectTab(AppTab.PROJECT)
            delay(50L)
            renderAndSave("knx-v3-explorer-light.png")

            controller.selectTab(AppTab.MONITOR)
            delay(50L)
            renderAndSave("knx-v3-monitor-light.png")

            controller.setThemeMode(ThemeMode.DARK)
        } finally {
            scene.close()
            controller.close()
            tmpReportDir.deleteRecursively()
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun androidMobileFieldAppRendersAllPhoneScreensCleanly() = runBlocking {
        val controller = AppController()
        val screenshotDir = System.getProperty("knx.screenshot.dir")?.let(::File)?.apply { mkdirs() }
        // 412x915 dp Android flagship phone viewport @ 2x density -> 824x1830 px
        val scene = ImageComposeScene(width = 824, height = 1830, density = Density(2f)) {
            MobileFieldApp(controller = controller)
        }
        try {
            controller.loadDemoProject()
            controller.connectSimulator()
            var simAttempts = 0
            while (controller.uiState.value.connectionState !is ConnectionState.Connected && simAttempts < 50) {
                delay(50L)
                simAttempts++
            }
            assertTrue(controller.uiState.value.connectionState is ConnectionState.Connected)

            controller.runFullSiteCheck()
            var attempts = 0
            while (controller.uiState.value.siteCheckReport == null && attempts < 180) {
                delay(50L)
                attempts++
            }
            assertNotNull(controller.uiState.value.siteCheckReport)

            controller.runProjectLineAudit(1, 1)
            attempts = 0
            while (controller.uiState.value.isAuditingLine && attempts < 60) {
                delay(40L)
                attempts++
            }

            var clockNs = 1_000_000_000L
            fun renderMobile(fileName: String) {
                scene.render(clockNs)
                clockNs += 300_000_000L
                val image = scene.render(clockNs)
                clockNs += 50_000_000L
                assertEquals(824, image.width)
                assertEquals(1830, image.height)
                if (screenshotDir != null) {
                    val pngBytes = image.encodeToData(EncodedImageFormat.PNG)?.bytes
                    assertNotNull(pngBytes)
                    File(screenshotDir, fileName).writeBytes(pngBytes)
                }
            }

            // 1. Mobile Explorer — Topology Lines & Devices
            controller.selectTab(AppTab.PROJECT)
            controller.selectExplorerNode(ExplorerSelection.TopologyRoot)
            delay(50L)
            renderMobile("knx-android-explorer.png")

            // 2. Mobile Explorer — Device Group Objects (C R W T U flags + 1-tap controls)
            controller.openDeviceObjectsInExplorer(IndividualAddress(1, 1, 20))
            delay(50L)
            renderMobile("knx-android-device-objects.png")

            // 3. Mobile Live Bus Monitor (emit live DPT writes so decoded values shine)
            controller.writeGroupSwitch("1/1/1", true)
            controller.writeGroupPercent("1/2/2", 75.0)
            controller.writeGroupFloat16("1/3/1", 22.4)
            delay(80L)
            controller.selectTab(AppTab.MONITOR)
            delay(80L)
            renderMobile("knx-android-monitor.png")

            // 4. Mobile Smart DPT Sender
            controller.selectTab(AppTab.SENDER)
            controller.selectGroupAddressForSender("1/1/1")
            delay(80L)
            renderMobile("knx-android-sender.png")

            // 5. Mobile Field Diagnostics & Readiness Score
            controller.selectTab(AppTab.DIAGNOSTICS)
            delay(80L)
            renderMobile("knx-android-diagnostics.png")

            // 6. Mobile Wi-Fi KNXnet/IP Gateway Connect Bottom Sheet
            controller.discoverGateways()
            delay(150L)
            attempts = 0
            while (controller.uiState.value.isDiscoveringGateways && attempts < 80) {
                delay(50L)
                attempts++
            }
            controller.toggleConnectDialog(true)
            delay(80L)
            renderMobile("knx-android-connect-sheet.png")

            // 7. Light Mode Mobile Views
            controller.toggleConnectDialog(false)
            controller.setThemeMode(ThemeMode.LIGHT)
            controller.selectTab(AppTab.PROJECT)
            delay(80L)
            renderMobile("knx-android-explorer-light.png")

            controller.selectTab(AppTab.MONITOR)
            delay(80L)
            renderMobile("knx-android-monitor-light.png")
        } finally {
            scene.close()
            controller.close()
        }
    }
}
