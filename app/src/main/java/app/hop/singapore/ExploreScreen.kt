@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package app.hop.singapore

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable fun MapSearchScreen(vm:HopViewModel,center:GeoPoint,onDismiss:()->Unit,onPlace:(Place)->Unit,onStation:(Station)->Unit) {
    val colors=MaterialTheme.colorScheme; val keyboard=LocalSoftwareKeyboardController.current
    val searchCenter=remember { center }
    val top=WindowInsets.statusBars.asPaddingValues().calculateTopPadding(); val bottom=WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val query=vm.mapQuery.trim()
    val saved=vm.places.filter { query.isNotBlank() && (it.name+it.subtitle+it.memo).contains(query,true) }
    val stations=vm.rail?.stations.orEmpty().filter { query.isNotBlank() && (it.name+it.codes.joinToString()).contains(query,true) }.take(20)
    fun search() { keyboard?.hide(); vm.searchMap(searchCenter) }
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        Surface(Modifier.fillMaxSize(),color=colors.background) {
            Column(Modifier.fillMaxSize().padding(top=top,bottom=bottom).imePadding()) {
                Row(Modifier.fillMaxWidth().padding(8.dp),verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick=onDismiss) { Icon(Icons.AutoMirrored.Rounded.ArrowBack,"지도 검색 닫기") }
                    OutlinedTextField(vm.mapQuery,{vm.mapQuery=it},label={Text("장소·주소·MRT 역 검색")},modifier=Modifier.weight(1f),shape=CircleShape,singleLine=true,keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={search()}),trailingIcon={IconButton(onClick={vm.mapQuery="";vm.clearMapResults()}) { Icon(Icons.Rounded.Close,"검색어 지우기") }})
                }
                Button(onClick=::search,enabled=query.isNotBlank() && !vm.mapSearching,modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp).heightIn(min=48.dp)) { Icon(Icons.Rounded.Search,null); Spacer(Modifier.width(8.dp)); Text("싱가포르에서 검색") }
                if(vm.mapSearching) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top=8.dp))
                LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    if(query.isBlank()) {
                        item { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Text("최근 검색",Modifier.weight(1f),fontWeight=FontWeight.Bold); TextButton(onClick=vm::clearSearchHistory) { Text("지우기") } } }
                        items(vm.searchHistory) { h -> ListItem(headlineContent={Text(h)},leadingContent={Icon(Icons.Rounded.History,null)},modifier=Modifier.clickable { vm.mapQuery=h;search() }) }
                        item { Text("장소 이름, 도로명, 우편번호, 역 코드를 검색해 보세요.\n저장한 장소와 MRT 역은 인터넷 없이도 검색돼요.",fontSize=14.sp,color=colors.onSurfaceVariant) }
                    }
                    if(saved.isNotEmpty()) item { Text("저장한 곳",fontWeight=FontWeight.Bold) }
                    items(saved,key={"saved-${it.id}"}) { p -> SearchPlaceRow(p,"저장됨",{onPlace(p);onDismiss()}) }
                    if(stations.isNotEmpty()) item { Text("MRT 역",fontWeight=FontWeight.Bold) }
                    items(stations,key={"station-${it.id}"}) { s -> Surface(onClick={onStation(s);onDismiss()},shape=RoundedCornerShape(20.dp)) { ListItem(headlineContent={Text(s.name)},supportingContent={Text(s.codes.joinToString(" · "))},leadingContent={Icon(Icons.Rounded.Train,null)}) } }
                    vm.mapSearchError?.let { error -> item { Text(error,color=colors.error,fontSize=14.sp) } }
                    if(vm.mapResults.isNotEmpty()) item { Row(verticalAlignment=Alignment.CenterVertically) { Text("지도 검색 ${vm.mapResults.size}곳",Modifier.weight(1f),fontWeight=FontWeight.Bold); TextButton(onClick=onDismiss) { Text("지도에서 보기") } } }
                    items(vm.mapResults,key={"online-${it.id}"}) { p -> SearchPlaceRow(p,formatDistance(distanceMetres(searchCenter,p.point)),{onPlace(p);onDismiss()}) }
                    if(vm.mapResults.isNotEmpty()) item { Text("© OpenStreetMap · Photon 검색 결과",fontSize=12.sp,color=colors.onSurfaceVariant) }
                }
            }
        }
    }
}
@Composable private fun SearchPlaceRow(place:Place,meta:String,onClick:()->Unit) {
    Surface(onClick=onClick,shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface) {
        ListItem(headlineContent={Text(place.name,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis)},supportingContent={Text(place.subtitle.ifBlank { "싱가포르" },maxLines=2,overflow=TextOverflow.Ellipsis)},leadingContent={Icon(Icons.Rounded.Place,null)},trailingContent={Text(meta,fontSize=11.sp)})
    }
}
@Composable fun MapLayersSheet(vm:HopViewModel,onDismiss:()->Unit,onOverview:()->Unit) {
    ModalBottomSheet(onDismissRequest=onDismiss,containerColor=MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("지도 표시",fontSize=22.sp,fontWeight=FontWeight.SemiBold)
            Text("도로명·건물·상점명이 보이는 상세 지도를 기본으로 사용해요.",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            ListItem(headlineContent={Text("상세 지도")},supportingContent={Text(if(vm.detailedMap) "OpenStreetMap 도로·장소 표시" else "저장된 도심 간략 지도")},trailingContent={Switch(vm.detailedMap,{vm.changeMap()})})
            ListItem(headlineContent={Text("MRT 노선과 역 이름")},trailingContent={Switch(vm.showRail,{vm.toggleRail()})})
            ListItem(headlineContent={Text("저장한 장소")},trailingContent={Switch(vm.showSaved,{vm.toggleSaved()})})
            OutlinedButton(onClick={onOverview();onDismiss()},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Icon(Icons.Rounded.ZoomOutMap,null); Spacer(Modifier.width(8.dp)); Text("싱가포르 전체 보기") }
            Text("상세 지도는 인터넷이 필요해요. 이미 본 타일은 캐시되고, 연결에 실패해도 MRT와 저장한 장소는 남아요.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
        }
    }
}
@Composable fun NearbyResultsSheet(vm:HopViewModel,center:GeoPoint,onDismiss:()->Unit,onPlace:(Place)->Unit) {
    ModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=MaterialTheme.colorScheme.surface) {
        Text("${vm.mapCategory?.title ?: "검색 결과"} ${vm.mapResults.size}곳",Modifier.padding(horizontal=20.dp),fontSize=22.sp,fontWeight=FontWeight.SemiBold)
        Text("지도 중심 주변 · 직선거리 순",Modifier.padding(horizontal=20.dp,vertical=8.dp),fontSize=12.sp)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max=520.dp),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            items(vm.mapResults.sortedBy { distanceMetres(center,it.point) },key={it.id}) { p -> SearchPlaceRow(p,formatDistance(distanceMetres(center,p.point)),{onPlace(p);onDismiss()}) }
        }
    }
}
@Composable fun StationMapCard(station:Station,vm:HopViewModel,modifier:Modifier,onClose:()->Unit) {
    Surface(modifier,shape=RoundedCornerShape(28.dp),shadowElevation=5.dp) {
        Column(Modifier.padding(horizontal=16.dp,vertical=8.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) { Icon(Icons.Rounded.Train,null); Text(station.name,Modifier.weight(1f).padding(start=8.dp),fontSize=18.sp,fontWeight=FontWeight.SemiBold); IconButton(onClick=onClose) { Icon(Icons.Rounded.Close,"역 정보 닫기") } }
            Text(station.codes.joinToString(" · "),fontSize=13.sp,color=MaterialTheme.colorScheme.primary)
            Text("800m 안에 저장한 곳 ${within(station,vm.places,800).size}곳",Modifier.padding(vertical=6.dp),fontSize=13.sp)
            Button(onClick={vm.chooseStation(station)},modifier=Modifier.fillMaxWidth()) { Text("이 역 주변의 저장한 곳 보기") }
        }
    }
}
@Composable fun PlaceActionsSheet(place:Place,station:Station?,onDismiss:()->Unit) {
    val context=LocalContext.current; var error by remember { mutableStateOf<String?>(null) }; var fromStation by remember { mutableStateOf(false) }
    fun open(url:String) { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url))) }.onSuccess { onDismiss() }.onFailure { error="지도를 열 수 있는 앱이 없어요." } }
    ModalBottomSheet(onDismissRequest=onDismiss,containerColor=MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal=20.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(place.name,fontSize=22.sp,fontWeight=FontWeight.SemiBold)
            if(place.subtitle.isNotBlank()) Text(place.subtitle,fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text("%.5f, %.5f".format(java.util.Locale.US,place.point.lat,place.point.lon),fontSize=12.sp)
            if(station!=null) Row(verticalAlignment=Alignment.CenterVertically) { Text("${station.name}에서 출발",Modifier.weight(1f),fontSize=14.sp); Switch(fromStation,{fromStation=it}) }
            Text(if(fromStation) "선택한 역을 출발점으로 Google 지도에서 경로를 확인해요." else "Google 지도에서 현재 위치를 출발점으로 경로를 확인해요.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf(Triple("도보","walking",Icons.Rounded.DirectionsWalk),Triple("대중교통","transit",Icons.Rounded.Train),Triple("자동차","driving",Icons.Rounded.DirectionsCar)).forEach { (title,mode,icon) ->
                    FilledTonalButton(onClick={open(directionUrl(place,mode,if(fromStation) station?.point else null))},modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=6.dp,vertical=12.dp)) { Column(horizontalAlignment=Alignment.CenterHorizontally) { Icon(icon,null); Text(title,fontSize=12.sp) } }
                }
            }
            OutlinedButton(onClick={open(mapPlaceUrl(place))},modifier=Modifier.fillMaxWidth()) { Text("Google 지도에서 장소 정보 보기") }
            TextButton(onClick={runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type="text/plain"; putExtra(Intent.EXTRA_TEXT,"${place.name}\n${mapPlaceUrl(place)}") },"장소 공유")) }.onFailure { error="공유 앱을 열 수 없어요." }},modifier=Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Share,null); Spacer(Modifier.width(8.dp)); Text("이 장소 공유") }
            error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(24.dp))
        }
    }
}
