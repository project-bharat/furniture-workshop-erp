# Roles and approval policy

## Built-in roles

| Role | Main access |
|---|---|
| Owner / Admin | Full administration, financial visibility and final approval authority |
| Workshop Manager | Project execution, production stages, task allocation and workshop reporting |
| Carpenter / Worker | Assigned tasks, instructions, drawings released for production and task updates |
| Office / Sales Staff | Customer enquiries, follow-ups, customer records and project coordination |
| Delivery / Installation Team | Assigned dispatch, delivery and installation tasks with proof of completion |
| Purchase / Inventory Manager | Material catalogue, stock operations, purchase requests and goods receipt |

The initial migration creates global system role templates and permission mappings. The organization provisioning flow will assign the appropriate templates to organization memberships. Later releases may add custom organization-scoped roles.

Workers must not receive financial permissions by default. Access to project drawings and customer details is limited to assigned work and operational necessity.

## Owner approval gates

The owner/admin role is the only built-in role with the approval-decision permission.

Approval is required for:
- New quotations before they are sent as approved commercial offers.
- Every quotation revision after an earlier revision was approved.
- Purchase orders before they are issued to a supplier.
- Expenses above the configurable approval threshold.
- Any additional high-value or exceptional commitment configured by the owner.

A workshop manager may prepare and submit a request but cannot approve the request by virtue of the workshop-manager role. The purchase/inventory manager may prepare purchase requests and supplier comparisons but cannot approve the purchase order.

## Approval record requirements

Each approval decision must record the organization, target document and revision, requester, approver, decision, decision timestamp and optional reason. A revised document must not inherit the previous revision's approval. Rejected requests remain visible with their history and may be resubmitted as a new revision or request.
