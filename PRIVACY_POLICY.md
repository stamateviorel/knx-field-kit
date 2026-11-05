# Privacy Policy for KNX Field Kit

**Last Updated:** November 5, 2026  
**Developer:** Viorel Stamate (`stamateviorel`)  
**Application:** KNX Field Kit (Android & Desktop)

---

### 1. Overview
KNX Field Kit is a professional commissioning, diagnostic, and monitoring tool for KNX building automation systems. We respect your privacy and are committed to ensuring complete transparency about how the app operates.

**Summary:** KNX Field Kit **does NOT collect, store, sell, or transmit any personal data, telemetry, or user analytics** to any third-party or remote servers. All communications occur strictly between your device and your local KNX IP router / interface on your local network.

---

### 2. Information We Collect
We do **not** collect any personally identifiable information (PII).

* **No User Accounts:** You do not need to register, log in, or provide personal details to use the application.
* **No Tracking or Analytics:** The application contains zero advertising SDKs, zero user tracking libraries, and zero cloud analytics (no Google Analytics, Firebase Analytics, or similar third-party services).
* **No Cloud Storage of Project Files:** Your imported `.knxproj` ETS project archives and project files are parsed and kept solely on your local device. They are never uploaded or sent to external servers.

---

### 3. Device Permissions & How They Are Used
The app requests minimal permissions strictly necessary for communicating with KNX IP hardware:

* **INTERNET (`android.permission.INTERNET`)**:  
  Used exclusively for local KNXnet/IP UDP unicast and multicast communication (`224.0.23.12:3671`) on your local Wi-Fi or local area network (LAN) to discover and interact with KNX IP interfaces and routers.
* **ACCESS_NETWORK_STATE (`android.permission.ACCESS_NETWORK_STATE`)**:  
  Used to determine whether Wi-Fi or LAN connectivity is active to establish reliable KNX tunneling connections.
* **POST_NOTIFICATIONS (`android.permission.POST_NOTIFICATIONS`)**:  
  Optional on Android 13+. Used solely for local background bus monitor alerts or long-running device walk test notifications.

---

### 4. Data Stored Locally
All settings, connection history (e.g., KNX gateway IP addresses and port numbers), custom group address labels, and diagnostic logs remain 100% on your device:
* Settings are saved locally using device-encrypted application preferences or local application cache.
* Bus monitor logs and diagnostic export files are created only upon your explicit command and saved to your device's local storage.
* You can erase all application data at any time via Android Settings -> Apps -> KNX Field Kit -> Clear Storage.

---

### 5. Third-Party Services
KNX Field Kit does not integrate with any third-party APIs or external advertising networks.

---

### 6. Children's Privacy
KNX Field Kit is an industrial and professional engineering tool and does not knowingly collect or target information from children under the age of 13.

---

### 7. Changes to this Privacy Policy
We may update this Privacy Policy periodically. Any updates will be posted in this repository:  
`https://github.com/stamateviorel/knx-field-kit/blob/main/PRIVACY_POLICY.md`

---

### 8. Contact Information
If you have questions or inquiries regarding this privacy policy or the application:
* **GitHub Repository:** [https://github.com/stamateviorel/knx-field-kit](https://github.com/stamateviorel/knx-field-kit)
* **GitHub Issues:** [https://github.com/stamateviorel/knx-field-kit/issues](https://github.com/stamateviorel/knx-field-kit/issues)
