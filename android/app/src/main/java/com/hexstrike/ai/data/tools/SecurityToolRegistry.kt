package com.hexstrike.ai.data.tools

import com.hexstrike.ai.data.venice.ToolDefinition

private val genericTools: List<SecurityTool> = listOf(
    SecurityTool(
        id = "run_shell_command",
        description = "Run an arbitrary shell command inside the private Ubuntu Linux environment on this device. " +
            "Use this for anything not covered by a dedicated tool (e.g. piping tools together, reading scan output files, using apt/pip/go directly).",
        category = ToolCategory.NETWORK_RECON,
        install = InstallMethod.Script(emptyList()),
        params = listOf(ToolParam("command", description = "The full shell command to execute", required = true)),
    ) { a -> a.getValue("command") },
    SecurityTool(
        id = "read_file",
        description = "Read the contents of a text file from the Linux environment (e.g. scan output, a config file).",
        category = ToolCategory.NETWORK_RECON,
        install = InstallMethod.Script(emptyList()),
        requiresConfirmation = false,
        params = listOf(
            ToolParam("path", description = "Absolute path inside the Linux environment", required = true),
            ToolParam("max_lines", type = "integer", description = "Only return the first N lines", default = "500"),
        ),
    ) { a -> "head -n ${a["max_lines"] ?: "500"} ${shEscape(a.getValue("path"))}" },
    SecurityTool(
        id = "write_file",
        description = "Write text content to a file in the Linux environment, e.g. a wordlist, payload, or resource script.",
        category = ToolCategory.NETWORK_RECON,
        install = InstallMethod.Script(emptyList()),
        params = listOf(
            ToolParam("path", description = "Absolute path inside the Linux environment", required = true),
            ToolParam("content", description = "Text content to write", required = true),
        ),
    ) { a -> "cat > ${shEscape(a.getValue("path"))} <<'HEXSTRIKE_EOF'\n${a.getValue("content")}\nHEXSTRIKE_EOF" },
)

object SecurityToolRegistry {

    val allTools: List<SecurityTool> = buildList {
        addAll(networkTools)
        addAll(webAppTools)
        addAll(passwordAuthTools)
        addAll(osintTools)
        addAll(binaryForensicsTools)
        addAll(cloudTools)
        addAll(exploitationTools)
        addAll(genericTools)
    }

    private val byId: Map<String, SecurityTool> = allTools.associateBy { it.id }

    fun find(id: String): SecurityTool? = byId[id]

    fun byCategory(category: ToolCategory): List<SecurityTool> = allTools.filter { it.category == category }

    /** Function-calling schemas to hand to Venice AI's `tools` request field. */
    fun toolDefinitions(): List<ToolDefinition> = allTools.map { it.toToolDefinition() }

    /** The compact bundle offered during first-run setup: fast to install, broadly useful,
     * all apt-native so there's no Go/pip toolchain bootstrap required first. */
    val recommendedCoreToolIds: Set<String> = setOf(
        "nmap", "gobuster", "dirb", "nikto", "sqlmap", "hydra", "john", "hashcat",
        "ffuf", "wfuzz", "masscan", "enum4linux", "smbmap", "binwalk", "foremost",
        "steghide", "exiftool", "strings", "xxd", "checksec", "radare2", "gdb",
        "objdump", "wafw00f", "nbtscan", "arp_scan", "dnsenum",
    )
}
