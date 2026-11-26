package com.geotrace.navigation

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

class VoiceGuidanceManager(context: Context) {

    companion object {
        private const val TAG = "VoiceGuidanceManager"
    }

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    @Volatile
    private var tts: TextToSpeech? = null
    @Volatile
    private var isReady: Boolean = false
    @Volatile
    private var currentRouteId: String? = null
    private val spokenKeys = HashSet<String>()
    private var activeFocusRequest: AudioFocusRequest? = null

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    fun ensureInitialized() {
        if (tts != null) return
        try {
            tts = TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val instance = tts ?: return@TextToSpeech
                    val langRes = instance.setLanguage(Locale.getDefault())
                    if (langRes == TextToSpeech.LANG_MISSING_DATA || langRes == TextToSpeech.LANG_NOT_SUPPORTED) {
                        instance.setLanguage(Locale.US)
                    }
                    instance.setAudioAttributes(audioAttributes)
                    instance.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {
                            abandonAudioFocus()
                        }
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            abandonAudioFocus()
                        }
                    })
                    isReady = true
                } else {
                    Log.w(TAG, "TextToSpeech init failed with status=$status")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to initialize TTS: ${e.message}")
        }
    }

    fun resetForRoute(routeId: String) {
        currentRouteId = routeId
        spokenKeys.clear()
    }

    fun evaluateAndSpeak(
        navState: ActiveNavigationState,
        speedMps: Float
    ) {
        if (navState.voiceMode == VoiceGuidanceMode.MUTED) return
        if (navState.route.id != currentRouteId) {
            resetForRoute(navState.route.id)
        }
        ensureInitialized()

        if (navState.hasArrived) {
            val arrivalKey = "arrived_${navState.route.id}"
            if (spokenKeys.add(arrivalKey)) {
                speak("You have arrived at ${navState.route.destinationName}.")
            }
            return
        }

        if (navState.isOffRoute) {
            val offRouteKey = "offroute_${navState.currentStepIndex}"
            if (spokenKeys.add(offRouteKey)) {
                val msg = if (navState.isOfflineRerouteBlocked) {
                    "Off route. Offline mode active; return to the highlighted route."
                } else {
                    "Off route. Recalculating."
                }
                speak(msg)
            }
            return
        }

        val upcoming = navState.nextStep ?: navState.currentStep ?: return
        val dist = navState.distanceToNextTurnMeters
        val effectiveSpeed = speedMps.toDouble().coerceAtLeast(navState.route.profile.fallbackSpeedMps * 0.4)
        val previewThreshold = maxOf(350.0, effectiveSpeed * 24.0)
        val prepareThreshold = maxOf(110.0, effectiveSpeed * 8.5)
        val executeThreshold = maxOf(28.0, effectiveSpeed * 2.6)

        val currentStepLen = navState.currentStep?.distanceMeters ?: dist
        val promptTarget = upcoming.verbalPrompt.ifBlank { upcoming.instruction }

        if (navState.voiceMode == VoiceGuidanceMode.ALERTS_ONLY) {
            if (dist <= executeThreshold && spokenKeys.add("${upcoming.index}_exec")) {
                speak(promptTarget)
            }
            return
        }

        // VOICE_ON: 3 speed-adaptive announcement tiers per upcoming maneuver
        when {
            navState.currentStepIndex == 0 && spokenKeys.add("0_start") -> {
                val startText = navState.currentStep?.verbalPrompt?.ifBlank { navState.currentStep.instruction }
                    ?: "Starting navigation to ${navState.route.destinationName}."
                speak(startText)
            }
            dist <= executeThreshold && spokenKeys.add("${upcoming.index}_exec") -> {
                spokenKeys.add("${upcoming.index}_prep")
                spokenKeys.add("${upcoming.index}_prev")
                speak(promptTarget)
            }
            dist <= prepareThreshold && spokenKeys.add("${upcoming.index}_prep") -> {
                spokenKeys.add("${upcoming.index}_prev")
                speak("In ${formatSpokenDistance(dist)}, $promptTarget")
            }
            currentStepLen >= 400.0 && dist <= previewThreshold && spokenKeys.add("${upcoming.index}_prev") -> {
                speak("In ${formatSpokenDistance(dist)}, $promptTarget")
            }
        }
    }

    fun speakAlert(text: String, mode: VoiceGuidanceMode) {
        if (mode == VoiceGuidanceMode.MUTED) return
        ensureInitialized()
        speak(text)
    }

    private fun speak(text: String) {
        val engine = tts
        if (!isReady || engine == null || text.isBlank()) return
        try {
            requestAudioFocus()
            val params = Bundle().apply {
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
            }
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, "nav_${System.currentTimeMillis()}")
        } catch (e: Exception) {
            abandonAudioFocus()
            Log.w(TAG, "TTS speak failed: ${e.message}")
        }
    }

    private fun requestAudioFocus() {
        val am = audioManager ?: return
        val req = activeFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(audioAttributes)
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener { }
            .build()
            .also { activeFocusRequest = it }
        try {
            am.requestAudioFocus(req)
        } catch (_: Exception) {
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        val req = activeFocusRequest ?: return
        try {
            am.abandonAudioFocusRequest(req)
        } catch (_: Exception) {
        }
    }

    private fun formatSpokenDistance(meters: Double): String {
        return when {
            meters < 100.0 -> "${((meters / 10.0).toInt() * 10).coerceAtLeast(20)} meters"
            meters < 950.0 -> "${((meters / 50.0).toInt() * 50).coerceAtLeast(100)} meters"
            else -> String.format(Locale.US, "%.1f kilometers", meters / 1000.0)
        }
    }

    fun stopSpeaking() {
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
        abandonAudioFocus()
    }

    fun shutdown() {
        stopSpeaking()
        try {
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
        isReady = false
    }
}
