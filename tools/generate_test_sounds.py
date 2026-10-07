#!/usr/bin/env python3
"""Generate synthetic ambience sounds for testing Tavern Tales.

Everything is synthesized from scratch (no samples), so the output is free to use and share.
Loops are seamless: noise textures are shaped in the frequency domain over exactly the loop length
(which makes them periodic), envelopes are built from whole-cycle sinusoids, and events or reverb
tails that run past the end are wrapped around to the start.

Usage:  python tools/generate_test_sounds.py [output_dir]      (default: build/test-sounds)
Needs:  numpy, scipy
"""
import sys
from pathlib import Path

import numpy as np
from scipy import signal
from scipy.io import wavfile

SR = 44100
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
    end = min(len(buf), s + len(stereo))
    if s < len(buf):
        buf[s:end] += stereo[:end - s]


def normalize(x, rms_db, peak=0.89):
    x = x * (10 ** (rms_db / 20) / (np.sqrt(np.mean(x ** 2)) + 1e-12))
    top = np.max(np.abs(x))
    return x * (peak / top) if top > peak else x


def write(path, x):
    wavfile.write(path, SR, (np.clip(x, -1, 1) * 32767).astype(np.int16))
    print(f"  {path.name:<24} {len(x) / SR:5.1f} s")


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


def syllable(f0, dur, female):
    m = secs(dur)
    t = np.arange(m) / SR
    pitch = f0 * (1 + rng.uniform(-0.15, 0.15) * t / dur)
    src = signal.sawtooth(2 * np.pi * np.cumsum(pitch) / SR) + 0.15 * rng.standard_normal(m)
    vowel = VOWELS[rng.integers(len(VOWELS))]
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


def town_crowd(n):
    buf = np.zeros((n + 2 * SR, 2))
    for _ in range(22):  # murmuring voices
        female = rng.random() < 0.5
        f0 = rng.uniform(170, 250) if female else rng.uniform(90, 140)
        position, gain = rng.uniform(-0.9, 0.9), rng.uniform(0.2, 1.0)
        t = rng.uniform(0, 3)
        while t < n / SR:
            for _ in range(rng.integers(3, 12)):
                dur = rng.uniform(0.09, 0.26)
                place(buf, t, pan(syllable(f0 * rng.uniform(0.92, 1.08), dur, female) * gain, position))
                t += dur * rng.uniform(0.85, 1.1)
            t += rng.uniform(0.4, 3.0)
    voices = circular_filter(fold(buf, n), band_shape(150, 3500))
    voices = reverb(voices / (np.std(voices) + 1e-12), 1.2, wet=0.35)

    steps = np.zeros((n + 10 * SR, 2))
    for _ in range(int(n / SR / 2.5)):  # people walking past on cobblestones
        start, interval = rng.uniform(0, n / SR), rng.uniform(0.48, 0.6)
        count = rng.integers(8, 18)
        direction = rng.choice([-1, 1])
        loudness = rng.uniform(0.2, 0.8)
        for k in range(count):
            progress = k / (count - 1)
            near = np.sin(np.pi * progress)  # loudest when passing in front
            place(steps, start + k * interval + rng.normal(0, 0.01),
                  pan(footstep() * loudness * (0.25 + 0.75 * near), direction * (2 * progress - 1) * 0.9))
    steps = fold(steps, n)
    # Voices are at unit RMS; scale sparse footsteps by their peak so they sit just above the murmur.
    return normalize(voices + 1.5 * steps / (np.max(np.abs(steps)) + 1e-12), -22)


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


def hearth_fire(n):
    out = np.zeros((n, 2))
    flicker = 0.75 + 0.25 * periodic_lfo(n, 20, 2)
    for ch in range(2):
        out[:, ch] = periodic_noise(n, band_shape(25, 220, 2)) * flicker
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
    return normalize(0.35 * out + fold(buf, n), -24)


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


def main():
    out_dir = Path(sys.argv[1] if len(sys.argv) > 1 else "build/test-sounds")
    out_dir.mkdir(parents=True, exist_ok=True)
    loop = secs(40)
    print(f"Writing to {out_dir}/")
    write(out_dir / "town_crowd_bustle.wav", town_crowd(loop))
    write(out_dir / "rain.wav", rain(loop))
    write(out_dir / "wind.wav", wind(loop))
    write(out_dir / "tavern_music.wav", tavern_music())
    write(out_dir / "hearth_fire.wav", hearth_fire(loop))
    write(out_dir / "church_bell.wav", church_bell())


if __name__ == "__main__":
    main()
