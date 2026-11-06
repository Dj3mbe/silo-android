package org.siloserver.silo.tv.ui.screens.search

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * BK addition: listens inside the app (like Emby) instead of handing off to the
 * system "Speak a title" screen. Needs the RECORD_AUDIO permission, asked once.
 */
internal class TvInAppVoiceController(
    val isAvailable: Boolean,
    private val begin: () -> Unit,
) {
    fun start() = begin()
}

@Composable
internal fun rememberTvInAppVoice(
    onResult: (String) -> Unit,
    onStatus: (String?) -> Unit,
): TvInAppVoiceController {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnStatus by rememberUpdatedState(onStatus)
    val isAvailable = remember(context) { SpeechRecognizer.isRecognitionAvailable(context) }
    val holder = remember { arrayOfNulls<SpeechRecognizer>(1) }

    val handler = remember { Handler(Looper.getMainLooper()) }
    // The single place the microphone is released. Called after every result,
    // every error, a hard time limit, leaving the screen, and BK leaving the
    // foreground, so nothing can keep listening.
    val stopListening: () -> Unit = {
        handler.removeCallbacksAndMessages(null)
        holder[0]?.destroy()
        holder[0] = null
    }

    val startListening: () -> Unit = {
        stopListening()
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        holder[0] = recognizer
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = currentOnStatus("Listening…")
            override fun onBeginningOfSpeech() = currentOnStatus("Listening…")
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onResults(results: Bundle?) {
                val spoken = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.trim().orEmpty()
                stopListening()
                if (spoken.isEmpty()) {
                    currentOnStatus("Didn't catch that. Try again.")
                } else {
                    currentOnStatus(null)
                    currentOnResult(spoken)
                }
            }
            override fun onError(error: Int) {
                stopListening()
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> currentOnStatus("Didn't catch that. Try again.")
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        currentOnStatus("Allow the microphone for BK to use voice search.")
                    SpeechRecognizer.ERROR_CLIENT -> currentOnStatus(null)
                    else -> currentOnStatus("Voice search had a problem. Try again.")
                }
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        currentOnStatus("Listening…")
        recognizer.startListening(intent)
        // Hard stop: never listen longer than this, whatever the engine does.
        handler.postDelayed({
            stopListening()
            currentOnStatus(null)
        }, 12_000L)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startListening()
        else currentOnStatus("Allow the microphone for BK to use voice search.")
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP || event == Lifecycle.Event.ON_PAUSE) {
                stopListening()
                currentOnStatus(null)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            stopListening()
        }
    }

    return remember(isAvailable) {
        TvInAppVoiceController(isAvailable) {
            val granted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            if (granted) startListening() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}
