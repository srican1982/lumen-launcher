package com.lumen.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.launcher.data.TravelCategory
import com.lumen.launcher.vm.LauncherUiState
import com.lumen.launcher.vm.LauncherViewModel
import com.lumen.launcher.ui.theme.Outfit

@Composable
internal fun TravelPadPanel(state: LauncherUiState, vm: LauncherViewModel, modifier: Modifier) {
    var open by remember(state.homePulse,state.activeSpace) { mutableStateOf<TravelCategory?>(null) }
    val passes=state.travelAttachments.filter { it.travelCategory==TravelCategory.Flights }
    Column(modifier.padding(8.dp),verticalArrangement=Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
        com.lumen.launcher.travel.TripModeCard(compact=true)
        Row(Modifier.fillMaxWidth().height(78.dp).clip(RoundedCornerShape(12.dp))
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFFE9C5FF),Color(0xFFB9DFFF)))).clickable { open=TravelCategory.Flights }.padding(8.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("BOARDING PASSES",color=Color(0xFF393044),fontFamily=Outfit,fontSize=9.sp,lineHeight=12.sp,fontWeight=FontWeight.Medium)
                Text(if(passes.isEmpty()) "Add boarding pass" else if(passes.size == 1) "Your boarding pass" else "${passes.size} boarding passes",color=Color(0xFF171321),fontFamily=Outfit,fontSize=11.sp,lineHeight=15.sp,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
            Icon(Icons.Outlined.ConfirmationNumber,null,tint=Color(0xFF673BB5),modifier=Modifier.size(20.dp))
        }

    }
    open?.let { category -> TravelCollection(state,vm,category,onDismiss={open=null}) }
}

@Composable
internal fun TravelShortcutRow(state: LauncherUiState, vm: LauncherViewModel) {
    var open by remember(state.homePulse,state.activeSpace) { mutableStateOf<TravelCategory?>(null) }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
        .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.White.copy(alpha=.13f),Color.White.copy(alpha=.04f))))
        .border(1.dp,Color.White.copy(alpha=.2f),RoundedCornerShape(24.dp)).padding(vertical=10.dp),horizontalArrangement=Arrangement.SpaceEvenly) {
        listOf(TravelCategory.Hotels,TravelCategory.Itinerary,TravelCategory.Tickets,TravelCategory.Apps).forEach { category ->
            Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable { open=category }.padding(vertical=4.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Icon(category.icon,null,tint=category.tint,modifier=Modifier.size(28.dp))
                Text(if(category==TravelCategory.Apps) "Trip apps" else category.title,color=Color.White,fontFamily=Outfit,fontSize=12.sp,maxLines=1)
            }
        }
    }
    open?.let { TravelCollection(state,vm,it,onDismiss={open=null}) }
}
