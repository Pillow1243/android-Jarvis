# Architecture

## Execution boundary

```text
Text / foreground speech / quick action / saved routine
                        │
                 normalize + resolve
          ┌─────────────┴─────────────────┐
     complete local grammar       free-form Gemini planner
     (no network request)          bounded SSE function turns
          └─────────────┬─────────────────┘
                immutable Call values
                        │
       closed schema → permission → local choice → risk gate
                        │
           one-shot foreground commit → Android handler
                        │
      actual Receipt ───┬── local detail: UI + optional speech
                       ├── sanitized journal: SQLite
                       └── bounded status/metrics: functionResponse
```

The provider cannot run code, open arbitrary intent schemes, invoke UI gestures, change the risk level, mark a step verified, or suppress a confirmation. Unknown tools and extra arguments fail validation. The Android process owns approvals. A financial, authentication, account deletion or destructive file operation has **no handler**.

### Modules

| Code | Responsibility |
|---|---|
| `MainActivity`, `ui/Kit`, `Console`, `Controls`, `SignalField` | Bilingual native UI, six destinations, modal gates, foreground state, system insets, visualizations and real counters. Signal rendering uses batched paths; stops off-screen, respects motion preferences and Android animation disablement. |
| `core/LocalPlanner`, `Text`, `Call`, `Spec`, `PermissionPolicy` | Deterministic multi-step grammar, Persian normalization, typed input/output contracts, enum/length/control-character checks and conditional runtime-permission requirements. Message bodies do not pass through name normalization. |
| `agent/Agent` | Single main-thread state machine with asynchronous provider and device reads. Task generation and step identity guard every callback. One-shot commit requires a live foreground owner. |
| `ai/Gemini`, `Provider` | Official REST model metadata, function-calling probe, full model content/signature round-trip, streamed responses, response-size and budget checks. The test adapter is injected **only in instrumentation**. |
| `tools/ToolBox`, `device/AppIndex`, `People` | Tool descriptors registered with handlers. Read-only package discovery, contacts lookup and explicit platform handoffs. No downloaded tools. |
| `voice/Voice` | Android `SpeechRecognizer` partial/final/confidence/RMS events and interruptible `TextToSpeech`. On API 34+, requests language detection/switching where implemented. |
| `automation/Reminders`, `ReminderReceiver` | Time-zone-aware reminders, immutable pending intents, persisted alarm registrations, private notifications and re-registration on boot. |
| `accessibility/WindowReceiptService` | Opt-in, time-limited expected-window receipt; manual-only, one-scroll Display walkthrough. Cannot consume text or receive model instructions. |
| `memory/Store`, `security/KeyVault` | Migrated SQLite schema, retention controls, task/step transition journal and daily budgets; credential storage is a separate Keystore-backed domain. |
| `gateway/` | Optional independently deployable OIDC-authenticated inference gateway. Does not execute Android tools. |

### Planning, repair and context

Nexus only executes a local compound instruction if it recognizes **every** clause. Otherwise it submits the user's instruction to the configured provider or explains why cloud planning is unavailable. Local shortcuts go through the same pipeline. The installed-app guard is a real conditional: a failed availability check stops dependent local steps. The Gemini loop may select subsequent actions based on prior `functionResponse` observations. A completed model turn with `STOP` is required before any streamed action is considered.

A task can use at most six provider rounds and ten tool steps, with at most five calls in a returned batch. Calls are executed sequentially. Whole model content parts—including opaque thought signatures and function IDs—are preserved within the turn history. These signatures are not displayed or logged. Incomplete streams never dispatch a partially decoded action. Only a transient server failure receives one bounded planning retry. A 429 stops the run; a user denial or uncertain side effect is not something a model may retry.

A canonical name/argument fingerprint blocks duplicates within a task. A generation change discards stale network, dialog, query and speech callbacks. A timed-out external action stays uncertain. After an intent handoff the next dispatch waits for foreground re-entry. Cancellation stops future work; it does not reverse something another app has already accepted.

Memory retrieval includes explicit aliases and shortcut expansion, matched notes (local presentation only), a bounded recent tool-state summary, and a short in-process prior dialogue when enabled. Last app/destination and clarification slots resolve supported follow-ups. If no target is known, Nexus asks rather than inspecting arbitrary prior apps. Raw conversations are not saved as an automatic long-term transcript.

### Evidence

`requested` records the plan, not an effect. `attempted` records the one-shot dispatch boundary. `executed` means the handler returned an observed positive OS or storage result. The terminal receipt distinguishes:

- **verified:** a provider row, OS reading, local read-back, alarm registration, or scoped window/scroll event was actually observed. An observed window is not evidence of a later send, call connection or setting change.
- **handed_off:** Android accepted an intent; its outcome inside the other app is unknown.
- **failed / declined / interrupted / skipped:** no completion claim. The full task can be partial when some steps produced receipts and others failed.

SQLite retains state transitions, timestamps, tool IDs and non-sensitive evidence codes—not arguments, message texts or contact numbers. Long-lived history is capped at 150 tasks. Recovery marks open tasks/steps interrupted and never reconstructs executable arguments. With history disabled, the active trace is in memory only. The final action report is built from local receipts; a model-generated “success” sentence cannot replace it.

### Extending safely

1. Define a unique `Spec` with exact string/enum fields, risk, permission, timeout, verification method, category and cloud eligibility. Each spec exposes its JSON input declaration and output schema.
2. Register its handler with `ToolBox.add`. Read private data using `Invocation.query` after permission checks. For a side effect, supply the exact final preview to `Invocation.commit`; never launch from a worker or a model callback.
3. Return a `Receipt` with a safe code and bounded metrics. Separate user-visible detail from output allowed back to the provider. If the OS has no completion signal, return a handoff.
4. Add bilingual labels, tests for invalid fields, denial, cancellation, duplicates and actual observable results. Update the managed gateway allowlist only if the tool is safe for cloud selection.

### Research decisions

The [OpenJarvis orchestrator](https://github.com/open-jarvis/OpenJarvis/blob/c3ed63f1d2a2c0c1f7dabbc69050e1968a11dc10/src/openjarvis/agents/orchestrator.py) and [native ReAct implementation](https://github.com/open-jarvis/OpenJarvis/blob/c3ed63f1d2a2c0c1f7dabbc69050e1968a11dc10/src/openjarvis/agents/native_react.py) informed the separation of inference, registry, execution and observation. Nexus does not port their Python runtime, shell tools, learned skills or local models.

Google lists `gemini-3.1-flash-lite` as a stable model supporting function calling and structured outputs [1](https://ai.google.dev/gemini-api/docs/models/gemini-3.1-flash-lite). The REST function-calling protocol uses preserved thought signatures [2](https://ai.google.dev/gemini-api/docs/generate-content/function-calling). Google's pricing table lists a free tier, not unlimited access [1](https://ai.google.dev/gemini-api/docs/pricing). Documentation was checked November 18, 2026; **availability for an individual account is only established by its live probe**. The target is API 36, the Play submission baseline for new phone apps/updates at this date [1](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en).
