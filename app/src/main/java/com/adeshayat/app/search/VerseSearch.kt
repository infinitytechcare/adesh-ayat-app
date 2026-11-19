package com.adeshayat.app.search

import com.adeshayat.app.data.QuranData
import com.adeshayat.app.data.Verse

/**
 * বাংলা ও আরবি কিওয়ার্ডে আয়াত খোঁজে।
 *
 * বাংলায় খুঁজলে পূর্ণ অনুবাদ, সংক্ষিপ্ত ভাবার্থ, সূরার নাম, বিষয়ের নাম আর আয়াত নম্বরে
 * (যেমন "২:২৫৫" বা "2:255") মেলানো হয়। আরবিতে খুঁজলে হরকত বাদ দিয়ে আরবি পাঠে মেলানো হয়।
 * একাধিক শব্দ লিখলে সব শব্দ যেসব আয়াতে আছে শুধু সেগুলো দেখায়।
 */
class VerseSearch(data: QuranData) {

    private val bnIndex: Map<String, String>
    private val arIndex: Map<String, String>

    init {
        val bn = HashMap<String, String>(data.verses.size)
        val ar = HashMap<String, String>(data.verses.size)
        for (v in data.verses) {
            val titles = buildString {
                append(data.categoryTitle(v.categoryId))
                for (t in v.themeIds) append(' ').append(data.categoryTitle(t))
            }
            bn[v.key] = normalizeBangla(
                "${v.translation} ${v.meaningBn} ${v.surahName} $titles ${v.surah}:${v.ayah}"
            )
            ar[v.key] = normalizeArabic(v.arabic)
        }
        bnIndex = bn
        arIndex = ar
    }

    /** খালি কোয়েরি হলে সব আয়াত মেলে */
    fun matches(verse: Verse, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        return if (ARABIC_CHAR.containsMatchIn(q)) {
            val text = arIndex[verse.key] ?: return false
            normalizeArabic(q).split(' ').filter { it.isNotEmpty() }.all { text.contains(it) }
        } else {
            val text = bnIndex[verse.key] ?: return false
            normalizeBangla(q).split(' ').filter { it.isNotEmpty() }.all { token ->
                // আয়াত নম্বর হলে হুবহু মেলাই, যাতে "২:২৫" লিখলে ২:২৫৫ না আসে
                if (REF.matches(token)) text.contains(" $token ") else text.contains(token)
            }
        }
    }

    companion object {
        private val ARABIC_CHAR = Regex("[؀-ۿ]")
        private val ARABIC_MARKS = Regex("[ؐ-ًؚ-ٰٟۖ-ۭـ]")
        private val REF = Regex("\\d+:\\d+")
        private val SPACES = Regex("\\s+")
        private val PUNCT = Regex("[\"'‘’“”,.;!?()\\[\\]{}।॥:—–-]")

        fun normalizeArabic(s: String): String = ARABIC_MARKS.replace(s, "")
            .replace('آ', 'ا').replace('أ', 'ا')
            .replace('إ', 'ا').replace('ٱ', 'ا')
            .replace('ى', 'ي').replace('ة', 'ه')
            .replace('ؤ', 'و').replace('ئ', 'ي')
            .let { SPACES.replace(it, " ") }

        /**
         * বানানের ছোটখাটো পার্থক্য উপেক্ষা করে: য়/ড়/ঢ় এর দুই রূপ, ZWNJ, ী/ি, ূ/ু,
         * বাংলা অঙ্ক। আয়াত নম্বরের ":" রাখা হয়, যাতে "২:২৫৫" খোঁজা যায়।
         */
        fun normalizeBangla(s: String): String {
            val sb = StringBuilder(s.length)
            var i = 0
            while (i < s.length) {
                val c = s[i]
                val next = if (i + 1 < s.length) s[i + 1] else '\u0000'
                if (next == '়' && (c == 'য' || c == 'ড' || c == 'ঢ')) {
                    sb.append(
                        when (c) {
                            'য' -> '\u09DF'; 'ড' -> '\u09DC'; else -> '\u09DD'
                        }
                    )
                    i += 2
                    continue
                }
                when (c) {
                    '‌', '‍', '়' -> {}
                    'ী' -> sb.append('ি')
                    'ূ' -> sb.append('ু')
                    'ঈ' -> sb.append('ই')
                    'ঊ' -> sb.append('উ')
                    in '০'..'৯' -> sb.append('0' + (c - '০'))
                    else -> sb.append(c.lowercaseChar())
                }
                i++
            }
            // আয়াত নম্বরের ":" রেখে বাকি যতিচিহ্ন সরাই
            val withRefs = Regex("(\\d)\\s*:\\s*(\\d)").replace(sb.toString(), "$1#$2")
            return SPACES.replace(PUNCT.replace(withRefs, " "), " ").replace('#', ':').trim()
                .let { " $it " }
        }
    }
}
