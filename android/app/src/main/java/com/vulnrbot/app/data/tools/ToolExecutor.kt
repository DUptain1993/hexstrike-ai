package com.vulnrbot.app.data.tools

import com.vulnrbot.app.data.linux.LinuxShell
import com.vulnrbot.app.data.venice.veniceJson
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

data class ToolExecution(
    val toolId: String,
    val command: String,
    val args: Map<String, String>,
    val tool: SecurityTool?,
)

data class ToolExecutionResult(
    val output: String,
    val exitCode: Int,
    val timedOut: Boolean,
)

class ToolExecutor(private val shell: LinuxShell) {

    /** Parses the model's raw `arguments` JSON and resolves the shell command that would run,
     * without executing it — used to show the user a confirmation prompt first. */
    fun prepare(toolId: String, rawArguments: String): ToolExecution {
        val tool = SecurityToolRegistry.find(toolId)
        val args = parseArguments(rawArguments)
        val missing = tool?.params?.filter { it.required && args[it.name].isNullOrBlank() }.orEmpty()
        val command = when {
            tool == null -> "# unknown tool: $toolId"
            missing.isNotEmpty() -> "# missing required parameter(s): ${missing.joinToString { it.name }}"
            else -> runCatching { tool.buildCommand(args) }.getOrElse { "# failed to build command: ${it.message}" }
        }
        return ToolExecution(toolId, command, args, tool)
    }

    suspend fun execute(execution: ToolExecution, onOutputLine: (String) -> Unit = {}): ToolExecutionResult {
        val tool = execution.tool
        if (tool == null) {
            return ToolExecutionResult("Error: unknown tool '${execution.toolId}'. It is not in the security tool registry.", -1, false)
        }
        if (execution.command.startsWith("# missing required parameter")) {
            return ToolExecutionResult("Error: ${execution.command.removePrefix("# ")}", -1, false)
        }
        if (!shell.isReady()) {
            return ToolExecutionResult(
                "Error: the on-device Linux environment isn't installed yet. Ask the user to finish setup " +
                    "from Settings > Linux environment, or continue without running tools.",
                -1,
                false,
            )
        }
        val result = shell.exec(execution.command, timeoutMs = tool.defaultTimeoutMs, onOutputLine = onOutputLine)
        return ToolExecutionResult(truncate(result.output), result.exitCode, result.timedOut)
    }

    private fun truncate(output: String, maxChars: Int = 12_000): String {
        if (output.length <= maxChars) return output
        val head = output.take(maxChars / 2)
        val tail = output.takeLast(maxChars / 2)
        return "$head\n\n...[output truncated, ${output.length - maxChars} characters omitted]...\n\n$tail"
    }

    private fun parseArguments(rawArguments: String): Map<String, String> {
        if (rawArguments.isBlank()) return emptyMap()
        return runCatching {
            veniceJson.parseToJsonElement(rawArguments).jsonObject.mapValues { (_, value) ->
                (value as? JsonPrimitive)?.let { if (it.isString) it.content else it.toString() } ?: value.toString()
            }
        }.getOrDefault(emptyMap())
    }
}
