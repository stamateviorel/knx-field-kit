package dev.knxfieldkit.knxproj

import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import java.io.File
import java.nio.file.Files
import dev.knxfieldkit.model.*

sealed class KnxProjOpenException(message: String, cause: Throwable? = null) : Exception(message, cause)
class PasswordRequiredException(val projectId: String) : KnxProjOpenException("Project '$projectId' is password-protected.")
class InvalidPasswordException(val projectId: String, cause: Throwable? = null) : KnxProjOpenException("Invalid password for project '$projectId'.", cause)
class MalformedProjectException(message: String, cause: Throwable? = null) : KnxProjOpenException(message, cause)

data class ExtractedKnxArchive(
    val projectId: String,
    val passwordProtected: Boolean,
    val knxMasterXml: String?,
    val projectXml: String?,
    val zeroXml: String,
    val hardwareXmlFiles: List<String> = emptyList()
)

object KnxProjArchiveReader {
    private val INNER_PROJECT_ZIP_REGEX = Regex("""^(P-[0-9A-Za-z_-]+)\.zip$""", RegexOption.IGNORE_CASE)

    private fun isRelevantEntry(fileName: String): Boolean {
        val normalized = fileName.replace('\\', '/')
        val simpleName = File(normalized).name.lowercase()
        return simpleName == "knx_master.xml" ||
               simpleName == "hardware.xml" ||
               simpleName == "0.xml" ||
               simpleName == "project.xml" ||
               INNER_PROJECT_ZIP_REGEX.matches(simpleName)
    }

    private fun extractRelevantFiles(zip: ZipFile, targetDir: File) {
        for (header in zip.fileHeaders) {
            if (header.isDirectory) continue
            if (isRelevantEntry(header.fileName)) {
                zip.extractFile(header, targetDir.absolutePath)
            }
        }
    }

    private fun File.readTextNoBom(): String = readText(Charsets.UTF_8).trimStart('\uFEFF')

    fun isPasswordProtected(knxProjFile: File): Boolean {
        if (!knxProjFile.isFile) throw MalformedProjectException("File not found: ${knxProjFile.absolutePath}")
        val tmp = Files.createTempDirectory("knxproj-probe-").toFile()
        try {
            val zip = ZipFile(knxProjFile)
            if (zip.isEncrypted) return true
            for (header in zip.fileHeaders) {
                val simpleName = File(header.fileName).name
                if (INNER_PROJECT_ZIP_REGEX.matches(simpleName)) {
                    zip.extractFile(header, tmp.absolutePath)
                    return ZipFile(File(tmp, header.fileName)).isEncrypted
                }
            }
            return false
        } finally {
            tmp.deleteRecursively()
        }
    }

    fun extract(knxProjFile: File, password: String? = null): ExtractedKnxArchive {
        if (!knxProjFile.isFile) throw MalformedProjectException("File not found: ${knxProjFile.absolutePath}")
        val tmp = Files.createTempDirectory("knxproj-extract-").toFile()
        try {
            val outerZip = ZipFile(knxProjFile)
            if (outerZip.isEncrypted) {
                if (password == null) throw PasswordRequiredException(knxProjFile.nameWithoutExtension)
                try {
                    val authZip = ZipFile(knxProjFile, password.toCharArray())
                    extractRelevantFiles(authZip, tmp)
                } catch (e: ZipException) {
                    throw InvalidPasswordException(knxProjFile.nameWithoutExtension, e)
                }
            } else {
                extractRelevantFiles(outerZip, tmp)
            }
            val knxMasterXml = File(tmp, "knx_master.xml").takeIf { it.isFile }?.readTextNoBom()
            val hardwareXmlFiles = tmp.walkTopDown().filter { it.isFile && it.name.equals("Hardware.xml", ignoreCase = true) }.map { it.readTextNoBom() }.toList()
            val innerZipFile = tmp.listFiles()?.firstOrNull { it.isFile && INNER_PROJECT_ZIP_REGEX.matches(it.name) }
            if (innerZipFile != null) {
                val projectId = innerZipFile.nameWithoutExtension
                val projDir = File(tmp, projectId).apply { mkdirs() }
                val probeZip = ZipFile(innerZipFile)
                val encrypted = probeZip.isEncrypted
                if (encrypted) {
                    if (password == null) throw PasswordRequiredException(projectId)
                    val ets6Chars = EtsCrypto.deriveEts6ZipPassword(password)
                    val extractedEts6 = try {
                        val zEts6 = ZipFile(innerZipFile, ets6Chars)
                        extractRelevantFiles(zEts6, projDir)
                        true
                    } catch (_: ZipException) {
                        projDir.deleteRecursively()
                        projDir.mkdirs()
                        false
                    }
                    if (!extractedEts6) {
                        try {
                            val zLegacy = ZipFile(innerZipFile, password.toCharArray())
                            extractRelevantFiles(zLegacy, projDir)
                        } catch (e: ZipException) {
                            throw InvalidPasswordException(projectId, e)
                        }
                    }
                } else {
                    extractRelevantFiles(probeZip, projDir)
                }
                val zeroXml = projDir.walkTopDown().firstOrNull { it.isFile && it.name == "0.xml" }?.readTextNoBom()
                    ?: throw MalformedProjectException("Missing 0.xml in $projectId")
                val projectXml = projDir.walkTopDown().firstOrNull { it.isFile && it.name.equals("project.xml", ignoreCase = true) }?.readTextNoBom()
                return ExtractedKnxArchive(projectId, outerZip.isEncrypted || encrypted, knxMasterXml, projectXml, zeroXml, hardwareXmlFiles)
            }
            val projDir = tmp.listFiles()?.firstOrNull { it.isDirectory && it.name.startsWith("P-", ignoreCase = true) }
            if (projDir != null) {
                val zeroXml = projDir.walkTopDown().firstOrNull { it.isFile && it.name == "0.xml" }?.readTextNoBom()
                    ?: throw MalformedProjectException("Missing 0.xml in ${knxProjFile.name}")
                val projectXml = projDir.walkTopDown().firstOrNull { it.isFile && it.name.equals("project.xml", ignoreCase = true) }?.readTextNoBom()
                return ExtractedKnxArchive(projDir.name, outerZip.isEncrypted, knxMasterXml, projectXml, zeroXml, hardwareXmlFiles)
            }
            throw MalformedProjectException("No P-xxxx project folder or archive found in ${knxProjFile.name}")
        } finally {
            tmp.deleteRecursively()
        }
    }
}
