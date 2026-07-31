package com.avgangsplaneraren.app.notifications

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.avgangsplaneraren.app.AppLanguageState
import com.avgangsplaneraren.app.MainActivity
import com.avgangsplaneraren.app.R
import com.avgangsplaneraren.app.ui.withLocale

class DepartureAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_FROM = "extra_from"
        const val EXTRA_TO = "extra_to"
        private const val NOTIFICATION_ID = 2001
    }

    override fun onReceive(context: Context, intent: Intent) {
        val fromPlace = intent.getStringExtra(EXTRA_FROM).orEmpty()
        val toPlace = intent.getStringExtra(EXTRA_TO).orEmpty()
        val localizedContext = context.withLocale(AppLanguageState.currentTagSync(context))

        NotificationScheduler.ensureChannel(context)

        val openAppIntent = Intent(context, MainActivity::class.java)
        val contentIntent = android.app.PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NotificationScheduler.channelId())
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentTitle(localizedContext.getString(R.string.notification_title))
            .setContentText(localizedContext.getString(R.string.notification_text, fromPlace, toPlace))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(localizedContext.getString(R.string.notification_text, fromPlace, toPlace))
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }
}
