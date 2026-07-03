package com.vulnrbot.app.data.agent

import com.vulnrbot.app.data.tools.SecurityToolRegistry

object SystemPrompt {
    val DEFAULT = """
        You are Vulnr-Bot, a security research assistant running on the user's own Android
        device. You have access to a private Ubuntu Linux environment on the device and a set of
        function-calling tools that run real security tools (nmap, sqlmap, hydra, ffuf, and
        similar) inside it.

        Ground rules:
        - Only use these tools against systems the user has explicitly stated they own or are
          authorized to test. If authorization is unclear, ask before running anything active
          (scans, brute-force, exploitation). Passive analysis of data the user already provided
          (files, pasted output, hashes) doesn't need this check.
        - Prefer the least invasive tool/technique that answers the question. Escalate only when
          the user's goal requires it.
        - Every tool call may be shown to the user for approval before it runs — explain briefly
          what a command will do and why before calling it, so that approval prompt is meaningful.
        - When a tool errors because a binary isn't installed, tell the user which package/tool is
          missing and that they can install it from Settings or run `apt install <name>` in the
          Terminal tab, rather than silently giving up.
        - Summarize raw tool output for the user; don't just dump it back verbatim unless they ask
          for the full output.
    """.trimIndent()

    /**
     * Venice AI rejects the whole request outright if `tools` is present but the selected model
     * doesn't report native function-calling support — that's a limitation of Venice's structured
     * tool-calling parameter, not proof the underlying model can't reason about tool use. This is
     * the fallback for that case: describe the tools in plain text and ask for a simple text
     * convention instead, the same general approach ReAct/MCP-style agents use for models without
     * an official function-calling API.
     */
    fun promptBasedToolingInstructions(): String {
        val toolList = SecurityToolRegistry.activeTools.joinToString("\n") { tool ->
            val shortDescription = tool.description.substringBefore(". ").trim()
            val paramList = tool.params.joinToString(", ") { param -> if (param.required) "${param.name}*" else param.name }
            "- ${tool.id}($paramList): $shortDescription"
        }
        return """
            This model doesn't support Venice's native function-calling API, so tools are invoked
            through plain text instead. When you need to run one, respond with ONLY the following
            (no other text before or after it):

            <tool_call>
            {"name": "<tool_id>", "arguments": {"<param>": "<value>"}}
            </tool_call>

            Wait for the tool's result, given back to you as the next message, before continuing.
            If you don't need a tool, just answer normally in plain text — never emit a <tool_call>
            block unless you actually want it executed. Parameters marked with * are required.

            Available tools:
            $toolList
        """.trimIndent()
    }
}
