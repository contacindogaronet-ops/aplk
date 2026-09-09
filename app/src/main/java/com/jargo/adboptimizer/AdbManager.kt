package com.jargo.adboptimizer

import android.content.Context
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object AdbManager {

    private var dadbInstance: Dadb? = null

    fun isConnected(): Boolean {
        return dadbInstance != null
    }

    fun disconnect() {
        try {
            dadbInstance?.close()
        } catch (_: Exception) {}
        dadbInstance = null
    }

    // 1. Eksekusi Pairing ADB internal (Otomatis tanpa app luar)
    suspend fun pair(host: String = "127.0.0.1", port: Int, pairingCode: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val paired = Dadb.pair(host, port, pairingCode)
                if (paired) {
                    Result.success("PAIRING_SUCCESS: Berhasil terhubung ke ADB Pairing Server ($port).")
                } else {
                    Result.failure(Exception("PAIRING_FAILED: Kode pairing atau port salah."))
                }
            } catch (e: Exception) {
                Result.failure(Exception("PAIRING_ERROR: ${e.localizedMessage}"))
            }
        }
    }

    // 2. Hubungkan ADB Client Internal ke Wireless Debugging
    suspend fun connect(context: Context, host: String = "127.0.0.1", port: Int): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                disconnect()
                // Membuka koneksi ADB Shell berbasis TLS Socket internal
                val dadb = Dadb.create(host, port)
                dadbInstance = dadb

                // Test eksekusi shell sederhana untuk verifikasi UID 2000
                val response = dadb.shell("id")
                if (response.exitCode == 0) {
                    Result.success("CONNECT_SUCCESS: Terhubung ke Wireless ADB (UID 2000 / AID_SHELL).\nResult: ${response.output.trim()}")
                } else {
                    Result.failure(Exception("CONNECT_FAILED: Exit Code ${response.exitCode}"))
                }
            } catch (e: Exception) {
                disconnect()
                Result.failure(Exception("CONNECT_ERROR: ${e.localizedMessage}. Pastikan Wireless Debugging aktif dan Port sudah benar."))
            }
        }
    }

    fun sanitizeCommand(command: String): String {
        var clean = command.trim()
        if (clean.startsWith("adb shell ")) {
            clean = clean.substring(10)
        } else if (clean.startsWith("adb ")) {
            clean = clean.substring(4)
        }
        return clean.trim()
    }

    // 3. Eksekusi Perintah Shell Langsung dari ADB Engine Internal
    suspend fun executeCommand(command: String): String {
        return withContext(Dispatchers.IO) {
            val dadb = dadbInstance
                ?: return@withContext "EXEC_ERROR: ADB Client belum terhubung. Lakukan Connect Port terlebih dahulu."

            val cleanCmd = sanitizeCommand(command)
            try {
                val response = dadb.shell(cleanCmd)
                val output = (response.output + "\n" + response.errorOutput).trim()
                if (response.exitCode == 0) {
                    if (output.isEmpty()) "SUCCESS (OK)" else output
                } else {
                    "ERROR (Exit ${response.exitCode}): $output"
                }
            } catch (e: Exception) {
                "EXEC_EXCEPTION: ${e.localizedMessage}"
            }
        }
    }
}
