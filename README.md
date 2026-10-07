# KalPlan 0.2.0-dev

Android app for reviewing appointment requests, checking device calendars and planning replies. This branch turns the original scaffold into a functional development build using the Dispatcher design and icon 1.

## Use
1. Install the debug APK (Android 8/API 26 or later).
2. Load clearly labeled demo requests to explore list, detail, calendar and swipe views. Samples cannot send mail.
3. Add IMAP/SMTP accounts, app passwords or registered native OAuth public-client settings; select folders and extraction profiles.
4. Grant calendar read access and select calendars explicitly. Configure independently which private details can appear. Without calendars, productive acceptance is blocked.
5. Configure origin, buffers, labels, reply templates and optional value estimates. Correct uncertain extraction and select a candidate before proceeding.
6. Check both trip legs manually. Routing requires your own provider API key and explicit coordinates; it never runs automatically.
7. Review a reply and confirm separately. Reservations are optional tentative entries after acknowledged sending, not confirmed bookings.

Notifications and a resizable scrollable Android widget open the review screen. IMAP source mail remains read-only. Attachments are downloaded only on request. Debug controls are behind five quick taps on the footer; mail defaults to simulation and calendar writes are blocked.

## Build and validation
Pinned Gradle wrapper, Java 17, Kotlin 2.4.10, AGP 9.4.0, target/compile API 37. CI performs unit tests, Android Lint, resolved-dependency OSV lookup, APK assembly and device smoke tests with screenshots on minimum/target APIs.
```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

## Security and limits
State, credentials and OAuth tokens are encrypted using Android Keystore AES-GCM. TLS verification is mandatory. RE2/J protects profile regex execution; jsoup converts HTML locally; AppAuth implements OAuth code/PKCE.

This is not a production release. Real provider testing, license selection, signing and large-store/export migration remain necessary. Advanced optional features and all known limitations are recorded in [the audit](docs/AUDIT_2026-10-07.md). See [security](docs/SECURITY.md), [dependency policy](docs/DEPENDENCIES.md) and [third-party notices](THIRD_PARTY_NOTICES.md).

Implementation review: https://github.com/3115a083/KalPlan/pull/6
