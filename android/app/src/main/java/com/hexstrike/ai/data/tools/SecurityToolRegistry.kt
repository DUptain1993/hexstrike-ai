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

    /** The default bundle installed by Settings > "Install security tools": 30 tools chosen
     * specifically for what actually works **without root** inside a proot Ubuntu environment.
     * proot fakes uid 0 for userspace checks but doesn't grant real Linux capabilities — the
     * process still runs under the app's real (unprivileged) Android UID underneath, so anything
     * needing CAP_NET_RAW (masscan, nmap's SYN/UDP scan types, wireless tools like aircrack-ng)
     * either fails outright or silently degrades. This set sticks to tools that only ever need
     * ordinary TCP/DNS/HTTP connections or pure userspace computation. nmap is kept despite that
     * caveat because it auto-detects the lack of raw-socket privileges and falls back to an
     * unprivileged TCP connect scan — slower, but it works and is too fundamental to drop.
     * Anything outside this set (including masscan) is still fully usable by the AI agent; it
     * just isn't pre-installed, and — for the raw-socket tools specifically — likely won't
     * function correctly on-device regardless of whether it's installed. */
    val recommendedCoreToolIds: Set<String> = linkedSetOf(
        // Recon / OSINT
        "theharvester", "amass", "sublist3r", "photon", "spiderfoot", "recon_ng",
        // Web application testing
        "sqlmap", "xsstrike", "dalfox", "wafw00f", "dirsearch", "ffuf", "wfuzz", "arjun", "crlfuzz",
        // API / web automation
        "newman", "httpx", "httprobe",
        // Password & credential attacks
        "hydra", "medusa", "patator", "cewl", "crunch",
        // Exploitation frameworks
        "metasploit", "routersploit", "searchsploit",
        // Reverse engineering / binary analysis
        "ghidra_headless", "radare2", "pwntools_eval",
        // Network (works rootless via automatic fallback; see caveat above)
        "nmap",
    )

    val recommendedCoreToolCount: Int get() = recommendedCoreToolIds.size

    /** Tools actually exposed to the model in a single request: the 30 curated, pre-installed-
     * by-default tools above, plus the three generic escape-hatch tools (run_shell_command,
     * read_file, write_file) that cover anything else via manual apt/pip/go install + invocation.
     * This isn't just a token-budget nicety — sending the full ~84-tool catalog reproducibly
     * triggered a hard rejection from Venice ("Invalid type for 'tools.83', expected a json
     * object"), i.e. a real per-request tool-count ceiling somewhere around 83. Staying at 33
     * leaves generous headroom. */
    val activeToolIds: Set<String> = recommendedCoreToolIds + setOf("run_shell_command", "read_file", "write_file")

    val activeTools: List<SecurityTool> get() = allTools.filter { it.id in activeToolIds }

    /** Function-calling schemas to hand to Venice AI's `tools` request field. */
    fun toolDefinitions(): List<ToolDefinition> = activeTools.map { it.toToolDefinition() }
}
