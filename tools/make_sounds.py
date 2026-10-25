"""
Generates the three alert sounds into app/src/main/res/raw.

Each sound follows the rhythm of its vibration pattern, so sound and vibration
say the same thing:
  drones   - 2 short buzzy beeps, 3 times   (like a moped engine)
  missiles - 3 medium rising sirens, 5 times
  end      - 1 long calm chime, 2 times

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


def save(name, signal):
    signal = signal / np.max(np.abs(signal)) * 0.9
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
