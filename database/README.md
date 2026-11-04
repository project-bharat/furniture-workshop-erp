# Database

PostgreSQL is the central source of truth. Migrations are ordered, immutable SQL files in migrations/. Never edit a migration that has already been applied in a shared environment; add a forward migration instead.

## Initial migration

0001_foundation.sql creates organization/branch ownership, users, role and permission mapping, device registration, audit records, sync event receipts and initial roles/permissions.

## Migration conventions

- Use UUID identifiers and TIMESTAMPTZ for timestamps.
- Use NUMERIC(18,2) for monetary values and integer millimetres for dimensions.
- Add indexes for tenant-scoped lookups and foreign keys.
- Keep ledger tables append-only in later modules.
- Include organization_id in tenant-owned rows and enforce tenant scope in application queries; add stronger database-level isolation policies as deployment requirements are finalized.
- Apply migrations through a migration runner in deployment, not manually from application startup.

## Bootstrap

The first owner account must be provisioned through a one-time secure bootstrap flow or administrative command. Do not seed a known default password or commit production credentials.
