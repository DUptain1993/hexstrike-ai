package com.vulnrbot.app.data.tools

import com.vulnrbot.app.data.tools.ToolCategory.WEB_APP

val webAppTools: List<SecurityTool> = listOf(
    SecurityTool(
        id = "gobuster",
        description = "Brute-force directories, DNS subdomains, vhosts, or fuzz parameters against a web target.",
        category = WEB_APP,
        install = InstallMethod.Apt(listOf("gobuster")),
        params = listOf(
            ToolParam("url", description = "Target URL or domain", required = true),
            ToolParam("mode", description = "Gobuster mode", default = "dir", enumValues = listOf("dir", "dns", "fuzz", "vhost")),
            ToolParam("wordlist", description = "Path to wordlist inside the Linux environment", default = "/usr/share/wordlists/dirb/common.txt"),
            ToolParam("additional_args", description = "Extra gobuster flags", isRawFlags = true),
        ),
    ) { a ->
        buildString {
            append("gobuster ${a["mode"] ?: "dir"} -u ${shEscape(a.getValue("url"))} -w ${shEscape(a["wordlist"] ?: "/usr/share/wordlists/dirb/common.txt")}")
            a["additional_args"]?.let { append(" $it") }
        }
    },
    SecurityTool(
        id = "dirb",
        description = "Classic recursive web content scanner for hidden directories and files.",
        category = WEB_APP,
        install = InstallMethod.Apt(listOf("dirb")),
        params = listOf(
            ToolParam("url", description = "Target URL", required = true),
            ToolParam("wordlist", description = "Wordlist path", default = "/usr/share/wordlists/dirb/common.txt"),
            ToolParam("additional_args", description = "Extra dirb flags", isRawFlags = true),
        ),
    ) { a -> "dirb ${shEscape(a.getValue("url"))} ${shEscape(a["wordlist"] ?: "/usr/share/wordlists/dirb/common.txt")} ${a["additional_args"] ?: ""}".trim() },
    SecurityTool(
        id = "dirsearch",
        description = "Fast, feature-rich web path brute-forcer with recursive scanning and extension filters.",
        category = WEB_APP,
        install = InstallMethod.Pip(listOf("dirsearch")),
        params = listOf(
            ToolParam("url", description = "Target URL", required = true),
            ToolParam("extensions", description = "Comma separated extensions, e.g. php,html,js"),
            ToolParam("additional_args", description = "Extra dirsearch flags", isRawFlags = true),
        ),
    ) { a ->
        buildString {
            append("dirsearch -u ${shEscape(a.getValue("url"))}")
            a["extensions"]?.let { append(" -e ${shEscape(it)}") }
            a["additional_args"]?.let { append(" $it") }
        }
    },
    SecurityTool(
        id = "feroxbuster",
        description = "Fast recursive content discovery tool written in Rust, good for large wordlists.",
        category = WEB_APP,
        install = InstallMethod.Script(listOf("DEBIAN_FRONTEND=noninteractive apt-get install -y feroxbuster || (curl -sL https://raw.githubusercontent.com/epi052/feroxbuster/main/install-nix.sh | bash)")),
        params = listOf(
            ToolParam("url", description = "Target URL", required = true),
            ToolParam("wordlist", description = "Wordlist path", default = "/usr/share/wordlists/dirb/common.txt"),
            ToolParam("additional_args", description = "Extra feroxbuster flags", isRawFlags = true),
        ),
    ) { a -> "feroxbuster -u ${shEscape(a.getValue("url"))} -w ${shEscape(a["wordlist"] ?: "/usr/share/wordlists/dirb/common.txt")} ${a["additional_args"] ?: ""}".trim() },
    SecurityTool(
        id = "ffuf",
        description = "Fast web fuzzer for directories, vhosts, or parameters using the FUZZ keyword.",
        category = WEB_APP,
        install = InstallMethod.Apt(listOf("ffuf")),
        params = listOf(
            ToolParam("url", description = "Target URL (mode=directory appends /FUZZ automatically)", required = true),
            ToolParam("mode", description = "Fuzzing mode", default = "directory", enumValues = listOf("directory", "vhost", "parameter", "raw")),
            ToolParam("wordlist", description = "Wordlist path", default = "/usr/share/wordlists/dirb/common.txt"),
            ToolParam("match_codes", description = "HTTP status codes to match", default = "200,204,301,302,307,401,403"),
            ToolParam("additional_args", description = "Extra ffuf flags", isRawFlags = true),
        ),
    ) { a ->
        val url = shEscape(a.getValue("url"))
        val wordlist = shEscape(a["wordlist"] ?: "/usr/share/wordlists/dirb/common.txt")
        buildString {
            append("ffuf")
            when (a["mode"] ?: "directory") {
                "vhost" -> append(" -u $url -H 'Host: FUZZ' -w $wordlist")
                "parameter" -> append(" -u $url?FUZZ=value -w $wordlist")
                "raw" -> append(" -u $url -w $wordlist")
                else -> append(" -u $url/FUZZ -w $wordlist")
            }
            append(" -mc ${a["match_codes"] ?: "200,204,301,302,307,401,403"}")
            a["additional_args"]?.let { append(" $it") }
        }
    },
    SecurityTool(
        id = "wfuzz",
        description = "Web application fuzzer for parameters, headers, and payload injection points.",
        category = WEB_APP,
        install = InstallMethod.Pip(listOf("wfuzz")),
        params = listOf(
            ToolParam("url", description = "Target URL containing a FUZZ marker", required = true),
            ToolParam("wordlist", description = "Wordlist path", default = "/usr/share/wordlists/dirb/common.txt"),
            ToolParam("additional_args", description = "Extra wfuzz flags", isRawFlags = true),
        ),
    ) { a -> "wfuzz -w ${shEscape(a["wordlist"] ?: "/usr/share/wordlists/dirb/common.txt")} ${a["additional_args"] ?: ""} ${shEscape(a.getValue("url"))}".trim() },
    SecurityTool(
        id = "nikto",
        description = "Web server scanner checking for outdated software, misconfigurations, and known vulnerable files.",
        category = WEB_APP,
        install = InstallMethod.Apt(listOf("nikto")),
        params = listOf(
            ToolParam("target", description = "Target URL or host", required = true),
            ToolParam("additional_args", description = "Extra nikto flags", isRawFlags = true),
        ),
    ) { a -> "nikto -h ${shEscape(a.getValue("target"))} ${a["additional_args"] ?: ""}".trim() },
    SecurityTool(
        id = "nuclei",
        description = "Template-driven vulnerability scanner covering CVEs, misconfigurations, and exposed panels.",
        category = WEB_APP,
        install = InstallMethod.GoInstall(listOf("github.com/projectdiscovery/nuclei/v3/cmd/nuclei")),
        defaultTimeoutMs = 15 * 60 * 1000L,
        params = listOf(
            ToolParam("target", description = "Target URL or host", required = true),
            ToolParam("severity", description = "Comma-separated severities, e.g. critical,high"),
            ToolParam("tags", description = "Comma-separated template tags, e.g. cve,exposure"),
            ToolParam("template", description = "Specific template path or ID"),
            ToolParam("additional_args", description = "Extra nuclei flags", isRawFlags = true),
        ),
    ) { a ->
        buildString {
            append("nuclei -u ${shEscape(a.getValue("target"))}")
            a["severity"]?.let { append(" -severity ${shEscape(it)}") }
            a["tags"]?.let { append(" -tags ${shEscape(it)}") }
            a["template"]?.let { append(" -t ${shEscape(it)}") }
            a["additional_args"]?.let { append(" $it") }
        }
    },
    SecurityTool(
        id = "sqlmap",
        description = "Automated SQL injection detection and exploitation tool.",
        category = WEB_APP,
        install = InstallMethod.Apt(listOf("sqlmap")),
        defaultTimeoutMs = 15 * 60 * 1000L,
        params = listOf(
            ToolParam("url", description = "Target URL, e.g. https://site.com/page.php?id=1", required = true),
            ToolParam("data", description = "POST body to test, e.g. username=x&password=y"),
            ToolParam("additional_args", description = "Extra sqlmap flags, e.g. --dbs --risk=2", isRawFlags = true),
        ),
    ) { a ->
        buildString {
            append("sqlmap -u ${shEscape(a.getValue("url"))} --batch")
            a["data"]?.let { append(" --data=${shEscape(it)}") }
            a["additional_args"]?.let { append(" $it") }
        }
    },
    SecurityTool(
        id = "wpscan",
        description = "WordPress security scanner for vulnerable plugins, themes, users, and configuration issues.",
        category = WEB_APP,
        install = InstallMethod.Script(listOf("DEBIAN_FRONTEND=noninteractive apt-get install -y ruby-full && gem install wpscan")),
        params = listOf(
            ToolParam("url", description = "Target WordPress site URL", required = true),
            ToolParam("additional_args", description = "Extra wpscan flags, e.g. --enumerate p,u", isRawFlags = true),
        ),
    ) { a -> "wpscan --url ${shEscape(a.getValue("url"))} ${a["additional_args"] ?: ""}".trim() },
    SecurityTool(
        id = "wafw00f",
        description = "Fingerprint which Web Application Firewall (if any) is protecting a target.",
        category = WEB_APP,
        install = InstallMethod.Pip(listOf("wafw00f")),
        params = listOf(ToolParam("url", description = "Target URL", required = true)),
    ) { a -> "wafw00f ${shEscape(a.getValue("url"))}" },
    SecurityTool(
        id = "httpx",
        description = "Fast HTTP probing tool: checks liveness, status codes, titles, and tech stack for a list of hosts.",
        category = WEB_APP,
        install = InstallMethod.GoInstall(listOf("github.com/projectdiscovery/httpx/cmd/httpx")),
        params = listOf(
            ToolParam("target", description = "Target URL, host, or domain", required = true),
            ToolParam("additional_args", description = "Extra httpx flags, e.g. -title -tech-detect", isRawFlags = true),
        ),
    ) { a -> "echo ${shEscape(a.getValue("target"))} | httpx ${a["additional_args"] ?: "-title -tech-detect -status-code"}".trim() },
    SecurityTool(
        id = "katana",
        description = "Next-generation web crawler for discovering endpoints, JS files, and forms.",
        category = WEB_APP,
        install = InstallMethod.GoInstall(listOf("github.com/projectdiscovery/katana/cmd/katana")),
        params = listOf(
            ToolParam("url", description = "Target URL to crawl", required = true),
            ToolParam("additional_args", description = "Extra katana flags", isRawFlags = true),
        ),
    ) { a -> "katana -u ${shEscape(a.getValue("url"))} ${a["additional_args"] ?: ""}".trim() },
    SecurityTool(
        id = "hakrawler",
        description = "Simple, fast web crawler for discovering endpoints and assets.",
        category = WEB_APP,
        install = InstallMethod.GoInstall(listOf("github.com/hakluke/hakrawler")),
        params = listOf(ToolParam("url", description = "Target URL", required = true)),
    ) { a -> "echo ${shEscape(a.getValue("url"))} | hakrawler" },
    SecurityTool(
        id = "waybackurls",
        description = "Fetch known URLs for a domain from the Wayback Machine archive.",
        category = WEB_APP,
        install = InstallMethod.GoInstall(listOf("github.com/tomnomnom/waybackurls")),
        params = listOf(ToolParam("domain", description = "Target domain", required = true)),
    ) { a -> "echo ${shEscape(a.getValue("domain"))} | waybackurls" },
    SecurityTool(
        id = "gau",
        description = "Fetch known URLs from AlienVault OTX, Wayback Machine, and Common Crawl for a domain.",
        category = WEB_APP,
        install = InstallMethod.GoInstall(listOf("github.com/lc/gau/v2/cmd/gau")),
        params = listOf(ToolParam("domain", description = "Target domain", required = true)),
    ) { a -> "gau ${shEscape(a.getValue("domain"))}" },
    SecurityTool(
        id = "arjun",
        description = "Discover hidden HTTP GET/POST parameters on a web endpoint.",
        category = WEB_APP,
        install = InstallMethod.Pip(listOf("arjun")),
        params = listOf(
            ToolParam("url", description = "Target URL", required = true),
            ToolParam("additional_args", description = "Extra arjun flags", isRawFlags = true),
        ),
    ) { a -> "arjun -u ${shEscape(a.getValue("url"))} ${a["additional_args"] ?: ""}".trim() },
    SecurityTool(
        id = "dalfox",
        description = "Automated XSS scanner and parameter analyzer.",
        category = WEB_APP,
        install = InstallMethod.GoInstall(listOf("github.com/hahwul/dalfox/v2")),
        params = listOf(
            ToolParam("url", description = "Target URL", required = true),
            ToolParam("additional_args", description = "Extra dalfox flags", isRawFlags = true),
        ),
    ) { a -> "dalfox url ${shEscape(a.getValue("url"))} ${a["additional_args"] ?: ""}".trim() },
    SecurityTool(
        id = "qsreplace",
        description = "Rewrite query string values in a list of URLs, useful for building fuzzing payload lists.",
        category = WEB_APP,
        install = InstallMethod.GoInstall(listOf("github.com/tomnomnom/qsreplace")),
        params = listOf(
            ToolParam("url", description = "Target URL", required = true),
            ToolParam("replacement", description = "Replacement value", default = "FUZZ"),
        ),
    ) { a -> "echo ${shEscape(a.getValue("url"))} | qsreplace ${shEscape(a["replacement"] ?: "FUZZ")}" },
    SecurityTool(
        id = "uro",
        description = "Deduplicate and filter noisy URL lists (e.g. from waybackurls/gau) down to unique, testable ones.",
        category = WEB_APP,
        install = InstallMethod.Pip(listOf("uro")),
        params = listOf(ToolParam("input_file", description = "Path to file containing URLs, one per line", required = true)),
    ) { a -> "uro -i ${shEscape(a.getValue("input_file"))}" },
    SecurityTool(
        id = "paramspider",
        description = "Mine parameters from a domain's archived URLs for fuzzing candidates.",
        category = WEB_APP,
        install = InstallMethod.Script(listOf("pip3 install --break-system-packages git+https://github.com/devanshbatham/paramspider")),
        params = listOf(ToolParam("domain", description = "Target domain", required = true)),
    ) { a -> "paramspider -d ${shEscape(a.getValue("domain"))}" },
    SecurityTool(
        id = "xsser",
        description = "Cross-Site Scripting (XSS) detection and exploitation framework.",
        category = WEB_APP,
        install = InstallMethod.Apt(listOf("xsser")),
        params = listOf(
            ToolParam("url", description = "Target URL", required = true),
            ToolParam("additional_args", description = "Extra XSSer flags", isRawFlags = true),
        ),
    ) { a -> "xsser --url ${shEscape(a.getValue("url"))} ${a["additional_args"] ?: "--auto"}".trim() },
    SecurityTool(
        id = "xsstrike",
        description = "Advanced XSS detection suite with context analysis and payload generation, more precise than xsser for tricky reflections.",
        category = WEB_APP,
        install = InstallMethod.Script(
            listOf(
                "git clone https://github.com/s0md3v/XSStrike /opt/xsstrike",
                "pip3 install --break-system-packages -r /opt/xsstrike/requirements.txt",
            ),
        ),
        params = listOf(
            ToolParam("url", description = "Target URL", required = true),
            ToolParam("additional_args", description = "Extra XSStrike flags", isRawFlags = true),
        ),
    ) { a -> "python3 /opt/xsstrike/xsstrike.py -u ${shEscape(a.getValue("url"))} ${a["additional_args"] ?: "--skip-dom"}".trim() },
    SecurityTool(
        id = "crlfuzz",
        description = "Scan a URL (or a list of crawled URLs) for CRLF injection vulnerabilities.",
        category = WEB_APP,
        install = InstallMethod.GoInstall(listOf("github.com/dwisiswant0/crlfuzz/cmd/crlfuzz")),
        params = listOf(ToolParam("url", description = "Target URL", required = true)),
    ) { a -> "crlfuzz -u ${shEscape(a.getValue("url"))}" },
    SecurityTool(
        id = "httprobe",
        description = "Quickly check which hosts in a list are alive over HTTP/HTTPS.",
        category = WEB_APP,
        install = InstallMethod.GoInstall(listOf("github.com/tomnomnom/httprobe")),
        requiresConfirmation = false,
        params = listOf(ToolParam("host", description = "Host or domain to probe", required = true)),
    ) { a -> "echo ${shEscape(a.getValue("host"))} | httprobe" },
    SecurityTool(
        id = "newman",
        description = "Run a Postman collection from the command line for automated API security testing.",
        category = WEB_APP,
        install = InstallMethod.Script(listOf("DEBIAN_FRONTEND=noninteractive apt-get install -y nodejs npm", "npm install -g newman")),
        defaultTimeoutMs = 10 * 60 * 1000L,
        params = listOf(
            ToolParam("collection_path", description = "Path to the Postman collection JSON file", required = true),
            ToolParam("additional_args", description = "Extra newman flags, e.g. --env-var key=value", isRawFlags = true),
        ),
    ) { a -> "newman run ${shEscape(a.getValue("collection_path"))} ${a["additional_args"] ?: ""}".trim() },
)
