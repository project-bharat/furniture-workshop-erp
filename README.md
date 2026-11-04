# Furniture Workshop ERP

Offline-first operations platform for custom furniture manufacturing and interior projects.

## Platforms

- **Android:** Kotlin, Jetpack Compose, Material 3, Room, WorkManager
- **Web admin:** Next.js, TypeScript
- **Backend:** Kotlin, Ktor, REST/JSON, OpenAPI 3
- **Central database:** PostgreSQL
- **Deployment:** Docker; object storage for project files and backups

## Product principles

1. Android remains usable when the workshop has no internet connection.
2. Local writes are persisted in Room and an outbox in the same database transaction.
3. Server-side authorization and business rules are authoritative when online.
4. Financial and inventory ledgers are append-only; corrections use reversals.
5. Every important business action is attributable through audit records.
6. Measurements are stored canonically in integer millimetres while retaining the entered value and unit.
7. Quotations, purchases and configured major expenses require owner approval.

## Repository layout

```
android-app/          Native Android application
web-admin/            Browser-based owner/admin panel
backend/              Kotlin Ktor API
database/migrations/  Versioned PostgreSQL migrations
database/seeds/       Development reference data
shared-contracts/     OpenAPI and shared schemas
docs/                  Architecture, workflows and operations
deployment/            Docker and deployment configuration
```

## Current milestone

**Phase 1 — Foundation:** repository structure, architecture decisions, database identity/access foundation, API contract baseline and UI design system.

See [implementation roadmap](docs/roadmap.md), [architecture overview](docs/architecture/overview.md), and [database notes](database/README.md).

## Local development

The services are being added incrementally. Do not deploy this initial scaffold as a production system. Configure secrets through environment variables; never commit credentials, signing keys, production data or customer documents.
