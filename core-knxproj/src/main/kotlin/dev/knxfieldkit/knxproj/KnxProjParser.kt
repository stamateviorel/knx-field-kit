package dev.knxfieldkit.knxproj

import dev.knxfieldkit.model.*
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import java.io.File
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

private fun parseXmlSafe(xml: String): Document {
    val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    }
    return factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
}

private fun NodeList.elements(): List<Element> = buildList {
    for (i in 0 until length) {
        val n = item(i)
        if (n is Element) add(n)
    }
}

private fun Element.directChildElements(tagName: String? = null): List<Element> = buildList {
    var child: Node? = firstChild
    while (child != null) {
        if (child is Element && (tagName == null || child.tagName == tagName)) {
            add(child)
        }
        child = child.nextSibling
    }
}

object KnxProjParser {
    fun openAndParse(knxProjFile: File, password: String? = null): KnxProject {
        val archive = KnxProjArchiveReader.extract(knxProjFile, password)
        return parseArchive(archive)
    }

    fun parseArchive(archive: ExtractedKnxArchive): KnxProject {
        val manufacturerNames = parseManufacturerNames(archive.knxMasterXml)
        val productCatalog = parseProductCatalog(archive.hardwareXmlFiles)

        val zeroDoc = parseXmlSafe(archive.zeroXml)
        val projDoc = archive.projectXml?.let(::parseXmlSafe)
        val infoDoc = if (projDoc != null && projDoc.getElementsByTagName("ProjectInformation").length > 0) projDoc else zeroDoc

        val projectId = infoDoc.getElementsByTagName("Project").elements().firstOrNull()?.getAttribute("Id")
            ?.takeIf { it.isNotBlank() } ?: archive.projectId

        val rootEl = zeroDoc.documentElement
        val createdBy = rootEl.getAttribute("CreatedBy")
        val toolVer = rootEl.getAttribute("ToolVersion")
        val etsVersion = listOf(createdBy, toolVer).filter { it.isNotBlank() }.joinToString(" ")
            .ifBlank { rootEl.getAttribute("xmlns").substringAfterLast('/', "") }

        val projInfoEl = infoDoc.getElementsByTagName("ProjectInformation").elements().firstOrNull()
        val projectName = projInfoEl?.getAttribute("Name")?.takeIf { it.isNotBlank() } ?: projectId
        val groupAddressStyle = when (projInfoEl?.getAttribute("GroupAddressStyle")) {
            "TwoLevel" -> GroupAddressStyle.TWO_LEVEL
            "Free" -> GroupAddressStyle.FREE
            else -> GroupAddressStyle.THREE_LEVEL
        }

        // Map GroupAddress XML elements (both full Id "P-0100-0_GA-1" and short suffix "GA-1")
        val gaByRaw = linkedMapOf<GroupAddress, GroupAddressInfo>()
        val gaKeyToAddress = mutableMapOf<String, GroupAddress>()

        for (gaEl in zeroDoc.getElementsByTagName("GroupAddress").elements()) {
            val rawAddr = gaEl.getAttribute("Address").trim().toIntOrNull() ?: continue
            if (rawAddr <= 0 || rawAddr > 0xFFFF) continue
            val ga = GroupAddress(rawAddr, groupAddressStyle)
            val id = gaEl.getAttribute("Id").trim()
            val name = gaEl.getAttribute("Name")
            val desc = gaEl.getAttribute("Description")
            val dpt = DptCodec.normalizeDptId(gaEl.getAttribute("DatapointType"))
            gaByRaw[ga] = GroupAddressInfo(
                address = ga,
                name = name,
                description = desc,
                dpt = dpt,
                linkedObjects = emptyList(),
            )
            if (id.isNotEmpty()) {
                gaKeyToAddress[id] = ga
                val shortKey = id.substringAfter('_', "")
                if (shortKey.isNotEmpty()) {
                    gaKeyToAddress[shortKey] = ga
                }
            }
        }

        val deviceIdToAddress = mutableMapOf<String, IndividualAddress>()
        val areas = mutableListOf<TopologyArea>()

        for (areaEl in zeroDoc.getElementsByTagName("Area").elements()) {
            val areaNum = (areaEl.getAttribute("Address").ifBlank { areaEl.getAttribute("Id") }).toIntOrNull() ?: continue
            if (areaNum !in 0..15) continue
            val areaName = areaEl.getAttribute("Name").ifBlank { "Area $areaNum" }
            val lines = mutableListOf<TopologyLine>()

            for (lineEl in areaEl.getElementsByTagName("Line").elements()) {
                val lineNum = (lineEl.getAttribute("Address").ifBlank { lineEl.getAttribute("Id") }).toIntOrNull() ?: continue
                if (lineNum !in 0..15) continue
                val lineName = lineEl.getAttribute("Name").ifBlank { "Line $areaNum.$lineNum" }
                val medium = lineEl.getAttribute("MediumTypeRefId").ifBlank { "TP" }
                val devices = mutableListOf<ProjectDevice>()

                for (devEl in lineEl.getElementsByTagName("DeviceInstance").elements()) {
                    val devAddrNum = devEl.getAttribute("Address").trim().toIntOrNull() ?: continue
                    if (devAddrNum !in 0..255) continue
                    val ia = IndividualAddress(areaNum, lineNum, devAddrNum)
                    val devId = devEl.getAttribute("Id").trim()
                    if (devId.isNotEmpty()) {
                        deviceIdToAddress[devId] = ia
                    }

                    val prodRef = devEl.getAttribute("ProductRefId").trim()
                    val mfrId = prodRef.substringBefore('_', "")
                    val mfrName = if (mfrId.isNotEmpty()) (manufacturerNames[mfrId] ?: mfrId) else ""
                    val (prodName, orderNum) = productCatalog[prodRef] ?: ("" to "")
                    val devLinkedGas = linkedSetOf<GroupAddress>()

                    // ETS5.7+ / ETS6 ComObjectInstanceRef Links + ETS4/5 Send/Receive connectors
                    for (coEl in devEl.getElementsByTagName("ComObjectInstanceRef").elements()) {
                        val refId = coEl.getAttribute("RefId")
                        val coText = coEl.getAttribute("Text")
                        val fnText = coEl.getAttribute("FunctionText")
                        val coDpt = DptCodec.normalizeDptId(coEl.getAttribute("DatapointType"))

                        val refKeys = mutableListOf<String>()
                        val linksAttr = coEl.getAttribute("Links").trim()
                        if (linksAttr.isNotEmpty()) {
                            refKeys += linksAttr.split(Regex("\\s+"))
                        }
                        for (s in coEl.getElementsByTagName("Send").elements()) {
                            s.getAttribute("GroupAddressRefId").takeIf { it.isNotBlank() }?.let(refKeys::add)
                        }
                        for (r in coEl.getElementsByTagName("Receive").elements()) {
                            r.getAttribute("GroupAddressRefId").takeIf { it.isNotBlank() }?.let(refKeys::add)
                        }

                        val linkObj = ComObjectLink(
                            id = refId,
                            name = coText,
                            functionText = fnText,
                            dpt = coDpt,
                            deviceAddress = ia,
                        )
                        for (rk in refKeys) {
                            val ga = gaKeyToAddress[rk] ?: gaKeyToAddress[rk.substringAfter('_', rk)] ?: continue
                            devLinkedGas += ga
                            val existing = gaByRaw[ga] ?: continue
                            gaByRaw[ga] = existing.copy(
                                dpt = existing.dpt ?: coDpt,
                                linkedObjects = existing.linkedObjects + linkObj,
                            )
                        }
                    }

                    // Direct Send / Receive elements directly under DeviceInstance (if not wrapped in ComObjectInstanceRef)
                    val directRefs = (devEl.directChildElements("Send") + devEl.directChildElements("Receive"))
                    for (dr in directRefs) {
                        val rk = dr.getAttribute("GroupAddressRefId").trim()
                        val ga = gaKeyToAddress[rk] ?: gaKeyToAddress[rk.substringAfter('_', rk)] ?: continue
                        devLinkedGas += ga
                    }

                    val devName = devEl.getAttribute("Name").ifBlank { prodName.ifBlank { "Device $ia" } }
                    devices += ProjectDevice(
                        address = ia,
                        name = devName,
                        description = devEl.getAttribute("Description"),
                        manufacturerName = mfrName,
                        productName = prodName,
                        orderNumber = orderNum,
                        hardwareSerial = devEl.getAttribute("SerialNumber").takeIf { it.isNotBlank() },
                        maskVersion = devEl.getAttribute("MaskVersion").takeIf { it.isNotBlank() },
                        groupAddresses = devLinkedGas.toList(),
                    )
                }

                lines += TopologyLine(
                    area = areaNum,
                    line = lineNum,
                    name = lineName,
                    mediumType = medium,
                    devices = devices.sortedBy { it.address },
                )
            }
            areas += TopologyArea(area = areaNum, name = areaName, lines = lines.sortedBy { it.line })
        }

        val locations = mutableListOf<BuildingLocation>()
        for (locRoot in zeroDoc.getElementsByTagName("Locations").elements()) {
            for (spaceEl in locRoot.directChildElements("Space")) {
                locations += parseSpaceRecursive(spaceEl, deviceIdToAddress)
            }
        }

        return KnxProject(
            projectId = projectId,
            name = projectName,
            etsVersion = etsVersion,
            groupAddressStyle = groupAddressStyle,
            passwordProtected = archive.passwordProtected,
            areas = areas.sortedBy { it.area },
            groupAddresses = gaByRaw,
            locations = locations,
        )
    }

    private fun parseSpaceRecursive(
        spaceEl: Element,
        deviceIdToAddress: Map<String, IndividualAddress>,
    ): BuildingLocation {
        val devAddrs = spaceEl.directChildElements("DeviceInstanceRef")
            .mapNotNull { deviceIdToAddress[it.getAttribute("RefId").trim()] }
        val children = spaceEl.directChildElements("Space")
            .map { parseSpaceRecursive(it, deviceIdToAddress) }
        return BuildingLocation(
            name = spaceEl.getAttribute("Name"),
            type = spaceEl.getAttribute("Type").ifBlank { "Room" },
            deviceAddresses = devAddrs,
            children = children,
        )
    }

    private fun parseManufacturerNames(knxMasterXml: String?): Map<String, String> {
        if (knxMasterXml.isNullOrBlank()) return emptyMap()
        val doc = runCatching { parseXmlSafe(knxMasterXml) }.getOrNull() ?: return emptyMap()
        return buildMap {
            for (el in doc.getElementsByTagName("Manufacturer").elements()) {
                val id = el.getAttribute("Id")
                val name = el.getAttribute("Name")
                if (id.isNotBlank() && name.isNotBlank()) put(id, name)
            }
        }
    }

    private fun parseProductCatalog(hardwareXmlFiles: List<String>): Map<String, Pair<String, String>> = buildMap {
        for (xml in hardwareXmlFiles) {
            val doc = runCatching { parseXmlSafe(xml) }.getOrNull() ?: continue
            for (el in doc.getElementsByTagName("Product").elements()) {
                val id = el.getAttribute("Id")
                if (id.isNotBlank()) {
                    put(id, el.getAttribute("Text") to el.getAttribute("OrderNumber"))
                }
            }
        }
    }
}

object KnxKeyringReader {
    fun parseKeyring(knxKeysFile: File, password: String): KnxKeyring {
        val doc = parseXmlSafe(knxKeysFile.readText(Charsets.UTF_8))
        val root = doc.getElementsByTagName("Keyring").elements().firstOrNull() ?: doc.documentElement
        val created = root.getAttribute("Created")
        val aesKey = EtsCrypto.deriveKeyringAesKey(password)
        val iv = EtsCrypto.deriveKeyringIv(created)

        val backboneHex = doc.getElementsByTagName("Backbone").elements().firstOrNull()
            ?.getAttribute("Key")
            ?.takeIf { it.isNotBlank() }
            ?.let { HexBytes.toHex(EtsCrypto.decryptKeyringAttribute(it, aesKey, iv), "") }

        val interfaces = doc.getElementsByTagName("Interface").elements().mapNotNull { el ->
            val ia = IndividualAddress.parseOrNull(el.getAttribute("IndividualAddress")) ?: return@mapNotNull null
            val userEl = el.getElementsByTagName("User").elements().firstOrNull()
            KeyringInterface(
                individualAddress = ia,
                host = el.getAttribute("Host").takeIf { it.isNotBlank() },
                userId = userEl?.getAttribute("Id")?.toIntOrNull(),
                userPasswordHashOrPlain = userEl?.getAttribute("Password")?.takeIf { it.isNotBlank() }
                    ?.let { EtsCrypto.decryptKeyringPasswordString(it, aesKey, iv) },
                authCodePlain = userEl?.getAttribute("Authentication")?.takeIf { it.isNotBlank() }
                    ?.let { EtsCrypto.decryptKeyringPasswordString(it, aesKey, iv) },
            )
        }

        val groupKeysHex = buildMap {
            for (el in doc.getElementsByTagName("Group").elements()) {
                val ga = GroupAddress.parseOrNull(el.getAttribute("Address")) ?: continue
                val keyB64 = el.getAttribute("Key").takeIf { it.isNotBlank() } ?: continue
                put(ga, HexBytes.toHex(EtsCrypto.decryptKeyringAttribute(keyB64, aesKey, iv), ""))
            }
        }

        val deviceToolKeysHex = buildMap {
            for (el in doc.getElementsByTagName("Device").elements()) {
                val ia = IndividualAddress.parseOrNull(el.getAttribute("IndividualAddress")) ?: continue
                val tkB64 = el.getAttribute("ToolKey").takeIf { it.isNotBlank() } ?: continue
                put(ia, HexBytes.toHex(EtsCrypto.decryptKeyringAttribute(tkB64, aesKey, iv), ""))
            }
        }

        return KnxKeyring(
            projectName = root.getAttribute("Project").ifBlank { knxKeysFile.nameWithoutExtension },
            createdBy = root.getAttribute("CreatedBy"),
            backboneKeyHex = backboneHex,
            interfaces = interfaces,
            groupKeysHex = groupKeysHex,
            deviceToolKeysHex = deviceToolKeysHex,
        )
    }
}
