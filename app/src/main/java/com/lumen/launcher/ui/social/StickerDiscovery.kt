package com.lumen.launcher.ui.social

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.launcher.social.stickers.LumenStickerPack
import com.lumen.launcher.social.stickers.WhatsAppStickers

private val StickerGold = Color(0xFFF5CB7E)

@Composable
internal fun StickerDiscoveryTip(onDismiss: () -> Unit) {
    Row(
        Modifier.padding(horizontal=16.dp,vertical=4.dp).fillMaxWidth()
            .background(StickerGold.copy(alpha=.08f),RoundedCornerShape(16.dp))
            .border(1.dp,StickerGold.copy(alpha=.3f),RoundedCornerShape(16.dp))
            .padding(start=12.dp),
        verticalAlignment=Alignment.CenterVertically
    ) {
        Text("Turn your creation into a sticker for WhatsApp.",color=StickerGold,fontSize=12.sp,modifier=Modifier.weight(1f))
        IconButton(onClick=onDismiss) { Icon(Icons.Outlined.Close,"Dismiss sticker tip",tint=StickerGold,modifier=Modifier.size(18.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun rememberStickerSavedPrompt(): (LumenStickerPack) -> Unit {
    val context = LocalContext.current
    var saved by remember { mutableStateOf<LumenStickerPack?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val error = WhatsAppStickers.validationError(result.resultCode,result.data)
        if(error != null) Toast.makeText(context,"WhatsApp couldn't add the pack: $error",Toast.LENGTH_LONG).show()
        else if(result.resultCode == android.app.Activity.RESULT_OK) {
            saved=null
            Toast.makeText(context,"Open a WhatsApp chat and find it in Stickers.",Toast.LENGTH_LONG).show()
        }
    }
    saved?.let { pack ->
        val targets = WhatsAppStickers.installedTargets(context)
        com.lumen.launcher.ui.focus.FocusPopupSheet(
            onDismiss={saved=null},stationary=true,whiteGlass=true,heightFraction=.8f,maxWidth=520.dp
        ) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=4.dp).padding(bottom=8.dp),
                horizontalAlignment=Alignment.CenterHorizontally,
                verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("Your sticker",fontSize=22.sp,color=StickerGold)
                Box(Modifier.fillMaxWidth().background(Color.White.copy(alpha=.07f),RoundedCornerShape(20.dp)).padding(16.dp)) {
                    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Text("How was your day?",color=Color.White,fontSize=14.sp,
                            modifier=Modifier.align(Alignment.Start)
                                .background(Color.White.copy(alpha=.12f),RoundedCornerShape(topStart=4.dp,topEnd=16.dp,bottomEnd=16.dp,bottomStart=16.dp))
                                .padding(horizontal=14.dp,vertical=10.dp))
                        coil.compose.AsyncImage(
                            model=java.io.File(com.lumen.launcher.social.stickers.StickerLibrary.packDir(context,pack.identifier),pack.stickers.last().fileName),
                            contentDescription="Your creation as a sticker",
                            modifier=Modifier.size(144.dp).align(Alignment.End),
                            contentScale=androidx.compose.ui.layout.ContentScale.Fit
                        )
                    }
                }
                Text("Saved to Lumen Stickers. Add it to an app, then send it from the sticker tray in a chat.",color=Color.White.copy(alpha=.8f),fontSize=14.sp)
                targets.forEach { target ->
                    Button(onClick={
                        try { launcher.launch(WhatsAppStickers.addPackIntent(context,pack,target)) }
                        catch (_: android.content.ActivityNotFoundException) {
                            Toast.makeText(context,"Couldn't open ${target.label}. Your sticker is saved.",Toast.LENGTH_LONG).show()
                        }
                    },modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF25D366),contentColor=Color.White)) {
                        Icon(androidx.compose.ui.res.painterResource(com.lumen.launcher.R.drawable.ic_whatsapp_sticker),null,modifier=Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add to ${target.label}")
                    }
                }
                if(targets.isEmpty()) Text("WhatsApp isn't installed. Your sticker is saved for later.",fontSize=12.sp)
                val telegramAvailable = com.lumen.launcher.social.stickers.TelegramStickers.isAvailable(context)
                Button(onClick={
                    try {
                        com.lumen.launcher.social.stickers.TelegramStickers.importIntent(context,pack)?.let { context.startActivity(it) }
                    } catch (_: android.content.ActivityNotFoundException) {
                        Toast.makeText(context,"Couldn't open Telegram. Your sticker is saved.",Toast.LENGTH_LONG).show()
                    }
                },enabled=telegramAvailable,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF229ED9),contentColor=Color.White)) {
                    Icon(androidx.compose.ui.res.painterResource(com.lumen.launcher.R.drawable.ic_telegram_sticker),null,modifier=Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if(telegramAvailable) "Add to Telegram" else "Telegram not installed")
                }
                TextButton(onClick={saved=null}) { Text("Done",color=StickerGold) }
            }
        }
    }
    return { saved=it }
}
