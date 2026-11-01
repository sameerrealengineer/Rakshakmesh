package org.rakshakmesh.mesh

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.rakshakmesh.MainActivity
import org.rakshakmesh.R
import org.rakshakmesh.data.MeshMessage

object Notifications {
    const val CHANNEL_MESH = "mesh"
    const val CHANNEL_SOS = "sos_nearby"
    const val CHANNEL_MSG = "messages"
    const val ID_SERVICE = 1001

    const val EXTRA_ROUTE = "route"
    const val EXTRA_FORWARD_SOS = "forward_sos_id"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MESH, context.getString(R.string.notif_channel_mesh), NotificationManager.IMPORTANCE_LOW),
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SOS, context.getString(R.string.notif_channel_sos), NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 600, 300, 600, 300, 600)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
                )
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MSG, context.getString(R.string.notif_channel_msg), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    private fun openApp(context: Context, req: Int, route: String, extra: (Intent.() -> Unit)? = null): PendingIntent {
        val i = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_ROUTE, route)
        extra?.invoke(i)
        return PendingIntent.getActivity(context, req, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun service(context: Context, peers: Int): android.app.Notification {
        val stop = PendingIntent.getService(
            context, 2, Intent(context, MeshService::class.java).setAction(MeshService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_MESH)
            .setSmallIcon(R.drawable.ic_stat_mesh)
            .setContentTitle(context.getString(R.string.notif_mesh_title))
            .setContentText(context.getString(R.string.notif_mesh_text, peers))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(context, 1, "nearby"))
            .addAction(0, context.getString(R.string.notif_stop), stop)
            .build()
    }

    @SuppressLint("MissingPermission") // checked with areNotificationsEnabled()
    fun sos(context: Context, m: MeshMessage, distanceText: String?) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val dial = PendingIntent.getActivity(
            context, 3, Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")), PendingIntent.FLAG_IMMUTABLE,
        )
        val text = listOfNotNull(distanceText, m.body.take(140)).joinToString(" · ")
        val n = NotificationCompat.Builder(context, CHANNEL_SOS)
            .setSmallIcon(R.drawable.ic_stat_mesh)
            .setContentTitle(context.getString(R.string.notif_sos_title, m.originName.ifBlank { "?" }))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openApp(context, m.id.hashCode(), "nearby"))
            .addAction(0, context.getString(R.string.notif_forward_sms), openApp(context, m.id.hashCode() + 1, "nearby") {
                putExtra(EXTRA_FORWARD_SOS, m.id)
            })
            .addAction(0, context.getString(R.string.notif_call_112), dial)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(m.id.hashCode(), n) }
    }

    @SuppressLint("MissingPermission") // checked with areNotificationsEnabled()
    fun message(context: Context, m: MeshMessage) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val n = NotificationCompat.Builder(context, CHANNEL_MSG)
            .setSmallIcon(R.drawable.ic_stat_mesh)
            .setContentTitle(m.originName.ifBlank { context.getString(R.string.app_name) })
            .setContentText(m.body.ifBlank { m.fileName ?: "" }.take(140))
            .setAutoCancel(true)
            .setContentIntent(openApp(context, m.id.hashCode(), "nearby"))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(m.id.hashCode(), n) }
    }
}
