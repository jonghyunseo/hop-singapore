package app.hop.singapore

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class MainActivity : ComponentActivity() {
    private var sharedText by mutableStateOf<Pair<String,String>?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        if(savedInstanceState==null) receive(intent)
        setContent {
            val vm: HopViewModel = viewModel()
            val travel: TravelViewModel = viewModel()
            LaunchedEffect(sharedText) { sharedText?.let { travel.capture(it.first,it.second); vm.changeTab(1); sharedText=null } }
            HopTheme(vm.theme) { HopApp(vm,travel) }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); receive(intent) }
    private fun receive(intent: Intent?) { if(intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("text/")==true) {
        val body=intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty().take(16000)
        val subject=(intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: intent.getStringExtra(Intent.EXTRA_TITLE)).orEmpty().take(160)
        if(body.isNotBlank() || subject.isNotBlank()) sharedText=body to subject
    } }
}

@Suppress("MissingPermission","DEPRECATION")
suspend fun currentLocation(context: android.content.Context): GeoPoint? {
    if(context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED && context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
    val manager = context.getSystemService(LocationManager::class.java)
    val providers = buildList { if(context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED) add(LocationManager.GPS_PROVIDER); add(LocationManager.NETWORK_PROVIDER) }.filter { manager.isProviderEnabled(it) }
    for(provider in providers) {
        val point = withTimeoutOrNull(if(provider==LocationManager.GPS_PROVIDER) 8000L else 4000L) {
        suspendCancellableCoroutine<GeoPoint?> { cont ->
            if(android.os.Build.VERSION.SDK_INT >= 30) {
                val signal=CancellationSignal(); cont.invokeOnCancellation { signal.cancel() }
                manager.getCurrentLocation(provider,signal,context.mainExecutor) { location -> if(cont.isActive) cont.resume(location?.let { GeoPoint(it.latitude,it.longitude) }) }
            } else {
                val listener=object: LocationListener {
                    override fun onLocationChanged(location: android.location.Location) { manager.removeUpdates(this); if(cont.isActive) cont.resume(GeoPoint(location.latitude,location.longitude)) }
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                }
                cont.invokeOnCancellation { manager.removeUpdates(listener) }; manager.requestSingleUpdate(provider,listener,Looper.getMainLooper())
            }
        }
        }
        if(point!=null) return point
    }
    return null
}
