package hu.novamobile

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.Settings
import kotlin.math.max
import kotlin.math.min

object CommandRouter {

    enum class ResultType {
        EXECUTED,
        UNKNOWN,
        AMBIGUOUS,
        CLARIFICATION
    }

    data class CommandResult(
        val type: ResultType,
        val response: String,
        val options: List<String> = emptyList()
    )

    private data class Command(
        val id: String,
        val label: String,
        val aliases: List<String>,
        val action: (Context) -> String
    )

    private data class Match(
        val command: Command,
        val score: Double
    )

    // ============================================================
    // NORMALIZÁLÁS
    // ============================================================

    private fun normalize(input: String): String {

        return MainActivity
            .normalize(input)
            .lowercase()
            .replace("alkalmazást", "app")
            .replace("alkalmazas", "app")
            .replace("alkalmazás", "app")
            .replace("alkalmazast", "app")
            .replace("programot", "app")
            .replace("program", "app")
            .replace("alkalmazásomat", "app")
            .replace("alkalmazasomat", "app")
            .replace(Regex("[^a-z0-9áéíóöőúüű ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun words(text: String): List<String> {
        return normalize(text)
            .split(" ")
            .filter { it.length >= 2 }
    }

    // ============================================================
    // LEVENSHTEIN
    // ============================================================

    private fun levenshtein(
        a: String,
        b: String
    ): Int {

        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var previous =
            IntArray(b.length + 1) { it }

        for (i in a.indices) {

            val current =
                IntArray(b.length + 1)

            current[0] = i + 1

            for (j in b.indices) {

                val cost =
                    if (a[i] == b[j]) 0 else 1

                current[j + 1] =
                    min(
                        min(
                            current[j] + 1,
                            previous[j + 1] + 1
                        ),
                        previous[j] + cost
                    )
            }

            previous = current
        }

        return previous[b.length]
    }

    // ============================================================
    // FUZZY
    // ============================================================

    private fun fuzzySimilarity(
        a: String,
        b: String
    ): Double {

        val aa = normalize(a)
        val bb = normalize(b)

        if (aa.isEmpty() || bb.isEmpty()) {
            return 0.0
        }

        if (aa == bb) {
            return 1.0
        }

        if (aa.contains(bb)) {
            return 0.96
        }

        if (bb.contains(aa) && aa.length >= 3) {
            return 0.94
        }

        val distance =
            levenshtein(aa, bb)

        val longest =
            max(
                aa.length,
                bb.length
            )

        return (
            1.0 -
                distance.toDouble() /
                longest.toDouble()
            ).coerceIn(0.0, 1.0)
    }

    private fun tokenSimilarity(
        input: String,
        alias: String
    ): Double {

        val inputWords =
            words(input)

        val aliasWords =
            words(alias)

        if (
            inputWords.isEmpty() ||
            aliasWords.isEmpty()
        ) {
            return fuzzySimilarity(
                input,
                alias
            )
        }

        var total = 0.0
        var matched = 0

        for (aliasWord in aliasWords) {

            var best = 0.0

            for (inputWord in inputWords) {

                best =
                    max(
                        best,
                        fuzzySimilarity(
                            inputWord,
                            aliasWord
                        )
                    )
            }

            total += best

            if (best >= 0.52) {
                matched++
            }
        }

        val average =
            total / aliasWords.size

        val coverage =
            matched.toDouble() /
                aliasWords.size

        return (
            average * 0.70 +
                coverage * 0.30
            ).coerceIn(0.0, 1.0)
    }

    private fun commandSimilarity(
        input: String,
        alias: String
    ): Double {

        val a = normalize(input)
        val b = normalize(alias)

        if (a == b) {
            return 1.0
        }

        if (a.contains(b)) {
            return 0.98
        }

        if (b.contains(a) && a.length >= 3) {
            return 0.94
        }

        return max(
            fuzzySimilarity(a, b),
            tokenSimilarity(a, b)
        )
    }

    // ============================================================
    // 100+ VARIÁCIÓ AUTOMATIKUSAN
    // ============================================================

    private fun generateAliases(
        vararg names: String
    ): List<String> {

        val result =
            linkedSetOf<String>()

        val prefixes =
            listOf(
                "",
                "nyisd meg",
                "nyisd ki",
                "nyisd fel",
                "inditsd el",
                "inditsd",
                "nyisd meg nekem",
                "nyisd ki nekem",
                "nyisd fel nekem",
                "mutasd",
                "mutasd meg",
                "hozd elo",
                "hozd elő",
                "menj ide",
                "menj a",
                "menjunk ide",
                "ugorj ide",
                "ugorj a",
                "vigyel ide",
                "nyomd meg",
                "kapcsold be",
                "induljon",
                "inditsd be",
                "tedd meg",
                "nyisd",
                "indits",
                "meg tudod nyitni",
                "meg tudnad nyitni",
                "keresd meg",
                "keresd ki",
                "hozd be",
                "hozd elo nekem"
            )

        val suffixes =
            listOf(
                "",
                "app",
                "alkalmazas",
                "alkalmazást",
                "program",
                "alkalmazast",
                "nekem",
                "most",
                "kerlek",
                "legyszi"
            )

        val typoVariants =
            listOf(
                "a",
                "o",
                "e",
                "i",
                "u",
                "y"
            )

        for (name in names) {

            val clean =
                normalize(name)

            result += clean

            for (prefix in prefixes) {

                if (prefix.isBlank()) {
                    result += clean
                } else {
                    result +=
                        "$prefix $clean"
                }
            }

            for (suffix in suffixes) {

                if (suffix.isNotBlank()) {
                    result +=
                        "$clean $suffix"
                }
            }

            for (prefix in prefixes) {
                for (suffix in suffixes) {

                    if (
                        prefix.isNotBlank() &&
                        suffix.isNotBlank()
                    ) {

                        result +=
                            "$prefix $clean $suffix"
                    }
                }
            }

            for (variant in typoVariants) {

                if (clean.length >= 4) {

                    result +=
                        clean.dropLast(1) + variant

                    result +=
                        clean.drop(1) + variant
                }
            }
        }

        return result.toList()
    }

    // ============================================================
    // TELEPÍTETT APP KERESÉS
    // ============================================================

    private fun findInstalledApp(
        context: Context,
        requestedName: String,
        knownPackages: List<String>
    ): Intent? {

        val pm =
            context.packageManager

        // --------------------------------------------------------
        // 1. Ismert package-ek
        // --------------------------------------------------------

        for (packageName in knownPackages) {

            try {

                val intent =
                    pm.getLaunchIntentForPackage(
                        packageName
                    )

                if (intent != null) {
                    return intent
                }

            } catch (_: Exception) {
            }
        }

        // --------------------------------------------------------
        // 2. TELEFONON TALÁLHATÓ ÖSSZES LAUNCHER APP
        // --------------------------------------------------------

        try {

            val launcherIntent =
                Intent(
                    Intent.ACTION_MAIN
                ).apply {
                    addCategory(
                        Intent.CATEGORY_LAUNCHER
                    )
                }

            val apps =
                pm.queryIntentActivities(
                    launcherIntent,
                    PackageManager.MATCH_ALL
                )

            val wanted =
                normalize(requestedName)

            var bestIntent: Intent? = null
            var bestScore = 0.0

            for (info in apps) {

                val appName =
                    info
                        .loadLabel(pm)
                        .toString()

                val packageName =
                    info.activityInfo.packageName

                val labelScore =
                    commandSimilarity(
                        wanted,
                        normalize(appName)
                    )

                val packageScore =
                    commandSimilarity(
                        wanted,
                        normalize(
                            packageName
                                .substringAfterLast(".")
                                .replace(
                                    Regex("[^A-Za-z0-9 ]"),
                                    " "
                                )
                        )
                    )

                val score =
                    max(
                        labelScore,
                        packageScore
                    )

                if (
                    score > bestScore
                ) {

                    bestScore = score

                    bestIntent =
                        pm.getLaunchIntentForPackage(
                            packageName
                        )
                }
            }

            if (
                bestIntent != null &&
                bestScore >= 0.58
            ) {
                return bestIntent
            }

        } catch (_: Exception) {
        }

        return null
    }

    // ============================================================
    // APP MEGNYITÁSA
    // ============================================================

    private fun openApp(
        label: String,
        vararg packageNames: String
    ): (Context) -> String = { context ->

        try {

            val intent =
                findInstalledApp(
                    context,
                    label,
                    packageNames.toList()
                )

            if (intent == null) {

                "$label alkalmazást nem találtam a telefonon."

            } else {

                intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                )

                context.startActivity(intent)

                "Megnyitom a $label alkalmazást."
            }

        } catch (_: Exception) {

            "Nem sikerült megnyitnom a $label alkalmazást."
        }
    }

    // ============================================================
    // BEÁLLÍTÁS
    // ============================================================

    private fun openSettings(
        action: String,
        response: String
    ): (Context) -> String = { context ->

        try {

            context.startActivity(
                Intent(action).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                    )
                }
            )

            response

        } catch (_: Exception) {

            try {

                context.startActivity(
                    Intent(
                        Settings.ACTION_SETTINGS
                    ).apply {
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK
                        )
                    }
                )

                "Megnyitottam a rendszerbeállításokat."

            } catch (_: Exception) {

                "Nem sikerült megnyitnom a beállításokat."
            }
        }
    }

    // ============================================================
    // PARANCSOK
    // ============================================================

    private val commands =
        listOf(

            // ----------------------------------------------------
            // WIFI
            // ----------------------------------------------------

            Command(
                "wifi",
                "Wi-Fi",
                generateAliases(
                    "wifi",
                    "wi fi",
                    "wifit",
                    "wifi beallitas",
                    "wifi beallitasok",
                    "vezetek nelkuli halozat",
                    "vezetek nelkuli kapcsolat",
                    "internet wifi",
                    "wifi kapcsolat",
                    "wifi menu",
                    "wifi oldal"
                ),
                openSettings(
                    Settings.ACTION_WIFI_SETTINGS,
                    "Megnyitom a Wi-Fi beállításokat."
                )
            ),

            // ----------------------------------------------------
            // BLUETOOTH
            // ----------------------------------------------------

            Command(
                "bluetooth",
                "Bluetooth",
                generateAliases(
                    "bluetooth",
                    "blutoth",
                    "blutooth",
                    "blu tut",
                    "bluetooth beallitas",
                    "bluetooth beallitasok",
                    "bluetooth kapcsolat",
                    "bluetooth menu"
                ),
                openSettings(
                    Settings.ACTION_BLUETOOTH_SETTINGS,
                    "Megnyitom a Bluetooth beállításokat."
                )
            ),

            // ----------------------------------------------------
            // KIJELZŐ
            // ----------------------------------------------------

            Command(
                "display",
                "kijelző",
                generateAliases(
                    "kijelzo",
                    "kepernyo",
                    "display",
                    "monitor",
                    "fenyero",
                    "fenyero beallitas",
                    "vilagossag",
                    "kepernyo beallitas",
                    "kijelzo beallitas"
                ),
                openSettings(
                    Settings.ACTION_DISPLAY_SETTINGS,
                    "Megnyitom a kijelző beállításait."
                )
            ),

            // ----------------------------------------------------
            // HELY
            // ----------------------------------------------------

            Command(
                "location",
                "helymeghatározás",
                generateAliases(
                    "gps",
                    "helymeghatarozas",
                    "helyadatok",
                    "helyzet",
                    "lokacio",
                    "location",
                    "gps beallitas",
                    "helymeghatarozas beallitas"
                ),
                openSettings(
                    Settings.ACTION_LOCATION_SOURCE_SETTINGS,
                    "Megnyitom a helymeghatározás beállításait."
                )
            ),

            // ----------------------------------------------------
            // ÉRTESÍTÉSEK
            // ----------------------------------------------------

            Command(
                "notifications",
                "értesítések",
                generateAliases(
                    "ertesites",
                    "ertesitesek",
                    "jelzesek",
                    "notifikaciok",
                    "notification",
                    "notifications",
                    "ertesitesi beallitas",
                    "ertesitesi beallitasok"
                ),
                openSettings(
                    "android.settings.NOTIFICATION_SETTINGS",
                    "Megnyitom az értesítési beállításokat."
                )
            ),

            // ----------------------------------------------------
            // VPN
            // ----------------------------------------------------

            Command(
                "vpn",
                "VPN",
                generateAliases(
                    "vpn",
                    "vpn beallitas",
                    "vpn beallitasok",
                    "virtualis maganhalozat",
                    "virtualis halozat"
                ),
                openSettings(
                    "android.settings.VPN_SETTINGS",
                    "Megnyitom a VPN beállításokat."
                )
            ),

            // ----------------------------------------------------
            // BEÁLLÍTÁSOK
            // ----------------------------------------------------

            Command(
                "settings",
                "rendszerbeállítások",
                generateAliases(
                    "beallitas",
                    "beallitasok",
                    "telefon beallitas",
                    "telefon beallitasok",
                    "rendszerbeallitas",
                    "rendszerbeallitasok",
                    "settings",
                    "setting",
                    "telefon settings"
                ),
                openSettings(
                    Settings.ACTION_SETTINGS,
                    "Megnyitom a rendszerbeállításokat."
                )
            ),

            // ----------------------------------------------------
            // TÁRHELY
            // ----------------------------------------------------

            Command(
                "storage",
                "tárhely",
                generateAliases(
                    "tarhely",
                    "tarhely informacio",
                    "tarhely beallitas",
                    "tarhely beallitasok",
                    "belso memoria",
                    "storage",
                    "memoria",
                    "hely a telefonon"
                ),
                openSettings(
                    Settings.ACTION_INTERNAL_STORAGE_SETTINGS,
                    "Megnyitom a tárhely beállításait."
                )
            ),

            // ----------------------------------------------------
            // HOTSPOT
            // ----------------------------------------------------

            Command(
                "hotspot",
                "mobil hotspot",
                generateAliases(
                    "hotspot",
                    "hot spot",
                    "mobil hotspot",
                    "wifi hotspot",
                    "internet megosztas",
                    "net megosztas",
                    "internetmegosztas"
                ),
                openSettings(
                    "android.settings.TETHER_SETTINGS",
                    "Megnyitom a hotspot beállításait."
                )
            ),

            // ----------------------------------------------------
            // YOUTUBE
            // ----------------------------------------------------

            Command(
                "youtube",
                "YouTube",
                generateAliases(
                    "youtube",
                    "youtub",
                    "jutub",
                    "jutube",
                    "youtube app",
                    "youtube video",
                    "videok",
                    "videók",
                    "youtube alkalmazas",
                    "youtube program"
                ),
                openApp(
                    "YouTube",
                    "com.google.android.youtube",
                    "com.google.android.youtube.tv"
                )
            ),

            // ----------------------------------------------------
            // CHROME
            // ----------------------------------------------------

            Command(
                "chrome",
                "Chrome",
                generateAliases(
                    "chrome",
                    "chrom",
                    "crome",
                    "krom",
                    "google chrome",
                    "chrome bongeszo",
                    "google bongeszo",
                    "bongeszo",
                    "internet",
                    "internet bongeszo",
                    "web bongeszo"
                ),
                openApp(
                    "Chrome",
                    "com.android.chrome",
                    "com.chrome.beta",
                    "com.chrome.dev",
                    "com.chrome.canary"
                )
            ),

            // ----------------------------------------------------
            // DISCORD
            // ----------------------------------------------------

            Command(
                "discord",
                "Discord",
                generateAliases(
                    "discord",
                    "diszkord",
                    "diskord",
                    "disscord",
                    "discord app",
                    "discord alkalmazas",
                    "discordot",
                    "dc",
                    "d c",
                    "discord chat",
                    "diszkort",
                    "discort"
                ),
                openApp(
                    "Discord",
                    "com.discord",
                    "com.discord.android"
                )
            ),

            // ----------------------------------------------------
            // TIKTOK
            // ----------------------------------------------------

            Command(
                "tiktok",
                "TikTok",
                generateAliases(
                    "tiktok",
                    "tik tok",
                    "tiktokk",
                    "tiktoc",
                    "tiktok app",
                    "tiktok video",
                    "rovid videok",
                    "short videok"
                ),
                openApp(
                    "TikTok",
                    "com.zhiliaoapp.musically",
                    "com.ss.android.ugc.trill"
                )
            ),

            // ----------------------------------------------------
            // INSTAGRAM
            // ----------------------------------------------------

            Command(
                "instagram",
                "Instagram",
                generateAliases(
                    "instagram",
                    "insta",
                    "insta gram",
                    "instagrm",
                    "instagram app",
                    "instat",
                    "insta app"
                ),
                openApp(
                    "Instagram",
                    "com.instagram.android"
                )
            ),

            // ----------------------------------------------------
            // FACEBOOK
            // ----------------------------------------------------

            Command(
                "facebook",
                "Facebook",
                generateAliases(
                    "facebook",
                    "facebok",
                    "facebook app",
                    "feszbuk",
                    "face",
                    "facebook alkalmazas"
                ),
                openApp(
                    "Facebook",
                    "com.facebook.katana"
                )
            ),

            // ----------------------------------------------------
            // MESSENGER
            // ----------------------------------------------------

            Command(
                "messenger",
                "Messenger",
                generateAliases(
                    "messenger",
                    "mesenger",
                    "messenger app",
                    "uzenetek",
                    "uzenetek messenger",
                    "chat",
                    "messenger chat"
                ),
                openApp(
                    "Messenger",
                    "com.facebook.orca"
                )
            ),

            // ----------------------------------------------------
            // WHATSAPP
            // ----------------------------------------------------

            Command(
                "whatsapp",
                "WhatsApp",
                generateAliases(
                    "whatsapp",
                    "what app",
                    "whats app",
                    "watsapp",
                    "whatsup",
                    "whatsapp app"
                ),
                openApp(
                    "WhatsApp",
                    "com.whatsapp",
                    "com.whatsapp.w4b"
                )
            ),

            // ----------------------------------------------------
            // TELEGRAM
            // ----------------------------------------------------

            Command(
                "telegram",
                "Telegram",
                generateAliases(
                    "telegram",
                    "telegran",
                    "telegram app",
                    "telegram alkalmazas",
                    "telegram chat"
                ),
                openApp(
                    "Telegram",
                    "org.telegram.messenger"
                )
            ),

            // ----------------------------------------------------
            // SNAPCHAT
            // ----------------------------------------------------

            Command(
                "snapchat",
                "Snapchat",
                generateAliases(
                    "snapchat",
                    "snap chat",
                    "snap",
                    "snapcsat",
                    "snapchat app"
                ),
                openApp(
                    "Snapchat",
                    "com.snapchat.android"
                )
            ),

            // ----------------------------------------------------
            // X
            // ----------------------------------------------------

            Command(
                "x",
                "X",
                generateAliases(
                    "twitter",
                    "x twitter",
                    "twitter app",
                    "eksz",
                    "x app"
                ),
                openApp(
                    "X",
                    "com.twitter.android"
                )
            ),

            // ----------------------------------------------------
            // REDDIT
            // ----------------------------------------------------

            Command(
                "reddit",
                "Reddit",
                generateAliases(
                    "reddit",
                    "red it",
                    "reddit app",
                    "redditet"
                ),
                openApp(
                    "Reddit",
                    "com.reddit.frontpage"
                )
            ),

            // ----------------------------------------------------
            // SPOTIFY
            // ----------------------------------------------------

            Command(
                "spotify",
                "Spotify",
                generateAliases(
                    "spotify",
                    "spoty",
                    "spotify app",
                    "zene",
                    "zenet",
                    "zenelejatszo",
                    "zene lejatszo"
                ),
                openApp(
                    "Spotify",
                    "com.spotify.music"
                )
            ),

            // ----------------------------------------------------
            // STEAM
            // ----------------------------------------------------

            Command(
                "steam",
                "Steam",
                generateAliases(
                    "steam",
                    "stim",
                    "steam app",
                    "steam mobil"
                ),
                openApp(
                    "Steam",
                    "com.valvesoftware.android.steam.community"
                )
            ),

            // ----------------------------------------------------
            // TWITCH
            // ----------------------------------------------------

            Command(
                "twitch",
                "Twitch",
                generateAliases(
                    "twitch",
                    "tvis",
                    "twics",
                    "twitch app",
                    "streamek",
                    "stream"
                ),
                openApp(
                    "Twitch",
                    "tv.twitch.android.app"
                )
            ),

            // ----------------------------------------------------
            // NETFLIX
            // ----------------------------------------------------

            Command(
                "netflix",
                "Netflix",
                generateAliases(
                    "netflix",
                    "netfliks",
                    "netfli",
                    "netflix app",
                    "filmek",
                    "sorozatok"
                ),
                openApp(
                    "Netflix",
                    "com.netflix.mediaclient"
                )
            ),

            // ----------------------------------------------------
            // WAZE
            // ----------------------------------------------------

            Command(
                "waze",
                "Waze",
                generateAliases(
                    "waze",
                    "wejz",
                    "waze app",
                    "navigacio",
                    "utvonal"
                ),
                openApp(
                    "Waze",
                    "com.waze"
                )
            ),

            // ----------------------------------------------------
            // UBER
            // ----------------------------------------------------

            Command(
                "uber",
                "Uber",
                generateAliases(
                    "uber",
                    "uber app",
                    "ubert",
                    "fuvar",
                    "taxi uber"
                ),
                openApp(
                    "Uber",
                    "com.ubercab"
                )
            ),

            // ----------------------------------------------------
            // BOLT
            // ----------------------------------------------------

            Command(
                "bolt",
                "Bolt",
                generateAliases(
                    "bolt",
                    "bolt app",
                    "boltot",
                    "taxi",
                    "bolt taxi"
                ),
                openApp(
                    "Bolt",
                    "ee.mtakso.client"
                )
            ),

            // ----------------------------------------------------
            // GMAIL
            // ----------------------------------------------------

            Command(
                "gmail",
                "Gmail",
                generateAliases(
                    "gmail",
                    "g mail",
                    "gmail app",
                    "email",
                    "e mail",
                    "levelek",
                    "posta",
                    "gmail posta"
                ),
                openApp(
                    "Gmail",
                    "com.google.android.gm"
                )
            ),

            // ----------------------------------------------------
            // PLAY ÁRUHÁZ
            // ----------------------------------------------------

            Command(
                "playstore",
                "Play Áruház",
                generateAliases(
                    "play aruhaz",
                    "play store",
                    "playstore",
                    "google play",
                    "google play store",
                    "aruhaz",
                    "app aruhaz",
                    "alkalmazasbolt"
                ),
                openApp(
                    "Play Áruház",
                    "com.android.vending"
                )
            ),

            // ----------------------------------------------------
            // GOOGLE FOTÓK
            // ----------------------------------------------------

            Command(
                "photos",
                "Google Fotók",
                generateAliases(
                    "fotok",
                    "google fotok",
                    "kepek",
                    "galeria",
                    "photos",
                    "google photos",
                    "fotok app"
                ),
                openApp(
                    "Google Fotók",
                    "com.google.android.apps.photos"
                )
            ),

            // ----------------------------------------------------
            // HANGERŐ
            // ----------------------------------------------------

            Command(
                "volume",
                "hangerő",
                generateAliases(
                    "hangero",
                    "hangerő",
                    "hang",
                    "hangositsd",
                    "hangosits",
                    "hangot fel",
                    "hangosabb",
                    "noveld a hangot",
                    "hangero fel"
                )
            ) { context ->

                try {

                    val audio =
                        context.getSystemService(
                            Context.AUDIO_SERVICE
                        ) as AudioManager

                    audio.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_RAISE,
                        AudioManager.FLAG_SHOW_UI
                    )

                    "Feljebb vettem a hangerőt."

                } catch (_: Exception) {

                    "Nem sikerült módosítanom a hangerőt."
                }
            },

            // ----------------------------------------------------
            // AKKU
            // ----------------------------------------------------

            Command(
                "battery",
                "akkumulátor",
                generateAliases(
                    "akku",
                    "akkumulator",
                    "akku szint",
                    "akku allapot",
                    "akkumulator allapot",
                    "toltottseg",
                    "hany szazalek az akku",
                    "mennyi az akku",
                    "mennyi akkum van",
                    "akku mennyi"
                )
            ) { context ->

                try {

                    val battery =
                        context.getSystemService(
                            Context.BATTERY_SERVICE
                        ) as BatteryManager

                    val level =
                        battery.getIntProperty(
                            BatteryManager
                                .BATTERY_PROPERTY_CAPACITY
                        )

                    if (level >= 0) {

                        "Az akkumulátor töltöttsége $level százalék."

                    } else {

                        "Nem tudtam lekérni az akkumulátor töltöttségét."
                    }

                } catch (_: Exception) {

                    "Nem tudtam lekérni az akkumulátor töltöttségét."
                }
            },

            // ----------------------------------------------------
            // FÁJLOK
            // ----------------------------------------------------

            Command(
                "files",
                "fájlkezelő",
                generateAliases(
                    "fajlok",
                    "fajlkezelo",
                    "dokumentumok",
                    "dokumentum",
                    "fileok",
                    "file kezelo",
                    "file manager",
                    "mappak",
                    "mappák",
                    "fajlok megnyitasa"
                )
            ) { context ->

                try {

                    val intent =
                        Intent(
                            Intent.ACTION_OPEN_DOCUMENT
                        ).apply {

                            type = "*/*"

                            addCategory(
                                Intent.CATEGORY_OPENABLE
                            )

                            addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK
                            )
                        }

                    context.startActivity(intent)

                    "Megnyitom a fájlkezelőt."

                } catch (_: Exception) {

                    "Nem sikerült megnyitnom a fájlkezelőt."
                }
            },

            // ----------------------------------------------------
            // SZÁMOLÓGÉP
            // ----------------------------------------------------

            Command(
                "calculator",
                "számológép",
                generateAliases(
                    "szamologep",
                    "kalkulator",
                    "calculator",
                    "matek",
                    "szamolni",
                    "szamologep megnyitasa"
                )
            ) { context ->

                try {

                    val intent =
                        Intent(
                            Intent.ACTION_MAIN
                        ).apply {

                            addCategory(
                                "android.intent.category.APP_CALCULATOR"
                            )

                            addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK
                            )
                        }

                    context.startActivity(intent)

                    "Megnyitom a számológépet."

                } catch (_: Exception) {

                    "Nem találtam számológép alkalmazást."
                }
            },

            // ----------------------------------------------------
            // ÓRA
            // ----------------------------------------------------

            Command(
                "clock",
                "óra",
                generateAliases(
                    "ora",
                    "ebreszto",
                    "ebresztoora",
                    "riaszto",
                    "alarm",
                    "clock",
                    "ora app"
                )
            ) { context ->

                try {

                    val intent =
                        Intent(
                            "android.intent.action.SHOW_ALARMS"
                        ).apply {

                            addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK
                            )
                        }

                    context.startActivity(intent)

                    "Megnyitom az órát és az ébresztőket."

                } catch (_: Exception) {

                    "Nem sikerült megnyitnom az órát."
                }
            },

            // ----------------------------------------------------
            // NAPTÁR
            // ----------------------------------------------------

            Command(
                "calendar",
                "naptár",
                generateAliases(
                    "naptar",
                    "calendar",
                    "esemenyek",
                    "programok",
                    "talalkozok",
                    "naptar app",
                    "naptar megnyitasa"
                )
            ) { context ->

                try {

                    val intent =
                        Intent(
                            Intent.ACTION_MAIN
                        ).apply {

                            addCategory(
                                "android.intent.category.APP_CALENDAR"
                            )

                            addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK
                            )
                        }

                    context.startActivity(intent)

                    "Megnyitom a naptárat."

                } catch (_: Exception) {

                    "Nem sikerült megnyitnom a naptárat."
                }
            },

            // ----------------------------------------------------
            // TÉRKÉP
            // ----------------------------------------------------

            Command(
                "maps",
                "Google Térkép",
                generateAliases(
                    "google maps",
                    "google map",
                    "maps",
                    "map",
                    "terkep",
                    "terkepek",
                    "navigacio",
                    "google terkep",
                    "terkep app"
                )
            ) { context ->

                try {

                    val intent =
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("geo:0,0?q=")
                        ).apply {

                            addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK
                            )
                        }

                    context.startActivity(intent)

                    "Megnyitom a Google Térképet."

                } catch (_: Exception) {

                    "Nem sikerült megnyitnom a térképet."
                }
            }
        )

    // ============================================================
    // MATCH
    // ============================================================

    private fun findMatches(
        input: String
    ): List<Match> {

        val result =
            mutableListOf<Match>()

        for (command in commands) {

            var best = 0.0

            for (alias in command.aliases) {

                best =
                    max(
                        best,
                        commandSimilarity(
                            input,
                            alias
                        )
                    )
            }

            if (best >= 0.43) {

                result +=
                    Match(
                        command,
                        best
                    )
            }
        }

        return result
            .sortedByDescending {
                it.score
            }
    }

    // ============================================================
    // EXECUTE
    // ============================================================

    fun execute(
        context: Context,
        utterance: String
    ): CommandResult {

        val input =
            normalize(utterance)

        if (input.isBlank()) {

            return CommandResult(
                ResultType.UNKNOWN,
                "Nem hallottam parancsot."
            )
        }

        val matches =
            findMatches(input)

        if (matches.isEmpty()) {

            return CommandResult(
                ResultType.UNKNOWN,
                "Ezt még nem ismertem fel. Mondd másképp."
            )
        }

        val best =
            matches.first()

        // Erős egyezés
        if (best.score >= 0.80) {

            return CommandResult(
                ResultType.EXECUTED,
                best.command.action(context)
            )
        }

        // Két hasonló parancs
        if (matches.size >= 2) {

            val second =
                matches[1]

            if (
                second.score >= 0.63 &&
                best.score - second.score <= 0.09
            ) {

                return CommandResult(
                    ResultType.AMBIGUOUS,
                    "Nem vagyok teljesen biztos. Melyiket szeretnéd?",
                    listOf(
                        best.command.label,
                        second.command.label
                    )
                )
            }
        }

        // Közepesen erős egyezés
        if (best.score >= 0.60) {

            return CommandResult(
                ResultType.CLARIFICATION,
                "Erre gondoltál: ${best.command.label}?",
                listOf(
                    best.command.label
                )
            )
        }

        return CommandResult(
            ResultType.UNKNOWN,
            "Nem vagyok elég biztos abban, mit mondtál."
        )
    }
}
