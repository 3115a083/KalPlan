# Security, development build 0.4.0

See AUDIT_2026-10-07.md for implemented controls, findings and remaining validation.

- Android Keystore AES-GCM, authenticated filenames, no-backup directory and atomic writes protect stored requests, credentials, tokens and API keys.
- Cleartext disabled. System trust, hostname checks, mandatory TLS or STARTTLS, TLS 1.2/1.3 only, network timeouts.
- IMAP folders READ_ONLY, PEEK, never EXPUNGE or change mail flags. Bounded batches, MIME size/depth/part limits. Attachments downloaded only on explicit request to a scoped private cache.
- AppAuth code/PKCE/browser authorization, encrypted state and serialized token refresh. Public clients only. Real provider registration and interoperability testing remain necessary.
- Profiles are created with a bounded guided editor. Generated patterns are schema-validated and use RE2/J linear-time execution. HTML is parsed locally with jsoup; no remote resources are loaded.
- Productive replies require review and a separate confirmation; recipient and source are reloaded and validated. Calendar checked again immediately before DATA.
- A persisted SENDING fence prevents automatic retry after unknown SMTP delivery. Uncertain delivery is shown explicitly for manual sent-mail inspection.
- Debug defaults to SMTP simulation; optional real test mail has a backend recipient override with no fallback. Calendar writes are disabled in the writer in debug.
- Published defaults contain no customer, trade or organization presets. Synthetic `.invalid` examples exist only behind debug mode and are generated relative to the selected calendar.
- Only selected calendars are queried. Hidden details are removed before UI display; hidden locations require separate permission for manual routing.
- Routing is explicit, bounded and quota-accounted before the call, never automatic or retried. No telemetry.
- CI has unit tests, Android Lint, exact-version OSV query and emulator smoke tests. These checks do not certify provider behavior or the absence of unknown vulnerabilities.

Production release still needs provider/device testing, backup/export strategy, large-store migration, distribution license choice and release signing.

Live messages above 16 MiB (or with an unavailable size) are rejected before ENVELOPE/BODYSTRUCTURE processing. Oversized source mail stays in the mail account and appears as an unclear placeholder. Provider parser recursion overflow is normalized to an unsafe-MIME result. Traversal limits supplement the trusted protocol library; they do not constitute a complete hostile-server parser sandbox. No attachment bodies are prefetched for normal extraction.
