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

/** অডিওর উৎস: প্রতিটির নিজস্ব সার্ভার-ফোল্ডার আর অ্যাপের ভেতরের ক্যাশ ফোল্ডার। */
enum class AudioSource(val label: String, val baseUrl: String, val cacheDir: String) {
    ARABIC(
        label = "আরবি তেলাওয়াত",
        baseUrl = "https://everyayah.com/data/Alafasy_64kbps/",
        cacheDir = "audio", // ১.০ সংস্করণের ডাউনলোড এখানেই আছে, তাই নাম বদলানো হয়নি
    ),
    URDU(
        label = "উর্দু অনুবাদ",
        baseUrl = "https://everyayah.com/data/translations/urdu_shamshad_ali_khan_46kbps/",
        cacheDir = "audio_ur",
    );

    fun url(verse: Verse): String = "$baseUrl${verse.key}.mp3"
}

/** সার্ভারে ফাইলটি নেই (HTTP 404) — নেটওয়ার্কের সমস্যা নয়। */
class AudioNotFoundException(message: String) : IOException(message)

/**
 * প্রতিটি আয়াতের অডিও একবার ডাউনলোড করে অ্যাপের নিজস্ব স্টোরেজে রেখে দেয়।
 * পরেরবার একই আয়াত বাজালে আর নেটওয়ার্ক থেকে নামায় না।
 */
class AudioCache(context: Context) {

    private val dirs: Map<AudioSource, File> = AudioSource.values().associateWith { src ->
        File(context.filesDir, src.cacheDir).apply { mkdirs() }
    }
    private val locks = ConcurrentHashMap<String, Mutex>()

    private fun dir(source: AudioSource): File = dirs.getValue(source)

    fun fileFor(verse: Verse, source: AudioSource): File = File(dir(source), "${verse.key}.mp3")

    fun cachedKeys(source: AudioSource): Set<String> =
        dir(source).listFiles()
            ?.filter { it.name.endsWith(".mp3") && it.length() > 0 }
            ?.map { it.name.removeSuffix(".mp3") }
            ?.toSet()
            ?: emptySet()

    fun remove(verse: Verse, source: AudioSource) {
        fileFor(verse, source).delete()
    }

    /** ক্যাশে থাকলে সেই ফাইল দেয়, না থাকলে ডাউনলোড করে সেভ করে তারপর দেয়। */
    suspend fun get(verse: Verse, source: AudioSource): File = withContext(Dispatchers.IO) {
        val target = fileFor(verse, source)
        if (target.exists() && target.length() > 0) return@withContext target

        val lock = locks.getOrPut("${source.name}/${verse.key}") { Mutex() }
        lock.withLock {
            if (target.exists() && target.length() > 0) return@withLock target
            download(source.url(verse), target)
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
