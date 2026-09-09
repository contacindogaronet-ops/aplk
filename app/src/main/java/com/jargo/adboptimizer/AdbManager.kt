package com.jargo.adboptimizer

import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    // Hubungkan ADB Client Internal ke Wireless Debugging Port
    suspend fun connect(host: String = "127.0.0.1", port: Int): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                disconnect()
                // Inisialisasi TCP Socket ADB langsung
                val dadb = Dadb.create(host, port)
                dadbInstance = dadb

                // Tes eksekusi perintah "id" untuk memverifikasi konteks UID 2000
                val response = dadb.shell("id")
                if (response.exitCode == 0) {
                    Result.success("CONNECT_SUCCESS: Terhubung ke Wireless ADB (UID 2000 / AID_SHELL).\nOutput: ${response.output.trim()}")
                } else {
                    Result.failure(Exception("CONNECT_FAILED (Exit ${response.exitCode}): ${response.output}"))
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

    // Eksekusi Shell Command via Internal Dadb Client Engine
    suspend fun executeCommand(command: String): String {
        return withContext(Dispatchers.IO) {
            val dadb = dadbInstance
                ?: return@withContext "EXEC_ERROR: ADB Client belum terhubung. Lakukan Connect Port terlebih dahulu."

            val cleanCmd = sanitizeCommand(command)
            try {
                val response = dadb.shell(cleanCmd)
                val output = response.output.trim()
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
