package app.hop.singapore

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Location updates exist only while the visible map is explicitly following the user. */
@Suppress("MissingPermission","DEPRECATION")
@Composable fun FollowLocation(enabled:Boolean,onFix:(GeoPoint)->Unit) {
    val context=LocalContext.current; val lifecycle=LocalLifecycleOwner.current.lifecycle
    val callback=rememberUpdatedState(onFix)
    DisposableEffect(enabled,lifecycle) {
        val manager=context.getSystemService(LocationManager::class.java)
        val permitted=context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED || context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
        var listening=false
        val listener=object:LocationListener {
            override fun onLocationChanged(location:Location) { GeoPoint(location.latitude,location.longitude).takeIf { it.inSingapore }?.let(callback.value) }
            override fun onStatusChanged(provider:String?,status:Int,extras:Bundle?) {}
        }
        fun sync() {
            val active=enabled && permitted && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if(active && !listening) {
                listOf(LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER).filter { manager.isProviderEnabled(it) }.forEach { provider -> runCatching { manager.requestLocationUpdates(provider,5000L,5f,listener,Looper.getMainLooper()) } }
                listening=true
            } else if(!active && listening) { manager.removeUpdates(listener); listening=false }
        }
        val observer=LifecycleEventObserver { _,_->sync() }; lifecycle.addObserver(observer); sync()
        onDispose { lifecycle.removeObserver(observer); manager.removeUpdates(listener) }
    }
}
