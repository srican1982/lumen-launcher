package com.lumen.launcher.ui.social

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.rememberAsyncImagePainter
import com.lumen.launcher.social.stickers.LumenSticker
import com.lumen.launcher.social.stickers.LumenStickerPack
import com.lumen.launcher.social.stickers.StickerLibrary
import com.lumen.launcher.social.stickers.TelegramStickers
import com.lumen.launcher.social.stickers.WhatsAppStickers
import com.lumen.launcher.social.stickers.WhatsAppTarget
import com.lumen.launcher.ui.theme.Outfit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * "Lumen Stickers": every pack Lumen offers to WhatsApp, its stickers, and an
 * Add to WhatsApp / WhatsApp Business button per pack (WhatsApp does not allow "add all").
 */
@Composable
fun StickerPackScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val packs by StickerLibrary.packs.collectAsState()
    var refreshTick by remember { mutableIntStateOf(0) }
    var pendingRemove by remember { mutableStateOf<Pair<LumenStickerPack, LumenSticker>?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { runCatching { StickerLibrary.ensureReady(context) } }
    }

    val targets = remember { WhatsAppStickers.installedTargets(context) }
    val telegram = remember { TelegramStickers.isAvailable(context) }

    fun sendToTelegram(pack: LumenStickerPack) {
        val intent = TelegramStickers.importIntent(context, pack)
        if (intent == null) {
            Toast.makeText(context, "No stickers to send yet", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Telegram isn't installed or is too old for sticker import", Toast.LENGTH_LONG).show()
        }
    }

    // packId -> (target -> added?) ; null means WhatsApp couldn't tell us.
    val added by produceState(emptyMap<String, Map<WhatsAppTarget, Boolean?>>(), packs, refreshTick) {
        value = withContext(Dispatchers.IO) {
            packs.associate { p ->
                p.identifier to targets.associateWith { WhatsAppStickers.isPackAdded(context, it, p.identifier) }
            }
        }
    }

    // Must be startActivityForResult so WhatsApp can verify which app is adding the pack.
    val addLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        WhatsAppStickers.validationError(result.resultCode, result.data)?.let {
            Toast.makeText(context, "WhatsApp couldn't add the pack: $it", Toast.LENGTH_LONG).show()
        }
        refreshTick++
    }

    fun addTo(pack: LumenStickerPack, target: WhatsAppTarget) {
        try {
            addLauncher.launch(WhatsAppStickers.addPackIntent(context, pack, target))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "${target.label} isn't installed or is too old for sticker packs", Toast.LENGTH_LONG).show()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .zIndex(10f)
            .drawBehind { createScreenBackground() }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0x1AFFFFFF))
                        .border(1.dp, Color(0x2EFFFFFF), CircleShape)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Close, "Close", tint = Color.White, modifier = Modifier.size(24.dp))
                }
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text("Lumen Stickers", color = Color.White, fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 24.sp)
                    Text("Real stickers for WhatsApp & Telegram.", color = CreatePalette.Subtitle, fontFamily = Outfit, fontSize = 14.sp)
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    "Stickers you add from Scribble, Quote or Photo land here. Add a pack to WhatsApp once; " +
                        "new stickers are added to the same pack (WhatsApp may take a moment to show them).",
                    color = CreatePalette.Label,
                    fontFamily = Outfit,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )
                if (targets.isEmpty()) {
                    Text(
                        "WhatsApp isn't installed on this phone.",
                        color = Color(0xFFFF8A80),
                        fontFamily = Outfit,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                if (packs.isEmpty()) {
                    Text("Preparing your first pack…", color = Color.White.copy(alpha = 0.6f), fontFamily = Outfit, fontSize = 14.sp)
                }
                packs.forEach { pack ->
                    PackCard(
                        pack = pack,
                        targets = targets,
                        addedState = added[pack.identifier].orEmpty(),
                        onAdd = { target -> addTo(pack, target) },
                        telegram = telegram,
                        onTelegram = { sendToTelegram(pack) },
                        onStickerTap = { sticker -> pendingRemove = pack to sticker }
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
        }

        pendingRemove?.let { (pack, sticker) ->
            RemoveStickerDialog(
                pack = pack,
                sticker = sticker,
                onCancel = { pendingRemove = null },
                onRemove = {
                    pendingRemove = null
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            StickerLibrary.remove(context, pack.identifier, sticker.fileName)
                        }
                        Toast.makeText(
                            context,
                            if (ok) "Sticker removed" else "A pack needs at least ${StickerLibrary.MIN_STICKERS} stickers",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
        }
    }
}

@Composable
private fun PackCard(
    pack: LumenStickerPack,
    targets: List<WhatsAppTarget>,
    addedState: Map<WhatsAppTarget, Boolean?>,
    onAdd: (WhatsAppTarget) -> Unit,
    telegram: Boolean,
    onTelegram: () -> Unit,
    onStickerTap: (LumenSticker) -> Unit
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0x14FFFFFF))
            .border(1.dp, Color(0x559D63EE), shape)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(pack.name, color = Color.White, fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 18.sp, modifier = Modifier.weight(1f))
            Text(
                "${pack.stickers.size}/${StickerLibrary.MAX_STICKERS}",
                color = CreatePalette.Label,
                fontFamily = Outfit,
                fontSize = 13.sp
            )
        }
        if (pack.stickers.size < StickerLibrary.MIN_STICKERS) {
            Text(
                "${pack.stickers.size} of ${StickerLibrary.MIN_STICKERS} needed to add to WhatsApp",
                color = CreatePalette.Label,
                fontFamily = Outfit,
                fontSize = 12.sp
            )
        }

        // Sticker grid, 4 per row. Tap a sticker to remove it.
        val dir = StickerLibrary.packDir(context, pack.identifier)
        pack.stickers.chunked(4).forEach { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { sticker ->
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF1C1230))
                            .clickable { onStickerTap(sticker) }
                            .padding(6.dp)
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(File(dir, sticker.fileName)),
                            contentDescription = sticker.accessibilityText.ifBlank { "Sticker" },
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        // One button per installed WhatsApp app.
        targets.forEach { target ->
            val isAdded = addedState[target] == true
            val btnShape = RoundedCornerShape(26.dp)
            Row(
                Modifier
                    .padding(top = 14.dp)
                    .fillMaxWidth()
                    .height(52.dp)
                    .then(
                        if (isAdded) Modifier
                            .clip(btnShape)
                            .background(Color(0x14FFFFFF))
                            .border(1.dp, Color(0x40A36BF0), btnShape)
                        else Modifier
                            .shadow(14.dp, btnShape, spotColor = CreatePalette.Accent)
                            .clip(btnShape)
                            .background(CreatePalette.ShareFill)
                    )
                    .clickable(enabled = pack.isValid) { onAdd(target) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (isAdded) {
                    Icon(Icons.Outlined.Check, null, tint = Color(0xFF86EFAC), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    if (isAdded) "Added to ${target.label}" else "Add to ${target.label}",
                    color = if (isAdded) Color.White else CreatePalette.Ink,
                    fontFamily = Outfit,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp
                )
            }
        }
        if (telegram) {
            val tgShape = RoundedCornerShape(26.dp)
            Row(
                Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(tgShape)
                    .background(Color(0x14FFFFFF))
                    .border(1.dp, Color(0x6629A9EB), tgShape)
                    .clickable(enabled = pack.stickers.isNotEmpty(), onClick = onTelegram),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text("Send copy to Telegram", color = Color(0xFF7CC8F5), fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            }
            Text(
                "Telegram makes its own copy. Stickers you add later need another send (it creates a new Telegram pack).",
                color = CreatePalette.Label,
                fontFamily = Outfit,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun RemoveStickerDialog(
    pack: LumenStickerPack,
    sticker: LumenSticker,
    onCancel: () -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    val canRemove = pack.stickers.size > StickerLibrary.MIN_STICKERS
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onCancel),
        contentAlignment = Alignment.Center
    ) {
        val shape = RoundedCornerShape(28.dp)
        Column(
            Modifier
                .padding(32.dp)
                .clip(shape)
                .background(Color(0xFF1E1233))
                .border(1.dp, Color(0x559D63EE), shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = rememberAsyncImagePainter(File(StickerLibrary.packDir(context, pack.identifier), sticker.fileName)),
                contentDescription = null,
                modifier = Modifier.size(140.dp)
            )
            Text(
                if (canRemove) "Remove this sticker?" else "Can't remove",
                color = Color.White,
                fontFamily = Outfit,
                fontWeight = FontWeight.Medium,
                fontSize = 18.sp,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                if (canRemove) "It will also disappear from WhatsApp once WhatsApp refreshes the pack."
                else "WhatsApp needs at least ${StickerLibrary.MIN_STICKERS} stickers in a pack.",
                color = CreatePalette.Label,
                fontFamily = Outfit,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CreateChip("Cancel", false, onClick = onCancel)
                if (canRemove) CreateChip("Remove", true, onClick = onRemove)
            }
        }
    }
}
