package com.jargo.adboptimizer

import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AdbManager {
    private var connectedPort: Int = -1
    private var isConnected: Boolean = false

    // Sanitizer: Hapus prefix 'adb shell' atau 'adb'
    fun sanitizeCommand(command: String): String {
        var clean = command.trim()
        if (clean.startsWith("adb shell ")) {
            clean = clean.substring(10)
        } else if (clean.startsWith("adb ")) {
            clean = clean.substring(4)
        }
        return clean.trim()
    }

    fun connect(port: Int): Result<String> {
        return try {
            val socket = Socket("127.0.0.1", port)
            socket.close()
            connectedPort = port
            isConnected = true
            Result.success("Terhubung ke ADB Local Socket 127.0.0.1:$port")
        } catch (e: Exception) {
            isConnected = false
            Result.failure(Exception("Gagal terhubung ke port $port: ${e.message}"))
        }
    }

    fun executeCommand(command: String): String {
        val cleanCmd = sanitizeCommand(command)
        if (!isConnected || connectedPort <= 0) {
            return "EXEC_ERROR: ADB Socket belum terhubung. Lakukan Connect Port terlebih dahulu."
        }

        return try {
            Socket("127.0.0.1", connectedPort).use { socket ->
                socket.soTimeout = 5000
                val output = socket.getOutputStream()
                val input = socket.getInputStream()

                // Kirim ADB OPEN Packet
                val service = "shell:$cleanCmd"
                sendAdbPacket(output, A_OPEN, 1, 0, service.toByteArray(Charsets.UTF_8))

                // Baca response stream
                val response = StringBuilder()
                val buffer = ByteArray(1024)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    response.append(String(buffer, 0, bytesRead, Charsets.UTF_8))
                }
                
                val result = response.toString().trim()
                if (result.isEmpty()) "SUCCESS (OK)" else result
            }
        } catch (e: Exception) {
            "EXEC_ERROR: ${e.localizedMessage ?: "Gagal mengeksekusi via ADB Socket"}"
        }
    }

    // ADB Protocol Constants
    private const val A_OPEN = 0x4e45504f // "OPEN"

    private fun sendAdbPacket(output: OutputStream, command: Int, arg0: Int, arg1: Int, payload: ByteArray) {
        val header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(command)
        header.putInt(arg0)
        header.putInt(arg1)
        header.putInt(payload.size)
        header.putInt(getChecksum(payload))
        header.putInt(command xor -0x1)

        output.write(header.array())
        if (payload.isNotEmpty()) {
            output.write(payload)
        }
        output.flush()
    }

    private fun getChecksum(payload: ByteArray): Int {
        var sum = 0
        for (b in payload) {
            sum += b.toInt() and 0xFF
        }
        return sum
    }
}
