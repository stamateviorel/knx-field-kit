package dev.knxfieldkit.android

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import dev.knxfieldkit.app.AppController
import dev.knxfieldkit.app.AppTab
import dev.knxfieldkit.app.ExplorerSelection
import dev.knxfieldkit.app.ThemeMode
import dev.knxfieldkit.app.UserPrefsStore
import dev.knxfieldkit.bus.PureKnxNetIpClient
import dev.knxfieldkit.model.IndividualAddress
import dev.knxfieldkit.mobile.MobileFieldApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {

    private var multicastLock: WifiManager.MulticastLock? = null
    private lateinit var controller: AppController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        UserPrefsStore.configureBaseDir(filesDir)

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        multicastLock = wifiManager?.createMulticastLock("knx-field-kit-multicast")?.apply {
            setReferenceCounted(false)
        }

        controller = AppController(
            hardwareClientFactory = { scope -> PureKnxNetIpClient(scope) },
            multicastAcquireCallback = {
                runCatching {
                    if (multicastLock?.isHeld == false) {
                        multicastLock?.acquire()
                    }
                }
            },
            multicastReleaseCallback = {
                runCatching {
                    if (multicastLock?.isHeld == true) {
                        multicastLock?.release()
                    }
                }
            },
        )

        // Handle .knxproj / .knxkeys tapped directly from Files, WhatsApp, Drive, or Email
        val hasFilePayload = (intent?.action == Intent.ACTION_VIEW || intent?.action == Intent.ACTION_SEND) &&
            (intent.data != null || intent.clipData != null || intent.hasExtra(Intent.EXTRA_STREAM))
        if (hasFilePayload) {
            handleIncomingFileIntent(intent)
        } else {
            // Auto-open last imported project if no project is active
            val lastImport = File(filesDir, "imports").listFiles()
                ?.filter { it.isFile && (it.extension.equals("knxproj", true) || it.extension.equals("knxkeys", true)) }
                ?.maxByOrNull { it.lastModified() }
            if (lastImport != null && controller.uiState.value.project == null) {
                controller.openProjectOrKeyringFile(lastImport)
            }
        }

        setContent {
            val openDocumentLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument(),
            ) { uri: Uri? ->
                if (uri != null) {
                    importAndOpenUri(uri)
                }
            }

            MobileFieldApp(
                controller = controller,
                modifier = Modifier.safeDrawingPadding(),
                onOpenProjectFile = {
                    openDocumentLauncher.launch(arrayOf("*/*"))
                },
                onShareHtmlReport = {
                    val target = File(File(filesDir, "exports").apply { mkdirs() }, "knx-commissioning-report.html")
                    controller.exportHtmlCommissioningReport(target) { exported ->
                        shareExportedFile(exported, "text/html", "Share KNX Commissioning Report")
                    }
                },
                onShareMonitorCsv = {
                    val target = File(File(filesDir, "exports").apply { mkdirs() }, "knx-bus-monitor.csv")
                    controller.exportMonitorCsv(target) { exported ->
                        shareExportedFile(exported, "text/csv", "Share KNX Bus Monitor Log")
                    }
                },
                onShareMarkdownSummary = {
                    val target = File(File(filesDir, "exports").apply { mkdirs() }, "knx-project-summary.md")
                    controller.exportProjectMarkdown(target) { exported ->
                        shareExportedFile(exported, "text/markdown", "Share KNX Project Summary")
                    }
                },
            )
        }

        handleIncomingFileIntent(intent)
        handleNavigationIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingFileIntent(intent)
        handleNavigationIntent(intent)
    }

    private fun handleNavigationIntent(intent: Intent?) {
        if (intent == null) return
        val tab = intent.getStringExtra("tab")
        if (tab != null) {
            when (tab.uppercase()) {
                "MONITOR" -> controller.selectTab(AppTab.MONITOR)
                "SENDER" -> controller.selectTab(AppTab.SENDER)
                "DIAGNOSTICS" -> controller.selectTab(AppTab.DIAGNOSTICS)
                "PROJECT", "EXPLORER" -> controller.selectTab(AppTab.PROJECT)
            }
        }
        val theme = intent.getStringExtra("theme")
        if (theme != null) {
            when (theme.uppercase()) {
                "LIGHT" -> controller.setThemeMode(ThemeMode.LIGHT)
                "DARK" -> controller.setThemeMode(ThemeMode.DARK)
                "SYSTEM" -> controller.setThemeMode(ThemeMode.SYSTEM)
            }
        }
        val nav = intent.getStringExtra("nav")
        if (nav != null) {
            when {
                nav == "groups" -> controller.selectExplorerNode(ExplorerSelection.AllGroupAddresses)
                nav.startsWith("group:") -> {
                    val key = nav.removePrefix("group:")
                    val proj = controller.uiState.value.project
                    val range = proj?.effectiveGroupRanges?.firstOrNull { it.id == key || it.prefixLabel == key || it.name.equals(key, ignoreCase = true) }
                        ?: proj?.effectiveGroupRanges?.flatMap { it.children }?.firstOrNull { it.id == key || it.prefixLabel == key || it.name.equals(key, ignoreCase = true) }
                    if (range != null) {
                        controller.selectExplorerNode(ExplorerSelection.GroupRange(range.id))
                    } else {
                        controller.selectExplorerNode(ExplorerSelection.GroupRange(key))
                    }
                }
                nav == "rooms" || nav == "buildings" -> controller.selectExplorerNode(ExplorerSelection.BuildingSpace("", "", "Root", emptyList()))
                nav == "expand_all" || nav == "tree:all" -> controller.selectExplorerNode(ExplorerSelection.BuildingSpace("__ALL__", "__ALL__", "Tree", emptyList()))
                nav.startsWith("building:") || nav.startsWith("space:") -> {
                    val target = if (nav.startsWith("building:")) nav.removePrefix("building:") else nav.removePrefix("space:")
                    controller.selectExplorerNode(ExplorerSelection.BuildingSpace(target, target, "Space", emptyList()))
                }
                nav == "topology" || nav == "root" -> controller.selectExplorerNode(ExplorerSelection.TopologyRoot)
                nav.startsWith("area:") -> {
                    val a = nav.removePrefix("area:").toIntOrNull() ?: 0
                    controller.selectExplorerNode(ExplorerSelection.Area(a))
                }
                nav.startsWith("line:") -> {
                    val parts = nav.removePrefix("line:").split(".")
                    if (parts.size == 2) {
                        controller.selectExplorerNode(ExplorerSelection.Line(parts[0].toInt(), parts[1].toInt()))
                    }
                }
                nav.startsWith("device:") -> {
                    val addr = IndividualAddress.parseOrNull(nav.removePrefix("device:"))
                    if (addr != null) controller.selectExplorerNode(ExplorerSelection.Device(addr))
                }
            }
        }
    }

    override fun onDestroy() {
        runCatching {
            if (multicastLock?.isHeld == true) multicastLock?.release()
        }
        if (::controller.isInitialized) {
            controller.close()
        }
        super.onDestroy()
    }

    private fun handleIncomingFileIntent(intent: Intent?) {
        if (intent == null) return
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: intent.data
            }
            else -> null
        }
        if (uri != null) {
            importAndOpenUri(uri)
        }
    }

    private fun importAndOpenUri(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                controller.setStatus("Importing project file...")
            }
            val result = runCatching {
                if (uri.scheme == "file" && uri.path != null) {
                    val local = File(uri.path!!)
                    if (local.isFile && local.canRead()) {
                        return@runCatching local
                    }
                }
                val rawName = resolveDisplayName(uri) ?: "project.knxproj"
                val cleanedName = rawName.replace(Regex("[^a-zA-Z0-9._\\- ]"), "_")
                val finalName = when {
                    cleanedName.endsWith(".knxkeys", ignoreCase = true) -> cleanedName
                    cleanedName.endsWith(".knxproj", ignoreCase = true) -> cleanedName
                    else -> "$cleanedName.knxproj"
                }
                val importsDir = File(filesDir, "imports").apply { mkdirs() }
                val dst = File(importsDir, finalName)
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(dst).use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IllegalStateException("Cannot read input stream for $uri")
                dst
            }
            result.onSuccess { file ->
                withContext(Dispatchers.Main) {
                    controller.openProjectOrKeyringFile(file)
                }
            }.onFailure { err ->
                withContext(Dispatchers.Main) {
                    controller.setStatus("Failed to open file: ${err.message}")
                }
            }
        }
    }

    private fun resolveDisplayName(uri: Uri): String? {
        if (uri.scheme == "content") {
            val cursor: Cursor? = runCatching {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            }.getOrNull()
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val name = it.getString(idx)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
        }
        val seg = uri.lastPathSegment?.substringAfterLast('/')
        return if (!seg.isNullOrBlank()) seg else null
    }

    private fun shareExportedFile(file: File, mimeType: String, chooserTitle: String) {
        runOnUiThread {
            runCatching {
                val contentUri = FileProvider.getUriForFile(
                    this,
                    "$packageName.fileprovider",
                    file,
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, chooserTitle))
            }
        }
    }
}
