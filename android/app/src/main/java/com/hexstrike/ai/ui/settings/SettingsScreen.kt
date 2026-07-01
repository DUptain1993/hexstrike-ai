package com.hexstrike.ai.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hexstrike.ai.data.linux.LinuxEnvironmentState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val settings by viewModel.settings.collectAsState()
    val modelsState by viewModel.modelsState.collectAsState()
    val linuxState by viewModel.linuxState.collectAsState()
    var apiKeyVisible by remember { mutableStateOf(false) }
    var apiKeyField by remember(settings.apiKey) { mutableStateOf(settings.apiKey) }
    var baseUrlField by remember(settings.baseUrl) { mutableStateOf(settings.baseUrl) }
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
                                onClick = { viewModel.updateModel(model.id); modelMenuExpanded = false },
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
        item { LinuxEnvironmentCard(linuxState, onInstall = viewModel::installLinuxEnvironment) }
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
private fun LinuxEnvironmentCard(state: LinuxEnvironmentState, onInstall: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (state) {
                LinuxEnvironmentState.Unavailable -> Text(
                    "Not built into this APK. See native/README.md to cross-compile proot; until then, security tools are unavailable and chat still works.",
                )
                LinuxEnvironmentState.NotInstalled -> {
                    Text("Ubuntu environment not installed yet (~150-400MB download).")
                    Button(onClick = onInstall) { Text("Install now") }
                }
                is LinuxEnvironmentState.Downloading -> {
                    val pct = if (state.totalBytes > 0) (state.bytesRead * 100 / state.totalBytes).toInt() else 0
                    Text("Downloading Ubuntu base… $pct%")
                }
                LinuxEnvironmentState.Extracting -> Text("Extracting root filesystem…")
                LinuxEnvironmentState.Configuring -> Text("Installing baseline packages…")
                LinuxEnvironmentState.Ready -> Text("Ready. Reinstall packages any time from the Terminal tab with apt.", color = MaterialTheme.colorScheme.primary)
                is LinuxEnvironmentState.Error -> {
                    Text("Setup failed: ${state.message}", color = MaterialTheme.colorScheme.error)
                    Button(onClick = onInstall) { Text("Retry") }
                }
            }
        }
    }
}
