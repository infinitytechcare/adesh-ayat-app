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
import com.adeshayat.app.audio.AudioNotFoundException
import com.adeshayat.app.audio.AudioSource
import com.adeshayat.app.data.QuranData
import com.adeshayat.app.data.Verse
import com.adeshayat.app.data.bn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

const val REPEAT_TIMES = 5

/** প্রতিটি আয়াতে কোন কোন অডিও পরপর বাজবে */
enum class AudioMode(val label: String, val sources: List<AudioSource>) {
    ARABIC_ONLY("শুধু আরবি তেলাওয়াত", listOf(AudioSource.ARABIC)),
    ARABIC_URDU("আরবি তেলাওয়াত + উর্দু অনুবাদ", listOf(AudioSource.ARABIC, AudioSource.URDU)),
    URDU_ONLY("শুধু উর্দু অনুবাদ", listOf(AudioSource.URDU)),
}

enum class RepeatSetting(val label: String, val short: String) {
    ONCE("প্রতিটি আয়াত ১ বার, তারপর পরেরটি", "১ বার"),
    FIVE("প্রতিটি আয়াত ৫ বার, তারপর পরেরটি", "৫ বার"),
    ONE("একটি আয়াতই বারবার (Repeat One)", "বারবার"),
}

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    val data: QuranData = QuranData.load(app)
    private val cache = AudioCache(app)
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // ---------- সেটিংস ----------
    var showBangla by mutableStateOf(prefs.getBoolean(KEY_SHOW_BN, true))
        private set
    var audioMode by mutableStateOf(enumPref(KEY_AUDIO_MODE, AudioMode.ARABIC_ONLY))
        private set
    var repeatMode by mutableStateOf(loadRepeatMode())
        private set

    // ---------- UI state ----------
    var selectedCategory by mutableStateOf<Int?>(null)
        private set
    var visible by mutableStateOf(data.verses)
        private set

    var currentVerse by mutableStateOf<Verse?>(null)
        private set
    /** বর্তমান আয়াতের কোন অডিও বাজছে (আরবি না উর্দু) */
    var currentSource by mutableStateOf<AudioSource?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var isLoading by mutableStateOf(false)
        private set
    /** বর্তমান আয়াত কততম বার বাজছে */
    var playCount by mutableIntStateOf(1)
        private set

    var positionMs by mutableIntStateOf(0)
        private set
    var durationMs by mutableIntStateOf(0)
        private set

    var downloadedArabic by mutableStateOf(cache.cachedKeys(AudioSource.ARABIC))
        private set
    var downloadedUrdu by mutableStateOf(cache.cachedKeys(AudioSource.URDU))
        private set

    /** সব আয়াত অফলাইনে সেভ করার অগ্রগতি (কয়টি আয়াত তৈরি); null মানে চলছে না। */
    var bulkProgress by mutableStateOf<Int?>(null)
        private set

    var message by mutableStateOf<String?>(null)
        private set

    // ---------- internal ----------
    private var player: MediaPlayer? = null
    private var prepared = false
    private var queue: List<Verse> = emptyList()
    private var trackIndex = 0
    /** এই পুনরাবৃত্তিতে আয়াতের অন্তত একটি ট্র্যাক বেজেছে কিনা (অডিও না পেলে অসীম লুপ ঠেকাতে) */
    private var unitPlayedSomething = false
    private var loadJob: Job? = null
    private var tickJob: Job? = null
    private var bulkJob: Job? = null

    // ================================================================ settings

    fun updateShowBangla(value: Boolean) {
        showBangla = value
        prefs.edit().putBoolean(KEY_SHOW_BN, value).apply()
    }

    /** নতুন মোড পরের আয়াত (বা পরের পুনরাবৃত্তি) থেকে কার্যকর হয়। */
    fun updateAudioMode(mode: AudioMode) {
        audioMode = mode
        prefs.edit().putString(KEY_AUDIO_MODE, mode.name).apply()
    }

    fun updateRepeatMode(mode: RepeatSetting) {
        repeatMode = mode
        prefs.edit().putString(KEY_REPEAT_MODE, mode.name).apply()
    }

    /** প্লেয়ারের চিপ চাপলে: ১ বার → ৫ বার → বারবার → ১ বার */
    fun cycleRepeatMode() {
        val all = RepeatSetting.values()
        updateRepeatMode(all[(repeatMode.ordinal + 1) % all.size])
    }

    private fun downloadedSet(source: AudioSource): Set<String> =
        if (source == AudioSource.ARABIC) downloadedArabic else downloadedUrdu

    /** বর্তমান অডিও মোডের সব ফাইল এই আয়াতের জন্য ডাউনলোড করা আছে কিনা */
    fun isOffline(verse: Verse): Boolean = audioMode.sources.all { verse.key in downloadedSet(it) }

    fun offlineCount(): Int = data.verses.count { isOffline(it) }

    // ================================================================ list

    fun selectCategory(id: Int?) {
        selectedCategory = id
        visible = if (id == null) data.verses else data.verses.filter { it.categoryId == id }
    }

    fun clearMessage() {
        message = null
    }

    // ================================================================ playback controls

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
        val cur = currentVerse
        when {
            cur == null -> playAll()
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
            else -> playTrack(cur, trackIndex.coerceIn(0, audioMode.sources.size - 1))
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

    fun stop() {
        stopPlayback()
        currentVerse = null
        currentSource = null
    }

    // ================================================================ playback engine

    private fun startVerse(verse: Verse, resetCount: Boolean) {
        if (resetCount) playCount = 1
        playTrack(verse, 0)
    }

    /** একটি আয়াতের নির্দিষ্ট ট্র্যাক (আরবি/উর্দু) বাজায়; দরকার হলে আগে ডাউনলোড করে। */
    private fun playTrack(verse: Verse, index: Int) {
        loadJob?.cancel()
        releasePlayer()
        val sources = audioMode.sources
        val source = sources[index]
        currentVerse = verse
        currentSource = source
        trackIndex = index
        if (index == 0) unitPlayedSomething = false
        isLoading = true
        isPlaying = false
        positionMs = 0
        durationMs = 0

        loadJob = viewModelScope.launch {
            val file = try {
                cache.get(verse, source)
            } catch (e: CancellationException) {
                throw e
            } catch (e: AudioNotFoundException) {
                // এই আয়াতের এই অডিও সার্ভারে নেই — ট্র্যাকটি বাদ দিয়ে এগিয়ে যাই
                isLoading = false
                message = "${source.label}: ${verse.surah.bn()}:${verse.ayah.bn()} আয়াতের অডিও পাওয়া যায়নি, বাদ দেওয়া হলো"
                if (index + 1 >= sources.size && !unitPlayedSomething) next() else onTrackComplete()
                return@launch
            } catch (e: Exception) {
                isLoading = false
                isPlaying = false
                message = "ডাউনলোড হয়নি — ইন্টারনেট সংযোগ পরীক্ষা করুন"
                return@launch
            }
            markDownloaded(verse, source)
            if (currentVerse?.key != verse.key || currentSource != source) return@launch

            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            mp.setWakeMode(getApplication(), PowerManager.PARTIAL_WAKE_LOCK)
            try {
                mp.setDataSource(file.absolutePath)
            } catch (e: Exception) {
                mp.release()
                cache.remove(verse, source)
                unmarkDownloaded(verse, source)
                isLoading = false
                message = "অডিও ফাইল নষ্ট ছিল, আবার চাপলে নতুন করে ডাউনলোড হবে"
                return@launch
            }
            mp.setOnPreparedListener { readyPlayer ->
                if (player !== readyPlayer) return@setOnPreparedListener
                prepared = true
                unitPlayedSomething = true
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
                cache.remove(verse, source)
                unmarkDownloaded(verse, source)
                releasePlayer()
                isLoading = false
                isPlaying = false
                message = "অডিও চালাতে সমস্যা হয়েছে, আবার চেষ্টা করুন"
                true
            }
            player = mp
            mp.prepareAsync()

            prefetch(verse)
        }
    }

    /** একটি ট্র্যাক শেষ হলে: আয়াতের পরের ট্র্যাক, নয়তো পুনরাবৃত্তি, নয়তো পরের আয়াত। */
    private fun onTrackComplete() {
        stopTicker()
        val verse = currentVerse ?: return
        val sources = audioMode.sources

        if (trackIndex + 1 < sources.size) {
            playTrack(verse, trackIndex + 1)
            return
        }

        val repeatAgain = when (repeatMode) {
            RepeatSetting.ONCE -> false
            RepeatSetting.FIVE -> playCount < REPEAT_TIMES
            RepeatSetting.ONE -> true
        }
        if (repeatAgain) {
            playCount += 1
            val p = player
            if (sources.size == 1 && p != null && prepared) {
                // একটাই ট্র্যাক — নতুন করে লোড না করে শুরু থেকে আবার বাজাই
                p.seekTo(0)
                p.start()
                isPlaying = true
                startTicker()
            } else {
                playTrack(verse, 0)
            }
        } else {
            isPlaying = false
            positionMs = durationMs
            next()
        }
    }

    /** বর্তমান আয়াতের বাকি ট্র্যাক আর পরের আয়াতের ট্র্যাকগুলো আগেই ডাউনলোড করে রাখে। */
    private fun prefetch(verse: Verse) {
        val q = queue.ifEmpty { visible }
        val i = q.indexOfFirst { it.key == verse.key }
        val targets = buildList {
            add(verse)
            q.getOrNull(i + 1)?.let { add(it) }
        }
        val sources = audioMode.sources
        viewModelScope.launch {
            for (v in targets) {
                for (src in sources) {
                    if (v.key in downloadedSet(src)) continue
                    try {
                        cache.get(v, src)
                        markDownloaded(v, src)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (ignored: Exception) {
                        // পরে বাজানোর সময় আবার চেষ্টা হবে
                    }
                }
            }
        }
    }

    /** বর্তমান অডিও মোডের সব ফাইল একবারে ডাউনলোড করে; আবার চাপলে থেমে যায়। */
    fun toggleDownloadAll() {
        if (bulkJob?.isActive == true) {
            bulkJob?.cancel()
            bulkProgress = null
            return
        }
        val sources = audioMode.sources
        bulkJob = viewModelScope.launch {
            bulkProgress = offlineCount()
            var missing = 0
            try {
                for (v in data.verses) {
                    for (src in sources) {
                        if (v.key in downloadedSet(src)) continue
                        try {
                            cache.get(v, src)
                            markDownloaded(v, src)
                        } catch (e: AudioNotFoundException) {
                            missing++
                        }
                    }
                    bulkProgress = offlineCount()
                }
                message = if (missing == 0) "সব আয়াত অফলাইনের জন্য সেভ হয়েছে"
                else "সেভ হয়েছে; ${missing}টি অডিও সার্ভারে পাওয়া যায়নি"
            } catch (e: CancellationException) {
                throw e
            } catch (ignored: Exception) {
                message = "ডাউনলোড থেমে গেছে — ইন্টারনেট সংযোগ পরীক্ষা করুন"
            } finally {
                bulkProgress = null
            }
        }
    }

    private fun markDownloaded(verse: Verse, source: AudioSource) {
        when (source) {
            AudioSource.ARABIC -> if (verse.key !in downloadedArabic) downloadedArabic = downloadedArabic + verse.key
            AudioSource.URDU -> if (verse.key !in downloadedUrdu) downloadedUrdu = downloadedUrdu + verse.key
        }
    }

    private fun unmarkDownloaded(verse: Verse, source: AudioSource) {
        when (source) {
            AudioSource.ARABIC -> downloadedArabic = downloadedArabic - verse.key
            AudioSource.URDU -> downloadedUrdu = downloadedUrdu - verse.key
        }
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

    // ================================================================ prefs helpers

    private inline fun <reified T : Enum<T>> enumPref(key: String, default: T): T {
        val name = prefs.getString(key, null) ?: return default
        return try {
            enumValueOf<T>(name)
        } catch (ignored: IllegalArgumentException) {
            default
        }
    }

    /** ১.০ সংস্করণে শুধু "৫ বার" চালু/বন্ধ (boolean) ছিল — সেটিকেও মেনে নিই। */
    private fun loadRepeatMode(): RepeatSetting {
        if (prefs.contains(KEY_REPEAT_MODE)) return enumPref(KEY_REPEAT_MODE, RepeatSetting.ONCE)
        return if (prefs.getBoolean(KEY_OLD_REPEAT, false)) RepeatSetting.FIVE else RepeatSetting.ONCE
    }

    companion object {
        private const val KEY_SHOW_BN = "show_bangla"
        private const val KEY_AUDIO_MODE = "audio_mode"
        private const val KEY_REPEAT_MODE = "repeat_mode"
        private const val KEY_OLD_REPEAT = "repeat_five"
    }
}
