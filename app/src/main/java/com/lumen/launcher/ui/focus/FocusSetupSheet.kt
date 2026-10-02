package com.lumen.launcher.ui.focus

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.launcher.focus.FocusPeopleGroup
import com.lumen.launcher.focus.FocusPerson
import com.lumen.launcher.focus.FocusPolicySettings
import com.lumen.launcher.ui.theme.Outfit

private val FocusGreen = Color(0xFF34D399)
private val FocusBlue = Color(0xFF60A5FA)

@Composable
fun FocusSetupSheet(
    people: List<FocusPerson>,
    settings: FocusPolicySettings,
    groups: List<FocusPeopleGroup>,
    capabilityNote: String,
    onDuration: (Int) -> Unit,
    onOpenPeople: () -> Unit,
    onSettingsChange: (FocusPolicySettings) -> Unit,
    onStart: (Int) -> Unit,
    onCustomDuration: () -> Unit
) {
    var duration by remember(settings.lastDurationMinutes) {
        mutableIntStateOf(settings.lastDurationMinutes.coerceIn(15, 120).let {
            when {
                it <= 15 -> 15
                it <= 30 -> 30
                it <= 60 -> 60
                else -> 120
            }
        })
    }
    val shape = RoundedCornerShape(26.dp)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xD9182030), Color(0xD9121824))))
            .border(1.dp, Color.White.copy(0.12f), shape)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "Set Focus",
            color = Color.White,
            fontFamily = Outfit,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp
        )
        Text(
            "Quiet, without becoming unreachable.",
            color = Color.White.copy(0.65f),
            fontFamily = Outfit,
            fontSize = 13.sp
        )

        Text("Duration", color = Color.White.copy(0.55f), fontFamily = Outfit, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(15, 30, 60, 120).forEach { mins ->
                val selected = duration == mins
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) FocusGreen.copy(0.22f) else Color.White.copy(0.07f))
                        .border(
                            1.dp,
                            if (selected) FocusGreen.copy(0.55f) else Color.White.copy(0.12f),
                            RoundedCornerShape(14.dp)
                        )
                        .clickable {
                            duration = mins
                            onDuration(mins)
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${mins}m",
                        color = Color.White,
                        fontFamily = Outfit,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
        }
        Text(
            "Custom…",
            color = FocusBlue,
            fontFamily = Outfit,
            fontSize = 13.sp,
            modifier = Modifier.clickable(onClick = onCustomDuration)
        )

        Text("Allow these people", color = Color.White.copy(0.55f), fontFamily = Outfit, fontSize = 12.sp)
        GroupChip(
            icon = Icons.Outlined.FavoriteBorder,
            title = "Family",
            detail = groupDetail(groups, FocusPeopleGroup.Family.id, people),
            accent = Color(0xFFFBBF24)
        )
        GroupChip(
            icon = Icons.Outlined.WorkOutline,
            title = "Work VIPs",
            detail = groupDetail(groups, FocusPeopleGroup.WorkVips.id, people),
            accent = FocusBlue
        )
        GroupChip(
            icon = Icons.Outlined.LocalHospital,
            title = "Emergency only",
            detail = groupDetail(groups, FocusPeopleGroup.Emergency.id, people),
            accent = Color(0xFFFB7185)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(0.06f))
                .clickable(onClick = onOpenPeople)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Add, null, tint = FocusGreen, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                if (people.isEmpty()) "+ Customize list"
                else "${people.size} people selected · Edit",
                color = Color.White,
                fontFamily = Outfit,
                fontSize = 14.sp
            )
        }

        Text("Exceptions", color = Color.White.copy(0.55f), fontFamily = Outfit, fontSize = 12.sp)
        PolicyToggle("Allow calls from selected people", settings.allowCallsFromSelected) {
            onSettingsChange(settings.copy(allowCallsFromSelected = it))
        }
        PolicyToggle("Allow messages from selected people", settings.allowMessagesFromSelected) {
            onSettingsChange(settings.copy(allowMessagesFromSelected = it))
        }
        PolicyToggle("Allow repeated caller (within 3 minutes)", settings.allowRepeatedCallers) {
            onSettingsChange(settings.copy(allowRepeatedCallers = it))
        }
        PolicyToggle("Allow alarms", settings.allowAlarms) {
            onSettingsChange(settings.copy(allowAlarms = it))
        }
        PolicyToggle("Allow calendar reminders", settings.allowCalendarReminders) {
            onSettingsChange(settings.copy(allowCalendarReminders = it))
        }
        PolicyToggle("Silence everyone else", settings.silenceEveryoneElse) {
            onSettingsChange(settings.copy(silenceEveryoneElse = it))
        }

        Text(capabilityNote, color = Color.White.copy(0.4f), fontFamily = Outfit, fontSize = 11.sp)

        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.horizontalGradient(listOf(FocusGreenDeep, FocusGreen)))
                .clickable { onStart(duration) }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Start Focus",
                color = Color.White,
                fontFamily = Outfit,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
        }
    }
}

private val FocusGreenDeep = Color(0xFF059669)

@Composable
private fun GroupChip(icon: ImageVector, title: String, detail: String, accent: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(0.05f))
            .border(1.dp, accent.copy(0.25f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontFamily = Outfit, fontSize = 14.sp)
            Text(detail, color = Color.White.copy(0.45f), fontFamily = Outfit, fontSize = 11.sp)
        }
    }
}

@Composable
private fun PolicyToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = Color.White.copy(0.85f),
            fontFamily = Outfit,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = FocusGreen,
                uncheckedThumbColor = Color.White.copy(0.8f),
                uncheckedTrackColor = Color.White.copy(0.2f)
            )
        )
    }
}

private fun groupDetail(groups: List<FocusPeopleGroup>, id: String, people: List<FocusPerson>): String {
    val count = groups.find { it.id == id }?.personIds?.size ?: 0
    return if (count > 0) "$count people" else "Assign people later"
}
