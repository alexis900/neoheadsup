package io.alexis900.neoheadsup

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon

/** Builds the Heads Up notification. */
object NotificationHelper {

    const val CHANNEL_ID = "heads_up"
    private const val NOTIFICATION_ID = 2001

    fun createChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as? NotificationManager ?: return

        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_description)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 150, 250)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    fun show(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as? NotificationManager ?: return
        createChannel(context)

        val title = context.resources
            .getStringArray(R.array.notification_titles)
            .random()

        val openIntent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val contentIntent = PendingIntent.getActivity(
            context, 1, openIntent,
            pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT)
        )

        val snoozeIntent = Intent(context, NotificationActionReceiver::class.java)
            .setAction(NotificationActionReceiver.ACTION_SNOOZE)
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context, 2, snoozeIntent,
            pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT)
        )

        val disableIntent = Intent(context, NotificationActionReceiver::class.java)
            .setAction(NotificationActionReceiver.ACTION_DISABLE)
        val disablePendingIntent = PendingIntent.getBroadcast(
            context, 3, disableIntent,
            pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT)
        )

        val actionIcon = Icon.createWithResource(context, R.drawable.ic_notification)

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.notification_text))
            .setSubText(context.getString(R.string.notification_subtext))
            .setStyle(
                Notification.BigTextStyle()
                    .bigText(context.getString(R.string.notification_text))
            )
            .setCategory(Notification.CATEGORY_REMINDER)
            .setPriority(Notification.PRIORITY_HIGH)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setColor(context.getColor(R.color.primary))
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .addAction(
                Notification.Action.Builder(
                    actionIcon,
                    context.getString(R.string.action_snooze),
                    snoozePendingIntent
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    actionIcon,
                    context.getString(R.string.action_disable),
                    disablePendingIntent
                ).build()
            )
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as? NotificationManager ?: return
        manager.cancel(NOTIFICATION_ID)
    }

    private fun pendingIntentFlags(base: Int): Int =
        base or PendingIntent.FLAG_IMMUTABLE
}
