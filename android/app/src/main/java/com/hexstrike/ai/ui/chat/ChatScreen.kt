package com.hexstrike.ai.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ChatScreen(viewModel: ChatViewModel = viewModel()) {
    val messages by viewModel.messages.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val pendingApproval by viewModel.pendingApproval.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!settings.isReadyToChat) {
            Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Add a Venice AI API key and choose a model in Settings to start chatting.",
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        } else if (settings.linuxEnvironmentEnabled && !settings.selectedModelSupportsTools) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "\"${settings.selectedModel}\" doesn't support tool calling, so this chat won't be able to run " +
                        "security tools — pick a model with tool support in Settings for that.",
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(messages, key = { _, item -> item.id }) { _, message ->
                when (message) {
                    is UserBubble -> UserMessageRow(message)
                    is AssistantBubble -> AssistantMessageRow(message)
                    is ToolBubble -> ToolMessageRow(
                        message = message,
                        awaitingThisApproval = pendingApproval?.callId == message.id,
                        onApprove = { viewModel.respondToApproval(true) },
                        onDeny = { viewModel.respondToApproval(false) },
                    )
                    is SystemNotice -> SystemNoticeRow(message)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message HexStrike AI…") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                enabled = !isStreaming,
            )
            if (isStreaming) {
                CircularProgressIndicator(modifier = Modifier.padding(start = 12.dp).heightIn(max = 24.dp))
            } else {
                IconButton(onClick = { viewModel.sendMessage(input); input = "" }, enabled = input.isNotBlank()) {
                    Icon(Icons.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@Composable
private fun UserMessageRow(message: UserBubble) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Card(
            shape = RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Text(message.text, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
private fun AssistantMessageRow(message: AssistantBubble) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Card(
            shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.widthIn(max = 340.dp),
        ) {
            Column(Modifier.padding(12.dp)) {
                if (message.text.isNotBlank()) {
                    MarkdownText(message.text)
                } else if (message.streaming) {
                    Text("…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ToolMessageRow(
    message: ToolBubble,
    awaitingThisApproval: Boolean,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🔧 ${message.toolId}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Text("  ·  ${statusLabel(message.status)}", style = MaterialTheme.typography.labelMedium)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp))
                    .padding(8.dp),
            ) {
                Text(message.command, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
            if (message.output.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .background(MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(message.output, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (awaitingThisApproval) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onApprove) { Text("Run") }
                    OutlinedButton(onClick = onDeny) { Text("Deny") }
                }
            }
        }
    }
}

@Composable
private fun SystemNoticeRow(message: SystemNotice) {
    Surface(
        color = if (message.isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            message.text,
            modifier = Modifier.padding(10.dp),
            color = if (message.isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun statusLabel(status: ToolStatus): String = when (status) {
    ToolStatus.PENDING_APPROVAL -> "waiting for approval"
    ToolStatus.RUNNING -> "running…"
    ToolStatus.SUCCESS -> "done"
    ToolStatus.FAILED -> "failed"
    ToolStatus.DENIED -> "denied"
}
