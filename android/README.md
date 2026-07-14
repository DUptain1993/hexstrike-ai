# Vulnr-Bot — Android

A standalone Android client for AI-driven security testing. Add a
[Venice AI](https://venice.ai) API key, pick a model, and chat with an agent that can run real
security tools (nmap, sqlmap, ffuf, hydra, and ~80 others) as **real root** inside an existing
on-device Ubuntu **chroot** — no separate server, no Termux install required.

**Requires a rooted device with an Ubuntu chroot already set up** (default path
`/data/local/chroot/ubuntu`, configurable in Settings). The app enters that chroot via `su` at
runtime — it does not build the chroot for you, and it does not run on non-rooted devices. Earlier
versions bundled a proot rootfs to avoid needing root; that was dropped because proot's fake-root
can't open raw sockets, orphans processes when killed, and needs fragile native-lib packaging —
all problems a real root + chroot simply doesn't have.

**Only use this against systems you own or are explicitly authorized to test.** The app requires
you to acknowledge this before first use (see `ui/onboarding`), but that acknowledgment doesn't
change your actual legal obligations — it's on you.

## Why this exists / how it relates to the rest of the repo

The root of this repository is `hexstrike_server.py` + `hexstrike_mcp.py`: a Flask server plus an
MCP wrapper that lets desktop AI clients (Claude Desktop, Cursor, etc.) drive ~90 pentesting tools
over HTTP. This Android app is a from-scratch reimplementation of that same idea for a phone: it
doesn't call the Python server at all. Instead:

- **The LLM** is Venice AI's OpenAI-compatible API instead of Claude/GPT via MCP.
- **The tool execution environment** is an existing on-device Ubuntu chroot entered as real root
  (via `su`) instead of the host machine the Python server runs on.
- **The tool catalog** (`data/tools/*.kt`) is a Kotlin port of the same command-building logic
  found in `hexstrike_server.py`'s `/api/tools/*` routes — same flags, same defaults, same tools
  where practical for a mobile ARM/Ubuntu environment.

## Architecture

```
ui/            Jetpack Compose screens (onboarding, settings, chat, terminal) + ViewModels
data/agent/    AgentOrchestrator: the tool-calling loop between Venice AI and the tool executor
data/venice/   Venice AI API client (OpenAI-compatible chat completions, streaming, /models)
data/tools/    ~84 security tool definitions (command templates + JSON schemas) + ToolExecutor
data/linux/    ChrootManager (su + chroot invocation), LinuxShell/Session, root+chroot state
data/settings/ Encrypted, persisted user settings (API key, model, chroot path, toggles)
data/db/       Room persistence for chat history
```

Data flow for a single user turn (`AgentOrchestrator.runTurn`):

1. Send the conversation + the full tool catalog's JSON schemas to Venice AI as a streaming
   `chat/completions` request.
2. Stream the reply into the chat UI token-by-token.
3. If the model requests a tool call, resolve it to an actual shell command
   (`ToolExecutor.prepare`), show the user an approve/deny prompt (unless auto-approve is on or
   the model asked for a read-only tool like `read_file`/`exiftool`), then run it as root inside
   the Ubuntu chroot (`LinuxShell.exec` → `ChrootManager`) and stream stdout back into the same bubble.
4. Feed the tool's output back to Venice AI as a `tool` role message and repeat until the model
   stops calling tools (capped at 15 tool calls per turn as a runaway-loop guard).

## Building it

You need Android Studio (or just the Android SDK command-line tools) — no NDK, no native code.

```bash
cd android
./gradlew assembleDebug
```

That's it. The APK bundles no proot binary and no rootfs — the Linux environment is the chroot
that already exists on the target device, so there's nothing to fetch or cross-compile at build
time.

## On-device setup

1. Root the device and set up an Ubuntu chroot (any standard rooted chroot works; the mount layout
   mirrors a typical `busybox` bind-mount script — `/dev`, `/dev/pts`, `/proc`, `/sys`, `/dev/shm`,
   `/sdcard`). Default path is `/data/local/chroot/ubuntu`; change it in Settings if yours differs.
2. Install the app and open **Settings › Linux environment**. Tap **Test root & chroot** — the
   first `su` call triggers your superuser manager's grant prompt. It prints the raw `id` output
   and the chroot probe so you can see exactly what's detected.
3. Once it shows **Ready**, tap **Install security tools** to run baseline `apt` setup (git, go,
   pip3, …) plus the default tool set inside the chroot.

### Signing a release build

`app/build.gradle.kts`'s release signing config reads four environment variables
(`VULNRBOT_KEYSTORE_PATH`, `VULNRBOT_KEYSTORE_PASSWORD`, `VULNRBOT_KEY_ALIAS`,
`VULNRBOT_KEY_PASSWORD`); without them, `assembleRelease` produces an unsigned APK.

## First run, from the user's side

1. **Onboarding** — acknowledge authorized-use-only, once.
2. **Settings** — paste a Venice AI API key, tap "Test connection & load models" to populate the
   model dropdown from `GET /models` (only models are shown; the app also surfaces which ones
   report `supportsFunctionCalling`, since tool-calling models are what you want for the agent to
   actually run commands rather than just talk about them).
3. **Settings → Linux environment** — set your chroot path if it isn't the default, tap
   **Test root & chroot** to confirm root + the chroot are detected, then **Install security
   tools** to run baseline `apt-get install` for common prerequisites (Go, pip, build tools). This
   is optional — skip it and the app is a plain Venice AI chat client.
4. **Settings → Security tools** — once the environment is Ready, tap "Install security tools" to
   apt/go/pip-install a curated default set of 30 tools (sqlmap, hydra, medusa, ffuf, theHarvester,
   amass, RouterSploit, Ghidra headless, pwntools, and more — see
   `SecurityToolRegistry.recommendedCoreToolIds`). Per-tool pass/fail is shown; a failure doesn't
   block the rest, and anything that fails can be installed manually from the Terminal tab. The
   other ~54 tools in the registry are still fully usable by the AI agent — they just aren't
   pre-installed.
5. **Chat** — ask it to do something. It'll propose tool calls; approve or deny each one (or flip
   "Auto-approve tool executions" in Settings if you trust it / are scripting a CTF box you
   already own). While a tool is running, a foreground service notification keeps the process
   alive if you background the app mid-scan.
6. **Terminal** — a raw shell into the same Ubuntu environment, e.g. to `apt install` a tool that
   wasn't in the curated catalog, or to babysit a long-running scan directly.

## Capabilities (real root, real chroot)

Because tools run as **real root** inside a real chroot, the whole toolbox is available — including
the raw-socket tools that proot couldn't support: masscan, nmap `-sS`/`-sU`, `arp-scan`, etc. all
work (subject to the device actually exposing a usable network interface). The default 30-tool set
is just a broadly-useful starter selection, not a rootless-only subset; anything else in the
registry can be installed on demand from the Terminal tab or asked for directly.

Two runtime notes:

- **No controlling terminal.** The chrooted shell runs without a pty, so you'll see harmless
  `bash: cannot set terminal process group` / `no job control` warnings, and Ctrl-C in the Terminal
  tab is delivered as a best-effort ETX byte rather than a real SIGINT.
- **Process cleanup on timeout is best-effort.** A one-shot command is tagged with a JOBID comment
  so its bash and any `apt`/`dpkg` it spawns can be killed on timeout; a scan tool's own child
  processes (e.g. an `nmap` mid-scan) may briefly linger. Full process-group teardown is a known
  future improvement.

## What's finished vs. what needs a device

Finished and should work as written: the Venice AI client (verified against the live API), the
settings/onboarding/chat/terminal UI, the Room-backed chat history, the tool-calling agent loop,
the root+chroot detection and `su`/`chroot` command construction (base64-injected commands,
idempotent persistent mounts, JOBID-based kill), the security-tools installer, and the ~84-tool
registry's command construction. All of this compiles and passes unit tests via the
`android-build.yml` GitHub Actions workflow on every push.

Needs a real rooted device to validate, because this sandbox can't run a rooted Android emulator:
the actual `su` grant + chroot entry at runtime (surfaced by the **Test root & chroot** diagnostic
in Settings, which prints the raw `id`/probe output so failures are diagnosable without guesswork),
and the apt/go/pip installability of each tool in a real Ubuntu-on-ARM chroot. Ubuntu's repos don't
carry every tool `hexstrike_server.py` assumes (that project targets Kali), so some entries fall
back to `go install`/`pip install`/upstream scripts; expect a handful to need manual fixes in
Terminal, which is why tool install failures are non-fatal and reported individually.
