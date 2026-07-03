package com.vulnrbot.app.data.tools

import com.vulnrbot.app.data.tools.ToolCategory.OSINT_SUBDOMAIN

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
        description = "In-depth attack surface mapping and subdomain enumeration. Uses ordinary DNS/HTTP " +
            "requests (no raw sockets), so it works fine without root; passive mode (-passive in " +
            "additional_args) is fastest and least likely to trip rate limits.",
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
        id = "theharvester",
        description = "Gather emails, subdomains, hosts, and employee names for a domain from public sources.",
        category = OSINT_SUBDOMAIN,
        install = InstallMethod.Apt(listOf("theharvester")),
        params = listOf(
            ToolParam("domain", description = "Target domain", required = true),
            ToolParam("sources", description = "Comma-separated data sources", default = "bing,crtsh,duckduckgo,hackertarget"),
            ToolParam("additional_args", description = "Extra theHarvester flags", isRawFlags = true),
        ),
    ) { a -> "theHarvester -d ${shEscape(a.getValue("domain"))} -b ${a["sources"] ?: "bing,crtsh,duckduckgo,hackertarget"} ${a["additional_args"] ?: ""}".trim() },
    SecurityTool(
        id = "sublist3r",
        description = "Fast passive subdomain enumeration by aggregating results from search engines and OSINT sources.",
        category = OSINT_SUBDOMAIN,
        install = InstallMethod.Script(
            listOf(
                "git clone https://github.com/aboul3la/Sublist3r /opt/sublist3r",
                "pip3 install --break-system-packages -r /opt/sublist3r/requirements.txt",
            ),
        ),
        params = listOf(ToolParam("domain", description = "Target domain", required = true)),
    ) { a -> "python3 /opt/sublist3r/sublist3r.py -d ${shEscape(a.getValue("domain"))}" },
    SecurityTool(
        id = "photon",
        description = "Fast web crawler that extracts URLs, endpoints, files, secrets, and third-party domains from a site.",
        category = OSINT_SUBDOMAIN,
        install = InstallMethod.Script(
            listOf(
                "git clone https://github.com/s0md3v/Photon /opt/photon",
                "pip3 install --break-system-packages -r /opt/photon/requirements.txt",
            ),
        ),
        params = listOf(
            ToolParam("url", description = "Target URL to crawl", required = true),
            ToolParam("depth", type = "integer", description = "Crawl depth", default = "2"),
        ),
    ) { a -> "python3 /opt/photon/photon.py -u ${shEscape(a.getValue("url"))} -l ${a["depth"] ?: "2"} --output /root/photon_output" },
    SecurityTool(
        id = "spiderfoot",
        description = "OSINT automation engine that runs dozens of footprinting modules against a target in one pass.",
        category = OSINT_SUBDOMAIN,
        install = InstallMethod.Script(
            listOf(
                "git clone https://github.com/smicallef/spiderfoot /opt/spiderfoot",
                "pip3 install --break-system-packages -r /opt/spiderfoot/requirements.txt",
            ),
        ),
        defaultTimeoutMs = 20 * 60 * 1000L,
        params = listOf(
            ToolParam("target", description = "Domain, IP, email, or name to investigate", required = true),
            ToolParam("use_case", description = "Module set to run", default = "footprint", enumValues = listOf("all", "footprint", "investigate", "passive")),
        ),
    ) { a -> "python3 /opt/spiderfoot/sf.py -s ${shEscape(a.getValue("target"))} -u ${a["use_case"] ?: "footprint"} -o json" },
    SecurityTool(
        id = "recon_ng",
        description = "Modular web reconnaissance framework; run one or more console commands non-interactively via a resource script.",
        category = OSINT_SUBDOMAIN,
        install = InstallMethod.Script(
            listOf(
                "git clone https://github.com/lanmaster53/recon-ng /opt/recon-ng",
                "pip3 install --break-system-packages -r /opt/recon-ng/REQUIREMENTS",
            ),
        ),
        params = listOf(
            ToolParam(
                "commands",
                description = "Semicolon-separated recon-ng console commands, e.g. " +
                    "\"marketplace install recon/domains-hosts/hackertarget;modules load recon/domains-hosts/hackertarget;options set SOURCE example.com;run\"",
                required = true,
            ),
        ),
    ) { a ->
        val lines = a.getValue("commands").split(";").joinToString(" ") { "'${it.trim().replace("'", "'\\''")}'" }
        "printf '%s\\n' $lines | python3 /opt/recon-ng/recon-ng"
    },
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
