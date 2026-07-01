package com.hexstrike.ai.data.tools

import com.hexstrike.ai.data.tools.ToolCategory.PASSWORD_AUTH

val passwordAuthTools: List<SecurityTool> = listOf(
    SecurityTool(
        id = "hydra",
        description = "Network login brute-forcer supporting SSH, FTP, HTTP forms, SMB, RDP, and dozens of other services.",
        category = PASSWORD_AUTH,
        install = InstallMethod.Apt(listOf("hydra")),
        defaultTimeoutMs = 20 * 60 * 1000L,
        params = listOf(
            ToolParam("target", description = "Target IP or hostname", required = true),
            ToolParam("service", description = "Service, e.g. ssh, ftp, http-post-form", required = true),
            ToolParam("username", description = "Single username to try"),
            ToolParam("username_file", description = "Path to a username wordlist"),
            ToolParam("password", description = "Single password to try"),
            ToolParam("password_file", description = "Path to a password wordlist"),
            ToolParam("additional_args", description = "Extra hydra flags", isRawFlags = true),
        ),
    ) { a ->
        buildString {
            append("hydra -t 4")
            a["username"]?.let { append(" -l ${shEscape(it)}") } ?: a["username_file"]?.let { append(" -L ${shEscape(it)}") }
            a["password"]?.let { append(" -p ${shEscape(it)}") } ?: a["password_file"]?.let { append(" -P ${shEscape(it)}") }
            a["additional_args"]?.let { append(" $it") }
            append(" ${shEscape(a.getValue("target"))} ${shEscape(a.getValue("service"))}")
        }
    },
    SecurityTool(
        id = "john",
        description = "John the Ripper password hash cracker; runs against a hash file with a wordlist or rules.",
        category = PASSWORD_AUTH,
        install = InstallMethod.Apt(listOf("john")),
        defaultTimeoutMs = 20 * 60 * 1000L,
        params = listOf(
            ToolParam("hash_file", description = "Path to the file containing hashes", required = true),
            ToolParam("wordlist", description = "Wordlist path", default = "/usr/share/wordlists/rockyou.txt"),
            ToolParam("format", description = "Hash format, e.g. NT, sha512crypt"),
            ToolParam("additional_args", description = "Extra john flags", isRawFlags = true),
        ),
    ) { a ->
        buildString {
            append("john")
            a["format"]?.let { append(" --format=${shEscape(it)}") }
            append(" --wordlist=${shEscape(a["wordlist"] ?: "/usr/share/wordlists/rockyou.txt")}")
            a["additional_args"]?.let { append(" $it") }
            append(" ${shEscape(a.getValue("hash_file"))}")
        }
    },
    SecurityTool(
        id = "hashcat",
        description = "GPU-accelerated password hash cracker (runs in CPU mode on-device).",
        category = PASSWORD_AUTH,
        install = InstallMethod.Apt(listOf("hashcat")),
        defaultTimeoutMs = 20 * 60 * 1000L,
        params = listOf(
            ToolParam("hash_file", description = "Path to the file containing hashes", required = true),
            ToolParam("hash_type", description = "Hashcat -m mode number, e.g. 1000 for NTLM", required = true),
            ToolParam("attack_mode", description = "Hashcat -a mode: 0 wordlist, 3 mask brute-force", default = "0"),
            ToolParam("wordlist", description = "Wordlist path (attack_mode 0)", default = "/usr/share/wordlists/rockyou.txt"),
            ToolParam("mask", description = "Mask pattern (attack_mode 3), e.g. ?a?a?a?a?a?a"),
            ToolParam("additional_args", description = "Extra hashcat flags", isRawFlags = true),
        ),
    ) { a ->
        buildString {
            append("hashcat -m ${a.getValue("hash_type")} -a ${a["attack_mode"] ?: "0"} ${shEscape(a.getValue("hash_file"))}")
            if ((a["attack_mode"] ?: "0") == "3" && a["mask"] != null) {
                append(" ${a["mask"]}")
            } else {
                append(" ${shEscape(a["wordlist"] ?: "/usr/share/wordlists/rockyou.txt")}")
            }
            a["additional_args"]?.let { append(" $it") }
        }
    },
    SecurityTool(
        id = "netexec",
        description = "Swiss-army knife for testing credentials and enumerating SMB/WinRM/SSH/RDP/LDAP on a network (formerly CrackMapExec).",
        category = PASSWORD_AUTH,
        install = InstallMethod.Pip(listOf("netexec")),
        params = listOf(
            ToolParam("target", description = "Target IP, hostname, or CIDR range", required = true),
            ToolParam("protocol", description = "Protocol", default = "smb", enumValues = listOf("smb", "winrm", "ssh", "rdp", "ldap")),
            ToolParam("username", description = "Username to authenticate with"),
            ToolParam("password", description = "Password to authenticate with"),
            ToolParam("hash", description = "NTLM hash to authenticate with"),
            ToolParam("module", description = "netexec module to run"),
            ToolParam("additional_args", description = "Extra netexec flags", isRawFlags = true),
        ),
    ) { a ->
        buildString {
            append("nxc ${a["protocol"] ?: "smb"} ${shEscape(a.getValue("target"))}")
            a["username"]?.let { append(" -u ${shEscape(it)}") }
            a["password"]?.let { append(" -p ${shEscape(it)}") }
            a["hash"]?.let { append(" -H ${shEscape(it)}") }
            a["module"]?.let { append(" -M ${shEscape(it)}") }
            a["additional_args"]?.let { append(" $it") }
        }
    },
    SecurityTool(
        id = "smbmap",
        description = "Enumerate SMB share permissions across a target or domain.",
        category = PASSWORD_AUTH,
        install = InstallMethod.Pip(listOf("smbmap")),
        params = listOf(
            ToolParam("target", description = "Target IP or hostname", required = true),
            ToolParam("username", description = "Username"),
            ToolParam("password", description = "Password"),
            ToolParam("additional_args", description = "Extra smbmap flags", isRawFlags = true),
        ),
    ) { a ->
        buildString {
            append("smbmap -H ${shEscape(a.getValue("target"))}")
            a["username"]?.let { append(" -u ${shEscape(it)}") }
            a["password"]?.let { append(" -p ${shEscape(it)}") }
            a["additional_args"]?.let { append(" $it") }
        }
    },
    SecurityTool(
        id = "enum4linux",
        description = "Enumerate users, shares, groups, and policies from Windows/Samba hosts over SMB.",
        category = PASSWORD_AUTH,
        install = InstallMethod.Apt(listOf("enum4linux")),
        params = listOf(
            ToolParam("target", description = "Target IP or hostname", required = true),
            ToolParam("additional_args", description = "Extra enum4linux flags", default = "-a", isRawFlags = true),
        ),
    ) { a -> "enum4linux ${a["additional_args"] ?: "-a"} ${shEscape(a.getValue("target"))}" },
    SecurityTool(
        id = "enum4linux_ng",
        description = "Modern rewrite of enum4linux with structured output for SMB share/user/group/policy enumeration.",
        category = PASSWORD_AUTH,
        install = InstallMethod.Script(listOf("pip3 install --break-system-packages git+https://github.com/cddmp/enum4linux-ng")),
        params = listOf(
            ToolParam("target", description = "Target IP or hostname", required = true),
            ToolParam("username", description = "Username for authenticated enumeration"),
            ToolParam("password", description = "Password for authenticated enumeration"),
            ToolParam("additional_args", description = "Extra flags", default = "-A", isRawFlags = true),
        ),
    ) { a ->
        buildString {
            append("enum4linux-ng ${shEscape(a.getValue("target"))}")
            a["username"]?.let { append(" -u ${shEscape(it)}") }
            a["password"]?.let { append(" -p ${shEscape(it)}") }
            append(" ${a["additional_args"] ?: "-A"}")
        }
    },
    SecurityTool(
        id = "rpcclient",
        description = "Interact with a Windows/Samba RPC endpoint for enumeration (run in non-interactive mode with -c).",
        category = PASSWORD_AUTH,
        install = InstallMethod.Apt(listOf("smbclient")),
        params = listOf(
            ToolParam("target", description = "Target IP or hostname", required = true),
            ToolParam("command", description = "RPC command to run, e.g. enumdomusers", default = "srvinfo"),
            ToolParam("username", description = "Username", default = ""),
        ),
    ) { a -> "rpcclient -U ${shEscape(a["username"] ?: "")} -N -c ${shEscape(a["command"] ?: "srvinfo")} ${shEscape(a.getValue("target"))}" },
    SecurityTool(
        id = "nbtscan",
        description = "Scan a network range for NetBIOS name information.",
        category = PASSWORD_AUTH,
        install = InstallMethod.Apt(listOf("nbtscan")),
        params = listOf(ToolParam("target", description = "IP or CIDR range", required = true)),
    ) { a -> "nbtscan ${shEscape(a.getValue("target"))}" },
)
