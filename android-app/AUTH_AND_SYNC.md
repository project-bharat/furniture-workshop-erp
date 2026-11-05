# Android authentication and first-owner setup

## First deployment

1. Deploy the API and PostgreSQL using the repository's root `docker-compose.yml`.
2. Create a private `.env` file in the repository root. Set:
   - `POSTGRES_PASSWORD`: a strong database password.
   - `JWT_SECRET`: a cryptographically random value of at least 32 bytes.
   - `BOOTSTRAP_SECRET`: a separate one-time owner-setup secret.
3. Start the stack with `docker compose up --build -d`. The API listens on port 8080.
4. Put the public HTTPS API origin (for example, `https://api.example.com`) in the Android app. Do not append `/api/v1`.
5. On the app sign-in screen choose **First time here? Set up a new workshop**. Enter the API URL, `BOOTSTRAP_SECRET`, workshop display name, legal business name, main branch, owner name, email and a password of 12–72 characters. Phone is optional.
6. Tap **Create workshop & owner**. A successful response creates the organization, main branch, OWNER_ADMIN membership and first session; the app signs in automatically.
7. Rotate `BOOTSTRAP_SECRET` in the deployment environment and redeploy the API after setup. Never place the secret in source control, a public document, or a shared APK.

The server enforces first-owner setup only while no organization exists. Repeated attempts return HTTP 409. The secret is validated server-side and is never stored in Android preferences. Use this operation only over trusted HTTPS. The emulator URL `http://10.0.2.2:8080` is permitted for local development only.

## Existing accounts

Choose **Sign in** and use the owner or invited user's registered email and password.

## API authentication

- `POST /api/v1/auth/bootstrap`: one-time organization and owner creation.
- `POST /api/v1/auth/login`: email/password sign-in.
- `POST /api/v1/auth/refresh`: one-time refresh-token rotation.
- `GET /api/v1/auth/me`: verify bearer access token and reload active membership and permissions.
- `POST /api/v1/auth/logout`: revoke refresh session.

Access and refresh tokens are encrypted with AndroidX Security Crypto backed by Android Keystore. Passwords and the bootstrap secret are not persisted on the device.

## Offline synchronization

Local writes are represented by durable Room outbox entries. WorkManager retries transient failures when network connectivity is available. This is a transport foundation, not yet complete bidirectional synchronization: legacy local-only screens still need atomic local-write-plus-outbox integration, server idempotency, conflict/version handling and pull/merge behavior before production sync can be enabled for those modules.
