# KalPlan 0.6.0-dev work in progress

Based on 0.5.1-dev (PR #7). User-requested scope:

- Synchronous local text/selection/IME state; settings text and number drafts commit once on Apply.
- Optional Photon address suggestions, minimum 3 characters, debounce, shared throttling, cache and durable daily cap. No routing queries are triggered by typing.
- Combinable work/travel/order flat fees, optionally replacing the corresponding hourly component. Old pricing remains readable.
- Visual OR groups containing AND conditions for sender, subject and body; legacy label rules remain supported.
- Day/week/month calendar, colored calendar-event dots, per-calendar display colors and direct year/month navigation.
- Location, missing/hidden location and online icons across views.
- Rich swipe review card with expandable source excerpt, calendar occupation/collisions, labels, duration, travel warnings and estimated value. The design workshop selected both request-first and calendar-first layouts, persisted under Design and language. Both present the same information with three fixed actions below the scrolling card. Instrumented coverage exercises both layouts at 320×320 dp and 2× font scale.
- Pull-to-refresh on request lists including empty/filtered lists; shared unique manual sync with toolbar action, observed worker progress and queued/offline status. Repeated gestures do not enqueue concurrent manual syncs.
- Back from swipe detail returns to swipe; next Back returns to the list. Swipe view state is retained across detail navigation.
- Password-encrypted portable backup/import with optional credentials. AES-GCM with PBKDF2-HMAC-SHA256 (600,000 rounds). Bounded, validated files; explicit replace preview; device-encrypted pre-import recovery and durable restore journal. Connections pause, calendar IDs and signature URI grants require re-selection. Local send fences and request limits survive imports.
- Receive-only mail accounts no longer require an outgoing address/SMTP configuration; host/login whitespace is normalized. Connection errors distinguish authentication, DNS, TLS and timeout without displaying server responses or secrets.

Validation is pending CI. New regression coverage includes text insertion/selection, restore and send fencing, swipe Back behavior, year/month navigation, flexible matching, combinable pricing, encrypted backup integrity, and real loopback Angus TLS connections. No real user mailbox or OAuth provider credentials were available, so the user's original provider-specific connection failure is not yet proven resolved.
