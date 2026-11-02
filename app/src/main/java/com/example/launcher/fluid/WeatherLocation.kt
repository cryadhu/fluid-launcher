package com.example.launcher.fluid

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.*
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import kotlinx.coroutines.*
import kotlin.coroutines.resume

internal data class WeatherLocation(val latitude:Double,val longitude:Double,val name:String)

/** Foreground one-shot lookup. No coordinates are saved and no background tracking is registered. */
@Suppress("MissingPermission", "DEPRECATION")
internal suspend fun currentWeatherLocation(context:Context):WeatherLocation {
    check(ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED){"Allow location access or enter a city for weather."}
    val manager=context.getSystemService(LocationManager::class.java)
    check(LocationManagerCompat.isLocationEnabled(manager)){"Turn on phone location, or enter a city for weather."}
    val providers=manager.getProviders(true).sortedBy{when(it){"network"->0;"fused"->1;else->2}}
    val recent=providers.mapNotNull{runCatching{manager.getLastKnownLocation(it)}.getOrNull()}.filter{System.currentTimeMillis()-it.time in 0..30*60_000L}.maxByOrNull{it.time}
    val location=recent ?: withTimeoutOrNull(20_000) {
        providers.firstNotNullOfOrNull { provider ->
            suspendCancellableCoroutine<Location?> { continuation ->
                val signal=CancellationSignal()
                continuation.invokeOnCancellation{signal.cancel()}
                runCatching { LocationManagerCompat.getCurrentLocation(manager,provider,signal,ContextCompat.getMainExecutor(context)){ value -> if(continuation.isActive)continuation.resume(value) } }
                    .onFailure{if(continuation.isActive)continuation.resume(null)}
            }
        }
    } ?: error("Couldn't locate this phone. Try again or enter a city.")
    val name=withContext(Dispatchers.IO){runCatching {
        if(Geocoder.isPresent())Geocoder(context).getFromLocation(location.latitude,location.longitude,1)?.firstOrNull()?.let{it.locality ?: it.subAdminArea ?: it.adminArea} else null
    }.getOrNull()} ?: "Current location"
    return WeatherLocation(location.latitude,location.longitude,name)
}
