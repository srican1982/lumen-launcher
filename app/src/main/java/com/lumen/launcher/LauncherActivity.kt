package com.lumen.launcher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.WindowManager
import java.util.Locale
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
import com.lumen.launcher.voice.HeyLumenListener
import com.lumen.launcher.voice.LumenVoiceSession
import kotlinx.coroutines.launch

class LauncherActivity : FragmentActivity() {

    private val viewModel: LauncherViewModel by viewModels()
    private var authenticating = false
    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    private var listenAfterSpeak = false
    private var pendingMicForVoice = false
    private var overLockscreen = false
    private var askedWakeMic = false
    private lateinit var heyLumen: HeyLumenListener

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
        heyLumen = HeyLumenListener(applicationContext) { remainder ->
            showOverLockscreen()
            viewModel.onWakeWord(remainder)
        }
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
            val ok = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) ==
                PackageManager.PERMISSION_GRANTED
            if (ok) viewModel.onCallLogPermission(true)
            else callLogPermission.launch(Manifest.permission.READ_CALL_LOG)
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
        viewModel.onSpeak = { text, listen -> speak(text, listen) }
        viewModel.onListen = {
            if (::heyLumen.isInitialized) heyLumen.setEnabled(false)
            startListen()
        }
        viewModel.onStopVoice = { stopVoice() }
        viewModel.onRequestAssistant = {
            AssistantRole.request(this, assistantRoleLauncher)
        }
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) {
                        runOnUiThread {
                            if (listenAfterSpeak) {
                                listenAfterSpeak = false
                                startListen()
                            } else {
                                viewModel.onSpeakFinished()
                            }
                        }
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        runOnUiThread { viewModel.onSpeakFinished() }
                    }
                })
            }
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
                        heyLumen.setEnabled(want)
                        if (state.sheet != Sheet.Voice) hideOverLockscreen()
                    }
                } finally {
                    heyLumen.setEnabled(false)
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
        if (::heyLumen.isInitialized) heyLumen.release()
        stopVoice()
        tts?.shutdown()
        tts = null
        recognizer?.destroy()
        recognizer = null
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

    private fun speak(text: String, listenAfter: Boolean) {
        listenAfterSpeak = listenAfter
        val engine = tts
        if (engine == null) {
            if (listenAfter) startListen() else viewModel.onSpeakFinished()
            return
        }
        val spoken = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "lumen")
        if (spoken != TextToSpeech.SUCCESS) {
            if (listenAfter) startListen() else viewModel.onSpeakFinished()
        }
    }

    private fun startListen() {
        if (::heyLumen.isInitialized) heyLumen.setEnabled(false)
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            viewModel.onVoiceFailed()
            return
        }
        val rec = recognizer ?: SpeechRecognizer.createSpeechRecognizer(this).also { created ->
            created.setRecognitionListener(voiceListener)
            recognizer = created
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 400L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 600L)
        }
        viewModel.onVoiceListening()
        runCatching { rec.startListening(intent) }.onFailure { viewModel.onVoiceFailed() }
    }

    private fun stopVoice() {
        listenAfterSpeak = false
        runCatching { recognizer?.cancel() }
        runCatching { tts?.stop() }
    }

    private val voiceListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onError(error: Int) {
            when (error) {
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                SpeechRecognizer.ERROR_NO_MATCH -> viewModel.onVoiceNoSpeech()
                SpeechRecognizer.ERROR_CLIENT,
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    window.decorView.postDelayed({
                        if (viewModel.state.value.sheet == Sheet.Voice) startListen()
                    }, 160L)
                }
                else -> viewModel.onVoiceFailed()
            }
        }
        override fun onResults(results: Bundle?) {
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            viewModel.onVoiceResult(text)
        }
        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            viewModel.onVoicePartial(text)
        }
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
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
            .setTitle("Unlock Private Space")
            .setSubtitle("Confirm it’s you to open locked apps")
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
