package at.gregor.layermaxxing

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Instant
import java.util.concurrent.TimeUnit

class NotificationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val store = SessionStore(applicationContext)
        val token = store.token ?: return Result.success()
        return runCatching {
            createChannels(applicationContext)
            val api = ApiClient()
            val messages = api.messages(token)
            val requests = api.incomingRequests(token)
            val oldMessages = store.notifiedMessages().toMutableSet()
            val oldRequests = store.notifiedRequests().toMutableSet()

            requests.forEach { request ->
                val key = request.id.toString()
                if (oldRequests.add(key)) notify(applicationContext, (10_000 + request.id).toInt(),
                    "Neue Freundschaftsanfrage", "${request.senderName} möchte mit dir befreundet sein.", REQUEST_CHANNEL)
            }
            messages.forEach { message ->
                val receivedKey = "received:${message.id}"
                if (oldMessages.add(receivedKey)) notify(applicationContext, message.id.toInt(),
                    "Neue geheime Nachricht", "Von ${message.peerName}${message.title.takeIf(String::isNotBlank)?.let { ": $it" }.orEmpty()}", MESSAGE_CHANNEL)
                val openKey = "open:${message.id}"
                if (message.unlocked && message.readAt == null && oldMessages.add(openKey)) notify(applicationContext,
                    (1_000_000 + message.id).toInt(), "Nachricht freigegeben", "Die Nachricht von ${message.peerName} kann jetzt geöffnet werden.", MESSAGE_CHANNEL)
                if (!message.unlocked && message.mode == "timed" && message.releaseAt != null) {
                    NotificationScheduler.scheduleAt(applicationContext, message.id, message.releaseAt)
                }
            }
            store.saveNotified(oldMessages.toList().takeLast(1000).toSet(), oldRequests.toList().takeLast(500).toSet())
            Result.success()
        }.getOrElse { Result.retry() }
    }

    private fun notify(context: Context, id: Int, title: String, text: String, channel: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val intent = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(text)
            .setContentIntent(intent).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    companion object {
        const val MESSAGE_CHANNEL = "layermaxxing_messages"
        const val REQUEST_CHANNEL = "layermaxxing_requests"
        fun createChannels(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannels(listOf(
                NotificationChannel(MESSAGE_CHANNEL, "Nachrichten", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(REQUEST_CHANNEL, "Freundschaftsanfragen", NotificationManager.IMPORTANCE_DEFAULT),
            ))
        }
    }
}

object NotificationScheduler {
    fun start(context: Context) {
        runCatching {
            NotificationWorker.createChannels(context)
            val periodic = PeriodicWorkRequestBuilder<NotificationWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("layermaxxing-sync", ExistingPeriodicWorkPolicy.UPDATE, periodic)
            WorkManager.getInstance(context).enqueueUniqueWork("layermaxxing-now", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<NotificationWorker>().build())
        }
    }

    fun scheduleAt(context: Context, messageId: Long, epochSeconds: Long) {
        val delay = (epochSeconds - Instant.now().epochSecond).coerceAtLeast(1)
        val request = OneTimeWorkRequestBuilder<NotificationWorker>().setInitialDelay(delay, TimeUnit.SECONDS).build()
        runCatching {
            WorkManager.getInstance(context).enqueueUniqueWork("message-release-$messageId", ExistingWorkPolicy.REPLACE, request)
        }
    }
}
