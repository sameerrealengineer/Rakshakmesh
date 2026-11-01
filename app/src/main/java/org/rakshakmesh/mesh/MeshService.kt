package org.rakshakmesh.mesh

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import org.rakshakmesh.RakshakApp

/**
 * Keeps the offline mesh running while the screen is off or the app is in the background,
 * so a phone in someone's pocket can still receive and pass on SOS messages.
 * Shows a permanent notification with a Stop button.
 */
class MeshService : Service() {
    companion object {
        const val ACTION_STOP = "org.rakshakmesh.mesh.STOP"

        /** Start from the foreground (activity) only. */
        fun start(context: Context) {
            if (!Permissions.hasMesh(context)) return
            runCatching { ContextCompat.startForegroundService(context, Intent(context, MeshService::class.java)) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MeshService::class.java))
        }

        @SuppressLint("MissingPermission") // missing permission only hides the notification
        fun refreshNotification(app: RakshakApp) {
            if (!running) return
            runCatching {
                NotificationManagerCompat.from(app).notify(Notifications.ID_SERVICE, Notifications.service(app, app.mesh.peers.value.size))
            }
        }

        @Volatile var running = false
            private set
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as RakshakApp
        if (intent?.action == ACTION_STOP) {
            app.appScope.launch { app.settings.setMeshEnabled(false) }
            app.mesh.stop()
            stopSelf()
            return START_NOT_STICKY
        }
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        try {
            ServiceCompat.startForeground(this, Notifications.ID_SERVICE, Notifications.service(this, app.mesh.peers.value.size), type)
        } catch (e: Exception) {
            // Not allowed right now (e.g. missing permission): run the mesh only while the app is open.
            app.appScope.launch { app.mesh.start() }
            stopSelf()
            return START_NOT_STICKY
        }
        running = true
        app.appScope.launch { app.mesh.start() }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }
}
