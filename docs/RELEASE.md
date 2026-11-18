# JARVIS NEXUS 1.0.0 — debug testing distribution

First Android-native testing release. The working branch started from a README-only repository. This is not a claim of Play publication or completed production qualification.

- Native bilingual operations console and animated voice interface, with six functional destinations.
- Closed, permission-aware tool registry; exact previews for side effects and distinct handoff/verified receipts.
- Iterative Gemini function calling, structured arguments, streaming, stateful follow-ups, usage budgets and personal-key configuration.
- Local conditional and multi-step commands that do not consume model requests.
- Verified local memory writes, editable aliases/routines, pinned favorites, reminders, privacy controls and export.
- Optional, narrow accessibility observation instead of unrestricted third-party UI manipulation. Native opt-in, one verified Display scroll and opt-out were exercised on the API 35 emulator.
- Managed inference gateway reference with authenticated, rate-limited access; separate from device execution.

For the exact APK hash, source revision, emulator outcomes and outstanding qualification items see [the test report](TESTING.md). The [feature matrix](FEATURES.md) describes unsupported and conditional behaviors. The APK is debug-signed only; testing builds from different machines/runs may not share a signing certificate.

## Binary provenance

Tested application source: [`113afbd15894`](https://github.com/Pillow1243/android-Jarvis/tree/113afbd15894ba002330399a0256f1cbe09110b1) · [green device and compatibility jobs](https://github.com/Pillow1243/android-Jarvis/actions/runs/44384092541). The distributed debug APK is not rebuilt after testing.

- File: `JARVIS-NEXUS.apk` · 397,905 bytes
- SHA-256: `cdd3b85458b5b5149095048b1b294c1ddec4c461c9adeef0fab7ef18ae5f9848`
- Debug signer SHA-256: `d9deeb1fe08db35015c974fe537cf7a39bbc48b2d3c6097e109a79ab83f0c11a`
- See [the gate matrix](TESTING.md) for the failed/unverified production criteria, not just the passing tests.
