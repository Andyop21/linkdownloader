package io.github.andyop21.linkdownloader.download

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.WorkManager
import io.github.andyop21.linkdownloader.R
import java.util.UUID
import kotlin.math.roundToInt

/**
 * The app's notifications, all on one low-importance channel, "Downloads" (docs/product.md B8):
 * the ongoing progress of a download with a Cancel action, and the result once it ends.
 */
object Notifications {

    const val CHANNEL_DOWNLOADS = "downloads"

    /** The ongoing notification of the running download (the foreground service's). */
    const val ID_PROGRESS = 1

    /** "Saved to Downloads" or the failure; a newer result replaces an older one. */
    const val ID_RESULT = 2

    /** "Choose which videos to save"; removed when the next download starts. */
    const val ID_CHOOSE = 3

    /**
     * Extra on the intent that opens the app from the "Choose which videos" notification: the
     * work ID (a [UUID] string) of the download whose output holds the choices.
     */
    const val EXTRA_CHOOSE_WORK_ID = "io.github.andyop21.linkdownloader.extra.CHOOSE_WORK_ID"

    private const val PROGRESS_MAX = 1000
    private const val REQUEST_OPEN_APP = 0
    private const val REQUEST_OPEN_VIDEO = 1
    private const val REQUEST_CHOOSE = 2

    /** Creates the "Downloads" channel. Called at app start; creating it again changes nothing. */
    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_DOWNLOADS,
            context.getString(R.string.notification_channel_downloads),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notification_channel_downloads_description)
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /** The ongoing notification for the download [workId] in state [progress]. */
    fun progress(context: Context, workId: UUID, progress: DownloadProgress): Notification {
        val stageText = when (progress.stage) {
            DownloadStage.Preparing -> R.string.notification_checking_link
            DownloadStage.Downloading -> R.string.notification_downloading
            DownloadStage.Saving -> R.string.notification_saving
        }
        val cancel = WorkManager.getInstance(context).createCancelPendingIntent(workId)
        return builder(context)
            .setContentTitle(progress.title ?: context.getString(R.string.notification_getting_ready))
            .setContentText(context.getString(stageText))
            .apply {
                if (progress.count > 1) {
                    setSubText(context.getString(R.string.notification_position, progress.position, progress.count))
                }
                val fraction = progress.fraction
                if (fraction == null) {
                    setProgress(0, 0, true)
                } else {
                    setProgress(PROGRESS_MAX, (fraction * PROGRESS_MAX).roundToInt().coerceIn(0, PROGRESS_MAX), false)
                }
            }
            .setContentIntent(openApp(context, REQUEST_OPEN_APP, null))
            .addAction(0, context.getString(R.string.notification_cancel), cancel)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /** Posts or updates the ongoing notification, when the app may post notifications. */
    fun showProgress(context: Context, notification: Notification) = post(context, ID_PROGRESS, notification)

    /** "Saved to Downloads" with the file name, or the count for several videos; a tap plays the first. */
    fun showSaved(context: Context, videos: List<SavedVideo>, failed: Int) {
        val first = videos.firstOrNull() ?: return
        val resources = context.resources
        var text = if (videos.size == 1) {
            first.fileName
        } else {
            resources.getQuantityString(R.plurals.notification_videos_saved, videos.size, videos.size)
        }
        if (failed > 0) {
            text += " · " + resources.getQuantityString(R.plurals.notification_videos_not_saved, failed, failed)
        }
        val notification = builder(context)
            .setContentTitle(context.getString(R.string.notification_saved_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openVideo(context, first.uri))
            .setAutoCancel(true)
            .build()
        post(context, ID_RESULT, notification)
    }

    /** The friendly reason a download failed; a tap opens the app. */
    fun showFailed(context: Context, reason: FailureReason) {
        val text = context.getString(reason.messageRes)
        val notification = builder(context)
            .setContentTitle(context.getString(R.string.notification_failed_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp(context, REQUEST_OPEN_APP, null))
            .setAutoCancel(true)
            .build()
        post(context, ID_RESULT, notification)
    }

    /** "Choose which videos to save" for the post [title]; a tap opens the app on the choice of [workId]. */
    fun showChooseVideos(context: Context, workId: UUID, title: String) {
        val notification = builder(context)
            .setContentTitle(context.getString(R.string.notification_choose_title))
            .setContentText(title)
            .setContentIntent(openApp(context, REQUEST_CHOOSE, workId))
            .setAutoCancel(true)
            .build()
        post(context, ID_CHOOSE, notification)
    }

    fun cancel(context: Context, id: Int) = NotificationManagerCompat.from(context).cancel(id)

    private fun builder(context: Context) = NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
        .setSmallIcon(R.drawable.ic_stat_download)
        .setPriority(NotificationCompat.PRIORITY_LOW)

    /** Opens the app as the launcher does, bringing a running instance forward. */
    private fun openApp(context: Context, requestCode: Int, chooseWorkId: UUID?): PendingIntent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent().setPackage(context.packageName)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        chooseWorkId?.let { intent.putExtra(EXTRA_CHOOSE_WORK_ID, it.toString()) }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** Plays [uri] in the phone's video player, with read access granted to it. */
    private fun openVideo(context: Context, uri: Uri): PendingIntent {
        val type = context.contentResolver.getType(uri) ?: "video/*"
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, type)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            REQUEST_OPEN_VIDEO,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** Posts [notification] unless the app may not (POST_NOTIFICATIONS refused on API 33+). */
    private fun post(context: Context, id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
