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

class LauncherActivity : FragmentActivity() {

    private val viewModel: LauncherViewModel by viewModels()
    private var authenticating = false
    private var pendingMicForVoice = false
    private var overLockscreen = false
    private var askedWakeMic = false
    private var promptInFlight = false
    private var lastSheet: Sheet? = null
    private lateinit var voiceEngine: VoiceEngine
    private val promptWatchdog = Runnable {
        if (!promptInFlight) return@Runnable
        promptInFlight = false
        if (viewModel.state.value.sheet == Sheet.Voice) viewModel.onVoiceEngineStuck()
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
            viewModel.onVoiceResults(texts)
        } else {
            viewModel.onVoiceNoSpeech()
        }
    }

    private val homeRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshApps()
    }

    private val assistantRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    private val contactsPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onContactsPermission(granted)
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
    }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private val micPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (pendingMicForVoice) viewModel.onMicPermission(granted)
        else if (!granted) viewModel.setHeyLumen(false)
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
        voiceEngine = VoiceEngine(applicationContext, voiceCallbacks)
        viewModel.onAuthenticate = { onSuccess, onFail -> authenticate(onSuccess, onFail) }
        viewModel.onRequestContacts = {
            contactsPermission.launch(Manifest.permission.READ_CONTACTS)
        }
        viewModel.onRequestCalendar = {
            val ok = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) ==
                PackageManager.PERMISSION_GRANTED
            if (ok) viewModel.onCalendarPermission(true)
            else calendarPermission.launch(Manifest.permission.READ_CALENDAR)
        }
        viewModel.onRequestCallLog = {
            if (!AssistantRole.isHeld(this)) {
                AssistantRole.request(this, assistantRoleLauncher)
            } else {
                val ok = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) ==
                    PackageManager.PERMISSION_GRANTED
                if (ok) viewModel.onCallLogPermission(true)
                else callLogPermission.launch(Manifest.permission.READ_CALL_LOG)
            }
        }
        viewModel.onRequestNotifications = {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        viewModel.onStartActivity = { intent ->
            runCatching { startActivity(intent) }.isSuccess
        }
        viewModel.onRequestMic = {
            requestMic(forVoice = true)
        }
        viewModel.onRequestMicQuiet = {
            requestMic(forVoice = false)
        }
        viewModel.onSpeak = { text, listen -> voiceEngine.speak(text, listen) }
        viewModel.onListen = { startListen() }
        viewModel.onStopVoice = { stopVoice() }
        viewModel.onRequestAssistant = {
            AssistantRole.request(this, assistantRoleLauncher)
        }
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            BackHandler(enabled = state.sheet != Sheet.None || state.privatePageActive || state.activeApp != null) {
                when {
                    state.activeApp != null -> viewModel.dismissAppActions()
                    state.sheet != Sheet.None -> viewModel.closeSheet()
                    else -> viewModel.closePrivatePage()
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
                            !state.privatePageActive
                        if (::voiceEngine.isInitialized) voiceEngine.setWakeWanted(want)
                        if (state.sheet != Sheet.Voice) hideOverLockscreen()
                    }
                } finally {
                    if (::voiceEngine.isInitialized) voiceEngine.setWakeWanted(false)
                }
            }
        }
        handleVoiceIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        clearNavScrim()
        viewModel.refreshApps()
        viewModel.refreshFlow()
    }

    override fun onStop() {
        super.onStop()
        if (!authenticating) viewModel.lockPrivate()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.onHomeVisible()
        handleVoiceIntent(intent)
    }

    override fun onDestroy() {
        stopVoice()
        if (::voiceEngine.isInitialized) voiceEngine.release()
        super.onDestroy()
    }

    private fun requestMic(forVoice: Boolean) {
        pendingMicForVoice = forVoice
        val ok = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (ok) {
            if (forVoice) viewModel.onMicPermission(true)
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun handleVoiceIntent(intent: Intent?) {
        val action = intent?.action ?: return
        if (action == LumenVoiceSession.ACTION_HEY_LUMEN ||
            action == Intent.ACTION_ASSIST ||
            action == Intent.ACTION_VOICE_COMMAND
        ) {
            showOverLockscreen()
            viewModel.openVoice(fromWake = true)
        }
    }

    private fun showOverLockscreen() {
        overLockscreen = true
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
    }

    private fun hideOverLockscreen() {
        if (!overLockscreen) return
        overLockscreen = false
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(false)
            setTurnScreenOn(false)
        }
    }

    private fun startListen() {
        if (promptInFlight) return
        if (!voiceEngine.hasRecognizer()) {
            listenViaPrompt()
            return
        }
        voiceEngine.listenCommand()
    }

    private fun listenViaPrompt() {
        if (promptInFlight) return
        val intent = SpeechRecognizers.promptIntent(this)
        if (intent == null) {
            viewModel.onVoiceEngineStuck()
            return
        }
        promptInFlight = true
        window.decorView.removeCallbacks(promptWatchdog)
        window.decorView.postDelayed(promptWatchdog, 45_000L)
        viewModel.onVoiceListening()
        runCatching { speechPrompt.launch(intent) }.onFailure {
            promptInFlight = false
            viewModel.onVoiceEngineStuck()
        }
    }

    private fun stopVoice() {
        promptInFlight = false
        window.decorView.removeCallbacks(promptWatchdog)
        if (::voiceEngine.isInitialized) voiceEngine.stopCommand()
    }

    private val voiceCallbacks = object : VoiceEngine.Callbacks {
        override fun onStarting() = viewModel.onVoiceStarting()
        override fun onListening() = viewModel.onVoiceListening()
        override fun onPartial(text: String) = viewModel.onVoicePartial(text)
        override fun onResults(texts: List<String>) = viewModel.onVoiceResults(texts)
        override fun onWake(remainder: String) {
            showOverLockscreen()
            viewModel.onWakeWord(remainder)
        }
        override fun onLevel(level: Float) = viewModel.onVoiceLevel(level)
        override fun onHardError(message: String) = viewModel.onVoiceHardError(message)
        override fun onSoftMiss() = viewModel.onVoiceNoSpeech()
        override fun onSpeakFinished(listenAfter: Boolean) {
            if (listenAfter) {
                window.decorView.postDelayed({
                    if (viewModel.state.value.sheet == Sheet.Voice) startListen()
                }, 280L)
            } else {
                viewModel.onSpeakFinished()
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
