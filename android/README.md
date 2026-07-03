# HexStrike AI — Android

A standalone Android client for HexStrike-style AI-driven security testing. Add a
[Venice AI](https://venice.ai) API key, pick a model, and chat with an agent that can run real
security tools (nmap, sqlmap, ffuf, hydra, and ~80 others) inside a private Ubuntu Linux
environment on the device itself — no separate server, no Termux install required.

**Only use this against systems you own or are explicitly authorized to test.** The app requires
you to acknowledge this before first use (see `ui/onboarding`), but that acknowledgment doesn't
change your actual legal obligations — it's on you.

## Why this exists / how it relates to the rest of the repo

The root of this repository is `hexstrike_server.py` + `hexstrike_mcp.py`: a Flask server plus an
MCP wrapper that lets desktop AI clients (Claude Desktop, Cursor, etc.) drive ~90 pentesting tools
over HTTP. This Android app is a from-scratch reimplementation of that same idea for a phone: it
doesn't call the Python server at all. Instead:

- **The LLM** is Venice AI's OpenAI-compatible API instead of Claude/GPT via MCP.
- **The tool execution environment** is a proot-hosted Ubuntu filesystem living in this app's
  private storage instead of the host machine the Python server runs on.
- **The tool catalog** (`data/tools/*.kt`) is a Kotlin port of the same command-building logic
  found in `hexstrike_server.py`'s `/api/tools/*` routes — same flags, same defaults, same tools
  where practical for a mobile ARM/Ubuntu environment.

## Architecture

```
ui/            Jetpack Compose screens (onboarding, settings, chat, terminal) + ViewModels
data/agent/    AgentOrchestrator: the tool-calling loop between Venice AI and the tool executor
data/venice/   Venice AI API client (OpenAI-compatible chat completions, streaming, /models)
data/tools/    ~84 security tool definitions (command templates + JSON schemas) + ToolExecutor
data/linux/    proot + Ubuntu rootfs lifecycle: download, extract, exec, interactive terminal
data/settings/ Encrypted, persisted user settings (API key, model, toggles)
data/db/       Room persistence for chat history
native/        Docs for the proot binaries; scripts/fetch-proot.sh does the actual fetching
```

Data flow for a single user turn (`AgentOrchestrator.runTurn`):

1. Send the conversation + the full tool catalog's JSON schemas to Venice AI as a streaming
   `chat/completions` request.
2. Stream the reply into the chat UI token-by-token.
3. If the model requests a tool call, resolve it to an actual shell command
   (`ToolExecutor.prepare`), show the user an approve/deny prompt (unless auto-approve is on or
   the model asked for a read-only tool like `read_file`/`exiftool`), then run it inside the proot
   Ubuntu environment (`LinuxShell.exec`) and stream stdout back into the same bubble.
4. Feed the tool's output back to Venice AI as a `tool` role message and repeat until the model
   stops calling tools (capped at 15 tool calls per turn as a runaway-loop guard).

## Building it

You need Android Studio (or just the Android SDK command-line tools) — no NDK required.

```bash
cd android
./gradlew assembleDebug
```

That's it — no extra setup step. A `preBuild`-attached Gradle task (`fetchProotBinaries`) runs
`scripts/fetch-proot.sh` automatically, which downloads checksummed, prebuilt `proot` binaries
from Termux's official package repository for all three target ABIs and packages them into the
APK. Full explanation of why prebuilt binaries instead of an NDK cross-compile, and why the
binaries end up named `libproot.so` etc.: see `native/README.md`.

The one thing that needs tools beyond a stock JDK: the fetch script shells out to `curl`, `ar`,
`tar`, `sha256sum`, and `patchelf`. All five are standard on Linux/macOS dev machines and on the
GitHub Actions `ubuntu-latest` runner this repo's CI uses; if `patchelf` specifically is missing,
install it (`apt install patchelf` / `brew install patchelf`) — the task fails loudly rather than
silently producing a broken build.

### Signing a release build

`app/build.gradle.kts`'s release signing config reads four environment variables
(`HEXSTRIKE_KEYSTORE_PATH`, `HEXSTRIKE_KEYSTORE_PASSWORD`, `HEXSTRIKE_KEY_ALIAS`,
`HEXSTRIKE_KEY_PASSWORD`); without them, `assembleRelease` produces an unsigned APK.

## First run, from the user's side

1. **Onboarding** — acknowledge authorized-use-only, once.
2. **Settings** — paste a Venice AI API key, tap "Test connection & load models" to populate the
   model dropdown from `GET /models` (only models are shown; the app also surfaces which ones
   report `supportsFunctionCalling`, since tool-calling models are what you want for the agent to
   actually run commands rather than just talk about them).
3. **Settings → Linux environment** — tap "Install now" to download Ubuntu's official minimal
   base rootfs (`cdimage.ubuntu.com/ubuntu-base`, picked automatically for the device's CPU:
   arm64/armhf/x86_64) and run baseline `apt-get install` for common prerequisites (Go, pip,
   build tools). This is optional — skip it and the app is a plain Venice AI chat client.
4. **Settings → Security tools** — once the Linux environment is ready, tap "Install security
   tools" to apt/go/pip-install a curated default set of 30 tools chosen specifically for what
   actually works **without root** in a proot environment (sqlmap, hydra, medusa, ffuf, nuclei-
   adjacent web fuzzers, theHarvester, amass, RouterSploit, Ghidra headless, pwntools, and more —
   see `SecurityToolRegistry.recommendedCoreToolIds`). Per-tool pass/fail is shown; a failure
   doesn't block the rest, and anything that fails can be installed manually from the Terminal
   tab. The other ~54 tools in the registry beyond this default set (including nmap's more
   invasive scan types, masscan, etc.) are still fully usable by the AI agent — they just aren't
   pre-installed, and some of them have a real functional caveat, not just an install one (see
   below).
5. **Chat** — ask it to do something. It'll propose tool calls; approve or deny each one (or flip
   "Auto-approve tool executions" in Settings if you trust it / are scripting a CTF box you
   already own). While a tool is running, a foreground service notification keeps the process
   alive if you background the app mid-scan.
6. **Terminal** — a raw shell into the same Ubuntu environment, e.g. to `apt install` a tool that
   wasn't in the curated catalog, or to babysit a long-running scan directly.

## Networking limits inside proot (read this before picking tools)

proot's `-0` flag fakes root for *filesystem* checks (so `apt`, `useradd`-style setup, etc. work),
but it doesn't and can't grant real Linux capabilities — the process is still running under the
Android app's actual, unprivileged UID as far as the kernel is concerned. That matters because a
chunk of the pentesting toolbox needs `CAP_NET_RAW` to open raw sockets:

- **Won't work here**: masscan (raw packet I/O by design), `arp-scan` (needs a raw link-layer
  socket), nmap's `-sS`/`-sU` scan types, and anything wireless (aircrack-ng, bettercap — not in
  this app's registry at all for that reason).
- **Works fine**: nmap still works because it detects the missing privilege and *automatically*
  falls back to an unprivileged TCP connect scan (`-sT`) — slower and more visible to the target,
  but functional, which is why it's still in the default install set despite the caveat.
- **Also fine, and often overlooked**: rustscan (async `connect()`, not raw sockets), and
  essentially everything in the default 30-tool set — web app testing (sqlmap, ffuf, dalfox,
  XSStrike, ...), OSINT (theHarvester, Amass, Sublist3r, ...), credential attacks (hydra, medusa,
  patator — all regular protocol-level TCP), and scripting/analysis tools (radare2, Ghidra
  headless, pwntools) never touch raw sockets at all.

Tool descriptions in `data/tools/*.kt` call this out per-tool (nmap, nmap_advanced, masscan,
rustscan, arp_scan) so the model can explain a failure accurately instead of just seeing "command
not found" or a permission error and guessing why.

## What's genuinely finished vs. what needs real-device follow-up

Finished and should work as written: the Venice AI client (verified against the live API's actual
response shapes during development), the settings/onboarding/chat/terminal UI, the Room-backed
chat history, the tool-calling agent loop, the security-tools installer button, the foreground
service that keeps long scans alive in the background, the `fetchProotBinaries` Gradle task
(verified end-to-end — download, checksum, `.deb` extraction, and `patchelf` retargeting all
produce valid, correctly-linked ELF binaries for all three ABIs), and the ~84-tool registry's
command construction. All of this compiles cleanly and passes unit tests via the
`android-build.yml` GitHub Actions workflow, which runs the real Android/Kotlin toolchain on every
push.

Needs a real device to validate, because this sandbox can't run an Android emulator: whether proot
actually chroots successfully at runtime once installed on-device (the binaries are confirmed
valid and correctly linked, but "compiles and links" isn't the same as "a phone's kernel accepts
the ptrace/seccomp calls it makes" — this is inherently untestable without a physical device or
emulator), and the actual apt/go/pip installability of every tool in `data/tools/` on a real
Ubuntu-on-ARM-via-proot environment.

This real-device gap already caught one concrete bug: `packaging.jniLibs.useLegacyPackaging` was
left at the AGP default (`false`), which stores native libs uncompressed/page-aligned *inside* the
APK and loads them via `mmap` without ever extracting them to `applicationInfo.nativeLibraryDir` —
so `ProotManager`'s file-existence checks failed on every real device even though the libs were
correctly present in the APK and CI's zip-content check passed. Fixed by setting
`useLegacyPackaging = true` so PackageManager actually extracts the libs at install time; the
`android-build.yml` "verify proot native libs" step now also checks that every `lib/**/*.so` entry
in the built APK's zip listing is compressed (`Defl`) rather than stored uncompressed (`Stored`) —
that's the actual mechanism `useLegacyPackaging` controls, and it fails CI loudly instead of only
showing up on-device. Lesson: CI proving a native lib is *inside* the APK doesn't prove it's
*extracted at install time* — those are genuinely different checks, and the "verified
end-to-end" claim above about `fetchProotBinaries` only ever covered the former.
Ubuntu-on-ARM-via-proot environment — Ubuntu's official repos don't carry every tool
`hexstrike_server.py` assumes (that project targets a Kali-like host), so some entries fall back to
`go install`/`pip install`/upstream install scripts. Expect a handful to need manual `apt`/`pip`
fixes in Terminal on first use; that's why tool install failures are non-fatal and reported
individually rather than aborting the whole setup.
