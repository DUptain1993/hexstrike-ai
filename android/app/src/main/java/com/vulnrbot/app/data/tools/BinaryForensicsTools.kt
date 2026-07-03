package com.vulnrbot.app.data.tools

import com.vulnrbot.app.data.tools.ToolCategory.BINARY_FORENSICS

val binaryForensicsTools: List<SecurityTool> = listOf(
    SecurityTool(
        id = "binwalk",
        description = "Analyze and optionally extract embedded files/firmware images from a binary blob.",
        category = BINARY_FORENSICS,
        install = InstallMethod.Apt(listOf("binwalk")),
        params = listOf(
            ToolParam("file_path", description = "Path to the file to analyze", required = true),
            ToolParam("extract", type = "boolean", description = "Extract discovered embedded files", default = "false"),
            ToolParam("additional_args", description = "Extra binwalk flags", isRawFlags = true),
        ),
    ) { a ->
        buildString {
            append("binwalk")
            if (a["extract"] == "true") append(" -e")
            a["additional_args"]?.let { append(" $it") }
            append(" ${shEscape(a.getValue("file_path"))}")
        }
    },
    SecurityTool(
        id = "foremost",
        description = "Carve recoverable files out of a disk image or memory dump by file signature.",
        category = BINARY_FORENSICS,
        install = InstallMethod.Apt(listOf("foremost")),
        params = listOf(
            ToolParam("file_path", description = "Path to the image/dump file", required = true),
            ToolParam("output_dir", description = "Output directory", default = "/root/foremost_output"),
        ),
    ) { a -> "foremost -i ${shEscape(a.getValue("file_path"))} -o ${shEscape(a["output_dir"] ?: "/root/foremost_output")}" },
    SecurityTool(
        id = "steghide",
        description = "Extract or embed hidden data inside JPEG/BMP/WAV/AU files.",
        category = BINARY_FORENSICS,
        install = InstallMethod.Apt(listOf("steghide")),
        params = listOf(
            ToolParam("file_path", description = "Carrier file path", required = true),
            ToolParam("passphrase", description = "Passphrase to try", default = ""),
        ),
    ) { a -> "steghide extract -sf ${shEscape(a.getValue("file_path"))} -p ${shEscape(a["passphrase"] ?: "")}" },
    SecurityTool(
        id = "exiftool",
        description = "Read and display metadata (EXIF, GPS, author, software) embedded in a file.",
        category = BINARY_FORENSICS,
        install = InstallMethod.Apt(listOf("libimage-exiftool-perl")),
        requiresConfirmation = false,
        params = listOf(ToolParam("file_path", description = "Path to the file", required = true)),
    ) { a -> "exiftool ${shEscape(a.getValue("file_path"))}" },
    SecurityTool(
        id = "strings",
        description = "Print printable character sequences found in a binary file.",
        category = BINARY_FORENSICS,
        install = InstallMethod.Apt(listOf("binutils")),
        requiresConfirmation = false,
        params = listOf(
            ToolParam("file_path", description = "Path to the file", required = true),
            ToolParam("min_length", type = "integer", description = "Minimum string length", default = "4"),
        ),
    ) { a -> "strings -n ${a["min_length"] ?: "4"} ${shEscape(a.getValue("file_path"))}" },
    SecurityTool(
        id = "xxd",
        description = "Produce a hex + ASCII dump of a file, optionally limited to a byte range.",
        category = BINARY_FORENSICS,
        install = InstallMethod.Apt(listOf("xxd")),
        requiresConfirmation = false,
        params = listOf(
            ToolParam("file_path", description = "Path to the file", required = true),
            ToolParam("length", type = "integer", description = "Number of bytes to dump"),
        ),
    ) { a ->
        buildString {
            append("xxd")
            a["length"]?.let { append(" -l $it") }
            append(" ${shEscape(a.getValue("file_path"))}")
        }
    },
    SecurityTool(
        id = "checksec",
        description = "Report which binary hardening protections (NX, PIE, canary, RELRO) a binary was built with.",
        category = BINARY_FORENSICS,
        install = InstallMethod.Pip(listOf("checksec.py")),
        requiresConfirmation = false,
        params = listOf(ToolParam("file_path", description = "Path to the binary", required = true)),
    ) { a -> "checksec --file=${shEscape(a.getValue("file_path"))}" },
    SecurityTool(
        id = "objdump",
        description = "Disassemble or dump sections/headers of an ELF/PE binary.",
        category = BINARY_FORENSICS,
        install = InstallMethod.Apt(listOf("binutils")),
        requiresConfirmation = false,
        params = listOf(
            ToolParam("file_path", description = "Path to the binary", required = true),
            ToolParam("additional_args", description = "objdump flags, e.g. -d for disassembly", default = "-d", isRawFlags = true),
        ),
    ) { a -> "objdump ${a["additional_args"] ?: "-d"} ${shEscape(a.getValue("file_path"))}" },
    SecurityTool(
        id = "radare2",
        description = "Reverse engineering framework; run one or more r2 commands non-interactively against a binary.",
        category = BINARY_FORENSICS,
        install = InstallMethod.Apt(listOf("radare2")),
        params = listOf(
            ToolParam("file_path", description = "Path to the binary", required = true),
            ToolParam("commands", description = "Semicolon-separated r2 commands, e.g. \"aaa;afl;pdf@main\"", default = "aaa;afl"),
        ),
    ) { a -> "r2 -q -c ${shEscape(a["commands"] ?: "aaa;afl")} ${shEscape(a.getValue("file_path"))}" },
    SecurityTool(
        id = "gdb",
        description = "GNU Debugger; run one or more gdb commands non-interactively against a binary (batch mode).",
        category = BINARY_FORENSICS,
        install = InstallMethod.Apt(listOf("gdb")),
        params = listOf(
            ToolParam("file_path", description = "Path to the binary", required = true),
            ToolParam("commands", description = "Semicolon-separated gdb commands, e.g. \"break main;run;info registers\"", default = "run"),
        ),
    ) { a ->
        val exFlags = (a["commands"] ?: "run").split(";").joinToString(" ") { "-ex ${shEscape(it.trim())}" }
        "gdb -q -batch $exFlags ${shEscape(a.getValue("file_path"))}"
    },
    SecurityTool(
        id = "ropgadget",
        description = "Search a binary for ROP/JOP gadgets useful in exploit development.",
        category = BINARY_FORENSICS,
        install = InstallMethod.Pip(listOf("ropgadget")),
        params = listOf(
            ToolParam("file_path", description = "Path to the binary", required = true),
            ToolParam("additional_args", description = "Extra ROPgadget flags", isRawFlags = true),
        ),
    ) { a -> "ROPgadget --binary ${shEscape(a.getValue("file_path"))} ${a["additional_args"] ?: ""}".trim() },
    SecurityTool(
        id = "ropper",
        description = "Alternative ROP gadget finder with semantic gadget search and binary info display.",
        category = BINARY_FORENSICS,
        install = InstallMethod.Pip(listOf("ropper")),
        params = listOf(
            ToolParam("file_path", description = "Path to the binary", required = true),
            ToolParam("additional_args", description = "Extra ropper flags", isRawFlags = true),
        ),
    ) { a -> "ropper --file ${shEscape(a.getValue("file_path"))} ${a["additional_args"] ?: ""}".trim() },
    SecurityTool(
        id = "volatility3",
        description = "Memory forensics framework for analyzing RAM dumps (processes, network connections, injected code, etc).",
        category = BINARY_FORENSICS,
        install = InstallMethod.Pip(listOf("volatility3")),
        defaultTimeoutMs = 15 * 60 * 1000L,
        params = listOf(
            ToolParam("memory_file", description = "Path to the memory dump", required = true),
            ToolParam("plugin", description = "Volatility3 plugin, e.g. windows.pslist, linux.bash", required = true),
            ToolParam("additional_args", description = "Extra volatility3 flags", isRawFlags = true),
        ),
    ) { a -> "vol -f ${shEscape(a.getValue("memory_file"))} ${a.getValue("plugin")} ${a["additional_args"] ?: ""}".trim() },
    SecurityTool(
        id = "hashid",
        description = "Identify the likely hash algorithm(s) for a given hash string.",
        category = BINARY_FORENSICS,
        install = InstallMethod.Pip(listOf("hashid")),
        requiresConfirmation = false,
        params = listOf(ToolParam("hash", description = "The hash string to identify", required = true)),
    ) { a -> "hashid ${shEscape(a.getValue("hash"))}" },
    SecurityTool(
        id = "ghidra_headless",
        description = "Run NSA Ghidra's headless analyzer against a binary for automated reverse engineering (disassembly, decompilation) without the GUI.",
        category = BINARY_FORENSICS,
        install = InstallMethod.Script(
            listOf(
                "apt-get install -y default-jdk",
                "curl -L \"$(curl -s https://api.github.com/repos/NationalSecurityAgency/ghidra/releases/latest | grep -o 'https://[^\"]*_PUBLIC_[0-9_]*\\.zip' | head -1)\" -o /tmp/ghidra.zip",
                "unzip -q /tmp/ghidra.zip -d /opt && mv /opt/ghidra_* /opt/ghidra",
            ),
        ),
        defaultTimeoutMs = 20 * 60 * 1000L,
        params = listOf(
            ToolParam("file_path", description = "Path to the binary to analyze", required = true),
            ToolParam("additional_args", description = "Extra analyzeHeadless flags, e.g. -postScript MyScript.java", isRawFlags = true),
        ),
    ) { a ->
        buildString {
            append("mkdir -p /root/ghidra_projects && /opt/ghidra/support/analyzeHeadless /root/ghidra_projects VulnrBotProject")
            append(" -import ${shEscape(a.getValue("file_path"))} -deleteProject")
            a["additional_args"]?.let { append(" $it") }
        }
    },
    SecurityTool(
        id = "pwntools_eval",
        description = "Run a short Python snippet with the pwntools exploit-development library already imported " +
            "(process/remote/ELF/context/p32/p64/etc all in scope) — for scripted binary exploitation and CTF pwn challenges.",
        category = BINARY_FORENSICS,
        install = InstallMethod.Pip(listOf("pwntools")),
        defaultTimeoutMs = 10 * 60 * 1000L,
        params = listOf(
            ToolParam("code", description = "Python code to run after `from pwn import *`", required = true),
        ),
    ) { a -> "python3 -c ${shEscape("from pwn import *\n" + a.getValue("code"))}" },
)
