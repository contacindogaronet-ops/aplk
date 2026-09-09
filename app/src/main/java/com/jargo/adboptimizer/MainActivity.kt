package com.jargo.adboptimizer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    var pairPort by remember { mutableStateOf("") }
    var pairCode by remember { mutableStateOf("") }
    var connectPort by remember { mutableStateOf("") }
    var logs by remember { mutableStateOf("System Ready. Masukkan Port ADB untuk memulai.\n") }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("ADB Modular Wi-Fi Optimizer", fontSize = 20.sp, color = Color.Black)
        Spacer(modifier = Modifier.height(12.dp))

        // 1. ADB Pair
        Text("1. ADB Pair (127.0.0.1)", fontSize = 14.sp)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = pairPort,
                onValueChange = { pairPort = it },
                label = { Text("Port Pair") },
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = pairCode,
                onValueChange = { pairCode = it },
                label = { Text("Pairing Code") },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                logs += "> Pair dipicu pada port $pairPort...\n"
                // Pairing logic
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Eksekusi Pair")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. ADB Connect
        Text("2. ADB Connect (127.0.0.1)", fontSize = 14.sp)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = connectPort,
                onValueChange = { connectPort = it },
                label = { Text("Port Connect") },
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    val port = connectPort.toIntOrNull()
                    if (port != null) {
                        val result = AdbManager.connect(port)
                        logs += "${result.getOrElse { it.localizedMessage }}\n"
                    } else {
                        logs += "ERROR: Port Connect tidak valid!\n"
                    }
                },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("Connect")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Run Modules
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
                                val sanitized = AdbManager.sanitizeCommand(rawCmd)
                                logs += "> $rawCmd\n"
                                val res = AdbManager.executeCommand(sanitized)
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
            Text("Jalankan Semua Modul YAML (.yaml)", color = Color.White)
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
