# Backend — Kotlin + Ktor

The API is a modular monolith. Business modules share PostgreSQL with organization-scoped queries.

## Runtime configuration

Required environment variables:

- `DATABASE_URL`: PostgreSQL JDBC URL, for example `jdbc:postgresql://localhost:5432/furniture_erp`
- `DATABASE_USER` and `DATABASE_PASSWORD`: database credentials
- `JWT_SECRET`: at least 32 UTF-8 bytes; use a randomly generated secret
- `BOOTSTRAP_SECRET`: one-time secret used to authorize initial owner setup

Optional:

- `JWT_ISSUER` (default `furniture-workshop-erp`)
- `JWT_AUDIENCE` (default `furniture-workshop-clients`)
- `ACCESS_TOKEN_TTL_SECONDS` (default 900; allowed 60–3600)
- `DB_POOL_SIZE` (default 10)

Apply every SQL file in `database/migrations` in lexical order before starting the API. Do not expose the bootstrap secret to the Android or web client; send it only during the controlled first-owner setup request, then remove/rotate it at the deployment layer.

## Authentication endpoints

- `POST /api/v1/auth/bootstrap`: one-time organization, main branch and OWNER_ADMIN creation
- `POST /api/v1/auth/login`: email/password sign-in
- `POST /api/v1/auth/refresh`: one-time refresh-token rotation (30-day session)
- `POST /api/v1/auth/logout`: revoke a refresh session
- `GET /api/v1/auth/me`: verify bearer access token and reload active membership and permissions from PostgreSQL
- `GET /api/v1/health`: liveness check; does not require database configuration

Passwords are BCrypt-hashed (cost 12, 12–72 characters). Refresh tokens are cryptographically random opaque values; only SHA-256 hashes are stored. Reuse of a revoked refresh token revokes the user's active refresh sessions. Access tokens are short-lived JWTs. Authorization decisions must always use permissions reloaded from PostgreSQL, never trust permission claims supplied by the client.

The first-owner bootstrap is guarded by a server-only secret and a PostgreSQL transaction-level advisory lock; it refuses setup after the first organization exists.

## Local deployment with Docker

From the repository root, provide `POSTGRES_PASSWORD`, `JWT_SECRET` (at least 32 random bytes) and `BOOTSTRAP_SECRET` in your shell or deployment secret store, then run `docker compose up --build -d`. PostgreSQL initializes a new volume by applying `database/migrations/*.sql` in filename order; the API waits for PostgreSQL health before starting. Existing database volumes are not re-initialized automatically, so apply any newly added migration once to an existing database before deploying the matching API. The API health check is `GET /api/v1/health` on port 8080.

For the Next.js admin deployment, set `WORKSHOP_API_URL` to the public API origin plus `/api/v1`. Keep database credentials, JWT secret and bootstrap secret server-side only.

## API conventions

- Base path: `/api/v1`
- JSON request/response bodies
- UUID resource identifiers
- UTC ISO-8601 timestamps
- Organization-scoped data access
- OpenAPI contract: `shared-contracts/openapi/openapi.yaml`

This is an incremental implementation. Customer/project CRUD APIs, rate limiting, invitation flows, and broader authorization middleware are still pending.
