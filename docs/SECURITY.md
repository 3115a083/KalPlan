# Security, development build 0.2.0

See AUDIT_2026-10-07.md for implemented controls, findings and remaining validation.

- Android Keystore AES-GCM, authenticated filenames, no-backup directory and atomic writes protect stored requests, credentials, tokens and API keys.
- Cleartext disabled. System trust, hostname checks, mandatory TLS or STARTTLS, TLS 1.2/1.3 only, network timeouts.
- IMAP folders READ_ONLY, PEEK, never EXPUNGE or change mail flags. Bounded batches, MIME size/depth/part limits. Attachments downloaded only on explicit request to a scoped private cache.
- AppAuth code/PKCE/browser authorization, encrypted state and serialized token refresh. Public clients only. Real provider registration and interoperability testing remain necessary.
- Imported profiles are bounded, schema-validated and use RE2/J linear-time patterns. HTML parsed locally with jsoup; no remote resources.
- Productive replies require review and a separate confirmation; recipient and source are reloaded and validated. Calendar checked again immediately before DATA.
- A persisted SENDING fence prevents automatic retry after unknown SMTP delivery. Uncertain delivery is shown explicitly for manual sent-mail inspection.
- Debug defaults to SMTP simulation; optional real test mail has a backend recipient override with no fallback. Calendar writes are disabled in the writer in debug.
- Only selected calendars are queried. Hidden details are removed before UI display; hidden locations require separate permission for manual routing.
- Routing is explicit, bounded and quota-accounted before the call, never automatic or retried. No telemetry.
- CI has unit tests, Android Lint, exact-version OSV query and emulator smoke tests. These checks do not certify provider behavior or the absence of unknown vulnerabilities.

Production release still needs provider/device testing, backup/export strategy, large-store migration, distribution license choice and release signing.
