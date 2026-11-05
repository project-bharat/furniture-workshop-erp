# Free hosting setup (no paid domain or server)

This guide deploys the current ERP branch using free subdomains:
- API: https://furniture-workshop-erp-api-pb.onrender.com
- Admin: https://furniture-workshop-erp-admin-pb.onrender.com
- Database: Neon PostgreSQL (free plan)

These hostnames are proposed by the Render Blueprint. If Render reports that a name is unavailable, choose a unique name and update WORKSHOP_API_URL to match the API service's actual onrender.com address.

## Important status

This is a free pilot/development deployment, not a guaranteed always-on production service. Render free web services sleep after 15 minutes without inbound traffic and may take about a minute to wake. Free services share a 750-instance-hour monthly workspace allowance. Free Render web services have ephemeral filesystems, so the API must not store business records or uploads on local disk. Neon Free currently includes 1 GB database storage, 100 compute-hours per project monthly, 5 GB public transfer and a six-hour instant-restore history window. Monitor usage and maintain independent exports.

Do not enter live payroll or other critical business data until offline synchronisation, backup/restore, access control and payroll calculations have passed acceptance tests. Statutory PF, ESI, professional tax and TDS are not currently calculated.

## Part A — Create the free database

1. Open https://neon.com/ and create a free account.
2. Create a project named `furniture-workshop-erp`.
3. Select a region close to the Render Singapore region if Neon offers one.
4. Open the project's SQL Editor.
5. Open this repository file: `database/neon-initial-setup.sql`.
6. Copy the complete file contents, paste them into the Neon SQL Editor and run it once on the new, empty database.
7. In Neon, open the connection details and select the pooled connection string if available. Record the host, database name, username and password privately.

For Render's `DATABASE_URL`, use JDBC format:
`jdbc:postgresql://YOUR_NEON_HOST/YOUR_DATABASE?sslmode=require`
Use the exact host and database name shown by Neon. Enter the Neon username as `DATABASE_USER` and password as `DATABASE_PASSWORD`. Never paste credentials into GitHub files, public issues or this chat.

## Part B — Deploy both services together

1. Open https://dashboard.render.com/ and create a free account.
2. Connect the GitHub account that can access `project-bharat/furniture-workshop-erp`.
3. In Render, choose **New + → Blueprint**.
4. Select this repository and branch `feature/android-auth-offline-sync`.
5. Render reads the root `render.yaml` and shows the two web services.
6. When asked for values, enter the Neon JDBC URL, database username and database password for the API service.
7. Keep the generated JWT_SECRET and BOOTSTRAP_SECRET values private. Render generates these automatically.
8. Review the plan for both services and confirm that each is set to **Free**. Do not select a paid plan or add a payment method just for this setup.
9. Start the deployment and wait until both services report Live.

If the API service name is changed, update the admin service's WORKSHOP_API_URL to:
`https://ACTUAL-API-SERVICE-NAME.onrender.com/api/v1`

## Part C — Verify the deployment

1. Open `https://furniture-workshop-erp-api-pb.onrender.com/api/v1/health`.
2. A healthy API returns JSON with status `ok`.
3. Open `https://furniture-workshop-erp-admin-pb.onrender.com`.
4. The sign-in page should load. Do not attempt first-owner setup until the API health check succeeds and the database migrations have completed successfully.

## Part D — Create the first owner

1. In Render, open the API service → Environment.
2. Reveal/copy the generated BOOTSTRAP_SECRET privately.
3. Open the Android app's first-time workshop setup screen.
4. Enter the API base URL: `https://furniture-workshop-erp-api-pb.onrender.com/api/v1`.
5. Enter the bootstrap secret and the actual workshop/owner details.
6. Use a unique strong owner password. Do not send it to anyone.
7. Complete setup. The app should sign in using the newly created owner account.
8. Immediately rotate or remove BOOTSTRAP_SECRET in Render after successful setup. Do not reuse it.

## Free-tier operating limits and safety

- Render free web services sleep after 15 minutes without inbound traffic and take about a minute to wake. A free service can restart at any time.
- Render's free instance hours are shared across services in a workspace. Two services can exhaust the monthly allowance sooner than one.
- Neon Free compute scales to zero after inactivity and has monthly compute and network-transfer limits.
- Never store uploads, SQLite files or other persistent business data on Render's local filesystem.
- Neon Free's restore history is not a substitute for independent backups. Arrange and test periodic encrypted exports and a restore procedure before using real records.
- Do not use uptime-pinging services to bypass free-tier sleep restrictions.
- If a free quota is exhausted, the service/database may be suspended until the quota resets; do not assume the app is continuously available.

## Development notes

- API container build context is `backend/`; Dockerfile is `backend/server/Dockerfile`.
- Admin app uses Next.js server routes for HttpOnly cookie authentication and same-origin API proxying. Do not convert it to a static export without replacing those server routes.
- Database migrations in `database/migrations/` are immutable and must be applied in order. The combined Neon setup file is for a brand-new database only. Future migrations must be applied individually once, before deploying code that requires them.
