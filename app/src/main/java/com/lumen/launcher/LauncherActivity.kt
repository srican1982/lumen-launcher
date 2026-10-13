package com.lumen.launcher

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.lumen.launcher.ui.LauncherRoot
import com.lumen.launcher.util.AssistantRole
import com.lumen.launcher.util.HomeRole
import com.lumen.launcher.vm.LauncherViewModel
import com.lumen.launcher.vm.Sheet
import com.lumen.launcher.voice.LumenVoiceSession
import com.lumen.launcher.voice.SpeechRecognizers
import com.lumen.launcher.voice.VoiceEngine
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class LauncherActivity : FragmentActivity() {

    private val viewModel: LauncherViewModel by viewModels()
    private val alarmViewModel: com.lumen.launcher.alarm.AlarmViewModel by viewModels()
    private val voiceViewModel: com.lumen.launcher.vm.VoiceViewModel by viewModels {
        object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                com.lumen.launcher.vm.VoiceViewModel(application, viewModel.homeActions, viewModel.taskActions, viewModel.alarmActions) as T
        }
    }
    private var authenticating = false
    private var pendingMicForVoice = false
    private var askedWakeMic = false
    private var promptInFlight = false
    private var lastSheet: Sheet? = null
    private lateinit var voiceEngine: VoiceEngine
    private val promptWatchdog = Runnable {
        if (!promptInFlight) return@Runnable
        promptInFlight = false
        if (viewModel.state.value.sheet == Sheet.Voice) voiceViewModel.onVoiceEngineStuck()
    }

    private val speechPrompt = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        window.decorView.removeCallbacks(promptWatchdog)
        promptInFlight = false
        if (viewModel.state.value.sheet != Sheet.Voice) return@registerForActivityResult
        val texts = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            .orEmpty()
        if (result.resultCode == Activity.RESULT_OK && texts.any { it.isNotBlank() }) {
            voiceViewModel.onVoiceResults(texts)
        } else {
            voiceViewModel.onVoiceNoSpeech()
        }
    }

    private val homeRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshApps()
    }

    private var pendingCallLogAccess = false

    private val assistantRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (pendingCallLogAccess) {
            pendingCallLogAccess = false
            if (AssistantRole.isHeld(this)) requestPhoneHistoryPermission()
        }
    }

    private val contactsPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onContactsPermission(granted)
    }

    private val photoSearchPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        viewModel.onPhotoSearchPermission(granted.values.any { it })
    }

    private val calendarPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onCalendarPermission(granted)
    }

    private val callLogPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onCallLogPermission(granted)
        if (!granted) android.widget.Toast.makeText(this,
            "Call history was not allowed. Enable Call logs in Lumen app permissions.", android.widget.Toast.LENGTH_LONG).show()
    }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.onFocusNotificationPermission(granted) }

    private val micPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (pendingMicForVoice) voiceViewModel.onMicPermission(granted)
        else if (!granted) viewModel.setHeyLumen(false)
    }

    private fun requestPhoneHistoryPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
            viewModel.onCallLogPermission(true)
        } else {
            val prefs = getSharedPreferences("permission_requests", MODE_PRIVATE)
            if (prefs.getBoolean("call_history", false) && !shouldShowRequestPermissionRationale(Manifest.permission.READ_CALL_LOG)) {
                startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:$packageName")))
            } else {
                prefs.edit().putBoolean("call_history", true).apply()
                callLogPermission.launch(Manifest.permission.READ_CALL_LOG)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        clearNavScrim()
        viewModel.bindVoice(voiceViewModel)
        viewModel.bindAlarms(alarmViewModel)
        voiceEngine = VoiceEngine(applicationContext, voiceCallbacks)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.events.collect { event -> handleEvent(event) }
            }
        }
        setContent {
            val state by viewModel.chromeState.collectAsStateWithLifecycle()
            // Always handle Back on the home screen. If nothing is open, do nothing: letting
            // Android finish the launcher made it restart and replay the icon animation.
            BackHandler(enabled = true) {
                when {
                    state.activeApp != null -> viewModel.dismissAppActions()
                    state.sheet != Sheet.None -> viewModel.closeSheet()
                    state.privatePageActive -> viewModel.closePrivatePage()
                    else -> Unit
                }
            }
            LauncherRoot(
                viewModel = viewModel,
                state = state,
                onRequestDefaultHome = ::promptDefaultHome
            )
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                try {
                    viewModel.state.collect { state ->
                        if (state.sheet == Sheet.Voice && lastSheet != Sheet.Voice) {
                            promptInFlight = false
                        }
                        lastSheet = state.sheet
                        val mic = ContextCompat.checkSelfPermission(
                            this@LauncherActivity,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (state.heyLumen && !mic && !askedWakeMic) {
                            askedWakeMic = true
                            requestMic(forVoice = false)
                        }
                        val want = state.heyLumen &&
                            mic &&
                            state.sheet == Sheet.None &&
                            !state.privatePageActive &&
                            !getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked
                        if (::voiceEngine.isInitialized) {
                            voiceEngine.updateContext(state.voiceLanguage, voiceViewModel.speechHints())
                            voiceEngine.setWakeWanted(want)
                        }
                    }
                } finally {
                    if (::voiceEngine.isInitialized) voiceEngine.setWakeWanted(false)
                }
            }
        }
        handleVoiceIntent(intent)
        handleWidgetIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.onCallLogPermission(ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED)
        voiceViewModel.onVoiceHomeResume()
        viewModel.resumeFocusSetup()
        clearNavScrim()
        viewModel.refreshApps()
        viewModel.refreshFlow()
        lifecycleScope.launch {
            val trips = com.lumen.launcher.travel.data.TripRepository.get(applicationContext)
            runCatching {
                trips.restoreIfNeeded()
                trips.syncNewPhotos()
                // A camera can return before MediaStore finishes publishing its image.
                kotlinx.coroutines.delay(2_000)
                trips.syncNewPhotos()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked && ::voiceEngine.isInitialized) {
            voiceEngine.stopCommand()
            viewModel.closeSheet()
        }
        if (!authenticating) viewModel.lockPrivate()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!com.lumen.launcher.widgets.WidgetActions.isWidgetIntent(intent)) viewModel.onHomeVisible()
        handleVoiceIntent(intent)
        handleWidgetIntent(intent)
    }

    override fun onDestroy() {
        stopVoice()
        if (::voiceEngine.isInitialized) voiceEngine.release()
        super.onDestroy()
    }

    private fun handleEvent(event: com.lumen.launcher.vm.LauncherEvent) {
        val locked = getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked
        if (locked) {
            voiceEngine.stopCommand()
            if (event is com.lumen.launcher.vm.LauncherEvent.Authenticate) event.failure()
            return
        }
        when (event) {
            is com.lumen.launcher.vm.LauncherEvent.Authenticate -> authenticate(event.success, event.failure)
            is com.lumen.launcher.vm.LauncherEvent.Open -> runCatching { startActivity(event.intent) }
            is com.lumen.launcher.vm.LauncherEvent.Conversation -> {
                if (!com.lumen.launcher.inbox.InboxHub.openConversation(this, event.item))
                    android.widget.Toast.makeText(this, "This conversation link has expired. Open a newer notification.", android.widget.Toast.LENGTH_LONG).show()
            }
            is com.lumen.launcher.vm.LauncherEvent.Missed -> {
                val pending = com.lumen.launcher.inbox.WhatsAppMissedHub.contentIntent(event.call.key)
                if (pending == null || (Build.VERSION.SDK_INT >= 31 && !pending.isActivity) || !com.lumen.launcher.inbox.InboxHub.sendFromTap(this, pending)) event.fallback()
            }
            is com.lumen.launcher.vm.LauncherEvent.Speak -> voiceEngine.speak(event.text, event.listen)
            com.lumen.launcher.vm.LauncherEvent.Listen -> startListen()
            com.lumen.launcher.vm.LauncherEvent.StopVoice -> stopVoice()
            is com.lumen.launcher.vm.LauncherEvent.Confirm -> android.app.AlertDialog.Builder(this)
                .setTitle(event.title).setMessage(event.message).setNegativeButton("Cancel", null)
                .setPositiveButton("Open settings") { _, _ -> event.action() }.show()
            is com.lumen.launcher.vm.LauncherEvent.Permission -> when (event) {
                com.lumen.launcher.vm.LauncherEvent.Permission.Contacts -> contactsPermission.launch(Manifest.permission.READ_CONTACTS)
                com.lumen.launcher.vm.LauncherEvent.Permission.MediaImages -> photoSearchPermission.launch(
                    when {
                        Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
                        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                )
                com.lumen.launcher.vm.LauncherEvent.Permission.Calendar -> {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED)
                        viewModel.onCalendarPermission(true)
                    else calendarPermission.launch(Manifest.permission.READ_CALENDAR)
                }
                com.lumen.launcher.vm.LauncherEvent.Permission.CallLog -> requestPhoneHistoryPermission()
                com.lumen.launcher.vm.LauncherEvent.Permission.Notifications -> {
                    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                com.lumen.launcher.vm.LauncherEvent.Permission.Mic -> requestMic(true)
                com.lumen.launcher.vm.LauncherEvent.Permission.MicQuiet -> requestMic(false)
                com.lumen.launcher.vm.LauncherEvent.Permission.Assistant -> AssistantRole.request(this, assistantRoleLauncher)
            }
        }
    }

    private fun requestMic(forVoice: Boolean) {
        pendingMicForVoice = forVoice
        val ok = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (ok) {
            if (forVoice) voiceViewModel.onMicPermission(true)
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun handleVoiceIntent(intent: Intent?) {
        if (intent == null || !LumenVoiceSession.isTrusted(intent)) return
        val keyguard = getSystemService(android.app.KeyguardManager::class.java)
        if (keyguard.isKeyguardLocked) {
            keyguard.requestDismissKeyguard(this, object : android.app.KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    if (Build.VERSION.SDK_INT >= 27) setShowWhenLocked(false)
                    voiceViewModel.openVoice(fromWake = true)
                }
                override fun onDismissCancelled() { if (Build.VERSION.SDK_INT >= 27) setShowWhenLocked(false) }
                override fun onDismissError() { if (Build.VERSION.SDK_INT >= 27) setShowWhenLocked(false) }
            })
        } else voiceViewModel.openVoice(fromWake = true)
    }

    private fun handleWidgetIntent(intent: Intent?) {
        val action = com.lumen.launcher.widgets.WidgetActions.read(this, intent) ?: return
        val keyguard = getSystemService(android.app.KeyguardManager::class.java)
        fun open() {
            lifecycleScope.launch {
                val ready = kotlinx.coroutines.withTimeoutOrNull(5_000) {
                    viewModel.state.first { it.homePreferencesReady }
                }
                if (ready != null && !keyguard.isKeyguardLocked) viewModel.openWidgetDestination(action.first, action.second)
            }
        }
        if (keyguard.isKeyguardLocked) keyguard.requestDismissKeyguard(this, object : android.app.KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() { open() }
        }) else open()
    }

    private fun startListen() {
        if (voiceViewModel.voiceTyping) return
        if (getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked) return
        if (promptInFlight) return
        if (!voiceEngine.hasRecognizer()) {
            listenViaPrompt()
            return
        }
        voiceEngine.listenCommand()
    }

    private fun listenViaPrompt() {
        if (voiceViewModel.voiceTyping) return
        if (getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked) return
        if (promptInFlight) return
        val intent = SpeechRecognizers.promptIntent(this, viewModel.state.value.voiceLanguage, voiceViewModel.speechHints())
        if (intent == null) {
            voiceViewModel.onVoiceEngineStuck()
            return
        }
        promptInFlight = true
        window.decorView.removeCallbacks(promptWatchdog)
        window.decorView.postDelayed(promptWatchdog, 45_000L)
        voiceViewModel.onVoiceListening()
        runCatching { speechPrompt.launch(intent) }.onFailure {
            promptInFlight = false
            voiceViewModel.onVoiceEngineStuck()
        }
    }

    private fun stopVoice() {
        promptInFlight = false
        window.decorView.removeCallbacks(promptWatchdog)
        if (::voiceEngine.isInitialized) voiceEngine.stopCommand()
    }

    private val voiceCallbacks = object : VoiceEngine.Callbacks {
        override fun onStarting() = voiceViewModel.onVoiceStarting()
        override fun onListening() = voiceViewModel.onVoiceListening()
        override fun onPartial(text: String) = voiceViewModel.onVoicePartial(text)
        override fun onResults(texts: List<String>) = voiceViewModel.onVoiceResults(texts)
        override fun onWake(remainder: String) {
            if (!getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked) voiceViewModel.onWakeWord(remainder)
        }
        override fun onLevel(level: Float) = voiceViewModel.onVoiceLevel(level)
        override fun onHardError(message: String) = voiceViewModel.onVoiceHardError(message)
        override fun onSoftMiss() = voiceViewModel.onVoiceNoSpeech()
        override fun onSpeakFinished(listenAfter: Boolean) {
            if (listenAfter) {
                window.decorView.postDelayed({
                    if (viewModel.state.value.sheet == Sheet.Voice) startListen()
                }, 280L)
            } else {
                voiceViewModel.onSpeakFinished()
            }
        }
        override fun onNeedPrompt() = listenViaPrompt()
    }

    private fun authenticate(onSuccess: () -> Unit, onFail: () -> Unit) {
        authenticating = true
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    authenticating = false
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    authenticating = false
                    onFail()
                    if (errorCode == BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL ||
                        errorCode == BiometricPrompt.ERROR_NO_BIOMETRICS ||
                        errorCode == BiometricPrompt.ERROR_HW_NOT_PRESENT
                    ) {
                        openLockScreenSettings()
                    }
                }

                override fun onAuthenticationFailed() = Unit
            }
        )
        val info = runCatching { promptInfo() }.getOrNull()
        if (info == null) {
            authenticating = false
            onFail()
            openLockScreenSettings()
            return
        }
        runCatching { prompt.authenticate(info) }.onFailure {
            authenticating = false
            onFail()
            openLockScreenSettings()
        }
    }

    private fun promptInfo(): BiometricPrompt.PromptInfo {
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Locked Space")
            .setSubtitle("Confirm it’s you to open apps locked inside Lumen")
        if (Build.VERSION.SDK_INT >= 30) {
            builder.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setDeviceCredentialAllowed(true)
        }
        return builder.build()
    }

    private fun openLockScreenSettings() {
        val intent = Intent(Settings.ACTION_SECURITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }
    }

    private fun clearNavScrim() {
        if (Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
        }
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        }
    }

    private fun promptDefaultHome() {
        viewModel.closeSheet()
        HomeRole.request(this, homeRoleLauncher)
    }
}
