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
        const val ACTION_START = "hu.novamobile.NOVA_START"
        const val ACTION_STOP = "hu.novamobile.NOVA_STOP"

        private const val CHANNEL_ID = "nova_voice_channel"
        private const val NOTIFICATION_ID = 3001

        private const val WAKE_WORD = "nova"
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var speechIntent: Intent? = null
    private var textToSpeech: TextToSpeech? = null

    private var isListening = false
    private var isSpeaking = false
    private var shouldListen = false

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale("hu", "HU")
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

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            return
        }

        speechRecognizer =
            SpeechRecognizer.createSpeechRecognizer(this)

        speechRecognizer?.setRecognitionListener(this)

        speechIntent =
            Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            ).apply {

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    "hu-HU"
                )

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                    "hu-HU"
                )

                putExtra(
                    RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                    false
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
    }

    private fun startListening() {

        if (!shouldListen) {
            return
        }

        if (isSpeaking) {
            return
        }

        if (isListening) {
            return
        }

        if (
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val recognizer =
            speechRecognizer ?: run {
                setupSpeechRecognizer()
                speechRecognizer
            } ?: return

        val intent =
            speechIntent ?: return

        try {

            isListening = true

            recognizer.startListening(intent)

        } catch (_: Exception) {

            isListening = false

            restartListening()
        }
    }

    private fun stopListening() {

        shouldListen = false
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

        if (!shouldListen) {
            return
        }

        isListening = false

        android.os.Handler(
            mainLooper
        ).postDelayed(
            {
                if (shouldListen && !isSpeaking) {
                    startListening()
                }
            },
            500
        )
    }

    // ============================================================
    // FELISMERÉS
    // ============================================================

    override fun onResults(results: Bundle?) {

        isListening = false

        if (!shouldListen) {
            return
        }

        val recognizedText =
            results
                ?.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION
                )
                ?.firstOrNull()
                ?.trim()
                ?: ""

        if (recognizedText.isNotBlank()) {

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

        // --------------------------------------------------------
        // CSAK AKKOR REAGÁL, HA ELHANGZIK A "NOVA"
        // --------------------------------------------------------

        if (!containsWakeWord(normalized)) {

            restartListening()
            return
        }

        // --------------------------------------------------------
        // WAKE WORD KISZEDÉSE
        // --------------------------------------------------------

        val commandText =
            removeWakeWord(normalized)

        // Csak annyit mondott:
        // "Nova"

        if (commandText.isBlank()) {

            speak(
                "Itt vagyok."
            )

            return
        }

        executeCommand(
            commandText
        )
    }

    // ============================================================
    // PARANCS VÉGREHAJTÁS
    // ============================================================

    private fun executeCommand(
        commandText: String
    ) {

        try {

            val result =
                CommandRouter.execute(
                    this,
                    commandText
                )

            when (result.type) {

                CommandRouter.ResultType.EXECUTED -> {

                    speak(
                        result.response
                    )
                }

                CommandRouter.ResultType.AMBIGUOUS -> {

                    val options =
                        result.options.joinToString(
                            " vagy "
                        )

                    speak(
                        if (options.isNotBlank()) {
                            "${result.response} $options"
                        } else {
                            result.response
                        }
                    )
                }

                CommandRouter.ResultType.CLARIFICATION -> {

                    val option =
                        result.options.firstOrNull()

                    speak(
                        if (option != null) {
                            "${result.response} $option"
                        } else {
                            result.response
                        }
                    )
                }

                CommandRouter.ResultType.UNKNOWN -> {

                    speak(
                        result.response
                    )
                }
            }

        } catch (e: Exception) {

            speak(
                "Hiba történt a parancs végrehajtásakor."
            )
        }
    }

    // ============================================================
    // TTS
    // ============================================================

    private fun speak(
        text: String
    ) {

        if (text.isBlank()) {
            restartListening()
            return
        }

        val tts =
            textToSpeech ?: run {
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

        } catch (_: Exception) {

            isSpeaking = false
            restartListening()
        }
    }

    // ============================================================
    // TTS CALLBACK KEZELÉS
    // ============================================================

    private fun finishSpeaking() {

        isSpeaking = false

        if (shouldListen) {
            restartListening()
        }
    }

    // ============================================================
    // NORMALIZÁLÁS
    // ============================================================

    private fun normalize(
        input: String
    ): String {

        return input
            .lowercase(Locale("hu", "HU"))
            .replace(
                "á",
                "a"
            )
            .replace(
                "é",
                "e"
            )
            .replace(
                "í",
                "i"
            )
            .replace(
                "ó",
                "o"
            )
            .replace(
                "ö",
                "o"
            )
            .replace(
                "ő",
                "o"
            )
            .replace(
                "ú",
                "u"
            )
            .replace(
                "ü",
                "u"
            )
            .replace(
                "ű",
                "u"
            )
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
    // 100+ MEGSZÓLÍTÁS / WAKE WORD
    // ============================================================

    private fun containsWakeWord(
        text: String
    ): Boolean {

        val wakeWords =
            listOf(

                "nova",
                "nóva",
                "no va",
                "noova",
                "novaa",
                "novah",
                "novi",
                "novaa",
                "nov",
                "noba",
                "nóva",
                "noba",
                "nava",
                "nora",
                "noa",
                "noya",
                "nóva",

                "nova gyere",
                "nova figyelj",
                "nova hallasz",
                "nova hallod",
                "nova itt vagy",
                "nova vagy ott",
                "nova jelentkezz",
                "nova figyelsz",
                "nova hallgatsz",
                "nova ébreszto",
                "nova ébresztő",
                "nova indulj",
                "nova kezdj",
                "nova indul",
                "nova aktiv",
                "nova aktiválás",
                "nova aktivizal",
                "nova aktivizál",
                "nova start",
                "nova startolj",
                "nova kezdes",
                "nova kezdés",
                "nova segíts",
                "nova segits",
                "nova segits nekem",
                "nova segíts nekem",
                "nova segítség",
                "nova segitseg",
                "nova figyelj ram",
                "nova figyelj rám",
                "nova hallgass",
                "nova hallgass meg",
                "nova figyelj ide",
                "nova ide",
                "nova itt",
                "nova most",
                "nova hé",
                "nova hej",
                "nova hey",
                "nova hello",
                "nova szia",
                "nova cső",
                "nova cso",
                "nova csá",
                "nova csa",
                "nova te",
                "nova te ott",
                "nova chatbot",
                "nova asszisztens",
                "nova assistant",
                "nova ai",
                "nova rendszer",
                "nova rendszerem",
                "nova program",
                "nova alkalmazas",
                "nova alkalmazás",
                "nova app",
                "nova hang",
                "nova hangasszisztens",
                "nova hang asszisztens",
                "nova voice",
                "nova voice assistant",
                "nova computer",
                "nova gep",
                "nova gép",
                "nova telefon",
                "nova mobil",
                "nova mobile",
                "nova android",
                "nova androidos",
                "nova figyelsz ram",
                "nova figyelsz rám",
                "nova hallasz engem",
                "nova hallod engem",
                "nova hallasz engem",
                "nova itt vagy nekem",
                "nova itt vagy meg",
                "nova ott vagy",
                "nova vagy",
                "nova vagy ott",
                "nova el",
                "nova működj",
                "nova mukodj",
                "nova működj már",
                "nova mukodj mar",
                "nova kelj fel",
                "nova ebredj",
                "nova ébredj",
                "nova ébredj fel",
                "nova ebredj fel",
                "nova ébren vagy",
                "nova ebren vagy",
                "nova felébredtél",
                "nova felebredtel",
                "nova online",
                "nova online vagy",
                "nova rendszer online",
                "nova bekapcsol",
                "nova kapcsold be magad",
                "nova aktiv vagy",
                "nova aktiv vagy",
                "nova készen állsz",
                "nova keszen allsz",
                "nova készen állsz",
                "nova ready",
                "nova ready vagy",
                "nova wake up",
                "nova wakeup",
                "nova wake up now",
                "nova start",
                "nova start up",
                "nova indulas",
                "nova indulás",
                "nova go",
                "nova go go",
                "nova respond",
                "nova response",
                "nova válaszolj",
                "nova valaszolj",
                "nova válasz",
                "nova valasz",
                "nova beszélj",
                "nova beszelj",
                "nova beszélj hozzám",
                "nova beszelj hozzam",
                "nova beszélj velem",
                "nova beszelj velem",
                "nova szólj",
                "nova szolj",
                "nova mondj valamit",
                "nova mondj valamit nekem",
                "nova kommunikáció",
                "nova kommunikacio",
                "nova kapcsolat",
                "nova kapcsolatba",
                "nova kapcsolódj",
                "nova kapcsolodj",
                "nova figyelem",
                "nova figyelem ide",
                "nova figyelmet",
                "nova rám figyelj",
                "nova ram figyelj",
                "nova hallgass ide",
                "nova hallgass ram",
                "nova hallgass rám"
            )

        // Normál "nova" keresés
        if (
            text == "nova" ||
            text.startsWith("nova ") ||
            text.contains(" nova ")
        ) {
            return true
        }

        // Fuzzy wake-word felismerés
        val words =
            text.split(" ")

        for (word in words) {

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

        for (wakeWord in wakeWords) {

            if (
                text.contains(
                    normalize(wakeWord)
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

        var result = text

        result =
            result.replace(
                Regex(
                    "\\bnova\\b"
                ),
                " "
            )

        result =
            result.replace(
                Regex(
                    "\\bnoba\\b"
                ),
                " "
            )

        result =
            result.replace(
                Regex(
                    "\\bnava\\b"
                ),
                " "
            )

        result =
            result.replace(
                Regex(
                    "\\bnora\\b"
                ),
                " "
            )

        result =
            result.replace(
                Regex(
                    "\\bno va\\b"
                ),
                " "
            )

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "NOVA hangasszisztens",
                    NotificationManager.IMPORTANCE_LOW
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

    private fun createNotification(): Notification {

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
                android.R.drawable.ic_btn_speak_now
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

        if (shouldListen && !isSpeaking) {
            restartListening()
        }
    }

    override fun onError(
        error: Int
    ) {

        isListening = false

        if (!shouldListen) {
            return
        }

        restartListening()
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
    // SERVICE LIFECYCLE
    // ============================================================

    override fun onDestroy() {

        shouldListen = false
        isListening = false

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
