# KNX Field Kit — Technical Design Specification

## 1. `.knxproj` Password Unlocking Specification

ETS exports `.knxproj` files as standard ZIP containers holding metadata (`knx_master.xml`, manufacturer catalogs `M-xxxx/`) and one or more project directories/archives (`P-xxxx/` or `P-xxxx.zip`).

When a project password is set in ETS, `P-xxxx.zip` is encrypted inside the outer `.knxproj` archive:
- **User Prompt**: When an encrypted `P-xxxx.zip` entry is detected, the app prompts the user for the project password.
- **ETS4 / ETS5 Mode**: `P-xxxx.zip` uses the raw user password string directly (ZipCrypto or WinZip AES-256).
- **ETS6 Mode**: ETS6 derives a 32-byte key from the user's password before passing it as a Base64 string to AES-256 ZIP encryption:
  - **Input bytes**: `password.toByteArray(Charsets.UTF_16LE)` *(Note: Standard JVM `PBEKeySpec(char[])` uses UTF-8 and MUST NOT be used directly; implement RFC 2898 PBKDF2 using `javax.crypto.Mac.getInstance("HmacSHA256")` over raw `UTF-16LE` bytes).*
  - **Salt**: `"21.project.ets.knx.org".toByteArray(Charsets.US_ASCII)`
  - **PRF**: `HMAC-SHA256`
  - **Iterations**: `65536`
  - **Key Length**: `32` bytes (`256` bits)
  - **ZIP Password**: `Base64.getEncoder().encodeToString(derivedKey)`

### Optional `.knxkeys` Keyring Import
For installations using KNX IP Secure or KNX Data Secure, users can import their exported `.knxkeys` XML file:
- Key derivation uses `PBKDF2-HMAC-SHA256` with salt `"1.keyring.ets.knx.org"` (`65536` iterations, `16` bytes / `128` bits) and `SHA-256` IV truncation to decrypt backbone keys, tunneling passwords, and group keys.

---

## 2. Module Boundaries & Contracts

### `:core-model`
- `IndividualAddress(area: Int, line: Int, device: Int)` (`"1.1.15"` <-> `UShort`)
- `GroupAddress` supporting 3-level (`main/middle/sub`), 2-level (`main/sub`), and free (`1..65535`) formatting
- `DptCodec` registry for essential field types:
  - `1.xxx` (Boolean Switch/Trigger)
  - `5.001` (Scaling 0..100%), `5.010` (1-byte Unsigned 0..255)
  - `9.xxx` (2-byte KNX Float: Temperature °C, Lux, Humidity, Wind)
  - `13.xxx` / `14.xxx` (4-byte Signed Int / IEEE-754 Float)
  - `16.001` (14-byte ISO-8859-1 String)
  - `17.001` / `18.001` (Scene Number / Scene Control)
  - `20.102` (HVAC Mode)
- `KnxProject`, `TopologyArea`, `TopologyLine`, `ProjectDevice`, `GroupAddressInfo`
- `BusTelegram`, `DeviceDescriptorInfo`, `LineAuditEntry`, `GatewayEndpoint`

### `:core-knxproj`
- `KnxProjArchiveReader`: Detects unencrypted vs. encrypted `P-xxxx.zip`, applies ETS6 derived key first with fallback to ETS5 raw password via `net.lingala.zip4j:zip4j`.
- `KnxProjXmlParser`: Streaming XML (`javax.xml.stream.XMLInputFactory`) parser for `knx_master.xml` and `P-xxxx/0.xml` so large commercial projects (10,000+ group addresses) load in under 1 second with low memory footprint.

### `:core-bus`
- `interface KnxBusClient`:
  - `val connectionState: StateFlow<ConnectionState>`
  - `val incomingTelegrams: Flow<BusTelegram>`
  - `suspend fun discoverGateways(timeoutMs: Long = 3000): List<GatewayEndpoint>`
  - `suspend fun connect(config: BusConnectionConfig)`
  - `suspend fun disconnect()`
  - `suspend fun readGroupValue(address: GroupAddress, timeoutMs: Long = 2500): GroupReadResult`
  - `suspend fun writeGroupValue(address: GroupAddress, apduData: ByteArray)`
  - `suspend fun pingDevice(address: IndividualAddress, timeoutMs: Long = 1500): DevicePingResult`
  - `fun scanLine(area: Int, line: Int, addresses: List<Int>, rateLimitDelayMs: Long = 120): Flow<DevicePingResult>`
  - `suspend fun findProgrammingModeDevices(timeoutMs: Long = 2000): List<IndividualAddress>`
  - `suspend fun readDeviceDetails(address: IndividualAddress): DeviceDiagnosticDetails`
- Implementations:
  - `CalimeroKnxBusClient`: Wraps `io.calimero:calimero-core` + `calimero-device` on `Dispatchers.IO`.
  - `SimulatedKnxBusClient`: Deterministic virtual KNX TP1/IP bus for offline development, automated integration tests, and UI demos.

### `:app-desktop`
- Compose Multiplatform Desktop (`org.jetbrains.compose`) with Material 3 dark/light field theme.
- Four main workspaces: **Project Explorer**, **Live Bus Monitor**, **Telegram Sender**, and **Field Diagnostics (Ping / Line Audit / Prog-Mode Finder)**.
