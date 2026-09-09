package com.jargo.adboptimizer

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
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
    val scope = rememberCoroutineScope()

    var connectPort by remember { mutableStateOf("") }
    var isConnected by remember { mutableStateOf(AdbManager.isConnected()) }
    var logs by remember { mutableStateOf("System Ready (Pure Standalone ADB Engine).\n") }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("ADB Optimizer (Standalone ADB Engine)", fontSize = 18.sp, color = Color.Black)
        Spacer(modifier = Modifier.height(10.dp))

        // Indikator Status Koneksi
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(if (isConnected) Color(0xFF2E7D32) else Color.Red)
            )
            Text(
                text = if (isConnected) "ADB Connected (UID 2000)" else "ADB Disconnected",
                fontSize = 13.sp,
                color = if (isConnected) Color(0xFF2E7D32) else Color.Red
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Card Koneksi ADB Wireless
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Connect Wireless Debugging Port", fontSize = 14.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = connectPort,
                        onValueChange = { connectPort = it },
                        label = { Text("Port (misal: 37xxx / 5555)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            val cPort = connectPort.toIntOrNull()
                            if (cPort != null) {
                                logs += "> Mengisi koneksi ADB ke 127.0.0.1:$cPort...\n"
                                scope.launch {
                                    val res = AdbManager.connect(port = cPort)
                                    res.onSuccess { msg ->
                                        logs += "$msg\n"
                                        isConnected = true
                                    }.onFailure { err ->
                                        logs += "${err.localizedMessage}\n"
                                        isConnected = false
                                    }
                                }
                            } else {
                                Toast.makeText(context, "Port Connect tidak valid!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("Connect")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Tombol Eksekusi Modul YAML
        Button(
            onClick = {
                scope.launch {
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
                }
            },
            enabled = isConnected,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF673AB7))
        ) {
            Text("Jalankan Semua Modul YAML", color = Color.White)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Log Output Konsol
        Text("System Output Logs:", fontSize = 11.sp)
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
                fontSize = 10.sp,
                modifier = Modifier.verticalScroll(scrollState)
            )
        }
    }
}
