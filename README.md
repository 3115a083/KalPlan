# KalPlan 0.7.0-dev

The profile workbench supports freely named fields, selectable source regions, recognition refinement, explicit expected-value examples and a shared extraction preview. Missing appointment data can be suggested by a bounded local heuristic fallback. See [the focused review](docs/REVIEW_0.7.0.md) for scope and verification limits.

Android app for reviewing appointment requests, checking device calendars and planning replies. This branch turns the original scaffold into a functional development build using the Dispatcher design and icon 1.

## Use
1. Install the debug APK (Android 8/API 26 or later).
2. Enable debug mode with five quick taps on the centered footer and generate synthetic calendar-aware cases. Samples cannot send mail or create calendar entries.
3. Add IMAP/SMTP accounts, app passwords or registered native OAuth public-client settings; select folders and create extraction profiles in the guided editor.
4. Grant calendar read access and select calendars explicitly. Configure independently which private details can appear. Without calendars, productive acceptance is blocked.
5. Configure origin, buffers, visual labels, reply templates and optional value estimates. Enabling a label price override makes empty numeric components zero; disabling the override uses global rates.
6. Check both trip legs manually. Routing requires your own provider API key and explicit coordinates; it never runs automatically.
7. Review a reply and confirm separately. Reservations are optional tentative entries after acknowledged sending, not confirmed bookings.

Settings apply immediately. Design, language and light/dark mode are grouped together. Calendar-specific options open only when a calendar is enabled and remain editable by tapping that calendar. Synchronization supports an overnight quiet period. Declining discards locally by default; sending a decline message is an explicit opt-in.

Notifications and a resizable scrollable Android widget open the review screen. IMAP source mail remains read-only. Attachments are downloaded only on request. Users may paste a local profile test example without creating an inbox request. Recognition can be refined against explicitly saved examples while source mail stays unchanged.

## Build and validation
Pinned Gradle wrapper, Java 17, Kotlin 2.4.10, AGP 9.4.0, target/compile API 37. CI performs unit tests, Android Lint, resolved-dependency OSV lookup, APK assembly and device smoke tests with screenshots on minimum/newest available emulator APIs.
```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

## Security and limits
State, credentials and OAuth tokens are encrypted using Android Keystore AES-GCM. TLS verification is mandatory. RE2/J protects profile regex execution; jsoup converts HTML locally; AppAuth implements OAuth code/PKCE.

This is not a production release. Real provider testing, license selection, signing and large-store/export migration remain necessary. Advanced optional features and all known limitations are recorded in [the audit](docs/AUDIT_2026-10-07.md). See [security](docs/SECURITY.md), [dependency policy](docs/DEPENDENCIES.md) and [third-party notices](THIRD_PARTY_NOTICES.md).

Implementation review: https://github.com/3115a083/KalPlan/pull/6
