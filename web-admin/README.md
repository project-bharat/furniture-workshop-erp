# Web administration panel

Next.js + TypeScript web client for owners, office staff and managers.

## Initial shell

- Secure sign-in
- Role-aware navigation
- Operational overview
- User and role administration
- Organization and branch settings
- Approval inbox
- Audit activity

The web client consumes the same versioned API contract as Android. It must not connect directly to PostgreSQL. All permissions are enforced by the backend.


## API deployment configuration

The admin client uses a same-origin server proxy for authenticated API calls. Access and refresh tokens are stored in HttpOnly, SameSite=Strict cookies; browser JavaScript does not receive either token.

Set `WORKSHOP_API_URL` in the web-admin deployment to the backend base URL including `/api/v1`, for example `https://api.example.com/api/v1`. Local development defaults to `http://localhost:8080/api/v1`.

Start the Ktor backend on port 8080, then run `npm install` and `npm run dev`. Provision the first owner through the one-time backend bootstrap process. Staff sign in with their assigned account. The UI actions are permission-aware and the API rechecks permissions on every request.

## Workforce and payroll screens

- Employee directory with employment details and salary basis.
- Date-wise attendance with status, notes and batch saving.
- Monthly payroll draft calculation and approval lock.
- Advances, deductions, incentives, bonuses and advance recovery.
- Partial/full salary payments with UPI, bank transfer, cash or cheque references.
- Month-wise payroll and payment history.

Payroll calculations use monthly salary, configured weekly off, joining/exit dates, attendance, active salary components and recorded adjustments. This release does not calculate statutory PF, ESI, professional tax or TDS; configure and verify those rules before using the system for statutory payroll.
