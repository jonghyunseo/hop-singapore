package app.hop.singapore

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.*

fun worldX(lon: Double) = (lon + 180) / 360
fun worldY(lat: Double): Double { val s = sin(lat.coerceIn(-85.0,85.0) * Math.PI / 180); return .5 - ln((1 + s) / (1 - s)) / (4 * Math.PI) }
private fun fromWorld(x: Double, y: Double) = GeoPoint(atan(sinh(Math.PI * (1 - 2 * y))) * 180 / Math.PI, x * 360 - 180)
@Stable class MapCamera(lat: Double = 1.2878, lon: Double = 103.8529, zoom: Double = 15.3) {
    var point by mutableStateOf(GeoPoint(lat, lon)); var zoom by mutableDoubleStateOf(zoom)
    fun focus(p: GeoPoint, z: Double = 15.3) { point = p; zoom = z }
    fun screen(p: GeoPoint, width: Int, height: Int): Offset { val scale = 256 * 2.0.pow(zoom); return Offset(((worldX(p.lon) - worldX(point.lon)) * scale + width / 2).toFloat(), ((worldY(p.lat) - worldY(point.lat)) * scale + height / 2).toFloat()) }
    fun at(offset: Offset, width: Int, height: Int): GeoPoint { val scale = 256 * 2.0.pow(zoom); return fromWorld(worldX(point.lon) + (offset.x - width / 2) / scale, worldY(point.lat) + (offset.y - height / 2) / scale) }
    fun transform(centroid: Offset, pan: Offset, factor: Float, size: IntSize) {
        val anchor = at(centroid, size.width, size.height)
        zoom = (zoom + log2(factor.toDouble())).coerceIn(10.0, 19.0)
        val scale = 256 * 2.0.pow(zoom)
        val next = fromWorld(worldX(anchor.lon) - (centroid.x - size.width / 2 + pan.x) / scale, worldY(anchor.lat) - (centroid.y - size.height / 2 + pan.y) / scale)
        point = GeoPoint(next.lat.coerceIn(1.12,1.52), next.lon.coerceIn(103.55,104.16))
    }
    companion object { val StateSaver = Saver<MapCamera,List<Double>>(save = { listOf(it.point.lat,it.point.lon,it.zoom) }, restore = { MapCamera(it[0],it[1],it[2]) }) }
}
@Composable fun rememberMapCamera() = rememberSaveable(saver = MapCamera.StateSaver) { MapCamera() }

private data class Tile(val z: Int, val x: Int, val y: Int) { val key = "$z-$x-$y" }
private class TileCache(context: Context) {
    private val directory = File(context.cacheDir,"map-tiles").apply { mkdirs() }
    private val memory = LruCache<String, Bitmap>(72)
    private val slots = Semaphore(3)
    suspend fun get(tile: Tile): Bitmap? = withContext(Dispatchers.IO) { slots.withPermit {
        memory.get(tile.key)?.let { return@withPermit it }
        val file = File(directory,tile.key)
        if(file.exists() && System.currentTimeMillis() - file.lastModified() < 7L * 24 * 3600 * 1000) return@withPermit BitmapFactory.decodeFile(file.path)?.also { memory.put(tile.key,it) }
        try {
            val c = URL("https://tile.openstreetmap.org/${tile.z}/${tile.x}/${tile.y}.png").openConnection() as HttpURLConnection
            c.connectTimeout = 5000; c.readTimeout = 5000
            c.setRequestProperty("User-Agent", "hop-android/0.1 (personal Singapore MRT planner)")
            try {
                if(file.exists()) c.ifModifiedSince = file.lastModified()
                if(c.responseCode == 304) { file.setLastModified(System.currentTimeMillis()); return@withPermit BitmapFactory.decodeFile(file.path)?.also { memory.put(tile.key,it) } }
                if(c.responseCode != 200) return@withPermit null
                val bytes = c.inputStream.use { it.readBytes() }
                val bitmap = BitmapFactory.decodeByteArray(bytes,0,bytes.size) ?: return@withPermit null
                file.writeBytes(bytes); memory.put(tile.key,bitmap)
                // Only visible tiles are fetched. Bound the cache without prefetching.
                if(directory.listFiles().orEmpty().size > 1200) directory.listFiles().orEmpty().sortedBy { it.lastModified() }.take(100).forEach { it.delete() }
                bitmap
            } finally { c.disconnect() }
        } catch(_: Exception) { if(file.exists()) BitmapFactory.decodeFile(file.path) else null }
    }}
}
private val railColors = mapOf("North South Line" to Color(0xFFCF4249), "East West Line" to Color(0xFF20885B), "North East Line" to Color(0xFF9651A5), "Circle Line" to Color(0xFFD18D16), "Downtown Line" to Color(0xFF357DB8), "Thomson-East Coast Line" to Color(0xFFA57953))
private data class PreparedFeature(val kind: String, val name: String, val path: Path)

@Composable fun SingaporeMap(
    rail: RailData, places: List<Place>, camera: MapCamera, modifier: Modifier = Modifier,
    selected: Place? = null, selectedStation: Station? = null, radius: Int? = null,
    detailed: Boolean = false, currentLocation: GeoPoint? = null,
    onPlace: (Place) -> Unit = {}, onStation: (Station) -> Unit = {}, onLongPress: (GeoPoint) -> Unit = {}, interactive: Boolean = true
) {
    val colors = MaterialTheme.colorScheme; val primary = colors.primary; val surface = colors.surface; val ink = colors.onSurface
    val dark = surface.luminance() < .3f
    val ctx = LocalContext.current; val uri = LocalUriHandler.current; val density = LocalDensity.current.density
    var size by remember { mutableStateOf(IntSize.Zero) }
    val cache = remember(ctx) { TileCache(ctx) }; val bitmaps = remember { mutableStateMapOf<String,Bitmap>() }
    val zoom = camera.zoom; val center = camera.point
    val tiles = remember(zoom,center,size,detailed) {
        if(!detailed || size.width == 0) emptyList() else {
            val z = floor(zoom).toInt(); val n = 2.0.pow(z); val tileSize = 256 * 2.0.pow(zoom-z)
            val cx = worldX(center.lon) * n; val cy = worldY(center.lat) * n
            buildList { for(x in floor(cx-size.width/2/tileSize).toInt()..floor(cx+size.width/2/tileSize).toInt()) for(y in floor(cy-size.height/2/tileSize).toInt()..floor(cy+size.height/2/tileSize).toInt()) add(Tile(z,x,y)) }
        }
    }
    LaunchedEffect(tiles) {
        delay(180)
        coroutineScope { tiles.forEach { tile -> launch { cache.get(tile)?.let { bitmaps[tile.key] = it } } } }
        bitmaps.keys.filter { key -> tiles.none { it.key == key } }.toList().forEach { bitmaps.remove(it) }
    }
    val ox = worldX(103.85); val oy = worldY(1.3); val unit = 256 * 2.0.pow(16)
    val features = remember(rail) { rail.features.map { f ->
        val path = Path()
        f.paths.forEach { points -> points.forEachIndexed { i,p -> val x = ((worldX(p.lon)-ox)*unit).toFloat(); val y = ((worldY(p.lat)-oy)*unit).toFloat(); if(i==0) path.moveTo(x,y) else path.lineTo(x,y) }; if(f.polygon) path.close() }
        PreparedFeature(f.kind,f.name,path)
    }}
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL) } }
    Box(modifier.background(if(dark) Color(0xFF222D3D) else Color(0xFFF1F3F7))) {
        Canvas(Modifier.fillMaxSize().onSizeChanged { size = it }.semantics { contentDescription = "싱가포르 MRT 노선과 저장 장소 지도. 장소 목록에서도 선택할 수 있습니다." }
            .pointerInput(interactive) { if(interactive) detectTransformGestures { centroid,pan,factor,_ -> camera.transform(centroid,pan,factor,size) } }
            .pointerInput(places,rail,interactive) { if(interactive) detectTapGestures(onDoubleTap = { camera.transform(it,Offset.Zero,1.7f,size) }, onLongPress = { val p=camera.at(it,size.width,size.height); if(p.inSingapore) onLongPress(p) }, onTap = { tap ->
                places.minByOrNull { (camera.screen(it.point,size.width,size.height)-tap).getDistance() }?.let { p -> if((camera.screen(p.point,size.width,size.height)-tap).getDistance() < 25*density) { onPlace(p); return@detectTapGestures } }
                rail.stations.minByOrNull { (camera.screen(it.point,size.width,size.height)-tap).getDistance() }?.let { s -> if((camera.screen(s.point,size.width,size.height)-tap).getDistance() < 20*density) onStation(s) }
            }) }) {
            val scale = 2.0.pow(camera.zoom-16).toFloat(); val origin = camera.screen(GeoPoint(1.3,103.85),size.width.toInt(),size.height.toInt())
            withTransform({ translate(origin.x,origin.y); scale(scale,scale,Offset.Zero) }) {
                features.filter { it.kind != "rail" }.forEach { f ->
                    val color = when(f.kind) { "water" -> if(dark) Color(0xFF253E53) else Color(0xFFD9ECF4); "park" -> if(dark) Color(0xFF30423F) else Color(0xFFE2EDE1); else -> if(dark) Color(0xFF46536A) else Color.White }
                    drawPath(f.path,color,style = if(f.kind=="road") Stroke(2*density/scale) else Fill)
                }
            }
            tiles.forEach { tile -> bitmaps[tile.key]?.let { bitmap ->
                val n = 2.0.pow(tile.z); val corner = camera.screen(fromWorld(tile.x/n,tile.y/n),size.width.toInt(),size.height.toInt()); val tileSize = ceil(256*2.0.pow(camera.zoom-tile.z)).toInt()+1
                drawImage(bitmap.asImageBitmap(),dstOffset=IntOffset(corner.x.roundToInt(),corner.y.roundToInt()),dstSize=IntSize(tileSize,tileSize), colorFilter=if(dark) ColorFilter.tint(Color(0xFF8B9BB8),BlendMode.Multiply) else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(.3f) }))
            }}
            withTransform({ translate(origin.x,origin.y); scale(scale,scale,Offset.Zero) }) {
                features.filter { it.kind == "rail" }.forEach { drawPath(it.path,surface.copy(alpha=.8f),style=Stroke(5*density/scale)); drawPath(it.path,railColors[it.name] ?: primary,style=Stroke(2.8f*density/scale)) }
            }
            fun xy(p: GeoPoint) = camera.screen(p,size.width.toInt(),size.height.toInt())
            if(radius != null && selectedStation != null) {
                val pixelRadius = (radius / (40_075_016.686*cos(selectedStation.point.lat*Math.PI/180)) * 256*2.0.pow(camera.zoom)).toFloat()
                drawCircle(primary.copy(alpha=.08f),pixelRadius,xy(selectedStation.point)); drawCircle(primary.copy(alpha=.65f),pixelRadius,xy(selectedStation.point),style=Stroke(1.5f*density,pathEffect=PathEffect.dashPathEffect(floatArrayOf(6*density,5*density))))
            }
            rail.stations.forEach { s -> val p=xy(s.point); if(p.x in -10f..size.width+10f && p.y in -10f..size.height+10f) { drawCircle(surface,if(s.codes.size>1) 4.3f*density else 3f*density,p); drawCircle(Color(0xFF8793A8),if(s.codes.size>1) 4.3f*density else 3f*density,p,style=Stroke(density)) } }
            if(selected != null && selectedStation != null) { drawLine(primary,xy(selected.point),xy(selectedStation.point),2*density,pathEffect=PathEffect.dashPathEffect(floatArrayOf(5*density,5*density))); drawCircle(surface,6*density,xy(selectedStation.point)); drawCircle(primary,6*density,xy(selectedStation.point),style=Stroke(2*density)) }
            fun label(text: String, p: Offset, foreground: Color, background: Color) {
                paint.textSize=12*density; val width=paint.measureText(text)+16*density; val left=(p.x-width/2).coerceIn(4*density,max(4*density,size.width-width-4*density)); val top=(p.y-37*density).coerceAtLeast(4*density)
                drawRoundRect(background,Offset(left,top),androidx.compose.ui.geometry.Size(width,28*density),androidx.compose.ui.geometry.CornerRadius(14*density)); paint.color=foreground.toArgb()
                drawContext.canvas.nativeCanvas.drawText(text,left+8*density,top+19*density,paint)
            }
            places.sortedBy { it.id == selected?.id }.forEach { place -> val p=xy(place.point); if(p.x in 0f..size.width.toFloat() && p.y in 0f..size.height.toFloat()) { drawCircle(surface,8*density,p); drawCircle(if(place.id==selected?.id) primary else ink,5.5f*density,p); if(place.id==selected?.id) label(place.name.take(18),p,colors.onPrimary,primary) } }
            selectedStation?.let { s -> val p=xy(s.point); paint.textSize=11*density; paint.color=ink.toArgb(); drawContext.canvas.nativeCanvas.drawText(s.name,(p.x+9*density).coerceAtMost(size.width-paint.measureText(s.name)-4*density),p.y+17*density,paint) }
            currentLocation?.let { drawCircle(primary.copy(alpha=.15f),22*density,xy(it)); drawCircle(Color.White,9*density,xy(it)); drawCircle(primary,6*density,xy(it)) }
        }
        Surface(Modifier.align(Alignment.BottomStart).padding(8.dp),shape=RoundedCornerShape(10.dp),color=colors.surface.copy(alpha=.95f)) {
            TextButton(onClick={uri.openUri("https://www.openstreetmap.org/copyright")},contentPadding=PaddingValues(horizontal=8.dp,vertical=0.dp),modifier=Modifier.heightIn(min=48.dp)) { Text("© OpenStreetMap · sgraildata",fontSize=10.sp) }
        }
        if(interactive) Column(Modifier.align(Alignment.BottomEnd).padding(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            listOf(Icons.Rounded.Add to 1.0,Icons.Rounded.Remove to -1.0).forEach { (icon,delta) -> FilledTonalIconButton(onClick={camera.zoom=(camera.zoom+delta).coerceIn(10.0,19.0)},modifier=Modifier.size(48.dp),shape=CircleShape) { Icon(icon,if(delta>0) "지도 확대" else "지도 축소") } }
        }
        if(detailed && tiles.isNotEmpty() && tiles.none { bitmaps.containsKey(it.key) }) Surface(Modifier.align(Alignment.TopStart).padding(8.dp),shape=CircleShape) { Text("상세 지도 연결 중 · 간략 지도 사용 가능",Modifier.padding(8.dp),fontSize=11.sp) }
    }
}
