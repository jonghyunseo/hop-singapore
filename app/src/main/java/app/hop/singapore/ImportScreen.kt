@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package app.hop.singapore

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable fun GoogleImportHelp(onDismiss:()->Unit,onChooseFile:()->Unit,onShare:()->Unit) {
    val uri=LocalUriHandler.current; val colors=MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=colors.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal=20.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.Bookmarks,null,Modifier.size(28.dp),tint=colors.primary)
            Text("Google 지도 즐겨찾기",fontSize=23.sp,fontWeight=FontWeight.SemiBold)
            Text("저장한 목록과 여행 메모를 hop 지도로 가져와요.",fontSize=14.sp,color=colors.onSurfaceVariant)
            Surface(shape=RoundedCornerShape(24.dp),color=colors.surfaceVariant) {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("1  Google Takeout에서 Saved(저장됨) 선택",fontSize=14.sp)
                    Text("2  내보내기를 만든 뒤 ZIP 다운로드",fontSize=14.sp)
                    Text("3  파일 선택 → 장소 위치 확인 → 가져오기",fontSize=14.sp)
                }
            }
            OutlinedButton(onClick={uri.openUri("https://takeout.google.com/")},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text("Google Takeout 열기"); Spacer(Modifier.width(8.dp)); Icon(Icons.Rounded.OpenInNew,null,Modifier.size(18.dp)) }
            Button(onClick=onChooseFile,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Icon(Icons.Rounded.FileOpen,null); Spacer(Modifier.width(8.dp)); Text("다운로드한 파일 선택") }
            Text("ZIP · CSV · GeoJSON / 최대 1,000곳\n좌표가 없는 항목은 장소명 검색이나 지도 핀으로 확인해요. 계정 자동 동기화는 지원하지 않아요.",fontSize=12.sp,color=colors.onSurfaceVariant)
            TextButton(onClick=onShare,modifier=Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Link,null); Spacer(Modifier.width(8.dp)); Text("장소 하나는 Google 지도 링크로 추가") }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable fun GoogleImportReview(vm:HopViewModel) {
    val review=vm.importReview ?: return; val colors=MaterialTheme.colorScheme
    var needsOnly by remember { mutableStateOf(false) }; var close by remember { mutableStateOf(false) }
    val top=WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom=WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Dialog(onDismissRequest={if(!vm.importSaving) close=true},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        Surface(Modifier.fillMaxSize(),color=colors.background) {
            Column(Modifier.fillMaxSize().padding(top=top,bottom=bottom)) {
                Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick={close=true},enabled=!vm.importSaving) { Icon(Icons.AutoMirrored.Rounded.ArrowBack,"가져오기 닫기") }
                    Text("즐겨찾기 가져오기",fontSize=20.sp,fontWeight=FontWeight.SemiBold)
                }
                Text("${review.entries.size}곳 · 선택 ${review.selected.size}곳",Modifier.padding(horizontal=20.dp,vertical=8.dp),fontSize=22.sp,fontWeight=FontWeight.SemiBold)
                Text("핀 위치를 확인하고 가져올 장소를 선택하세요. 중복은 자동으로 제외해요.",Modifier.padding(horizontal=20.dp),fontSize=13.sp,color=colors.onSurfaceVariant)
                vm.importNotice?.let { Text(it,Modifier.padding(horizontal=20.dp,vertical=8.dp),fontSize=13.sp,color=colors.primary) }
                Row(Modifier.padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(!needsOnly,{needsOnly=false},{Text("전체")})
                    FilterChip(needsOnly,{needsOnly=true},{Text("위치 확인 ${review.entries.count { !it.ready }}")})
                }
                if(review.ignoredFiles>0) Text("장소 목록 외 ${review.ignoredFiles}개 파일 제외",Modifier.padding(horizontal=20.dp),fontSize=12.sp,color=colors.onSurfaceVariant)
                if(vm.importSaving) LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(horizontal=16.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    val visible=review.entries.filter { !needsOnly || !it.ready }
                    if(visible.isEmpty()) item { Text("위치 확인이 필요한 장소가 없어요.",Modifier.padding(16.dp)) }
                    items(visible,key={it.id}) { entry ->
                        Surface(shape=RoundedCornerShape(20.dp),color=colors.surface) {
                            Row(Modifier.fillMaxWidth().padding(start=12.dp,end=4.dp,top=8.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically) {
                                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                                    Text(entry.name.ifBlank { "이름 없는 장소" },fontSize=15.sp,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis)
                                    Text(when { entry.ready -> vm.rail?.let { nearest(entry.point!!,it.stations).firstOrNull()?.let { c -> "${c.station.name} · ${formatDistance(c.metres)}" } } ?: "위치 확인됨"; entry.point!=null && !entry.point.inSingapore -> "싱가포르 밖 · 저장 제외"; else -> "좌표 없음 · 위치 확인 필요" },fontSize=12.sp,color=if(entry.ready) colors.primary else colors.onSurfaceVariant)
                                    if(entry.memo.isNotBlank()) Text(entry.memo,fontSize=12.sp,color=colors.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
                                    if(entry.collection.isNotBlank()) Text(entry.collection,fontSize=11.sp,color=colors.onSurfaceVariant,maxLines=1)
                                }
                                if(entry.ready) {
                                    IconButton(onClick={vm.editImport(entry)},enabled=!vm.importSaving) { Icon(Icons.Rounded.EditLocationAlt,"${entry.name} 위치 수정",Modifier.size(20.dp)) }
                                    Checkbox(entry.selected,{vm.toggleImport(entry.id)},enabled=!vm.importSaving,modifier=Modifier.semantics { contentDescription="${entry.name} 가져오기 선택" })
                                } else TextButton(onClick={vm.editImport(entry)},enabled=!vm.importSaving) { Text("위치 확인",fontSize=12.sp) }
                            }
                        }
                    }
                }
                Surface(color=colors.background) {
                    Column(Modifier.padding(horizontal=20.dp,vertical=8.dp)) {
                        if(review.entries.any { !it.ready }) Text("확인이 필요한 항목은 저장 후에도 이 목록에 남아요.",fontSize=12.sp,color=colors.onSurfaceVariant)
                        Button(onClick=vm::commitImport,enabled=review.selected.isNotEmpty() && !vm.importSaving,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),shape=CircleShape) { Text("선택한 ${review.selected.size}곳 가져오기") }
                    }
                }
            }
        }
        if(close) AlertDialog(onDismissRequest={close=false},title={Text("가져오기 목록을 닫을까요?")},text={Text("이미 저장한 장소는 유지돼요. 남은 항목은 파일을 다시 선택해 가져올 수 있어요.")},confirmButton={TextButton(onClick={close=false;vm.closeImport()}){Text("닫기")}},dismissButton={TextButton(onClick={close=false}){Text("계속 확인")}})
    }
}
