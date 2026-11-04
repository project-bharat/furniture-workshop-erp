# Implementation roadmap

## Phase 1 — Foundation
- [x] Establish repository and top-level architecture.
- [x] Define visual identity tokens based on the supplied Aakruti reference.
- [x] Add initial tenant, identity, authorization, device, audit and sync schema.
- [ ] Add backend authentication, refresh-token rotation and authorization middleware.
- [ ] Add Android Compose shell, Room foundation and offline outbox.
- [ ] Add responsive web admin shell and sign-in.
- [ ] Add CI checks, local Docker development and migration verification.

## Phase 2 — Enquiries and customers
Customer records, multiple contacts and addresses, enquiry intake, lead ownership, follow-up reminders, site visits, and site measurements.

## Phase 3 — Quotations and projects
Configurable item catalogue, room-wise furniture schedules, measurement conversion, quotation revisions, estimate documents, approval workflow and sales-order conversion.

## Phase 4 — Production and workforce
Production orders, stage boards, task assignments, worker updates, photo evidence, attendance, work logs and configurable wage calculations.

## Phase 5 — Inventory and procurement
Material catalogue, units of measure, warehouses, stock ledger, reservations, material issues, purchase requests, supplier comparisons, purchase orders and goods receipts.

## Phase 6 — Finance
Item/project costing, expenses, approval thresholds, customer receipts, supplier bills/payments, GST invoices, non-GST estimates and financial reports. Tax configuration must be reviewed by a qualified Indian tax professional before production use.

## Phase 7 — Quality, delivery and installation
Inspection checklists, defects, rework, dispatch planning, delivery proof, installation tasks, customer sign-off and closure.

## Phase 8 — Hardening and rollout
Conflict handling, encrypted backup/restore, access review, performance, security testing, migration rehearsal, Play Store/internal distribution and production deployment.

## Release gates
- No production release until authentication, tenant isolation, authorization, backup/restore and audit trails are tested.
- No financial or stock ledger mutation may be silently overwritten by a sync conflict.
- Owner approval is required for quotations, quotation revisions, purchase orders and configured major expenses.
- Workers see only assigned work and explicitly permitted operational data.
