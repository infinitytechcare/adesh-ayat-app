import sys, json, re
sys.path.insert(0, __import__('os').path.dirname(__file__))
from hadith_lib import load, clean, grade_bn, BOOK_BN
from hadith_candidates import C
H=load()
vp=__import__('os').path.join(__import__('os').path.dirname(__file__),'..','app','src','main','assets','verses.json')
d=json.load(open(vp)); VS={f"{v['s']}:{v['a']}" for v in d['verses']}
BN=str.maketrans('0123456789','০১২৩৪৫৬৭৮৯')
problems=[]; hadiths={}; per_verse={}
for b,n,rx,vs in C:
    h=H[b].get(n)
    if not h: problems.append(f'missing {b} {n}'); continue
    t=clean(h['text'],n); g=grade_bn(b,h)
    if not re.search(rx,t): problems.append(f'regex {b} {n}: {t[:90]}')
    if not g: problems.append(f'grade {b} {n}: {h.get("grades")}')
    if len(t)<40: problems.append(f'short {b} {n}: {t}')
    if re.search(r'[؀-ۿ]{3,}',t[:30]): problems.append(f'arabic-start {b} {n}: {t[:60]}')
    for v in vs:
        if v not in VS: problems.append(f'verse {v} not in list ({b} {n})')
    hid=f'{b}:{n}'
    if b=='muslim':
        m=re.search(r'ইসলামি?ক ফাউন্ডেশন[ঃ:.\s-]*([০-৯]+)',h['text'])
        ref=f"{BOOK_BN[b]}, হাদিস {str(n).translate(BN)}"+(f" (ইসলামিক ফাউন্ডেশন {m.group(1)})" if m else '')
    else:
        ref=f"{BOOK_BN[b]}, হাদিস {str(n).translate(BN)}"
    hadiths[hid]={'ref':ref,'grade':g,'text':t}
    for v in vs: per_verse.setdefault(v,[]).append(hid)
# প্রতি আয়াতে সর্বোচ্চ ৩টি: যে হাদিস কম আয়াতের সাথে যুক্ত (বেশি নির্দিষ্ট) সেটি আগে
spec={f'{b}:{n}':len(vs) for b,n,rx,vs in C}
per_verse={v:sorted(ids,key=lambda i:spec[i])[:3] for v,ids in per_verse.items()}
used={i for ids in per_verse.values() for i in ids}
hadiths={k:v for k,v in hadiths.items() if k in used}
print('hadith',len(hadiths),'verses with hadith',len(per_verse))
print('problems',len(problems)); print('\n'.join(problems))
json.dump({'hadiths':hadiths,'byVerse':per_verse},open('hadith_out.json','w'),ensure_ascii=False,indent=1)
from collections import Counter; print('max per verse',max(len(x) for x in per_verse.values()), Counter(len(x) for x in per_verse.values()))
