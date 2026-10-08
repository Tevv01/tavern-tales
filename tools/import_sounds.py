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
  exact_loop  true for material already made to loop (music): used whole, no crossfade
  sparse      seconds between sounds: cut the recording into its separate sounds and scatter them
              with silence between (chains, creaks), instead of looping it continuously
  limit_db    how far peaks may be limited before the whole sound is turned down instead (default 6);
              raise it for clicky material like fire crackle, where short peaks hide limiting well
  stack       one-shots only: list of offsets (s) to layer copies at (one arrow -> a volley)

Options: --out DIR (default: the assets folder), --manifest FILE, --credits FILE,
         --credits-only (only regenerate SOUND_CREDITS.md and the app's credits list)
Arguments: sound names to process only those (default: every manifest entry); the credits always
           cover every built-in sound. A new sound only needs a manifest entry.

The app's Credits screen reads app/src/main/assets/credits/sounds.json, written alongside
SOUND_CREDITS.md from the same manifest.

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
from scipy.ndimage import minimum_filter1d, uniform_filter1d

sys.path.insert(0, str(Path(__file__).parent))
from generate_sounds import SR, write  # noqa: E402  (shared encoder settings)

ROOT = Path(__file__).resolve().parent.parent
SOURCES = ROOT / "sound-sources"
MANIFEST = ROOT / "tools" / "sound_sources.json"
ASSETS = ROOT / "app" / "src" / "main" / "assets" / "sounds"
CREDITS = ROOT / "SOUND_CREDITS.md"
APP_CREDITS = ROOT / "app" / "src" / "main" / "assets" / "credits" / "sounds.json"

# Loudness targets (RMS dBFS) matching generate_sounds.py, so replaced and synthesized sounds sit together.
TARGET_RMS = {
    "tavern_music": -20, "town_crowd": -22, "tavern_chatter": -22, "market_crowd": -21, "rain": -22, "wind": -22,
    "hearth_fire": -24, "torches": -25, "horse_cart": -23, "birdsong": -24, "forest_breeze": -25, "stream": -23,
    "crickets": -27, "water_drips": -26, "dark_drone": -24, "chains": -27, "cave_wind": -24,
    "ocean_waves": -22, "ship_creak": -26, "seagulls": -27, "swamp_frogs": -25, "mud_bubbles": -27, "great_hall": -22,
    "temple_choir": -22, "temple_bells": -27, "blizzard": -21, "wolves": -27,
}
# One-shots are matched on their loudest 400 ms instead (the synthesized events sit around -12 there).
EVENT_LOUDEST = -12
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


def trim_silence(x, threshold_db=-60):
    env = np.max(np.abs(x), axis=1)
    loud = np.nonzero(env > 10 ** (threshold_db / 20) * env.max())[0]
    return x[loud[0]:loud[-1] + 1] if len(loud) else x


def exact_loop(x):
    """For material made to loop (music): keep all of it, only smoothing the seam over 20 ms.

    If it ends in a decay (the last notes ringing out), the loop ends where the decay starts and the
    decay is mixed over the start, like a band going straight into the next round of the tune.
    """
    x = trim_silence(x)  # lossy previews often carry encoder padding at the ends
    block = SR // 10
    levels = 20 * np.log10(block_rms(x, block))
    playing = np.nonzero(levels > np.median(levels) - 10)[0]
    end = min(len(x), (playing[-1] + 2) * block)
    tail = x[end:]
    if len(tail) > SR // 2:
        print(f"    {len(tail) / SR:.1f} s decay at the end mixed over the start")
        x = x[:end].copy()
        tail = tail[:len(x)]
        x[:len(tail)] += tail
    fade = int(0.02 * SR)
    out = x[:-fade].copy()
    t = np.linspace(0, 1, fade)[:, None]
    out[:fade] = x[:fade] * np.sin(t * np.pi / 2) + x[-fade:] * np.cos(t * np.pi / 2)
    return out


def sparse_loop(x, length, mean_gap, seed=5):
    """Cut the recording into its individual sounds and scatter them with silence between (e.g. chains)."""
    rng = np.random.default_rng(seed)
    block = SR // 50
    levels = 20 * np.log10(block_rms(x, block))
    active = levels > levels.max() - 30
    events, start = [], None
    for i, on in enumerate(np.append(active, False)):
        if on and start is None:
            start = i
        elif not on and start is not None:
            if events and start - events[-1][1] < 8:  # merge sounds less than 160 ms apart
                events[-1] = (events[-1][0], i)
            else:
                events.append((start, i))
            start = None
    events = [(max(0, a - 3) * block, min(len(x), (b + 3) * block)) for a, b in events if b - a >= 5]
    if not events:
        sys.exit("no distinct sounds found for sparse loop")
    buf = np.zeros((length + 10 * SR, 2))
    t = rng.uniform(0, mean_gap) * SR
    while t < length:
        a, b = events[rng.integers(len(events))]
        piece = x[a:b].copy()
        fade = min(len(piece) // 2, SR // 100)
        piece[:fade] *= np.linspace(0, 1, fade)[:, None]
        piece[-fade:] *= np.linspace(1, 0, fade)[:, None]
        s = int(t)
        buf[s:s + len(piece)] += piece * rng.uniform(0.5, 1.0)
        t += len(piece) + rng.exponential(mean_gap) * SR
    out = buf[:length].copy()  # wrap the overhang onto the start so it loops
    tail = buf[length:]
    out[:len(tail)] += tail[:length]
    print(f"    sparse: {len(events)} distinct sounds scattered")
    return out


def stack(x, offsets):
    """Layer copies of a one-shot at the given offsets (s), alternating left and right."""
    out = np.zeros((len(x) + int(max(offsets) * SR), 2))
    for i, offset in enumerate(offsets):
        s = int(offset * SR)
        angle = (0.5 + (0.35 if i % 2 else -0.35)) * np.pi / 2
        out[s:s + len(x)] += x * np.array([np.cos(angle), np.sin(angle)]) * np.sqrt(2) * (1 - 0.1 * i)
    return out


def momentary_max_rms(x):
    """Loudest 400 ms stretch; used for one-shots, whose quiet tails would skew an overall average."""
    w = int(0.4 * SR)
    if len(x) <= w:
        return np.sqrt(np.mean(x ** 2))
    return np.sqrt(uniform_filter1d(np.mean(x ** 2, axis=1), w).max())


def limit(x, ceiling, max_reduction_db, circular):
    """Peak limiter: pulls peaks above `ceiling` down by up to `max_reduction_db`; if peaks are hotter
    than that, the whole sound is turned down instead, so transients aren't squashed flat."""
    allowed = ceiling * 10 ** (max_reduction_db / 20)
    top = np.max(np.abs(x))
    if top > allowed:
        print(f"    turned down {20 * np.log10(top / allowed):.1f} dB (peaks beyond {max_reduction_db} dB of limiting)")
        x = x * allowed / top
    if np.max(np.abs(x)) <= ceiling:
        return x
    w = int(0.01 * SR)
    amp = np.max(np.abs(x), axis=1)
    if circular:  # loops: the gain curve must also be seamless
        amp = np.concatenate([amp[-2 * w:], amp, amp[:2 * w]])
    gain = np.minimum(1.0, ceiling / np.maximum(amp, 1e-9))
    # Hold the minimum over +-w, then average over +-w: every sample within w of a peak stays at or
    # below that peak's required gain, and the gain changes smoothly (10 ms attack and release).
    gain = minimum_filter1d(gain, 2 * w + 1)
    gain = uniform_filter1d(gain, 2 * w + 1)
    if circular:
        gain = gain[2 * w:-2 * w]
    return x * gain[:, None]


def match_loudness(x, rms_db, gain_db, one_shot, limit_db, ceiling=0.85):  # headroom: Vorbis overshoots peaks
    rms = momentary_max_rms(x) if one_shot else np.sqrt(np.mean(x ** 2))
    x = x * 10 ** ((rms_db + gain_db) / 20) / (rms + 1e-12)
    return limit(x, ceiling, max_reduction_db=limit_db, circular=not one_shot)


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
    if one_shot:
        out = make_one_shot(x, start, length)
        if entry.get("stack"):
            out = stack(out, entry["stack"])
    elif entry.get("exact_loop"):
        out, start = exact_loop(x), 0
    elif entry.get("sparse"):
        out, start = sparse_loop(x, length, entry["sparse"]), 0
    else:
        out = make_loop(x, start, length)
    rms = EVENT_LOUDEST if one_shot else TARGET_RMS.get(name, -22)
    return match_loudness(out, rms, entry.get("gain_db", 0.0), one_shot, entry.get("limit_db", 6)), start / SR


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


def write_app_credits(manifest, names, path):
    """The same credits as SOUND_CREDITS.md, as JSON for the app's Credits screen."""
    entries = []
    for name in names:
        e = manifest.get(name)
        if e:
            entries.append({"sound": name, "title": e.get("title", e["file"]), "author": e.get("author", ""),
                            "license": e.get("license", ""), "source": e.get("source", "")})
        else:
            entries.append({"sound": name, "title": "Synthesized for Tavern Tales", "author": "Tavern Tales",
                            "license": "Public domain", "source": ""})
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(entries, indent=1, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", type=Path, default=ASSETS)
    parser.add_argument("--manifest", type=Path, default=MANIFEST)
    parser.add_argument("--credits", type=Path, default=CREDITS)
    parser.add_argument("--credits-only", action="store_true")
    parser.add_argument("sounds", nargs="*", help="only process these (default: every manifest entry)")
    args = parser.parse_args()
    manifest = json.loads(args.manifest.read_text(encoding="utf-8")) if args.manifest.exists() else {}
    names = sorted({p.stem for p in ASSETS.glob("*.ogg")} | set(manifest))
    for sound in args.sounds:
        if sound not in manifest:
            sys.exit(f"{sound}: no entry in {args.manifest.name}")
    todo = {} if args.credits_only else {k: v for k, v in manifest.items() if not args.sounds or k in args.sounds}
    for name, entry in todo.items():
        if not (SOURCES / entry["file"]).is_file():
            print(f"  {name}: {entry['file']} not found in sound-sources/, keeping the current file")
            continue
        out, start = process(name, entry)
        print(f"  {name}: from {entry['file']} @ {start:.1f} s")
        args.out.mkdir(parents=True, exist_ok=True)
        write(args.out, name, out)
    write_credits(manifest, names, args.credits)
    write_app_credits(manifest, names, APP_CREDITS)
    print(f"Wrote {args.credits.name} and {APP_CREDITS.relative_to(ROOT).as_posix()}")


if __name__ == "__main__":
    main()
