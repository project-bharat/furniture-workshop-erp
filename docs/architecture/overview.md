# Architecture overview

## System shape

The system is an offline-first Android client plus a web administration client backed by a Kotlin Ktor modular monolith and PostgreSQL.

    Android (Compose + Room + WorkManager) ─┐
                                            ├── HTTPS REST API (Ktor) ── PostgreSQL
    Web Admin (Next.js + TypeScript) ───────┘              |
                                                           └── S3-compatible object storage

PostgreSQL is the central source of truth. Room is the Android device's durable local working store and supports disconnected operations. A local write and its outbox event must be committed in one Room transaction.

## Backend modules

- identity: authentication, sessions, users, roles and permissions
- organization: organizations, branches and business settings
- customers: customers, addresses and enquiries
- estimating: quotations, revisions, approvals and estimates
- projects: rooms, furniture items, specifications and drawings
- production: stages, tasks, assignments and work logs
- workforce: attendance, adjustments and payroll inputs
- inventory: materials, warehouses, stock movements and reservations
- procurement: suppliers, purchase requests, purchase orders and receipts
- finance: expenses, customer receipts, supplier bills, invoices and costing
- fulfilment: quality, delivery, installation and sign-off
- sync: device registration, change feeds, idempotency and conflict records
- files: metadata, upload authorization and object-storage references
- audit: append-only records of security-sensitive and business-critical actions

## Core engineering rules

- UUID primary keys; UTC timestamps; explicit organization and branch ownership.
- Server-side authorization on every protected operation. Hiding a button is not authorization.
- Use decimal-safe money (NUMERIC(18,2) in PostgreSQL; integer paise or BigDecimal in application code). Never use floating-point money.
- Store dimensions as integer millimetres; retain original entered value and unit for display and traceability.
- Use explicit status transitions and revision numbers for approved business documents.
- Approved quotations and drawings are immutable snapshots. Changes create a new revision and approval cycle.
- Stock and financial movements are append-only. Correct errors with compensating entries.
- Every mutation that may be retried carries an idempotency key.
- Audit records identify actor, organization, action, target, timestamp, request/correlation ID and safe change metadata. Do not log passwords, tokens or secrets.

## Offline synchronization

1. Android commits the domain change and outbox event atomically.
2. WorkManager sends queued events with stable event IDs and idempotency keys.
3. The server authenticates the device/user, checks permissions and validates the business transition.
4. The server acknowledges accepted events and returns canonical IDs, versions and server timestamps.
5. Android pulls changes after its last server cursor and applies them transactionally.
6. Conflicts are explicit records with resolution policies; never resolve financial, stock, approved quotation or approved drawing conflicts by last-write-wins.

## Security baseline

TLS in transit; strong password hashing on the server; short-lived access tokens and rotating refresh tokens; secure device token storage; rate limiting; server-side role checks; encrypted backups; least-privilege object access; audit logging; and tested restore procedures. Production secrets are injected through environment configuration.
