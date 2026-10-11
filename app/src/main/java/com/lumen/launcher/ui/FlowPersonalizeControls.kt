package com.lumen.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.lumen.launcher.flow.parseFlowOrder
import com.lumen.launcher.ui.theme.Lumen
import kotlinx.coroutines.delay

@Composable
internal fun FlowConnectionRow(title: String, detail: String, allowed: Boolean, onAllow: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom=8.dp)
        .background(Color.White.copy(alpha=.08f),RoundedCornerShape(16.dp)).padding(12.dp),
        verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title,color=Lumen.Text,fontSize=15.sp)
            Text(detail,color=Lumen.Faint,fontSize=12.sp)
        }
        if(allowed) Text("✓ Allowed",color=Lumen.Text,fontSize=12.sp)
        else TextButton(onClick=onAllow) { Text("Allow",color=Color(0xFFF5CB7E)) }
    }
}

@Composable
internal fun FlowCardOrderEditor(
    flowOrder: List<String>, enabled: Set<String>, onToggle: (String)->Unit,
    onMove: (String,Int)->Unit, modifier: Modifier = Modifier
) {
    var order by remember(flowOrder) { mutableStateOf(parseFlowOrder(flowOrder)) }
    var dragging by remember { mutableStateOf<String?>(null) }
    var startIndex by remember { mutableIntStateOf(0) }
    var offset by remember { mutableFloatStateOf(0f) }
    val list = rememberLazyListState()
    val step = with(LocalDensity.current) {72.dp.toPx()}
    val move by rememberUpdatedState(onMove)
    fun finish() {
        dragging?.let {name ->
            val destination=order.indexOfFirst {it.name==name}
            if(destination>=0 && destination!=startIndex) move(name,destination-startIndex)
        }
        dragging=null;offset=0f
    }
    fun shift(delta: Float) {
        offset+=delta
        val name=dragging ?: return
        val index=order.indexOfFirst {it.name==name}
        val direction=when {offset>step * .65f -> 1;offset< -step * .65f -> -1;else -> 0}
        val target=index+direction
        if(direction!=0 && target in order.indices) {
            order=order.toMutableList().apply {add(target,removeAt(index))}
            offset-=direction*step
        }
    }
    LaunchedEffect(dragging) {
        while(dragging!=null) {
            val item=list.layoutInfo.visibleItemsInfo.firstOrNull {it.key==dragging}
            if(item!=null) {
                val top=item.offset+offset
                val bottom=top+item.size
                val delta=when {
                    bottom>list.layoutInfo.viewportEndOffset-32 -> 8f
                    top<list.layoutInfo.viewportStartOffset+32 -> -8f
                    else -> 0f
                }
                if(delta!=0f) shift(list.scrollBy(delta))
            }
            delay(16)
        }
    }
    Column(modifier) {
        Text("Choose your cards. Drag the handles to reorder.",color=Lumen.Faint,fontSize=14.sp,modifier=Modifier.padding(bottom=12.dp))
        LazyColumn(state=list,verticalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.weight(1f)) {
            items(order,key={it.name}) {module ->
                val active=dragging==module.name
                Row(Modifier.animateItem(placementSpec=if(dragging != null && !active) androidx.compose.animation.core.spring(stiffness=600f) else null).fillMaxWidth().height(64.dp).zIndex(if(active) 1f else 0f)
                    .graphicsLayer {translationY=if(active) offset else 0f}
                    .background(Color.White.copy(alpha=if(active) .2f else .08f),RoundedCornerShape(16.dp))
                    .padding(start=12.dp,end=4.dp),verticalAlignment=Alignment.CenterVertically) {
                    Switch(checked=module.name in enabled,onCheckedChange={onToggle(module.name)},
                        modifier=Modifier.semantics {contentDescription=module.title},
                        colors=SwitchDefaults.colors(
                            checkedThumbColor=Color.White,checkedTrackColor=Color(0xFFF5CB7E),checkedBorderColor=Color(0xFFF5CB7E),
                            uncheckedThumbColor=Color.White,uncheckedTrackColor=Color.White.copy(alpha=.23f),uncheckedBorderColor=Color.White.copy(alpha=.3f)))
                    Text(module.title,color=Lumen.Text,fontSize=16.sp,modifier=Modifier.weight(1f).padding(start=10.dp))
                    Box(Modifier.size(48.dp).semantics {
                        contentDescription="Reorder ${module.title}"
                        customActions=listOf(
                            CustomAccessibilityAction("Move up") {onMove(module.name,-1);true},
                            CustomAccessibilityAction("Move down") {onMove(module.name,1);true})
                    }.pointerInput(module.name) {
                        detectDragGestures(
                            onDragStart={dragging=module.name;startIndex=order.indexOfFirst {it.name==module.name};offset=0f},
                            onDragEnd={finish()},onDragCancel={order=parseFlowOrder(flowOrder);dragging=null;offset=0f},
                            onDrag={change,delta -> change.consume();shift(delta.y)})
                    },contentAlignment=Alignment.Center) {
                        Text("≡",color=Lumen.Text,fontSize=26.sp)
                    }
                }
            }
        }
    }
}