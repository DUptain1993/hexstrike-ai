package com.hexstrike.ai.data.tools

import com.hexstrike.ai.data.tools.ToolCategory.OSINT_SUBDOMAIN

val osintTools: List<SecurityTool> = listOf(
    SecurityTool(
        id = "subfinder",
        description = "Passive subdomain enumeration using dozens of OSINT sources.",
        category = OSINT_SUBDOMAIN,
        install = InstallMethod.GoInstall(listOf("github.com/projectdiscovery/subfinder/v2/cmd/subfinder")),
        params = listOf(
            ToolParam("domain", description = "Target domain", required = true),
            ToolParam("all_sources", type = "boolean", description = "Use all sources (slower, more thorough)", default = "false"),
            ToolParam("additional_args", description = "Extra subfinder flags", isRawFlags = true),
        ),
    ) { a ->
        buildString {
            append("subfinder -d ${shEscape(a.getValue("domain"))} -silent")
            if (a["all_sources"] == "true") append(" -all")
            a["additional_args"]?.let { append(" $it") }
        }
    },
    SecurityTool(
        id = "amass",
        description = "In-depth attack surface mapping and subdomain enumeration (active or passive).",
        category = OSINT_SUBDOMAIN,
        install = InstallMethod.GoInstall(listOf("github.com/owasp-amass/amass/v4/...")),
        defaultTimeoutMs = 15 * 60 * 1000L,
        params = listOf(
            ToolParam("domain", description = "Target domain", required = true),
            ToolParam("mode", description = "Amass subcommand", default = "enum", enumValues = listOf("enum", "intel")),
            ToolParam("additional_args", description = "Extra amass flags, e.g. -passive", isRawFlags = true),
        ),
    ) { a -> "amass ${a["mode"] ?: "enum"} -d ${shEscape(a.getValue("domain"))} ${a["additional_args"] ?: ""}".trim() },
    SecurityTool(
        id = "searchsploit",
        description = "Search the offline Exploit-DB mirror for known exploits matching a product/version.",
        category = OSINT_SUBDOMAIN,
        install = InstallMethod.Script(
            listOf(
                "git clone https://github.com/offensive-security/exploitdb /opt/exploitdb",
                "ln -sf /opt/exploitdb/searchsploit /usr/local/bin/searchsploit",
            ),
        ),
        requiresConfirmation = false,
        params = listOf(ToolParam("query", description = "Search term, e.g. \"Apache 2.4.49\"", required = true)),
    ) { a -> "searchsploit ${shEscape(a.getValue("query"))}" },
)
