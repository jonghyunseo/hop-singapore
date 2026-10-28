package app.hop.singapore

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlin.math.*

fun worldX(lon: Double) = (lon + 180) / 360
fun worldY(lat: Double): Double { val s = sin(lat.coerceIn(-85.0,85.0) * Math.PI / 180); return .5 - ln((1 + s) / (1 - s)) / (4 * Math.PI) }
private fun fromWorld(x: Double, y: Double) = GeoPoint(atan(sinh(Math.PI * (1 - 2 * y))) * 180 / Math.PI, x * 360 - 180)
fun framePoints(points:List<GeoPoint>,widthDp:Double,heightDp:Double):Pair<GeoPoint,Double> {
    require(points.isNotEmpty())
    val xs=points.map { worldX(it.lon) }; val ys=points.map { worldY(it.lat) }
    val dx=(xs.max()-xs.min()).coerceAtLeast(1e-9); val dy=(ys.max()-ys.min()).coerceAtLeast(1e-9)
    val zoom=min(log2((widthDp-64).coerceAtLeast(32.0)/(512*dx)),log2((heightDp-64).coerceAtLeast(32.0)/(512*dy))).coerceIn(10.0,16.5)
    return fromWorld((xs.max()+xs.min())/2,(ys.max()+ys.min())/2) to zoom
}
@Stable class MapCamera(lat: Double = 1.2878, lon: Double = 103.8529, zoom: Double = 15.3) {
    var point by mutableStateOf(GeoPoint(lat, lon)); var zoom by mutableDoubleStateOf(zoom)
    var viewportShift by mutableStateOf(Offset.Zero)
    var bearing by mutableDoubleStateOf(0.0)
    var request by mutableIntStateOf(0)
    var fitPoints: List<GeoPoint>? = null
    fun focus(p: GeoPoint, z: Double = 15.3) { point = p; zoom = z; fitPoints = null; request++ }
    fun zoomBy(delta: Double) { zoom = (zoom + delta).coerceIn(10.0,19.0); fitPoints = null; request++ }
    fun northUp() { bearing = 0.0; fitPoints = null; request++ }
    fun fit(points: List<GeoPoint>) { if(points.isEmpty()) return; if(points.size==1) { focus(points.first(),16.0); return }; fitPoints=points; request++ }
    fun screen(p: GeoPoint, width: Int, height: Int): Offset { val scale = 256 * 2.0.pow(zoom); return Offset(((worldX(p.lon) - worldX(point.lon)) * scale + width / 2 + viewportShift.x).toFloat(), ((worldY(p.lat) - worldY(point.lat)) * scale + height / 2 + viewportShift.y).toFloat()) }
    fun at(offset: Offset, width: Int, height: Int): GeoPoint { val scale = 256 * 2.0.pow(zoom); return fromWorld(worldX(point.lon) + (offset.x - width / 2 - viewportShift.x) / scale, worldY(point.lat) + (offset.y - height / 2 - viewportShift.y) / scale) }
    fun transform(centroid: Offset, pan: Offset, factor: Float, size: IntSize) {
        val anchor = at(centroid, size.width, size.height)
        zoom = (zoom + log2(factor.toDouble())).coerceIn(10.0, 19.0)
        val scale = 256 * 2.0.pow(zoom)
        val next = fromWorld(worldX(anchor.lon) - (centroid.x - size.width / 2 - viewportShift.x + pan.x) / scale, worldY(anchor.lat) - (centroid.y - size.height / 2 - viewportShift.y + pan.y) / scale)
        point = GeoPoint(next.lat.coerceIn(1.12,1.52), next.lon.coerceIn(103.55,104.16))
    }
    companion object { val StateSaver = Saver<MapCamera,List<Double>>(save = { listOf(it.point.lat,it.point.lon,it.zoom) }, restore = { MapCamera(it[0],it[1],it[2]) }) }
}
@Composable fun rememberMapCamera() = rememberSaveable(saver = MapCamera.StateSaver) { MapCamera() }
