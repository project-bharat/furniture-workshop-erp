# Deployment runbook

## 1. Start PostgreSQL and the API

1. Copy `.env.example` to `.env`.
2. Replace all three secret placeholders with unique random values. Keep `.env` out of Git.
3. Run `docker compose up --build -d`.
4. Confirm the API is healthy: `curl http://localhost:8080/api/v1/health`.

The PostgreSQL container applies the ordered SQL files in `database/migrations` only when its data volume is first initialized. When upgrading an existing installation, back up the database and apply each new migration exactly once before deploying the API version that depends on it. Never edit a migration that has already been applied.

## 2. Create the first owner

Send one controlled server-side request to `POST /api/v1/auth/bootstrap` with the bootstrap secret and owner details:

```json
{
  "bootstrapSecret": "the-one-time-secret-from-your-server-environment",
  "organizationName": "Aakruti Enterprises",
  "legalName": "Aakruti Enterprises",
  "branchName": "Main Workshop",
  "ownerName": "Workshop Owner",
  "email": "owner@example.com",
  "phone": "",
  "password": "use-a-unique-12-to-72-character-password",
  "deviceLabel": "Initial owner setup"
}
```

Use the real owner details and a unique password. Do not put the bootstrap secret in the Android app, web browser, source code or a public issue. After successful bootstrap, remove or rotate the bootstrap secret in the deployment environment. The API also refuses a second bootstrap after an organization exists.

## 3. Deploy the web admin

Deploy `web-admin` as a Next.js application and set `WORKSHOP_API_URL` to the externally reachable API origin including `/api/v1`. The web app calls the API through its same-origin server route and keeps access/refresh tokens in HttpOnly cookies. Do not expose database credentials, JWT secrets or bootstrap secrets to the Next.js client.

For production, put the API behind HTTPS, restrict database network access, configure automated PostgreSQL backups and test restore procedures before entering live payroll data.

## 4. Workforce and payroll permissions

- Owner administrator: employee management, attendance, payroll preparation, approval and payments.
- Workshop manager: employee/attendance viewing and attendance entry; payroll data remains restricted unless explicitly granted.
- Payroll is organization-scoped. Every request rechecks the current membership permissions.
- Approved payroll runs are locked. Payments are append-only and idempotency-key protected.
- Draft payroll requires every scheduled workday through the calculation date to have an attendance entry. Unmarked workdays are not silently treated as present.
- Current-month payroll is accrued against the full month's scheduled working days; future days are not counted as worked days.
- Recurring salary components are effective-dated and snapshotted into payroll calculations. Disabling a component does not rewrite historical payroll.

## 5. Operational checks

Before first production payroll, verify each employee's monthly salary, joining/exit date, weekly off, salary components and attendance. Check a sample payslip against a manual calculation before approval. PF, ESI, professional tax, TDS, gratuity and state-specific labour compliance are not calculated by this release; have statutory rules configured and reviewed before relying on it for legal payroll.
