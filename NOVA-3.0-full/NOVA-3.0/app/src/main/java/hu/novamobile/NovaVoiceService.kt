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

        private const val LISTEN_DELAY = 500L
        private const val ERROR_DELAY = 1200L
        private const val COMMAND_DELAY = 700L
    }

    private val handler = Handler(Looper.getMainLooper())

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null

    private var running = false
    private var listening = false
    private var speaking = false
    private var processingCommand = false
    private var novaActivated = false

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
                startNova()
            }

            ACTION_STOP -> {
                stopNova()
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel = NotificationChannel(
                CHANNEL_ID,
                "NOVA hangvezérlés",
                NotificationManager.IMPORTANCE_LOW
            )

            channel.description =
                "NOVA háttérben futó hangvezérlése"

            channel.setShowBadge(false)

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

            try {

                tts?.setLanguage(
                    Locale("hu", "HU")
                )

                tts?.setSpeechRate(1.0f)
                tts?.setPitch(1.0f)

            } catch (_: Exception) {
            }
        }
    }

    private fun createRecognizer() {

        destroyRecognizer()

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
                    listening = true
                }

                override fun onBeginningOfSpeech() {
                    listening = true
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
                    listening = false
                }

                override fun onError(
                    error: Int
                ) {

                    listening = false

                    if (
                        running &&
                        !speaking &&
                        !processingCommand
                    ) {
                        scheduleListening(ERROR_DELAY)
                    }
                }

                override fun onResults(
                    results: Bundle?
                ) {

                    listening = false

                    if (!running) {
                        return
                    }

                    if (speaking || processingCommand) {
                        return
                    }

                    val heard =
                        results
                            ?.getStringArrayList(
                                SpeechRecognizer.RESULTS_RECOGNITION
                            )
                            ?.firstOrNull()
                            .orEmpty()

                    if (heard.isBlank()) {
                        scheduleListening(LISTEN_DELAY)
                        return
                    }

                    processSpeech(heard)
                }

                override fun onPartialResults(
                    partialResults: Bundle?
                ) {
                    // Nem hajtunk végre parancsot részleges eredményből.
                }

                override fun onEvent(
                    eventType: Int,
                    params: Bundle?
                ) {
                }
            }
        )
    }

    private fun startNova() {

        if (running) {
            return
        }

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
        listening = false
        speaking = false
        processingCommand = false
        novaActivated = false

        scheduleListening(300L)
    }

    private fun scheduleListening(
        delay: Long
    ) {

        handler.removeCallbacksAndMessages(null)

        if (!running) {
            return
        }

        if (speaking || processingCommand) {
            return
        }

        handler.postDelayed({

            if (!running) {
                return@postDelayed
            }

            if (speaking || processingCommand) {
                return@postDelayed
            }

            startRecognition()

        }, delay)
    }

    private fun startRecognition() {

        if (!running) {
            return
        }

        if (speaking || processingCommand) {
            return
        }

        if (listening) {
            return
        }

        try {

            recognizer?.cancel()

        } catch (_: Exception) {
        }

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
                    false
                )

                putExtra(
                    RecognizerIntent.EXTRA_MAX_RESULTS,
                    3
                )

                putExtra(
                    RecognizerIntent.EXTRA_CALLING_PACKAGE,
                    packageName
                )
            }

        try {

            recognizer?.startListening(
                recognitionIntent
            )

            listening = true

        } catch (_: Exception) {

            listening = false

            scheduleListening(ERROR_DELAY)
        }
    }

    private fun processSpeech(
        raw: String
    ) {

        if (!running) {
            return
        }

        if (processingCommand) {
            return
        }

        val normalized =
            MainActivity.normalize(raw)

        if (normalized.isBlank()) {
            scheduleListening(LISTEN_DELAY)
            return
        }

        val containsNova =
            containsWakeWord(normalized)

        if (!novaActivated) {

            if (!containsNova) {
                scheduleListening(LISTEN_DELAY)
                return
            }

            novaActivated = true

            val command =
                removeWakeWord(normalized)

            if (command.isBlank()) {

                speakAndResume(
                    "Igen?"
                )

                return
            }

            executeCommand(command)

            return
        }

        val command =
            removeWakeWord(normalized)

        if (command.isBlank()) {

            speakAndResume(
                "Igen?"
            )

            return
        }

        executeCommand(command)
    }

    private fun containsWakeWord(
        text: String
    ): Boolean {

        val normalized =
            MainActivity.normalize(text)

        val words = listOf(
            "nova",
            "nóva",
            "no va",
            "noa",
            "novaa",
            "novah",
            "nová"
        )

        for (word in words) {

            val pattern =
                Regex(
                    "(^|\\s)" +
                            Regex.escape(word) +
                            "(\\s|$)"
                )

            if (
                pattern.containsMatchIn(
                    normalized
                )
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

        val words = listOf(
            "nova",
            "nóva",
            "no va",
            "noa",
            "novaa",
            "novah",
            "nová"
        )

        for (word in words) {

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

        if (!running) {
            return
        }

        if (processingCommand) {
            return
        }

        processingCommand = true

        stopRecognition()

        handler.postDelayed({

            if (!running) {
                processingCommand = false
                return@postDelayed
            }

            try {

                val result =
                    CommandRouter.execute(
                        applicationContext,
                        command
                    )

                val response =
                    when (result.type) {

                        CommandRouter.ResultType.EXECUTED ->
                            result.response

                        CommandRouter.ResultType.UNKNOWN ->
                            result.response

                        CommandRouter.ResultType.AMBIGUOUS -> {

                            val options =
                                result.options.joinToString(
                                    separator = " vagy "
                                )

                            if (options.isBlank()) {
                                result.response
                            } else {
                                "${result.response} $options."
                            }
                        }

                        CommandRouter.ResultType.CLARIFICATION ->
                            result.response
                    }

                speakAndResume(response)

            } catch (_: Exception) {

                speakAndResume(
                    "Nem sikerült végrehajtanom a parancsot."
                )
            }

        }, COMMAND_DELAY)
    }

    private fun speakAndResume(
        message: String
    ) {

        if (!running) {
            return
        }

        processingCommand = false
        stopRecognition()

        if (message.isBlank()) {
            scheduleListening(LISTEN_DELAY)
            return
        }

        speaking = true

        try {

            tts?.speak(
                message,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "nova_response"
            )

        } catch (_: Exception) {

            speaking = false

            if (running) {
                scheduleListening(LISTEN_DELAY)
            }

            return
        }

        handler.postDelayed({

            speaking = false

            if (running) {
                scheduleListening(400L)
            }

        }, calculateSpeechDelay(message))
    }

    private fun calculateSpeechDelay(
        text: String
    ): Long {

        val length =
            text.length.coerceIn(
                1,
                200
            )

        return (
                700L +
                        length * 45L
                ).coerceAtMost(7000L)
    }

    private fun stopRecognition() {

        listening = false

        try {
            recognizer?.cancel()
        } catch (_: Exception) {
        }
    }

    private fun stopNova() {

        running = false
        listening = false
        speaking = false
        processingCommand = false
        novaActivated = false

        handler.removeCallbacksAndMessages(null)

        stopRecognition()

        try {
            tts?.stop()
        } catch (_: Exception) {
        }
    }

    private fun destroyRecognizer() {

        try {
            recognizer?.cancel()
        } catch (_: Exception) {
        }

        try {
            recognizer?.destroy()
        } catch (_: Exception) {
        }

        recognizer = null
        listening = false
    }

    override fun onDestroy() {

        stopNova()

        destroyRecognizer()

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
