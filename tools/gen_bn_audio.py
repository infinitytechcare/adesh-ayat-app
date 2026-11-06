"""সব বাংলা অর্থের অডিও তৈরি করে: docs/audio/bn_<voice>/SSSAAA.mp3
ফাইলের নাম = যে আয়াতের সাথে অর্থটি লেখা আছে (গ্রুপের ক্ষেত্রে গ্রুপের প্রথম আয়াত)।
ব্যবহার: python3 gen_bn_audio.py <verses.json> <out_root>
"""
import asyncio
import json
import os
import re
import sys

import certifi

CA = os.path.expanduser('~/.ccr/ca-bundle.crt')
if os.path.exists(CA):
    certifi.where = lambda: CA
import edge_tts  # noqa: E402

VOICES = {'bn_pradeep': 'bn-BD-PradeepNeural', 'bn_nabanita': 'bn-BD-NabanitaNeural'}

# উচ্চারণ-সংশোধন তালিকা: ভুল শোনা শব্দ এখানে যোগ করুন (লেখা -> পড়ার বানান)
PRONUNCIATION_FIXES = {
}


def speech_text(meaning: str) -> str:
    t = meaning
    # পাঠকের জন্য নোট/রেফারেন্স — শোনার সময় দরকার নেই
    t = re.sub(r'\s*\((?:নোট|অনুরূপ|একই নির্দেশ|অনেক মুফাসসিরের)[^)]*\)', '', t)
    t = t.replace('...', ', ').replace('…', ', ').replace('—', ', ').replace('/', ' বা ')
    t = re.sub(r'\s+-\s+', ', ', t)  # তালিকার "- " চিহ্ন
    # য়/ড়/ঢ় — দুই অংশে লেখা রূপকে একক অক্ষরে (নইলে TTS ভুল পড়ে)
    t = t.replace('য়', 'য়').replace('ড়', 'ড়').replace('ঢ়', 'ঢ়')
    for wrong, right in PRONUNCIATION_FIXES.items():
        t = t.replace(wrong, right)
    return re.sub(r'\s+', ' ', t).strip()


async def make(sem, voice, text, out):
    if os.path.exists(out) and os.path.getsize(out) > 0:
        return 'skip'
    async with sem:
        for attempt in range(4):
            try:
                tmp = out + '.part'
                await edge_tts.Communicate(text, voice).save(tmp)
                if os.path.getsize(tmp) == 0:
                    raise RuntimeError('empty')
                os.replace(tmp, out)
                return 'ok'
            except Exception as e:  # noqa: BLE001
                if attempt == 3:
                    return f'FAIL {out}: {e}'
                await asyncio.sleep(2 * (attempt + 1))


async def main(verses_path, out_root):
    d = json.load(open(verses_path, encoding='utf-8'))
    jobs = []
    for v in d['verses']:
        if not v['bn']:
            continue
        key = f"{v['s']:03d}{v['a']:03d}"
        text = speech_text(v['bn'])
        jobs.append((key, text))
    sem = asyncio.Semaphore(4)
    tasks = []
    for folder, voice in VOICES.items():
        os.makedirs(os.path.join(out_root, folder), exist_ok=True)
        for key, text in jobs:
            tasks.append(make(sem, voice, text, os.path.join(out_root, folder, key + '.mp3')))
    results = await asyncio.gather(*tasks)
    fails = [r for r in results if r.startswith('FAIL')]
    print(f'meanings={len(jobs)} files={len(tasks)} ok={results.count("ok")} skip={results.count("skip")} fail={len(fails)}')
    for f in fails[:20]:
        print(f)


if __name__ == '__main__':
    asyncio.run(main(sys.argv[1], sys.argv[2]))
