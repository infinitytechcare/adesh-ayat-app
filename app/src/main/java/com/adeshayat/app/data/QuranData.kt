package com.adeshayat.app.data

import android.content.Context
import org.json.JSONObject
import java.util.Locale

/** একটি হাদিস: সূত্র (গ্রন্থ ও নম্বর), মান আর বাংলা অনুবাদ */
data class Hadith(
    val id: String,
    val ref: String,
    val grade: String,
    val text: String,
)

/** না থামার চিহ্ন (ۙ)-এর কারণে মূল আয়াতের সাথে যুক্ত পরের আয়াত */
data class Ayah(
    val surah: Int,
    val ayah: Int,
    val arabic: String,
    val translation: String,
) {
    val key: String = String.format(Locale.US, "%03d%03d", surah, ayah)
}

data class Category(
    val id: Int,
    val title: String,
    /** কিওয়ার্ডভিত্তিক বিষয় (একটি আয়াত একাধিক বিষয়ে থাকতে পারে) */
    val isTheme: Boolean = false,
)

data class Verse(
    val surah: Int,
    val ayah: Int,
    val categoryId: Int,
    val arabic: String,
    /** বাংলা ভাবার্থ — পরের সংস্করণে UI-তে দেখানো হবে; ডেটা এখনই প্রস্তুত। */
    val meaningBn: String,
    val surahName: String,
    /** কয়েকটি আয়াতের অর্থ একসাথে লেখা থাকলে, যেমন "112:1-4"; না থাকলে খালি। */
    val group: String = "",
    /**
     * বাংলা অর্থের অডিও ফাইলের নাম (SSSAAA)। একসাথে লেখা অর্থের ক্ষেত্রে গ্রুপের শেষ আয়াতে
     * গ্রুপের প্রথম আয়াতের নাম থাকে, মাঝের আয়াতগুলোতে null — অর্থটি পুরো গ্রুপের পর একবার বাজে।
     */
    val bnAudioKey: String? = null,
    /** পূর্ণ বাংলা অনুবাদ (ড. আবু বকর মুহাম্মাদ যাকারিয়া) */
    val translation: String = "",
    /** সম্পর্কিত হাদিসের id (QuranData.hadiths-এ খুঁজুন) */
    val hadithIds: List<String> = emptyList(),
    /** কিওয়ার্ডভিত্তিক বিষয়ের id (যেমন সত্যবাদিতা, ধৈর্য) */
    val themeIds: List<Int> = emptyList(),
    /** আয়াতের শেষে ۙ (না থামার চিহ্ন) থাকলে পরের আয়াতগুলো — একসাথে দেখানো ও বাজানো হয় */
    val continuation: List<Ayah> = emptyList(),
) {
    /** মূল আয়াত আর যুক্ত আয়াতগুলোর অডিও ফাইলের নাম, ক্রমানুসারে */
    val audioKeys: List<String> get() = listOf(key) + continuation.map { it.key }
    val lastAyah: Int get() = continuation.lastOrNull()?.ayah ?: ayah

    fun inCategory(id: Int): Boolean = categoryId == id || id in themeIds

    /** গ্রুপের প্রথম আয়াত কিনা (একসাথে লেখা অর্থ এই আয়াতের নিচেই দেখানো হয়) */
    val isGroupStart: Boolean get() = group.isEmpty() || meaningBn.isNotEmpty()
    /** everyayah.com-এর ফাইল নাম: সূরা ৩ ঘর + আয়াত ৩ ঘর, যেমন 001007 */
    val key: String = String.format(Locale.US, "%03d%03d", surah, ayah)

}

class QuranData(
    val categories: List<Category>,
    val verses: List<Verse>,
    val hadiths: Map<String, Hadith> = emptyMap(),
    val themes: List<Category> = emptyList(),
) {
    fun countIn(id: Int): Int = verses.count { it.inCategory(id) }

    fun hadithsFor(verse: Verse): List<Hadith> = verse.hadithIds.mapNotNull { hadiths[it] }

    fun categoryTitle(id: Int): String =
        (categories.firstOrNull { it.id == id } ?: themes.firstOrNull { it.id == id })?.title ?: ""

    companion object {
        fun load(context: Context): QuranData {
            val text = context.assets.open("verses.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(text)

            val catArray = root.getJSONArray("categories")
            val categories = ArrayList<Category>(catArray.length())
            for (i in 0 until catArray.length()) {
                val c = catArray.getJSONObject(i)
                categories.add(Category(c.getInt("id"), c.getString("title")))
            }

            val themes = ArrayList<Category>()
            root.optJSONArray("themes")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val c = arr.getJSONObject(i)
                    themes.add(Category(c.getInt("id"), c.getString("title"), isTheme = true))
                }
            }

            val surahNames = root.getJSONObject("surahs")

            val verseArray = root.getJSONArray("verses")
            val verses = ArrayList<Verse>(verseArray.length())
            for (i in 0 until verseArray.length()) {
                val v = verseArray.getJSONObject(i)
                val s = v.getInt("s")
                verses.add(
                    Verse(
                        surah = s,
                        ayah = v.getInt("a"),
                        categoryId = v.getInt("cat"),
                        arabic = v.getString("ar"),
                        meaningBn = v.optString("bn", ""),
                        surahName = surahNames.optString(s.toString(), s.toString()),
                        group = v.optString("group", ""),
                        translation = v.optString("tr", ""),
                        hadithIds = v.optJSONArray("hd")?.let { arr ->
                            List(arr.length()) { arr.getString(it) }
                        } ?: emptyList(),
                        continuation = v.optJSONArray("nx")?.let { arr ->
                            List(arr.length()) {
                                val n = arr.getJSONObject(it)
                                Ayah(s, n.getInt("a"), n.getString("ar"), n.optString("tr", ""))
                            }
                        } ?: emptyList(),
                        themeIds = v.optJSONArray("tp")?.let { arr ->
                            List(arr.length()) { arr.getInt(it) }
                        } ?: emptyList(),
                    )
                )
            }
            // গ্রুপের যে আয়াতটি তালিকায় সবশেষে আছে (কোনো আয়াত অন্য বিষয়ে আগেই থাকলে গ্রুপ থেকে বাদ পড়ে)
            val groupLastAyah: Map<String, Int> = verses.filter { it.group.isNotEmpty() }
                .groupBy { it.group }
                .mapValues { (_, list) -> list.maxOf { it.ayah } }
            val hadiths = HashMap<String, Hadith>()
            root.optJSONObject("hadiths")?.let { hs ->
                val keys = hs.keys()
                while (keys.hasNext()) {
                    val id = keys.next()
                    val h = hs.getJSONObject(id)
                    hadiths[id] = Hadith(id, h.getString("ref"), h.optString("grade", ""), h.getString("text"))
                }
            }
            return QuranData(
                categories,
                verses.map { it.copy(bnAudioKey = bnAudioKeyFor(it, groupLastAyah)) },
                hadiths,
                themes,
            )
        }

        /** "112:1-4" ধরনের গ্রুপ থেকে বাংলা অডিওর ফাইল নাম বের করে */
        private fun bnAudioKeyFor(v: Verse, groupLastAyah: Map<String, Int>): String? {
            if (v.group.isEmpty()) return if (v.meaningBn.isNotEmpty()) v.key else null
            val m = Regex("""^(\d+):(\d+)-(\d+)$""").find(v.group) ?: return null
            val (s, a, _) = m.destructured
            if (v.ayah != groupLastAyah[v.group]) return null
            return String.format(Locale.US, "%03d%03d", s.toInt(), a.toInt())
        }
    }
}

/** ইংরেজি অঙ্কগুলোকে বাংলা অঙ্কে রূপান্তর করে। */
fun Int.bn(): String = toString().toBengaliDigits()

fun String.toBengaliDigits(): String {
    val sb = StringBuilder(length)
    for (ch in this) {
        if (ch in '0'..'9') sb.append('০' + (ch - '0')) else sb.append(ch)
    }
    return sb.toString()
}
