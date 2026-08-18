package hu.novamobile

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var transcript: TextView
    private lateinit var listen: Button
    private val audioRequest = 10
    private val notificationRequest = 11

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.statusText)
        transcript = findViewById(R.id.transcriptText)
        listen = findViewById(R.id.listenButton)

        listen.setOnClickListener {
            if (hasAudio()) startNova() else requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), audioRequest)
        }
        findViewById<Button>(R.id.settingsButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), notificationRequest)
        }
    }

    private fun hasAudio() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun startNova() {
        try {
            val i = Intent(this, NovaVoiceService::class.java).setAction(NovaVoiceService.ACTION_START)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
            status.text = "NOVA aktív • mondd: Nova..."
            listen.text = "NOVA FUT"
        } catch (e: Exception) { status.text = "Nem sikerült elindítani: ${e.message ?: "ismeretlen hiba"}" }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grants: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grants)
        if (requestCode == audioRequest && grants.firstOrNull() == PackageManager.PERMISSION_GRANTED) startNova()
        else if (requestCode == audioRequest) status.text = "A mikrofonengedély kell a NOVA-hoz."
    }

    companion object {
        fun normalize(text: String): String = java.text.Normalizer.normalize(text.lowercase(java.util.Locale("hu", "HU")), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "").replace("ő", "o").replace("ű", "u")
            .replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
    }
}
