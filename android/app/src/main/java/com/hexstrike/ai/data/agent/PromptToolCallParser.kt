package com.hexstrike.ai.data.agent

import com.hexstrike.ai.data.venice.veniceJson
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Parses the `<tool_call>{...}</tool_call>` text convention used as a fallback for models Venice
 * won't let use its native `tools` request field (see [SystemPrompt.promptBasedToolingInstructions]).
 */
object PromptToolCallParser {

    private val TOOL_CALL_REGEX = Regex("<tool_call>(.*?)</tool_call>", RegexOption.DOT_MATCHES_ALL)

    data class ParsedCall(val name: String, val argumentsJson: String)

    fun extract(text: String): ParsedCall? {
        val match = TOOL_CALL_REGEX.find(text) ?: return null
        return runCatching {
            val obj = veniceJson.parseToJsonElement(match.groupValues[1].trim()).jsonObject
            val name = (obj["name"] as? JsonPrimitive)?.content ?: return null
            val argumentsJson = obj["arguments"]?.toString() ?: "{}"
            ParsedCall(name, argumentsJson)
        }.getOrNull()
    }

    fun stripToolCallBlock(text: String): String = text.replace(TOOL_CALL_REGEX, "").trim()
}
