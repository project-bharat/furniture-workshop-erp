# Android application

Native Android client built with Kotlin, Jetpack Compose, Material 3, Room and WorkManager.

## Initial navigation

- Sign in
- Dashboard
- Enquiries
- Projects
- Production
- Inventory
- Attendance
- Approvals
- Finance (permission-gated)
- Settings and sync status

Navigation destinations and data must be filtered by effective permissions. The server remains the authorization authority.

## Offline behavior

Room is the local working store. Every locally initiated mutation and corresponding outbox record must be committed in one Room transaction. WorkManager retries sync with backoff. Sensitive tokens are stored using Android Keystore-backed storage.

## Measurement convention

Store canonical dimensions in integer millimetres. Preserve entered value and unit for display. Support feet/inches entry and millimetres for technical drawings; do not round intermediate conversions.
