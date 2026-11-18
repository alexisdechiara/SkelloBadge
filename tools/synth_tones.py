"""Synthétise les sons intégrés de l'application (mono, 44,1 kHz).

Usage : python3 tools/synth_tones.py <dossier>  puis, pour chaque .wav :
  ffmpeg -i <nom>.wav -c:a libvorbis -q:a 5 app/src/main/res/raw/<nom>.ogg
"""
import sys, wave
import numpy as np

SR = 44100
out = sys.argv[1]

def t(d): return np.arange(int(SR * d)) / SR

def env(n, attack=0.004, decay=0.4):
    x = np.arange(n) / SR
    a = np.clip(x / attack, 0, 1)
    return a * np.exp(-x / decay)

def bell(f, d, decay=0.45, bright=1.0):
    x = t(d)
    # Partiels inharmoniques d'une cloche, les aigus s'éteignant plus vite.
    parts = [(1.0, 1.0, 1.0), (2.0, 0.5 * bright, 0.6), (2.76, 0.35 * bright, 0.4),
             (4.07, 0.2 * bright, 0.25), (5.4, 0.12 * bright, 0.15)]
    s = sum(a * np.sin(2 * np.pi * f * r * x) * env(len(x), 0.003, decay * k) for r, a, k in parts)
    return s

def marimba(f, d=0.35):
    x = t(d)
    s = np.sin(2 * np.pi * f * x) * env(len(x), 0.002, 0.12)
    s += 0.25 * np.sin(2 * np.pi * f * 4 * x) * env(len(x), 0.001, 0.03)
    s += 0.1 * np.sin(2 * np.pi * f * 9.2 * x) * env(len(x), 0.001, 0.01)
    return s

def beep(f, d, soft=0.006):
    x = t(d)
    s = np.sin(2 * np.pi * f * x) + 0.3 * np.sin(2 * np.pi * 3 * f * x) + 0.12 * np.sin(2 * np.pi * 5 * f * x)
    n = len(x); r = int(SR * soft)
    e = np.ones(n); e[:r] = np.linspace(0, 1, r); e[-r:] = np.linspace(1, 0, r)
    return s * e

def place(total, items):
    buf = np.zeros(int(SR * total))
    for start, sig in items:
        i = int(SR * start); j = min(len(buf), i + len(sig))
        buf[i:j] += sig[: j - i]
    return buf

def silence(d): return np.zeros(int(SR * d))

tones = {
    # Deux notes montantes : se distingue des sons d'autres applications sans agresser.
    "tone_carillon": place(1.6, [(0.0, bell(784, 1.6, 0.5)), (0.18, bell(1047, 1.4, 0.55))]),
    "tone_clochette": place(1.3, [(0.0, bell(1319, 1.3, 0.4, bright=1.3))]),
    "tone_marimba": place(0.95, [(0.0, marimba(523)), (0.12, marimba(659)), (0.24, marimba(784)),
                                 (0.36, marimba(1047, 0.55))]),
    "tone_bips": place(0.62, [(0.0, beep(1760, 0.09)), (0.17, beep(1760, 0.09)), (0.34, beep(1760, 0.09))]),
    # Conçu pour tourner en boucle : la fin retombe en silence juste avant la reprise.
    "tone_reveil": place(1.0, [(i * 0.125, beep(1175 if i % 2 == 0 else 880, 0.085)) for i in range(4)]),
}

for name, s in tones.items():
    s = s / np.max(np.abs(s)) * 0.89  # environ -1 dBFS
    pcm = (s * 32767).astype("<i2")
    with wave.open(f"{out}/{name}.wav", "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())
    print(name, round(len(s) / SR, 2), "s")
