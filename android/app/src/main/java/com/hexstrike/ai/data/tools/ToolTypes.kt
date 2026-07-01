package com.hexstrike.ai.data.tools

import com.hexstrike.ai.data.venice.FunctionSpec
import com.hexstrike.ai.data.venice.ToolDefinition
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

enum class ToolCategory(val displayName: String) {
    NETWORK_RECON("Network & Port Scanning"),
    WEB_APP("Web Application"),
    PASSWORD_AUTH("Password & Authentication"),
    OSINT_SUBDOMAIN("OSINT & Subdomain Enumeration"),
    BINARY_FORENSICS("Binary Analysis & Forensics"),
    CLOUD_CONTAINER("Cloud & Container Security"),
    EXPLOITATION("Exploitation"),
}

sealed class InstallMethod {
    data class Apt(val packages: List<String>) : InstallMethod()
    data class Pip(val packages: List<String>) : InstallMethod()
    data class GoInstall(val modules: List<String>) : InstallMethod()
    data class Script(val commands: List<String>) : InstallMethod()

    fun toShellCommand(): String = when (this) {
        is Apt -> "DEBIAN_FRONTEND=noninteractive apt-get install -y ${packages.joinToString(" ")}"
        is Pip -> "pip3 install --break-system-packages --quiet ${packages.joinToString(" ")}"
        is GoInstall -> modules.joinToString(" && ") { "go install $it@latest" }
        is Script -> commands.joinToString(" && ")
    }
}

data class ToolParam(
    val name: String,
    val type: String = "string",
    val description: String,
    val required: Boolean = false,
    val default: String? = null,
    val enumValues: List<String>? = null,
    /** free-flag params (e.g. "additional_args") are passed through unescaped since they're
     * meant to hold multiple shell flags; everything else is single-quote escaped. */
    val isRawFlags: Boolean = false,
)

data class SecurityTool(
    val id: String,
    val description: String,
    val category: ToolCategory,
    val install: InstallMethod,
    val params: List<ToolParam>,
    val requiresConfirmation: Boolean = true,
    val defaultTimeoutMs: Long = 5 * 60 * 1000L,
    val buildCommand: (args: Map<String, String>) -> String,
)

/** POSIX single-quote escaping so target/url/wordlist-style values can never break out of the
 * `/bin/sh -c "..."` invocation, regardless of what the model puts in them. */
fun shEscape(value: String): String = "'" + value.replace("'", "'\\''") + "'"

fun SecurityTool.toFunctionSpec(): FunctionSpec {
    val properties = buildJsonObject {
        params.forEach { param ->
            put(
                param.name,
                buildJsonObject {
                    put("type", param.type)
                    put("description", param.description)
                    param.enumValues?.let { values ->
                        put("enum", buildJsonArray { values.forEach { add(JsonPrimitive(it)) } })
                    }
                },
            )
        }
    }
    val required = buildJsonArray { params.filter { it.required }.forEach { add(JsonPrimitive(it.name)) } }
    val schema = buildJsonObject {
        put("type", "object")
        put("properties", properties)
        put("required", required)
    }
    return FunctionSpec(name = id, description = description, parameters = schema)
}

fun SecurityTool.toToolDefinition(): ToolDefinition = ToolDefinition(function = toFunctionSpec())
