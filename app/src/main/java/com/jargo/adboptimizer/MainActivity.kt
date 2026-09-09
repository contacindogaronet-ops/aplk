package com.jargo.adboptimizer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.yaml.snakeyaml.Yaml
import java.io.InputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    OptimizerScreen(assetsManager = assets)
                }
            }
        }
    }
}

@Composable
fun OptimizerScreen(assetsManager: android.content.res.AssetManager) {
    val context = LocalContext.current
    var daemonStatus by remember { mutableStateOf(false) }
    var logs by remember { mutableStateOf("System Ready.\n") }
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        daemonStatus = AdbManager.isDaemonAlive()
        logs += if (daemonStatus) {
            "STATUS: ADB Daemon Shizuku Aktif (UID 2000 Connected).\n"
        } else {
            "STATUS: ADB Daemon Inaktif. Salin perintah starter di bawah ke ADB Shell.\n"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("ADB Optimizer (Shizuku Mode)", fontSize = 20.sp, color = Color.Black)
        Spacer(modifier = Modifier.height(8.dp))

        // Status Badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(if (daemonStatus) Color.Green else Color.Red)
            )
            Text(
                text = if (daemonStatus) "Daemon Active (UID 2000)" else "Daemon Inactive",
                fontSize = 14.sp,
                color = if (daemonStatus) Color(0xFF2E7D32) else Color.Red
            )
            Button(
                onClick = {
                    daemonStatus = AdbManager.isDaemonAlive()
                    Toast.makeText(context, "Status Refreshed", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text("Cek Status", fontSize = 10.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Starter Command Box
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("ADB Starter Command (UID 2000):", fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = AdbManager.STARTER_CMD,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = Color.DarkGray
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Starter Command", AdbManager.STARTER_CMD)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Command Disalin!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Salin Perintah Starter ADB")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Run YAML Modules Button
        Button(
            onClick = {
                logs += "\n=== Memulai Eksekusi Modul YAML ===\n"
                try {
                    val moduleFiles = assetsManager.list("modules") ?: arrayOf()
                    for (file in moduleFiles.sorted()) {
                        if (file.endsWith(".yaml") || file.endsWith(".yml")) {
                            logs += "\n[Modul]: $file\n"
                            val inputStream: InputStream = assetsManager.open("modules/$file")
                            val yaml = Yaml()
                            val data: Map<String, Any> = yaml.load(inputStream)
                            val commands = data["commands"] as? List<*>

                            commands?.forEach { cmd ->
                                val rawCmd = cmd.toString()
                                logs += "> $rawCmd\n"
                                val res = AdbManager.executeCommand(rawCmd)
                                logs += "$res\n"
                            }
                        }
                    }
                } catch (e: Exception) {
                    logs += "YAML_ERROR: ${e.localizedMessage}\n"
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF673AB7))
        ) {
            Text("Jalankan Semua Modul YAML", color = Color.White)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Logs Output Console
        Text("System Output Logs:", fontSize = 12.sp)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1E1E1E))
                .padding(8.dp)
        ) {
            Text(
                text = logs,
                color = Color(0xFF4AF626),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                modifier = Modifier.verticalScroll(scrollState)
            )
        }
    }
}
