package com.example.wearosapp.device.util

import android.Manifest
import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.wearosapp.device.receiver.NotificationActionReceiver
import com.example.wearosapp.device.model.Notification
import java.util.UUID

class NotificationManager(private val context: Context) {
    private val channelId = "mqtt_channel"

    init {
        val channel = NotificationChannel(
            channelId,
            "MQTT Messages",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    fun showNotification(notification: Notification) {

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_overlay)
            .setContentTitle(notification.caption)
            .setContentText(notification.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notification.message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        // Dynamically add buttons
        val notificationId = UUID.randomUUID().hashCode()
        notification.buttons.forEach { label ->
            val intent = Intent(context, NotificationActionReceiver::class.java).apply {
                putExtra("pressed", label)
                putExtra("notificationId", notificationId)
                putExtra("id", notification.id)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                label.hashCode(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(0, label, pendingIntent)
        }


        val manager = NotificationManagerCompat.from(context)
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        manager.notify(notificationId, builder.build())
    }

}
