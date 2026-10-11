package com.lumen.launcher.ui.social

import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lumen.launcher.social.CreationKind
import com.lumen.launcher.social.SocialCreateCoordinator
import com.lumen.launcher.social.photo.PhotoCutout
import com.lumen.launcher.social.stickers.*
import com.lumen.launcher.ui.rememberNavBottomPadding
import com.lumen.launcher.ui.theme.Outfit
import kotlinx.coroutines.*

private val CutoutGold = Color(0xFFF5CB7E)

@Composable
internal fun PhotoCutoutDialog(source: Bitmap, coordinator: SocialCreateCoordinator, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cutout by remember(source) { mutableStateOf<Bitmap?>(null) }
    var status by remember { mutableStateOf("Preparing background removal...") }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var originalView by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var pack by remember { mutableStateOf<LumenStickerPack?>(null) }
    var targetChoice by remember { mutableStateOf(false) }
    val targets = remember { WhatsAppStickers.installedTargets(context) }
    fun message(text: String) { Toast.makeText(context, text, Toast.LENGTH_LONG).show() }
    val whatsapp = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        WhatsAppStickers.validationError(result.resultCode, result.data)?.let { message("WhatsApp couldn't add the pack: $it") }
    }
    LaunchedEffect(source, attempt) {
        error = null
        try {
            cutout = PhotoCutout.remove(context, source) { status = it }
        } catch (_: TimeoutCancellationException) {
            error = "The model download is taking longer than expected. Check your connection and retry."
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: "Couldn't remove the background. Try another photo."
        }
    }
    fun export(target: WhatsAppTarget?) {
        val result = cutout ?: return
        if (busy) return
        if (target == null && !TelegramStickers.isAvailable(context)) { message("Install or update Telegram to add stickers."); return }
        busy = true
        scope.launch {
            try {
                val saved = pack ?: withContext(Dispatchers.IO) {
                    when (val added = StickerLibrary.add(context, result, "\u2728", "A photo cutout", "Photo")) {
                        is AddStickerResult.Added -> added.pack
                        is AddStickerResult.Failed -> error(added.reason)
                    }
                }.also { pack = it }
                if (target != null) whatsapp.launch(WhatsAppStickers.addPackIntent(context, saved, target))
                else {
                    val intent = withContext(Dispatchers.IO) { TelegramStickers.importIntent(context, saved) }
                        ?: error("No stickers available to import.")
                    context.startActivity(intent)
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: ActivityNotFoundException) { message("Install or update ${target?.label ?: "Telegram"} to add stickers.")
            } catch (failure: Exception) { message(failure.message ?: "Couldn't export this sticker.")
            } finally { busy = false }
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val navBottom = rememberNavBottomPadding(56.dp)
        Column(
            Modifier.fillMaxSize().background(Color(0xFF151B24))
                .statusBarsPadding()
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = navBottom + 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Photo cutout", color = Color.White, fontFamily = Outfit, fontSize = 23.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Close", color = CutoutGold) }
            }
            Text("Your original stays unchanged.", color = Color.White.copy(alpha = .65f), fontFamily = Outfit, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = originalView, onClick = { originalView = true }, label = { Text("Original") })
                FilterChip(selected = !originalView, onClick = { originalView = false }, label = { Text("Cutout") }, enabled = cutout != null)
            }
            Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val square = 16.dp.toPx()
                    val cols = (size.width / square).toInt() + 1
                    val rows = (size.height / square).toInt() + 1
                    for (y in 0 until rows) for (x in 0 until cols) drawRect(
                        if ((x + y) % 2 == 0) Color(0xFF303740) else Color(0xFF252C34), Offset(x * square, y * square), Size(square, square))
                }
                val preview = if (originalView) source else cutout
                if (preview != null) Image(remember(preview) { preview.asImageBitmap() }, "Photo preview", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                else Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (error == null) CircularProgressIndicator(color = CutoutGold)
                    Text(error ?: status, color = Color.White, fontFamily = Outfit)
                    if (error != null) TextButton(onClick = { attempt++ }) { Text("Retry", color = CutoutGold) }
                }
            }
            if (originalView && cutout == null) {
                Text(error ?: status, color = Color.White, fontFamily = Outfit, fontSize = 12.sp)
                if (error != null) TextButton(onClick = { attempt++ }) { Text("Retry", color = CutoutGold) }
            }
            if (cutout != null) Text("Save and share the transparent cutout. Sticker options add it to your Lumen pack.", color = Color.White.copy(alpha = .65f), fontFamily = Outfit, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CutoutAction("Save", cutout != null && !busy, Modifier.weight(1f)) { cutout?.let { coordinator.saveImageToGallery(CreationKind.Photo, it) } }
                CutoutAction("Share", cutout != null && !busy, Modifier.weight(1f)) { cutout?.let { image ->
                    coordinator.saveBitmap(CreationKind.Photo, image) { coordinator.share(coordinator.repository.fileFor(it)) }
                } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CutoutAction("WhatsApp", cutout != null && !busy, Modifier.weight(1f)) {
                    when (targets.size) {
                        0 -> message("Install WhatsApp to add stickers.")
                        1 -> export(targets.first())
                        else -> targetChoice = true
                    }
                }
                CutoutAction("Telegram", cutout != null && !busy, Modifier.weight(1f)) { export(null) }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = CutoutGold)
        }
        if (targetChoice) AlertDialog(onDismissRequest = { targetChoice = false }, title = { Text("Add sticker pack") }, text = {
            Column { targets.forEach { target -> TextButton(onClick = { targetChoice = false; export(target) }) { Text(target.label) } } }
        }, confirmButton = {})
    }
}

@Composable
private fun CutoutAction(label: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier, shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = CutoutGold, contentColor = Color(0xFF211B13))) {
        Text(label, fontFamily = Outfit, maxLines = 1)
    }
}
