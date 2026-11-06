package com.adeshayat.app

import android.app.Application
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.PowerManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.adeshayat.app.audio.AudioCache
import com.adeshayat.app.data.QuranData
import com.adeshayat.app.data.Verse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

const val REPEAT_TIMES = 5

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    val data: QuranData = QuranData.load(app)
    private val cache = AudioCache(app)
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // ---------- UI state ----------
    var selectedCategory by mutableStateOf<Int?>(null)
        private set
    var visible by mutableStateOf(data.verses)
        private set

    var currentVerse by mutableStateOf<Verse?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var isLoading by mutableStateOf(false)
        private set

    /** চালু থাকলে প্রতিটি আয়াত ৫ বার বাজবে, তারপর পরের আয়াতে যাবে। */
    var repeatFive by mutableStateOf(prefs.getBoolean(KEY_REPEAT, false))
        private set
    var playCount by mutableIntStateOf(1)
        private set

    var positionMs by mutableIntStateOf(0)
        private set
    var durationMs by mutableIntStateOf(0)
        private set

    var downloaded by mutableStateOf(cache.cachedKeys())
        private set

    /** সব আয়াত অফলাইনে সেভ করার অগ্রগতি; null মানে চলছে না। */
    var bulkProgress by mutableStateOf<Int?>(null)
        private set

    var message by mutableStateOf<String?>(null)
        private set

    // ---------- internal ----------
    private var player: MediaPlayer? = null
    private var prepared = false
    private var queue: List<Verse> = emptyList()
    private var loadJob: Job? = null
    private var tickJob: Job? = null
    private var bulkJob: Job? = null

    fun selectCategory(id: Int?) {
        selectedCategory = id
        visible = if (id == null) data.verses else data.verses.filter { it.categoryId == id }
    }

    fun clearMessage() {
        message = null
    }

    /** তালিকার প্রথম আয়াত থেকে একের পর এক বাজানো শুরু করে। */
    fun playAll() {
        visible.firstOrNull()?.let { playVerse(it) }
    }

    fun playVerse(verse: Verse) {
        queue = visible
        startVerse(verse, resetCount = true)
    }

    fun togglePlay() {
        val p = player
        when {
            currentVerse == null -> playAll()
            isLoading -> Unit
            isPlaying -> {
                p?.pause()
                isPlaying = false
                stopTicker()
            }
            p != null && prepared -> {
                p.start()
                isPlaying = true
                startTicker()
            }
            else -> currentVerse?.let { startVerse(it, resetCount = false) }
        }
    }

    fun next() {
        val cur = currentVerse ?: return
        val q = queue.ifEmpty { visible }
        val i = q.indexOfFirst { it.key == cur.key }
        if (i >= 0 && i + 1 < q.size) {
            startVerse(q[i + 1], resetCount = true)
        } else {
            stopPlayback()
            message = "তালিকার শেষ আয়াত পর্যন্ত বাজানো হয়েছে"
        }
    }

    fun previous() {
        val cur = currentVerse ?: return
        val q = queue.ifEmpty { visible }
        val i = q.indexOfFirst { it.key == cur.key }
        if (i > 0) startVerse(q[i - 1], resetCount = true)
        else startVerse(cur, resetCount = true)
    }

    fun toggleRepeat() {
        repeatFive = !repeatFive
        prefs.edit().putBoolean(KEY_REPEAT, repeatFive).apply()
        if (!repeatFive) playCount = 1
    }

    fun stop() {
        stopPlayback()
        currentVerse = null
    }

    private fun startVerse(verse: Verse, resetCount: Boolean) {
        loadJob?.cancel()
        releasePlayer()
        currentVerse = verse
        if (resetCount) playCount = 1
        isLoading = true
        isPlaying = false
        positionMs = 0
        durationMs = 0

        loadJob = viewModelScope.launch {
            try {
                val file = cache.get(verse)
                markDownloaded(verse)
                if (currentVerse?.key != verse.key) return@launch

                val mp = MediaPlayer()
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                mp.setWakeMode(getApplication(), PowerManager.PARTIAL_WAKE_LOCK)
                mp.setDataSource(file.absolutePath)
                mp.setOnPreparedListener { readyPlayer ->
                    if (player !== readyPlayer) return@setOnPreparedListener
                    prepared = true
                    isLoading = false
                    durationMs = readyPlayer.duration
                    readyPlayer.start()
                    isPlaying = true
                    startTicker()
                }
                mp.setOnCompletionListener { donePlayer ->
                    if (player === donePlayer) onTrackComplete()
                }
                mp.setOnErrorListener { _, _, _ ->
                    // ফাইল নষ্ট হলে মুছে দিই, পরেরবার আবার ডাউনলোড হবে
                    cache.remove(verse)
                    downloaded = downloaded - verse.key
                    releasePlayer()
                    isLoading = false
                    isPlaying = false
                    message = "অডিও চালাতে সমস্যা হয়েছে, আবার চেষ্টা করুন"
                    true
                }
                player = mp
                mp.prepareAsync()

                prefetchNext(verse)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                isLoading = false
                isPlaying = false
                message = "ডাউনলোড হয়নি — ইন্টারনেট সংযোগ পরীক্ষা করুন"
            }
        }
    }

    private fun onTrackComplete() {
        stopTicker()
        val p = player
        if (repeatFive && playCount < REPEAT_TIMES && p != null) {
            playCount += 1
            p.seekTo(0)
            p.start()
            isPlaying = true
            startTicker()
        } else {
            isPlaying = false
            positionMs = durationMs
            next()
        }
    }

    /** বর্তমান আয়াত বাজার সময় পরের আয়াতটি আগেই ডাউনলোড করে রাখে, যাতে বিরতি না হয়। */
    private fun prefetchNext(verse: Verse) {
        val q = queue.ifEmpty { visible }
        val i = q.indexOfFirst { it.key == verse.key }
        val nextVerse = q.getOrNull(i + 1) ?: return
        if (nextVerse.key in downloaded) return
        viewModelScope.launch {
            try {
                cache.get(nextVerse)
                markDownloaded(nextVerse)
            } catch (e: CancellationException) {
                throw e
            } catch (ignored: Exception) {
                // পরে বাজানোর সময় আবার চেষ্টা হবে
            }
        }
    }

    /** সব আয়াত অফলাইনে শোনার জন্য একবারে ডাউনলোড করে; আবার চাপলে থেমে যায়। */
    fun toggleDownloadAll() {
        if (bulkJob?.isActive == true) {
            bulkJob?.cancel()
            bulkProgress = null
            return
        }
        bulkJob = viewModelScope.launch {
            bulkProgress = downloaded.size
            try {
                for (v in data.verses) {
                    if (v.key in downloaded) continue
                    cache.get(v)
                    markDownloaded(v)
                    bulkProgress = downloaded.size
                }
                message = "সব আয়াত অফলাইনের জন্য সেভ হয়েছে"
            } catch (e: CancellationException) {
                throw e
            } catch (ignored: Exception) {
                message = "ডাউনলোড থেমে গেছে — ইন্টারনেট সংযোগ পরীক্ষা করুন"
            } finally {
                bulkProgress = null
            }
        }
    }

    private fun markDownloaded(verse: Verse) {
        if (verse.key !in downloaded) downloaded = downloaded + verse.key
    }

    private fun startTicker() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (isActive) {
                val p = player
                if (p != null && prepared) {
                    try {
                        positionMs = p.currentPosition
                    } catch (ignored: IllegalStateException) {
                    }
                }
                delay(250)
            }
        }
    }

    private fun stopTicker() {
        tickJob?.cancel()
        tickJob = null
    }

    private fun stopPlayback() {
        loadJob?.cancel()
        releasePlayer()
        isPlaying = false
        isLoading = false
    }

    private fun releasePlayer() {
        stopTicker()
        player?.let {
            try {
                it.release()
            } catch (ignored: Exception) {
            }
        }
        player = null
        prepared = false
    }

    override fun onCleared() {
        releasePlayer()
        super.onCleared()
    }

    companion object {
        private const val KEY_REPEAT = "repeat_five"
    }
}
