package com.lumen.launcher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.launcher.ui.theme.Lumen
import com.lumen.launcher.ui.theme.Outfit

/** Small glass pill inside a comfortable touch target. */
@Composable
internal fun CompactSearchButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.height(48.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Glass(modifier = Modifier.width(108.dp).height(34.dp), shape = RoundedCornerShape(20.dp)) {
            Row(Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center) {
                Icon(Icons.Outlined.Search, null, tint = Lumen.BrandGold, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Search", color = Lumen.Text,
                    style = TextStyle(fontFamily = Outfit, fontSize = 13.sp,
                        platformStyle = PlatformTextStyle(includeFontPadding = false)))
            }
        }
    }
}
