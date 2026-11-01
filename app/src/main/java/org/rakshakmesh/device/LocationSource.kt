package org.rakshakmesh.device

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import org.rakshakmesh.core.Fix
import kotlin.coroutines.resume

/**
 * One-shot location only (PRD 5.5). GPS works with no network. Never runs in the background.
 * Uses Fused Location when Play services exist, otherwise the plain GPS provider.
 */
class LocationSource(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private val hasPlayServices: Boolean by lazy {
        runCatching {
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        }.getOrDefault(false)
    }

    /** Fresh fix, or null after [timeoutMs] (FR-SOS-03: 20 s). */
    @SuppressLint("MissingPermission")
    suspend fun currentFix(timeoutMs: Long = 20_000): Fix? {
        if (!hasPermission()) return null
        return withTimeoutOrNull(timeoutMs) {
            if (hasPlayServices) {
                val cts = CancellationTokenSource()
                try {
                    LocationServices.getFusedLocationProviderClient(context)
                        .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                        .await()?.toFix()
                } catch (e: Exception) {
                    null
                } finally {
                    cts.cancel()
                }
            } else {
                gpsFix()
            }
        }
    }

    /** Last known fix from any provider, without turning the GPS on. */
    @SuppressLint("MissingPermission")
    suspend fun lastKnown(): Fix? {
        if (!hasPermission()) return null
        val fused = if (hasPlayServices) {
            runCatching { LocationServices.getFusedLocationProviderClient(context).lastLocation.await() }.getOrNull()
        } else null
        val lm = context.getSystemService(LocationManager::class.java)
        val platform = runCatching {
            lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
        }.getOrNull()
        return listOfNotNull(fused, platform).maxByOrNull { it.time }?.toFix()
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private suspend fun gpsFix(): Fix? = suspendCancellableCoroutine { cont ->
        val lm = context.getSystemService(LocationManager::class.java)
        val provider = when {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
        if (provider == null) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val signal = CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            lm.getCurrentLocation(provider, signal, context.mainExecutor) { loc ->
                if (cont.isActive) cont.resume(loc?.toFix())
            }
        } else {
            val listener = android.location.LocationListener { loc ->
                if (cont.isActive) cont.resume(loc.toFix())
            }
            cont.invokeOnCancellation { lm.removeUpdates(listener) }
            lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
        }
    }

    private fun Location.toFix() = Fix(latitude, longitude, if (hasAccuracy()) accuracy else null, time)
}
