package fi.anssi.kalakartta.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import fi.anssi.kalakartta.R

internal fun shouldShowMissingWeatherUpdateNotification(result: MissingWeatherUpdateResult): Boolean =
    result.attempted > 0

internal object MissingWeatherDataNotification {
    private const val CHANNEL_ID = "missing_weather_update"
    private const val NOTIFICATION_ID = 1004

    fun show(context: Context, result: MissingWeatherUpdateResult) {
        val notificationManager = NotificationManagerCompat.from(context)
        if (!notificationManager.areNotificationsEnabled()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Säätietojen päivitys",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Puuttuvien säätietojen automaattisen päivityksen tulokset"
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val openUpdateIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, WeatherUpdateActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = "Onnistuneesti päivitetty: ${result.successful}, " +
                "epäonnistui: ${result.failed}" +
                if (result.noChanges > 0) ", ei muutoksia: ${result.noChanges}" else ""

        try {
            notificationManager.notify(
                NOTIFICATION_ID,
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ahven)
                    .setContentTitle("Puuttuvien säätietojen päivitys valmis")
                    .setContentText(text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    .setContentIntent(openUpdateIntent)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .build()
            )
        } catch (_: SecurityException) {
            // Android 13+: notification permission may not have been granted.
        }
    }
}
