package com.jargo.adboptimizer

import android.net.LocalSocket
import android.net.LocalSocketAddress
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter

object AdbManager {
    private const val SOCKET_NAME = "jargo_adb_server"
    
    // Perintah Starter untuk menembak app_process dari ADB
    const val STARTER_CMD = "export PKG=com.jargo.adboptimizer; export APK=\$(pm path \$PKG | cut -d':' -f2 | tr -d '\\r'); CLASSPATH=\$APK app_process /system/bin com.jargo.adboptimizer.server.Server &"

    fun isDaemonAlive(): Boolean {
        return try {
            val socket = LocalSocket()
            socket.connect(LocalSocketAddress(SOCKET_NAME))
            socket.close()
            true
        } catch (e: Exception) {
            false
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

    fun executeCommand(command: String): String {
        val cleanCmd = sanitizeCommand(command)
        return try {
            val socket = LocalSocket()
            socket.connect(LocalSocketAddress(SOCKET_NAME))
            socket.soTimeout = 5000

            val writer = PrintWriter(socket.outputStream, true)
            val reader = BufferedReader(InputStreamReader(socket.inputStream))

            writer.println(cleanCmd)

            val response = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                response.append(line).append("\n")
            }

            socket.close()
            val result = response.toString().trim()
            if (result.isEmpty()) "SUCCESS (OK)" else result
        } catch (e: Exception) {
            "EXEC_ERROR: Daemon Shizuku (UID 2000) belum aktif. Jalankan Starter Command via ADB terlebih dahulu."
        }
    }
}
