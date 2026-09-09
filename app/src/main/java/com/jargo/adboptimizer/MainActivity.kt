package com.jargo.adboptimizer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.yaml.snakeyaml.Yaml
import java.io.BufferedReader
import java.io.InputStreamReader

data class YamlModule(
    var name: String = "",
    var description: String = "",
    var commands: List<String> = emptyList()
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AdbOptimizerApp(
                        onExecuteYamlModules = { loadAndExecuteYamlModules() },
                        onRunAdbCommand = { cmd -> executeShellCommand(cmd) }
                    )
                }
            }
        }
    }

    private suspend fun loadAndExecuteYamlModules(): List<String> = withContext(Dispatchers.IO) {
        val logs = mutableListOf<String>()
        val yaml = Yaml()

        try {
            val assetFiles = assets.list("modules") ?: emptyArray()
            for (fileName in assetFiles) {
                if (fileName.endsWith(".yaml") || fileName.endsWith(".yml")) {
                    val inputStream = assets.open("modules/$fileName")
                    val module = yaml.loadAs(inputStream, YamlModule::class.java)

                    logs.add("----------------------------------------")
                    logs.add("Executing Module: [${module.name}]")
                    logs.add("Desc: ${module.description}")

                    for (rawCmd in module.commands) {
                        val fullCmd = "adb shell $rawCmd"
                        val result = executeShellCommand(fullCmd)
                        logs.add("> $fullCmd\n$result")
                    }
                }
            }
        } catch (e: Exception) {
            logs.add("Error loading YAML modules: ${e.localizedMessage}")
        }
        return@withContext logs
    }

    private fun executeShellCommand(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(command)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))

            val output = StringBuilder()
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            while (errorReader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }

            process.waitFor()
            output.toString().trim().ifEmpty { "SUCCESS (No Output)" }
        } catch (e: Exception) {
            "EXEC_ERROR: ${e.localizedMessage}"
        }
    }
}

@Composable
fun AdbOptimizerApp(
    onExecuteYamlModules: suspend () -> List<String>,
    onRunAdbCommand: (String) -> String
) {
    var pairPort by remember { mutableStateOf("") }
    var pairCode by remember { mutableStateOf("") }
    var connectPort by remember { mutableStateOf("") }
    var logs by remember { mutableStateOf(listOf("Ready. Connected to Local ADB Engine.")) }
    var isRunning by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "ADB Modular Wi-Fi Optimizer",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(12.dp))

        // --- SECTION PAIRING ---
        Text(text = "1. ADB Pair (127.0.0.1)", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
        Button(
            onClick = {
                scope.launch(Dispatchers.IO) {
                    isRunning = true
                    val res = onRunAdbCommand("adb pair 127.0.0.1:$pairPort $pairCode")
                    logs = logs + "PAIR RESULT:\n$res"
                    isRunning = false
                }
            },
            enabled = !isRunning && pairPort.isNotEmpty() && pairCode.isNotEmpty(),
            modifier = Modifier.padding(top = 4.dp)
        ) {
            Text("Eksekusi Pair")
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- SECTION CONNECT ---
        Text(text = "2. ADB Connect (127.0.0.1)", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = connectPort,
                onValueChange = { connectPort = it },
                label = { Text("Port Connect") },
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        isRunning = true
                        val res = onRunAdbCommand("adb connect 127.0.0.1:$connectPort")
                        logs = logs + "CONNECT RESULT:\n$res"
                        isRunning = false
                    }
                },
                enabled = !isRunning && connectPort.isNotEmpty(),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("Connect")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- EXECUTE YAML MODULES ---
        Button(
            onClick = {
                scope.launch {
                    isRunning = true
                    val resLogs = onExecuteYamlModules()
                    logs = logs + resLogs
                    isRunning = false
                }
            },
            enabled = !isRunning,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Jalankan Semua Modul YAML (.yaml)")
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- CONSOLE LOG OUTPUT ---
        Text(text = "System Output Logs:", style = MaterialTheme.typography.labelLarge)
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF1E1E1E))
                .padding(8.dp)
        ) {
            items(logs) { logMessage ->
                Text(
                    text = logMessage,
                    color = Color(0xFF00FF00),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}
