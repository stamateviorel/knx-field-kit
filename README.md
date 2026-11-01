# KNX Field Kit

Cross-platform Kotlin & Compose Multiplatform Desktop companion tool for **KNX field diagnostics, live bus monitoring, interactive telegram testing, and `.knxproj` project inspection** (including password-protected ETS4/ETS5/ETS6 exports and `.knxkeys` keyrings).

## Quick Start

### Launch the Desktop App
Double-click `run-app.cmd` or run from PowerShell / Command Prompt:

```powershell
.\run-app.cmd
```

*(Alternatively, with JDK 21 in `JAVA_HOME`: `.\gradlew.bat :app-desktop:run`)*

### Run All Unit & Integration Tests
```powershell
.\gradlew.bat test check
```

## Implemented Capabilities

1. **`.knxproj` & `.knxkeys` Inspector (`:core-knxproj` + Project Tab)**
   - Unlocks both unencrypted and **password-protected `.knxproj` archives** using the user-supplied project password (both ETS4/ETS5 ZIP encryption and ETS6 `UTF-16LE` PBKDF2-HMAC-SHA256 AES-256 key derivation).
   - Unlocks `.knxkeys` KNX Secure keyrings (`UTF-8` PBKDF2-HMAC-SHA256 + AES-128-CBC) to inspect Group Keys, Backbone Key, and Secure Tunneling Interface slots.
   - Parses topology (`Areas` -> `Lines` -> `Devices`), building locations, Group Addresses (`ThreeLevel`, `TwoLevel`, `Free`), linked ComObjects, manufacturer catalog names, and Datapoint Types (DPTs).
   - Includes a one-click **Generate & Unlock ETS6 Encrypted Sample** action for instant verification and Markdown field report export.

2. **Gateway Discovery & Bus Connectivity (`:core-bus` + Gateways Dialog)**
   - Auto-discovers KNXnet/IP Interfaces and Routers on LAN (`SearchRequest`).
   - Connects over **KNXnet/IP Tunneling** (UDP + NAT mode + KNX IP Secure user/device auth), **KNXnet/IP Routing** (`224.0.23.12:3671`), or the built-in **Virtual KNX Bus Simulator** for offline testing and training.

3. **Live Traffic Monitor & Interactive Telegram Sender (`Monitor` & `Send & Poll` Tabs)**
   - Real-time telegram stream enriched with `.knxproj` device names, group address names, and decoded DPT values (`DPT 1`, `5`, `9`, `13`, `14`, `16`, `17`, `20`, and raw hex).
   - Flags unmapped (**GHOST**) group addresses observed on the bus that are absent from the loaded `.knxproj` file, with search filtering and CSV export.
   - Interactive `GroupValue_Read` (with round-trip latency measurement), rate-limited batch polling (`~8 telegrams/s` safe for TP1), and typed `GroupValue_Write` controls (Switch On/Off, Dimming %, 2-byte Float `°C` / lux, Scene `1..64`, HVAC Mode, and Raw Hex).

4. **Field Test & Commissioning Diagnostics Suite (`Field Tests` Tab)**
   - **Individual Address Ping (`DeviceDescriptor_Read` DD0)**: Verify physical device reachability, response latency, and BCU mask version (`07B0`, `0705`, `091A`, etc.).
   - **Project-vs-Bus Line Audit**: Automatically compare every expected device on a `.knxproj` line against the live KNX bus (`ONLINE`, `UNREACHABLE`, `MASK_MISMATCH`).
   - **Unknown-Site Subnet Scanner**: Scan `Area.Line.Start..End` without a `.knxproj` file to discover active devices on undocumented installations.
   - **Programming Mode LED Finder (`IndividualAddress_Read`)**: Broadcast check to identify which physical device(s) currently have their Programming LED active.
   - **BCU Device Diagnostics**: Read Serial Number, Manufacturer ID, Application Load State, and Run State, plus Fault-Injection toggles when connected to the Virtual Bus Simulator.

## Repository Structure

```text
knx-field-kit/
├── core-model/     # Pure Kotlin domain models, DPT codecs, Telegram & Audit types
├── core-knxproj/   # .knxproj & .knxkeys decryption (Zip4j + PBKDF2) and XML parsers
├── core-bus/       # KnxBusClient interface, Calimero 3.0-M2 adapter, & Virtual Bus Simulator
├── app-desktop/    # Compose Multiplatform Desktop UI (Material 3 dark engineering theme)
├── scripts/        # Local AI (Headless Ollama / Qwen2.5-Coder) streaming codegen helpers
├── docs/           # Architecture & protocol design specifications (DESIGN.md)
└── run-app.cmd     # One-click Windows launcher (auto-detects User JAVA_HOME)
```

## Hybrid Cloud + Local AI Workflow

This repository uses an **Architect + Local Worker** setup:
- **Cloud Orchestrator (Antigravity)**: Architecture, module contracts, ETS6/Calimero crypto & protocol verification, Gradle build orchestration, and integration polishing.
- **Local AI (`qwen2.5-coder:7b` / `qwen2.5-coder:14b` on Intel Vulkan iGPU via Headless Ollama)**: Generates Kotlin domain models, DPT codecs, XML parsers, simulator engines, Material 3 Compose screens, and JUnit 5 tests via `scripts/local_codegen.py` (`scripts/local-codegen.ps1`).
