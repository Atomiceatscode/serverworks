"""Generates Serverworks' sound effects by synthesis, so every sound is original and reproducible.

Run: art/.venv/bin/python art/make_sounds.py   (see art/requirements.txt)

Each sound is built from sine tones, filtered noise and envelopes, and written as mono
Ogg Vorbis: Minecraft only positions mono sounds in the world.
"""
from pathlib import Path

import numpy as np
import soundfile

RATE = 44100
OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/serverworks/sounds"
rng = np.random.default_rng(2603)


def t(seconds):
    return np.arange(int(seconds * RATE)) / RATE


def env(length, attack=0.005, release=0.05):
    """A linear attack, flat middle and exponential-ish release, in seconds."""
    n = int(length * RATE)
    e = np.ones(n)
    a = max(1, int(attack * RATE))
    r = max(1, int(release * RATE))
    e[:a] = np.linspace(0, 1, a)
    e[-r:] *= np.linspace(1, 0, r) ** 2
    return e


def decay(length, rate):
    return np.exp(-t(length) * rate)


def lowpass(signal, cutoff):
    """A simple one-pole low-pass filter."""
    alpha = 1 - np.exp(-2 * np.pi * cutoff / RATE)
    out = np.empty_like(signal)
    acc = 0.0
    for i, x in enumerate(signal):
        acc += alpha * (x - acc)
        out[i] = acc
    return out


def bandpass(signal, low, high):
    return lowpass(signal, high) - lowpass(signal, low)


def noise(length):
    return rng.uniform(-1, 1, int(length * RATE))


def tone(freq, length, harmonics=(1.0,)):
    x = t(length)
    return sum(a * np.sin(2 * np.pi * freq * (i + 1) * x) for i, a in enumerate(harmonics))


def place(buffer, sound, at):
    start = int(at * RATE)
    end = min(len(buffer), start + len(sound))
    buffer[start:end] += sound[:end - start]


def normalize(signal, peak=0.8):
    m = np.max(np.abs(signal)) or 1
    return signal / m * peak


def save(name, signal, peak=0.8):
    OUT.mkdir(parents=True, exist_ok=True)
    target = OUT / f"{name}.ogg"
    soundfile.write(str(target), normalize(signal, peak).astype(np.float32), RATE, format="OGG", subtype="VORBIS")
    print(f"wrote sounds/{target.name}")


# ---------------------------------------------------------------- sounds


def rack_hum():
    """A 4-second seamless loop: mains hum, a fan's broadband whoosh, and slow beating."""
    length = 4.0
    x = t(length)
    # Whole numbers of cycles in 4 s, so the loop has no seam.
    hum = 0.5 * np.sin(2 * np.pi * 60 * x) + 0.25 * np.sin(2 * np.pi * 120 * x) + 0.08 * np.sin(2 * np.pi * 180.25 * x)
    beat = 1 + 0.15 * np.sin(2 * np.pi * 0.5 * x)
    fan = bandpass(noise(length + 0.5), 300, 2200)[int(0.5 * RATE):]
    blade = 1 + 0.25 * np.sin(2 * np.pi * 37 * x)
    loop = hum * beat * 0.6 + fan * blade * 0.35
    # Cross-fade the ends so the filtered noise also wraps cleanly.
    fade = int(0.25 * RATE)
    loop[:fade] = loop[:fade] * np.linspace(0, 1, fade) + loop[-fade:] * np.linspace(1, 0, fade)
    loop = loop[:-fade]
    save("rack_hum", loop, 0.5)


def boot():
    """A bright rising arpeggio with a soft pad underneath."""
    out = np.zeros(int(1.4 * RATE))
    for i, freq in enumerate([523.25, 659.25, 783.99, 1046.5]):
        note = tone(freq, 0.6, (1.0, 0.3, 0.1)) * decay(0.6, 6)
        place(out, note * env(0.6, 0.004, 0.1), i * 0.09)
    pad = tone(261.63, 1.4, (1.0, 0.5)) * env(1.4, 0.2, 0.8) * 0.25
    out += pad
    save("rack_boot", out)


def power_down():
    """A falling tone with a filter closing over it."""
    length = 1.2
    x = t(length)
    freq = 440 * np.exp(-x * 1.6)
    phase = 2 * np.pi * np.cumsum(freq) / RATE
    sweep = (np.sin(phase) + 0.4 * np.sin(2 * phase)) * env(length, 0.01, 0.4)
    hum = tone(60, length, (1.0, 0.5)) * decay(length, 3) * 0.4
    save("rack_power_down", sweep + hum, 0.7)


def alarm():
    """Two urgent two-tone beeps."""
    out = np.zeros(int(1.6 * RATE))
    for i in range(4):
        freq = 880 if i % 2 == 0 else 660
        beep = tone(freq, 0.18, (1.0, 0.0, 0.3)) * env(0.18, 0.003, 0.03)
        place(out, beep, i * 0.2 + (0.5 if i >= 2 else 0))
    save("rack_alarm", out, 0.7)


def click(length=0.03, freq=2400):
    return bandpass(noise(length), freq * 0.5, freq * 2) * decay(length, 120)


def drive_insert():
    out = np.zeros(int(0.45 * RATE))
    slide = bandpass(noise(0.22), 400, 1800) * env(0.22, 0.02, 0.05) * 0.5
    place(out, slide, 0)
    place(out, click(0.05, 1800) * 1.4, 0.22)
    place(out, tone(140, 0.12) * decay(0.12, 40) * 0.8, 0.22)
    save("drive_insert", out)


def drive_eject():
    out = np.zeros(int(0.45 * RATE))
    place(out, click(0.04, 2600) * 1.2, 0)
    place(out, tone(180, 0.08) * decay(0.08, 50) * 0.6, 0)
    slide = bandpass(noise(0.25), 500, 2200) * env(0.25, 0.01, 0.12) * 0.5
    place(out, slide, 0.06)
    save("drive_eject", out)


def drive_seek():
    """A burst of head movements: quick ticks with a little tone."""
    out = np.zeros(int(0.5 * RATE))
    at = 0.0
    while at < 0.42:
        place(out, click(0.012, rng.uniform(2500, 4200)) * rng.uniform(0.5, 1.0), at)
        at += rng.uniform(0.02, 0.06)
    out += tone(1200, 0.5) * env(0.5, 0.01, 0.2) * 0.03
    save("drive_seek", out, 0.55)


def disk_write():
    """Data warble: a frequency-modulated chirp, then a confirming click."""
    length = 0.7
    x = t(length)
    carrier = 900 + 500 * np.sin(2 * np.pi * 14 * x) * np.sin(2 * np.pi * 1.3 * x)
    phase = 2 * np.pi * np.cumsum(carrier) / RATE
    warble = np.sign(np.sin(phase)) * 0.25 + np.sin(phase) * 0.5
    warble = lowpass(warble, 3500) * env(length, 0.01, 0.15)
    out = np.zeros(int(0.9 * RATE))
    place(out, warble, 0)
    place(out, tone(1568, 0.15, (1.0, 0.2)) * decay(0.15, 25), 0.72)
    save("disk_write", out, 0.6)


def gateway_open():
    """A shimmering, rising chord."""
    out = np.zeros(int(1.6 * RATE))
    for i, freq in enumerate([392.0, 493.88, 587.33, 783.99]):
        x = t(1.4)
        shimmer = np.sin(2 * np.pi * freq * x + 0.6 * np.sin(2 * np.pi * 6 * x)) * env(1.4, 0.3, 0.6)
        place(out, shimmer * 0.4, i * 0.06)
    save("gateway_open", out, 0.6)


def gateway_travel():
    """A rushing whoosh that rises and opens up."""
    length = 1.4
    whoosh = noise(length)
    x = t(length)
    rising = np.zeros_like(whoosh)
    # Crossfade between a dull and a bright band to sweep the colour upward.
    dull = bandpass(whoosh, 150, 900)
    bright = bandpass(whoosh, 1200, 6000)
    mix = np.clip(x / length, 0, 1)
    rising = dull * (1 - mix) + bright * mix
    shape = np.sin(np.pi * np.clip(x / length, 0, 1)) ** 1.5
    sweep_freq = 200 * np.exp(x * 1.8)
    tone_sweep = np.sin(2 * np.pi * np.cumsum(sweep_freq) / RATE) * shape * 0.2
    save("gateway_travel", rising * shape + tone_sweep, 0.7)


if __name__ == "__main__":
    rack_hum()
    boot()
    power_down()
    alarm()
    drive_insert()
    drive_eject()
    drive_seek()
    disk_write()
    gateway_open()
    gateway_travel()
