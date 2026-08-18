package hu.novamobile

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import java.util.Locale

class NovaVoiceService : Service(), RecognitionListener {

    companion object {
        const val ACTION_START =
            "hu.novamobile.NOVA_START"

        const val ACTION_STOP =
            "hu.novamobile.NOVA_STOP"

        private const val CHANNEL_ID =
            "nova_voice_channel"

        private const val NOTIFICATION_ID =
            3001
    }

    private var speechRecognizer:
        SpeechRecognizer? = null

    private var speechIntent:
        Intent? = null

    private var textToSpeech:
        TextToSpeech? = null

    private var isListening = false
    private var isSpeaking = false
    private var shouldListen = false

    private val handler =
        Handler(mainLooper)

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        textToSpeech =
            TextToSpeech(this) { status ->

                if (status ==
                    TextToSpeech.SUCCESS
                ) {
                    textToSpeech?.language =
                        Locale("hu", "HU")

                    textToSpeech?.setSpeechRate(
                        1.0f
                    )
                }
            }

        setupSpeechRecognizer()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {

            ACTION_STOP -> {
                shouldListen = false
                stopListening()
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_START -> {

                startForeground(
                    NOTIFICATION_ID,
                    createNotification()
                )

                shouldListen = true

                startListening()
            }

            else -> {

                startForeground(
                    NOTIFICATION_ID,
                    createNotification()
                )

                shouldListen = true

                startListening()
            }
        }

        return START_STICKY
    }

    // ============================================================
    // SPEECH RECOGNIZER
    // ============================================================

    private fun setupSpeechRecognizer() {

        if (
            !SpeechRecognizer
                .isRecognitionAvailable(this)
        ) {
            return
        }

        try {

            speechRecognizer =
                SpeechRecognizer
                    .createSpeechRecognizer(this)

            speechRecognizer
                ?.setRecognitionListener(this)

            speechIntent =
                Intent(
                    RecognizerIntent
                        .ACTION_RECOGNIZE_SPEECH
                ).apply {

                    putExtra(
                        RecognizerIntent
                            .EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent
                            .LANGUAGE_MODEL_FREE_FORM
                    )

                    putExtra(
                        RecognizerIntent
                            .EXTRA_LANGUAGE,
                        "hu-HU"
                    )

                    putExtra(
                        RecognizerIntent
                            .EXTRA_LANGUAGE_PREFERENCE,
                        "hu-HU"
                    )

                    putExtra(
                        RecognizerIntent
                            .EXTRA_PARTIAL_RESULTS,
                        false
                    )

                    putExtra(
                        RecognizerIntent
                            .EXTRA_MAX_RESULTS,
                        5
                    )
                }

        } catch (_: Exception) {
            speechRecognizer = null
        }
    }

    private fun startListening() {

        if (!shouldListen) return
        if (isSpeaking) return
        if (isListening) return

        if (
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        if (speechRecognizer == null) {
            setupSpeechRecognizer()
        }

        val recognizer =
            speechRecognizer ?: return

        val intent =
            speechIntent ?: return

        try {

            isListening = true

            recognizer.startListening(
                intent
            )

        } catch (_: Exception) {

            isListening = false

            restartListening()
        }
    }

    private fun stopListening() {

        isListening = false

        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {
        }

        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {
        }
    }

    private fun restartListening() {

        if (!shouldListen) return
        if (isSpeaking) return

        isListening = false

        handler.postDelayed({

            if (
                shouldListen &&
                !isSpeaking &&
                !isListening
            ) {
                startListening()
            }

        }, 700)
    }

    // ============================================================
    // SPEECH RESULTS
    // ============================================================

    override fun onResults(
        results: Bundle?
    ) {

        isListening = false

        if (!shouldListen) {
            return
        }

        val recognizedText =
            results
                ?.getStringArrayList(
                    SpeechRecognizer
                        .RESULTS_RECOGNITION
                )
                ?.firstOrNull()
                ?.trim()
                ?: ""

        if (
            recognizedText.isNotBlank()
        ) {
            handleSpeech(
                recognizedText
            )
        } else {
            restartListening()
        }
    }

    private fun handleSpeech(
        text: String
    ) {

        val normalized =
            normalize(text)

        if (
            !containsWakeWord(
                normalized
            )
        ) {
            restartListening()
            return
        }

        val command =
            removeWakeWord(
                normalized
            )

        if (command.isBlank()) {

            speak(
                "Itt vagyok."
            )

            return
        }

        executeCommand(command)
    }

    // ============================================================
    // COMMAND ROUTER
    // ============================================================

    private fun executeCommand(
        command: String
    ) {

        try {

            val result =
                CommandRouter.execute(
                    this,
                    command
                )

            when (result.type) {

                CommandRouter
                    .ResultType.EXECUTED -> {

                    speak(
                        result.response
                    )
                }

                CommandRouter
                    .ResultType.AMBIGUOUS -> {

                    val options =
                        result.options
                            .joinToString(
                                " vagy "
                            )

                    speak(
                        if (
                            options.isNotBlank()
                        ) {
                            "${result.response} $options"
                        } else {
                            result.response
                        }
                    )
                }

                CommandRouter
                    .ResultType.CLARIFICATION -> {

                    val option =
                        result.options
                            .firstOrNull()

                    speak(
                        if (
                            option != null
                        ) {
                            "${result.response} $option"
                        } else {
                            result.response
                        }
                    )
                }

                CommandRouter
                    .ResultType.UNKNOWN -> {

                    speak(
                        result.response
                    )
                }
            }

        } catch (_: Exception) {

            speak(
                "Hiba történt a parancs végrehajtásakor."
            )
        }
    }

    // ============================================================
    // TEXT TO SPEECH
    // ============================================================

    private fun speak(
        text: String
    ) {

        if (text.isBlank()) {
            restartListening()
            return
        }

        val tts =
            textToSpeech

        if (tts == null) {
            restartListening()
            return
        }

        isSpeaking = true

        try {

            tts.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "NOVA_RESPONSE"
            )

            handler.postDelayed({

                if (isSpeaking) {
                    finishSpeaking()
                }

            }, calculateSpeechDelay(text))

        } catch (_: Exception) {

            finishSpeaking()
        }
    }

    private fun calculateSpeechDelay(
        text: String
    ): Long {

        val estimated =
            text.length * 45L

        return estimated.coerceIn(
            1000L,
            8000L
        )
    }

    private fun finishSpeaking() {

        isSpeaking = false

        if (shouldListen) {
            restartListening()
        }
    }

    // ============================================================
    // NORMALIZE
    // ============================================================

    private fun normalize(
        input: String
    ): String {

        return input
            .lowercase(
                Locale("hu", "HU")
            )
            .replace("á", "a")
            .replace("é", "e")
            .replace("í", "i")
            .replace("ó", "o")
            .replace("ö", "o")
            .replace("ő", "o")
            .replace("ú", "u")
            .replace("ü", "u")
            .replace("ű", "u")
            .replace(
                Regex("[^a-z0-9 ]"),
                " "
            )
            .replace(
                Regex("\\s+"),
                " "
            )
            .trim()
    }

    // ============================================================
    // FUZZY SIMILARITY
    // ============================================================

    private fun fuzzySimilarity(
        a: String,
        b: String
    ): Double {

        val aa =
            normalize(a)

        val bb =
            normalize(b)

        if (aa == bb) {
            return 1.0
        }

        if (
            aa.isEmpty() ||
            bb.isEmpty()
        ) {
            return 0.0
        }

        var previous =
            IntArray(
                bb.length + 1
            ) { it }

        for (i in aa.indices) {

            val current =
                IntArray(
                    bb.length + 1
                )

            current[0] =
                i + 1

            for (j in bb.indices) {

                val cost =
                    if (
                        aa[i] ==
                        bb[j]
                    ) {
                        0
                    } else {
                        1
                    }

                current[j + 1] =
                    minOf(
                        current[j] + 1,
                        previous[j + 1] + 1,
                        previous[j] + cost
                    )
            }

            previous =
                current
        }

        val distance =
            previous[bb.length]

        val longest =
            maxOf(
                aa.length,
                bb.length
            )

        if (longest == 0) {
            return 1.0
        }

        return 1.0 -
            distance.toDouble() /
            longest.toDouble()
    }

    // ============================================================
    // WAKE WORD
    // ============================================================

    private fun containsWakeWord(
        text: String
    ): Boolean {

        if (
            text == "nova" ||
            text.startsWith("nova ") ||
            text.contains(" nova ")
        ) {
            return true
        }

        val wakeWords =
            listOf(

                "nova",
                "noba",
                "nava",
                "nora",
                "no va",
                "noova",
                "novaa",
                "novah",
                "novi",
                "noya",

                "nova gyere",
                "nova figyelj",
                "nova hallasz",
                "nova hallod",
                "nova itt vagy",
                "nova vagy ott",
                "nova jelentkezz",
                "nova figyelsz",
                "nova hallgatsz",
                "nova indulj",
                "nova kezdj",
                "nova indul",
                "nova aktiv",
                "nova aktivizal",
                "nova start",
                "nova startolj",
                "nova kezdes",
                "nova segits",
                "nova segits nekem",
                "nova figyelj ram",
                "nova hallgass",
                "nova hallgass meg",
                "nova figyelj ide",
                "nova ide",
                "nova itt",
                "nova most",
                "nova hey",
                "nova hello",
                "nova szia",
                "nova cso",
                "nova te",
                "nova te ott",
                "nova chatbot",
                "nova asszisztens",
                "nova assistant",
                "nova ai",
                "nova rendszer",
                "nova program",
                "nova alkalmazas",
                "nova app",
                "nova hang",
                "nova hangasszisztens",
                "nova voice",
                "nova voice assistant",
                "nova computer",
                "nova gep",
                "nova telefon",
                "nova mobil",
                "nova mobile",
                "nova android",
                "nova figyelsz ram",
                "nova hallasz engem",
                "nova hallod engem",
                "nova itt vagy nekem",
                "nova ott vagy",
                "nova vagy",
                "nova mukodj",
                "nova mukodj mar",
                "nova kelj fel",
                "nova ebredj",
                "nova ebredj fel",
                "nova ebren vagy",
                "nova felebredtel",
                "nova online",
                "nova online vagy",
                "nova rendszer online",
                "nova bekapcsol",
                "nova aktiv vagy",
                "nova keszen allsz",
                "nova ready",
                "nova ready vagy",
                "nova wake up",
                "nova wakeup",
                "nova start up",
                "nova indulás",
                "nova go",
                "nova respond",
                "nova response",
                "nova valaszolj",
                "nova valasz",
                "nova beszelj",
                "nova beszelj hozzam",
                "nova beszelj velem",
                "nova szolj",
                "nova mondj valamit",
                "nova mondj valamit nekem",
                "nova kommunikacio",
                "nova kapcsolat",
                "nova kapcsolodj",
                "nova figyelem",
                "nova figyelem ide",
                "nova figyelmet",
                "nova ram figyelj",
                "nova hallgass ide",
                "nova hallgass ram"
            )

        for (wakeWord in wakeWords) {

            if (
                text.contains(
                    normalize(wakeWord)
                )
            ) {
                return true
            }
        }

        for (word in text.split(" ")) {

            if (word.length < 2) {
                continue
            }

            if (
                fuzzySimilarity(
                    word,
                    "nova"
                ) >= 0.65
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
            text

        val variants =
            listOf(
                "nova",
                "noba",
                "nava",
                "nora",
                "no va",
                "noova",
                "novaa",
                "novah",
                "novi",
                "noya"
            )

        for (variant in variants) {

            result =
                result.replace(
                    Regex(
                        "\\b${Regex.escape(variant)}\\b"
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

    // ============================================================
    // NOTIFICATION
    // ============================================================

    private fun createNotificationChannel() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "NOVA hangasszisztens",
                    NotificationManager
                        .IMPORTANCE_LOW
                ).apply {

                    description =
                        "NOVA hangfelismerési szolgáltatás"

                    setShowBadge(false)
                }

            val manager =
                getSystemService(
                    NotificationManager::class.java
                )

            manager.createNotificationChannel(
                channel
            )
        }
    }

    private fun createNotification():
        Notification {

        return NotificationCompat
            .Builder(
                this,
                CHANNEL_ID
            )
            .setContentTitle(
                "NOVA"
            )
            .setContentText(
                "NOVA hangasszisztens aktív"
            )
            .setSmallIcon(
                android.R.drawable
                    .ic_btn_speak_now
            )
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    // ============================================================
    // RECOGNITION LISTENER
    // ============================================================

    override fun onReadyForSpeech(
        params: Bundle?
    ) {
    }

    override fun onBeginningOfSpeech() {
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

        isListening = false

        if (
            shouldListen &&
            !isSpeaking
        ) {
            restartListening()
        }
    }

    override fun onError(
        error: Int
    ) {

        isListening = false

        if (shouldListen) {
            restartListening()
        }
    }

    override fun onPartialResults(
        partialResults: Bundle?
    ) {
    }

    override fun onEvent(
        eventType: Int,
        params: Bundle?
    ) {
    }

    // ============================================================
    // DESTROY
    // ============================================================

    override fun onDestroy() {

        shouldListen = false
        isListening = false
        isSpeaking = false

        handler.removeCallbacksAndMessages(
            null
        )

        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {
        }

        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }

        speechRecognizer = null

        try {
            textToSpeech?.stop()
        } catch (_: Exception) {
        }

        try {
            textToSpeech?.shutdown()
        } catch (_: Exception) {
        }

        textToSpeech = null

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? {
        return null
    }
}
