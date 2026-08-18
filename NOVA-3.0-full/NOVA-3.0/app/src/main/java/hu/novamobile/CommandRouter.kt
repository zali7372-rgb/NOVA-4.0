package hu.novamobile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.media.AudioManager

object CommandRouter {
    data class Result(val ok:Boolean,val text:String)
    private fun n(s:String)=MainActivity.normalize(s)
    private fun openPackages(ctx:Context, label:String, vararg pkgs:String):String {
        for (p in pkgs) ctx.packageManager.getLaunchIntentForPackage(p)?.let { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(it); return "Megnyitom a $label alkalmazást." }
        return "$label nincs telepítve, vagy ez a telefon más csomagnevet használ."
    }
    private fun settings(ctx:Context, action:String, msg:String):String=try{ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));msg}catch(_:Exception){ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));"Megnyitottam a rendszerbeállításokat."}

    fun execute(ctx:Context, raw:String):Result {
        val s=n(raw)
        if(s.isBlank()) return Result(false,"Nem hallottam parancsot.")
        fun has(vararg x:String)=x.any{s.contains(n(it))}
        return try {
            when {
                has("chrome","krom","crome","google bongeszo","bongeszo","internet") -> Result(true,openPackages(ctx,"Chrome","com.android.chrome","com.google.android.apps.chrome"))
                has("discord","diszkord","diskord","dc") -> Result(true,openPackages(ctx,"Discord","com.discord","com.huawei.himovie.overseas"))
                has("youtube","jutub","youtub") -> Result(true,openPackages(ctx,"YouTube","com.google.android.youtube"))
                has("tiktok","tik tok","tiktoc") -> Result(true,openPackages(ctx,"TikTok","com.zhiliaoapp.musically","com.ss.android.ugc.trill"))
                has("instagram","insta","insta gram") -> Result(true,openPackages(ctx,"Instagram","com.instagram.android"))
                has("facebook","feszbuk") -> Result(true,openPackages(ctx,"Facebook","com.facebook.katana"))
                has("messenger","mesenger") -> Result(true,openPackages(ctx,"Messenger","com.facebook.orca"))
                has("whatsapp","what app","watsapp") -> Result(true,openPackages(ctx,"WhatsApp","com.whatsapp","com.whatsapp.w4b"))
                has("telegram","telegran") -> Result(true,openPackages(ctx,"Telegram","org.telegram.messenger"))
                has("snapchat","snap chat") -> Result(true,openPackages(ctx,"Snapchat","com.snapchat.android"))
                has("spotify","spoty","zene") -> Result(true,openPackages(ctx,"Spotify","com.spotify.music"))
                has("netflix","netfliks","filmek") -> Result(true,openPackages(ctx,"Netflix","com.netflix.mediaclient"))
                has("waze","wejz","navigacio") -> Result(true,openPackages(ctx,"Waze","com.waze"))
                has("gmail","g mail","email","e mail","posta") -> Result(true,openPackages(ctx,"Gmail","com.google.android.gm"))
                has("play aruhaz","play store","playstore","google play","aruhaz") -> Result(true,openPackages(ctx,"Play Áruház","com.android.vending"))
                has("fotok","google fotok","galeria","kepek") -> Result(true,openPackages(ctx,"Google Fotók","com.google.android.apps.photos"))
                has("steam","stim") -> Result(true,openPackages(ctx,"Steam","com.valvesoftware.android.steam.community"))
                has("twitch","twics") -> Result(true,openPackages(ctx,"Twitch","tv.twitch.android.app"))
                has("bolt","boltot","taxi") -> Result(true,openPackages(ctx,"Bolt","ee.mtakso.client"))
                has("uber","ubert","fuvar") -> Result(true,openPackages(ctx,"Uber","com.ubercab"))
                has("wifi","wi fi") -> Result(true,settings(ctx,Settings.ACTION_WIFI_SETTINGS,"Megnyitom a Wi-Fi beállításokat."))
                has("bluetooth","blutoth","blutooth") -> Result(true,settings(ctx,Settings.ACTION_BLUETOOTH_SETTINGS,"Megnyitom a Bluetooth beállításokat."))
                has("kijelzo","kepernyo","display","fenyero") -> Result(true,settings(ctx,Settings.ACTION_DISPLAY_SETTINGS,"Megnyitom a kijelző beállításait."))
                has("helymeghatarozas","gps","lokacio") -> Result(true,settings(ctx,Settings.ACTION_LOCATION_SOURCE_SETTINGS,"Megnyitom a helymeghatározást."))
                has("ertesites","ertesitesek","notification") -> Result(true,settings(ctx,"android.settings.NOTIFICATION_SETTINGS","Megnyitom az értesítési beállításokat."))
                has("vpn") -> Result(true,settings(ctx,"android.settings.VPN_SETTINGS","Megnyitom a VPN beállításokat."))
                has("beallitas","beallitasok","settings") -> Result(true,settings(ctx,Settings.ACTION_SETTINGS,"Megnyitom a beállításokat."))
                has("hangero","hangositsd","hangosabb","hangot fel") -> { val a=ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager; a.adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_RAISE,AudioManager.FLAG_SHOW_UI); Result(true,"Feljebb vettem a hangerőt.") }
                has("hangot le","halkitsd","halkabb") -> { val a=ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager; a.adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_LOWER,AudioManager.FLAG_SHOW_UI); Result(true,"Lejjebb vettem a hangerőt.") }
                has("telefon","tarhely","storage") -> Result(true,settings(ctx,Settings.ACTION_INTERNAL_STORAGE_SETTINGS,"Megnyitom a tárhelyet."))
                has("maps","terkep","terkepek") -> {ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));Result(true,"Megnyitom a térképet.")}
                else -> Result(false,"Ezt nem ismertem fel. Próbáld például: Nova, nyisd meg a Chrome-ot.")
            }
        } catch(e:Exception){Result(false,"Nem sikerült végrehajtanom a parancsot.")}
    }
}
