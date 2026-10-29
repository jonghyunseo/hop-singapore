@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package app.hop.singapore

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private fun TravelKind.icon():ImageVector=when(this) {
    TravelKind.PLACE->Icons.Rounded.Place; TravelKind.FOOD->Icons.Rounded.Restaurant; TravelKind.STAY->Icons.Rounded.Hotel
    TravelKind.BOOKING->Icons.Rounded.ConfirmationNumber; TravelKind.GUIDE->Icons.Rounded.CollectionsBookmark; TravelKind.NOTE->Icons.Rounded.Notes
}

@Composable fun TravelScreen(travel:TravelViewModel,hop:HopViewModel,modifier:Modifier,bottom:Dp,onPlace:(Place)->Unit,onImport:()->Unit,onDelete:(Place)->Unit) {
    val colors=MaterialTheme.colorScheme
    var help by rememberSaveable { mutableStateOf(false) }
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            listOf("공유함","일정","장소").forEachIndexed { index,title ->
                Surface(onClick={travel.section=index},modifier=Modifier.weight(1f).semantics { selected=travel.section==index; role=Role.Tab },shape=CircleShape,color=if(travel.section==index) colors.primaryContainer else colors.surfaceVariant) {
                    Text(title,Modifier.padding(vertical=14.dp),fontSize=14.sp,fontWeight=FontWeight.SemiBold,textAlign=androidx.compose.ui.text.style.TextAlign.Center,color=if(travel.section==index) colors.onPrimaryContainer else colors.onSurfaceVariant)
                }
            }
        }
        if(travel.section==2) SavedScreen(hop,Modifier.weight(1f),bottom,onPlace,onImport,onDelete,travel::plan)
        else {
            val filtered=travel.items.filter { (it.title+it.note+it.content+it.kind.label).contains(travel.query,true) && when(travel.filter) { 1->it.pinned; 2->!it.done; else->true } }
            val ordered=if(travel.section==1) scheduleItems(filtered) else filtered.sortedWith(compareByDescending<TravelItem> { it.pinned }.thenByDescending { it.createdAt })
            LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(start=16.dp,end=16.dp,top=4.dp,bottom=bottom+8.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                if(travel.items.isEmpty()) item {
                    Surface(shape=RoundedCornerShape(24.dp),color=colors.primaryContainer) {
                        Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            Icon(if(travel.section==1) Icons.Rounded.EventNote else Icons.Rounded.MoveToInbox,null,tint=colors.primary,modifier=Modifier.size(28.dp))
                            Column(Modifier.weight(1f)) {
                                Text(if(travel.section==1) "꺼내 보기 쉬운 여행 일정" else "공유하면, 여행에 남아요",fontWeight=FontWeight.SemiBold,fontSize=17.sp)
                                Text(if(travel.section==1) "날짜·시간은 싱가포르 현지 기준" else "지도 목록 · 예약 링크 · 맛집 · 메모",fontSize=13.sp,color=colors.onPrimaryContainer)
                            }
                            IconButton(onClick={help=true}) { Icon(Icons.Rounded.HelpOutline,"공유로 보관하는 방법") }
                        }
                    }
                }
                if(travel.items.isNotEmpty() && travel.section==1) item { Text("싱가포르 현지 시간 기준",fontSize=13.sp,color=colors.onSurfaceVariant) }
                if(travel.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                travel.failure?.let { problem->item { Text(problem,color=colors.error) } }
                item { OutlinedTextField(travel.query,{travel.query=it},Modifier.fillMaxWidth(),label={Text("여행 정보 검색")},leadingIcon={Icon(Icons.Rounded.Search,null)},singleLine=true,shape=CircleShape) }
                item { FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("전체 ${travel.items.size}","중요","미완료").forEachIndexed { i,label -> FilterChip(travel.filter==i,{travel.filter=i},{Text(label)},modifier=Modifier.heightIn(min=48.dp)) } } }
                if(ordered.isEmpty() && !travel.loading) item {
                    Column(Modifier.fillMaxWidth().padding(vertical=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        Text(if(travel.items.isEmpty()) "보고 있던 정보를 그대로 보관하세요" else "조건에 맞는 정보가 없어요",fontSize=19.sp,fontWeight=FontWeight.SemiBold)
                        Text("Google 지도·브라우저·예약 앱에서\n공유 → hop을 선택하면 바로 저장돼요.\n목록 링크는 원문으로 열어 볼 수 있어요.",fontSize=15.sp,color=colors.onSurfaceVariant)
                        Button(onClick=travel::beginNew,modifier=Modifier.heightIn(min=48.dp)) { Icon(Icons.Rounded.Add,null); Spacer(Modifier.width(8.dp)); Text("링크·메모 직접 추가") }
                    }
                }
                if(travel.section==1) {
                    ordered.groupBy { it.date }.forEach { (day,entries)->
                        item(key="day-$day") { Text((if(day==singaporeToday()) "오늘 · " else "")+travelDayLabel(day),Modifier.padding(top=12.dp,bottom=2.dp),fontWeight=FontWeight.SemiBold,fontSize=18.sp) }
                        items(entries,key={it.id}) { item->TravelItemCard(item,hop.places.find { it.id==item.placeId },true,{travel.fromShare=false;travel.selectedId=item.id},{travel.toggleDone(item)}) }
                    }
                } else items(ordered,key={it.id}) { item->TravelItemCard(item,hop.places.find { it.id==item.placeId },false,{travel.fromShare=false;travel.selectedId=item.id},{travel.toggleDone(item)}) }
            }
        }
    }
    if(help) ShareHelpSheet { help=false }
}

@Composable private fun TravelItemCard(item:TravelItem,place:Place?,scheduled:Boolean,onOpen:()->Unit,onDone:()->Unit) {
    val colors=MaterialTheme.colorScheme
    Surface(shape=RoundedCornerShape(24.dp),color=colors.surface) {
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable(onClick=onOpen).padding(4.dp).semantics { role=Role.Button },verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    Icon(item.kind.icon(),null,Modifier.size(18.dp),tint=colors.primary)
                    Text(item.kind.label+" · "+TravelShare.source(item.url),fontSize=12.sp,color=colors.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f))
                    if(item.pinned) Icon(Icons.Rounded.PushPin,"중요한 정보",Modifier.size(16.dp),tint=colors.tertiary)
                }
                Text(item.title,fontSize=17.sp,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis)
                if(item.note.isNotBlank()) Text(item.note,fontSize=14.sp,maxLines=2,overflow=TextOverflow.Ellipsis,color=colors.onSurfaceVariant)
                val meta=listOfNotNull(if(scheduled) item.time.ifBlank { "시간 미정" } else item.date.takeIf { it.isNotBlank() }?.let { travelDayLabel(it)+" "+item.time },place?.name?.let { "지도 연결 · $it" },if(item.done) "완료" else null).joinToString(" · ")
                if(meta.isNotBlank()) Text(meta,fontSize=12.sp,color=colors.primary)
            }
            if(scheduled) Checkbox(item.done,{onDone()},modifier=Modifier.semantics { contentDescription="${item.title} 일정 완료" })
            else IconButton(onClick=onOpen) { Icon(Icons.Rounded.ChevronRight,"${item.title} 열기") }
        }
    }
}

@Composable fun TravelDetail(travel:TravelViewModel,hop:HopViewModel,onPlace:(Place)->Unit,onLink:(TravelItem)->Unit) {
    val item=travel.selected ?: return; val context=LocalContext.current; val colors=MaterialTheme.colorScheme
    val place=hop.places.find { it.id==item.placeId }
    var expanded by remember(item.id) { mutableStateOf(false) }
    fun close() { travel.selectedId=null; travel.fromShare=false }
    fun openOriginal() {
        if(!TravelShare.safeUrl(item.url)) { travel.message="열 수 있는 웹 링크가 없어요."; return }
        runCatching { CustomTabsIntent.Builder().setShowTitle(true).setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder().setToolbarColor(colors.surface.toArgb()).build()).build().launchUrl(context,Uri.parse(item.url)) }.onFailure { travel.message="링크를 열 수 있는 브라우저가 없어요." }
    }
    ModalBottomSheet(onDismissRequest=::close,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=colors.surface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=20.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(travel.fromShare) Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp),modifier=Modifier.semantics { liveRegion=LiveRegionMode.Polite }) { Icon(Icons.Rounded.CheckCircle,null,Modifier.size(18.dp),tint=colors.primary); Text("공유함에 보관됨",fontSize=13.sp,color=colors.primary) }
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(item.kind.label+" · "+TravelShare.source(item.url),Modifier.weight(1f),fontSize=13.sp,color=colors.onSurfaceVariant)
                IconToggleButton(item.pinned,{travel.togglePin(item)}) { Icon(if(item.pinned) Icons.Rounded.PushPin else Icons.Rounded.BookmarkBorder,if(item.pinned) "중요 해제" else "중요 표시") }
                IconButton(onClick=::close) { Icon(Icons.Rounded.Close,"여행 정보 닫기") }
            }
            Text(item.title,fontSize=25.sp,fontWeight=FontWeight.SemiBold)
            if(item.date.isNotBlank()) AssistChip(onClick={travel.edit(item)},label={Text(travelDayLabel(item.date)+" "+item.time)},leadingIcon={Icon(Icons.Rounded.Schedule,null,Modifier.size(18.dp))})
            if(item.note.isNotBlank()) Surface(shape=RoundedCornerShape(20.dp),color=colors.surfaceVariant) { Text(item.note,Modifier.fillMaxWidth().padding(16.dp),fontSize=16.sp) }
            if(item.url.isNotBlank()) {
                Button(onClick=::openOriginal,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Icon(Icons.Rounded.OpenInNew,null); Spacer(Modifier.width(8.dp)); Text("원문 보기") }
                Text(if(TravelShare.isMaps(item.url)) "Google 지도에서 공유한 원본을 열어요. 목록은 링크로 보관되며, 개별 장소의 지도 연결은 선택할 수 있어요." else "원본 페이지를 열어 예약·정보를 확인해요. 로그인이나 인터넷이 필요할 수 있어요.",fontSize=13.sp,color=colors.onSurfaceVariant)
            } else if(item.content.isNotBlank() && item.content!=item.title) Text(item.content,fontSize=16.sp)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={travel.edit(item)},modifier=Modifier.weight(1f).heightIn(min=48.dp),contentPadding=PaddingValues(8.dp)) { Icon(Icons.Rounded.EventNote,null,Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("일정·메모") }
                FilledTonalButton(onClick={if(place!=null) { close();onPlace(place) } else { travel.selectedId=null;onLink(item) }},modifier=Modifier.weight(1f).heightIn(min=48.dp),contentPadding=PaddingValues(8.dp)) { Icon(Icons.Rounded.Map,null,Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(if(place!=null) "지도 보기" else "장소 연결") }
            }
            Row(Modifier.fillMaxWidth().toggleable(value=item.done,role=Role.Checkbox,onValueChange={travel.toggleDone(item)}),verticalAlignment=Alignment.CenterVertically) { Checkbox(item.done,null); Text(if(item.date.isBlank()) "확인 완료" else "이 일정 완료",fontSize=14.sp) }
            if(item.url.isNotBlank()) {
                TextButton(onClick={expanded=!expanded}) { Text(if(expanded) "공유한 내용 접기" else "공유한 내용 보기"); Icon(if(expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,null) }
                if(expanded) Text(item.content.ifBlank { item.url },fontSize=14.sp,color=colors.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                TextButton(onClick={runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type="text/plain"; putExtra(Intent.EXTRA_SUBJECT,item.title); putExtra(Intent.EXTRA_TEXT,listOf(item.title,item.url.ifBlank { item.content },item.note).filter { it.isNotBlank() }.distinct().joinToString("\n")) },"여행 정보 공유")) }.onFailure { travel.message="공유 앱을 열 수 없어요." }}) { Icon(Icons.Rounded.Share,null,Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("다시 공유") }
                TextButton(onClick={travel.remove(item)},enabled=!travel.saving) { Text("삭제",color=colors.error) }
            }
            if(travel.fromShare) OutlinedButton(onClick={(context as? Activity)?.finish()},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text("공유한 앱으로 돌아가기") }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable fun TravelPlacePicker(item:TravelItem,places:List<Place>,onDismiss:()->Unit,onPick:(Place)->Unit,onNew:()->Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("어느 장소의 정보인가요?",fontSize=22.sp,fontWeight=FontWeight.SemiBold)
            Text(item.title,fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2)
            OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),label={Text("저장한 장소 검색")},shape=CircleShape,singleLine=true)
            LazyColumn(Modifier.fillMaxWidth().weight(1f,false).heightIn(max=320.dp)) {
                items(places.filter { (it.name+it.subtitle).contains(query,true) },key={it.id}) { place->
                    ListItem(headlineContent={Text(place.name)},supportingContent={Text(place.subtitle,maxLines=1,overflow=TextOverflow.Ellipsis)},leadingContent={Icon(Icons.Rounded.Place,null)},modifier=Modifier.clickable { onPick(place) }.heightIn(min=64.dp))
                }
            }
            Button(onClick=onNew,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Icon(Icons.Rounded.AddLocationAlt,null); Spacer(Modifier.width(8.dp)); Text(if(TravelShare.isMaps(item.url) && !TravelShare.isList(item.url)) "공유한 장소 확인·추가" else "새 장소 검색·추가") }
            TextButton(onClick=onDismiss,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text("나중에 연결") }
        }
    }
}

@Composable fun TravelEditor(travel:TravelViewModel) {
    val item=travel.editing ?: return; val colors=MaterialTheme.colorScheme; val clipboard=LocalClipboardManager.current
    var calendar by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    val initial=remember(item.id) { item }
    fun close() { if(item!=initial) discard=true else travel.editing=null }
    Dialog(onDismissRequest=::close,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        Surface(Modifier.fillMaxSize(),color=colors.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(8.dp),verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick=::close,enabled=!travel.saving) { Icon(Icons.AutoMirrored.Rounded.ArrowBack,"여행 정보 편집 닫기") }
                    Text(if(item.id.isBlank()) "링크·메모 보관" else "일정과 메모",fontSize=22.sp,fontWeight=FontWeight.SemiBold)
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    if(item.id.isBlank()) OutlinedButton(onClick={clipboard.getText()?.text?.takeIf { it.isNotBlank() }?.let { travel.editing=TravelShare.capture(it) }},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Icon(Icons.Rounded.ContentPaste,null); Spacer(Modifier.width(8.dp)); Text("복사한 링크·내용 붙여넣기") }
                    OutlinedTextField(item.title,{travel.editing=item.copy(title=it.take(160))},Modifier.fillMaxWidth(),label={Text("제목")},shape=RoundedCornerShape(20.dp),isError=item.title.isBlank() && travel.editError!=null)
                    OutlinedTextField(item.url,{travel.editing=item.copy(url=it.take(8000))},Modifier.fillMaxWidth(),label={Text("원본 링크 (선택)")},shape=RoundedCornerShape(20.dp),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Uri),maxLines=3)
                    Text("분류",fontWeight=FontWeight.SemiBold)
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { TravelKind.entries.forEach { kind->FilterChip(item.kind==kind,{travel.editing=item.copy(kind=kind)},{Text(kind.label)},leadingIcon={Icon(kind.icon(),null,Modifier.size(18.dp))},modifier=Modifier.heightIn(min=48.dp)) } }
                    Text("일정 · 싱가포르 현지 시간",fontWeight=FontWeight.SemiBold)
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        FilledTonalButton(onClick={calendar=true},modifier=Modifier.heightIn(min=48.dp)) { Icon(Icons.Rounded.CalendarMonth,null); Spacer(Modifier.width(8.dp)); Text(if(item.date.isBlank()) "날짜 선택" else travelDayLabel(item.date)) }
                        if(item.date.isNotBlank()) TextButton(onClick={travel.editing=item.copy(date="",time="")},modifier=Modifier.heightIn(min=48.dp)) { Text("날짜 지우기") }
                    }
                    if(item.date.isNotBlank()) OutlinedTextField(item.time,{travel.editing=item.copy(time=it.take(5))},Modifier.fillMaxWidth(),label={Text("시간 (선택, 09:30)")},shape=RoundedCornerShape(20.dp),singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Text))
                    OutlinedTextField(item.note,{travel.editing=item.copy(note=it.take(8000))},Modifier.fillMaxWidth(),label={Text("메모 · 예약번호 · 준비할 것")},shape=RoundedCornerShape(20.dp),minLines=3)
                    Row(Modifier.fillMaxWidth().toggleable(value=item.pinned,role=Role.Checkbox,onValueChange={travel.editing=item.copy(pinned=it)}),verticalAlignment=Alignment.CenterVertically) { Checkbox(item.pinned,null); Text("중요한 정보로 위에 고정",fontSize=14.sp) }
                    travel.editError?.let { Text(it,color=colors.error,modifier=Modifier.semantics { liveRegion=LiveRegionMode.Polite }) }
                    Spacer(Modifier.height(8.dp))
                }
                Button(onClick=travel::saveEdit,enabled=!travel.saving,modifier=Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=8.dp).heightIn(min=52.dp)) { if(travel.saving) CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp) else Text("여행에 저장") }
            }
        }
        if(calendar) {
            val state=rememberDatePickerState(initialSelectedDateMillis=runCatching { LocalDate.parse(item.date.ifBlank { singaporeToday() }).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull())
            DatePickerDialog(onDismissRequest={calendar=false},confirmButton={TextButton(onClick={state.selectedDateMillis?.let { travel.editing=item.copy(date=Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) };calendar=false}) { Text("날짜 정하기") }},dismissButton={TextButton(onClick={calendar=false}) { Text("취소") }}) { DatePicker(state) }
        }
        if(discard) AlertDialog(onDismissRequest={discard=false},title={Text("수정한 내용을 닫을까요?")},text={Text("이미 보관한 원본 정보는 유지돼요.")},confirmButton={TextButton(onClick={travel.editing=null;discard=false}) { Text("닫기") }},dismissButton={TextButton(onClick={discard=false}) { Text("계속 편집") }})
    }
}

@Composable fun ShareHelpSheet(onDismiss:()->Unit) {
    ModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Rounded.MoveToInbox,null,Modifier.size(32.dp),tint=MaterialTheme.colorScheme.primary)
            Text("공유 한 번으로 여행에 보관",fontSize=24.sp,fontWeight=FontWeight.SemiBold)
            Text("1  지도·브라우저·예약 앱에서 공유 누르기\n2  공유 대상에서 hop 선택\n3  보관 완료. 날짜와 메모는 필요할 때 추가",fontSize=16.sp,lineHeight=28.sp)
            Text("Google 지도 목록은 원본 링크로 보관해 바로 열어요. 블로그·예약 링크·선택한 텍스트도 받을 수 있어요. 좌표를 몰라도 저장할 수 있어요.",fontSize=15.sp)
            Text("공유 대상에 hop이 없으면 ‘더보기’를 확인하세요. 웹 원문은 인터넷과 해당 서비스의 로그인이 필요할 수 있어요.",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick=onDismiss,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text("알겠어요") }
        }
    }
}
