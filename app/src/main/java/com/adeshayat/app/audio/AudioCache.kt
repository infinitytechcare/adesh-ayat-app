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

/**
 * প্রতিটি আয়াতের অডিও একবার ডাউনলোড করে অ্যাপের নিজস্ব স্টোরেজে (filesDir/audio) রেখে দেয়।
 * পরেরবার একই আয়াত বাজালে আর নেটওয়ার্ক থেকে নামায় না।
 */
class AudioCache(context: Context) {

    private val dir: File = File(context.filesDir, "audio").apply { mkdirs() }
    private val locks = ConcurrentHashMap<String, Mutex>()

    fun fileFor(verse: Verse): File = File(dir, "${verse.key}.mp3")

    fun isCached(verse: Verse): Boolean {
        val f = fileFor(verse)
        return f.exists() && f.length() > 0
    }

    fun cachedKeys(): Set<String> =
        dir.listFiles()
            ?.filter { it.name.endsWith(".mp3") && it.length() > 0 }
            ?.map { it.name.removeSuffix(".mp3") }
            ?.toSet()
            ?: emptySet()

    fun remove(verse: Verse) {
        fileFor(verse).delete()
    }

    /** ক্যাশে থাকলে সেই ফাইল দেয়, না থাকলে ডাউনলোড করে সেভ করে তারপর দেয়। */
    suspend fun get(verse: Verse): File = withContext(Dispatchers.IO) {
        val target = fileFor(verse)
        if (target.exists() && target.length() > 0) return@withContext target

        val lock = locks.getOrPut(verse.key) { Mutex() }
        lock.withLock {
            if (target.exists() && target.length() > 0) return@withLock target
            download(verse.audioUrl, target)
            target
        }
    }

    private fun download(url: String, target: File) {
        val tmp = File(dir, "${target.nameWithoutExtension}.part")
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = true
            conn.connect()
            val code = conn.responseCode
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
