# Dependency policy

KalPlan prefers established open-source implementations for protocols and security-sensitive standards.

## Included now

### Eclipse Angus Mail 2.0.5

Purpose:
- IMAP.
- SMTP.
- MIME/Jakarta Mail.
- built-in XOAUTH2 support.

Why:
- official Jakarta Mail implementation lineage.
- Android support.
- actively released.

KalPlan does not implement its own IMAP or SMTP parser/state machine.

### Angus Activation 2.0.3 / Jakarta Activation 2.1.4

Required companion pieces for MIME/activation handling.

### AndroidX / Jetpack Compose

- Material 3 UI.
- WorkManager for persistent background scheduling.

## Planned, not yet added

### Provider OAuth libraries

OAuth must not be hand-written.

Preferred approach:
- provider-supported maintained Android library where available.
- Microsoft: MSAL is a strong candidate.
- generic OAuth/OIDC: AppAuth can be considered, but its release/maintenance status must be re-evaluated before adoption.

### dav4jvm

Preferred later CalDAV/WebDAV basis from the DAVx5 ecosystem.

### synctools

Reference/library family for iCalendar and Android-provider integration.

### cert4android

Candidate for explicit custom/private CA support. Never replace certificate verification with Trust-All.

## Reference applications

FairEmail and DAVx5 are valuable references for edge cases and UX/security behavior.

Do not copy GPL application code into KalPlan unless the project license decision explicitly permits that.


### kotlinx.serialization JSON 1.11.0

Purpose:
- human-readable profile import/export.
- typed, versioned profile data.
- no executable profile code.

Decoded profiles are still validated separately before use.
