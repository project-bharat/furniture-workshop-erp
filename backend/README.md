# Backend — Kotlin + Ktor

The API is a modular monolith. Modules are separated by business capability and share one PostgreSQL database with explicit ownership boundaries.

## Initial API conventions

- Base path: /api/v1
- JSON request/response bodies
- UUID resource identifiers
- UTC ISO-8601 timestamps
- Consistent error envelope with stable error code, safe message, request ID and field validation details
- Authenticated routes require an access token and server-side permission checks
- Mutating client requests support Idempotency-Key
- OpenAPI contract lives in shared-contracts/openapi/openapi.yaml

## Authentication baseline

Use Argon2id password hashing, short-lived access tokens and rotating refresh sessions. Store only refresh-token hashes. Revoke sessions on logout, credential reset, account suspension and detected token reuse. Never log credentials or bearer tokens.

This directory is a module boundary placeholder; executable Ktor source and automated tests are added in the next foundation implementation pass.
