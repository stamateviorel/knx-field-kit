# KNX Field Kit

Cross-platform Kotlin & Compose Multiplatform companion tool for **KNX field diagnostics, live bus monitoring, interactive telegram testing, and `.knxproj` project inspection** (including password-protected ETS4/ETS5/ETS6 exports and `.knxkeys` keyrings).

## Core Capabilities (Planned)

1. **`.knxproj` & `.knxkeys` Inspector**
   - Unlocks standard and password-protected `.knxproj` archives (ETS4/ETS5 ZIP encryption + ETS6 `UTF-16LE` PBKDF2-HMAC-SHA256 key derivation).
   - Parses topology (Areas, Lines, Devices), building locations, Group Addresses, linked ComObjects, and Datapoint Types (DPTs).
   - Optional `.knxkeys` keyring import for KNX IP Secure & KNX Data Secure decoding.

2. **Gateway Discovery & Bus Connectivity**
   - Auto-discovers KNXnet/IP Interfaces and Routers (`SearchRequest` / `SearchRequestExtended`).
   - Connects over KNXnet/IP Tunneling, KNXnet/IP Routing, USB HID/FT1.2 (via Calimero), or a built-in **Virtual KNX Bus Simulator** for offline testing.

3. **Live Traffic Monitor & Interactive Telegram Sender**
   - Real-time `L_Data` / Busmon stream enriched with device names, group address names, and decoded DPT values from the loaded `.knxproj`.
   - Flags unmapped ("ghost") group addresses, repeated frames, and missing acknowledgements.
   - Typed `GroupValue_Read` / `GroupValue_Write` controls (On/Off, Dimming %, 2-byte Float °C, Scene, Raw Hex) and rate-limited batch polling.

4. **Field Test & Commissioning Diagnostics Suite**
   - **Individual Address Ping (`DeviceDescriptor_Read`)**: Verify device reachability, response latency, and BCU mask version.
   - **Project-vs-Bus Line Audit**: Automatically compare every expected device on a `.knxproj` line against the live KNX bus (`Online`, `Missing`, `Mask Mismatch`).
   - **Subnet Scanner**: Scan `Area.Line.1..255` without a project file to discover active devices on unknown installations.
   - **Programming Mode Finder (`IndividualAddress_Read`)**: Identify which device(s) currently have their physical Programming LED active.
   - **Device Diagnostics**: Read Serial Number, Manufacturer ID, Application load/run state, and error flags.

## Repository Structure

```text
knx-field-kit/
├── core-model/     # Pure Kotlin domain models, DPT codecs, Telegram & Audit types
├── core-knxproj/   # .knxproj & .knxkeys decryption (Zip4j + PBKDF2) and XML parsers
├── core-bus/       # KnxBusClient interface, Calimero adapter, & Virtual Bus Simulator
├── app-desktop/    # Compose Multiplatform Desktop UI (Material 3)
├── scripts/        # Local AI (Ollama / Qwen2.5-Coder) helper scripts
└── docs/           # Architecture & protocol design specifications
```

## Hybrid Cloud + Local AI Workflow

This repository uses an **Architect + Local Worker** setup:
- **Cloud Orchestrator (Antigravity)**: Architecture, interfaces, protocol/crypto edge cases, Gradle build orchestration, test verification, and code review.
- **Local AI (`qwen2.5-coder:7b` on Intel Vulkan iGPU via Headless Ollama)**: Implements domain models, XML parsers, DPT encoders/decoders, simulator logic, Compose UI components, and JUnit 5 test suites via `cc -p` or `scripts/local-codegen.ps1`.
