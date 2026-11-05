# Android authentication and synchronization

## Configuration
Set the API URL in Android Settings > Connection (or the managed deployment configuration) to the HTTPS origin of the deployed API, without the /api/v1 suffix. Local emulator development may use http://10.0.2.2:8080. Cleartext traffic must remain disabled for production.

## Authentication
The Android client uses POST /api/v1/auth/login, rotates refresh tokens through /auth/refresh after an access-token 401, verifies sessions through /auth/me, and revokes refresh sessions through /auth/logout. Access and refresh tokens are encrypted with AndroidX Security Crypto backed by Android Keystore. Passwords are never persisted.

## Offline synchronization
Local writes are represented by durable Room outbox entries with stable idempotency keys. WorkManager runs only with network connectivity, retries transient errors with exponential backoff, preserves ordering, and marks permanent 4xx failures for review instead of silently dropping them. Server acknowledgements are the only condition that marks a mutation synchronized.

## Rollout note
The current app still contains legacy local-only screens and sample records. Do not automatically upload seeded sample data. Before enabling production sync for a module, route every create/update/delete through a Room transaction that writes both the local entity and an outbox entry, and implement matching server-side idempotency and conflict/version semantics. This phase establishes secure session handling and the durable transport foundation; module-by-module migration is required before claiming full bidirectional synchronization.
