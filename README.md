# KalPlan

KalPlan is an Android app for safely processing appointment and job requests from email folders.

It is designed to extract requests from IMAP mail, compare them with selected Android calendars, rank them, optionally estimate value and travel feasibility, and help the user accept or decline them without unsafe automation.

## Status

**Early build scaffold. Do not use with production mailboxes yet.**

The repository currently contains:
- Android 8+ project base.
- Jetpack Compose + Material 3.
- German and English resources.
- Dispatcher navigation shell.
- Material You / preset theme foundation.
- domain models with multiple labels.
- tested duration fallback policy.
- tested offline rough-distance estimator.
- protocol ports for mail/calendar/routing/AI.
- Angus Mail session configuration with strict TLS/XOAUTH2 modes.
- CI and dependency update configuration.

No productive SMTP send, IMAP sync or calendar write path exists yet.

## Core safety model

- no autonomous mail replies.
- Accept/Decline always require explicit user interaction.
- productive sending will require a second confirmation.
- calendar is rechecked immediately before sending.
- a sent acceptance can create only an optional reservation, not a booked job.
- V1 mail processing is server-side read-only.
- no permanent mail deletion.
- routing is manual only.
- cloud AI is opt-in only.

## Offline rough travel estimate

KalPlan can estimate rough proximity without contacting a routing provider.

A local postcode/city index resolves approximate centroids. KalPlan then calculates straight-line distance and a conservative travel-time range.

Example intent:
- Dortmund to Bochum is recognized as much closer than Dortmund to Euskirchen.
- no API quota is consumed.
- no customer location is sent to a third party.

The result is explicitly shown as an offline estimate, not a route.

See [docs/OFFLINE_PROXIMITY.md](docs/OFFLINE_PROXIMITY.md).

## Protocol libraries

KalPlan avoids custom protocol implementations.

Current base:
- IMAP/SMTP/MIME: Eclipse Angus Mail.
- background work: AndroidX WorkManager.
- Android calendar: CalendarContract adapter planned.

Later:
- OAuth via maintained provider/native libraries.
- CalDAV via dav4jvm / DAVx5 ecosystem libraries where suitable.

See [docs/DEPENDENCIES.md](docs/DEPENDENCIES.md).

## Duration fallback

When duration cannot be extracted:

1. profile override.
2. highest-priority label override.
3. global default of 60 minutes.

If equal-priority labels specify different durations, the longer duration wins conservatively.

## UI

Design direction: **Dispatcher + Timeline**.

- compact request list.
- multiple labels per request.
- timeline in request detail/calendar context.
- Swipe is an alternate triage view.
- no prominent Today section.
- Material You or editable KalPlan presets.
- reservations use a light, low-saturation ghost appearance.

## Build

Requirements:
- JDK 17.
- Gradle 9.6.
- Android SDK 37.

Commands:

~~~bash
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
~~~

CI uses the same Gradle version.

## Project structure

~~~text
app/src/main/java/cc/stkmn/kalplan/
  core/
  domain/
    model/
    policy/
    port/
    proximity/
  infrastructure/
    mail/
  ui/
docs/
~~~

Read [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) and [docs/SECURITY.md](docs/SECURITY.md) before adding network or write operations.
