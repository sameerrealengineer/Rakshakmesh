package org.rakshakmesh.device

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.provider.Settings

/** Calls and SMS hand-off. No CALL_PHONE or SEND_SMS permission is used (FR-SOS-01, FR-SOS-06). */
object Phone {
    /** Opens the dialer with the number filled in; the user presses call. */
    fun dial(context: Context, number: String): Boolean = start(
        context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")),
    )

    /**
     * Opens the phone's SMS app pre-filled. Returns true if an SMS app opened.
     * We cannot know whether the user then pressed send, so callers must only record
     * HANDED_TO_SMS_APP, never SENT.
     */
    fun composeSms(context: Context, recipients: List<String>, body: String): Boolean {
        val uri = Uri.parse("smsto:" + recipients.joinToString(";") { Uri.encode(it.filter { c -> c.isDigit() || c == '+' }) })
        val intent = Intent(Intent.ACTION_SENDTO, uri).putExtra("sms_body", body)
        return start(context, intent)
    }

    fun openUrl(context: Context, url: String): Boolean = start(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    fun isAirplaneModeOn(context: Context): Boolean =
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0

    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun batteryPercent(context: Context): Int? =
        context.getSystemService(BatteryManager::class.java)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.takeIf { it in 0..100 }
}
