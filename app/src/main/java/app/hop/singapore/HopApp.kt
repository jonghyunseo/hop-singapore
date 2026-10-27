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
    var location by remember { mutableStateOf<GeoPoint?>(null) }; var locating by remember { mutableStateOf(false) }
    var importReview by remember { mutableStateOf<ImportReview?>(null) }; var importing by remember { mutableStateOf(false) }
    var previousTab by rememberSaveable { mutableIntStateOf(0) }
    var discard by remember { mutableStateOf(false) }
    val selected=vm.selected; val rail=vm.rail
    val candidates=remember(selected,rail) { selected?.let { p -> rail?.let { nearest(p.point,it.stations) } }.orEmpty() }
    val candidate=candidates.find { it.station.id==vm.candidateId } ?: candidates.firstOrNull()
    fun selectPlace(p: Place) { previousTab=vm.tab; vm.select(p) }
    fun locate() { scope.launch {
        locating=true
        val p=runCatching { currentLocation(context) }.getOrNull()
        if(p==null) snackbar.showSnackbar("현재 위치를 확인할 수 없어요. 지도는 계속 사용할 수 있어요.")
        else if(!p.inSingapore) snackbar.showSnackbar("현재 위치가 싱가포르 밖이에요. 여행 지도는 그대로 유지합니다.")
        else { location=p; camera.focus(p); vm.clearSelection() }
        locating=false
    }}
    val permissions=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if(result.values.any { it }) locate() else scope.launch { snackbar.showSnackbar("위치 권한 없이도 장소와 역을 비교할 수 있어요.") }
    }
    val filePicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if(uri!=null) scope.launch {
        importing=true
        runCatching { withContext(Dispatchers.IO) {
            val bytes=context.contentResolver.openInputStream(uri)?.use { input ->
                val output=java.io.ByteArrayOutputStream(); val buffer=ByteArray(8192)
                while(output.size()<=2_000_000) { val count=input.read(buffer); if(count<0) break; output.write(buffer,0,count) }; output.toByteArray()
            } ?: error("파일을 열지 못했어요.")
            require(bytes.size<=2_000_000) { "2MB 이하 파일을 선택해 주세요." }; parseImport(bytes.toString(Charsets.UTF_8))
        }}.onSuccess { importReview=it }.onFailure { snackbar.showSnackbar(it.message ?: "파일 형식을 확인해 주세요.") }
        importing=false
    }}
    LaunchedEffect(vm.message) { vm.message?.let { message -> vm.message=null; snackbar.showSnackbar(message) } }
    LaunchedEffect(selected?.id,candidate?.station?.id) {
        if(selected!=null) {
            val p=candidate?.station?.point ?: selected.point
            camera.focus(GeoPoint((selected.point.lat+p.lat)/2,(selected.point.lon+p.lon)/2), if((candidate?.metres ?: 0.0)>1600) 13.8 else 15.6)
        }
    }
    BackHandler(enabled=vm.draft==null && (vm.expanded || selected!=null || vm.tab!=0)) {
        when { vm.expanded -> vm.expanded=false; vm.tab==0 && previousTab!=0 -> { vm.changeTab(previousTab); previousTab=0 }; vm.tab==0 && selected!=null -> vm.clearSelection(); else -> vm.changeTab(0) }
    }
    Surface(Modifier.fillMaxSize(),color=colors.background) {
        if(vm.loading) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { CircularProgressIndicator() }
        else if(vm.failure!=null) Column(Modifier.fillMaxSize().padding(28.dp),verticalArrangement=Arrangement.Center) { Text(vm.failure!!); Button(onClick=vm::reload) { Text("다시 열기") } }
        else if(rail!=null) BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            val wide=maxWidth>=720.dp; val availableHeight=maxHeight; val density=LocalDensity.current
            var dockHeight by remember { mutableStateOf(84.dp) }
            val dockReserve=dockHeight+24.dp
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().heightIn(min=64.dp).padding(horizontal=20.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text("hop",fontSize=30.sp,fontWeight=FontWeight.Bold,color=colors.primary,letterSpacing=(-1.5).sp)
                    TextButton(onClick={showSettings=true},modifier=Modifier.weight(1f)) { Text("싱가포르",color=colors.onBackground,fontWeight=FontWeight.SemiBold); Icon(Icons.Rounded.KeyboardArrowDown,null,Modifier.size(18.dp)) }
                    FilledIconButton(onClick={vm.beginAdd()},modifier=Modifier.size(48.dp),colors=IconButtonDefaults.filledIconButtonColors(containerColor=colors.tertiaryContainer,contentColor=colors.onTertiaryContainer)) { Icon(Icons.Rounded.Add,"장소 추가") }
                }
                Box(Modifier.fillMaxSize()) {
                    when(vm.tab) {
                        0 -> {
                            val map: @Composable (Modifier) -> Unit = { modifier ->
                                Box(modifier.clip(RoundedCornerShape(28.dp))) {
                                    SingaporeMap(rail,vm.places,camera,Modifier.fillMaxSize(),selected,candidate?.station,detailed=vm.detailedMap,currentLocation=location,onPlace=::selectPlace,onStation=vm::chooseStation,onLongPress={vm.beginAdd(point=it)})
                                    Row(Modifier.align(Alignment.TopCenter).padding(12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                        Surface(onClick={vm.changeTab(1)},modifier=Modifier.weight(1f).heightIn(min=52.dp),shape=CircleShape,shadowElevation=2.dp) { Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) { Icon(Icons.Rounded.Search,null,Modifier.size(20.dp)); Text("저장한 곳 찾기",fontSize=14.sp) } }
                                        FilledTonalIconButton(onClick={permissions.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION))},modifier=Modifier.size(52.dp)) { if(locating) CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp) else Icon(Icons.Rounded.MyLocation,"내 위치") }
                                    }
                                    FilledTonalIconButton(onClick=vm::changeMap,modifier=Modifier.align(Alignment.TopEnd).padding(top=74.dp,end=12.dp).size(48.dp)) { Icon(Icons.Rounded.Layers,if(vm.detailedMap) "간략 지도로 전환" else "상세 지도로 전환") }
                                }
                            }
                            if(wide && selected!=null && candidate!=null) Row(Modifier.fillMaxSize().padding(start=16.dp,end=16.dp,bottom=dockReserve),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                                map(Modifier.weight(1f).fillMaxHeight()); PlaceDetail(vm,selected,candidate,candidates,Modifier.width(360.dp).fillMaxHeight().verticalScroll(rememberScrollState()),onEdit={vm.edit(selected)})
                            } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom=dockReserve),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                map(Modifier.fillMaxWidth().padding(horizontal=8.dp).height(if(selected==null) maxOf(360.dp,availableHeight-200.dp) else (availableHeight*.30f).coerceIn(190.dp,280.dp)))
                                if(selected!=null && candidate!=null) PlaceDetail(vm,selected,candidate,candidates,Modifier.fillMaxWidth().padding(horizontal=8.dp),onEdit={vm.edit(selected)})
                                else Text("핀을 누르면 가까운 역이 보여요.\n지도를 길게 누르면 새 장소를 저장할 수 있어요.",Modifier.padding(20.dp),color=colors.onSurfaceVariant,fontSize=14.sp)
                            }
                        }
                        1 -> SavedScreen(vm,Modifier.fillMaxSize(),dockReserve,::selectPlace,onDelete={p -> vm.delete(p) { scope.launch { if(snackbar.showSnackbar("${p.name} 삭제됨","실행 취소",duration=SnackbarDuration.Long)==SnackbarResult.ActionPerformed) vm.restore(p) } } })
                        2 -> GroupsScreen(vm,rail,Modifier.fillMaxSize(),dockReserve,onStationPicker={showStationPicker=true},onPlace=::selectPlace)
                    }
                }
            }
            FloatingDock(vm.tab,vm::changeTab,Modifier.align(Alignment.BottomCenter).padding(start=20.dp,end=20.dp,bottom=10.dp).widthIn(max=420.dp).fillMaxWidth().onSizeChanged { dockHeight=with(density){it.height.toDp()} })
            SnackbarHost(snackbar,Modifier.align(Alignment.BottomCenter).padding(bottom=dockReserve,start=16.dp,end=16.dp))
            if(importing) LinearProgressIndicator(Modifier.align(Alignment.TopCenter).fillMaxWidth())
        }
    }
    if(showStationPicker && rail!=null) StationPicker(rail.stations,{showStationPicker=false}) { vm.chooseStation(it); showStationPicker=false }
    if(showSettings) Settings(vm,onDismiss={showSettings=false},onImport={showSettings=false;filePicker.launch(arrayOf("text/*","application/json","application/geo+json","application/octet-stream"))})
    importReview?.let { review -> AlertDialog(onDismissRequest={importReview=null},title={Text("가져오기 확인")},text={Column(Modifier.verticalScroll(rememberScrollState())) {
        Text("전체 ${review.total}곳 중 위치 확인 ${review.places.size}곳\n위치·이름 확인 필요 ${review.missing}곳\n싱가포르 밖 ${review.outside}곳")
        Spacer(Modifier.height(12.dp)); review.places.take(12).forEach { Text("• ${it.name}",fontSize=14.sp) }
        Text("확인된 장소만 저장하며 중복은 건너뜁니다. 좌표가 없는 Google Takeout 항목은 개별 공유로 추가해 주세요.",Modifier.padding(top=12.dp),fontSize=12.sp)
    }},confirmButton={TextButton(enabled=review.places.isNotEmpty(),onClick={vm.importPlaces(review.places) { added,duplicates -> importReview=null; vm.message="${added}곳 저장 · 중복 ${duplicates}곳 제외" }}){Text("확인된 장소 저장")}},dismissButton={TextButton(onClick={importReview=null}){Text("취소")}}) }
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
                    Column(Modifier.heightIn(min=62.dp).padding(horizontal=4.dp,vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(2.dp)) { Icon(icon,null,Modifier.size(24.dp)); Text(label,fontSize=12.sp,fontWeight=if(tab==index) FontWeight.Bold else FontWeight.Medium) }
                }
            }
        }
    }
}
@Composable private fun CodeBadges(station: Station) {
    val codeColors=mapOf("NS" to 0xFFB12C37,"EW" to 0xFF126D43,"NE" to 0xFF773582,"CC" to 0xFF8A5804,"DT" to 0xFF205F9B,"TE" to 0xFF785336,"CG" to 0xFF126D43,"CE" to 0xFF8A5804)
    FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) { station.codes.forEach { code -> Text(code,Modifier.background(Color(codeColors[code.take(2)] ?: 0xFF526078),RoundedCornerShape(7.dp)).padding(horizontal=7.dp,vertical=2.dp),color=Color.White,fontSize=12.sp,lineHeight=16.sp,fontWeight=FontWeight.Bold) } }
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

@Composable private fun SavedScreen(vm: HopViewModel,modifier: Modifier,bottom:Dp,onPlace:(Place)->Unit,onDelete:(Place)->Unit) {
    val colors=MaterialTheme.colorScheme
    val visible=vm.places.filter { (vm.visitFilter==0 || (vm.visitFilter==1 && !it.visited) || (vm.visitFilter==2 && it.visited)) && (it.name+it.subtitle+it.memo).contains(vm.query,true) }
    LazyColumn(modifier,contentPadding=PaddingValues(start=20.dp,end=20.dp,top=12.dp,bottom=bottom),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item { Text("MY SINGAPORE",color=colors.primary,fontSize=12.sp,fontWeight=FontWeight.Bold); Text("가고 싶은 ${vm.places.size}곳",fontSize=27.sp,fontWeight=FontWeight.SemiBold) }
        item { OutlinedTextField(vm.query,{vm.query=it},modifier=Modifier.fillMaxWidth(),shape=CircleShape,label={Text("장소·메모 검색")},leadingIcon={Icon(Icons.Rounded.Search,null)},singleLine=true) }
        item { FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("전체","미방문","방문").forEachIndexed { i,label -> FilterChip(vm.visitFilter==i,{vm.visitFilter=i},{Text(label)},modifier=Modifier.heightIn(min=48.dp)) } } }
        if(visible.isEmpty()) item { Text("저장한 장소가 없어요. + 버튼으로 여행지를 추가해 보세요.",Modifier.padding(vertical=24.dp),color=colors.onSurfaceVariant) }
        items(visible,key={it.id}) { p ->
            Surface(shape=RoundedCornerShape(24.dp),color=colors.surface) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { onPlace(p) }.padding(vertical=8.dp).semantics { role=Role.Button }) {
                            Text(p.name,fontWeight=FontWeight.SemiBold,fontSize=17.sp)
                            val near=vm.rail?.let { nearest(p.point,it.stations).firstOrNull() }
                            if(near!=null) Text("${near.station.name} · 직선 ${formatDistance(near.metres)}",color=colors.onSurfaceVariant,fontSize=12.sp)
                            if(p.memo.isNotBlank()) Text(p.memo,fontSize=13.sp,color=colors.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis)
                        }
                        Checkbox(p.visited,{vm.toggleVisited(p)},modifier=Modifier.semantics { contentDescription="${p.name} 방문 완료" })
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                        TextButton(onClick={vm.edit(p)}) { Icon(Icons.Rounded.Edit,null,Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("메모·수정") }
                        IconButton(onClick={onDelete(p)}) { Icon(Icons.Rounded.DeleteOutline,"${p.name} 삭제") }
                    }
                }
            }
        }
        item { OutlinedButton(onClick={vm.beginAdd()},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Icon(Icons.Rounded.Add,null); Text("장소 추가") } }
    }
}

@Composable private fun GroupsScreen(vm: HopViewModel,rail:RailData,modifier:Modifier,bottom:Dp,onStationPicker:()->Unit,onPlace:(Place)->Unit) {
    val station=vm.groupStation ?: return; val colors=MaterialTheme.colorScheme; val rows=within(station,vm.places,vm.radius)
    val camera=rememberMapCamera(); LaunchedEffect(station.id,vm.radius) { camera.focus(station.point,if(vm.radius==1200) 14.2 else if(vm.radius==800) 14.8 else 15.4) }
    LazyColumn(modifier,contentPadding=PaddingValues(start=20.dp,end=20.dp,top=12.dp,bottom=bottom),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Surface(onClick=onStationPicker,shape=CircleShape,color=colors.surfaceVariant) { Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) { Text(station.name,Modifier.weight(1f),fontSize=20.sp,fontWeight=FontWeight.SemiBold); CodeBadges(station); Icon(Icons.Rounded.ExpandMore,null) } } }
        item { Text("한 번 내려서, ${rows.size}곳.",fontSize=25.sp,fontWeight=FontWeight.SemiBold) }
        item { Row(Modifier.fillMaxWidth().background(colors.surfaceVariant,CircleShape).padding(4.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) { listOf(400,800,1200).forEach { r ->
            val chosen=vm.radius==r
            Surface(onClick={vm.changeRadius(r)},modifier=Modifier.weight(1f).semantics { selected=chosen },shape=CircleShape,color=if(chosen) colors.surface else Color.Transparent) { Text(if(r==1200) "1.2km" else "${r}m",Modifier.padding(vertical=14.dp,horizontal=8.dp),textAlign=androidx.compose.ui.text.style.TextAlign.Center,color=if(chosen) colors.primary else colors.onSurface,fontWeight=if(chosen) FontWeight.Bold else FontWeight.Normal) }
        } } }
        item { SingaporeMap(rail,rows.map { it.first },camera,Modifier.fillMaxWidth().height(185.dp).clip(RoundedCornerShape(28.dp)),selectedStation=station,radius=vm.radius,onPlace=onPlace) }
        item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text("함께 갈 수 있는 내 스팟",fontWeight=FontWeight.SemiBold,fontSize=14.sp); Text("직선거리 순",fontSize=12.sp,color=colors.onSurfaceVariant) } }
        if(rows.isEmpty()) item { Text("이 반경에는 저장한 곳이 없어요.\n반경을 넓히거나 다른 역을 골라 보세요.",Modifier.padding(vertical=24.dp),color=colors.onSurfaceVariant) }
        items(rows,key={it.first.id}) { (place,distance) -> Surface(onClick={onPlace(place)},shape=RoundedCornerShape(22.dp),color=colors.surface) { Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) { Column(Modifier.weight(1f)) { Text(place.name,fontWeight=FontWeight.SemiBold); Text(place.subtitle,fontSize=12.sp,color=colors.onSurfaceVariant,maxLines=2) }; Text(formatDistance(distance),color=colors.primary,fontWeight=FontWeight.SemiBold); Icon(Icons.Rounded.ChevronRight,null,Modifier.size(20.dp)) } } }
        item { Text("역 중심 기준이에요. 실제 도보 동선은 달라요.",fontSize=12.sp,color=colors.onSurfaceVariant) }
    }
}

@Composable private fun StationPicker(stations:List<Station>,onDismiss:()->Unit,onSelect:(Station)->Unit) {
    var query by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest=onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal=20.dp)) { Text("역 선택",fontSize=24.sp,fontWeight=FontWeight.SemiBold); OutlinedTextField(query,{query=it},label={Text("역 이름 또는 코드")},modifier=Modifier.fillMaxWidth().padding(vertical=12.dp),singleLine=true,shape=CircleShape) }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max=480.dp),contentPadding=PaddingValues(start=20.dp,end=20.dp,bottom=32.dp)) {
            items(stations.filter { (it.name+it.codes.joinToString()).contains(query,true) }.sortedBy { it.name },key={it.id}) { s -> ListItem(headlineContent={Text(s.name)},supportingContent={CodeBadges(s)},modifier=Modifier.clip(RoundedCornerShape(16.dp)).clickable { onSelect(s) }.heightIn(min=72.dp)) }
        }
    }
}

@Composable private fun Settings(vm:HopViewModel,onDismiss:()->Unit,onImport:()->Unit) {
    val uri=LocalUriHandler.current
    ModalBottomSheet(onDismissRequest=onDismiss) { Column(Modifier.fillMaxWidth().padding(horizontal=24.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("나의 싱가포르",fontSize=25.sp,fontWeight=FontWeight.SemiBold)
        Text("장소는 이 기기에 저장돼요. 로그인 없이 사용할 수 있어요.",fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text("화면 테마",fontWeight=FontWeight.SemiBold)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("system" to "시스템","light" to "라이트","dark" to "다크").forEach { (value,label) -> FilterChip(vm.theme==value,{vm.changeTheme(value)},{Text(label)},modifier=Modifier.heightIn(min=48.dp)) } }
        OutlinedButton(onClick=onImport,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Icon(Icons.Rounded.FileOpen,null); Spacer(Modifier.width(8.dp)); Text("CSV · GeoJSON 가져오기") }
        Text("Google 즐겨찾기 자동 동기화는 지원하지 않아요. Google 지도에서 개별 장소를 공유하거나 좌표가 있는 파일을 가져오세요.",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider()
        Text("MRT ${vm.rail?.stations?.size ?: 0}개 역 · 데이터 기준 2026.10.28",fontSize=13.sp)
        Text("도심 간략 지도는 오프라인으로 제공됩니다. 상세 지도는 인터넷이 필요합니다. 거리 계산은 역 대표점 기준이며 실제 도보거리와 다릅니다.",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
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
    LaunchedEffect(point) { point?.let { camera.focus(it,15.5) } }
    Dialog(onDismissRequest={if(vm.picking) vm.picking=false else onClose()},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        Surface(Modifier.fillMaxSize(),color=colors.background) {
            Column(Modifier.fillMaxSize().padding(top=topInset,bottom=bottomInset).consumeWindowInsets(PaddingValues(top=topInset,bottom=bottomInset)).imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) { IconButton(onClick={if(vm.picking) vm.picking=false else onClose()}) { Icon(Icons.AutoMirrored.Rounded.ArrowBack,"장소 추가 닫기") }; Text(if(vm.picking) "지도에서 위치 선택" else if(d.editingId!=null) "장소 수정" else "장소 추가",fontSize=22.sp,fontWeight=FontWeight.SemiBold) }
                if(vm.picking) {
                    Box(Modifier.weight(1f).padding(horizontal=12.dp).clip(RoundedCornerShape(28.dp))) {
                        SingaporeMap(rail,emptyList(),camera,Modifier.fillMaxSize(),detailed=vm.detailedMap,onLongPress={vm.picked(it)})
                        Icon(Icons.Rounded.LocationOn,null,Modifier.align(Alignment.Center).size(42.dp).offset(y=(-21).dp),tint=colors.primary)
                    }
                    Text("지도를 움직여 가운데 핀을 맞춰 주세요.",Modifier.padding(16.dp),fontSize=14.sp)
                    Button(onClick={if(camera.point.inSingapore) vm.picked(camera.point) else vm.draftError="싱가포르 안에서 선택해 주세요."},modifier=Modifier.fillMaxWidth().padding(16.dp).heightIn(min=56.dp)) { Text("이 위치 선택") }
                } else {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    if(d.editingId==null) {
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
                Surface(color=colors.background) { Button(onClick=vm::saveDraft,enabled=!vm.busy,modifier=Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp).heightIn(min=58.dp),shape=CircleShape) { Icon(Icons.Rounded.BookmarkAdd,null); Spacer(Modifier.width(8.dp)); Text("내 장소에 저장",fontWeight=FontWeight.SemiBold) } }
                }
            }
        }
    }
}
