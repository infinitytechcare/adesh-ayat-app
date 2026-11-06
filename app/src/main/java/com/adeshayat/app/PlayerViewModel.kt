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

/** প্রতিটি আয়াতে কী কী বাজবে */
enum class PlayOrder(val label: String) {
    ARABIC_ONLY("শুধু আরবি তেলাওয়াত"),
    ARABIC_THEN_TRANSLATION("আরবি তেলাওয়াত, তারপর অনুবাদ"),
    TRANSLATION_ONLY("শুধু অনুবাদ"),
}

enum class RepeatSetting(val label: String, val short: String) {
    ONCE("প্রতিটি আয়াত ১ বার, তারপর পরেরটি", "১ বার"),
    FIVE("প্রতিটি আয়াত ৫ বার, তারপর পরেরটি", "৫ বার"),
    ONE("একটি আয়াতই বারবার (Repeat One)", "বারবার"),
}

val ARABIC_RECITERS = listOf(AudioSource.ALAFASY, AudioSource.ABDUL_SAMAD)
val TRANSLATION_AUDIOS = listOf(AudioSource.BN_PRADEEP, AudioSource.BN_NABANITA, AudioSource.URDU)

/** একটি আয়াতের একটি অডিও ট্র্যাক */
data class Track(val source: AudioSource, val key: String)

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    val data: QuranData = QuranData.load(app)
    private val cache = AudioCache(app)
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // ---------- সেটিংস ----------
    var showBangla by mutableStateOf(prefs.getBoolean(KEY_SHOW_BN, true))
        private set
    var reciter by mutableStateOf(enumPref(KEY_RECITER, AudioSource.ALAFASY).takeIf { it.isArabic } ?: AudioSource.ALAFASY)
        private set
    var translationAudio by mutableStateOf(loadTranslationAudio())
        private set
    var playOrder by mutableStateOf(loadPlayOrder())
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
    /** বর্তমান আয়াতের কোন অডিও বাজছে */
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

    /** কোন উৎসের কোন ফাইলগুলো ফোনে সেভ করা আছে */
    var downloaded by mutableStateOf(AudioSource.values().associateWith { cache.cachedKeys(it) })
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
    private var tracks: List<Track> = emptyList()
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

    /** নতুন অডিও সেটিং পরের আয়াত (বা পরের পুনরাবৃত্তি) থেকে কার্যকর হয়। */
    fun updateReciter(source: AudioSource) {
        reciter = source
        prefs.edit().putString(KEY_RECITER, source.name).apply()
    }

    fun updateTranslationAudio(source: AudioSource) {
        translationAudio = source
        prefs.edit().putString(KEY_TRANSLATION, source.name).apply()
    }

    fun updatePlayOrder(order: PlayOrder) {
        playOrder = order
        prefs.edit().putString(KEY_PLAY_ORDER, order.name).apply()
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

    /** বর্তমান সেটিং অনুযায়ী এই আয়াতে কোন কোন ট্র্যাক বাজবে */
    fun tracksFor(verse: Verse): List<Track> {
        val list = ArrayList<Track>(2)
        if (playOrder != PlayOrder.TRANSLATION_ONLY) {
            list.add(Track(reciter, verse.key))
        }
        if (playOrder != PlayOrder.ARABIC_ONLY) {
            translationAudio.fileKey(verse)?.let { list.add(Track(translationAudio, it)) }
        }
        return list
    }

    private fun isDownloaded(track: Track): Boolean = downloaded[track.source]?.contains(track.key) == true

    /** বর্তমান সেটিংয়ের সব ফাইল এই আয়াতের জন্য ফোনে আছে কিনা */
    fun isOffline(verse: Verse): Boolean = tracksFor(verse).all { isDownloaded(it) }

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
            else -> startVerse(cur, resetCount = false)
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
        currentVerse = verse
        tracks = tracksFor(verse)
        if (tracks.isEmpty()) {
            // যেমন: "শুধু বাংলা অনুবাদ" মোডে গ্রুপের মাঝের আয়াত — অর্থটি গ্রুপের শেষে বাজবে
            stopPlayback()
            currentSource = null
            next()
            return
        }
        playTrack(verse, 0)
    }

    /** একটি আয়াতের নির্দিষ্ট ট্র্যাক বাজায়; দরকার হলে আগে ডাউনলোড করে। */
    private fun playTrack(verse: Verse, index: Int) {
        loadJob?.cancel()
        releasePlayer()
        val track = tracks[index]
        val source = track.source
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
                cache.get(track.key, source)
            } catch (e: CancellationException) {
                throw e
            } catch (e: AudioNotFoundException) {
                // এই অডিও সার্ভারে নেই — ট্র্যাকটি বাদ দিয়ে এগিয়ে যাই
                isLoading = false
                message = "${source.shortLabel}: ${verse.surah.bn()}:${verse.ayah.bn()} আয়াতের অডিও পাওয়া যায়নি, বাদ দেওয়া হলো"
                if (index + 1 >= tracks.size && !unitPlayedSomething) next() else onTrackComplete()
                return@launch
            } catch (e: Exception) {
                isLoading = false
                isPlaying = false
                message = "ডাউনলোড হয়নি — ইন্টারনেট সংযোগ পরীক্ষা করুন"
                return@launch
            }
            markDownloaded(track)
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
                cache.remove(track.key, source)
                unmarkDownloaded(track)
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
                cache.remove(track.key, source)
                unmarkDownloaded(track)
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

        if (trackIndex + 1 < tracks.size) {
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
            val fresh = tracksFor(verse)
            if (fresh == tracks && tracks.size == 1 && p != null && prepared) {
                // একটাই ট্র্যাক — নতুন করে লোড না করে শুরু থেকে আবার বাজাই
                p.seekTo(0)
                p.start()
                isPlaying = true
                startTicker()
            } else {
                startVerse(verse, resetCount = false)
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
            addAll(tracksFor(verse))
            q.getOrNull(i + 1)?.let { addAll(tracksFor(it)) }
        }
        viewModelScope.launch {
            for (t in targets) {
                if (isDownloaded(t)) continue
                try {
                    cache.get(t.key, t.source)
                    markDownloaded(t)
                } catch (e: CancellationException) {
                    throw e
                } catch (ignored: Exception) {
                    // পরে বাজানোর সময় আবার চেষ্টা হবে
                }
            }
        }
    }

    /** বর্তমান সেটিংয়ের সব ফাইল একবারে ডাউনলোড করে; আবার চাপলে থেমে যায়। */
    fun toggleDownloadAll() {
        if (bulkJob?.isActive == true) {
            bulkJob?.cancel()
            bulkProgress = null
            return
        }
        bulkJob = viewModelScope.launch {
            bulkProgress = offlineCount()
            var missing = 0
            try {
                for (v in data.verses) {
                    for (t in tracksFor(v)) {
                        if (isDownloaded(t)) continue
                        try {
                            cache.get(t.key, t.source)
                            markDownloaded(t)
                        } catch (e: AudioNotFoundException) {
                            missing++
                        }
                    }
                    bulkProgress = offlineCount()
                }
                message = if (missing == 0) "সব আয়াত অফলাইনের জন্য সেভ হয়েছে"
                else "সেভ হয়েছে; ${missing.bn()}টি অডিও সার্ভারে পাওয়া যায়নি"
            } catch (e: CancellationException) {
                throw e
            } catch (ignored: Exception) {
                message = "ডাউনলোড থেমে গেছে — ইন্টারনেট সংযোগ পরীক্ষা করুন"
            } finally {
                bulkProgress = null
            }
        }
    }

    private fun markDownloaded(track: Track) {
        val set = downloaded[track.source] ?: emptySet()
        if (track.key !in set) downloaded = downloaded + (track.source to (set + track.key))
    }

    private fun unmarkDownloaded(track: Track) {
        val set = downloaded[track.source] ?: return
        downloaded = downloaded + (track.source to (set - track.key))
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

    /** ১.১ সংস্করণে "audio_mode" (ARABIC_ONLY / ARABIC_URDU / URDU_ONLY) ছিল — সেটিও মেনে নিই। */
    private fun loadPlayOrder(): PlayOrder {
        if (prefs.contains(KEY_PLAY_ORDER)) return enumPref(KEY_PLAY_ORDER, PlayOrder.ARABIC_ONLY)
        return when (prefs.getString(KEY_OLD_AUDIO_MODE, null)) {
            "ARABIC_URDU" -> PlayOrder.ARABIC_THEN_TRANSLATION
            "URDU_ONLY" -> PlayOrder.TRANSLATION_ONLY
            else -> PlayOrder.ARABIC_ONLY
        }
    }

    private fun loadTranslationAudio(): AudioSource {
        if (prefs.contains(KEY_TRANSLATION)) {
            val s = enumPref(KEY_TRANSLATION, AudioSource.BN_PRADEEP)
            return if (s.isArabic) AudioSource.BN_PRADEEP else s
        }
        // আগে উর্দু অডিও চালু করা থাকলে উর্দুই রাখি, নইলে নতুন ডিফল্ট বাংলা
        return when (prefs.getString(KEY_OLD_AUDIO_MODE, null)) {
            "ARABIC_URDU", "URDU_ONLY" -> AudioSource.URDU
            else -> AudioSource.BN_PRADEEP
        }
    }

    /** ১.০ সংস্করণে শুধু "৫ বার" চালু/বন্ধ (boolean) ছিল — সেটিকেও মেনে নিই। */
    private fun loadRepeatMode(): RepeatSetting {
        if (prefs.contains(KEY_REPEAT_MODE)) return enumPref(KEY_REPEAT_MODE, RepeatSetting.ONCE)
        return if (prefs.getBoolean(KEY_OLD_REPEAT, false)) RepeatSetting.FIVE else RepeatSetting.ONCE
    }

    companion object {
        private const val KEY_SHOW_BN = "show_bangla"
        private const val KEY_RECITER = "reciter"
        private const val KEY_TRANSLATION = "translation_audio"
        private const val KEY_PLAY_ORDER = "play_order"
        private const val KEY_REPEAT_MODE = "repeat_mode"
        private const val KEY_OLD_AUDIO_MODE = "audio_mode"
        private const val KEY_OLD_REPEAT = "repeat_five"
    }
}
