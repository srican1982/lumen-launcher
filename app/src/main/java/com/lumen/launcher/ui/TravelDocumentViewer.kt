package com.lumen.launcher.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lumen.launcher.data.TravelAttachment
import com.lumen.launcher.vm.LauncherUiState
import com.lumen.launcher.vm.LauncherViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun TravelDocumentViewer(items: List<TravelAttachment>, state: LauncherUiState, vm: LauncherViewModel, onManage: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val pager = rememberPagerState(pageCount = { items.size })
    com.lumen.launcher.ui.focus.FocusPopupSheet(stationary=true,onDismiss=onDismiss,heightFraction=.86f,maxWidth=640.dp) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("${pager.currentPage+1} / ${items.size}",color=Color.White,modifier=Modifier.weight(1f))
            TextButton(onClick=onManage) { Text("Add / manage",color=Color.White) }
        }
        HorizontalPager(state=pager,modifier=Modifier.fillMaxWidth().height((androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp*.57f).dp),key={items[it].uri}) { index ->
            val item=items[index]
            Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally) {
                Text(item.title,color=Color.White,fontSize=14.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                val mime=remember(item.uri,item.mime) { item.mime.ifBlank { runCatching { context.contentResolver.getType(Uri.parse(item.uri)) }.getOrNull().orEmpty() } }
                when {
                    mime.startsWith("image/") -> {
                        var scale by remember(item.uri) { mutableFloatStateOf(1f) }
                        var offset by remember(item.uri) { mutableStateOf(Offset.Zero) }
                        val transform=rememberTransformableState { zoom,pan,_ ->
                            scale=(scale*zoom).coerceIn(1f,4f)
                            offset=if(scale==1f) Offset.Zero else offset+pan
                        }
                        Box(Modifier.fillMaxSize().transformable(transform,canPan={scale>1f}),contentAlignment=Alignment.Center) {
                            AsyncImage(item.uri,"Attached photo",modifier=Modifier.fillMaxSize().graphicsLayer { scaleX=scale;scaleY=scale;translationX=offset.x;translationY=offset.y },contentScale=ContentScale.Fit)
                        }
                    }
                    mime=="application/pdf" -> TravelPdf(item.uri)
                    mime=="text/uri-list" -> TravelWebPreview(item.uri)
                    else -> {
                        var error by remember { mutableStateOf(false) }
                        Spacer(Modifier.weight(1f))
                        Button(onClick={error=!openTravelItem(context,state,vm,item)}) { Text(if(mime=="application/x-lumen-app") "Open app" else "Open attachment") }
                        if(error) Text("Unable to open this attachment.",color=Color.White)
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        Text("Swipe between attachments · Scroll PDF pages",color=Color.LightGray,fontSize=12.sp)
        com.lumen.launcher.ui.focus.FocusPrimaryAction("Done",action=onDismiss,showCheck=false)
    }
}

@Composable
private fun TravelPdf(uri: String) {
    val context=LocalContext.current
    // The producer assigns value below; workaround for the bundled Compose lint detector.
    @Suppress("ProduceStateDoesNotAssignValue")
    val pages by produceState<Int?>(null,uri) {
        value=withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.openFileDescriptor(Uri.parse(uri),"r")!!.use { fd -> PdfRenderer(fd).use { it.pageCount } } }.getOrDefault(-1)
        }
    }
    when {
        pages==null -> CircularProgressIndicator()
        pages!!<0 -> Text("This PDF could not be read. Use Add / manage to replace it with a local copy.",color=Color.White)
        else -> LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            items(pages!!) { page ->
                var failed by remember(uri,page) { mutableStateOf(false) }
                // The producer assigns value below; workaround for the bundled Compose lint detector.
                @Suppress("ProduceStateDoesNotAssignValue")
                val bitmap by produceState<Bitmap?>(null,uri,page) {
                    value=withContext(Dispatchers.IO) {
                        runCatching {
                            context.contentResolver.openFileDescriptor(Uri.parse(uri),"r")!!.use { fd ->
                                PdfRenderer(fd).use { renderer -> renderer.openPage(page).use { pdf ->
                                    val width=1000
                                    val height=(width.toFloat()*pdf.height/pdf.width).toInt().coerceIn(1,2400)
                                    Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888).also { image -> image.eraseColor(android.graphics.Color.WHITE);pdf.render(image,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
                                } }
                            }
                        }.getOrNull()
                    }
                    failed=value==null
                }
                Column(Modifier.fillMaxWidth()) {
                    Text("Page ${page+1} of $pages",color=Color.LightGray,fontSize=11.sp)
                    bitmap?.let { Image(it.asImageBitmap(),"PDF page ${page+1}",modifier=Modifier.fillMaxWidth().aspectRatio(it.width.toFloat()/it.height),contentScale=ContentScale.Fit) }
                        ?: Box(Modifier.fillMaxWidth().height(180.dp),contentAlignment=Alignment.Center) { Text(if(failed) "Unable to read this page" else "Loading page…",color=Color.LightGray) }
                }
            }
        }
    }
}

@Composable
private fun TravelWebPreview(url: String) {
    val context=LocalContext.current
    var web by remember { mutableStateOf<android.webkit.WebView?>(null) }
    var failed by remember(url) { mutableStateOf(false) }
    var loading by remember(url) { mutableStateOf(true) }
    DisposableEffect(url) { onDispose { web?.stopLoading(); web?.destroy(); web=null } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text(android.net.Uri.parse(url).host.orEmpty(),color=Color.LightGray,fontSize=12.sp,modifier=Modifier.weight(1f))
            TextButton(onClick={runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,Uri.parse(url))) }}) { Text("Open browser",color=Color.White) }
        }
        if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(failed) Text("Preview unavailable. Open this link in your browser.",color=Color.White,fontSize=13.sp)
        androidx.compose.ui.viewinterop.AndroidView(modifier=Modifier.fillMaxWidth().weight(1f),factory={ ctx ->
            android.webkit.WebView(ctx).apply {
                web=this
                settings.javaScriptEnabled=true
                settings.domStorageEnabled=true
                settings.allowFileAccess=false
                settings.allowContentAccess=false
                settings.mixedContentMode=android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                webViewClient=object: android.webkit.WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: android.webkit.WebView, request: android.webkit.WebResourceRequest): Boolean = request.url.scheme !in listOf("https","http")
                    override fun onPageFinished(view: android.webkit.WebView,url: String) { loading=false }
                    override fun onReceivedError(view: android.webkit.WebView,request: android.webkit.WebResourceRequest,error: android.webkit.WebResourceError) { if(request.isForMainFrame) { failed=true;loading=false } }
                }
                if(Uri.parse(url).scheme in listOf("https","http")) loadUrl(url) else { failed=true;loading=false }
            }
        })
    }
}
