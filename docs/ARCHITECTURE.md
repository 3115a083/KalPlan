# Architecture

KalPlan starts with strict domain boundaries so protocol and Android-specific code can be swapped without changing business rules.

## Layers

### Domain

Pure Kotlin:
- request models.
- multi-label rules.
- duration policy.
- offline proximity estimates.
- ports for mail, calendars, routing and AI.

Domain code must not depend directly on Angus Mail, CalendarContract, Ktor, a routing vendor or a local AI runtime.

### Infrastructure

Adapters implement the ports.

Initial choices:
- IMAP/SMTP/MIME: Eclipse Angus Mail.
- Android calendar: CalendarContract adapter.
- Work scheduling: WorkManager.
- direct CalDAV later: dav4jvm family.
- local place index: bundled preprocessed open data.

### UI

Jetpack Compose + Material 3.

Design direction:
- Dispatcher + Timeline.
- list-first.
- timeline in detail/calendar views.
- Swipe is optional triage, not the primary navigation model.

## Security boundary

A UI action never talks directly to SMTP or calendar writes.

Expected acceptance flow:

1. user chooses Accept.
2. first confirmation/view is shown.
3. user confirms again.
4. calendar is re-read.
5. conflicts are recalculated.
6. SMTP is attempted exactly once with idempotency protection.
7. optional reservation is written.
8. reply state remains pending until the customer actually confirms.

## Offline proximity

The preliminary estimator is intentionally separate from real routing.

It uses local city/postcode centroids to produce:
- straight-line distance.
- road-distance corridor.
- travel-time corridor.

Real routing is only triggered manually.
