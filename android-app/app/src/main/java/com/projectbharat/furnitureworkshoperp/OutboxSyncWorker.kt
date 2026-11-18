package com.projectbharat.furnitureworkshoperp

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Durable sync transport. Mutations are queued in Room in the same transaction as
 * the local write; this worker replays them in creation order with stable idempotency keys.
 */
class OutboxSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val session = SecureSession(applicationContext)
        if (session.accessToken() == null) return Result.success()
        val base = applicationContext.getSharedPreferences("workshop_connection", Context.MODE_PRIVATE)
            .getString("api_base_url", "").orEmpty()
        if (base.isBlank()) return Result.retry()
        val db = WorkshopDatabase.getInstance(applicationContext)
        val api = WorkshopApi(base, session)
        return try {
            // Validate the authenticated session before draining the queue. The outbox
            // DAO applies server acknowledgements only after each request succeeds.
            api.authenticated("/auth/me")
            val pending = db.syncOutboxDao().pending(50)
            for (item in pending) {
                try {
                    api.authenticated(item.endpoint, item.httpMethod, org.json.JSONObject(item.payload))
                    db.syncOutboxDao().markSynced(item.id)
                } catch (e: ApiException) {
                    if (e.status in 400..499 && e.status != 401 && e.status != 408 && e.status != 429) {
                        db.syncOutboxDao().markFailed(item.id, e.message ?: "Rejected by server")
                    } else {
                        db.syncOutboxDao().recordRetry(item.id, e.message ?: "Temporary server error")
                        return Result.retry()
                    }
                }
            }
            if (db.syncOutboxDao().pendingCount() > 0) Result.retry() else Result.success()
        } catch (_: Exception) { Result.retry() }
    }

    companion object {
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<OutboxSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("workshop-outbox-sync", ExistingWorkPolicy.KEEP, request)
        }
    }
}
