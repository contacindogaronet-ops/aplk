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

    var pairPort by remember { mutableStateOf("") }
    var pairCode by remember { mutableStateOf("") }
    var connectPort by remember { mutableStateOf("") }

    var isConnected by remember { mutableStateOf(AdbManager.isConnected()) }
    var logs by remember { mutableStateOf("System Ready (Pure Standalone ADB Mode).\n") }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("ADB Optimizer (Pure Standalone APK)", fontSize = 18.sp, color = Color.Black)
        Spacer(modifier = Modifier.height(10.dp))

        // Status Bar
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

        // Card 1: ADB Pair
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("1. Pair Wireless ADB", fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = pairPort,
                        onValueChange = { pairPort = it },
                        label = { Text("Pair Port") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = pairCode,
                        onValueChange = { pairCode = it },
                        label = { Text("Pair Code") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = {
                        val pPort = pairPort.toIntOrNull()
                        if (pPort != null && pairCode.isNotEmpty()) {
                            logs += "> Memulai proses ADB Pairing pada port $pPort...\n"
                            scope.launch {
                                val res = AdbManager.pair(port = pPort, pairingCode = pairCode)
                                res.onSuccess { msg -> logs += "$msg\n" }
                                   .onFailure { err -> logs += "${err.localizedMessage}\n" }
                            }
                        } else {
                            Toast.makeText(context, "Port Pair dan Code wajib diisi!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Eksekusi Pair")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Card 2: ADB Connect
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("2. Connect Wireless ADB", fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = connectPort,
                        onValueChange = { connectPort = it },
                        label = { Text("Connect Port") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            val cPort = connectPort.toIntOrNull()
                            if (cPort != null) {
                                logs += "> Mengisi koneksi ADB ke 127.0.0.1:$cPort...\n"
                                scope.launch {
                                    val res = AdbManager.connect(context = context, port = cPort)
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

        // Action Button: Run YAML
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

        // System Console Log
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
