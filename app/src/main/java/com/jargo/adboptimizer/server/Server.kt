package com.jargo.adboptimizer.server

import android.net.LocalServerSocket
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter

class Server {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val uid = android.os.Process.myUid()
            println("JARGO_ADB_SERVER_STARTED | UID: $uid")

            try {
                // Buka Abstract Unix Domain Socket di level OS
                val serverSocket = LocalServerSocket("jargo_adb_server")
                
                while (true) {
                    val socket = serverSocket.accept() ?: continue
                    try {
                        val reader = BufferedReader(InputStreamReader(socket.inputStream))
                        val writer = PrintWriter(socket.outputStream, true)

                        val command = reader.readLine()
                        if (!command.isNullOrBlank()) {
                            // Eksekusi shell murni di bawah UID 2000
                            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
                            val output = process.inputStream.bufferedReader().readText()
                            val error = process.errorStream.bufferedReader().readText()
                            process.waitFor()

                            val result = (output + error).trim()
                            writer.println(if (result.isEmpty()) "SUCCESS (OK)" else result)
                        }
                    } catch (e: Exception) {
                        // Klien terputus / error eksekusi parsial
                    } finally {
                        socket.close()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
