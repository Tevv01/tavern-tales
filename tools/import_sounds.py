#!/usr/bin/env python3
"""Turn downloaded recordings into the app's built-in sounds.

1. Put a downloaded file in sound-sources/<sound>/ (folder names match app/src/main/assets/sounds/).
2. Describe it in tools/sound_sources.json (file, title, author, source URL, license; optional trim).
3. Run:  python tools/import_sounds.py

For each entry the script resamples to 44.1 kHz stereo, picks a segment, makes loops seamless with an
equal-power crossfade of the end into the start, trims silence off one-shots, matches loudness to the
other built-in sounds, and writes Ogg Vorbis into the assets folder. SOUND_CREDITS.md is regenerated
from the manifest; sounds without an entry keep their synthesized version from generate_sounds.py.

Manifest fields per sound (only "file" is required to process; the rest feed the credits):
  file      path relative to sound-sources/
  title, author, source, license
  start     seconds into the file, or "auto" (default): steadiest stretch for loops, first onset for one-shots
  length    seconds to keep (default 45 for loops, whole sound for one-shots)
  gain_db   extra gain after loudness matching (default 0)

Options: --out DIR (default: the assets folder), --manifest FILE, --credits FILE

Needs: pip install -r tools/requirements.txt
"""
import argparse
import json
import math
import sys
from pathlib import Path

import numpy as np
import soundfile
from scipy import signal

sys.path.insert(0, str(Path(__file__).parent))
from generate_sounds import SR, write  # noqa: E402  (shared encoder settings)

ROOT = Path(__file__).resolve().parent.parent
SOURCES = ROOT / "sound-sources"
MANIFEST = ROOT / "tools" / "sound_sources.json"
ASSETS = ROOT / "app" / "src" / "main" / "assets" / "sounds"
CREDITS = ROOT / "SOUND_CREDITS.md"

# Loudness targets (RMS dBFS) matching generate_sounds.py, so replaced and synthesized sounds sit together.
TARGET_RMS = {
    "tavern_music": -20, "town_crowd": -22, "tavern_chatter": -22, "market_crowd": -21, "rain": -22, "wind": -22,
    "hearth_fire": -24, "torches": -25, "horse_cart": -23, "birdsong": -24, "forest_breeze": -25, "stream": -23,
    "crickets": -27, "water_drips": -26, "dark_drone": -24, "chains": -27, "cave_wind": -24, "church_bell": -20,
}
EVENT_RMS = -17
DEFAULT_LOOP_SECONDS = 45
CROSSFADE_SECONDS = 2.0


def is_one_shot(name):
    return name.startswith("event_") or name == "church_bell"


def load(path):
    x, sr = soundfile.read(str(path), always_2d=True, dtype="float64")
    if x.shape[1] == 1:
        x = np.repeat(x, 2, axis=1)
    x = x[:, :2]
    if sr != SR:
        g = math.gcd(SR, sr)
        x = signal.resample_poly(x, SR // g, sr // g, axis=0)
    return x


def block_rms(x, block):
    n = len(x) // block
    return np.sqrt(np.mean(x[:n * block].reshape(n, block, -1) ** 2, axis=(1, 2)) + 1e-12)


def steadiest_start(x, length):
    """Start (samples) of the window whose loudness varies least, ignoring quiet stretches."""
    block = SR // 2
    levels = 20 * np.log10(block_rms(x, block))
    width = int(length / block)
    if len(levels) <= width:
        return 0
    best, best_score = 0, None
    floor = np.median(levels) - 6
    for i in range(len(levels) - width + 1):
        window = levels[i:i + width]
        score = np.std(window) + 2 * np.sum(window < floor) / width * 10  # penalise dropouts
        if best_score is None or score < best_score:
            best, best_score = i, score
    return best * block


def first_onset(x, threshold_db=-45):
    env = np.max(np.abs(x), axis=1)
    above = np.nonzero(env > 10 ** (threshold_db / 20) * env.max())[0]
    return max(0, above[0] - SR // 100) if len(above) else 0


def make_loop(x, start, length):
    """Seamless loop of `length` samples: the material after the end is crossfaded into the start."""
    fade = int(CROSSFADE_SECONDS * SR)
    needed = length + fade
    if start + needed > len(x):
        start = max(0, len(x) - needed)
    if len(x) < needed:
        fade = max(SR // 10, (len(x) - SR) // 3)
        length = len(x) - fade
        print(f"    short recording: loop is {length / SR:.1f} s")
    seg = x[start:start + length + fade]
    out = seg[:length].copy()
    t = np.linspace(0, 1, fade)[:, None]
    out[:fade] = seg[:fade] * np.sin(t * np.pi / 2) + seg[length:length + fade] * np.cos(t * np.pi / 2)
    return out


def make_one_shot(x, start, length):
    seg = x[start:start + length] if length else x[start:]
    tail = 10 ** (-60 / 20) * np.max(np.abs(seg))
    loud = np.nonzero(np.max(np.abs(seg), axis=1) > tail)[0]
    seg = seg[:loud[-1] + SR // 4] if len(loud) else seg
    seg = seg.copy()
    fade_in, fade_out = min(len(seg), SR // 200), min(len(seg), int(0.3 * SR))
    seg[:fade_in] *= np.linspace(0, 1, fade_in)[:, None]
    seg[-fade_out:] *= np.linspace(1, 0, fade_out)[:, None] ** 2
    return seg


def match_loudness(x, rms_db, gain_db=0.0, peak=0.9):  # headroom: Vorbis overshoots peaks slightly
    active = x[np.max(np.abs(x), axis=1) > 1e-3 * np.max(np.abs(x))]
    rms = np.sqrt(np.mean(active ** 2)) + 1e-12
    x = x * 10 ** ((rms_db + gain_db) / 20) / rms
    top = np.max(np.abs(x))
    if top > peak:
        print(f"    peak-limited by {20 * np.log10(top / peak):.1f} dB")
        x *= peak / top
    return x


def process(name, entry):
    x = load(SOURCES / entry["file"])
    one_shot = is_one_shot(name)
    length = entry.get("length")
    length = int(length * SR) if length else (0 if one_shot else DEFAULT_LOOP_SECONDS * SR)
    start = entry.get("start", "auto")
    if start == "auto":
        start = first_onset(x) if one_shot else steadiest_start(x, length + CROSSFADE_SECONDS * SR)
    else:
        start = int(start * SR)
    out = make_one_shot(x, start, length) if one_shot else make_loop(x, start, length)
    rms = EVENT_RMS if name.startswith("event_") else TARGET_RMS.get(name, -22)
    return match_loudness(out, rms, entry.get("gain_db", 0.0)), start / SR


def write_credits(manifest, names, path):
    lines = [
        "# Sound credits",
        "",
        "Built-in sounds in `app/src/main/assets/sounds/`. Generated by `tools/import_sounds.py` from",
        "`tools/sound_sources.json`; edit the manifest, not this file.",
        "",
        "| Sound | Source | Author | License |",
        "|---|---|---|---|",
    ]
    for name in names:
        e = manifest.get(name)
        if e:
            title = f"[{e.get('title', e['file'])}]({e['source']})" if e.get("source") else e.get("title", e["file"])
            lines.append(f"| `{name}` | {title} | {e.get('author', '?')} | {e.get('license', '?')} |")
        else:
            lines.append(f"| `{name}` | Synthesized by `tools/generate_sounds.py` | Tavern Tales | Public domain |")
    path.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", type=Path, default=ASSETS)
    parser.add_argument("--manifest", type=Path, default=MANIFEST)
    parser.add_argument("--credits", type=Path, default=CREDITS)
    args = parser.parse_args()
    manifest = json.loads(args.manifest.read_text(encoding="utf-8")) if args.manifest.exists() else {}
    names = sorted(p.stem for p in ASSETS.glob("*.ogg"))
    for name, entry in manifest.items():
        if name not in names:
            sys.exit(f"{name}: not a built-in sound (expected one of {', '.join(names)})")
        if not (SOURCES / entry["file"]).is_file():
            print(f"  {name}: {entry['file']} not found in sound-sources/, keeping the current file")
            continue
        out, start = process(name, entry)
        print(f"  {name}: from {entry['file']} @ {start:.1f} s")
        args.out.mkdir(parents=True, exist_ok=True)
        write(args.out, name, out)
    write_credits(manifest, names, args.credits)
    print(f"Wrote {args.credits.name}")


if __name__ == "__main__":
    main()
