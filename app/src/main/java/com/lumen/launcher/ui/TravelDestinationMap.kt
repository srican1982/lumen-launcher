package com.lumen.launcher.ui

import android.content.Context
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.lumen.launcher.ui.theme.Lumen
import com.lumen.launcher.ui.theme.Outfit
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import kotlin.coroutines.resume

/** Only resolves a saved destination, never the phone's current location. */
private object TravelMapPlaces {
    private val lock = Mutex()
    private var lastRequest = 0L
    private fun valid(lat: Double, lon: Double) = lat.isFinite() && lon.isFinite() && lat in -85.0..85.0 && lon in -180.0..180.0

    suspend fun resolve(context: Context, query: String): Pair<Double, Double>? = lock.withLock {
        val name = query.trim().take(256)
        if (name.isBlank()) return@withLock null
        val cache = context.getSharedPreferences("travel_map_places", Context.MODE_PRIVATE)
        cache.getString(name, null)?.split(',')?.let { parts ->
            val lat = parts.getOrNull(0)?.toDoubleOrNull(); val lon = parts.getOrNull(1)?.toDoubleOrNull()
            if (lat != null && lon != null && valid(lat, lon)) return@withLock lat to lon
        }
        val local = if (!Geocoder.isPresent()) null else runCatching {
            val geocoder = Geocoder(context, Locale.getDefault())
            if (Build.VERSION.SDK_INT >= 33) withTimeoutOrNull(6_000) {
                suspendCancellableCoroutine<Pair<Double, Double>?> { continuation ->
                    geocoder.getFromLocationName(name, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<android.location.Address>) {
                            if (continuation.isActive) continuation.resume(addresses.firstOrNull()?.let { it.latitude to it.longitude })
                        }
                        override fun onError(errorMessage: String?) {
                            if (continuation.isActive) continuation.resume(null)
                        }
                    })
                }
            } else withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                geocoder.getFromLocationName(name, 1)?.firstOrNull()?.let { it.latitude to it.longitude }
            }
        }.getOrNull()
        val result = local?.takeIf { valid(it.first, it.second) } ?: run {
            // One request per chosen destination, cached and serialized; never autocomplete.
            val wait = 1100L - (android.os.SystemClock.elapsedRealtime() - lastRequest)
            if (wait > 0) delay(wait)
            lastRequest = android.os.SystemClock.elapsedRealtime()
            withContext(Dispatchers.IO) {
                runCatching {
                    val connection = URL("https://nominatim.openstreetmap.org/search?format=jsonv2&limit=1&q=${Uri.encode(name)}")
                        .openConnection() as HttpURLConnection
                    try {
                        connection.connectTimeout = 8_000; connection.readTimeout = 8_000
                        connection.setRequestProperty("User-Agent", "LumenLauncher/1.0 (https://github.com/srican1982/lumen-launcher)")
                        connection.setRequestProperty("Accept", "application/json")
                        val item = connection.inputStream.bufferedReader().use { JSONArray(it.readText()).optJSONObject(0) }
                        item?.let { it.getDouble("lat") to it.getDouble("lon") }?.takeIf { valid(it.first, it.second) }
                    } finally { connection.disconnect() }
                }.getOrNull()
            }
        }
        result?.also { cache.edit().putString(name, "${it.first},${it.second}").apply() }
    }
}

@Composable
internal fun TravelDestinationMap(query: String, latitude: Double?, longitude: Double?, modifier: Modifier,
    onOpenMaps: () -> Unit, onAddPlace: () -> Unit) {
    val context = LocalContext.current
    var point by remember(query, latitude, longitude) { mutableStateOf<Pair<Double, Double>?>(null) }
    var resolving by remember(query, latitude, longitude) { mutableStateOf(true) }
    var pageLoading by remember(query, latitude, longitude) { mutableStateOf(true) }
    var failed by remember(query, latitude, longitude) { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(query, latitude, longitude, retry) {
        resolving = true; failed = false; pageLoading = true
        point = if (latitude != null && longitude != null && latitude.isFinite() && longitude.isFinite() &&
            latitude in -85.0..85.0 && longitude in -180.0..180.0) latitude to longitude
            else TravelMapPlaces.resolve(context.applicationContext, query)
        resolving = false
    }
    val url = point?.let { (lat, lon) ->
        val south = (lat - .045).coerceAtLeast(-85.0); val north = (lat + .045).coerceAtMost(85.0)
        val west = (lon - .06).coerceAtLeast(-180.0); val east = (lon + .06).coerceAtMost(180.0)
        "https://www.openstreetmap.org/export/embed.html?bbox=${Uri.encode("$west,$south,$east,$north")}&layer=mapnik&marker=${Uri.encode("$lat,$lon") }"
    }
    val shape = RoundedCornerShape(22.dp)
    Box(modifier.clip(shape).background(Color(0xFF273B42)).border(.8.dp, Color.White.copy(.4f), shape)) {
        if (url != null && !failed) key(url, retry) {
            AndroidView(factory = { ctx -> WebView(ctx).apply {
                setBackgroundColor(android.graphics.Color.rgb(39,59,66))
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false; settings.allowContentAccess = false
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.userAgentString = settings.userAgentString + " LumenLauncher/1.0"
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, loaded: String?) { pageLoading = false }
                    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                        if (request?.isForMainFrame == true) { failed = true; pageLoading = false }
                    }
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val target = request?.url ?: return true
                        if (target.scheme == "https" && target.host == "www.openstreetmap.org" && target.path == "/export/embed.html") return false
                        if (target.scheme == "https") runCatching {
                            ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, target))
                        }
                        return true
                    }
                }
                loadUrl(url)
            } }, modifier = Modifier.fillMaxSize(), onRelease = { it.stopLoading(); it.destroy() })
            Icon(Icons.Outlined.OpenInFull, "Open destination in Maps", tint = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(36.dp).clip(CircleShape)
                    .background(Color(0xCC142936)).clickable(onClick = onOpenMaps).padding(8.dp))
        }
        if (resolving || (point != null && pageLoading && !failed)) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).size(24.dp), color = Lumen.BrandGold, strokeWidth = 2.dp)
        } else if (point == null || failed) {
            Column(Modifier.align(Alignment.Center).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (query.isBlank()) "Add a destination" else "Map unavailable", color = Color.White, fontFamily = Outfit, fontSize = 12.sp)
                Text(if (query.isBlank()) "Choose a place to see its map" else "Check your connection or try a more specific address.",
                    color = Lumen.Muted, fontFamily = Outfit, fontSize = 10.sp, lineHeight = 13.sp)
                Text(if (query.isBlank()) "Add place" else "Retry", color = Lumen.BrandGold, fontFamily = Outfit,
                    modifier = Modifier.clickable { if (query.isBlank()) onAddPlace() else retry++ }.padding(6.dp))
                if (query.isNotBlank()) Text("Open Maps", color = Color.White, fontFamily = Outfit, fontSize = 11.sp,
                    modifier = Modifier.clickable(onClick = onOpenMaps).padding(6.dp))
            }
        }
    }
}
