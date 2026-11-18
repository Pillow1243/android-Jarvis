# Feature implementation report

This report separates **implemented** behavior from **verified on this build** ([test evidence](TESTING.md)). An implementation is not a guarantee for every model, phone, speech engine or destination app.

| Feature | Implemented behavior | Boundary |
|---|---|---|
| Natural instructions | Persian/English deterministic commands, compound clauses, clarification, scoped app references, repeat, user-defined routines, and Gemini function calling for free-form requests | The offline grammar is deliberately limited. A free-form request needs a configured provider; unspecified destinations are not guessed. |
| Agent | Iterative plan → validated call → gated execution → evidence → next turn. Conditional app availability, safe error recovery, duplicate prevention, cancellation, interrupted-task journal | No unrestricted agent scripts or background activity launches. A model answer is not a receipt. |
| Apps | Query launchable activities, registry browser, localized name normalization, common aliases, user aliases and bounded edit-distance matching. Choose between matches. Return to the previous Android task | Current profile and Android package visibility apply. Moving a task to the back does not identify which app becomes foreground. |
| System | Wi-Fi, Bluetooth, location, display, sound, notifications, battery, apps and DND **pages**; Internet/volume panels where available. Camera, gallery, browser, contacts, dialer and system document picker | No claim that opening a panel changed its setting. Some OEMs lack particular public handlers. |
| Calls and contacts | Scoped contacts permission and local lookup; name/number selection; immutable exact preview; `ACTION_DIAL` only | No `CALL_PHONE`, background calling, password entry or call-connected claim. A number typed directly does not require contact access. |
| Messages | Native SMS/email drafts and sharesheet; exact recipient/body approval. Mailto handlers are resolved locally, dedicated mail apps preferred, and the draft is bound to the package shown in the preview. | Final recipient/send controls remain in the native app. No SMS permission, silent delivery or message-receipt claim. |
| Maps and browser | Approved HTTPS URLs, Google/YouTube searches, Maps search/directions previews. Encoded queries, no private location permission | Provider determines nearest place and computes the route. Nexus cannot verify arrival, route selection or arbitrary in-app search. |
| Accessibility | Explicit disclosure and Android opt-in; short-lived expected-window observation; manual, deterministic one-scroll Display walkthrough | No broad UI tapping/typing, CAPTCHA handling, authentication/payment automation or screen scraping. OEM windows can be unobservable. |
| Speech | Push-to-talk, partial transcript, real RMS envelope, low/unknown-confidence review, requested fa-IR/en-US, interruptible TTS, installed voice and speed settings, optional foreground conversation | Acoustic quality and language switching depend on Android's chosen engine and installed packs. No bundled ASR/TTS model, wake word or background recording. |
| Memory and routines | Private SQLite notes, local recall, app/contact aliases, editable shortcuts, Home favorites, compact recent-task summaries. View/edit/delete/pause/erase; explicit JSON export | Not a password manager. No automatic retention of message bodies, contact rows, raw prompts or API keys. Incidental secrets cannot be reliably classified; do not enter them. |
| Reminders | Confirmed text/time, approximate AlarmManager registration, notification channel, pending reminder list, cancellation and boot restore | Doze, OS notification settings and force-stop affect delivery. Scheduling verified ≠ notification already delivered. No exact-alarm exemption. |
| Home/Agent/Activity/Tools/Memory/Settings | Native dark themes, bilingual layout including RTL, original brand, animated signal field, real store/OS metrics, task timeline, controls and optional guide | No seeded dashboard activity, fabricated usage or placeholder services. |
| API and usage | Personal encrypted credential or scoped managed session; configurable model, live function probe, bounded SSE, complete signature round-trip, daily budget and reported token counts | Free-tier quotas and region/account eligibility vary. No key in APK. No successful cloud check implied by key storage. |
| Managed backend | Deployable fixed-upstream OIDC/RS256 gateway; persistent atomic budgets, concurrency limits, token scope, capped content, rotatable server-side provider key | No third-party infrastructure or login tenant provisioned in this delivery. Android accepts a short-lived token from the operator's identity system; not a turnkey consumer signup flow. |

## Registry (19 tools)

`device_report`, `list_apps`, `find_app`, `launch_app`, `previous_app`, `open_panel`, `open_interface`, `open_url`, `web_search`, `maps`, `find_contact`, `dial_contact`, `compose_sms`, `compose_email`, `share_text`, `recall_note`, `save_note`, `set_reminder`, `display_walkthrough`.

The Display walkthrough is not advertised to Gemini. The Files/Voice categories also expose the native document picker and push-to-talk entrypoint; these are not extra model tools. Tool usage metrics reflect **retained receipts**, so deleting or pausing history affects those graphs. Request budgets are separate and cannot be reset by clearing memory.

## Not offered as complete

Production signing, Play publication/review, arbitrary third-party UI control, unattended messages, verified user authentication or transactions, continuous background surveillance, guaranteed nearest-destination resolution, production cloud hosting, sustained battery measurements, physical-device acoustic validation, and unlimited Gemini access.
