# kemo-agent-app

<p align="center">
  <img src="kemo-agent-app.png" alt="kemo-agent-app logo" width="200">
</p>

<p align="center">
  <a href="readme.md">简体中文</a> · <strong>English</strong>
</p>

<p align="center">
  <strong>An Android companion that puts kemo-agent in your pocket.</strong>
</p>

<p align="center">
  Built for deployed kemo-agent instances and the Kemo gateway, kemo-agent-app brings connection, chat, tasks, files,<br>
  extensions, perception, and agent configuration to your phone, so your agent is no longer confined to the computer.
</p>

<p align="center">
  <a href="https://github.com/kesepain-KE/kemo-agent-app"><img src="https://img.shields.io/badge/version-1.1-blue" alt="version"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-Apache%202.0-green.svg" alt="license"></a>
</p>

---

## When the agent leaves the computer

kemo-agent can already keep working on the server: remembering what matters to you, moving complex plans forward, and waking at an agreed time to finish what was entrusted to it.

But it has always lived inside the computer.

Once you step out the door, only a small screen stands between you and it. Checking how a task is progressing, continuing a conversation, or confirming that the service is healthy all require finding a device with a browser.

kemo-agent-app is an attempt to make this relationship portable.

It is the Android companion of the kemo ecosystem. After connecting to your deployed kemo-agent and Kemo gateway, conversations, tasks, files, extensions, perception, runtime status, and agent configuration can all continue on your phone.

---

## One entry point, one Kemo ecosystem

| Scenario | What kemo-agent-app provides |
|---|---|
| On the go | Keep streaming conversations with your agent and inspect its reasoning and tool results without returning to the computer |
| Task progress | Review task plans, approve, pause, or abort steps; a home-screen widget keeps pending work visible |
| Quick checks | See whether the Kemo service is online, along with metrics such as sessions, queue, cache, and context |
| File exchange | Browse the workspace, upload files, download agent-generated results, and preview common formats |
| Capability management | Enable or disable extension and perception modules and inspect injected perception data |
| Configuration | Edit agent configuration on the phone, consistent with the web interface: models, provider, whitelists, and injection policies |
| Account switching | Save multiple connection accounts and switch between different agents or gateways at any time |
| Security | Unlock with biometrics or device credentials; an App password and automatic background lock protect your sessions |

These capabilities are not scattered features. They are different sides of the same goal: no matter where you are, your agent remains within reach.

---

## Connection: a reliable bridge

kemo-agent-app connects to your deployed kemo-agent through the bridge service. The connection page performs two-level authentication: a device token and a user account password.

- Credentials can be remembered, encrypted with the Android Keystore, for up to 7 days;
- Multiple accounts can coexist and be switched at any time from the Profile tab;
- An optional App password acts as the first line of defense when opening the app.

---

## Assistant: keep the conversation going

The chat screen is organized around real conversations rather than a single input box:

- Streaming output that reveals the agent's reply word by word;
- Markdown rendering with clear code blocks, lists, and quotes;
- Reasoning and tool calls (arguments, results, success or failure) are shown so the agent's actions stay understandable;
- Token usage, cache hits, and response time are displayed;
- Conversation history can be saved, switched, and deleted; clear the current conversation or manually compress context in one step;
- Attachments can be added and handed to the agent;
- Frequent actions (clear conversation, save history, compress context, save and start a new one) are grouped into quick actions.

---

## Tasks: every step stays visible

The tasks screen covers both task plans and scheduled tasks:

- Task plans can be filtered by status (pending, running, completed, failed) with step progress shown;
- Approve, pause, resume, or abort to keep control of the pace;
- Scheduled tasks can be reviewed and edited with clear scheduling parameters;
- A home-screen widget shows pending approvals and the latest task, tapping through to the tasks screen;
- The `kemo://task` deep link opens the task screen from any entry point.

---

## Files: the workspace in your pocket

The files screen separates uploaded files from agent-generated files:

- Browse server workspace directories at any depth;
- Upload local files, download or delete remote files;
- Common formats are previewed inside the app; files that cannot be parsed are downloaded and opened by compatible system apps;
- The download location can be customized or reset to the system default.

---

## Extensions and perception: capability switches at hand

The extensions screen lists the currently available extension and perception modules:

- View module names, scopes, and enabled states;
- Turn modules on or off (whitelist) and adjust injection policies directly;
- Inspect perception sources and injected data to see what the agent is "seeing".

---

## Status and configuration: everything under control

- The status screen shows the Kemo service online state and the latest runtime snapshot, including version, upstream connection, health, sessions, message queue, cache, context, congestion, and model service metrics;
- The configuration screen stays consistent with the current user configuration on the web: model and provider (base URL, API key, reasoning effort, streaming, multimodal inputs), subagent models, multimodal models, knowledge scope, shared skill/extension/perception/plugin whitelists, extension and perception injection policies, and automatic task-plan acceptance;
- The model list is fetched securely by the bridge service using saved Kemo credentials; provider keys are not stored on the phone.

---

## Security and privacy

- Connection credentials are encrypted with the Android Keystore;
- Unlock with biometrics or device credentials, or set an App password;
- The app locks automatically after 5 minutes in the background, and security settings require identity verification before changes;
- Sensitive operations such as model lists and configuration changes are authorized server-side; the app acts only as a trusted entry point.

---

## Getting started

### Requirements

- Android Studio (latest stable)
- JDK 17
- Android SDK 36 (compileSdk / targetSdk)

### Clone and build

```bash
git clone https://github.com/kesepain-KE/kemo-agent-app.git
cd kemo-agent-app
```

Open the project in Android Studio and run it on a device, or build from the command line:

```bash
./gradlew assembleDebug
```

The APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### Connect your agent

1. Install and open the app;
2. On the connection screen, enter the bridge service address (the emulator default for the host machine is `http://10.0.2.2:8742`), the device token, username, and password;
3. After connecting, you can start chatting and managing tasks.

> You will need to deploy and run [kemo-agent](https://github.com/kesepain-KE/kemo-agent) and the [kemo-adapter-api](https://github.com/kesepain-KE/kemo-adapter-api) gateway first, and configure an account and device token.

---

## What we want it to become

kemo-agent-app is not trying to replace the web interface or the command line.

It aims to be a natural extension of the Kemo ecosystem:

- when you need a full workspace, return to the web;
- when you need quick operations, open the command line;
- when you need something portable, reach for your phone.

No matter which entry point you use, it connects to the same agent, the same history, and the same trust. The goal is to let your agent become a companion you can talk to at any time, rather than a program living inside one computer.

---

## Current status

Current version: `1.1`

`1.1` brings a round of experience upgrades on top of 1.0: the chat screen now renders math formulas and media cards, lets you stop generation, regenerate the previous reply, and send guidance while the agent is responding; a background poller pushes a notification when the reply completes; the settings screen lets you pick an image or video as the global background; task and module cards open a full detail sheet on tap; file previews extend to audio, video, and PDF; the model picker in the configuration screen becomes searchable; and a new About page checks GitHub Releases and installs new versions right inside the app.

`1.0` is the first official release of kemo-agent-app, covering the mobile foundation of the Kemo ecosystem: connection with two-level authentication, streaming chat, task plans and scheduled tasks, file management, extension and perception toggles, runtime status, agent configuration, secure unlock, and a home-screen widget.

Available today:

- five main tabs: Assistant, Tasks, Files, Extensions, Profile;
- streaming chat with Markdown rendering, showing reasoning and tool-call details;
- conversation history with save, switch, delete, and context compression;
- task plans with status filters and approve/pause/resume/abort, plus scheduled task review and editing;
- a home-screen task widget and `kemo://task` deep links;
- separated management of uploaded and generated files, previews, and a customizable download directory;
- extension and perception module toggles (whitelists) with injected-data inspection;
- service health and runtime metric snapshots;
- agent configuration editing consistent with the web interface (provider, subagent models, multimodal models, whitelists, injection policies);
- secure model-list fetching and selection through the Kemo protocol;
- Chinese and English UI, light/dark/system themes, accent colors, and dynamic color;
- biometric unlock, App password, and automatic background lock;
- multiple saved accounts with quick switching.

Areas still being refined:

- adaptation for more device form factors and foldables;
- push notification completeness and reliability;
- preview support for more file types;
- ongoing performance and battery optimization.

If you are trying an early release, reports about problems and usability feedback are all welcome.

---

## Related projects

- [kemo-agent](https://github.com/kesepain-KE/kemo-agent)  
  The core agent framework this app connects to: a local multi-user Agent Runtime with conversation, memory, tasks, knowledge, extensions, and perception.

- [kemo-adapter-api](https://github.com/kesepain-KE/kemo-adapter-api)  
  A model-service adapter gateway for kemo-agent; kemo-agent-app bridges through it with two-level authentication.

- [kemo-graph](https://github.com/kesepain-KE/kemo-graph)  
  A knowledge-graph and RAG retrieval project that can be attached to kemo-agent as an external document station.

---

## Maintainer

[@kesepain](https://github.com/kesepain-KE)

---

## Contributing

kemo-agent-app is still at a very early stage. Bug reports, usability feedback, documentation improvements, and code contributions are all welcome.

Recommended workflow:

1. Fork this repository.
2. Create a feature branch.
3. Make the change and perform the necessary verification.
4. Open a Pull Request explaining what changed and why.

---

## License

This project is licensed under the [Apache License 2.0](LICENSE).
