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
import dev.knxfieldkit.app.UserPrefsStore
import dev.knxfieldkit.bus.PureKnxNetIpClient
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
        if (intent?.action == Intent.ACTION_VIEW || intent?.action == Intent.ACTION_SEND) {
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
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingFileIntent(intent)
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
