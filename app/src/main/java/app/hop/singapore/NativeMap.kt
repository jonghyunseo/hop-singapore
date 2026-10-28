package app.hop.singapore

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.module.http.HttpRequestUtil
import org.maplibre.android.style.expressions.Expression.*
import org.maplibre.android.style.layers.*
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.*

private object MapNetwork {
    private var installed = false
    @Synchronized fun install(context: Context) {
        if(installed) return
        MapLibre.getInstance(context.applicationContext)
        val client = OkHttpClient.Builder().cache(Cache(File(context.cacheDir,"native-map-http"),100L*1024*1024))
            .connectTimeout(8,TimeUnit.SECONDS).readTimeout(12,TimeUnit.SECONDS)
            .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent","hop-singapore/0.3 (+https://github.com/jonghyunseo/hop-singapore)").build()) }
            .addNetworkInterceptor { chain -> val response=chain.proceed(chain.request())
                if(response.request.url.host=="tile.openstreetmap.org" && response.header("Cache-Control")==null) response.newBuilder().header("Cache-Control","public, max-age=604800").build() else response
            }.build()
        HttpRequestUtil.setOkHttpClient(client)
        installed=true
    }
}
private fun coord(p:GeoPoint)=JSONArray().put(p.lon).put(p.lat)
private fun feature(type:String, coordinates:Any, props:JSONObject=JSONObject())=JSONObject().put("type","Feature").put("geometry",JSONObject().put("type",type).put("coordinates",coordinates)).put("properties",props)
private fun collection(features:List<JSONObject>)=JSONObject().put("type","FeatureCollection").put("features",JSONArray(features)).toString()
private val lineColor = mapOf("North South Line" to "#cf4249","East West Line" to "#20885b","North East Line" to "#9651a5","Circle Line" to "#d18d16","Downtown Line" to "#357db8","Thomson-East Coast Line" to "#a57953")

private fun baseStyle(rail:RailData,dark:Boolean,detailed:Boolean):String {
    val features=rail.features.map { f -> feature(if(f.polygon) "Polygon" else "MultiLineString",JSONArray(f.paths.map { path -> JSONArray(path.map(::coord)) }),JSONObject().put("kind",f.kind).put("name",f.name).put("color",lineColor[f.name] ?: "#526078")) }
    val sources=JSONObject().put("context",JSONObject().put("type","geojson").put("data",JSONObject(collection(features))))
    if(detailed) sources.put("streets",JSONObject().put("type","raster").put("tiles",JSONArray().put("https://tile.openstreetmap.org/{z}/{x}/{y}.png")).put("tileSize",256).put("maxzoom",19).put("attribution","© OpenStreetMap contributors"))
    fun layer(id:String,type:String,kind:String,paint:JSONObject)=JSONObject().put("id",id).put("type",type).put("source","context").put("filter",JSONArray().put("==").put("kind").put(kind)).put("paint",paint)
    val layers=JSONArray().put(JSONObject().put("id","background").put("type","background").put("paint",JSONObject().put("background-color",if(dark) "#222d3d" else "#f2f3ed")))
        .put(layer("parks","fill","park",JSONObject().put("fill-color",if(dark) "#30423f" else "#dcebd2")))
        .put(layer("water","fill","water",JSONObject().put("fill-color",if(dark) "#253e53" else "#b6d6e8")))
        .put(layer("roads","line","road",JSONObject().put("line-color",if(dark) "#46536a" else "#ffffff").put("line-width",3)))
    if(detailed) layers.put(JSONObject().put("id","streets").put("type","raster").put("source","streets").put("paint",JSONObject().put("raster-fade-duration",200)))
    layers.put(layer("mrt-halo","line","rail",JSONObject().put("line-color","#ffffff").put("line-width",5).put("line-opacity",0.85)))
    layers.put(layer("mrt","line","rail",JSONObject().put("line-color",JSONArray().put("get").put("color")).put("line-width",3)))
    return JSONObject().put("version",8).put("sources",sources).put("layers",layers).toString()
}
private fun labelBitmap(text:String,foreground:Int,background:Int):Bitmap {
    // Local bitmaps keep station/place labels available without a remote font service.
    val p=Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize=24f; typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL) }
    val label=text.take(42); val w=ceil(p.measureText(label)+24).toInt()
    return Bitmap.createBitmap(w,48,Bitmap.Config.ARGB_8888).apply {
        density=320
        val c=Canvas(this); p.color=background; c.drawRoundRect(RectF(0f,0f,w.toFloat(),48f),12f,12f,p)
        p.color=foreground; c.drawText(label,12f,32f,p)
    }
}

@Composable fun SingaporeMap(
    rail:RailData,places:List<Place>,camera:MapCamera,modifier:Modifier=Modifier,
    selected:Place?=null,selectedStation:Station?=null,radius:Int?=null,
    detailed:Boolean=true,currentLocation:GeoPoint?=null,
    controlsBottom:Dp=0.dp,viewportTop:Dp=0.dp,viewportBottom:Dp=0.dp,viewportLeft:Dp=0.dp,viewportRight:Dp=0.dp,
    onPlace:(Place)->Unit={},onStation:(Station)->Unit={},onLongPress:(GeoPoint)->Unit={},interactive:Boolean=true,
    onMapTap:(GeoPoint)->Unit={},showRail:Boolean=true,showSaved:Boolean=true,browsePlaces:List<Place> = emptyList(),onGesture:()->Unit={}
) {
    val context=LocalContext.current; val colors=MaterialTheme.colorScheme; val dark=colors.surface.luminance()<.3f
    val density=LocalDensity.current.density; val lifecycle=LocalLifecycleOwner.current.lifecycle; val uri=LocalUriHandler.current
    val state=rememberUpdatedState(MapCallbacks(places+listOfNotNull(selected),browsePlaces,onPlace,onStation,onMapTap,onLongPress,onGesture))
    var controller by remember { mutableStateOf<MapLibreMap?>(null) }; var style by remember { mutableStateOf<Style?>(null) }
    var retry by remember { mutableIntStateOf(0) }; var complete by remember { mutableStateOf(false) }; var timedOut by remember { mutableStateOf(false) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val activePlaceImages=remember { mutableSetOf<String>() }
    val connectivity=remember { context.getSystemService(ConnectivityManager::class.java) }
    var online by remember { mutableStateOf(connectivity.getNetworkCapabilities(connectivity.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)==true) }
    DisposableEffect(connectivity) {
        val callback=object:ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network:Network,capabilities:NetworkCapabilities) { online=capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) }
            override fun onLost(network:Network) { online=false }
        }
        connectivity.registerDefaultNetworkCallback(callback)
        onDispose { connectivity.unregisterNetworkCallback(callback) }
    }
    val view=remember {
        MapNetwork.install(context)
        MapView(context,MapLibreMapOptions.createFromAttributes(context).textureMode(true)).apply {
            onCreate(null)
            addOnDidFinishRenderingMapListener { fully -> if(fully && style!=null) complete=true }
            addOnDidFailLoadingMapListener { timedOut=true }
            getMapAsync { map ->
                controller=map; map.setPrefetchZoomDelta(0)
                map.setMinZoomPreference(10.0); map.setMaxZoomPreference(19.0)
                map.setLatLngBoundsForCameraTarget(LatLngBounds.from(1.52,104.16,1.12,103.55))
                map.uiSettings.apply { isLogoEnabled=false; isAttributionEnabled=false; isCompassEnabled=false; isTiltGesturesEnabled=false; setAllGesturesEnabled(interactive) }
                map.addOnCameraIdleListener { val c=map.cameraPosition; c.target?.let { camera.point=GeoPoint(it.latitude,it.longitude) }; camera.zoom=c.zoom; camera.bearing=c.bearing }
                map.addOnCameraMoveStartedListener { reason -> complete=false; timedOut=false; if(reason==MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) { camera.fitPoints=null; state.value.onGesture() } }
                map.addOnMapClickListener { point ->
                    val screen=map.projection.toScreenLocation(point)
                    val features=map.queryRenderedFeatures(RectF(screen.x-16*density,screen.y-16*density,screen.x+16*density,screen.y+16*density),"place-labels","place-dots","station-labels","station-dots")
                    val cb=state.value; val f=features.firstOrNull()
                    val id=f?.getStringProperty("id")
                    val place=(cb.places+cb.browse).firstOrNull { it.id==id }
                    val station=rail.stations.firstOrNull { it.id==id }
                    when { place!=null -> cb.onPlace(place); station!=null -> cb.onStation(station); else -> GeoPoint(point.latitude,point.longitude).takeIf { it.inSingapore }?.let(cb.onTap) }
                    true
                }
                map.addOnMapLongClickListener { p -> GeoPoint(p.latitude,p.longitude).takeIf { it.inSingapore }?.let(state.value.onLong); true }
            }
        }
    }
    DisposableEffect(view,lifecycle) {
        var started=false; var resumed=false
        fun sync() { val s=lifecycle.currentState
            if(s.isAtLeast(Lifecycle.State.STARTED) && !started) { view.onStart(); started=true }
            if(s.isAtLeast(Lifecycle.State.RESUMED) && !resumed) { view.onResume(); resumed=true }
            if(!s.isAtLeast(Lifecycle.State.RESUMED) && resumed) { view.onPause(); resumed=false }
            if(!s.isAtLeast(Lifecycle.State.STARTED) && started) { view.onStop(); started=false }
        }
        val observer=LifecycleEventObserver { _,_ -> sync() }; lifecycle.addObserver(observer); sync()
        onDispose { lifecycle.removeObserver(observer); if(resumed) view.onPause(); if(started) view.onStop(); view.onDestroy(); controller=null }
    }
    val json=remember(rail,dark,detailed) { baseStyle(rail,dark,detailed) }
    LaunchedEffect(complete,detailed) { if(!complete && detailed) { delay(12000); if(!complete) timedOut=true } }
    LaunchedEffect(controller,json,retry) {
        val map=controller ?: return@LaunchedEffect
        complete=false; timedOut=false; style=null
        activePlaceImages.clear()
        map.setStyle(Style.Builder().fromJson(json)) { loaded ->
            loaded.addSource(GeoJsonSource("stations",collection(rail.stations.map { s -> feature("Point",coord(s.point),JSONObject().put("id",s.id).put("icon","station-${s.id}")) })))
            rail.stations.forEach { s -> loaded.addImage("station-${s.id}",labelBitmap("${s.name} ${s.codes.first()}",0xff233044.toInt(),0xf2ffffff.toInt())) }
            loaded.addLayer(CircleLayer("station-dots","stations").withProperties(circleRadius(4f),circleColor("#ffffff"),circleStrokeWidth(2f),circleStrokeColor("#3659d9")))
            loaded.addLayer(SymbolLayer("station-labels","stations").withProperties(iconImage(get("icon")),iconAnchor(Property.ICON_ANCHOR_LEFT),iconOffset(arrayOf(8f,0f)),iconAllowOverlap(false)).apply { minZoom=12.5f })
            loaded.addSource(GeoJsonSource("places",collection(emptyList())))
            loaded.addLayer(CircleLayer("place-dots","places").withProperties(circleRadius(6f),circleColor("#3659d9"),circleStrokeColor("#ffffff"),circleStrokeWidth(2.5f)))
            loaded.addLayer(SymbolLayer("place-labels","places").withFilter(has("icon")).withProperties(iconImage(get("icon")),iconAnchor(Property.ICON_ANCHOR_BOTTOM),iconOffset(arrayOf(0f,-10f)),iconAllowOverlap(false)))
            loaded.addSource(GeoJsonSource("connection",collection(emptyList())))
            loaded.addLayer(LineLayer("connection","connection").withProperties(lineColor("#3659d9"),lineWidth(2.5f),lineDasharray(arrayOf(2f,2f))))
            loaded.addSource(GeoJsonSource("location",collection(emptyList())))
            loaded.addLayer(CircleLayer("location","location").withProperties(circleRadius(7f),circleColor("#2677ee"),circleStrokeColor("#ffffff"),circleStrokeWidth(3f)))
            loaded.addSource(GeoJsonSource("range",collection(emptyList())))
            loaded.addLayerBelow(FillLayer("range","range").withProperties(fillColor("#3659d9"),fillOpacity(.10f)),"station-dots")
            style=loaded
        }
        delay(12000); if(!complete && detailed) timedOut=true
    }
    LaunchedEffect(style,places,browsePlaces,selected,selectedStation,radius,currentLocation,showRail,showSaved,camera.point) {
        val loaded=style ?: return@LaunchedEffect
        val all=(if(showSaved) places else emptyList())+browsePlaces+listOfNotNull(selected)
        val distinct=all.distinctBy { it.id }
        val labeled=(distinct.sortedBy { distanceMetres(it.point,camera.point) }.take(80)+listOfNotNull(selected)).distinctBy { it.id }
        val imageIds=labeled.map { "place-${it.id}" }.toSet()
        (activePlaceImages-imageIds).forEach { loaded.removeImage(it) }; activePlaceImages.clear(); activePlaceImages.addAll(imageIds)
        labeled.forEach { p -> loaded.addImage("place-${p.id}",labelBitmap(p.name,if(p.id==selected?.id) android.graphics.Color.WHITE else 0xff233044.toInt(),if(p.id==selected?.id) 0xff3659d9.toInt() else 0xfaffffff.toInt())) }
        loaded.getSourceAs<GeoJsonSource>("places")?.setGeoJson(collection(distinct.map { p -> feature("Point",coord(p.point),JSONObject().put("id",p.id).apply { if("place-${p.id}" in imageIds) put("icon","place-${p.id}") }) }))
        loaded.getSourceAs<GeoJsonSource>("connection")?.setGeoJson(collection(if(selected!=null && selectedStation!=null) listOf(feature("LineString",JSONArray().put(coord(selected.point)).put(coord(selectedStation.point)))) else emptyList()))
        loaded.getSourceAs<GeoJsonSource>("location")?.setGeoJson(collection(currentLocation?.let { listOf(feature("Point",coord(it))) }.orEmpty()))
        val ring=if(radius!=null && selectedStation!=null) (0..64).map { i -> val a=i*2*PI/64; GeoPoint(selectedStation.point.lat+radius/111320.0*sin(a),selectedStation.point.lon+radius/(111320*cos(selectedStation.point.lat*PI/180))*cos(a)) } else emptyList()
        loaded.getSourceAs<GeoJsonSource>("range")?.setGeoJson(collection(if(ring.isEmpty()) emptyList() else listOf(feature("Polygon",JSONArray().put(JSONArray(ring.map(::coord)))))))
        listOf("mrt","mrt-halo","station-dots","station-labels").forEach { loaded.getLayer(it)?.setProperties(visibility(if(showRail) Property.VISIBLE else Property.NONE)) }
    }
    LaunchedEffect(controller,camera.request,viewportTop,viewportBottom,viewportLeft,viewportRight,viewSize) {
        val map=controller ?: return@LaunchedEffect
        if(viewSize.width==0 || viewSize.height==0) return@LaunchedEffect
        val points=camera.fitPoints
        val (point,zoom)=if(!points.isNullOrEmpty()) framePoints(points,viewSize.width/density.toDouble()-viewportLeft.value-viewportRight.value,viewSize.height/density.toDouble()-viewportTop.value-viewportBottom.value) else camera.point to camera.zoom
        map.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target(LatLng(point.lat,point.lon)).zoom(zoom).bearing(if(points!=null) 0.0 else camera.bearing).tilt(0.0).padding((viewportLeft.value*density).toDouble(),(viewportTop.value*density).toDouble(),(viewportRight.value*density).toDouble(),(viewportBottom.value*density).toDouble()).build()))
    }
    Box(modifier) {
        AndroidView(factory={view},modifier=Modifier.fillMaxSize().onSizeChanged { viewSize=it }.semantics { contentDescription="싱가포르 상세 지도. 도로, 건물, 장소와 MRT 노선. 검색과 장소 목록에서도 선택할 수 있습니다." })
        Column(Modifier.align(if(viewportLeft>0.dp) Alignment.BottomEnd else Alignment.BottomStart).padding(start=8.dp,end=if(viewportLeft>0.dp) 72.dp else 0.dp,bottom=controlsBottom+4.dp)) {
            val metresPerDp=78271.51696*cos(camera.point.lat*PI/180)/2.0.pow(camera.zoom)
            val metres=listOf(10,20,50,100,200,500,1000,2000,5000).lastOrNull { it/metresPerDp<100 } ?: 10
            Surface(shape=CircleShape,color=colors.surface.copy(alpha=.94f)) { Column(Modifier.padding(horizontal=10.dp,vertical=4.dp)) { Text(if(metres>=1000) "${metres/1000} km" else "$metres m",fontSize=10.sp); HorizontalDivider(Modifier.width((metres/metresPerDp).coerceIn(12.0,100.0).dp),thickness=2.dp,color=colors.onSurface) } }
            Surface(shape=CircleShape,color=colors.surface.copy(alpha=.94f)) { TextButton(onClick={uri.openUri("https://www.openstreetmap.org/copyright")},contentPadding=PaddingValues(horizontal=10.dp)) { Text("© OpenStreetMap · MapLibre",fontSize=10.sp) } }
        }
        if(interactive) Column(Modifier.align(Alignment.BottomEnd).padding(end=10.dp,bottom=controlsBottom+8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            if(abs(camera.bearing)>1.0) FilledTonalIconButton(onClick=camera::northUp,modifier=Modifier.size(48.dp)) { Icon(Icons.Rounded.Explore,"북쪽을 위로") }
            FilledTonalIconButton(onClick={camera.zoomBy(1.0)},modifier=Modifier.size(48.dp)) { Icon(Icons.Rounded.Add,"지도 확대") }
            FilledTonalIconButton(onClick={camera.zoomBy(-1.0)},modifier=Modifier.size(48.dp)) { Icon(Icons.Rounded.Remove,"지도 축소") }
        }
        if(detailed && (!online || !complete)) Surface(Modifier.align(Alignment.TopStart).padding(start=12.dp,end=72.dp,top=viewportTop+8.dp),shape=CircleShape,color=colors.surface) {
            Row(Modifier.padding(start=12.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(if(!online) "오프라인 · 저장된 지도와 MRT 표시" else if(timedOut) "상세 지도 연결 실패 · 저장된 지도 표시" else "도로·장소 지도를 불러오는 중",fontSize=12.sp,modifier=Modifier.weight(1f,false).padding(vertical=8.dp).semantics { liveRegion=LiveRegionMode.Polite })
                if(timedOut || !online) TextButton(onClick={retry++},enabled=online) { Text("재시도") } else CircularProgressIndicator(Modifier.padding(10.dp).size(16.dp),strokeWidth=2.dp)
            }
        }
    }
}
private data class MapCallbacks(val places:List<Place>,val browse:List<Place>,val onPlace:(Place)->Unit,val onStation:(Station)->Unit,val onTap:(GeoPoint)->Unit,val onLong:(GeoPoint)->Unit,val onGesture:()->Unit)
