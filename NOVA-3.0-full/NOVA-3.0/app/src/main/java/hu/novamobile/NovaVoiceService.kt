package hu.novamobile

import android.app.*
import android.content.*
import android.os.*
import android.speech.*
import android.speech.tts.TextToSpeech
import java.util.Locale

class NovaVoiceService: Service(), TextToSpeech.OnInitListener {
    companion object { const val ACTION_START="hu.novamobile.START_VOICE"; const val ACTION_STOP="hu.novamobile.STOP_VOICE"; private const val CHANNEL="nova_voice" }
    private var recognizer:SpeechRecognizer?=null
    private var tts:TextToSpeech?=null
    private var running=false
    private var listening=false
    private val handler=Handler(Looper.getMainLooper())

    override fun onCreate(){super.onCreate(); createChannel(); startForeground(1001,notification()); tts=TextToSpeech(this,this); createRecognizer()}
    override fun onStartCommand(i:Intent?,flags:Int,id:Int):Int { when(i?.action){ACTION_START->startNova();ACTION_STOP->stopNova()}; return START_NOT_STICKY }

    private fun createChannel(){if(Build.VERSION.SDK_INT>=26)(getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(NotificationChannel(CHANNEL,"NOVA hangvezérlés",NotificationManager.IMPORTANCE_LOW))}
    private fun notification():Notification { val pi=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT); return if(Build.VERSION.SDK_INT>=26) Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("NOVA aktív").setContentText("Hangvezérlés fut").setOngoing(true).setContentIntent(pi).build() else @Suppress("DEPRECATION") Notification.Builder(this).setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("NOVA aktív").setContentText("Hangvezérlés fut").setOngoing(true).build() }
    override fun onInit(status:Int){if(status==TextToSpeech.SUCCESS){tts?.setLanguage(Locale("hu","HU"));tts?.setSpeechRate(1f)}}
    private fun speak(s:String){if(s.isNotBlank()) tts?.speak(s,TextToSpeech.QUEUE_FLUSH,null,"nova")}

    private fun createRecognizer(){ recognizer?.destroy(); recognizer=SpeechRecognizer.createSpeechRecognizer(this); recognizer?.setRecognitionListener(object:RecognitionListener{
        override fun onReadyForSpeech(p:Bundle?){listening=true}; override fun onBeginningOfSpeech(){}; override fun onRmsChanged(v:Float){}; override fun onBufferReceived(b:ByteArray?){}; override fun onEndOfSpeech(){listening=false}; override fun onPartialResults(b:Bundle?){}; override fun onEvent(t:Int,b:Bundle?){}
        override fun onError(e:Int){listening=false;if(running) restart(700)}
        override fun onResults(b:Bundle?){listening=false; val h=b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty(); if(h.isNotBlank()) handle(h); if(running) restart(400)}
    }) }

    private fun startNova(){if(!SpeechRecognizer.isRecognitionAvailable(this)){speak("A beszédfelismerés nem érhető el.");return};running=true;restart(250)}
    private fun restart(delay:Long){handler.removeCallbacksAndMessages(null); if(!running)return; handler.postDelayed({if(!running)return@postDelayed; try{recognizer?.cancel(); val i=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{putExtra(RecognizerIntent.EXTRA_LANGUAGE,"hu-HU");putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false);putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,3)}; recognizer?.startListening(i)}catch(_:Exception){if(running)restart(1200)}},delay)}
    private fun handle(raw:String){val s=MainActivity.normalize(raw); val wake=Regex("\\bnova\\b").containsMatchIn(s); if(!wake)return; val cmd=s.replace(Regex("\\bnova\\b"),"").trim(); if(cmd.isBlank()){speak("Igen?");return}; val r=CommandRouter.execute(this,cmd); speak(r.text)}
    private fun stopNova(){running=false;handler.removeCallbacksAndMessages(null);recognizer?.cancel();tts?.stop();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()}
    override fun onDestroy(){running=false;handler.removeCallbacksAndMessages(null);recognizer?.cancel();recognizer?.destroy();recognizer=null;tts?.stop();tts?.shutdown();tts=null;super.onDestroy()}
    override fun onBind(i:Intent?):IBinder?=null
}
