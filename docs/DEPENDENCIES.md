# Dependency policy

Use established libraries for protocols and standards. Current additions: RE2/J 1.8 (linear-time profile regex), jsoup 1.23.2 (local HTML parser), AppAuth Android 0.11.1 (native OAuth code/PKCE). Angus Mail 2.0.5 remains the IMAP/SMTP/MIME library. No GPL application source was copied from reference projects.

Versions and upstream release/commit history were checked during implementation. AppAuth's published-release cadence is slower than upstream maintenance and needs continued monitoring. Future provider-specific adapters should be selected after interoperable account testing.

CI exports resolved debug runtime Maven coordinates and queries OSV. Network failures and advisory matches fail the build. Dependabot checks Gradle and pinned GitHub Actions weekly. Build actions are fixed to verified commit IDs. License texts and source links are bundled; inspect transitive notices before production distribution.

Direct CalDAV, custom private CA handling and AI adapters are not included. Never use Trust-All TLS or embed client secrets. Routing providers use documented HTTPS endpoints behind a small bounded client and are opt-in.
