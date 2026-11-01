#!/usr/bin/env python3
"""
Build-time checklist audio (PRD 7.1 A1): Sarvam Bulbul TTS -> app/src/main/assets/audio/<id>_<lang>.(mp3|wav)

    SARVAM_API_KEY=... python3 tools/generate_audio.py            # Odia only
    SARVAM_API_KEY=... python3 tools/generate_audio.py --lang or hi

The key is read from the environment and is never written into the app (PRD 7.4, AT-18).
Existing files are skipped, so re-running only fills gaps. Listen to every clip, and have an
Odia speaker approve them, before release.

Check the request fields against the current Sarvam API docs (https://docs.sarvam.ai) before
running; this script could not be tested against the live API when it was written.
"""
import argparse
import base64
import json
import os
import pathlib
import sys
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
CHECKLIST = ROOT / "app/src/main/assets/checklist.json"
OUT = ROOT / "app/src/main/assets/audio"
API = "https://api.sarvam.ai/text-to-speech"
LANG_CODES = {"or": "od-IN", "hi": "hi-IN", "en": "en-IN"}


def tts(key: str, text: str, lang: str, model: str, speaker: str | None) -> bytes:
    body = {"text": text, "target_language_code": LANG_CODES[lang], "model": model, "output_audio_codec": "mp3"}
    if speaker:
        body["speaker"] = speaker
    req = urllib.request.Request(
        API, data=json.dumps(body).encode(), method="POST",
        headers={"api-subscription-key": key, "Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=60) as r:
        data = json.load(r)
    return base64.b64decode(data["audios"][0])


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--lang", nargs="+", default=["or"], choices=sorted(LANG_CODES))
    ap.add_argument("--model", default="bulbul:v3")
    ap.add_argument("--speaker", default=None)
    args = ap.parse_args()
    key = os.environ.get("SARVAM_API_KEY")
    if not key:
        print("Set SARVAM_API_KEY", file=sys.stderr)
        return 2
    OUT.mkdir(parents=True, exist_ok=True)
    items = json.loads(CHECKLIST.read_text(encoding="utf-8"))
    chars = 0
    for item in items:
        for lang in args.lang:
            if any((OUT / f"{item['id']}_{lang}.{ext}").exists() for ext in ("mp3", "wav")):
                continue
            audio = tts(key, item[lang], lang, args.model, args.speaker)
            ext = "wav" if audio[:4] == b"RIFF" else "mp3"
            (OUT / f"{item['id']}_{lang}.{ext}").write_bytes(audio)
            chars += len(item[lang])
            print(f"wrote {item['id']}_{lang}.{ext}")
    print(f"done; {chars} characters sent (Bulbul: about Rs 30 per 10K characters)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
