# Security

Security failures that expose secrets, contact the wrong recipient, destroy mail/calendar data or leak customer data block a release.

## Current scaffold guarantees

- cleartext network traffic is disabled.
- Android system trust anchors are used by default.
- no Trust-All certificate mode exists.
- app backup is disabled in the scaffold.
- mail libraries are isolated behind ports.
- V1 design treats IMAP processing as read-only.
- no destructive mail operation is implemented.
- no productive SMTP send path is implemented yet.

## Mail

Target stack: Eclipse Angus Mail.

Required when implemented:
- explicit TLS mode.
- hostname verification.
- bounded connection/read/write timeouts.
- OAuth accounts use XOAUTH2 only.
- passwords/tokens never enter logs.
- source folders are opened read-only in V1.
- duplicate-send protection.

## OAuth

Do not implement OAuth protocol flows manually.

Use maintained provider/native libraries where possible. Generic AppAuth remains an option only after maintenance/security review at implementation time.

## Secrets

Planned:
- Android Keystore-backed encryption.
- credentials excluded from backup.
- redacted diagnostics.
- API keys never exported in plain text.

## Debug mode

Debug mode must technically redirect outgoing mail to a configured test address. UI-only redirection is insufficient.

Calendar writes are simulated by default in debug mode.

## Destructive actions

Future mail deletion means Move to Trash only. Never EXPUNGE as part of ordinary operation.
