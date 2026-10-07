#!/usr/bin/env python3
"""Generate the built-in sounds bundled with Tavern Tales (scene ambience loops and events).

Everything is synthesized from scratch (no samples), so the output is free to use and share.
Loops are seamless: noise textures are shaped in the frequency domain over exactly the loop length
(which makes them periodic), envelopes and tones use whole cycles over the loop, and events or
reverb tails that run past the end are wrapped around to the start.

Output is Ogg Vorbis (gapless in ExoPlayer) in app/src/main/assets/sounds/, referenced from
DefaultLibrary.kt as asset:///sounds/<name>.ogg. Pass --wav to write WAV files instead.

Usage:  python tools/generate_sounds.py [output_dir] [--wav]
Needs:  pip install -r tools/requirements.txt
"""
import sys
from pathlib import Path

import numpy as np
from scipy import signal
from scipy.io import wavfile

SR = 44100
LOOP_SECONDS = 30
rng = np.random.default_rng(7)


# ---------------------------------------------------------------------------- helpers

def secs(t):
    return int(round(t * SR))


def pan(mono, position):
    """Equal-power pan; position -1 (left) .. 1 (right). Returns (n, 2)."""
    angle = (position + 1) * np.pi / 4
    return np.stack([mono * np.cos(angle), mono * np.sin(angle)], axis=1)


def band_shape(lo, hi, order=2):
    """Magnitude response of a band-pass, for use with periodic_noise / circular_filter."""
    def shape(f):
        f = np.maximum(f, 1e-3)
        return 1 / np.sqrt(1 + (lo / f) ** (2 * order)) / np.sqrt(1 + (f / hi) ** (2 * order))
    return shape


def periodic_noise(n, shape):
    """Unit-RMS noise of length n, spectrally shaped by shape(freqs), exactly periodic over n."""
    spectrum = np.fft.rfft(rng.standard_normal(n)) * shape(np.fft.rfftfreq(n, 1 / SR))
    out = np.fft.irfft(spectrum, n)
    return out / (np.std(out) + 1e-12)


def periodic_lfo(n, max_cycles, min_cycles=1):
    """Smooth random envelope in [-1, 1], exactly periodic over n samples."""
    t = np.arange(n) / n
    out = np.zeros(n)
    for k in range(min_cycles, max_cycles + 1):
        out += rng.normal() / k * np.sin(2 * np.pi * k * t + rng.uniform(0, 2 * np.pi))
    return out / (np.max(np.abs(out)) + 1e-12)


def circular_filter(x, shape):
    """Filter a loop (n, 2) without a seam, by multiplying its spectrum."""
    f = np.fft.rfftfreq(len(x), 1 / SR)
    return np.fft.irfft(np.fft.rfft(x, axis=0) * shape(f)[:, None], len(x), axis=0)


def fold(buf, n):
    """Wrap everything past n back onto the start so the result loops seamlessly."""
    out = np.zeros((n,) + buf.shape[1:])
    for start in range(0, len(buf), n):
        chunk = buf[start:start + n]
        out[:len(chunk)] += chunk
    return out


def reverb(x, seconds, wet, lowpass=5000, circular=True):
    """Diffuse stereo reverb from a decaying noise impulse response. Circular for loops."""
    m = secs(seconds)
    env = 10 ** (-3 * np.arange(m) / m)  # -60 dB over `seconds`
    b, a = signal.butter(1, lowpass / (SR / 2))
    n = len(x) if circular else len(x) + m - 1
    out = np.zeros((n, 2))
    for ch in range(2):
        ir = signal.lfilter(b, a, rng.standard_normal(m)) * env
        ir /= np.sqrt(np.sum(ir ** 2))
        if circular:
            wet_sig = np.fft.irfft(np.fft.rfft(x[:, ch]) * np.fft.rfft(fold(ir, n)), n)
        else:
            wet_sig = signal.fftconvolve(x[:, ch], ir)[:n]
        out[:len(x), ch] += (1 - wet) * x[:, ch]
        out[:, ch] += wet * wet_sig
    return out


def place(buf, at_seconds, stereo):
    s = secs(at_seconds)
    if s < 0:  # timing jitter can push the very first event slightly before zero
        stereo, s = stereo[-s:], 0
    end = min(len(buf), s + len(stereo))
    if s < len(buf):
        buf[s:end] += stereo[:end - s]


def normalize(x, rms_db, peak=0.89):
    x = x * (10 ** (rms_db / 20) / (np.sqrt(np.mean(x ** 2)) + 1e-12))
    top = np.max(np.abs(x))
    return x * (peak / top) if top > peak else x


def write(out_dir, name, x, wav=False):
    x = np.clip(x, -1, 1)
    if wav:
        path = out_dir / f"{name}.wav"
        wavfile.write(path, SR, (x * 32767).astype(np.int16))
    else:
        import soundfile
        path = out_dir / f"{name}.ogg"
        # Written in chunks: one large write overflows libsndfile's stack in its Vorbis encoder on Windows.
        with soundfile.SoundFile(str(path), 'w', SR, 2, format='OGG', subtype='VORBIS',
                                 compression_level=0.55) as f:
            for start in range(0, len(x), 4096):
                f.write(x[start:start + 4096])
    print(f"  {path.name:<24} {len(x) / SR:5.1f} s  {path.stat().st_size / 1024:6.0f} KB")


def filtered_noise(m, lo=None, hi=None, order=2):
    """Plain (non-periodic) filtered noise for one-shot sounds."""
    x = rng.standard_normal(m)
    if lo and hi:
        b, a = signal.butter(order, [lo / (SR / 2), hi / (SR / 2)], btype='band')
    elif hi:
        b, a = signal.butter(order, hi / (SR / 2))
    else:
        b, a = signal.butter(order, lo / (SR / 2), btype='high')
    return signal.lfilter(b, a, x)


def periodic_tone(n, freq):
    """Rounds freq so a whole number of cycles fits the loop."""
    return max(1, round(freq * n / SR)) * SR / n


def ring(freq, ratios, amps, decays, dur):
    """Inharmonic struck-metal/glass sound."""
    t = np.arange(secs(dur)) / SR
    out = np.zeros(len(t))
    for r, a, d in zip(ratios, amps, decays):
        out += a * np.sin(2 * np.pi * freq * r * t + rng.uniform(0, 6.28)) * np.exp(-t / d)
    fade = min(len(out), secs(0.05))  # avoid a click if it is still ringing at the end
    out[-fade:] *= np.linspace(1, 0, fade)
    return out


def chirp(f0, f1, dur, shape=1.0):
    t = np.arange(secs(dur)) / SR
    f = f0 + (f1 - f0) * (t / dur) ** shape
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.sin(np.pi * t / dur) ** 2


def swell(t, attack, hold, release):
    """Smooth attack/hold/release envelope over time array t."""
    up = np.clip(t / attack, 0, 1)
    down = np.clip(1 - (t - attack - hold) / release, 0, 1)
    return (0.5 - 0.5 * np.cos(np.pi * up)) * (0.5 - 0.5 * np.cos(np.pi * down))


def finish_event(x, reverb_s, wet, lowpass=6000, rms_db=-17):
    """Reverb, short fade-out and loudness for one-shot events."""
    out = reverb(x if x.ndim == 2 else pan(x, 0), reverb_s, wet, lowpass, circular=False)
    fade = secs(0.3)
    out[-fade:] *= np.linspace(1, 0, fade)[:, None]
    return normalize(out, rms_db, peak=0.95)


def note_freq(name):
    """'A4' -> 440.0; supports sharps like 'F#3'."""
    names = {'C': 0, 'D': 2, 'E': 4, 'F': 5, 'G': 7, 'A': 9, 'B': 11}
    semis = names[name[0]] + (1 if '#' in name else 0)
    octave = int(name[-1])
    return 440.0 * 2 ** ((semis + 12 * (octave + 1) - 69) / 12)


# ---------------------------------------------------------------------------- sounds

def rain(n):
    out = np.zeros((n, 2))
    swell = 1 + 0.15 * periodic_lfo(n, 6)
    for ch in range(2):
        hiss = periodic_noise(n, lambda f: band_shape(400, 9000)(f) / np.sqrt(np.maximum(f, 20) / 400))
        out[:, ch] = 0.25 * hiss * swell

    buf = np.zeros((n + SR, 2))
    for _ in range(int(45 * n / SR)):  # light droplets
        dur = rng.uniform(0.004, 0.02)
        t = np.arange(secs(dur)) / SR
        drop = np.sin(2 * np.pi * rng.uniform(1500, 6000) * t * (1 + t * rng.uniform(5, 30))) * np.exp(-t / (dur / 4))
        place(buf, rng.uniform(0, n / SR), pan(drop * 0.3 * rng.uniform(0.1, 1) ** 2, rng.uniform(-1, 1)))
    b, a = signal.butter(2, [300 / (SR / 2), 2500 / (SR / 2)], btype='band')
    for _ in range(int(6 * n / SR)):  # heavier splats on roofs
        dur = rng.uniform(0.02, 0.05)
        t = np.arange(secs(dur)) / SR
        splat = signal.lfilter(b, a, rng.standard_normal(len(t))) * np.exp(-t / (dur / 5))
        place(buf, rng.uniform(0, n / SR), pan(splat * 0.25 * rng.uniform(0.2, 1), rng.uniform(-1, 1)))
    return normalize(out + fold(buf, n), -22)


def wind(n):
    out = np.zeros((n, 2))
    gusts = periodic_lfo(n, 5)
    for lo, hi, gain in [(60, 250, 1.0), (250, 700, 0.6), (600, 1400, 0.3), (1400, 3200, 0.1)]:
        detail = periodic_lfo(n, 14, 3)
        env = np.clip(0.55 + 0.45 * (0.75 * gusts + 0.25 * detail), 0.03, None) ** (1 + lo / 500)
        for ch in range(2):
            out[:, ch] += gain * periodic_noise(n, band_shape(lo, hi, 3)) * env
    return normalize(out, -22)


VOWELS = [(730, 1090, 2440), (270, 2290, 3010), (530, 1840, 2480), (570, 840, 2410),
          (440, 1020, 2240), (300, 870, 2240), (660, 1720, 2410), (490, 1350, 1690)]


def syllable(f0, dur, female, vowel=None, glide=0.15):
    m = secs(dur)
    t = np.arange(m) / SR
    pitch = f0 * (1 + rng.uniform(-glide, glide) * t / dur)
    src = signal.sawtooth(2 * np.pi * np.cumsum(pitch) / SR) + 0.15 * rng.standard_normal(m)
    vowel = vowel or VOWELS[rng.integers(len(VOWELS))]
    scale = 1.15 if female else 1.0
    out = np.zeros(m)
    for i, (formant, gain) in enumerate(zip(vowel, (1.0, 0.5, 0.25))):
        b, a = signal.iirpeak(min(formant * scale, 0.45 * SR), 5 + 3 * i, SR)
        out += gain * signal.lfilter(b, a, src)
    out *= np.sin(np.pi * t / dur) ** 1.5
    if rng.random() < 0.5:  # consonant hiss at the start
        k = min(m, secs(rng.uniform(0.015, 0.04)))
        bb, ab = signal.butter(2, [2000 / (SR / 2), 7000 / (SR / 2)], btype='band')
        out[:k] += 0.6 * signal.lfilter(bb, ab, rng.standard_normal(k)) * np.linspace(1, 0, k)
    return out


def footstep(cobble=True):
    dur = 0.08
    t = np.arange(secs(dur)) / SR
    b, a = signal.butter(2, 700 / (SR / 2))
    thump = signal.lfilter(b, a, rng.standard_normal(len(t))) * np.exp(-t / 0.015) * 4
    if cobble:
        bb, ab = signal.butter(2, [2000 / (SR / 2), 5000 / (SR / 2)], btype='band')
        thump += 0.4 * signal.lfilter(bb, ab, rng.standard_normal(len(t))) * np.exp(-t / 0.006)
    return thump


def babble(n, voices, gain_range=(0.2, 1.0), pause=(0.4, 3.0), room=1.2, wet=0.35):
    """Murmur of many unintelligible voices, unit RMS, seamless over n."""
    buf = np.zeros((n + 2 * SR, 2))
    for _ in range(voices):
        female = rng.random() < 0.5
        f0 = rng.uniform(170, 250) if female else rng.uniform(90, 140)
        position, gain = rng.uniform(-0.9, 0.9), rng.uniform(*gain_range)
        t = rng.uniform(0, 3)
        while t < n / SR:
            for _ in range(rng.integers(3, 12)):
                dur = rng.uniform(0.09, 0.26)
                place(buf, t, pan(syllable(f0 * rng.uniform(0.92, 1.08), dur, female) * gain, position))
                t += dur * rng.uniform(0.85, 1.1)
            t += rng.uniform(*pause)
    voices = circular_filter(fold(buf, n), band_shape(150, 3500))
    return reverb(voices / (np.std(voices) + 1e-12), room, wet=wet)


def peak_scaled(x, level):
    return level * x / (np.max(np.abs(x)) + 1e-12)


def passersby(n, per_second=0.4):
    """People walking past on cobblestones."""
    steps = np.zeros((n + 10 * SR, 2))
    for _ in range(int(n / SR * per_second)):
        start, interval = rng.uniform(0, n / SR), rng.uniform(0.48, 0.6)
        count = rng.integers(8, 18)
        direction = rng.choice([-1, 1])
        loudness = rng.uniform(0.2, 0.8)
        for k in range(count):
            progress = k / (count - 1)
            near = np.sin(np.pi * progress)  # loudest when passing in front
            place(steps, start + k * interval + rng.normal(0, 0.01),
                  pan(footstep() * loudness * (0.25 + 0.75 * near), direction * (2 * progress - 1) * 0.9))
    return fold(steps, n)


def town_crowd(n):
    # Voices are at unit RMS; sparse footsteps are scaled by their peak to sit just above the murmur.
    return normalize(babble(n, 22) + peak_scaled(passersby(n), 1.5), -22)


def laugh(female):
    f0 = rng.uniform(220, 300) if female else rng.uniform(120, 170)
    parts = []
    for k in range(rng.integers(4, 8)):
        parts.append(syllable(f0 * (1 - 0.04 * k), 0.11, female, vowel=VOWELS[0], glide=0.05))
        parts.append(np.zeros(secs(0.05)))
    return np.concatenate(parts)


def clink():
    """Mug or glass knocked on a table."""
    return ring(rng.uniform(1200, 2600), [1, 2.7, 5.4, 8.1], [1, 0.5, 0.25, 0.1], [0.12, 0.06, 0.03, 0.02],
                rng.uniform(0.2, 0.35))


def tavern_chatter(n):
    voices = babble(n, 14, gain_range=(0.4, 1.0), pause=(0.3, 2.0), room=0.8, wet=0.3)
    extras = np.zeros((n + 3 * SR, 2))
    for _ in range(int(n / SR / 6)):
        place(extras, rng.uniform(0, n / SR), pan(laugh(rng.random() < 0.5) * rng.uniform(0.5, 1), rng.uniform(-0.8, 0.8)))
    extras = peak_scaled(fold(extras, n), 2.0)
    clinks = np.zeros((n + SR, 2))
    for _ in range(int(n / SR * 1.2)):
        place(clinks, rng.uniform(0, n / SR), pan(clink() * rng.uniform(0.2, 1), rng.uniform(-0.9, 0.9)))
    clinks = reverb(fold(clinks, n), 0.7, wet=0.3)
    return normalize(voices + extras + peak_scaled(clinks, 1.2), -22)


def vendor_call():
    female = rng.random() < 0.5
    f0 = rng.uniform(200, 280) if female else rng.uniform(120, 170)
    parts = [syllable(f0 * rng.uniform(1.0, 1.2), rng.uniform(0.12, 0.2), female) for _ in range(rng.integers(2, 4))]
    parts.append(syllable(f0 * 1.25, rng.uniform(0.5, 0.9), female, glide=0.25))  # long, sung last word
    return np.concatenate(parts)


def coins():
    out = np.zeros(secs(0.6))
    for _ in range(rng.integers(4, 9)):
        c = ring(rng.uniform(3000, 6000), [1, 1.6, 2.4], [1, 0.5, 0.3], [0.05, 0.03, 0.02], 0.15)
        s = secs(rng.uniform(0, 0.4))
        out[s:s + len(c)] += c * rng.uniform(0.3, 1)
    return out


def market_crowd(n):
    voices = babble(n, 34, gain_range=(0.3, 1.0), pause=(0.2, 1.5), room=1.0, wet=0.25)
    calls = np.zeros((n + 3 * SR, 2))
    for _ in range(int(n / SR / 5)):
        place(calls, rng.uniform(0, n / SR), pan(vendor_call() * rng.uniform(0.6, 1), rng.uniform(-0.9, 0.9)))
    calls = reverb(fold(calls, n), 1.2, wet=0.3)
    jingles = np.zeros((n + SR, 2))
    for _ in range(int(n / SR / 5)):
        place(jingles, rng.uniform(0, n / SR), pan(coins(), rng.uniform(-0.7, 0.7)))
    return normalize(voices + peak_scaled(calls, 2.2) + peak_scaled(fold(jingles, n), 0.8)
                     + peak_scaled(passersby(n, 0.6), 1.2), -21)


def pluck(freq, dur, bright=0.5, decay=0.996):
    """Karplus-Strong plucked string (lute)."""
    delay = max(2, int(round(SR / freq - 0.5)))
    m = secs(dur)
    excitation = np.zeros(m)
    b, a = signal.butter(1, min(0.95, 0.1 + 0.8 * bright))
    excitation[:delay] = signal.lfilter(b, a, rng.uniform(-1, 1, delay))
    feedback = np.zeros(delay + 2)
    feedback[0], feedback[delay], feedback[delay + 1] = 1, -0.5 * decay, -0.5 * decay
    y = signal.lfilter([1], feedback, excitation)
    fade = secs(0.05)
    y[-fade:] *= np.linspace(1, 0, fade)
    return y


def flute(freq, dur):
    m = secs(dur + 0.06)
    t = np.arange(m) / SR
    vibrato = 1 + 0.006 * np.sin(2 * np.pi * 5.2 * t) * np.clip((t - 0.15) / 0.2, 0, 1)
    phase = 2 * np.pi * np.cumsum(freq * vibrato) / SR
    tone = np.sin(phase) + 0.35 * np.sin(2 * phase) + 0.12 * np.sin(3 * phase) + 0.05 * np.sin(4 * phase)
    b, a = signal.butter(2, [freq * 1.5 / (SR / 2), min(freq * 5, 15000) / (SR / 2)], btype='band')
    breath = signal.lfilter(b, a, rng.standard_normal(m))
    env = np.minimum(1, t / 0.04) * np.minimum(1, (t[-1] - t) / 0.06) * (1 - 0.15 * t / (dur + 0.06))
    return (tone + 0.25 * breath / (np.std(breath) + 1e-12)) * env


def frame_drum(strong):
    dur = 0.35 if strong else 0.12
    t = np.arange(secs(dur)) / SR
    if strong:
        body = np.sin(2 * np.pi * (70 * t + 50 * 0.03 * (1 - np.exp(-t / 0.03)))) * np.exp(-t / 0.09)
        return body + 0.1 * rng.standard_normal(len(t)) * np.exp(-t / 0.01)
    b, a = signal.butter(2, [800 / (SR / 2), 4000 / (SR / 2)], btype='band')
    return signal.lfilter(b, a, rng.standard_normal(len(t))) * np.exp(-t / 0.02) * 1.5


TUNE_A = [('A4', 1), ('D5', 1), ('C5', .5), ('B4', .5), ('A4', 1.5), ('G4', .5), ('F4', 1),
          ('G4', 1), ('A4', 1), ('B4', .5), ('G4', .5), ('A4', 2), ('D4', 1),
          ('F4', 1), ('G4', .5), ('F4', .5), ('E4', 1), ('D4', 1), ('E4', 1), ('F4', 1),
          ('G4', .5), ('F4', .5), ('E4', 1), ('C4', 1), ('D4', 3)]
TUNE_B = [('D5', 1), ('E5', .5), ('D5', .5), ('C5', 1), ('B4', 1), ('C5', 1), ('D5', 1),
          ('C5', 1), ('B4', .5), ('A4', .5), ('G4', 1), ('A4', 3),
          ('D5', 1), ('C5', .5), ('B4', .5), ('A4', 1), ('G4', 1), ('A4', .5), ('G4', .5), ('F4', 1),
          ('E4', 1), ('G4', 1), ('E4', 1), ('D4', 3)]
CHORDS_A = ['Dm', 'Dm', 'G', 'Dm', 'Dm', 'Dm', 'C', 'Dm']
CHORDS_B = ['Dm', 'G', 'C', 'Am', 'Dm', 'G', 'C', 'Dm']
CHORD_NOTES = {'Dm': ('D2', 'D3', 'F3', 'A3'), 'G': ('G2', 'G3', 'B3', 'D4'),
               'C': ('C3', 'C3', 'E3', 'G3'), 'Am': ('A2', 'A3', 'C4', 'E4')}


def tavern_music():
    beat = 60 / 132
    melody = TUNE_A + TUNE_A + TUNE_B + TUNE_B
    chords = CHORDS_A + CHORDS_A + CHORDS_B + CHORDS_B
    n = secs(len(chords) * 3 * beat)
    buf = np.zeros((n + 4 * SR, 2))

    t = 0.0
    for name, beats in melody:
        place(buf, t + rng.normal(0, 0.006), pan(0.28 * flute(note_freq(name), beats * beat * 0.97), 0.25))
        t += beats * beat

    for bar, chord in enumerate(chords):
        bar_start = bar * 3 * beat
        bass, *upper = CHORD_NOTES[chord]
        place(buf, bar_start, pan(0.5 * pluck(note_freq(bass), 3 * beat + 0.8, bright=0.3), -0.3))
        for beat_no in (1, 2):
            for i, name in enumerate(upper):  # quick strum
                place(buf, bar_start + beat_no * beat + i * 0.015 + rng.normal(0, 0.004),
                      pan(0.22 * pluck(note_freq(name), beat + 0.6, bright=0.6), -0.4))
        place(buf, bar_start, pan(0.6 * frame_drum(True), 0.0))
        place(buf, bar_start + 2 * beat, pan(0.3 * frame_drum(False), 0.1))
        if bar % 2:
            place(buf, bar_start + 2.5 * beat, pan(0.2 * frame_drum(False), 0.1))

    return normalize(reverb(fold(buf, n), 1.0, wet=0.25), -20)


def hearth_fire(n, rumble=1.0, rms_db=-24):
    out = np.zeros((n, 2))
    flicker = 0.75 + 0.25 * periodic_lfo(n, 20, 2)
    for ch in range(2):
        out[:, ch] = rumble * periodic_noise(n, band_shape(25, 220, 2)) * flicker
        out[:, ch] += 0.08 * periodic_noise(n, band_shape(2500, 9000)) * flicker

    buf = np.zeros((n + SR, 2))
    bb, ab = signal.butter(2, [1500 / (SR / 2), 7000 / (SR / 2)], btype='band')
    t = 0.0
    while t < n / SR:  # crackles come in little clusters
        for _ in range(rng.integers(1, 6)):
            dur = rng.uniform(0.0005, 0.004)
            k = max(4, secs(dur))
            crack = signal.lfilter(bb, ab, rng.standard_normal(k)) * np.exp(-np.arange(k) / (k / 4))
            place(buf, t, pan(crack * 3 * rng.uniform(0.05, 1) ** 2.5, rng.uniform(-0.5, 0.5)))
            t += rng.uniform(0.005, 0.06)
        t += rng.exponential(0.15)
    bp, ap = signal.butter(2, [400 / (SR / 2), 2500 / (SR / 2)], btype='band')
    for _ in range(int(0.8 * n / SR)):  # bigger pops
        k = secs(rng.uniform(0.008, 0.025))
        pop = signal.lfilter(bp, ap, rng.standard_normal(k)) * np.exp(-np.arange(k) / (k / 5))
        place(buf, rng.uniform(0, n / SR), pan(pop * rng.uniform(0.5, 1.5), rng.uniform(-0.4, 0.4)))
    return normalize(0.35 * out + fold(buf, n), rms_db)


def torches(n):
    """Wall torches: thinner fire than a hearth, sitting in a stone room."""
    return normalize(reverb(hearth_fire(n, rumble=0.35), 1.5, wet=0.3), -25)


def horse_cart(n):
    """Carts and horses passing by on cobblestones."""
    buf = np.zeros((n + 14 * SR, 2))
    for p in range(3):
        dur = 12.0
        m = secs(dur)
        t = np.arange(m) / SR
        center = dur / 2 + rng.uniform(-1, 1)
        direction = 1 if p % 2 == 0 else -1
        near = 1 / (1 + ((t - center) / 2.2) ** 2)
        position = direction * np.tanh((t - center) / 2.5) * 0.9
        mono = np.zeros(m)
        gait, beat = 0.0, rng.uniform(0.55, 0.65)
        while gait < dur - 0.2:  # trot: clip-clop pairs
            for offset in (0, 0.11):
                s = secs(gait + offset + abs(rng.normal(0, 0.008)))
                k = secs(0.05)
                tt = np.arange(k) / SR
                hit = filtered_noise(k, 800, 3000) * np.exp(-tt / 0.012) + 0.8 * np.sin(2 * np.pi * 220 * tt) * np.exp(-tt / 0.02)
                mono[s:s + k] += hit[:max(0, min(k, m - s))] * rng.uniform(0.7, 1)
            gait += beat / 2
        rumble = filtered_noise(m, hi=350) * 0.6
        rattle = np.zeros(m)
        for _ in range(int(dur * 25)):
            s = rng.integers(0, m - secs(0.02))
            rattle[s:s + secs(0.02)] += filtered_noise(secs(0.02), 300, 1500) * np.exp(-np.arange(secs(0.02)) / 150) * 0.3
        mono += 0.6 * peak_scaled(rumble, 1) + rattle
        if rng.random() < 0.8:  # wooden creak
            k = secs(0.35)
            tt = np.arange(k) / SR
            creak = signal.sawtooth(2 * np.pi * np.cumsum(260 + 180 * tt / 0.35) / SR) * swell(tt, 0.05, 0.2, 0.1)
            b, a = signal.iirpeak(900, 4, SR)
            s = secs(center + rng.uniform(-1, 1))
            mono[s:s + k] += 0.25 * signal.lfilter(b, a, creak)
        place(buf, p * n / SR / 3 + rng.uniform(0, 2), pan(mono * near, position))
    return normalize(reverb(fold(buf, n), 1.0, wet=0.2), -23)


def bird_song(kind):
    if kind == 'sparrow':
        return np.concatenate([np.concatenate([chirp(f, f + rng.uniform(1000, 1800), 0.05), np.zeros(secs(0.06))])
                               for f in rng.uniform(3000, 4000, rng.integers(3, 7))])
    if kind == 'trill':
        t = np.arange(secs(rng.uniform(0.5, 0.9))) / SR
        return np.sin(2 * np.pi * rng.uniform(3200, 4200) * t) * (0.5 + 0.5 * np.sin(2 * np.pi * 30 * t)) * swell(t, 0.05, t[-1] - 0.15, 0.1)
    if kind == 'whistler':
        return np.concatenate([chirp(2600, 3000, 0.15), np.zeros(secs(0.05)), chirp(2950, 2100, 0.25)])
    if kind == 'warbler':
        notes = [chirp(f, f * rng.uniform(0.8, 1.25), rng.uniform(0.04, 0.08)) for f in rng.uniform(2500, 5000, rng.integers(8, 15))]
        return np.concatenate(notes)
    # dove: soft low coos
    parts = []
    for f in (rng.uniform(480, 560), rng.uniform(560, 620), rng.uniform(450, 520)):
        t = np.arange(secs(rng.uniform(0.3, 0.45))) / SR
        parts += [np.sin(2 * np.pi * f * t * (1 + 0.01 * np.sin(2 * np.pi * 6 * t))) * swell(t, 0.08, t[-1] - 0.2, 0.12),
                  np.zeros(secs(0.12))]
    return 0.6 * np.concatenate(parts)


def birdsong(n):
    buf = np.zeros((n + 3 * SR, 2))
    for kind in ['sparrow', 'sparrow', 'trill', 'whistler', 'warbler', 'warbler', 'dove']:
        position, gain = rng.uniform(-0.9, 0.9), rng.uniform(0.3, 1.0) * (0.6 if kind == 'dove' else 1)
        t = rng.uniform(0, 4)
        while t < n / SR:
            song = bird_song(kind)
            place(buf, t, pan(song * gain, position))
            t += len(song) / SR + rng.uniform(2.5, 9)
    leaves = np.stack([periodic_noise(n, band_shape(1500, 7000)) for _ in range(2)], axis=1) * 0.02
    return normalize(reverb(fold(buf, n), 1.6, wet=0.3) + leaves, -24)


def forest_breeze(n):
    out = np.zeros((n, 2))
    gusts = 0.6 + 0.4 * periodic_lfo(n, 6)
    flutter = 0.7 + 0.3 * periodic_lfo(n, 900, 120)  # leaves shivering
    for ch in range(2):
        out[:, ch] = periodic_noise(n, band_shape(1200, 8000)) * gusts ** 2 * flutter
        out[:, ch] += 0.5 * periodic_noise(n, band_shape(150, 900)) * gusts
    return normalize(out, -25)


def stream(n):
    out = np.stack([periodic_noise(n, lambda f: band_shape(200, 3000)(f) / np.sqrt(np.maximum(f, 50) / 200))
                    for _ in range(2)], axis=1)
    out *= (0.8 + 0.2 * periodic_lfo(n, 120, 20))[:, None] * 0.3
    buf = np.zeros((n + SR, 2))
    for _ in range(int(80 * n / SR)):  # bubbles
        f0, dur = rng.uniform(300, 1400), rng.uniform(0.01, 0.04)
        t = np.arange(secs(dur)) / SR
        bubble = np.sin(2 * np.pi * np.cumsum(f0 * (1 + rng.uniform(1, 4) * t / dur)) / SR) * np.exp(-t / (dur / 3))
        place(buf, rng.uniform(0, n / SR), pan(bubble * rng.uniform(0.05, 0.4), rng.uniform(-0.8, 0.8)))
    return normalize(out + fold(buf, n), -23)


def crickets(n):
    out = np.zeros((n, 2))
    t = np.arange(n) / SR
    for _ in range(7):
        carrier = periodic_tone(n, rng.uniform(4000, 5200))
        period = n / SR / round(n / SR / rng.uniform(0.6, 1.1))  # whole number of chirps per loop
        phase = (t + rng.uniform(0, period)) % period
        pulses = rng.integers(3, 5)
        pulse = (phase < pulses * 0.033) * (np.sin(np.pi * (phase % 0.033) / 0.033) ** 2)
        out += pan(np.sin(2 * np.pi * carrier * t) * pulse * rng.uniform(0.2, 1), rng.uniform(-0.9, 0.9))
    night = np.stack([periodic_noise(n, band_shape(80, 600)) for _ in range(2)], axis=1) * 0.08
    return normalize(reverb(out, 1.2, wet=0.3) + night, -27)


def drip(f0):
    t = np.arange(secs(0.08)) / SR
    plink = np.sin(2 * np.pi * np.cumsum(f0 * (1 + 2.5 * t / 0.08)) / SR) * np.exp(-t / 0.015)
    plink[:secs(0.002)] += rng.standard_normal(secs(0.002)) * 0.08
    return plink


def water_drips(n):
    buf = np.zeros((n + SR, 2))
    for _ in range(3):  # steady drips from fixed spots
        f0, position, gain = rng.uniform(900, 2200), rng.uniform(-0.8, 0.8), rng.uniform(0.4, 1)
        count = rng.integers(8, 20)
        for k in range(count):
            place(buf, k * n / SR / count, pan(drip(f0 * rng.uniform(0.97, 1.03)) * gain, position))
    for _ in range(int(0.6 * n / SR)):
        place(buf, rng.uniform(0, n / SR), pan(drip(rng.uniform(800, 2600)) * rng.uniform(0.2, 0.7), rng.uniform(-0.9, 0.9)))
    return normalize(reverb(fold(buf, n), 3.5, wet=0.55, lowpass=4000), -26)


def dark_drone(n):
    t = np.arange(n) / SR
    out = np.zeros((n, 2))
    for ch in range(2):
        for f, gain in ((55, 1.0), (55.4, 0.8), (82.4, 0.5), (110.3, 0.3), (41.2, 0.6)):
            f = periodic_tone(n, f * (1 + 0.002 * ch))
            out[:, ch] += gain * signal.sawtooth(2 * np.pi * f * t + rng.uniform(0, 6.28))
    out = circular_filter(out, lambda f: 1 / np.sqrt(1 + (f / 350) ** 4))
    out *= (0.75 + 0.25 * periodic_lfo(n, 4))[:, None]
    air = np.stack([periodic_noise(n, band_shape(60, 400, 3)) for _ in range(2)], axis=1)
    out = out / np.std(out) + 0.5 * air * (0.6 + 0.4 * periodic_lfo(n, 5))[:, None]
    return normalize(reverb(out, 2.5, wet=0.3, lowpass=2000), -24)


def chains(n):
    buf = np.zeros((n + 3 * SR, 2))
    for _ in range(int(n / SR / 3.5)):
        start, position = rng.uniform(0, n / SR), rng.uniform(-0.8, 0.8)
        for _ in range(rng.integers(8, 20)):
            c = ring(rng.uniform(1500, 3200), [1, 2.76, 5.4, 8.9], [1, 0.6, 0.3, 0.15], [0.08, 0.05, 0.03, 0.02], 0.2)
            place(buf, start + rng.exponential(0.2), pan(c * rng.uniform(0.2, 1), position + rng.normal(0, 0.05)))
    return normalize(reverb(fold(buf, n), 2.5, wet=0.45, lowpass=5000), -27)


def cave_wind(n):
    out = np.zeros((n, 2))
    for fc in (180, 205, 260, 310, 415):  # resonances of the cave mouth
        env = np.clip(0.4 + 0.6 * periodic_lfo(n, 4), 0, None) ** 2
        for ch in range(2):
            tone = periodic_noise(n, lambda f, fc=fc: np.exp(-((f - fc) / 10) ** 2))
            out[:, ch] += tone * env * rng.uniform(0.5, 1)
    rumble = np.stack([periodic_noise(n, band_shape(40, 300, 3)) for _ in range(2)], axis=1)
    out = out / np.std(out) + 0.8 * rumble * (0.6 + 0.4 * periodic_lfo(n, 5))[:, None]
    return normalize(reverb(out, 2.5, wet=0.35, lowpass=3000), -24)


# ---------------------------------------------------------------------------- events (one-shots)

def fire_burst():
    m = secs(3.0)
    t = np.arange(m) / SR
    out = np.zeros(m)
    for i, (lo, hi, gain) in enumerate([(80, 400, 1.0), (300, 1200, 0.8), (1000, 3500, 0.5), (3000, 8000, 0.3)]):
        peak = 0.12 + i * 0.05
        out += gain * filtered_noise(m, lo, hi) * (t / peak) ** 1.5 * np.exp(1.5 * (1 - t / peak))
    out += 0.6 * filtered_noise(m, hi=300) * swell(t, 0.2, 0.3, 1.5)
    for _ in range(40):  # crackling afterwards
        s = secs(rng.uniform(0.3, 2.5))
        k = secs(0.003)
        out[s:s + k] += filtered_noise(k, 1500, 7000) * 0.6 * rng.uniform(0.1, 1) * np.exp(-s / SR)
    return finish_event(pan(out, np.linspace(-0.3, 0.3, m)), 1.5, 0.25)


def explosion():
    m = secs(5.0)
    t = np.arange(m) / SR
    stereo = np.zeros((m, 2))
    for ch in range(2):
        boom = np.sin(2 * np.pi * (38 * t + 45 * 0.12 * (1 - np.exp(-t / 0.12)))) * np.exp(-t / 0.7)
        crack = filtered_noise(m, hi=9000) * np.exp(-t / 0.03)
        body = filtered_noise(m, hi=1500) * np.exp(-t / 0.35)
        rumble = filtered_noise(m, hi=220) * np.exp(-t / 1.6)
        stereo[:, ch] = 1.5 * boom + 0.8 * crack + 1.2 * body + 2.0 * rumble
    for _ in range(30):  # debris raining down
        s = secs(rng.uniform(0.25, 2.8))
        k = secs(rng.uniform(0.01, 0.04))
        hit = filtered_noise(k, 600, 4000) * np.exp(-np.arange(k) / (k / 4)) * rng.uniform(0.1, 0.6) * np.exp(-s / SR / 1.5)
        stereo[s:s + k] += pan(hit, rng.uniform(-0.9, 0.9))
    return finish_event(np.tanh(1.5 * stereo / np.max(np.abs(stereo))) , 3.0, 0.3, lowpass=4000, rms_db=-15)


def light_beam():
    m = secs(5.5)
    t = np.arange(m) / SR
    env = swell(t, 1.0, 2.0, 2.3)
    stereo = np.zeros((m, 2))
    for ch, detune in ((0, 0.997), (1, 1.003)):
        for f in (523.25, 659.25, 783.99, 1046.5, 1318.5):
            stereo[:, ch] += (np.sin(2 * np.pi * f * detune * t) + 0.3 * np.sin(4 * np.pi * f * detune * t)) * env
        stereo[:, ch] += 0.8 * filtered_noise(m, 4000, 12000) * env  # bright shimmer
    stereo /= np.max(np.abs(stereo))
    for _ in range(70):  # sparkles
        s = secs(rng.uniform(0.2, 4.0))
        sparkle = ring(rng.uniform(3000, 8000), [1, 2.01], [1, 0.3], [0.08, 0.04], 0.25)
        k = min(len(sparkle), m - s)
        stereo[s:s + k] += pan(sparkle[:k] * 0.25, rng.uniform(-1, 1))
    for i, (lo, hi) in enumerate([(300, 900), (900, 2500), (2500, 7000)]):  # rising whoosh
        peak = 0.4 + i * 0.25
        stereo += pan(0.3 * filtered_noise(m, lo, hi) * np.exp(-((t - peak) / 0.25) ** 2), 0)
    return finish_event(stereo, 3.5, 0.45, lowpass=9000)


def thunder():
    m = secs(7.0)
    t = np.arange(m) / SR
    stereo = np.zeros((m, 2))
    for ch in range(2):
        crack = filtered_noise(m, hi=10000) * (np.exp(-t / 0.04) + 0.6 * np.exp(-np.abs(t - 0.09) / 0.03))
        bumps = sum(a * np.exp(-((t - c) / w) ** 2) for c, w, a in ((0.4, 0.3, 1.0), (1.1, 0.4, 0.8), (1.9, 0.5, 0.6), (2.9, 0.8, 0.4)))
        rumble = filtered_noise(m, hi=250) * bumps * np.exp(-t / 2.5)
        mid = filtered_noise(m, 250, 900) * bumps * np.exp(-t / 1.2) * 0.4
        stereo[:, ch] = 0.7 * crack + 3 * rumble + mid
    return finish_event(stereo, 3.0, 0.35, lowpass=3000, rms_db=-16)


def sword_clash():
    m = secs(2.0)
    stereo = np.zeros((m, 2))
    for at, f, position in ((0.0, 1650, -0.3), (0.55, 1900, 0.3)):
        hit = ring(f, [1, 1.47, 2.09, 2.56, 3.18, 4.05, 5.2], [1, .7, .6, .5, .4, .3, .2], [.6, .5, .4, .35, .3, .2, .15], 1.4)
        hit[:secs(0.006)] += filtered_noise(secs(0.006), 2000, 10000) * 2
        place(stereo, at, pan(hit, position))
    k = secs(0.3)  # blade scraping along blade
    scrape = filtered_noise(k, 3000, 8000) * (0.5 + 0.5 * np.abs(filtered_noise(k, hi=40))) * np.hanning(k) * 0.4
    place(stereo, 0.2, pan(scrape, np.linspace(-0.3, 0.3, k)))
    return finish_event(stereo, 1.2, 0.2, lowpass=9000)


def magic_spell():
    m = secs(3.5)
    t = np.arange(m) / SR
    stereo = np.zeros((m, 2))
    scale = [note_freq(n) for n in ('A4', 'C5', 'D5', 'E5', 'G5', 'A5', 'C6', 'D6', 'E6', 'G6', 'A6', 'C7', 'D7', 'E7')]
    for i, f in enumerate(scale):
        k = secs(1.2)
        tt = np.arange(k) / SR
        bell = np.sin(2 * np.pi * f * tt + 2 * np.exp(-tt / 0.1) * np.sin(2 * np.pi * 3.5 * f * tt)) * np.exp(-tt / 0.5)
        place(stereo, i * 0.06, pan(bell * 0.35, -0.8 + 1.6 * i / len(scale)))
    swirl = filtered_noise(m, 1000, 4000) * (0.6 + 0.4 * np.sin(2 * np.pi * 6 * t)) * swell(t, 0.6, 0.4, 1.5)
    stereo += pan(0.4 * swirl, np.sin(2 * np.pi * 0.8 * t) * 0.7)
    chord = sum(np.sin(2 * np.pi * f * t) for f in (note_freq('A5'), note_freq('E6'), note_freq('A6')))
    stereo += pan(0.25 * chord * (0.7 + 0.3 * np.sin(2 * np.pi * 7 * t)) * swell(t - 0.9, 0.1, 0.3, 2.0) * (t > 0.9), 0)
    return finish_event(stereo, 2.5, 0.4, lowpass=10000)


def monster_roar():
    m = secs(3.5)
    t = np.arange(m) / SR
    contour = np.interp(t, [0, 0.5, 1.6, 3.5], [70, 98, 82, 52])
    wobble = signal.lfilter(*signal.butter(1, 8 / (SR / 2)), rng.standard_normal(m))
    f0 = contour + 60 * wobble / np.max(np.abs(wobble)) * 0.15 + 3 * np.sin(2 * np.pi * 25 * t)
    src = signal.sawtooth(2 * np.pi * np.cumsum(f0) / SR) + 0.6 * rng.standard_normal(m)
    voice = np.zeros(m)
    for fc, q, gain in ((420, 4, 1.0), (750, 5, 0.7), (1700, 6, 0.3)):
        b, a = signal.iirpeak(fc, q, SR)
        voice += gain * signal.lfilter(b, a, src)
    voice = np.tanh(2.5 * voice / np.max(np.abs(voice)))
    sub = 0.4 * np.sin(2 * np.pi * np.cumsum(f0 / 2) / SR)
    out = (voice + sub) * swell(t, 0.35, 2.0, 1.1)
    return finish_event(pan(out, 0), 2.0, 0.3, lowpass=3500, rms_db=-15)


def arrows():
    m = secs(2.5)
    stereo = np.zeros((m, 2))
    for i in range(5):
        t0 = i * 0.12 + rng.uniform(0, 0.05)
        k = secs(0.35)
        tt = np.arange(k) / SR
        whoosh = filtered_noise(k, 1500, 5000) * np.exp(-((tt - 0.15) / 0.06) ** 2)
        place(stereo, t0, pan(whoosh * 0.6, np.linspace(-0.8, 0.8, k)))
        kk = secs(0.12)
        ti = np.arange(kk) / SR
        thunk = np.sin(2 * np.pi * rng.uniform(150, 220) * ti) * np.exp(-ti / 0.05) + filtered_noise(kk, hi=2500) * np.exp(-ti / 0.008)
        place(stereo, t0 + 0.3 + rng.uniform(0, 0.12), pan(thunk * rng.uniform(0.5, 1), rng.uniform(-0.6, 0.6)))
    return finish_event(stereo, 0.8, 0.15)


def church_bell():
    """One-shot: three tolls of a large bell."""
    strike_note = 196.0
    partials = [(0.5, 0.6, 9.0), (1.0, 0.9, 6.0), (1.19, 0.5, 5.0), (1.5, 0.3, 4.0), (2.0, 1.0, 3.5),
                (2.51, 0.25, 2.0), (2.66, 0.2, 1.8), (3.01, 0.15, 1.5), (4.07, 0.08, 1.0)]
    toll_gap, tail = 3.0, 9.0
    n = secs(2 * toll_gap + tail)
    out = np.zeros(n)
    for toll in range(3):
        start = secs(toll * toll_gap)
        t = np.arange(n - start) / SR
        ring = np.zeros(len(t))
        for ratio, amp, t60 in partials:
            f = strike_note * ratio
            beat = 0.5 * np.cos(2 * np.pi * rng.uniform(0.3, 1.2) * t)  # slow beating of a real bell
            ring += amp * np.sin(2 * np.pi * f * t + rng.uniform(0, 6.28)) * (0.75 + beat * 0.25) * 10 ** (-3 * t / t60)
        k = secs(0.012)
        b, a = signal.butter(2, [1000 / (SR / 2), 5000 / (SR / 2)], btype='band')
        ring[:k] += 0.8 * signal.lfilter(b, a, rng.standard_normal(k)) * np.linspace(1, 0, k)
        out[start:] += ring
    stereo = reverb(pan(out, 0.15), 2.5, wet=0.3, circular=False)
    fade = secs(1.0)
    stereo[-fade:] *= np.linspace(1, 0, fade)[:, None]
    return normalize(stereo, -18)


LOOPS = {
    'town_crowd': town_crowd, 'tavern_chatter': tavern_chatter, 'market_crowd': market_crowd,
    'rain': rain, 'wind': wind, 'hearth_fire': hearth_fire, 'torches': torches, 'horse_cart': horse_cart,
    'birdsong': birdsong, 'forest_breeze': forest_breeze, 'stream': stream, 'crickets': crickets,
    'water_drips': water_drips, 'dark_drone': dark_drone, 'chains': chains, 'cave_wind': cave_wind,
}
# Sounds with their own fixed length (music is still a seamless loop; the rest are one-shots).
FIXED_LENGTH = {
    'tavern_music': tavern_music, 'church_bell': church_bell,
    'event_fire': fire_burst, 'event_explosion': explosion, 'event_light': light_beam, 'event_thunder': thunder,
    'event_sword': sword_clash, 'event_magic': magic_spell, 'event_roar': monster_roar, 'event_arrows': arrows,
}


def main():
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    wav = '--wav' in sys.argv
    out_dir = Path(args[0] if args else "app/src/main/assets/sounds")
    out_dir.mkdir(parents=True, exist_ok=True)
    print(f"Writing to {out_dir}/")
    for name, make in LOOPS.items():
        write(out_dir, name, make(secs(LOOP_SECONDS)), wav)
    for name, make in FIXED_LENGTH.items():
        write(out_dir, name, make(), wav)


if __name__ == "__main__":
    main()
