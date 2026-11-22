package com.adeshayat.app.audio

import android.content.Context
import com.adeshayat.app.data.Verse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

// jsDelivr CDN সরাসরি GitHub repo থেকে ফাইল দেয় (GitHub Pages ডিপ্লয়ের ওপর নির্ভর করে না)
private const val BN_AUDIO_BASE = "https://cdn.jsdelivr.net/gh/infinitytechcare/adesh-ayat-app@main/docs/audio/"

/** অডিওর উৎস: সার্ভারের ফোল্ডার আর অ্যাপের ভেতরের ক্যাশ ফোল্ডার। */
enum class AudioSource(
    val label: String,
    val baseUrl: String,
    val cacheDir: String,
    val isArabic: Boolean,
    /** সেটিংসে নামের নিচে দেখানোর তথ্য */
    val info: String = "",
) {
    ABDUL_BASIT_MURATTAL(
        label = "আব্দুল বাসিত (মুরাত্তাল)",
        baseUrl = "https://everyayah.com/data/Abdul_Basit_Murattal_64kbps/",
        cacheDir = "audio_abdul_basit_murattal",
        isArabic = true,
        info = "everyayah.com · ৬৪ kbps",
    ),
    ABDUL_SAMAD(
        label = "আব্দুল বাসিত আব্দুস সামাদ",
        baseUrl = "https://everyayah.com/data/AbdulSamad_64kbps_QuranExplorer.Com/",
        cacheDir = "audio_abdulsamad",
        isArabic = true,
        info = "everyayah.com · ৬৪ kbps",
    ),
    ABDUL_BASIT_MUJAWWAD(
        label = "আব্দুল বাসিত (মুজাওয়াদ)",
        baseUrl = "https://everyayah.com/data/Abdul_Basit_Mujawwad_128kbps/",
        cacheDir = "audio_abdul_basit_mujawwad",
        isArabic = true,
        info = "everyayah.com · ১২৮ kbps",
    ),
    BASFAR(
        label = "আব্দুল্লাহ বাসফার",
        baseUrl = "https://everyayah.com/data/Abdullah_Basfar_64kbps/",
        cacheDir = "audio_basfar",
        isArabic = true,
        info = "everyayah.com · ৬৪ kbps",
    ),
    SUDAIS(
        label = "আব্দুর রহমান আস-সুদাইস",
        baseUrl = "https://everyayah.com/data/Abdurrahmaan_As-Sudais_64kbps/",
        cacheDir = "audio_sudais",
        isArabic = true,
        info = "everyayah.com · ৬৪ kbps",
    ),
    SHAATREE(
        label = "আবু বকর আশ-শাতরী",
        baseUrl = "https://everyayah.com/data/Abu_Bakr_Ash-Shaatree_64kbps/",
        cacheDir = "audio_shaatree",
        isArabic = true,
        info = "everyayah.com · ৬৪ kbps",
    ),
    AJAMY(
        label = "আহমদ ইবনে আলী আল-আজমী",
        baseUrl = "https://everyayah.com/data/Ahmed_ibn_Ali_al-Ajamy_64kbps_QuranExplorer.Com/",
        cacheDir = "audio_ajamy",
        isArabic = true,
        info = "everyayah.com · ৬৪ kbps",
    ),
    ALAFASY(
        label = "মিশারী রাশিদ আল-আফাসি",
        baseUrl = "https://everyayah.com/data/Alafasy_64kbps/",
        cacheDir = "audio",
        isArabic = true,
        info = "everyayah.com · ৬৪ kbps",
    ),
    GHAMADI(
        label = "সাদ আল-গামিদী",
        baseUrl = "https://everyayah.com/data/Ghamadi_40kbps/",
        cacheDir = "audio_ghamadi",
        isArabic = true,
        info = "everyayah.com · ৪০ kbps",
    ),
    HANI_RIFAI(
        label = "হানি আর-রিফাঈ",
        baseUrl = "https://everyayah.com/data/Hani_Rifai_64kbps/",
        cacheDir = "audio_hani_rifai",
        isArabic = true,
        info = "everyayah.com · ৬৪ kbps",
    ),
    HUSARY(
        label = "মাহমুদ খলিল আল-হুসারী",
        baseUrl = "https://everyayah.com/data/Husary_64kbps/",
        cacheDir = "audio_husary",
        isArabic = true,
        info = "everyayah.com · ৬৪ kbps",
    ),
    HUSARY_MUJAWWAD(
        label = "আল-হুসারী (মুজাওয়াদ)",
        baseUrl = "https://everyayah.com/data/Husary_Mujawwad_64kbps/",
        cacheDir = "audio_husary_mujawwad",
        isArabic = true,
        info = "everyayah.com · ৬৪ kbps",
    ),
    HUSARY_MUALLIM(
        label = "আল-হুসারী (মুআল্লিম, শেখার জন্য)",
        baseUrl = "https://everyayah.com/data/Husary_Muallim_128kbps/",
        cacheDir = "audio_husary_muallim",
        isArabic = true,
        info = "everyayah.com · ১২৮ kbps",
    ),
    HUDHAIFY(
        label = "আলী আল-হুযাইফী",
        baseUrl = "https://everyayah.com/data/Hudhaify_64kbps/",
        cacheDir = "audio_hudhaify",
        isArabic = true,
        info = "everyayah.com · ৬৪ kbps",
    ),
    IBRAHIM_AKHDAR(
        label = "ইবরাহীম আল-আখদার",
        baseUrl = "https://everyayah.com/data/Ibrahim_Akhdar_64kbps/",
        cacheDir = "audio_ibrahim_akhdar",
        isArabic = true,
        info = "everyayah.com · ৬৪ kbps",
    ),
    MAHER(
        label = "মাহের আল-মুআইকিলী",
        baseUrl = "https://everyayah.com/data/Maher_AlMuaiqly_64kbps/",
        cacheDir = "audio_maher",
        isArabic = true,
        info = "everyayah.com · ৬৪ kbps",
    ),
    MINSHAWI(
        label = "মুহাম্মাদ সিদ্দীক আল-মিনশাবী (মুরাত্তাল)",
        baseUrl = "https://everyayah.com/data/Menshawi_32kbps/",
        cacheDir = "audio_minshawi",
        isArabic = true,
        info = "everyayah.com · ৩২ kbps",
    ),
    MINSHAWI_MUJAWWAD(
        label = "আল-মিনশাবী (মুজাওয়াদ)",
        baseUrl = "https://everyayah.com/data/Minshawy_Mujawwad_64kbps/",
        cacheDir = "audio_minshawi_mujawwad",
        isArabic = true,
        info = "everyayah.com · ৬৪ kbps",
    ),
    TABLAWAY(
        label = "মুহাম্মাদ আত-তাবলাবী",
        baseUrl = "https://everyayah.com/data/Mohammad_al_Tablaway_64kbps/",
        cacheDir = "audio_tablaway",
        isArabic = true,
        info = "everyayah.com · ৬৪ kbps",
    ),
    AYYOUB(
        label = "মুহাম্মাদ আইয়ুব",
        baseUrl = "https://everyayah.com/data/Muhammad_Ayyoub_64kbps/",
        cacheDir = "audio_ayyoub",
        isArabic = true,
        info = "everyayah.com · ৬৪ kbps",
    ),
    JIBREEL(
        label = "মুহাম্মাদ জিবরীল",
        baseUrl = "https://everyayah.com/data/Muhammad_Jibreel_64kbps/",
        cacheDir = "audio_jibreel",
        isArabic = true,
        info = "everyayah.com · ৬৪ kbps",
    ),
    MUSTAFA_ISMAIL(
        label = "মুস্তফা ইসমাঈল",
        baseUrl = "https://everyayah.com/data/Mustafa_Ismail_48kbps/",
        cacheDir = "audio_mustafa_ismail",
        isArabic = true,
        info = "everyayah.com · ৪৮ kbps",
    ),
    SHURAYM(
        label = "সাউদ আশ-শুরাইম",
        baseUrl = "https://everyayah.com/data/Saood_ash-Shuraym_64kbps/",
        cacheDir = "audio_shuraym",
        isArabic = true,
        info = "everyayah.com · ৬৪ kbps",
    ),
    BUKHATIR(
        label = "সালাহ বুখাতির",
        baseUrl = "https://everyayah.com/data/Salaah_AbdulRahman_Bukhatir_128kbps/",
        cacheDir = "audio_bukhatir",
        isArabic = true,
        info = "everyayah.com · ১২৮ kbps",
    ),
    MUHSIN_QASIM(
        label = "মুহসিন আল-কাসিম",
        baseUrl = "https://everyayah.com/data/Muhsin_Al_Qasim_192kbps/",
        cacheDir = "audio_muhsin_qasim",
        isArabic = true,
        info = "everyayah.com · ১৯২ kbps",
    ),
    JUHAYNEE(
        label = "আব্দুল্লাহ আওয়াদ আল-জুহানী",
        baseUrl = "https://everyayah.com/data/Abdullaah_3awwaad_Al-Juhaynee_128kbps/",
        cacheDir = "audio_juhaynee",
        isArabic = true,
        info = "everyayah.com · ১২৮ kbps",
    ),
    BUDAIR(
        label = "সালাহ আল-বুদাইর",
        baseUrl = "https://everyayah.com/data/Salah_Al_Budair_128kbps/",
        cacheDir = "audio_budair",
        isArabic = true,
        info = "everyayah.com · ১২৮ kbps",
    ),
    MATROUD(
        label = "আব্দুল্লাহ মাতরুদ",
        baseUrl = "https://everyayah.com/data/Abdullah_Matroud_128kbps/",
        cacheDir = "audio_matroud",
        isArabic = true,
        info = "everyayah.com · ১২৮ kbps",
    ),
    NEANA(
        label = "আহমদ নাঈনা",
        baseUrl = "https://everyayah.com/data/Ahmed_Neana_128kbps/",
        cacheDir = "audio_neana",
        isArabic = true,
        info = "everyayah.com · ১২৮ kbps",
    ),
    ABDULKAREEM(
        label = "মুহাম্মাদ আব্দুল কারীম",
        baseUrl = "https://everyayah.com/data/Muhammad_AbdulKareem_128kbps/",
        cacheDir = "audio_abdulkareem",
        isArabic = true,
        info = "everyayah.com · ১২৮ kbps",
    ),
    TUNAIJI(
        label = "খলিফা আত-তুনাইজী",
        baseUrl = "https://everyayah.com/data/khalefa_al_tunaiji_64kbps/",
        cacheDir = "audio_tunaiji",
        isArabic = true,
        info = "everyayah.com · ৬৪ kbps",
    ),
    BANNA(
        label = "মাহমুদ আলী আল-বান্না",
        baseUrl = "https://everyayah.com/data/mahmoud_ali_al_banna_32kbps/",
        cacheDir = "audio_banna",
        isArabic = true,
        info = "everyayah.com · ৩২ kbps",
    ),
    QAHTANI(
        label = "খালিদ আব্দুল্লাহ আল-কাহতানী",
        baseUrl = "https://everyayah.com/data/Khaalid_Abdullaah_al-Qahtaanee_192kbps/",
        cacheDir = "audio_qahtani",
        isArabic = true,
        info = "everyayah.com · ১৯২ kbps",
    ),
    YASSER_DOSARI(
        label = "ইয়াসির আদ-দাওসারী",
        baseUrl = "https://everyayah.com/data/Yasser_Ad-Dussary_128kbps/",
        cacheDir = "audio_yasser_dosari",
        isArabic = true,
        info = "everyayah.com · ১২৮ kbps",
    ),
    QATAMI(
        label = "নাসের আল-কাতামী",
        baseUrl = "https://everyayah.com/data/Nasser_Alqatami_128kbps/",
        cacheDir = "audio_qatami",
        isArabic = true,
        info = "everyayah.com · ১২৮ kbps",
    ),
    SUESY(
        label = "আলী হাজ্জাজ আস-সুওয়াইসী",
        baseUrl = "https://everyayah.com/data/Ali_Hajjaj_AlSuesy_128kbps/",
        cacheDir = "audio_suesy",
        isArabic = true,
        info = "everyayah.com · ১২৮ kbps",
    ),
    SAHL_YASSIN(
        label = "সাহল ইয়াসীন",
        baseUrl = "https://everyayah.com/data/Sahl_Yassin_128kbps/",
        cacheDir = "audio_sahl_yassin",
        isArabic = true,
        info = "everyayah.com · ১২৮ kbps",
    ),
    AZIZ_ALILI(
        label = "আজিজ আলিলি",
        baseUrl = "https://everyayah.com/data/aziz_alili_128kbps/",
        cacheDir = "audio_aziz_alili",
        isArabic = true,
        info = "everyayah.com · ১২৮ kbps",
    ),
    YASER_SALAMAH(
        label = "ইয়াসির সালামাহ",
        baseUrl = "https://everyayah.com/data/Yaser_Salamah_128kbps/",
        cacheDir = "audio_yaser_salamah",
        isArabic = true,
        info = "everyayah.com · ১২৮ kbps",
    ),
    AKRAM_ALAQIMY(
        label = "আকরাম আল-আলাকিমী",
        baseUrl = "https://everyayah.com/data/Akram_AlAlaqimy_128kbps/",
        cacheDir = "audio_akram_alaqimy",
        isArabic = true,
        info = "everyayah.com · ১২৮ kbps",
    ),
    ALI_JABER(
        label = "আলী জাবের",
        baseUrl = "https://everyayah.com/data/Ali_Jaber_64kbps/",
        cacheDir = "audio_ali_jaber",
        isArabic = true,
        info = "everyayah.com · ৬৪ kbps",
    ),
    FARES_ABBAD(
        label = "ফারেস আব্বাদ",
        baseUrl = "https://everyayah.com/data/Fares_Abbad_64kbps/",
        cacheDir = "audio_fares_abbad",
        isArabic = true,
        info = "everyayah.com · ৬৪ kbps",
    ),
    AYMAN_SOWAID(
        label = "আইমান সুওয়াইদ",
        baseUrl = "https://everyayah.com/data/Ayman_Sowaid_64kbps/",
        cacheDir = "audio_ayman_sowaid",
        isArabic = true,
        info = "everyayah.com · ৬৪ kbps",
    ),
    PARHIZGAR(
        label = "শাহরিয়ার পারহিজগার",
        baseUrl = "https://everyayah.com/data/Parhizgar_48kbps/",
        cacheDir = "audio_parhizgar",
        isArabic = true,
        info = "everyayah.com · ৪৮ kbps",
    ),
    KARIM_MANSOORI(
        label = "করিম মানসুরী",
        baseUrl = "https://everyayah.com/data/Karim_Mansoori_40kbps/",
        cacheDir = "audio_karim_mansoori",
        isArabic = true,
        info = "everyayah.com · ৪০ kbps",
    ),
    WARSH_DOSARY(
        label = "ইবরাহীম আদ-দাওসারী (ওয়ারশ রেওয়ায়াত)",
        baseUrl = "https://everyayah.com/data/warsh/warsh_ibrahim_aldosary_128kbps/",
        cacheDir = "audio_warsh_dosary",
        isArabic = true,
        info = "everyayah.com · ১২৮ kbps",
    ),
    WARSH_JAZAERY(
        label = "ইয়াসিন আল-জাযায়েরী (ওয়ারশ রেওয়ায়াত)",
        baseUrl = "https://everyayah.com/data/warsh/warsh_yassin_al_jazaery_64kbps/",
        cacheDir = "audio_warsh_jazaery",
        isArabic = true,
        info = "everyayah.com · ৬৪ kbps",
    ),
    WARSH_ABDUL_BASIT(
        label = "আব্দুল বাসিত (ওয়ারশ রেওয়ায়াত)",
        baseUrl = "https://everyayah.com/data/warsh/warsh_Abdul_Basit_128kbps/",
        cacheDir = "audio_warsh_abdul_basit",
        isArabic = true,
        info = "everyayah.com · ১২৮ kbps",
    ),
    URDU(
        label = "উর্দু অনুবাদ (শামশাদ আলী খান)",
        baseUrl = "https://everyayah.com/data/translations/urdu_shamshad_ali_khan_46kbps/",
        cacheDir = "audio_ur",
        isArabic = false,
    ),
    BN_PRADEEP(
        label = "বাংলা অনুবাদ (প্রদীপ, পুরুষ কণ্ঠ)",
        // bn2 = প্রতিটি আয়াতের পূর্ণ অনুবাদ (১.৪); আগের bn_ ফোল্ডারে ছিল সংক্ষিপ্ত ভাবার্থ
        baseUrl = "${BN_AUDIO_BASE}bn2_pradeep/",
        cacheDir = "audio_bn2_pradeep",
        isArabic = false,
    ),
    BN_NABANITA(
        label = "বাংলা অনুবাদ (নবনীতা, নারী কণ্ঠ)",
        baseUrl = "${BN_AUDIO_BASE}bn2_nabanita/",
        cacheDir = "audio_bn2_nabanita",
        isArabic = false,
    );

    val shortLabel: String
        get() = when {
            isArabic -> "আরবি তেলাওয়াত"
            this == URDU -> "উর্দু অনুবাদ"
            else -> "বাংলা অনুবাদ"
        }

    val isBangla: Boolean get() = this == BN_PRADEEP || this == BN_NABANITA

    /** এই আয়াতে এই উৎসের কোন ফাইল বাজবে — ১.৪ থেকে প্রতিটি আয়াতের নিজস্ব পূর্ণ অনুবাদের অডিও আছে */
    fun fileKey(verse: Verse): String? = verse.key

    fun url(key: String): String = "$baseUrl$key.mp3"
}

/** সার্ভারে ফাইলটি নেই (HTTP 404) — নেটওয়ার্কের সমস্যা নয়। */
class AudioNotFoundException(message: String) : IOException(message)

/**
 * প্রতিটি অডিও একবার ডাউনলোড করে অ্যাপের নিজস্ব স্টোরেজে রেখে দেয়।
 * পরেরবার একই ফাইল বাজালে আর নেটওয়ার্ক থেকে নামায় না।
 */
class AudioCache(context: Context) {

    private val dirs: Map<AudioSource, File> = AudioSource.values().associateWith { src ->
        File(context.filesDir, src.cacheDir).apply { mkdirs() }
    }
    private val locks = ConcurrentHashMap<String, Mutex>()

    private fun dir(source: AudioSource): File = dirs.getValue(source)

    fun fileFor(key: String, source: AudioSource): File = File(dir(source), "$key.mp3")

    fun cachedKeys(source: AudioSource): Set<String> =
        dir(source).listFiles()
            ?.filter { it.name.endsWith(".mp3") && it.length() > 0 }
            ?.map { it.name.removeSuffix(".mp3") }
            ?.toSet()
            ?: emptySet()

    fun remove(key: String, source: AudioSource) {
        fileFor(key, source).delete()
    }

    /** ক্যাশে থাকলে সেই ফাইল দেয়, না থাকলে ডাউনলোড করে সেভ করে তারপর দেয়। */
    suspend fun get(key: String, source: AudioSource): File = withContext(Dispatchers.IO) {
        val target = fileFor(key, source)
        if (target.exists() && target.length() > 0) return@withContext target

        val lock = locks.getOrPut("${source.name}/$key") { Mutex() }
        lock.withLock {
            if (target.exists() && target.length() > 0) return@withLock target
            download(source.url(key), target)
            target
        }
    }

    private fun download(url: String, target: File) {
        val tmp = File(target.parentFile, "${target.nameWithoutExtension}.part")
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = true
            conn.connect()
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_NOT_FOUND) throw AudioNotFoundException("HTTP 404: $url")
            if (code != HttpURLConnection.HTTP_OK) throw IOException("HTTP $code")
            conn.inputStream.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
            if (tmp.length() == 0L) throw IOException("Empty file")
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        } catch (e: Exception) {
            tmp.delete()
            throw e
        } finally {
            conn.disconnect()
        }
    }
}
