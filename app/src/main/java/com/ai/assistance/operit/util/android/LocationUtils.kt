package com.ai.assistance.operit.util.android

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Address
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import com.ai.assistance.operit.util.logging.AppLogger
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import android.telephony.TelephonyManager
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.function.Consumer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object LocationUtils {

    private const val TAG = "LocationUtils"

    /**
     * 使用安卓原生API，检测设备是否在中国大陆。
     * 此方法不依赖任何Google Play Services。
     */
    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    suspend fun isDeviceInMainlandChina(context: Context): Boolean {
        // Fast, permissionless heuristics first
        getCountryIsoByTelephony(context)?.let { iso ->
            if (iso.equals("CN", true)) return true
        }
        if (isChinaTimezone()) return true

        if (!hasLocationPermission(context)) {
            AppLogger.w(TAG, "No location permission; returning result from heuristics only.")
            return false
        }
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        try {
            val lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (lastKnownLocation != null) {
                AppLogger.d(TAG, "Got last known location from native API.")
                return getCountryFromLocation(context, lastKnownLocation)
            }
            AppLogger.d(TAG, "No last known location, requesting current location update.")
            val currentLocation = getCurrentLocationNative(context, locationManager)
            return getCountryFromLocation(context, currentLocation)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to get location using native API", e)
            return false
        }
    }

    /**
     * 封装原生API的回调，以协程方式获取当前位置。
     */
    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private suspend fun getCurrentLocationNative(context: Context, locationManager: LocationManager): Location {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return suspendCancellableCoroutine { continuation ->
                val cancellationSignal = CancellationSignal()
                continuation.invokeOnCancellation { cancellationSignal.cancel() }
                locationManager.getCurrentLocation(
                    LocationManager.NETWORK_PROVIDER,
                    cancellationSignal,
                    context.mainExecutor,
                    Consumer { location ->
                        if (location != null) {
                            continuation.resume(location)
                        } else {
                            continuation.resumeWithException(RuntimeException("Failed to get location from Network provider."))
                        }
                    }
                )
            }
        } else {
            @Suppress("DEPRECATION")
            return suspendCancellableCoroutine { continuation ->
                val locationListener = object : android.location.LocationListener {
                    override fun onLocationChanged(location: Location) {
                        locationManager.removeUpdates(this)
                        continuation.resume(location)
                    }
                    override fun onProviderDisabled(provider: String) {
                        locationManager.removeUpdates(this)
                        continuation.resumeWithException(RuntimeException("Provider $provider disabled"))
                    }
                }
                continuation.invokeOnCancellation { locationManager.removeUpdates(locationListener) }
                locationManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, locationListener, context.mainLooper)
            }
        }
    }

    /**
     * 将Location对象通过Geocoder转换为国家代码并进行判断。
     */
    private suspend fun getCountryFromLocation(context: Context, location: Location): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val addresses: List<Address>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // Android 13+ async API — suspend until callback
                    suspendCancellableCoroutine { cont ->
                        try {
                            Geocoder(context, Locale.getDefault()).getFromLocation(
                                location.latitude,
                                location.longitude,
                                1,
                                object : Geocoder.GeocodeListener {
                                    override fun onGeocode(results: MutableList<Address>) {
                                        cont.resume(results)
                                    }

                                    override fun onError(errorMessage: String?) {
                                        AppLogger.w(TAG, "Geocoder error: $errorMessage")
                                        cont.resume(emptyList())
                                    }
                                }
                            )
                        } catch (e: Exception) {
                            cont.resumeWithException(e)
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    Geocoder(context, Locale.getDefault()).getFromLocation(
                        location.latitude,
                        location.longitude,
                        1
                    )
                }

                if (!addresses.isNullOrEmpty()) {
                    val countryCode = addresses[0].countryCode
                    AppLogger.d(TAG, "Detected country code: $countryCode")
                    return@withContext "CN".equals(countryCode, ignoreCase = true)
                }

                // Fallback to coordinate bounds if reverse geocoding failed
                val inBounds = isWithinMainlandChinaBounds(location.latitude, location.longitude)
                if (!inBounds) AppLogger.w(TAG, "Coordinates outside CN bounds; lat=${location.latitude}, lon=${location.longitude}")
                inBounds
            } catch (e: Exception) {
                AppLogger.e(TAG, "Geocoder failed", e)
                false
            }
        }
    }

    private fun isWithinMainlandChinaBounds(lat: Double, lon: Double): Boolean {
        val inRoughBounds = lat in 18.0..54.0 && lon in 73.0..135.0
        if (!inRoughBounds) return false
        // Exclude common non-mainland regions with coarse boxes
        val inTaiwan = lat in 20.5..25.6 && lon in 119.3..122.0
        val inHongKong = lat in 22.1..22.6 && lon in 113.8..114.4
        val inMacau = lat in 22.08..22.23 && lon in 113.52..113.65
        return !(inTaiwan || inHongKong || inMacau)
    }

    private fun getCountryIsoByTelephony(context: Context): String? {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val networkIso = tm.networkCountryIso?.trim()
            val simIso = tm.simCountryIso?.trim()
            val iso = (networkIso?.ifBlank { null } ?: simIso?.ifBlank { null })
            iso?.uppercase(Locale.ROOT)
        } catch (e: Exception) {
            AppLogger.w(TAG, "Telephony country ISO unavailable", e)
            null
        }
    }

    private fun isChinaTimezone(): Boolean {
        return try {
            val tz = TimeZone.getDefault()
            tz.id.equals("Asia/Shanghai", ignoreCase = true) || tz.id.equals("Asia/Urumqi", ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 检查应用是否具有粗略或精细位置权限。
     */
    private fun hasLocationPermission(context: Context): Boolean {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            AppLogger.w(TAG, "Location permission not granted.")
        }
        return hasPermission
    }
}
