package hu.novamobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

class NovaVoiceService : Service(), TextToSpeech.OnInitListener {

    companion object {
        const val ACTION_START = "hu.novamobile.START_VOICE"
        const val ACTION_STOP = "hu.novamobile.STOP_VOICE"

        private const val CHANNEL_ID = "nova_voice_channel"
        private const val NOTIFICATION_ID = 1001
    }

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null

    private var running = false
    private var novaActivated = false
    private var recognitionStarting = false

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        startForeground(
            NOTIFICATION_ID,
            createNotification()
        )

        tts = TextToSpeech(
            applicationContext,
            this
        )

        createRecognizer()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {

            ACTION_START -> {
                if (!running) {
                    startListening()
                }
            }

            ACTION_STOP -> {
                stopListening()
                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel = NotificationChannel(
                CHANNEL_ID,
                "NOVA hangvezérlés",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description =
                    "NOVA háttérben futó hangvezérlése"

                setShowBadge(false)
            }

            val manager =
                getSystemService(
                    Context.NOTIFICATION_SERVICE
                ) as NotificationManager

            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {

        val openIntent =
            Intent(
                this,
                MainActivity::class.java
            )

        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
            )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            Notification.Builder(
                this,
                CHANNEL_ID
            )
                .setContentTitle("NOVA aktív")
                .setContentText(
                    "NOVA hangvezérlés fut."
                )
                .setSmallIcon(
                    android.R.drawable.ic_btn_speak_now
                )
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .setCategory(
                    Notification.CATEGORY_SERVICE
                )
                .build()

        } else {

            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("NOVA aktív")
                .setContentText(
                    "NOVA hangvezérlés fut."
                )
                .setSmallIcon(
                    android.R.drawable.ic_btn_speak_now
                )
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .setCategory(
                    Notification.CATEGORY_SERVICE
                )
                .build()
        }
    }

    override fun onInit(status: Int) {

        if (status == TextToSpeech.SUCCESS) {

            val result =
                tts?.setLanguage(
                    Locale("hu", "HU")
                )

            tts?.setSpeechRate(1.0f)
            tts?.setPitch(1.0f)

            if (
                result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                // Magyar TTS nem érhető el.
            }
        }
    }

    private fun speak(message: String) {

        if (message.isBlank()) {
            return
        }

        try {
            tts?.speak(
                message,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "nova_response"
            )
        } catch (_: Exception) {
        }
    }

    private fun createRecognizer() {

        try {
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (_: Exception) {
        }

        recognizer = null

        if (
            !SpeechRecognizer.isRecognitionAvailable(
                applicationContext
            )
        ) {
            return
        }

        recognizer =
            SpeechRecognizer.createSpeechRecognizer(
                applicationContext
            )

        recognizer?.setRecognitionListener(
            object : RecognitionListener {

                override fun onReadyForSpeech(
                    params: Bundle?
                ) {
                    recognitionStarting = false
                }

                override fun onBeginningOfSpeech() {
                    recognitionStarting = false
                }

                override fun onRmsChanged(
                    rmsdB: Float
                ) {
                }

                override fun onBufferReceived(
                    buffer: ByteArray?
                ) {
                }

                override fun onEndOfSpeech() {
                    recognitionStarting = false
                }

                override fun onError(
                    error: Int
                ) {

                    recognitionStarting = false

                    if (running) {
                        restartRecognition(800)
                    }
                }

                override fun onResults(
                    results: Bundle?
                ) {

                    recognitionStarting = false

                    if (!running) {
                        return
                    }

                    val resultsList =
                        results?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )

                    val heard =
                        resultsList
                            ?.firstOrNull()
                            .orEmpty()

                    if (heard.isNotBlank()) {
                        handleSpeech(heard)
                    }

                    restartRecognition(600)
                }

                override fun onPartialResults(
                    partialResults: Bundle?
                ) {
                    // Részleges eredményből nem hajtunk végre parancsot.
                }

                override fun onEvent(
                    eventType: Int,
                    params: Bundle?
                ) {
                }
            }
        )
    }

    private fun startListening() {

        if (
            !SpeechRecognizer.isRecognitionAvailable(
                applicationContext
            )
        ) {

            speak(
                "A beszédfelismerés nem érhető el ezen a készüléken."
            )

            return
        }

        running = true
        novaActivated = false
        recognitionStarting = false

        restartRecognition(300)
    }

    private fun restartRecognition(
        delay: Long
    ) {

        if (!running) {
            return
        }

        if (recognitionStarting) {
            return
        }

        recognitionStarting = true

        handler.postDelayed({

            if (!running) {
                recognitionStarting = false
                return@postDelayed
            }

            try {

                recognizer?.cancel()

                val recognitionIntent =
                    Intent(
                        RecognizerIntent.ACTION_RECOGNIZE_SPEECH
                    ).apply {

                        putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE,
                            "hu-HU"
                        )

                        putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                            "hu-HU"
                        )

                        putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                        )

                        putExtra(
                            RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                            true
                        )

                        putExtra(
                            RecognizerIntent.EXTRA_MAX_RESULTS,
                            5
                        )

                        putExtra(
                            RecognizerIntent.EXTRA_CALLING_PACKAGE,
                            packageName
                        )
                    }

                recognizer?.startListening(
                    recognitionIntent
                )

            } catch (_: Exception) {

                recognitionStarting = false

                if (running) {
                    restartRecognition(1500)
                }
            }

        }, delay)
    }

    private fun handleSpeech(
        rawSpeech: String
    ) {

        val normalized =
            MainActivity.normalize(rawSpeech)

        if (normalized.isBlank()) {
            return
        }

        val containsNova =
            containsWakeWord(normalized)

        if (!novaActivated) {

            if (!containsNova) {
                return
            }

            novaActivated = true

            val command =
                removeWakeWord(normalized)

            if (command.isBlank()) {

                speak("Igen?")

                return
            }

            executeCommand(command)

            return
        }

        val command =
            removeWakeWord(normalized)

        if (command.isBlank()) {

            speak("Igen?")

            return
        }

        executeCommand(command)
    }

    private fun containsWakeWord(
        text: String
    ): Boolean {

        val normalized =
            MainActivity.normalize(text)

        val wakeWords = listOf(
            "nova",
            "nóva",
            "nóva",
            "no va",
            "noa",
            "novaa",
            "novah",
            "nová",
            "nóva"
        )

        for (word in wakeWords) {

            if (
                Regex(
                    "(^|\\s)" +
                            Regex.escape(word) +
                            "(\\s|$)"
                ).containsMatchIn(normalized)
            ) {
                return true
            }
        }

        return false
    }

    private fun removeWakeWord(
        text: String
    ): String {

        var result =
            MainActivity.normalize(text)

        val wakeWords = listOf(
            "nova",
            "nóva",
            "no va",
            "noa",
            "novaa",
            "novah",
            "nová"
        )

        for (word in wakeWords) {

            result =
                result.replace(
                    Regex(
                        "(^|\\s)" +
                                Regex.escape(word) +
                                "(?=\\s|$)"
                    ),
                    " "
                )
        }

        return result
            .replace(
                Regex("\\s+"),
                " "
            )
            .trim()
    }

    private fun executeCommand(
        command: String
    ) {

        try {

            val result =
                CommandRouter.execute(
                    applicationContext,
                    command
                )

            when (result.type) {

                CommandRouter.ResultType.EXECUTED -> {

                    speak(
                        result.response
                    )
                }

                CommandRouter.ResultType.UNKNOWN -> {

                    speak(
                        result.response
                    )
                }

                CommandRouter.ResultType.AMBIGUOUS -> {

                    val options =
                        result.options.joinToString(
                            separator = " vagy "
                        )

                    if (options.isBlank()) {

                        speak(
                            result.response
                        )

                    } else {

                        speak(
                            "${result.response} $options."
                        )
                    }
                }

                CommandRouter.ResultType.CLARIFICATION -> {

                    speak(
                        result.response
                    )
                }
            }

        } catch (_: Exception) {

            speak(
                "Nem sikerült végrehajtanom a parancsot."
            )
        }
    }

    private fun stopListening() {

        running = false
        novaActivated = false
        recognitionStarting = false

        handler.removeCallbacksAndMessages(null)

        try {
            recognizer?.cancel()
        } catch (_: Exception) {
        }

        try {
            tts?.stop()
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {

        running = false
        novaActivated = false
        recognitionStarting = false

        handler.removeCallbacksAndMessages(null)

        try {
            recognizer?.cancel()
        } catch (_: Exception) {
        }

        try {
            recognizer?.destroy()
        } catch (_: Exception) {
        }

        recognizer = null

        try {
            tts?.stop()
        } catch (_: Exception) {
        }

        try {
            tts?.shutdown()
        } catch (_: Exception) {
        }

        tts = null

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? {
        return null
    }
}
