package com.avgangsplaneraren.app.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.avgangsplaneraren.app.AppLanguageState
import com.avgangsplaneraren.app.R
import com.avgangsplaneraren.app.ui.withLocale

object NotificationScheduler {

    private const val CHANNEL_ID = "departure_reminders"
    private const val REQUEST_CODE = 1001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val localizedContext = context.withLocale(AppLanguageState.currentTagSync(context))
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            localizedContext.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = localizedContext.getString(R.string.notification_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    fun schedule(
        context: Context,
        triggerAtMillis: Long,
        fromPlace: String,
        toPlace: String
    ): Boolean {
        val alarmManager = context.getSystemService(AlarmManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            return false
        }

        val intent = Intent(context, DepartureAlarmReceiver::class.java).apply {
            putExtra(DepartureAlarmReceiver.EXTRA_FROM, fromPlace)
            putExtra(DepartureAlarmReceiver.EXTRA_TO, toPlace)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent
        )
        return true
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, DepartureAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        return alarmManager.canScheduleExactAlarms()
    }

    internal fun channelId() = CHANNEL_ID
}
