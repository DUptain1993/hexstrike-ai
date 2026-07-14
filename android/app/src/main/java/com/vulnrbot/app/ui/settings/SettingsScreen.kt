package com.vulnrbot.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vulnrbot.app.data.linux.LinuxEnvironmentState
import com.vulnrbot.app.data.tools.SecurityToolRegistry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val settings by viewModel.settings.collectAsState()
    val modelsState by viewModel.modelsState.collectAsState()
    val linuxState by viewModel.linuxState.collectAsState()
    val toolInstallRunning by viewModel.toolInstallRunning.collectAsState()
    val toolInstallCurrent by viewModel.toolInstallCurrent.collectAsState()
    val toolInstallResults by viewModel.toolInstallResults.collectAsState()
    val diagnostics by viewModel.diagnostics.collectAsState()
    val diagnosticsRunning by viewModel.diagnosticsRunning.collectAsState()
    var apiKeyVisible by remember { mutableStateOf(false) }
    var apiKeyField by remember(settings.apiKey) { mutableStateOf(settings.apiKey) }
    var baseUrlField by remember(settings.baseUrl) { mutableStateOf(settings.baseUrl) }
    var chrootPathField by remember(settings.chrootPath) { mutableStateOf(settings.chrootPath) }
    var modelMenuExpanded by remember { mutableStateOf(false) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SectionHeader("Venice AI") }
        item {
            OutlinedTextField(
                value = apiKeyField,
                onValueChange = { apiKeyField = it; viewModel.updateApiKey(it) },
                label = { Text("API key") },
                placeholder = { Text("Paste your key from venice.ai/settings/api") },
                singleLine = true,
                visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                        Icon(if (apiKeyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = null)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = baseUrlField,
                onValueChange = { baseUrlField = it; viewModel.updateBaseUrl(it) },
                label = { Text("API base URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Button(onClick = { viewModel.testConnectionAndLoadModels() }, enabled = apiKeyField.isNotBlank()) {
                Text("Test connection & load models")
            }
        }
        item {
            when (val state = modelsState) {
                is ModelsLoadState.Loading -> Row(modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                    Text("Contacting Venice AI…")
                }
                is ModelsLoadState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error)
                is ModelsLoadState.Loaded -> ExposedDropdownMenuBox(
                    expanded = modelMenuExpanded,
                    onExpandedChange = { modelMenuExpanded = it },
                ) {
                    OutlinedTextField(
                        value = state.models.find { it.id == settings.selectedModel }?.displayName ?: settings.selectedModel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Model") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelMenuExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    ExposedDropdownMenu(expanded = modelMenuExpanded, onDismissRequest = { modelMenuExpanded = false }) {
                        state.models.forEach { model ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(model.displayName)
                                        Text(
                                            if (model.supportsTools) "Supports tool use · ${model.contextLength ?: 0} ctx" else "No tool use · ${model.contextLength ?: 0} ctx",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                },
                                onClick = { viewModel.updateModel(model); modelMenuExpanded = false },
                            )
                        }
                    }
                }
                ModelsLoadState.Idle -> if (settings.selectedModel.isNotBlank()) Text("Model: ${settings.selectedModel}")
            }
        }

        item { SectionHeader("Agent behavior") }
        item {
            SettingsSwitchRow("Enable web search", "Let the model search the web when answering", settings.enableWebSearch, viewModel::setWebSearch)
        }
        item {
            SettingsSwitchRow("Hide model reasoning traces", "Strip <think> blocks from reasoning models", settings.stripThinkingResponse, viewModel::setStripThinking)
        }
        item {
            SettingsSwitchRow("Auto-approve tool executions", "Off: you confirm every command before it runs", settings.autoApproveTools, viewModel::setAutoApprove)
        }
        item {
            Column {
                Text("Temperature: ${"%.1f".format(settings.temperature)}")
                Slider(value = settings.temperature, onValueChange = viewModel::updateTemperature, valueRange = 0f..2f)
            }
        }

        item { SectionHeader("Linux environment") }
        item {
            SettingsSwitchRow("Enable security tools", "Turn off for chat-only mode with no local Linux environment", settings.linuxEnvironmentEnabled, viewModel::setLinuxEnabled)
        }
        item {
            OutlinedTextField(
                value = chrootPathField,
                onValueChange = { chrootPathField = it; viewModel.updateChrootPath(it) },
                label = { Text("Ubuntu chroot path") },
                placeholder = { Text("/data/local/chroot/ubuntu") },
                singleLine = true,
                supportingText = { Text("Path to your existing rooted chroot on the device.") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            RootChrootCard(
                state = linuxState,
                diagnostics = diagnostics,
                diagnosticsRunning = diagnosticsRunning,
                onTest = viewModel::testRootAndChroot,
                onRecheck = viewModel::refreshLinuxState,
                onPrepare = viewModel::installLinuxEnvironment,
            )
        }
        if (linuxState == LinuxEnvironmentState.Ready) {
            item {
                ToolInstallCard(
                    running = toolInstallRunning,
                    current = toolInstallCurrent,
                    results = toolInstallResults,
                    onInstall = viewModel::installSecurityTools,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun SettingsSwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
    )
}

@Composable
private fun RootChrootCard(
    state: LinuxEnvironmentState,
    diagnostics: String?,
    diagnosticsRunning: Boolean,
    onTest: () -> Unit,
    onRecheck: () -> Unit,
    onPrepare: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (state) {
                LinuxEnvironmentState.CheckingRoot -> Row {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp).heightIn(max = 20.dp))
                    Text("Checking root & chroot…")
                }
                LinuxEnvironmentState.NeedsRoot -> Text(
                    "Root not available. This build runs its tools inside your existing rooted Ubuntu chroot, " +
                        "so it needs root. Grant this app root in your superuser manager (Magisk/KernelSU), then re-check.",
                    color = MaterialTheme.colorScheme.error,
                )
                is LinuxEnvironmentState.ChrootNotFound -> Text(
                    "Root works, but no Ubuntu chroot was found at ${state.path} (no bin/bash there). " +
                        "Fix the path above, or set up the chroot on the device first.",
                    color = MaterialTheme.colorScheme.error,
                )
                LinuxEnvironmentState.Preparing -> Row {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp).heightIn(max = 20.dp))
                    Text("Installing baseline packages in the chroot…")
                }
                LinuxEnvironmentState.Ready -> Text(
                    "Ready — root granted and chroot found. Tools run as real root inside your chroot.",
                    color = MaterialTheme.colorScheme.primary,
                )
                is LinuxEnvironmentState.Error -> Text("Setup failed: ${state.message}", color = MaterialTheme.colorScheme.error)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onTest, enabled = !diagnosticsRunning) { Text("Test root & chroot") }
                OutlinedButton(onClick = onRecheck) { Text("Re-check") }
                if (state == LinuxEnvironmentState.Ready) {
                    OutlinedButton(onClick = onPrepare) { Text("Run baseline setup") }
                }
            }

            if (diagnostics != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                ) {
                    Text(diagnostics, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun ToolInstallCard(
    running: Boolean,
    current: String?,
    results: List<ToolInstallResult>,
    onInstall: () -> Unit,
) {
    val total = SecurityToolRegistry.recommendedCoreToolCount
    val succeeded = results.count { it.success }
    val failed = results.count { !it.success }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Security tools", style = MaterialTheme.typography.titleMedium)
            Text(
                "Installs $total widely used free/open-source pentesting tools (nmap, sqlmap, hydra, " +
                    "john, nuclei, subfinder, radare2, and more) into the Linux environment via apt/go/pip.",
                style = MaterialTheme.typography.bodySmall,
            )

            if (running) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("Installing${current?.let { " $it…" } ?: "…"} (${results.size}/$total)")
            } else if (results.isNotEmpty()) {
                Text(
                    "Done: $succeeded installed, $failed failed. Failures can usually be fixed with " +
                        "`apt install <name>` in the Terminal tab.",
                    color = if (failed == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }

            if (results.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    results.forEach { result ->
                        Text(
                            "${if (result.success) "✓" else "✗"} ${result.toolId}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (result.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                        // The actual apt/pip/go error was always captured in ToolInstallResult but
                        // never rendered anywhere, so a failure only ever showed as a bare red name
                        // with no way for anyone — user or dev — to tell *why* it failed.
                        if (!result.success && result.message.isNotBlank()) {
                            Text(
                                result.message.trim().takeLast(400),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 12.dp, bottom = 4.dp),
                            )
                        }
                    }
                }
            }

            if (results.isEmpty() || !running) {
                OutlinedButton(onClick = onInstall, enabled = !running) {
                    Text(if (results.isEmpty()) "Install security tools" else "Reinstall all")
                }
            }
        }
    }
}
