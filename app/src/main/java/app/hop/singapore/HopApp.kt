@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package app.hop.singapore

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.*

@Composable fun HopApp(vm: HopViewModel) {
    val context=LocalContext.current; val scope=rememberCoroutineScope(); val colors=MaterialTheme.colorScheme
    SideEffect { (context as? android.app.Activity)?.let { activity -> androidx.core.view.WindowCompat.getInsetsController(activity.window,activity.window.decorView).apply { isAppearanceLightStatusBars=colors.background.luminance()>.3f; isAppearanceLightNavigationBars=colors.background.luminance()>.3f } } }
    val snackbar=remember { SnackbarHostState() }; val camera=rememberMapCamera()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showStationPicker by rememberSaveable { mutableStateOf(false) }
    var showMapSearch by rememberSaveable { mutableStateOf(false) }
    var showLayers by rememberSaveable { mutableStateOf(false) }
    var showResults by rememberSaveable { mutableStateOf(false) }
    var actionPlace by remember { mutableStateOf<Place?>(null) }
    var location by remember { mutableStateOf<GeoPoint?>(null) }; var locating by remember { mutableStateOf(false) }
    var following by remember { mutableStateOf(false) }
    var showImportHelp by rememberSaveable { mutableStateOf(false) }
    var previousTab by rememberSaveable { mutableIntStateOf(0) }
    var discard by remember { mutableStateOf(false) }
    val selected=vm.selected; val rail=vm.rail; val stationPreview=vm.stationPreview
    val candidates=remember(selected,rail) { selected?.let { p -> rail?.let { nearest(p.point,it.stations) } }.orEmpty() }
    val candidate=candidates.find { it.station.id==vm.candidateId } ?: candidates.firstOrNull()
    fun selectPlace(p: Place) { following=false; previousTab=vm.tab; vm.select(p) }
    fun inspectStation(station:Station) { following=false; vm.inspectStation(station); camera.focus(station.point,15.3) }
    fun locate() { scope.launch {
        locating=true
        val p=runCatching { currentLocation(context) }.getOrNull()
        if(p==null) snackbar.showSnackbar("현재 위치를 확인할 수 없어요. 지도는 계속 사용할 수 있어요.")
        else if(!p.inSingapore) snackbar.showSnackbar("현재 위치가 싱가포르 밖이에요. 여행 지도는 그대로 유지합니다.")
        else { location=p; camera.focus(p); vm.clearSelection(); following=true }
        locating=false
    }}
    val permissions=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if(result.values.any { it }) locate() else scope.launch { snackbar.showSnackbar("위치 권한 없이도 장소와 역을 비교할 수 있어요.") }
    }
    val filePicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if(uri!=null) vm.loadImport(uri) }
    FollowLocation(following && vm.tab==0 && vm.draft==null && !showMapSearch) { point -> location=point; camera.focus(point,camera.zoom.coerceIn(14.0,18.0)) }
    LaunchedEffect(vm.message) { vm.message?.let { message -> vm.message=null; snackbar.showSnackbar(message) } }
    LaunchedEffect(selected?.id,candidate?.station?.id) {
        if(selected!=null) {
            val p=candidate?.station?.point ?: selected.point
            camera.fit(listOf(selected.point,p))
        }
    }
    LaunchedEffect(vm.mapResultsVersion) { if(vm.mapResultsVersion>0 && vm.mapResults.isNotEmpty()) camera.fit(vm.mapResults.map { it.point }) }
    BackHandler(enabled=vm.draft==null && (vm.expanded || selected!=null || stationPreview!=null || vm.mapResults.isNotEmpty() || vm.tab!=0)) {
        when { vm.expanded -> vm.expanded=false; vm.tab==0 && previousTab!=0 -> { vm.changeTab(previousTab); previousTab=0 }; vm.tab==0 && (selected!=null || stationPreview!=null) -> vm.clearSelection(); vm.tab==0 && vm.mapResults.isNotEmpty() -> vm.clearMapResults(); else -> vm.changeTab(0) }
    }
    Surface(Modifier.fillMaxSize(),color=colors.background) {
        if(vm.loading) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { CircularProgressIndicator() }
        else if(vm.failure!=null) Column(Modifier.fillMaxSize().padding(28.dp),verticalArrangement=Arrangement.Center) { Text(vm.failure!!); Button(onClick=vm::reload) { Text("다시 열기") } }
        else if(rail!=null) BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide=maxWidth>=600.dp || maxWidth>maxHeight
            val density=LocalDensity.current
            val top=WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val bottom=WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            var dockHeight by remember { mutableStateOf(60.dp) }
            var cardHeight by remember { mutableStateOf(156.dp) }
            val dockReserve=dockHeight+bottom+20.dp
            val hasPanel=selected!=null || stationPreview!=null || vm.mapResults.isNotEmpty() || vm.mapSearching || vm.mapSearchError!=null
            val mapBottom=if(hasPanel && !wide) dockReserve+cardHeight+8.dp else dockReserve
            if(vm.tab==0) {
                SingaporeMap(rail,vm.places,camera,Modifier.fillMaxSize(),selected,stationPreview ?: candidate?.station,
                    detailed=vm.detailedMap,currentLocation=location,controlsBottom=mapBottom,
                    viewportTop=top+124.dp,viewportBottom=mapBottom,viewportLeft=if(wide && hasPanel) 340.dp else 0.dp,viewportRight=72.dp,
                    onPlace=::selectPlace,onStation=::inspectStation,onLongPress={following=false;vm.previewPoint(it)},onMapTap={following=false;vm.previewPoint(it)},showRail=vm.showRail,showSaved=vm.showSaved,browsePlaces=vm.mapResults,onGesture={following=false})
                Row(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal=16.dp,vertical=8.dp).widthIn(max=600.dp).fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Surface(Modifier.weight(1f),shape=CircleShape,shadowElevation=3.dp,color=colors.surface) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            IconButton(onClick={showSettings=true}) { Icon(Icons.Rounded.Menu,"메뉴") }
                            Row(Modifier.weight(1f).heightIn(min=52.dp).clickable { following=false; showMapSearch=true },verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Rounded.Search,null,Modifier.size(20.dp),tint=colors.onSurfaceVariant)
                                Text("장소·주소·MRT 검색",fontSize=14.sp,color=colors.onSurfaceVariant,maxLines=1)
                            }
                            Text("hop",Modifier.padding(end=16.dp),fontSize=22.sp,fontWeight=FontWeight.Bold,color=colors.primary,letterSpacing=(-1).sp)
                        }
                    }
                    FilledIconButton(onClick={vm.beginAdd()},modifier=Modifier.size(52.dp),colors=IconButtonDefaults.filledIconButtonColors(containerColor=colors.tertiaryContainer,contentColor=colors.onTertiaryContainer)) { Icon(Icons.Rounded.Add,"장소 추가") }
                }
                Row(Modifier.align(Alignment.TopStart).statusBarsPadding().padding(top=70.dp,start=16.dp,end=16.dp).horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    PlaceCategory.entries.forEach { category -> FilterChip(selected=vm.mapCategory==category,onClick={following=false;vm.searchNearby(category,camera.point)},label={Text(category.title)},shape=CircleShape,colors=FilterChipDefaults.filterChipColors(containerColor=colors.surface)) }
                }
                FlowRow(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top=if(wide) 110.dp else 128.dp,end=16.dp).width(if(wide) 160.dp else 48.dp),maxItemsInEachRow=if(wide) 3 else 1,horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilledTonalIconButton(onClick={if(following) following=false else permissions.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION))},modifier=Modifier.size(48.dp)) { if(locating) CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp) else Icon(Icons.Rounded.MyLocation,if(following) "내 위치 추적 끄기" else "내 위치",tint=if(following) colors.primary else colors.onSurface) }
                    FilledTonalIconButton(onClick={showLayers=true},modifier=Modifier.size(48.dp)) { Icon(Icons.Rounded.Layers,"지도 표시 설정") }
                    FilledTonalIconButton(onClick={following=false;camera.fit(vm.places.map { it.point })},modifier=Modifier.size(48.dp)) { Icon(Icons.Rounded.ZoomOutMap,"저장한 장소 모두 보기") }
                }
                if(selected!=null && candidate!=null) CompactPlaceCard(vm,selected,candidate,onActions={actionPlace=selected},modifier=
                    Modifier.align(if(wide) Alignment.BottomStart else Alignment.BottomCenter)
                        .padding(start=16.dp,end=16.dp,bottom=dockReserve).widthIn(max=if(wide) 340.dp else 520.dp).fillMaxWidth()
                        .onSizeChanged { cardHeight=with(density){it.height.toDp()} })
                else if(stationPreview!=null) StationMapCard(stationPreview,vm,Modifier.align(if(wide) Alignment.BottomStart else Alignment.BottomCenter).padding(start=16.dp,end=16.dp,bottom=dockReserve).widthIn(max=if(wide) 340.dp else 520.dp).fillMaxWidth().onSizeChanged { cardHeight=with(density){it.height.toDp()} },vm::clearSelection)
                else if(vm.mapResults.isNotEmpty() || vm.mapSearching || vm.mapSearchError!=null) Surface(Modifier.align(if(wide) Alignment.BottomStart else Alignment.BottomCenter).padding(start=16.dp,end=16.dp,bottom=dockReserve).widthIn(max=if(wide) 340.dp else 520.dp).fillMaxWidth().onSizeChanged { cardHeight=with(density){it.height.toDp()} },shape=RoundedCornerShape(24.dp),shadowElevation=4.dp) {
                    Column(Modifier.padding(horizontal=16.dp,vertical=8.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically) { Text(vm.mapSearchError ?: if(vm.mapSearching) "주변 장소를 찾는 중…" else "${vm.mapCategory?.title ?: "검색 결과"} · ${vm.mapResults.size}곳",Modifier.weight(1f),fontSize=14.sp); IconButton(onClick=vm::clearMapResults) { Icon(Icons.Rounded.Close,"검색 결과 닫기") } }
                        if(vm.mapSearching) LinearProgressIndicator(Modifier.fillMaxWidth())
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { if(vm.mapCategory!=null) TextButton(onClick={vm.searchNearby(vm.mapCategory!!,camera.point)},enabled=!vm.mapSearching) { Text("이 지역 다시 찾기") }; if(vm.mapResults.isNotEmpty()) FilledTonalButton(onClick={showResults=true}) { Icon(Icons.Rounded.List,null); Text("목록 보기") } }
                    }
                }
            } else Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick={showSettings=true}) { Icon(Icons.Rounded.Menu,"메뉴") }
                    Text(if(vm.tab==1) "저장한 곳" else "역별로 둘러보기",Modifier.weight(1f),fontSize=20.sp,fontWeight=FontWeight.SemiBold)
                    IconButton(onClick={showImportHelp=true}) { Icon(Icons.Rounded.FileDownload,"Google 지도 즐겨찾기 가져오기") }
                    IconButton(onClick={vm.beginAdd()}) { Icon(Icons.Rounded.Add,"장소 추가") }
                }
                when(vm.tab) {
                    1 -> SavedScreen(vm,Modifier.weight(1f),dockReserve,::selectPlace,onImport={showImportHelp=true},onDelete={p -> vm.delete(p) { scope.launch { if(snackbar.showSnackbar("${p.name} 삭제됨","실행 취소",duration=SnackbarDuration.Long)==SnackbarResult.ActionPerformed) vm.restore(p) } } })
                    2 -> GroupsScreen(vm,rail,Modifier.weight(1f),dockReserve,onStationPicker={showStationPicker=true},onPlace=::selectPlace)
                }
            }
            FloatingDock(vm.tab,vm::changeTab,Modifier.align(Alignment.BottomCenter).padding(start=24.dp,end=24.dp,bottom=bottom+10.dp).widthIn(max=360.dp).fillMaxWidth().onSizeChanged { dockHeight=with(density){it.height.toDp()} })
            SnackbarHost(snackbar,Modifier.align(Alignment.BottomCenter).padding(bottom=dockReserve,start=16.dp,end=16.dp))
            if(vm.importLoading) LinearProgressIndicator(Modifier.align(Alignment.TopCenter).statusBarsPadding().fillMaxWidth())
        }
    }
    if(vm.expanded && selected!=null && candidate!=null) ModalBottomSheet(onDismissRequest={vm.expanded=false},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=colors.surface) {
        PlaceDetail(vm,selected,candidate,candidates,Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding(),onEdit={vm.expanded=false;vm.edit(selected)})
    }
    if(showMapSearch) MapSearchScreen(vm,camera.point,{showMapSearch=false},::selectPlace,::inspectStation)
    if(showLayers) MapLayersSheet(vm,{showLayers=false}) { vm.clearSelection(); camera.fit(listOf(GeoPoint(1.22,103.64),GeoPoint(1.45,104.02))) }
    if(showResults) NearbyResultsSheet(vm,camera.point,{showResults=false},::selectPlace)
    actionPlace?.let { p -> PlaceActionsSheet(p,rail?.let { nearest(p.point,it.stations).firstOrNull()?.station },{actionPlace=null}) }
    if(showStationPicker && rail!=null) StationPicker(rail.stations,{showStationPicker=false}) { vm.chooseStation(it); showStationPicker=false }
    if(showSettings) Settings(vm,onDismiss={showSettings=false},onImport={showSettings=false;showImportHelp=true})
    if(showImportHelp) GoogleImportHelp(onDismiss={showImportHelp=false},onChooseFile={showImportHelp=false;filePicker.launch(arrayOf("text/*","application/json","application/geo+json","application/zip","application/x-zip-compressed","application/octet-stream"))},onShare={showImportHelp=false;vm.beginAdd()})
    if(vm.importReview!=null && vm.draft==null) GoogleImportReview(vm)
    val draft=vm.draft
    if(draft!=null && rail!=null) {
        fun close() { if(draft.name.isNotBlank() || draft.link.isNotBlank() || draft.point!=null) discard=true else vm.closeDraft() }
        DraftScreen(vm,rail,onClose=::close)
    }
    if(discard) AlertDialog(onDismissRequest={discard=false},title={Text("저장하지 않고 닫을까요?")},text={Text("입력한 장소 정보가 사라집니다.")},confirmButton={TextButton(onClick={discard=false;vm.closeDraft()}){Text("닫기")}},dismissButton={TextButton(onClick={discard=false}){Text("계속 입력")}})
}

@Composable private fun FloatingDock(tab: Int,onTab:(Int)->Unit,modifier:Modifier) {
    val colors=MaterialTheme.colorScheme
    Surface(modifier,shape=RoundedCornerShape(40.dp),color=colors.surface,shadowElevation=12.dp,tonalElevation=0.dp) {
        Row(Modifier.padding(6.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            listOf(Triple(0,Icons.Rounded.Map,"지도"),Triple(1,Icons.Rounded.BookmarkBorder,"저장한 곳"),Triple(2,Icons.Rounded.Train,"역별")).forEach { (index,icon,label) ->
                Surface(onClick={onTab(index)},modifier=Modifier.weight(1f).semantics { selected=tab==index; role=Role.Tab },shape=CircleShape,color=if(tab==index) colors.primary else colors.surface,contentColor=if(tab==index) colors.onPrimary else colors.onSurfaceVariant) {
                    Row(Modifier.heightIn(min=48.dp).padding(horizontal=6.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp,Alignment.CenterHorizontally)) { Icon(icon,null,Modifier.size(20.dp)); Text(label,fontSize=12.sp,fontWeight=if(tab==index) FontWeight.Bold else FontWeight.Medium) }
                }
            }
        }
    }
}
@Composable private fun CodeBadges(station: Station) {
    val codeColors=mapOf("NS" to 0xFFB12C37,"EW" to 0xFF126D43,"NE" to 0xFF773582,"CC" to 0xFF8A5804,"DT" to 0xFF205F9B,"TE" to 0xFF785336,"CG" to 0xFF126D43,"CE" to 0xFF8A5804)
    FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) { station.codes.forEach { code -> Text(code,Modifier.background(Color(codeColors[code.take(2)] ?: 0xFF526078),RoundedCornerShape(7.dp)).padding(horizontal=7.dp,vertical=2.dp),color=Color.White,fontSize=12.sp,lineHeight=16.sp,fontWeight=FontWeight.Bold) } }
}
@Composable private fun CompactPlaceCard(vm:HopViewModel,place:Place,candidate:Candidate,onActions:()->Unit,modifier:Modifier) {
    val colors=MaterialTheme.colorScheme; val context=LocalContext.current
    Surface(modifier,shape=RoundedCornerShape(28.dp),color=colors.surface,shadowElevation=6.dp) {
        Column(Modifier.padding(start=16.dp,end=12.dp,top=4.dp,bottom=12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(place.name,Modifier.weight(1f),fontSize=17.sp,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                if(vm.places.none { it.id==place.id }) IconButton(onClick={vm.edit(place)}) { Icon(Icons.Rounded.BookmarkAdd,"이 장소 저장") }
                IconButton(onClick=onActions) { Icon(Icons.Rounded.MoreHoriz,"길찾기·정보·공유") }
                IconButton(onClick=vm::clearSelection) { Icon(Icons.Rounded.Close,"장소 선택 닫기",Modifier.size(20.dp)) }
            }
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Surface(shape=CircleShape,color=colors.primaryContainer) { Icon(Icons.Rounded.Train,null,Modifier.padding(10.dp).size(22.dp),tint=colors.primary) }
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    Text(candidate.station.name,fontSize=17.sp,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                    CodeBadges(candidate.station)
                }
                Column(horizontalAlignment=Alignment.End) { Text(formatDistance(candidate.metres),fontSize=22.sp,fontWeight=FontWeight.Bold,color=colors.primary); Text("직선거리",fontSize=11.sp,color=colors.onSurfaceVariant) }
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                TextButton(onClick={vm.expanded=true},modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=4.dp)) { Text("하차역 3곳 비교",fontSize=12.sp); Icon(Icons.Rounded.ChevronRight,null,Modifier.size(16.dp)) }
                FilledTonalButton(onClick={runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(walkingUrl(candidate.station,place)))) }.onFailure { vm.message="지도를 열 수 있는 앱이 없어요." }},contentPadding=PaddingValues(horizontal=16.dp),modifier=Modifier.heightIn(min=48.dp)) { Icon(Icons.Rounded.DirectionsWalk,null,Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("도보 길찾기",fontSize=12.sp) }
            }
        }
    }
}

@Composable private fun PlaceDetail(vm: HopViewModel,place: Place,candidate: Candidate,candidates: List<Candidate>,modifier: Modifier,onEdit:()->Unit) {
    val colors=MaterialTheme.colorScheme; val context=LocalContext.current
    Surface(modifier,shape=RoundedCornerShape(36.dp),color=colors.surface) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(32.dp,4.dp).background(colors.outlineVariant,CircleShape))
            Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(place.name,fontSize=23.sp,fontWeight=FontWeight.SemiBold); if(place.subtitle.isNotBlank()) Text(place.subtitle,fontSize=12.sp,color=colors.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis) }
                IconButton(onClick=onEdit,Modifier.size(48.dp)) { Icon(Icons.Rounded.Edit,"장소와 메모 수정") }
            }
            if(vm.expanded) candidates.forEach { c ->
                Surface(onClick={vm.candidateId=c.station.id},shape=RoundedCornerShape(22.dp),color=if(c.station.id==candidate.station.id) colors.primaryContainer else colors.surfaceVariant,modifier=Modifier.fillMaxWidth().semantics { selected=c.station.id==candidate.station.id }) {
                    Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) { Column(Modifier.weight(1f)) { Text(c.station.name,fontWeight=FontWeight.SemiBold); Spacer(Modifier.height(4.dp)); CodeBadges(c.station) }; Text(formatDistance(c.metres),fontWeight=FontWeight.Bold,color=colors.primary); Icon(if(c.station.id==candidate.station.id) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,null,Modifier.size(20.dp)) }
                }
            } else Surface(shape=RoundedCornerShape(28.dp),color=colors.primaryContainer) {
                Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) { Icon(Icons.Rounded.Train,null,Modifier.size(16.dp),tint=colors.onPrimaryContainer); Text(if(candidate==candidates.firstOrNull()) "직선으로 가장 가까운 역" else "선택한 하차 후보",fontSize=12.sp,color=colors.onPrimaryContainer) }
                    FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) { Text(candidate.station.name,fontSize=21.sp,fontWeight=FontWeight.SemiBold,color=colors.onPrimaryContainer); Text(formatDistance(candidate.metres),fontSize=28.sp,fontWeight=FontWeight.Bold,color=colors.primary) }
                    CodeBadges(candidate.station)
                }
            }
            TextButton(onClick={vm.expanded=!vm.expanded},modifier=Modifier.align(Alignment.CenterHorizontally)) { Text(if(vm.expanded) "후보 접기" else "다른 역 2곳 비교"); Icon(if(vm.expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,null) }
            if(place.memo.isNotBlank()) Text(place.memo,fontSize=14.sp,color=colors.onSurfaceVariant)
            Text("역 중심 기준 · 실제 도보거리와 달라요.",fontSize=12.sp,color=colors.onSurfaceVariant)
            Button(onClick={runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(walkingUrl(candidate.station,place)))) }.onFailure { vm.message="지도를 열 수 있는 앱이 없어요." }},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp),shape=CircleShape) { Icon(Icons.Rounded.DirectionsWalk,null); Spacer(Modifier.width(8.dp)); Text("이 역에서 도보 확인",fontWeight=FontWeight.SemiBold) }
        }
    }
}

@Composable private fun SavedScreen(vm: HopViewModel,modifier: Modifier,bottom:Dp,onPlace:(Place)->Unit,onImport:()->Unit,onDelete:(Place)->Unit) {
    val colors=MaterialTheme.colorScheme
    val visible=vm.places.filter { (vm.visitFilter==0 || (vm.visitFilter==1 && !it.visited) || (vm.visitFilter==2 && it.visited)) && (it.name+it.subtitle+it.memo).contains(vm.query,true) }
    LazyColumn(modifier,contentPadding=PaddingValues(start=16.dp,end=16.dp,top=4.dp,bottom=bottom),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        item { Surface(onClick=onImport,shape=RoundedCornerShape(20.dp),color=colors.primaryContainer) { Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) { Icon(Icons.Rounded.FileDownload,null,tint=colors.primary); Column(Modifier.weight(1f)) { Text("Google 지도 즐겨찾기 가져오기",fontWeight=FontWeight.SemiBold,fontSize=14.sp); Text("Takeout ZIP · CSV · GeoJSON",fontSize=12.sp,color=colors.onSurfaceVariant) }; Icon(Icons.Rounded.ChevronRight,null) } } }
        item { OutlinedTextField(vm.query,{vm.query=it},modifier=Modifier.fillMaxWidth(),shape=CircleShape,label={Text("장소·메모 검색")},leadingIcon={Icon(Icons.Rounded.Search,null)},singleLine=true) }
        item { FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("전체","미방문","방문").forEachIndexed { i,label -> FilterChip(vm.visitFilter==i,{vm.visitFilter=i},{Text(label)},modifier=Modifier.heightIn(min=48.dp)) } } }
        if(visible.isEmpty()) item { Text("저장한 장소가 없어요. + 버튼으로 여행지를 추가해 보세요.",Modifier.padding(vertical=24.dp),color=colors.onSurfaceVariant) }
        items(visible,key={it.id}) { p ->
            Surface(shape=RoundedCornerShape(24.dp),color=colors.surface) {
                Column(Modifier.padding(horizontal=12.dp,vertical=4.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { onPlace(p) }.padding(vertical=8.dp).semantics { role=Role.Button }) {
                            Text(p.name,fontWeight=FontWeight.SemiBold,fontSize=17.sp)
                            val near=vm.rail?.let { nearest(p.point,it.stations).firstOrNull() }
                            if(near!=null) Text("${near.station.name} · 직선 ${formatDistance(near.metres)}",color=colors.onSurfaceVariant,fontSize=12.sp)
                            if(p.memo.isNotBlank()) Text(p.memo,fontSize=13.sp,color=colors.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis)
                        }
                        Checkbox(p.visited,{vm.toggleVisited(p)},modifier=Modifier.semantics { contentDescription="${p.name} 방문 완료" })
                        var menu by remember { mutableStateOf(false) }
                        Box { IconButton(onClick={menu=true}) { Icon(Icons.Rounded.MoreVert,"${p.name} 옵션") }; DropdownMenu(expanded=menu,onDismissRequest={menu=false}) { DropdownMenuItem(text={Text("메모·수정")},onClick={menu=false;vm.edit(p)}); DropdownMenuItem(text={Text("삭제")},onClick={menu=false;onDelete(p)}) } }
                    }

                }
            }
        }
        item { OutlinedButton(onClick={vm.beginAdd()},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Icon(Icons.Rounded.Add,null); Text("장소 추가") } }
    }
}

@Composable private fun GroupsScreen(vm: HopViewModel,rail:RailData,modifier:Modifier,bottom:Dp,onStationPicker:()->Unit,onPlace:(Place)->Unit) {
    val station=vm.groupStation ?: return; val colors=MaterialTheme.colorScheme; val rows=within(station,vm.places,vm.radius)
    val camera=rememberMapCamera(); LaunchedEffect(station.id,vm.radius) { val delta=vm.radius/111320.0; camera.fit(listOf(GeoPoint(station.point.lat-delta,station.point.lon-delta),GeoPoint(station.point.lat+delta,station.point.lon+delta))) }
    LazyColumn(modifier,contentPadding=PaddingValues(start=16.dp,end=16.dp,top=4.dp,bottom=bottom),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        item { Surface(onClick=onStationPicker,shape=CircleShape,color=colors.surfaceVariant) { Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) { Text(station.name,Modifier.weight(1f),fontSize=20.sp,fontWeight=FontWeight.SemiBold); CodeBadges(station); Icon(Icons.Rounded.ExpandMore,null) } } }
        item { Text("한 번 내려서, ${rows.size}곳.",fontSize=21.sp,fontWeight=FontWeight.SemiBold) }
        item { Row(Modifier.fillMaxWidth().background(colors.surfaceVariant,CircleShape).padding(4.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) { listOf(400,800,1200).forEach { r ->
            val chosen=vm.radius==r
            Surface(onClick={vm.changeRadius(r)},modifier=Modifier.weight(1f).semantics { selected=chosen },shape=CircleShape,color=if(chosen) colors.surface else Color.Transparent) { Text(if(r==1200) "1.2km" else "${r}m",Modifier.padding(vertical=14.dp,horizontal=8.dp),textAlign=androidx.compose.ui.text.style.TextAlign.Center,color=if(chosen) colors.primary else colors.onSurface,fontWeight=if(chosen) FontWeight.Bold else FontWeight.Normal) }
        } } }
        item { SingaporeMap(rail,rows.map { it.first },camera,Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(28.dp)),selectedStation=station,radius=vm.radius,onPlace=onPlace) }
        item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text("함께 갈 수 있는 내 스팟",fontWeight=FontWeight.SemiBold,fontSize=14.sp); Text("직선거리 순",fontSize=12.sp,color=colors.onSurfaceVariant) } }
        if(rows.isEmpty()) item { Text("이 반경에는 저장한 곳이 없어요.\n반경을 넓히거나 다른 역을 골라 보세요.",Modifier.padding(vertical=24.dp),color=colors.onSurfaceVariant) }
        items(rows,key={it.first.id}) { (place,distance) -> Surface(onClick={onPlace(place)},shape=RoundedCornerShape(22.dp),color=colors.surface) { Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) { Column(Modifier.weight(1f)) { Text(place.name,fontWeight=FontWeight.SemiBold); Text(place.subtitle,fontSize=12.sp,color=colors.onSurfaceVariant,maxLines=2) }; Text(formatDistance(distance),color=colors.primary,fontWeight=FontWeight.SemiBold); Icon(Icons.Rounded.ChevronRight,null,Modifier.size(20.dp)) } } }
        item { Text("역 중심 기준이에요. 실제 도보 동선은 달라요.",fontSize=12.sp,color=colors.onSurfaceVariant) }
    }
}

@Composable private fun StationPicker(stations:List<Station>,onDismiss:()->Unit,onSelect:(Station)->Unit) {
    var query by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest=onDismiss,containerColor=MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal=20.dp)) { Text("역 선택",fontSize=24.sp,fontWeight=FontWeight.SemiBold); OutlinedTextField(query,{query=it},label={Text("역 이름 또는 코드")},modifier=Modifier.fillMaxWidth().padding(vertical=12.dp),singleLine=true,shape=CircleShape) }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max=480.dp),contentPadding=PaddingValues(start=20.dp,end=20.dp,bottom=32.dp)) {
            items(stations.filter { (it.name+it.codes.joinToString()).contains(query,true) }.sortedBy { it.name },key={it.id}) { s -> ListItem(headlineContent={Text(s.name)},supportingContent={CodeBadges(s)},modifier=Modifier.clip(RoundedCornerShape(16.dp)).clickable { onSelect(s) }.heightIn(min=72.dp)) }
        }
    }
}

@Composable private fun Settings(vm:HopViewModel,onDismiss:()->Unit,onImport:()->Unit) {
    val uri=LocalUriHandler.current
    ModalBottomSheet(onDismissRequest=onDismiss,containerColor=MaterialTheme.colorScheme.surface) { Column(Modifier.fillMaxWidth().padding(horizontal=20.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("나의 싱가포르",fontSize=25.sp,fontWeight=FontWeight.SemiBold)
        Text("장소는 이 기기에 저장돼요. 로그인 없이 사용할 수 있어요.",fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text("화면 테마",fontWeight=FontWeight.SemiBold)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("system" to "시스템","light" to "라이트","dark" to "다크").forEach { (value,label) -> FilterChip(vm.theme==value,{vm.changeTheme(value)},{Text(label)},modifier=Modifier.heightIn(min=48.dp)) } }
        OutlinedButton(onClick=onImport,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Icon(Icons.Rounded.FileOpen,null); Spacer(Modifier.width(8.dp)); Text("Google 지도 즐겨찾기 가져오기") }
        Text("Google Takeout의 저장 목록과 메모를 가져와요. 위치가 없는 장소도 검색·핀으로 확인할 수 있어요.",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider()
        Text("MRT ${vm.rail?.stations?.size ?: 0}개 역 · 데이터 기준 2026.10.28",fontSize=13.sp)
        Text("상세 도로 지도를 기본으로 사용합니다. 지도 표시 설정에서 노선·저장 장소를 켜고 끌 수 있어요. 상세 지도와 주변 검색은 인터넷이 필요합니다. 거리 계산은 역 대표점 기준이며 실제 도보거리와 다릅니다.",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick={uri.openUri("https://www.openstreetmap.org/copyright")}) { Text("© OpenStreetMap contributors · ODbL") }
        TextButton(onClick={uri.openUri("https://github.com/cheeaun/sgraildata")}) { Text("MRT 데이터 · sgraildata") }
        TextButton(onClick={uri.openUri("https://www.lta.gov.sg/content/ltagov/en/getting_around/public_transport/rail_network.html")}) { Text("LTA 공식 노선 정보 확인") }
        Spacer(Modifier.height(24.dp))
    } }
}

@Composable private fun DraftScreen(vm:HopViewModel,rail:RailData,onClose:()->Unit) {
    val d=vm.draft ?: return; val colors=MaterialTheme.colorScheme; val camera=rememberMapCamera()
    val point=d.point?.takeIf { it.inSingapore }; val candidate=point?.let { nearest(it,rail.stations).firstOrNull() }
    var showLinkEditor by rememberSaveable { mutableStateOf(false) }
    var showCoordinates by rememberSaveable { mutableStateOf(false) }
    val topInset=WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset=WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LaunchedEffect(point) { point?.let { camera.fit(listOfNotNull(it,candidate?.station?.point)) } }
    Dialog(onDismissRequest={if(vm.picking) vm.picking=false else onClose()},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        Surface(Modifier.fillMaxSize(),color=colors.background) {
            Column(Modifier.fillMaxSize().padding(top=topInset,bottom=bottomInset).consumeWindowInsets(PaddingValues(top=topInset,bottom=bottomInset)).imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) { IconButton(onClick={if(vm.picking) vm.picking=false else onClose()}) { Icon(Icons.AutoMirrored.Rounded.ArrowBack,"장소 추가 닫기") }; Text(if(vm.picking) "지도에서 위치 선택" else if(vm.importEditingId!=null) "가져온 장소 위치 확인" else if(d.editingId!=null) "장소 수정" else "장소 추가",fontSize=22.sp,fontWeight=FontWeight.SemiBold) }
                if(vm.picking) {
                    Box(Modifier.weight(1f).padding(horizontal=12.dp).clip(RoundedCornerShape(28.dp))) {
                        SingaporeMap(rail,emptyList(),camera,Modifier.fillMaxSize(),detailed=vm.detailedMap,onLongPress={vm.picked(it)})
                        Icon(Icons.Rounded.LocationOn,null,Modifier.align(Alignment.Center).size(42.dp).offset(y=(-21).dp),tint=colors.primary)
                    }
                    Text("지도를 움직여 가운데 핀을 맞춰 주세요.",Modifier.padding(16.dp),fontSize=14.sp)
                    Button(onClick={if(camera.point.inSingapore) vm.picked(camera.point) else vm.draftError="싱가포르 안에서 선택해 주세요."},modifier=Modifier.fillMaxWidth().padding(16.dp).heightIn(min=56.dp)) { Text("이 위치 선택") }
                } else {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    if(d.editingId==null && vm.importEditingId==null) {
                        if(point!=null && d.link.isNotBlank() && !showLinkEditor) {
                            Surface(shape=CircleShape,color=colors.primaryContainer) { Row(Modifier.fillMaxWidth().padding(start=14.dp,end=4.dp),verticalAlignment=Alignment.CenterVertically) { Icon(Icons.Rounded.Share,null,Modifier.size(18.dp),tint=colors.onPrimaryContainer); Text("Google 지도에서 받은 장소",Modifier.weight(1f).padding(start=8.dp),fontSize=12.sp,color=colors.onPrimaryContainer); IconButton(onClick={showLinkEditor=true}) { Icon(Icons.Rounded.Edit,"공유 링크 수정",Modifier.size(18.dp)) } } }
                            Text("이곳을 저장할까요?",fontSize=26.sp,fontWeight=FontWeight.SemiBold)
                        } else {
                            Text("가고 싶은 곳을 저장해요",fontSize=26.sp,fontWeight=FontWeight.SemiBold)
                            OutlinedTextField(d.link,{vm.draft=d.copy(link=it)},label={Text("Google 지도 링크 또는 위도, 경도")},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp),maxLines=3)
                            OutlinedButton(onClick={showLinkEditor=false;vm.resolveShared()},enabled=!vm.busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Icon(Icons.Rounded.Link,null); Spacer(Modifier.width(8.dp)); Text("링크에서 위치 확인") }
                        }
                    }
                    OutlinedTextField(d.name,{vm.draft=d.copy(name=it)},label={Text("장소 이름")},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp),singleLine=true)
                    if(d.editingId==null && (point==null || showLinkEditor)) TextButton(onClick={vm.searchName(d.name)},enabled=!vm.busy,modifier=Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Search,null); Text("이 이름으로 싱가포르에서 검색") }
                    if(vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    vm.draftError?.let { Text(it,color=colors.error,fontSize=14.sp,modifier=Modifier.semantics { liveRegion=LiveRegionMode.Polite }) }
                    vm.hits.forEach { hit -> Surface(onClick={vm.chooseHit(hit)},shape=RoundedCornerShape(20.dp),color=colors.surface) { Column(Modifier.fillMaxWidth().padding(14.dp)) { Text(hit.name,fontWeight=FontWeight.SemiBold); Text(hit.address,fontSize=12.sp,color=colors.onSurfaceVariant) } } }
                    if(point!=null) {
                        SingaporeMap(rail,listOf(Place("draft",d.name.ifBlank { "선택한 위치" },"",point)),camera,Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(28.dp)),selected=Place("draft",d.name.ifBlank { "선택한 위치" },"",point),selectedStation=candidate?.station,interactive=false)
                        candidate?.let { Surface(shape=RoundedCornerShape(24.dp),color=colors.primaryContainer) { Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) { Text("직선으로 가장 가까운 역",fontSize=12.sp,color=colors.onPrimaryContainer); Text("${it.station.name} · ${formatDistance(it.metres)}",fontWeight=FontWeight.SemiBold); CodeBadges(it.station) } } }
                    }
                    OutlinedButton(onClick={vm.picking=true},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Icon(Icons.Rounded.PinDrop,null); Spacer(Modifier.width(8.dp)); Text(if(point==null) "지도에서 핀 지정" else "지도에서 위치 조정") }
                    TextButton(onClick={showCoordinates=!showCoordinates}) { Text(if(showCoordinates) "좌표 접기" else "좌표 직접 입력·수정") }
                    if(showCoordinates || point==null) Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(d.latitude,{vm.draft=d.copy(latitude=it)},label={Text("위도")},modifier=Modifier.weight(1f),shape=RoundedCornerShape(18.dp),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true)
                        OutlinedTextField(d.longitude,{vm.draft=d.copy(longitude=it)},label={Text("경도")},modifier=Modifier.weight(1f),shape=RoundedCornerShape(18.dp),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true)
                    }
                    OutlinedTextField(d.memo,{vm.draft=d.copy(memo=it)},label={Text("여행 메모")},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp),minLines=2,maxLines=5)
                    Text("위치와 핀을 확인한 뒤 저장해 주세요.\nGoogle 즐겨찾기 자동 동기화 기능은 아닙니다.",fontSize=12.sp,color=colors.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                }
                Surface(color=colors.background) { Button(onClick=vm::saveDraft,enabled=!vm.busy,modifier=Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp).heightIn(min=58.dp),shape=CircleShape) { Icon(Icons.Rounded.BookmarkAdd,null); Spacer(Modifier.width(8.dp)); Text(if(vm.importEditingId!=null) "위치 확인하고 목록으로" else "내 장소에 저장",fontWeight=FontWeight.SemiBold) } }
                }
            }
        }
    }
}
