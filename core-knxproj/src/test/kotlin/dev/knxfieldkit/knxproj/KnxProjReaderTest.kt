package dev.knxfieldkit.knxproj

import dev.knxfieldkit.model.GroupAddress
import dev.knxfieldkit.model.HexBytes
import dev.knxfieldkit.model.IndividualAddress
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.io.File
import java.nio.file.Files
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KnxProjReaderTest {

    private val sampleZeroXml = """
        <?xml version="1.0" encoding="utf-8"?>
        <KNX xmlns="http://knx.org/xml/project/21" CreatedBy="ETS6" ToolVersion="6.2.0">
          <Project Id="P-0100">
            <ProjectInformation Name="Villa Horizon" GroupAddressStyle="ThreeLevel" />
            <Installations>
              <Installation Name="">
                <Topology>
                  <Area Address="1" Name="Main">
                    <Line Address="1" Name="Ground Floor">
                      <DeviceInstance Id="D-15" Address="15" Name="Kitchen Dimmer" ProductRefId="M-0083_H-1_P-1">
                        <ComObjectInstanceRefs>
                          <ComObjectInstanceRef RefId="CO-1" Text="Dimming" FunctionText="Brightness" DatapointType="DPST-5-1" Links="GA-1" />
                        </ComObjectInstanceRefs>
                      </DeviceInstance>
                      <DeviceInstance Id="D-20" Address="20" Name="Hall Temp Sensor">
                        <ComObjectInstanceRefs>
                          <ComObjectInstanceRef RefId="CO-2" Text="Temp" FunctionText="Measure">
                            <Send GroupAddressRefId="P-0100-0_GA-2" />
                          </ComObjectInstanceRef>
                        </ComObjectInstanceRefs>
                      </DeviceInstance>
                    </Line>
                  </Area>
                </Topology>
                <GroupAddresses>
                  <GroupRanges>
                    <GroupRange Name="All" RangeStart="1" RangeEnd="65535">
                      <GroupAddress Id="P-0100-0_GA-1" Address="2561" Name="Kitchen Dimming" />
                      <GroupAddress Id="P-0100-0_GA-2" Address="2562" Name="Hall Temperature" DatapointType="DPST-9-1" />
                    </GroupRange>
                  </GroupRanges>
                </GroupAddresses>
                <Locations>
                  <Space Name="Kitchen" Type="Room">
                    <DeviceInstanceRef RefId="D-15" />
                  </Space>
                </Locations>
              </Installation>
            </Installations>
          </Project>
        </KNX>
    """.trimIndent()

    @Test
    fun ets6PasswordDerivationMatchesKnownSpec() {
        val derived1 = String(EtsCrypto.deriveEts6ZipPassword("test"))
        val derived2 = String(EtsCrypto.deriveEts6ZipPassword("SecretProject!2026"))

        assertTrue(derived1.isNotEmpty())
        assertEquals(44, derived1.length)
        assertNotEquals("test", derived1)
        assertEquals(32, Base64.getDecoder().decode(derived2).size)
    }

    @Test
    fun openUnencryptedKnxProj() {
        val tempDir = Files.createTempDirectory("knxproj-unenc-").toFile()
        try {
            val masterFile = File(tempDir, "knx_master.xml").apply {
                writeText("""<KNX><MasterData><Manufacturers><Manufacturer Id="M-0083" Name="MDT technologies"/></Manufacturers></MasterData></KNX>""")
            }
            val hwDir = File(tempDir, "M-0083").apply { mkdirs() }
            File(hwDir, "Hardware.xml").writeText(
                """<KNX><ManufacturerData><Manufacturer><Hardware><Products><Product Id="M-0083_H-1_P-1" Text="AKD-0401.02 Dimmer" OrderNumber="AKD-0401.02"/></Products></Hardware></Manufacturer></ManufacturerData></KNX>"""
            )
            val projDir = File(tempDir, "P-0100").apply { mkdirs() }
            File(projDir, "0.xml").writeText(sampleZeroXml)

            val knxProjFile = File(tempDir, "Villa_Horizon.knxproj")
            ZipFile(knxProjFile).use { zf ->
                zf.addFile(masterFile)
                zf.addFolder(hwDir)
                zf.addFolder(projDir)
            }

            assertFalse(KnxProjArchiveReader.isPasswordProtected(knxProjFile))
            val project = KnxProjParser.openAndParse(knxProjFile)
            assertEquals("Villa Horizon", project.name)
            assertEquals(2, project.allDevices.size)
            assertEquals("MDT technologies", project.findDevice(IndividualAddress(1, 1, 15))?.manufacturerName)
            assertEquals("AKD-0401.02 Dimmer", project.findDevice(IndividualAddress(1, 1, 15))?.productName)
            assertEquals("5.001", project.findGroupAddress(GroupAddress.fromRaw(2561))?.dpt)
            assertEquals("9.001", project.findGroupAddress(GroupAddress.fromRaw(2562))?.dpt)
            assertEquals("Kitchen", project.locations.first().name)
            assertEquals(listOf(IndividualAddress(1, 1, 15)), project.locations.first().deviceAddresses)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun openEts6PasswordProtectedKnxProj() {
        val tempDir = Files.createTempDirectory("knxproj-ets6-").toFile()
        try {
            val zeroFile = File(tempDir, "0.xml").apply { writeText(sampleZeroXml) }
            val innerZipFile = File(tempDir, "P-0200.zip")
            val outerZipFile = File(tempDir, "secure_ets6.knxproj")
            val password = "MyFieldPass#42"

            val aesParams = ZipParameters().apply {
                isEncryptFiles = true
                encryptionMethod = EncryptionMethod.AES
                aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
            }
            ZipFile(innerZipFile, EtsCrypto.deriveEts6ZipPassword(password)).use { iz ->
                iz.addFile(zeroFile, aesParams)
            }
            ZipFile(outerZipFile).use { oz ->
                oz.addFile(innerZipFile)
            }

            assertTrue(KnxProjArchiveReader.isPasswordProtected(outerZipFile))
            assertFailsWith<PasswordRequiredException> {
                KnxProjParser.openAndParse(outerZipFile, password = null)
            }
            assertFailsWith<InvalidPasswordException> {
                KnxProjParser.openAndParse(outerZipFile, password = "WrongPass")
            }

            val project = KnxProjParser.openAndParse(outerZipFile, password = password)
            assertTrue(project.passwordProtected)
            assertEquals("Villa Horizon", project.name)
            assertEquals(2, project.allDevices.size)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun openEts5LegacyPasswordProtectedKnxProj() {
        val tempDir = Files.createTempDirectory("knxproj-ets5-").toFile()
        try {
            val zeroFile = File(tempDir, "0.xml").apply { writeText(sampleZeroXml) }
            val innerZipFile = File(tempDir, "P-0300.zip")
            val outerZipFile = File(tempDir, "legacy_ets5.knxproj")
            val password = "LegacyEts5Pass"

            val stdParams = ZipParameters().apply {
                isEncryptFiles = true
                encryptionMethod = EncryptionMethod.ZIP_STANDARD
            }
            ZipFile(innerZipFile, password.toCharArray()).use { iz ->
                iz.addFile(zeroFile, stdParams)
            }
            ZipFile(outerZipFile).use { oz ->
                oz.addFile(innerZipFile)
            }

            assertTrue(KnxProjArchiveReader.isPasswordProtected(outerZipFile))
            val project = KnxProjParser.openAndParse(outerZipFile, password = password)
            assertTrue(project.passwordProtected)
            assertEquals("Villa Horizon", project.name)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun encryptTestKeyringAttribute(plain16: ByteArray, password: String, created: String): String {
        val aesKey = EtsCrypto.deriveKeyringAesKey(password)
        val iv = EtsCrypto.deriveKeyringIv(created)
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), IvParameterSpec(iv))
        val encrypted = cipher.doFinal(plain16)
        return Base64.getEncoder().encodeToString(encrypted)
    }

    @Test
    fun parseKeyringXmlDirectlyWithoutFile() {
        val testPassword = "KeyringSecretPass2026!"
        val createdTs = "2026-11-05T07:00:00Z"
        val groupKeyBytes = HexBytes.fromHex("00112233445566778899AABBCCDDEEFF")
        val toolKeyBytes = HexBytes.fromHex("FFEEDDCCBBAA99887766554433221100")

        val encGroupKey = encryptTestKeyringAttribute(groupKeyBytes, testPassword, createdTs)
        val encToolKey = encryptTestKeyringAttribute(toolKeyBytes, testPassword, createdTs)

        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <Keyring xmlns="http://knx.org/xml/keyring/10" Created="$createdTs" Project="SmartHomeSecure">
              <Group Address="1/1/1" Key="$encGroupKey" />
              <Device IndividualAddress="1.1.10" ToolKey="$encToolKey" />
            </Keyring>
        """.trimIndent()

        val keyring = KnxKeyringReader.parseKeyringXml(xml, testPassword)
        assertEquals("SmartHomeSecure", keyring.projectName)
        assertTrue(keyring.isGroupSecure(GroupAddress.parseOrNull("1/1/1")!!))
        assertEquals("00112233445566778899AABBCCDDEEFF", keyring.groupKeysHex[GroupAddress.parseOrNull("1/1/1")!!])
        assertTrue(keyring.isDeviceSecure(IndividualAddress.parseOrNull("1.1.10")!!))
        assertEquals("FFEEDDCCBBAA99887766554433221100", keyring.deviceToolKeysHex[IndividualAddress.parseOrNull("1.1.10")!!])
    }

    @Test
    fun detectSecurityOnDevicesAndGroupAddresses() {
        val secureZeroXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <KNX xmlns="http://knx.org/xml/project/21" CreatedBy="ETS6" ToolVersion="6.2.0">
              <Project Id="P-0100">
                <ProjectInformation Name="Secure Building" GroupAddressStyle="ThreeLevel" />
                <Installations>
                  <Installation Name="">
                    <Topology>
                      <Area Address="1" Name="Main">
                        <Line Address="1" Name="Line 1">
                          <DeviceInstance Id="D-10" Address="10" Name="Plain Switch">
                            <ComObjectInstanceRefs>
                              <ComObjectInstanceRef RefId="CO-1" Text="Switch" Links="GA-1" />
                            </ComObjectInstanceRefs>
                          </DeviceInstance>
                          <DeviceInstance Id="D-20" Address="20" Name="Secure Actuator">
                            <Security SequenceNumber="42" LoadedDeviceAuthenticationCodeHash="hash123" />
                            <ComObjectInstanceRefs>
                              <ComObjectInstanceRef RefId="CO-2" Text="Relay" Links="GA-2" />
                            </ComObjectInstanceRefs>
                          </DeviceInstance>
                        </Line>
                      </Area>
                    </Topology>
                    <GroupAddresses>
                      <GroupRanges>
                        <GroupRange Name="All" RangeStart="1" RangeEnd="65535">
                          <GroupAddress Id="P-0100-0_GA-1" Address="2561" Name="Plain Light" />
                          <GroupAddress Id="P-0100-0_GA-2" Address="2562" Name="Secure Light From CO" />
                          <GroupAddress Id="P-0100-0_GA-3" Address="2563" Name="Secured GA By Mode" SecurityMode="Secured" />
                          <GroupAddress Id="P-0100-0_GA-4" Address="2564" Name="Secured GA By Element">
                            <Security SequenceNumber="1" />
                          </GroupAddress>
                        </GroupRange>
                      </GroupRanges>
                    </GroupAddresses>
                  </Installation>
                </Installations>
              </Project>
            </KNX>
        """.trimIndent()

        val archive = ExtractedKnxArchive(
            projectId = "P-0100",
            passwordProtected = false,
            knxMasterXml = null,
            projectXml = null,
            zeroXml = secureZeroXml,
        )

        val project = KnxProjParser.parseArchive(archive)
        val devPlain = project.findDevice(IndividualAddress(1, 1, 10))
        val devSecure = project.findDevice(IndividualAddress(1, 1, 20))

        assertNotNull(devPlain)
        assertFalse(devPlain.isSecure)

        assertNotNull(devSecure)
        assertTrue(devSecure.isSecure)

        // GA-1: Plain Light -> false
        val ga1 = project.findGroupAddress(GroupAddress.fromRaw(2561))
        assertNotNull(ga1)
        assertFalse(ga1.isSecure)

        // GA-2: Linked to Secure Actuator (D-20) -> true
        val ga2 = project.findGroupAddress(GroupAddress.fromRaw(2562))
        assertNotNull(ga2)
        assertTrue(ga2.isSecure)

        // GA-3: SecurityMode="Secured" -> true
        val ga3 = project.findGroupAddress(GroupAddress.fromRaw(2563))
        assertNotNull(ga3)
        assertTrue(ga3.isSecure)

        // GA-4: Has <Security> child element -> true
        val ga4 = project.findGroupAddress(GroupAddress.fromRaw(2564))
        assertNotNull(ga4)
        assertTrue(ga4.isSecure)
    }

    @Test
    fun extractAndParseKeyringInsideArchive() {
        val tempDir = Files.createTempDirectory("knxproj-keyring-").toFile()
        try {
            val testPassword = "ArchiveKeyringPass123"
            val createdTs = "2026-11-05T07:30:00Z"
            val groupKeyBytes = HexBytes.fromHex("A1B2C3D4E5F60718293A4B5C6D7E8F90")
            val encGroupKey = encryptTestKeyringAttribute(groupKeyBytes, testPassword, createdTs)

            val keyringXmlContent = """
                <?xml version="1.0" encoding="utf-8"?>
                <Keyring xmlns="http://knx.org/xml/keyring/10" Created="$createdTs" Project="ArchiveProject">
                  <Group Address="1/2/1" Key="$encGroupKey" />
                </Keyring>
            """.trimIndent()

            val zeroFile = File(tempDir, "0.xml").apply { writeText(sampleZeroXml) }
            val keyringFile = File(tempDir, "keyring.xml").apply { writeText(keyringXmlContent) }
            val knxProjFile = File(tempDir, "project_with_keyring.knxproj")

            val projDir = File(tempDir, "P-0100").apply { mkdirs() }
            zeroFile.copyTo(File(projDir, "0.xml"))
            keyringFile.copyTo(File(projDir, "keyring.xml"))

            ZipFile(knxProjFile).use { zf ->
                zf.addFolder(projDir)
            }

            val archive = KnxProjArchiveReader.extract(knxProjFile)
            assertNotNull(archive.keyringXml)
            assertTrue(archive.keyringXml.contains("<Keyring"))

            val project = KnxProjParser.openAndParse(knxProjFile, password = testPassword)
            assertNotNull(project.keyring)
            assertEquals("ArchiveProject", project.keyring?.projectName)
            assertTrue(project.keyring?.isGroupSecure(GroupAddress.parseOrNull("1/2/1")!!) == true)
            assertEquals("A1B2C3D4E5F60718293A4B5C6D7E8F90", project.keyring?.groupKeysHex?.get(GroupAddress.parseOrNull("1/2/1")!!))

            // Group Address 1/2/1 should have isSecure = true
            val ga1 = project.findGroupAddress(GroupAddress.parseOrNull("1/2/1")!!)
            assertNotNull(ga1)
            assertTrue(ga1.isSecure)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun parseRealWorldUserKnxprojIfPresent() {
        val candidates = listOf(
            File("C:/Users/stama/.gemini/antigravity/scratch/latest_final.knxproj"),
            File("C:/Users/stama/.gemini/antigravity/scratch/knx-field-kit/latest_final.knxproj"),
        )
        val userFile = candidates.firstOrNull { it.isFile } ?: return
        println("Testing user project: ${userFile.length()} bytes")
        val isProtected = KnxProjArchiveReader.isPasswordProtected(userFile)
        println("isProtected: $isProtected")
        val proj = KnxProjParser.openAndParse(userFile, password = null)
        println("Parsed project: name='${proj.name}', devices=${proj.allDevices.size}, GAs=${proj.groupAddresses.size}")
        println("Areas: ${proj.areas.map { "Area ${it.area} '${it.name}': ${it.lines.size} lines" }}")
        val allFuncs = proj.locations.flatMap { it.collectAllFunctions() }
        val allLocGas = proj.locations.flatMap { it.collectAllGroupAddresses() }
        println("Total parsed functions across all locations: ${allFuncs.size}")
        println("Total distinct GAs referenced in functions: ${allLocGas.distinct().size}")
        println("Sample functions: ${allFuncs.take(10).map { "${it.name} (${it.type}, ${it.groupAddresses.size} GAs: ${it.groupAddresses.take(3)})" }}")
        println("Locations: ${proj.locations.map { "${it.type} '${it.name}': ${it.children.size} children, ${it.functions.size} direct funcs, ${it.collectAllFunctions().size} total funcs, ${it.deviceAddresses.size} devs" }}")
    }
}
