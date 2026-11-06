package com.adeshayat.app.data

import android.content.Context
import org.json.JSONObject
import java.util.Locale

data class Category(
    val id: Int,
    val title: String,
)

data class Verse(
    val surah: Int,
    val ayah: Int,
    val categoryId: Int,
    val arabic: String,
    /** বাংলা ভাবার্থ — পরের সংস্করণে UI-তে দেখানো হবে; ডেটা এখনই প্রস্তুত। */
    val meaningBn: String,
    val surahName: String,
) {
    /** everyayah.com-এর ফাইল নাম: সূরা ৩ ঘর + আয়াত ৩ ঘর, যেমন 001007 */
    val key: String = String.format(Locale.US, "%03d%03d", surah, ayah)

    val audioUrl: String get() = "$AUDIO_BASE_URL$key.mp3"

    companion object {
        const val AUDIO_BASE_URL = "https://everyayah.com/data/Alafasy_64kbps/"
    }
}

class QuranData(
    val categories: List<Category>,
    val verses: List<Verse>,
) {
    fun categoryTitle(id: Int): String = categories.firstOrNull { it.id == id }?.title ?: ""

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
                    )
                )
            }
            return QuranData(categories, verses)
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
