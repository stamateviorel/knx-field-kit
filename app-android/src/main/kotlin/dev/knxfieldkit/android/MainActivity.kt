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
import dev.knxfieldkit.app.AppController
import dev.knxfieldkit.app.UserPrefsStore
import dev.knxfieldkit.bus.PureKnxNetIpClient
import dev.knxfieldkit.mobile.MobileFieldApp
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
        handleIncomingFileIntent(intent)

        setContent {
            val openDocumentLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument(),
            ) { uri: Uri? ->
                if (uri != null) {
                    val copied = copyContentUriToAppStorage(uri)
                    if (copied != null) {
                        controller.openProjectOrKeyringFile(copied)
                    }
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
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        val copied = copyContentUriToAppStorage(uri)
        if (copied != null) {
            controller.openProjectOrKeyringFile(copied)
        }
    }

    private fun copyContentUriToAppStorage(uri: Uri): File? = runCatching {
        val displayName = resolveDisplayName(uri)
            ?.replace(Regex("[^a-zA-Z0-9._\\- ]"), "_")
            ?.takeIf { it.isNotBlank() }
            ?: "imported_project.knxproj"
        val importsDir = File(filesDir, "imports").apply { mkdirs() }
        val dst = File(importsDir, displayName)
        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(dst).use { output ->
                input.copyTo(output)
            }
        } ?: return null
        dst
    }.getOrNull()

    private fun resolveDisplayName(uri: Uri): String? {
        if (uri.scheme == "content") {
            val cursor: Cursor? = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return it.getString(idx)
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
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
