"""হাদিস ডেটাসেট পড়া, লেখা পরিষ্কার করা, খোঁজা।"""
import json
import re

BOOKS = ['bukhari', 'muslim', 'abudawud', 'tirmidhi', 'nasai', 'ibnmajah']
BOOK_BN = {
    'bukhari': 'সহিহ বুখারী', 'muslim': 'সহিহ মুসলিম', 'abudawud': 'সুনানে আবু দাউদ',
    'tirmidhi': 'জামে তিরমিযী', 'nasai': 'সুনানে নাসাঈ', 'ibnmajah': 'সুনানে ইবনে মাজাহ',
}
GRADE_BN = {
    'sahih': 'সহিহ', 'hasan sahih': 'হাসান সহিহ', 'hasan': 'হাসান',
    'sahih lighairihi': 'সহিহ লিগাইরিহি', 'hasan lighairihi': 'হাসান লিগাইরিহি',
}
_H = None


def load(root='/home/claude/dl/hadith'):
    global _H
    if _H is None:
        _H = {b: {h['hadithnumber']: h for h in json.load(open(f'{root}/ben-{b}.json'))['hadiths']} for b in BOOKS}
    return _H


BN_DIGITS = str.maketrans('0123456789', '০১২৩৪৫৬৭৮৯')


def clean(text: str, number=None) -> str:
    t = text
    # অধ্যায়ের শিরোনাম বাদ: লেখার ভেতরে হাদিসের নিজের নম্বর ("৫০১৩. ") থাকলে সেখান থেকে শুরু
    own = None
    if number is not None:
        own = re.search(r'(?:^|\s)' + str(number).translate(BN_DIGITS) + r'\s?[.।]\s', t)
    if own:
        t = t[own.end():]
    else:
        # নইলে প্রথম "বর্ণিত/বর্ণনা"র আগের শেষ "N. " থেকে
        m = re.search(r'বর্ণিত|বর্ণনা কর|থেকে শুনেছি', t)
        if m:
            nums = list(re.finditer(r'(?:^|\s)[০-৯0-9]+\s?\.\s', t[:m.start()]))
            if nums:
                t = t[nums[-1].end():]
    t = re.sub(r'\s*\[[^\]]*[০-৯0-9][^\]]*\]', '', t)  # ক্রস-রেফারেন্স [৬৬৪৩, ৭৩৭৪; মুসলিম ...]
    # বর্ণনাকারীদের লম্বা সূত্র ("অমুক, তমুক ..... সাহাবী (রাঃ) হতে বর্ণিত") ছেঁটে সাহাবী থেকে শুরু
    dots = list(re.finditer(r'\.{3,}|…', t[:300]))
    if dots:
        t = t[dots[-1].end():]
    t = re.sub(r'^[\s।.]+', '', t)
    t = re.sub(r'\[\d+\]', '', t)
    # শেষের প্রকাশনী/ক্রস-রেফারেন্সের বন্ধনী
    for _ in range(4):
        t2 = re.sub(r'\s*\((?=[^()]*(?:প্রকাশনী|ফাউন্ডেশন|সেন্টার|মুসলিম|আহমাদ|হাঃ|তাওহীদ|[০-৯]{2,}))[^()]*\)\s*[।.]?\s*$', '', t)
        if t2 == t:
            break
        t = t2
    # আলবানীর মান ও তাখরীজের নোট (তিরমিযী ইত্যাদিতে লেখার শেষে থাকে) — মান আমরা আলাদা দেখাই
    t = re.split(r'\s(?:সহীহ|হাসান সহীহ|হাসান|সহীহ লিগাইরিহি|হাসান লিগাইরিহি)\s*[,،।]\s*(?=প্রাগুক্ত|ইবনু মাজাহ|ইবনু মাজা|সহীহা|সহীহাহ|মিশকাত|ইরওয়া|তা[’\']লীক|তাখরীজ|সহীহ আবূ|সহীহ আবু|আর-রাওয|গাইয়াতুল|বুখারী|মুসলিম|নাসাঈ|আবূ দাঊদ|আবূ দাউদ|সহীহুল|যঈফ)', t)[0]
    # শেষে প্রকাশকের মান-মন্তব্য (আমরা আলাদা করে মান দেখাই)
    t = re.sub(r'\s*(?:হাসান সহীহ|সহীহ|হাসান|য[ঈি]ফ|দুর্বল)(?:\s*লিগাইরিহি)?[,।]?\s*(?:সহীহা[^।]*|ইরওয়া[^।]*|মিশকাত[^।]*)?[।.]?\s*$', '', t)
    return re.sub(r'\s+', ' ', t).strip()


def albani_grade(h):
    for g in h.get('grades') or []:
        if g.get('name') == 'Al-Albani':
            return g.get('grade', '')
    return ''


def grade_bn(book, h):
    if book in ('bukhari', 'muslim'):
        return 'সহিহ'
    g = albani_grade(h).strip().lower()
    if not g:  # আলবানীর মান না থাকলে অন্য মুহাদ্দিসের মান
        gs = [x.get('grade', '').strip().lower() for x in (h.get('grades') or [])]
        g = next((x for x in gs if x in GRADE_BN), '')
    return GRADE_BN.get(g, '')  # সহিহ/হাসান নয় হলে খালি → বাদ


def search(book, pattern, limit=6):
    H = load()
    rx = re.compile(pattern)
    out = []
    for n, h in H[book].items():
        c = clean(h['text'], n)
        m = rx.search(c)
        if m:
            out.append((n, c, m.start()))
            if len(out) >= limit:
                break
    return out
