# Local IMAP TLS fixtures

`AngusTlsConnectionTest` binds only to IPv4 loopback on an ephemeral port. It uses
AngusSessionFactory, real Jakarta Mail protocol handling and real JSSE TLS sockets.
It never contacts a provider, reads real messages, or uses account credentials.
The fixture implements CAPABILITY, STARTTLS, AUTHENTICATE PLAIN, LIST and LOGOUT.
Unexpected mailbox commands fail the successful connection tests.

`mail-localhost.p12` contains a test-only server key, certificate and test CA chain.
Its public, non-secret fixture password is `fixture-only`. The server certificate
contains only DNS:localhost, allowing a separate IP/hostname mismatch check.
`mail-test-ca.pem` is trusted only in the test-created Session. Production trust,
hostname validation and protocol restrictions are not relaxed. No global JVM trust
store, default SSLContext or trust-all manager is changed.

Certificates were generated on 2026-10-18 and expire in October 2036. Replace both
fixtures before expiry, using a fresh local CA and a server certificate with
subjectAltName=DNS:localhost, extendedKeyUsage=serverAuth, basicConstraints=CA:FALSE.
Use OpenSSL req, x509 and pkcs12 commands; no external certificate service is needed.

Run with the existing project dependencies and Android SDK:

    ./gradlew :app:testDebugUnitTest --tests '*AngusTlsConnectionTest'

These regressions verify protocol/TLS behavior. They cannot establish which provider,
credentials, OAuth configuration, account policy or network caused a reported
real-account connection failure. Device-specific trust/network differences and
message import parsing require separate tests.
