# Authentication security foundation

This milestone adds reusable security primitives; it does not yet expose login, refresh, logout, owner bootstrap, or protected business routes.

## Access tokens
- HMAC-SHA256 signed JWTs include subject (user ID), organization ID, membership ID, issuer, audience, issued-at, expiry and a sorted unique permission claim.
- Default access-token lifetime is 15 minutes. Configuration is constrained to 60–3600 seconds.
- `JWT_SECRET` is mandatory when constructing production token settings and must contain at least 32 UTF-8 bytes. Never commit it or put it in application configuration files.
- The API must validate signature, issuer, audience and expiry on every protected request, then resolve active membership and permissions server-side. Token permissions are a fast authorization hint, not a replacement for checking membership status and critical approval rules.

## Passwords
- BCrypt cost factor 12.
- Passwords must be 12–72 characters. The 72-character limit is a BCrypt input constraint.
- Never log passwords, password hashes, access tokens or refresh tokens.
- Login endpoints must use generic authentication errors, rate limiting, and account lockout controls.

## Refresh tokens
- Refresh tokens must be cryptographically random opaque values, not JWTs.
- Persist only a SHA-256 hash in `refresh_sessions.token_hash`.
- Rotate refresh tokens atomically on use. Reuse of a revoked/replaced token should revoke the token family and require reauthentication.

## Still required before production
- Database-backed owner bootstrap guarded by a one-time setup secret and an empty-organization check.
- Login, refresh rotation, logout and `/me` endpoints.
- Ktor authentication middleware and server-side permission checks.
- Rate limiting, audit events, secret management, TLS and integration tests against PostgreSQL.
