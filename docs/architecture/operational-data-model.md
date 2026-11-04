# Operational core data model

Migration: `0002_operational_core.sql`

## Included modules
- Customers and sales enquiries, including follow-up dates and enquiry pipeline states.
- Projects linked to customers and optional originating enquiries.
- Project line items with both customer-entered dimensions and technical millimetre dimensions.
- Workshop tasks linked to projects/items, stages, assignees, due dates and time tracking.
- Material catalogue and append-only inventory movement ledger.
- Purchase requests and line items.
- Approval requests for quotations, purchases, expenses, discounts and stock adjustments.

## Data integrity rules
- Every operational record is scoped by `organization_id`; branch references use composite organization foreign keys.
- Cross-organization customer, project, item, material and purchase references are rejected by composite foreign keys.
- Monetary values use INR-compatible fixed precision numeric columns; currency is explicit on projects.
- Customer measurements and technical dimensions are stored separately. Customer-facing feet/inches values should be retained as entered; technical dimensions are normalized to millimetres in `dimensions_mm`.
- Stock on hand is derived from the sum of inventory movement deltas. Do not edit or delete posted movements; corrections must be compensating movements.
- Approval decisions must be made by a server-authorized user with `approvals.decide`. UI visibility is not authorization.
- The server must write audit events for status changes, approvals, stock movements and financial changes in the same transaction as the business operation.
- Use idempotency keys for retried writes from offline devices. A client must not generate stock or financial ledger rows by replaying a previously accepted event.

## Deliberate follow-up work
This migration establishes the data structures, not the complete business workflows. API handlers, transaction services, GST calculations, invoice/payment ledgers, supplier master, purchase receipts, attendance, quotations, attachments, and sync conflict resolution are subsequent implementation tasks.
