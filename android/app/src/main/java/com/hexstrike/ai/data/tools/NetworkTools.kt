package com.hexstrike.ai.data.tools

import com.hexstrike.ai.data.tools.ToolCategory.NETWORK_RECON

val networkTools: List<SecurityTool> = listOf(
    SecurityTool(
        id = "nmap",
        description = "Scan a host or network for open ports, services, and versions with nmap.",
        category = NETWORK_RECON,
        install = InstallMethod.Apt(listOf("nmap")),
        params = listOf(
            ToolParam("target", description = "IP, hostname, or CIDR range to scan", required = true),
            ToolParam("scan_type", description = "Nmap scan flags, e.g. -sCV, -sS, -sU", default = "-sCV", isRawFlags = true),
            ToolParam("ports", description = "Port or port range, e.g. 1-1000 or 80,443"),
            ToolParam("additional_args", description = "Extra nmap flags, e.g. -T4 -Pn", default = "-T4 -Pn", isRawFlags = true),
        ),
    ) { a ->
        buildString {
            append("nmap ${a["scan_type"] ?: "-sCV"}")
            a["ports"]?.let { append(" -p ${shEscape(it)}") }
            a["additional_args"]?.let { append(" $it") }
            append(" ${shEscape(a.getValue("target"))}")
        }
    },
    SecurityTool(
        id = "nmap_advanced",
        description = "Advanced nmap scan with NSE scripts for deeper vulnerability/service enumeration.",
        category = NETWORK_RECON,
        install = InstallMethod.Apt(listOf("nmap")),
        defaultTimeoutMs = 15 * 60 * 1000L,
        params = listOf(
            ToolParam("target", description = "IP, hostname, or CIDR range to scan", required = true),
            ToolParam("scripts", description = "Comma-separated NSE scripts, e.g. vuln,default,safe"),
            ToolParam("ports", description = "Port or port range"),
            ToolParam("additional_args", description = "Extra nmap flags", default = "-T4 -Pn -sV", isRawFlags = true),
        ),
    ) { a ->
        buildString {
            append("nmap")
            a["scripts"]?.let { append(" --script=${shEscape(it)}") }
            a["ports"]?.let { append(" -p ${shEscape(it)}") }
            a["additional_args"]?.let { append(" $it") }
            append(" ${shEscape(a.getValue("target"))}")
        }
    },
    SecurityTool(
        id = "masscan",
        description = "High-speed, Internet-scale port scanner. Use for very large port/IP ranges.",
        category = NETWORK_RECON,
        install = InstallMethod.Apt(listOf("masscan")),
        params = listOf(
            ToolParam("target", description = "IP, hostname, or CIDR range", required = true),
            ToolParam("ports", description = "Ports to scan", default = "1-65535"),
            ToolParam("rate", type = "integer", description = "Packets per second", default = "1000"),
            ToolParam("additional_args", description = "Extra masscan flags", isRawFlags = true),
        ),
    ) { a ->
        buildString {
            append("masscan ${shEscape(a.getValue("target"))} -p${a["ports"] ?: "1-65535"} --rate=${a["rate"] ?: "1000"}")
            a["additional_args"]?.let { append(" $it") }
        }
    },
    SecurityTool(
        id = "rustscan",
        description = "Very fast initial port discovery scanner, typically piped into nmap for service detection.",
        category = NETWORK_RECON,
        install = InstallMethod.Script(
            listOf(
                "curl -fsSL https://github.com/RustScan/RustScan/releases/latest/download/rustscan_2.4.1_amd64.deb -o /tmp/rustscan.deb || true",
                "cargo install rustscan || (apt-get install -y cargo && cargo install rustscan)",
            ),
        ),
        params = listOf(
            ToolParam("target", description = "IP, hostname, or CIDR range", required = true),
            ToolParam("ports", description = "Ports to scan, defaults to all 65535"),
            ToolParam("additional_args", description = "Extra rustscan flags, e.g. -- -sV", isRawFlags = true),
        ),
    ) { a ->
        buildString {
            append("rustscan -a ${shEscape(a.getValue("target"))}")
            a["ports"]?.let { append(" -p ${shEscape(it)}") }
            a["additional_args"]?.let { append(" $it") }
        }
    },
    SecurityTool(
        id = "arp_scan",
        description = "Discover live hosts on the local network segment via ARP requests.",
        category = NETWORK_RECON,
        install = InstallMethod.Apt(listOf("arp-scan")),
        params = listOf(
            ToolParam("interface", description = "Network interface, e.g. wlan0", default = "wlan0"),
            ToolParam("additional_args", description = "Extra flags", default = "--localnet", isRawFlags = true),
        ),
    ) { a -> "arp-scan -I ${shEscape(a["interface"] ?: "wlan0")} ${a["additional_args"] ?: "--localnet"}" },
    SecurityTool(
        id = "fierce",
        description = "DNS reconnaissance tool for locating non-contiguous IP space on a domain.",
        category = NETWORK_RECON,
        install = InstallMethod.Pip(listOf("fierce")),
        params = listOf(
            ToolParam("domain", description = "Target domain", required = true),
            ToolParam("additional_args", description = "Extra fierce flags", isRawFlags = true),
        ),
    ) { a -> "fierce --domain ${shEscape(a.getValue("domain"))} ${a["additional_args"] ?: ""}".trim() },
    SecurityTool(
        id = "dnsenum",
        description = "Enumerate DNS records, subdomains, and zone transfer attempts for a domain.",
        category = NETWORK_RECON,
        install = InstallMethod.Apt(listOf("dnsenum")),
        params = listOf(
            ToolParam("domain", description = "Target domain", required = true),
            ToolParam("additional_args", description = "Extra dnsenum flags", isRawFlags = true),
        ),
    ) { a -> "dnsenum ${a["additional_args"] ?: ""} ${shEscape(a.getValue("domain"))}".trim() },
    SecurityTool(
        id = "autorecon",
        description = "Automated multi-tool reconnaissance: runs nmap plus a battery of service-specific enumerators against a target.",
        category = NETWORK_RECON,
        install = InstallMethod.Pip(listOf("autorecon")),
        defaultTimeoutMs = 30 * 60 * 1000L,
        params = listOf(
            ToolParam("target", description = "IP or hostname", required = true),
            ToolParam("additional_args", description = "Extra AutoRecon flags", isRawFlags = true),
        ),
    ) { a -> "autorecon ${shEscape(a.getValue("target"))} ${a["additional_args"] ?: ""}".trim() },
)
