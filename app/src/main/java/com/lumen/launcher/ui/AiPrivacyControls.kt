package com.lumen.launcher.ui

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lumen.launcher.vm.LauncherUiState
import com.lumen.launcher.vm.LauncherViewModel
import com.lumen.launcher.ui.theme.Lumen
import com.lumen.launcher.data.AiModels
import androidx.compose.ui.platform.LocalContext

@Composable
fun AiPrivacyControls(state: LauncherUiState, viewModel: LauncherViewModel) {
    var consent by remember { mutableStateOf<String?>(null) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("AI inbox summaries", color = Lumen.Text)
        Switch(state.aiInboxSummary, { if (it) consent = "inbox" else viewModel.setAiInboxSummary(false) })
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Send contact names to AI", color = Lumen.Text)
        Switch(state.aiContactNames, { if (it) consent = "contacts" else viewModel.setAiContactNames(false) })
    }
    Text("Both options are off by default. AI voice sends your request, relevant app names, folder names, alarm times and recent conversation to your selected provider. Notifications stay local unless you enable summaries.", color = Lumen.Muted)
    if (consent != null) AlertDialog(
        onDismissRequest = { consent = null },
        title = { Text("Allow this AI data sharing?") },
        text = { Text(if (consent == "inbox")
            "Notification app names, titles and short previews will be sent to Gemini or OpenRouter using your key, only while Flow is visible. Hidden/private apps, secret notifications and numeric verification codes are excluded. You can turn this off anytime."
            else "Up to 40 contact names will be sent with AI voice requests to Gemini or OpenRouter. Phone numbers are not sent in this context. You can turn this off anytime.") },
        confirmButton = { TextButton(onClick = {
            if (consent == "inbox") viewModel.setAiInboxSummary(true) else viewModel.setAiContactNames(true)
            consent = null
        }) { Text("Allow") } },
        dismissButton = { TextButton(onClick = { consent = null }) { Text("Cancel") } }
    )
    val context = LocalContext.current
    var gemini by remember { mutableStateOf(AiModels.gemini) }
    var router by remember { mutableStateOf(AiModels.openRouter) }
    var error by remember { mutableStateOf(false) }
    OutlinedTextField(gemini, { gemini = it; error = false }, label = { Text("Gemini model") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(router, { router = it; error = false }, label = { Text("OpenRouter model") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    TextButton(onClick = { error = runCatching { AiModels.save(context, gemini.trim(), router.trim()) }.isFailure }) { Text("Save models") }
    if (error) Text("Use a valid provider model ID.", color = Lumen.Text)
    TextButton(onClick = { context.startActivity(android.content.Intent(context, com.lumen.launcher.PrivacyActivity::class.java)) }) { Text("Privacy and data sharing") }
    Spacer(Modifier.height(8.dp))
}
