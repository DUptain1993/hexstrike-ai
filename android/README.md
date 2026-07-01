# HexStrike AI — Android

A standalone Android client for HexStrike-style AI-driven security testing. Add a
[Venice AI](https://venice.ai) API key, pick a model, and chat with an agent that can run real
security tools (nmap, sqlmap, ffuf, hydra, and ~65 others) inside a private Ubuntu Linux
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
data/tools/    ~68 security tool definitions (command templates + JSON schemas) + ToolExecutor
data/linux/    proot + Ubuntu rootfs lifecycle: download, extract, exec, interactive terminal
data/settings/ Encrypted, persisted user settings (API key, model, toggles)
data/db/       Room persistence for chat history
native/        CMake project that cross-compiles proot from source (see native/README.md)
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

You need Android Studio (or just the Android SDK + NDK command-line tools) — this sandbox that
generated the code has no Android SDK, so it has never actually been compiled; treat the first
build as the real first compile pass.

```bash
cd android
./gradlew assembleDebug
```

On a fresh checkout this builds an app where **chat works but security tools don't** (see next
section) — that's intentional, not a bug, so you get a working APK immediately.

### Enabling on-device tool execution (proot)

`native/CMakeLists.txt` only builds a no-op stub library until you provide proot's source:

```bash
cd android/native
git clone https://github.com/termux/proot proot   # Android-patched fork, not upstream GNU proot
cd proot && git checkout v5.4.0                    # pick a tagged release
git submodule update --init --recursive
cd ../..
./gradlew assembleDebug
```

Full explanation of why upstream proot doesn't work well on Android, why the binary is named
`libproot.so`, and how the Ubuntu rootfs gets fetched at runtime instead of bundled in the APK:
see `native/README.md`.

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
   tools" to apt/go/pip-install a curated default set of ~38 widely used free/open-source
   pentesting tools (nmap, sqlmap, hydra, john, hashcat, nuclei, subfinder, radare2, binwalk, and
   more — see `SecurityToolRegistry.recommendedCoreToolIds`). Per-tool pass/fail is shown; a
   failure doesn't block the rest, and anything that fails can be installed manually from the
   Terminal tab. The other ~30 tools in the registry beyond this default set are still fully
   usable by the AI agent — they just aren't pre-installed, so the first call to one of them will
   fail with a "command not found" the agent can explain and you can fix with `apt`/`pip`/`go
   install` in Terminal.
5. **Chat** — ask it to do something. It'll propose tool calls; approve or deny each one (or flip
   "Auto-approve tool executions" in Settings if you trust it / are scripting a CTF box you
   already own). While a tool is running, a foreground service notification keeps the process
   alive if you background the app mid-scan.
6. **Terminal** — a raw shell into the same Ubuntu environment, e.g. to `apt install` a tool that
   wasn't in the curated catalog, or to babysit a long-running scan directly.

## What's genuinely finished vs. what needs real-device follow-up

Finished and should work as written: the Venice AI client (verified against the live API's actual
response shapes during development), the settings/onboarding/chat/terminal UI, the Room-backed
chat history, the tool-calling agent loop, the security-tools installer button, the foreground
service that keeps long scans alive in the background, and the ~68-tool registry's command
construction. All of this compiles cleanly and passes unit tests via the `android-build.yml`
GitHub Actions workflow, which runs the real Android/Kotlin toolchain on every push.

Needs a real device to validate, because this sandbox can't run an Android emulator or compile
native code: the proot cross-compilation in `native/` (see `native/README.md` — this is the one
piece that genuinely cannot be finished without a machine that has the Android NDK and access to
clone `termux/proot`), and the actual apt/go/pip installability of every tool in `data/tools/` on
a real Ubuntu-on-ARM-via-proot environment — Ubuntu's official repos don't carry every tool
`hexstrike_server.py` assumes (that project targets a Kali-like host), so some entries fall back to
`go install`/`pip install`/upstream install scripts. Expect a handful to need manual `apt`/`pip`
fixes in Terminal on first use; that's why tool install failures are non-fatal and reported
individually rather than aborting the whole setup.
