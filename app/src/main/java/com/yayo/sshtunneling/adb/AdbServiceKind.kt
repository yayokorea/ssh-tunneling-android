package com.yayo.sshtunneling.adb

enum class AdbServiceKind(
    val serviceType: String,
    val reversePort: Int,
) {
    CONNECT("_adb-tls-connect._tcp.", 5555),
    PAIRING("_adb-tls-pairing._tcp.", 5556),
}
