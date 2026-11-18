# Verification and release checklist

## Candidate actually delivered — 18 November 2026 (UTC)

**[Download the verified debug APK](https://github.com/Pillow1243/android-Jarvis/releases/download/v1.0.0-debug.1/JARVIS-NEXUS.apk)** · [Public, unauthenticated download read-back](https://github.com/Pillow1243/android-Jarvis/actions/runs/44386951244/job/135093989229). Not a production approval. [Passing Android verification run](https://github.com/Pillow1243/android-Jarvis/actions/runs/44384092541) · [tested build inputs](https://github.com/Pillow1243/android-Jarvis/tree/113afbd15894ba002330399a0256f1cbe09110b1) · [installed binary](../delivery/JARVIS-NEXUS.apk).

- `ai.nexus` **1.0.0 (1)** · Android 8.0+ (min 26), target 36. The signed APK is **397,905 bytes**. SHA-256: `cdd3b85458b5b5149095048b1b294c1ddec4c461c9adeef0fab7ef18ae5f9848`. Android Debug certificate SHA-256: `d9deeb1fe08db35015c974fe537cf7a39bbc48b2d3c6097e109a79ab83f0c11a`. This is not a release key.
- API 35 installed the CI SDK-built and debug-signed output, launched `MainActivity` and ran **169 assertions** in the installed integration suite. `adb pull` of the installed `base.apk` matched that build byte-for-byte. No fatal app exception was found in the captured AndroidRuntime window. API **26 and 36** each installed the **same hash** without the test APK, completed a visible two-step task and recovered without replay after being force-stopped at the exact-number approval screen.
- **50 host checks** cover the bilingual grammar, time/phone normalization, aliases/misspellings, validation and permission policy. **Three gateway tests** cover actual signed-token validation, persistent atomic budgets and bounded requests. The capture built **both APKs using the SDK-only lane** (no Maven dependencies) and executed the shared host checks. The ordinary Gradle build and JUnit suite passed in [an earlier job](https://github.com/Pillow1243/android-Jarvis/actions/runs/44382489594) with the same production application sources; that earlier emulator run did not pass. These are not 50 device scenarios or a production security audit.

**What was actually visible:** Chrome opened for the two-step YouTube search; Maps opened for the Persian pharmacy request; Google Messages opened for the SMS draft. The email intent was observed going to the reviewed `com.google.android.gm` package, but the visible surface was still Messages. **No Gmail mailbox, email creation/delivery, route selection, SMS send or call connection was verified.** The final `run.txt` count is 169. `instrumentation.txt` is an incremental record (159 assertions before the last accessibility scenario); `accessibility.txt` records the real system opt-in, observed Display window and scroll, and opt-out.

The actual final screenshots were inspected in both directions: the Persian Home shows its RTL controls and actual two-step result; landscape shows the centered compact voice control above navigation. The English Agent shows real receipts, while a fresh Activity screen and Memory screen show their empty states. This is not exhaustive font-size/OEM usability testing.

### Release gates

| Gate | Debug evidence | Remaining boundary |
|---|---|---|
| A · Engineering | Reproducible Gradle and SDK build instructions, unit and device suites, bounded execution, fault recovery, byte-identical install proof. | Native **Java/Views/SQLite** rather than Kotlin/Compose/Room. The separately signed local SDK build is **not** substituted for the CI-tested file. |
| B · AI | Real Android HTTPS negative probe: `configuration`; runner negative HTTP **400**. Scripted provider turns used real device tools, matched call IDs/signatures, skipped dependent actions on failure, and replanned from actual results. | **Live authenticated Gemini NOT verified**; no QA key supplied. No claim about account eligibility, real model quality/latency, sustained quota or authenticated managed-tenant compatibility. |
| C · Android | Real package/contacts/providers/intents, grants/denials, chooser/approval, microphone permission, recognizer availability, AlarmManager, three emulator APIs and process restart. See `instrumentation.txt` for the precise destinations reached; no calls/messages sent. | No physical phone attached (`adb devices -l` empty). A handoff is not a sent email, completed phone call, selected route or an exact-time delivered notification. |
| D · UX | Actual six-screen captures, Persian RTL, English, portrait/landscape and compact 360dp input/restart. Standby and scroll measured separately. | **Handset performance and acoustic sign-off open**. Software-renderer scroll missed frame deadlines; no 60fps or long-session battery claim. |
| E · Security | Encrypted BYOK round-trip, no plaintext key in preferences, restricted manifest, test-only fixture excluded from APK, exact approvals/denials, interruption/no replay, gateway tests. | No independent audit, production signing key, deployed tenant, or Play accessibility approval. |
| F · Delivery | This debug file is verified against both compatibility receipts, the installed base APK, signature and restricted manifest. Source and rebuild instructions accompany it. | Test distribution only; no production signing or store publication. |

### Measurements, not performance promises

| Test | Observed on this candidate |
|---|---|
| Single cold `am start -W` TotalTime | API 26 **946 ms**, API 35 **2745 ms**, API 36 **3604 ms** (different AVDs/runners). API 35 post-instrumentation relaunch: **2935 ms**. |
| Foreground memory after swipe sequence, API 35 | PSS **79,918 KB**, RSS **236,872 KB**. Not an endurance/leak test. |
| Settled standby, then six swipes | **0 frames** in the five-second idle window. Scroll: **38 frames**, **37 (97.37%)** janky, p50 **150ms**, p95 **200ms**. 1080×2340, 420 dpi, debug, SwiftShader. |
| Stock Settings reference | Same AVD and gesture script: **57 frames**, **45 (78.95%)** janky, p50 **65ms**, p95 **250ms**. Different view content; this does not waive the handset gate. |
| On-device invalid-credential request | **472 ms**, UID Δ received **5625 B**, transmitted **1547 B**. No positive model call implied. |
| Accessibility via native settings | Verified: opt-in via in-app disclosure, Android settings switch and OS confirmation. · Open Display: window_visible · Manual Display walkthrough: display_scrolled · Verified: service disconnected after native opt-out. |

## How to reproduce

Ordinary CI uses Gradle. The signed capture uses Android SDK 36, Build Tools 35.0.0 and JDK 17 directly; it does not download Android runtime dependencies or retry an external Maven 429. The installed tests are not skipped in that lane.

```sh
# Reproduce the capture lane after installing the SDK and JDK:
export BUILD_TOOLS="$ANDROID_HOME/build-tools/35.0.0"
scripts/sdk-build.sh && scripts/test-core.sh && scripts/sdk-device-tests.sh
NEXUS_APK=build/sdk-debug/JARVIS-NEXUS.apk NEXUS_TEST_APK=build/sdk-test/NEXUS-TEST.apk bash scripts/device-test.sh
```

```sh
./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest
node --test gateway/security.test.mjs
# On a disposable Android 15 / API 35 Google APIs x86_64 AVD, not a personal phone:
bash scripts/device-test.sh
# Black-box test against the delivered APK, on clean API 26 / API 36 AVDs:
NEXUS_APK=delivery/JARVIS-NEXUS.apk python3 scripts/smoke-device.py
# Independently verify the delivered archive against the recorded device receipts:
ANDROID_HOME=/path/to/sdk JAVA_HOME=/path/to/jdk-17 bash scripts/verify-delivery.sh
```

`device-test.sh` installs the real application plus a **separate shell-restricted test fixture**. That fixture writes two fictitious contacts on the disposable emulator and reads them back as a precondition. A shell-restricted Activity gives only this fixture a foreground owner; setup must read back both rows. A bounded retry can remove/reseed only the fixture’s own two stable IDs on this disposable AVD. The app itself never writes contacts. The test prepares a dialer UI with a reserved fictional number; it never presses Call or Send. The black-box compatibility test installs only the unmodified candidate APK, drives visible controls, kills the process at a pending approval, and verifies a non-replayed interrupted receipt after relaunch.

`NexusInstrumentation` exercises UI input and navigation with Android's `UiAutomation`, real activities, provider lookups, SQLite and Keystore, confirmation/denial, OS permissions, reminders, cancellation, rotation and lifecycle. Network protocol tests **explicitly inject a scripted provider** into an otherwise real agent and real device tools. Those tests do not prove a live Gemini account works. `scripts/provider-check.py` separately records a public endpoint negative test; successful live model metadata + function calling is attempted **only** when a maintainer provides the repository QA secret `JARVIS_TEST_GEMINI_API_KEY`. It never logs the secret or response bodies.

A user's own device can perform the real account-specific check in **Settings → Gemini provider → Save & test function calling**. The app does not label a key verified until the chosen model actually returns the expected nonce in a function call. Do not send API keys through chat or GitHub issues.

## Evidence index

Raw output from the captured build is under [`evidence/android/`](evidence/android/):

- `run-source.txt`, `compile.txt`: tested source revision, CI run and build result.
- `install.txt`, `apk.sha256`, `signature.txt`: installer output, exact installed/base APK comparison, SHA-256 and signer verification.
- `instrumentation.txt`, `run.txt`, `contact-fixture.txt`: scenarios, assertion count and provider precondition.
- `compat-26.json`, `compat-36.json`: exact candidate hash, booted build fingerprint and black-box results.
- `provider.json`, `device-network.txt`: the runner’s public endpoint check and the installed app’s own HTTPS negative probe. The second file includes UID byte-counter deltas (not packet payloads).
- `accessibility.txt`: what the device actually offered through the OS opt-in and opt-out UI; distinguish `window_visible`, `display_scrolled` and unsupported outcomes.
- `01-home.png`, `agent.png`, `activity.png`, `tools.png`, `memory.png`, `settings.png`, `verified-sequence.png`, `persian-home.png`, `landscape.png`: actual emulator captures, not design mockups.
- `cold-start.txt`, `relaunch.txt`, `memory.txt`: `am start -W` and post-instrumentation foreground `dumpsys meminfo`.
- `idle-frames.txt`, `frames.txt`: five seconds after standby settles vs. six scripted Home swipes. Ignore latency percentiles when no idle frames were rendered. `reference-settings-frames.txt` applies the same six swipes to stock Settings on the same software renderer; it is context, not an equivalent UI benchmark.
- `power.txt`, `network.txt`, `runtime-errors.txt`: short OS snapshots from a disposable emulator, not handset endurance or radio measurements.

## Boundaries of the evidence

A local planner test is not a live model test. A ContactsProvider row is not a connected phone call. A scheduled OS alarm is not proof of exact-time notification delivery. A software emulator with no spoken utterance is not an acoustic Farsi/English quality test. No production credential, release-signing key, physical handset, or Play approval is assumed. Long-session battery behavior and recognizer noise tolerance require separate physical-device testing. The data export, third-party app behavior, OEM accessibility event delivery and managed deployment also warrant device/customer-specific acceptance testing.

## Remaining qualification on real equipment

1. **Credentialed planning:** provision a low-budget test project in the provider's own console. Use the secure app settings or the named CI secret, never chat, Git, a shell argument saved to history, or a screenshot. Verify metadata + nonce, then try a free-form conditional request and inspect the actual receipts. A successful nonce alone is not a multi-turn device test. Remove/rotate the test key afterward.
2. **Handset speech and lifecycle:** test both languages using the phone's installed recognizer/TTS, in quiet and noisy conditions. Check partials, corrected words/numbers, exact confirmation, stop, lock/background, and foreground-only follow-up. Use fictitious recipients; never press the final system Call/Send button for a smoke test.
3. **Performance and power:** on a midrange physical phone, record OS/build, resolution, refresh rate, app build type and thermal state. Sample multiple cold/warm starts. Reset `adb shell dumpsys gfxinfo ai.nexus reset`, scroll and switch tabs, then record `adb shell dumpsys gfxinfo ai.nexus` and `adb shell dumpsys meminfo ai.nexus`. Run a longer voice/network session and idle comparison before claiming smoothness or battery life. Do not run the contact-seeding CI harness on a personal phone.
4. **Commercial operations:** deploy the managed gateway behind TLS against the operator's real issuer, enforce per-user/project quotas and revocation, review consent/retention and Play policy, and sign a non-debuggable production build with the operator's protected key. Re-verify the *new* signed binary; debug-APK evidence does not transfer automatically.

## Earlier runs and promotion policy

- The Android 15 suite passed on [an earlier candidate](https://github.com/Pillow1243/android-Jarvis/actions/runs/44372293896), but Android 8's compact-screen approval flow did **not** complete. It was not accepted as the final APK. [The next three-image run](https://github.com/Pillow1243/android-Jarvis/actions/runs/44374289217) verified focused input, the exact approval and safe restart on both API 26 and 36.
- [The subsequent run](https://github.com/Pillow1243/android-Jarvis/actions/runs/44375866206) exercised the real system accessibility switch, a matching Display scroll event, system opt-out, the on-device negative TLS client path and the updated render path.
- [Another build](https://github.com/Pillow1243/android-Jarvis/actions/runs/44377485192) stopped at a `SecurityException` in **fixture setup**, before application instrumentation; no pass was inferred from that run. Setup now uses a foreground test-only owner and synchronous provider read-back. Failures retain diagnostics separately, so a failed candidate cannot replace the last verified binary/evidence pair.

The email handoff tests also exposed a case where Android accepted `mailto:` but the visible UI was Messages. The app now excludes SMS-only handlers, prefers dedicated email apps and displays the exact target package before a package-limited dispatch. An emulator without an eligible mail client must fail safely instead. The tests distinguish native dispatch from an actually visible mailbox; they never sign in or send. For one subsequent run, the failure screenshot/UI tree showed Android's **Pixel Launcher** ANR obscuring navigation, not a Nexus exception. The disposable-device harness starts stock Settings before resizing the AVD and injects no early Menu key into the booting launcher. If necessary it may close that exact unresponsive *Pixel Launcher* via the system dialog on a disposable emulator only (repeated *Wait* could redisplay it); a Nexus ANR remains a failure.

Only the APK paired with the passing device and compatibility receipts in `evidence/android/run-source.txt` is eligible for debug distribution. `scripts/verify-delivery.sh` fails on a mismatched hash, signer, manifest permission set, compatibility result or changed application/build inputs. Separate documentation/evidence commits do not rebuild or re-sign the tested APK.
