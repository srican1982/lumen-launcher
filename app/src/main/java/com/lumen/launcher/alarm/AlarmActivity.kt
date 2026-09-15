package com.lumen.launcher.alarm

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.launcher.ui.LumenMark
import com.lumen.launcher.ui.theme.Lumen
import com.lumen.launcher.ui.theme.LumenTheme
import com.lumen.launcher.ui.theme.Outfit

class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        val hour = intent.getIntExtra(AlarmIntents.EXTRA_HOUR, 0)
        val minute = intent.getIntExtra(AlarmIntents.EXTRA_MINUTE, 0)
        val label = intent.getStringExtra(AlarmIntents.EXTRA_LABEL).orEmpty()
        val tone = intent.getStringExtra(AlarmIntents.EXTRA_TONE) ?: AlarmTones.AURA
        AlarmTonePlayer.start(this, tone, loop = true)
        setContent {
            LumenTheme {
                AlarmRingScreen(
                    time = LumenAlarm(hour = hour, minute = minute).displayTime(),
                    label = label.ifBlank { "Good morning" },
                    onSnooze = {
                        sendBroadcast(Intent(this, AlarmReceiver::class.java).setAction(AlarmIntents.ACTION_SNOOZE).putExtras(intent))
                        finish()
                    },
                    onDismiss = {
                        sendBroadcast(Intent(this, AlarmReceiver::class.java).setAction(AlarmIntents.ACTION_DISMISS).putExtras(intent))
                        finish()
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) AlarmTonePlayer.stop()
    }
}

@Composable
private fun AlarmRingScreen(
    time: String,
    label: String,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Lumen.Night)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(28.dp)
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(96.dp),
                contentAlignment = Alignment.Center
            ) {
                LumenMark(size = 96.dp, glow = true)
            }
            Text(
                "ALARM",
                color = Lumen.Faint,
                fontFamily = Outfit,
                fontWeight = FontWeight.Medium,
                letterSpacing = 3.sp,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 22.dp)
            )
            Text(
                time,
                color = Lumen.Text,
                fontFamily = Outfit,
                fontWeight = FontWeight.Light,
                fontSize = 64.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                label,
                color = Lumen.Muted,
                fontFamily = Outfit,
                fontSize = 18.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Snooze 10 min",
                color = Lumen.Text,
                fontFamily = Outfit,
                fontSize = 16.sp,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable(onClick = onSnooze)
                    .padding(vertical = 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Text(
                "Dismiss",
                color = Lumen.OnAccent,
                fontFamily = Outfit,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Lumen.Accent)
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}
