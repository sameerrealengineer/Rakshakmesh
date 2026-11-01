package org.rakshakmesh.mesh

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

/** Runtime permissions, per Android version. */
object Permissions {
    /** What the offline mesh needs to find and talk to nearby phones. */
    fun mesh(): Array<String> = buildList {
        when {
            Build.VERSION.SDK_INT >= 33 -> {
                add(Manifest.permission.BLUETOOTH_SCAN); add(Manifest.permission.BLUETOOTH_ADVERTISE)
                add(Manifest.permission.BLUETOOTH_CONNECT); add(Manifest.permission.NEARBY_WIFI_DEVICES)
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            Build.VERSION.SDK_INT >= 31 -> {
                add(Manifest.permission.BLUETOOTH_SCAN); add(Manifest.permission.BLUETOOTH_ADVERTISE)
                add(Manifest.permission.BLUETOOTH_CONNECT); add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            else -> add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }.toTypedArray()

    /** Everything the app asks for during setup: location, mesh, notifications, microphone. */
    fun all(): Array<String> = (
        mesh().toList() +
            Manifest.permission.ACCESS_COARSE_LOCATION +
            Manifest.permission.RECORD_AUDIO +
            (if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList())
        ).distinct().toTypedArray()

    fun granted(context: Context, p: String) =
        ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    fun hasMesh(context: Context) = mesh().all { granted(context, it) }

    fun hasMic(context: Context) = granted(context, Manifest.permission.RECORD_AUDIO)

    /** Before Android 12, Bluetooth discovery only works with location services switched on. */
    fun locationServicesNeeded(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 31) return false
        val lm = context.getSystemService(LocationManager::class.java) ?: return false
        return !LocationManagerCompat.isLocationEnabled(lm)
    }
}
