"""
Generates the alert sounds into app/src/main/res/raw.

The three defaults follow the rhythm of their vibration pattern, so sound and
vibration say the same thing:
  drones   - 2 short buzzy beeps, 3 times   (like a moped engine)
  missiles - 3 medium rising sirens, 5 times
  end      - 1 long calm chime, 2 times

The sound_* files are the other choices users can pick per alert kind
(AlertSounds.kt). Keep every sound under ~10 s: the app waits for it to end.

Run: python tools/make_sounds.py   (needs numpy)
"""
import os
import wave

import numpy as np

RATE = 22050
OUT_DIR = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "raw")


def silence(seconds):
    return np.zeros(int(RATE * seconds))


def envelope(signal, attack=0.01, release=0.03):
    """Short fades so a tone never starts or stops with a click."""
    n = len(signal)
    env = np.ones(n)
    a = min(int(RATE * attack), n // 2)
    r = min(int(RATE * release), n // 2)
    env[:a] = np.linspace(0, 1, a)
    env[n - r:] = np.linspace(1, 0, r)
    return signal * env


def buzz(seconds, freq=190.0):
    """Sawtooth with a fast wobble: the drone / moped sound."""
    t = np.arange(int(RATE * seconds)) / RATE
    wobble = 1 + 0.04 * np.sin(2 * np.pi * 28 * t)
    phase = np.cumsum(freq * wobble) / RATE
    saw = 2 * (phase % 1) - 1
    # A little of the octave above makes it cut through on a phone speaker.
    saw += 0.5 * (2 * ((2 * phase) % 1) - 1)
    return envelope(saw)


def siren(seconds, low=650.0, high=1500.0):
    """A tone sweeping up: the missile alarm."""
    t = np.arange(int(RATE * seconds)) / RATE
    freq = low + (high - low) * (t / seconds)
    phase = np.cumsum(freq) / RATE
    tone = np.sign(np.sin(2 * np.pi * phase)) * 0.6 + np.sin(2 * np.pi * phase) * 0.4
    return envelope(tone, release=0.05)


def chime(seconds):
    """A soft major chord fading out: the all-clear."""
    t = np.arange(int(RATE * seconds)) / RATE
    decay = np.exp(-2.2 * t)
    notes = [523.25, 659.25, 783.99, 1046.5]  # C5 E5 G5 C6
    tone = sum(np.sin(2 * np.pi * f * t) / (i + 1) for i, f in enumerate(notes))
    return envelope(tone * decay, attack=0.005, release=0.2)


def wail(seconds, low=420.0, high=880.0):
    """A civil-defence siren: a slow rise and fall."""
    t = np.arange(int(RATE * seconds)) / RATE
    sweep = 0.5 - 0.5 * np.cos(2 * np.pi * t / seconds)
    freq = low + (high - low) * sweep
    phase = np.cumsum(freq) / RATE
    tone = np.sin(2 * np.pi * phase) + 0.35 * np.sin(4 * np.pi * phase) + 0.15 * np.sin(6 * np.pi * phase)
    return envelope(tone, attack=0.2, release=0.3)


def square(seconds, freq, duty=0.5):
    t = np.arange(int(RATE * seconds)) / RATE
    tone = np.where((t * freq) % 1 < duty, 1.0, -1.0) * 0.7 + np.sin(2 * np.pi * freq * t) * 0.3
    return envelope(tone, attack=0.003, release=0.01)


def klaxon_pair():
    """Two-tone hi-lo, like a European emergency vehicle."""
    return np.concatenate([square(0.45, 950), square(0.45, 680)])


def beep(seconds=0.09, freq=2400.0):
    t = np.arange(int(RATE * seconds)) / RATE
    return envelope(np.sin(2 * np.pi * freq * t), attack=0.003, release=0.01)


def bell(seconds=1.6, base=440.0):
    """A struck bell: inharmonic partials with long decay."""
    t = np.arange(int(RATE * seconds)) / RATE
    partials = [(0.5, 1.0, 1.2), (1.0, 0.9, 1.6), (1.19, 0.6, 2.2), (1.56, 0.5, 2.8), (2.0, 0.35, 3.3), (2.74, 0.25, 4.5)]
    tone = sum(a * np.exp(-d * t) * np.sin(2 * np.pi * base * r * t) for r, a, d in partials)
    return envelope(tone, attack=0.002, release=0.15)


def horn(seconds=0.9, freq=110.0):
    """A low ship's horn: rich, slightly detuned."""
    t = np.arange(int(RATE * seconds)) / RATE
    tone = sum(np.sign(np.sin(2 * np.pi * freq * k * (1 + 0.003 * k) * t)) / k for k in (1, 2, 3, 4))
    return envelope(tone, attack=0.05, release=0.12)


def marimba(freq, seconds=0.45):
    t = np.arange(int(RATE * seconds)) / RATE
    tone = (np.sin(2 * np.pi * freq * t) + 0.3 * np.sin(2 * np.pi * freq * 4 * t) * np.exp(-30 * t)) * np.exp(-7 * t)
    return envelope(tone, attack=0.002, release=0.05)


def pulse_tone(seconds=0.6, freq=520.0):
    """A throbbing synth tone."""
    t = np.arange(int(RATE * seconds)) / RATE
    amp = 0.5 + 0.5 * np.sin(2 * np.pi * 12 * t)
    tone = (np.sin(2 * np.pi * freq * t) + 0.5 * np.sin(2 * np.pi * freq * 1.5 * t)) * amp
    return envelope(tone, attack=0.02, release=0.05)


def pluck(freq, seconds=1.2, brightness=0.996):
    """A plucked string (Karplus-Strong): harp and music box."""
    n = int(RATE * seconds)
    period = int(RATE / freq)
    rng = np.random.default_rng(int(freq))
    buf = rng.uniform(-1, 1, period)
    out = np.empty(n)
    for i in range(n):
        out[i] = buf[i % period]
        buf[i % period] = brightness * 0.5 * (buf[i % period] + buf[(i + 1) % period])
    return envelope(out, attack=0.002, release=0.1)


def glass(freq, seconds=2.0):
    """A soft glass ping: a pure tone with a slow fade."""
    t = np.arange(int(RATE * seconds)) / RATE
    tone = (np.sin(2 * np.pi * freq * t) + 0.2 * np.sin(2 * np.pi * freq * 2.76 * t) * np.exp(-6 * t)) * np.exp(-2.5 * t)
    return envelope(tone, attack=0.004, release=0.2)


def pad(seconds=3.5, notes=(261.63, 329.63, 392.0, 523.25)):
    """A warm chord that swells in and out."""
    t = np.arange(int(RATE * seconds)) / RATE
    swell = np.sin(np.pi * t / seconds) ** 2
    tone = sum(np.sin(2 * np.pi * f * t) + 0.3 * np.sin(2 * np.pi * f * 1.003 * t) for f in notes)
    return envelope(tone * swell, attack=0.05, release=0.3)


def wind_chimes(seconds=6.0):
    """A few light chimes at random moments."""
    rng = np.random.default_rng(7)
    out = np.zeros(int(RATE * seconds))
    for start in sorted(rng.uniform(0, seconds - 2.2, 9)):
        freq = rng.choice([1046.5, 1174.66, 1318.51, 1567.98, 1760.0])
        ping = glass(freq, 2.0) * rng.uniform(0.5, 1.0)
        i = int(start * RATE)
        out[i:i + len(ping)] += ping
    return envelope(out, attack=0.01, release=0.3)


def pattern(make_pulse, count, gap, repeats, group_gap):
    parts = []
    for group in range(repeats):
        if group:
            parts.append(silence(group_gap))
        for pulse in range(count):
            if pulse:
                parts.append(silence(gap))
            parts.append(make_pulse())
    return np.concatenate(parts)


def save(name, signal, peak=0.9):
    signal = signal / np.max(np.abs(signal)) * peak
    data = (signal * 32767).astype("<i2")
    path = os.path.join(OUT_DIR, name)
    with wave.open(path, "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(RATE)
        out.writeframes(data.tobytes())
    print(f"{path}: {len(signal) / RATE:.1f} s")


if __name__ == "__main__":
    os.makedirs(OUT_DIR, exist_ok=True)
    # Same timings as the vibration patterns in AlertNotifier.kt.
    save("alert_drones.wav", pattern(lambda: buzz(0.15), 2, 0.10, 3, 0.60))
    save("alert_missiles.wav", pattern(lambda: siren(0.40), 3, 0.15, 5, 0.60))
    save("alert_end.wav", pattern(lambda: chime(1.50), 1, 0.0, 2, 0.60))

    # Choices users can pick for any alert kind.
    save("sound_air_siren.wav", np.concatenate([wail(4.5), wail(4.5)]))
    save("sound_klaxon.wav", np.concatenate([klaxon_pair() for _ in range(5)]))
    save("sound_beeps.wav", pattern(beep, 3, 0.07, 6, 0.45))
    save("sound_alarm_clock.wav", pattern(lambda: beep(0.07, 3100.0), 4, 0.05, 6, 0.35))
    save("sound_bell.wav", pattern(bell, 1, 0.0, 4, 0.15))
    save("sound_horn.wav", pattern(horn, 1, 0.0, 3, 0.45))
    save("sound_pulse.wav", pattern(pulse_tone, 1, 0.0, 6, 0.25))
    # Softer choices: gentler timbres, and quieter (peak 0.6 instead of 0.9).
    save("sound_harp.wav", np.concatenate([pluck(f) for f in (261.63, 329.63, 392.0, 523.25, 659.25, 783.99)] + [silence(0.3)]), 0.6)
    save("sound_music_box.wav", np.concatenate(
        [pluck(f, 0.5, 0.9985) for f in (1046.5, 1318.51, 1567.98, 1318.51, 1174.66, 1046.5, 1174.66, 1318.51)] + [silence(0.3)]), 0.6)
    save("sound_glass.wav", pattern(lambda: np.concatenate([glass(880.0, 0.6), glass(1318.51, 2.0)]), 1, 0.0, 2, 0.3), 0.6)
    save("sound_soft_pad.wav", np.concatenate([pad(), pad(notes=(293.66, 369.99, 440.0, 587.33))]), 0.6)
    save("sound_wind_chimes.wav", wind_chimes(), 0.6)
    save("sound_doorbell.wav", pattern(lambda: np.concatenate([glass(659.25, 0.8), glass(523.25, 1.8)]), 1, 0.0, 2, 0.4), 0.6)
    save("sound_marimba.wav", np.concatenate(
        [np.concatenate([marimba(f) for f in (523.25, 659.25, 783.99, 1046.5)]), silence(0.5)] * 2))
