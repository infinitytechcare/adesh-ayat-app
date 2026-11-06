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

private const val BN_AUDIO_BASE = "https://infinitytechcare.github.io/adesh-ayat-app/audio/"

/** অডিওর উৎস: সার্ভারের ফোল্ডার আর অ্যাপের ভেতরের ক্যাশ ফোল্ডার। */
enum class AudioSource(
    val label: String,
    val baseUrl: String,
    val cacheDir: String,
    val isArabic: Boolean,
) {
    ALAFASY(
        label = "মিশারী রাশিদ আল-আফাসি",
        baseUrl = "https://everyayah.com/data/Alafasy_64kbps/",
        cacheDir = "audio", // ১.০ সংস্করণের ডাউনলোড এখানেই আছে, তাই নাম বদলানো হয়নি
        isArabic = true,
    ),
    ABDUL_SAMAD(
        label = "আব্দুল বাসিত আব্দুস সামাদ",
        baseUrl = "https://everyayah.com/data/AbdulSamad_64kbps_QuranExplorer.Com/",
        cacheDir = "audio_abdulsamad",
        isArabic = true,
    ),
    URDU(
        label = "উর্দু অনুবাদ (শামশাদ আলী খান)",
        baseUrl = "https://everyayah.com/data/translations/urdu_shamshad_ali_khan_46kbps/",
        cacheDir = "audio_ur",
        isArabic = false,
    ),
    BN_PRADEEP(
        label = "বাংলা অনুবাদ (প্রদীপ, পুরুষ কণ্ঠ)",
        baseUrl = "${BN_AUDIO_BASE}bn_pradeep/",
        cacheDir = "audio_bn_pradeep",
        isArabic = false,
    ),
    BN_NABANITA(
        label = "বাংলা অনুবাদ (নবনীতা, নারী কণ্ঠ)",
        baseUrl = "${BN_AUDIO_BASE}bn_nabanita/",
        cacheDir = "audio_bn_nabanita",
        isArabic = false,
    );

    val shortLabel: String
        get() = when (this) {
            ALAFASY, ABDUL_SAMAD -> "আরবি তেলাওয়াত"
            URDU -> "উর্দু অনুবাদ"
            BN_PRADEEP, BN_NABANITA -> "বাংলা অনুবাদ"
        }

    val isBangla: Boolean get() = this == BN_PRADEEP || this == BN_NABANITA

    /**
     * এই আয়াতে এই উৎসের কোন ফাইল বাজবে; null হলে এই আয়াতে এই উৎসের কিছু বাজবে না।
     * বাংলা অর্থ কয়েকটি আয়াতের জন্য একসাথে লেখা থাকলে গ্রুপের শেষ আয়াতের পর একবার বাজে।
     */
    fun fileKey(verse: Verse): String? = if (isBangla) verse.bnAudioKey else verse.key

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
