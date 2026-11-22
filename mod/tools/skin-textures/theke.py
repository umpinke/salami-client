"""Erzeugt die Sprites des Theke-Looks (salami-core, gui/sprites/theke).

Alle Bilder sind in doppelter Auflösung (1 GUI-Pixel = 2 Bildpixel), passend zur GUI-Größe 2.
"""
import json
import math
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw

OUT = Path(sys.argv[1])
OUT.mkdir(parents=True, exist_ok=True)
random.seed(7)


def hexc(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def mix(c1, c2, t):
    return tuple(round(c1[i] + (c2[i] - c1[i]) * t) for i in range(4))


def save(name, img, scaling=None):
    img.save(OUT / f"{name}.png")
    meta = OUT / f"{name}.png.mcmeta"
    if scaling is None:
        if meta.exists():
            meta.unlink()
        return
    with open(meta, "w", newline="\n") as f:
        json.dump({"gui": {"scaling": scaling}}, f, indent=2)
        f.write("\n")


RED = hexc("b23a3a")
RED_HI = hexc("c9524a")
RED_DARK = hexc("8a2a2a")
FAT = hexc("f3e3d3")
FAT_HI = hexc("fff6ea")
FAT_DARK = hexc("d8c1a6")
PEPPER = hexc("2b1d1a")

# ------------------------------------------------------------------ Markise
# Eine Periode: roter und cremefarbener Streifen je 10 GUI-Pixel, Stoffbahn 11 hoch, darunter
# Bögen 5 hoch -> 20 x 16 GUI = 40 x 32 Pixel.
W, H = 40, 32
BAND = 22
S = 8  # Supersampling für die Bögen
big = Image.new("RGBA", (W * S, H * S), (0, 0, 0, 0))
d = ImageDraw.Draw(big)
for i, (base, hi, dark) in enumerate(((RED, RED_HI, RED_DARK), (FAT, FAT_HI, FAT_DARK))):
    x0 = i * 20 * S
    # Stoffbahn mit Falte: links heller, rechts dunkler
    for x in range(20 * S):
        t = x / (20 * S)
        if t < 0.35:
            c = mix(hi, base, t / 0.35)
        elif t < 0.8:
            c = base
        else:
            c = mix(base, dark, (t - 0.8) / 0.2 * 0.8)
        d.line([(x0 + x, 0), (x0 + x, BAND * S)], fill=c)
    # Bogen unter dem Streifen
    d.ellipse([x0, (BAND - 10) * S, x0 + 20 * S - 1, (BAND + 10) * S], fill=base)
    # Saum: dunkle Kante am Bogen
    d.arc([x0, (BAND - 10) * S, x0 + 20 * S - 1, (BAND + 10) * S], 0, 180, fill=dark, width=2 * S)
img = big.resize((W, H), Image.LANCZOS)
px = img.load()
# oben: Schatten unter der Stange
for y in range(4):
    for x in range(W):
        r, g, b, a = px[x, y]
        k = 0.55 + 0.1 * y
        px[x, y] = (round(r * k), round(g * k), round(b * k), a)
# Saumlinie am Übergang zur Bogenreihe (leicht dunkler)
for x in range(W):
    for y in (BAND - 2, BAND - 1):
        r, g, b, a = px[x, y]
        px[x, y] = (round(r * 0.9), round(g * 0.9), round(b * 0.9), a)
save("awning", img, {"type": "tile", "width": 20, "height": 16})

# ------------------------------------------------------------------ Holz
WOOD = hexc("5a3a28")
WOOD_D = hexc("462c1e")
WOOD_L = hexc("6c4731")
img = Image.new("RGBA", (64, 16), WOOD)
px = img.load()
for y in range(16):
    phase = random.random() * 6
    for x in range(64):
        wave = math.sin(x / 9.0 + phase + y * 0.7) * 0.5 + math.sin(x / 3.7 + y) * 0.2
        n = random.random() * 0.25
        t = wave * 0.5 + n
        c = mix(WOOD, WOOD_L, max(0, t)) if t > 0 else mix(WOOD, WOOD_D, min(1, -t * 1.4))
        px[x, y] = c
# Maserlinien
for _ in range(5):
    y = random.randrange(1, 14)
    x = random.randrange(0, 64)
    length = random.randrange(12, 30)
    for k in range(length):
        yy = y + (1 if math.sin((x + k) / 6.0) > 0.6 else 0)
        px[(x + k) % 64, min(15, yy)] = WOOD_D
# Astloch
kx, ky = random.randrange(8, 56), random.randrange(4, 12)
for dx in range(-2, 3):
    for dy in range(-1, 2):
        if abs(dx) + abs(dy) <= 2:
            px[(kx + dx) % 64, ky + dy] = hexc("3d2619")
# Fuge zwischen den Brettern (unten)
for x in range(64):
    px[x, 15] = hexc("3d2619")
    px[x, 0] = mix(px[x, 0], WOOD_L, 0.5)
save("wood", img, {"type": "tile", "width": 32, "height": 8})

# ------------------------------------------------------------------ Kreidetafel
CHALK = hexc("23302b")
img = Image.new("RGBA", (96, 96), CHALK)
px = img.load()
for y in range(96):
    for x in range(96):
        n = (random.random() - 0.5) * 0.06
        px[x, y] = mix(CHALK, hexc("ffffff") if n > 0 else hexc("000000"), abs(n))
# verwischte Kreidereste
layer = Image.new("RGBA", (96, 96), (0, 0, 0, 0))
ld = ImageDraw.Draw(layer)
for _ in range(7):
    x = random.randrange(0, 96)
    y = random.randrange(0, 96)
    w = random.randrange(18, 40)
    h = random.randrange(3, 7)
    ld.ellipse([x, y, x + w, y + h], fill=(233, 239, 230, random.randrange(6, 14)))
# Kacheln nahtlos: Ränder umklappen
wrapped = Image.new("RGBA", (96, 96), (0, 0, 0, 0))
for ox in (-96, 0, 96):
    for oy in (-96, 0, 96):
        wrapped.alpha_composite(layer, (0, 0)) if (ox, oy) == (0, 0) else None
img.alpha_composite(layer)
save("chalk", img, {"type": "tile", "width": 48, "height": 48})

# ------------------------------------------------------------------ Papier (weiß, wird eingefärbt)
img = Image.new("RGBA", (128, 128), (255, 255, 255, 255))
px = img.load()
for y in range(128):
    for x in range(128):
        v = 255 - int(random.random() ** 3 * 9)
        px[x, y] = (v, v, v, 255)
# Fasern
for _ in range(40):
    x = random.randrange(0, 128)
    y = random.randrange(0, 128)
    ang = random.random() * math.pi
    length = random.randrange(3, 9)
    for k in range(length):
        xx = int(x + math.cos(ang) * k) % 128
        yy = int(y + math.sin(ang) * k) % 128
        v = 240 + random.randrange(0, 6)
        px[xx, yy] = (v, v, v, 255)
save("paper", img, {"type": "tile", "width": 64, "height": 64})


# ------------------------------------------------------------------ Stempel
def disc(size, fill, ring, ring_w, hi=None):
    s = 8
    big = Image.new("RGBA", (size * s, size * s), (0, 0, 0, 0))
    d = ImageDraw.Draw(big)
    d.ellipse([0, 0, size * s - 1, size * s - 1], fill=ring)
    d.ellipse([ring_w * s, ring_w * s, (size - ring_w) * s - 1, (size - ring_w) * s - 1], fill=fill)
    if hi:
        d.ellipse([ring_w * s + s, ring_w * s + s, size * s * 0.55, size * s * 0.55], fill=hi)
        d.ellipse([ring_w * s + s * 2, ring_w * s + s * 2, (size - ring_w) * s - 1, (size - ring_w) * s - 1], fill=fill)
    return big, d, s


def check(d, s, size, color, width):
    pts = [(0.27, 0.52), (0.43, 0.68), (0.74, 0.34)]
    d.line([(x * size * s, y * size * s) for x, y in pts], fill=color, width=int(width * s), joint="curve")


def dashed_ring(size, color, width, dashes):
    s = 8
    big = Image.new("RGBA", (size * s, size * s), (0, 0, 0, 0))
    d = ImageDraw.Draw(big)
    step = 360 / dashes
    for i in range(dashes):
        a = i * step - 90
        d.arc([width * s / 2, width * s / 2, size * s - width * s / 2 - 1, size * s - width * s / 2 - 1], a, a + step * 0.58,
              fill=color, width=int(width * s))
    return big.resize((size, size), Image.LANCZOS)


CREAM = hexc("fff3e6")
big, d, s = disc(22, RED, RED_DARK, 2, hi=hexc("c4483f"))
check(d, s, 22, CREAM, 2.6)
save("stamp_on", big.resize((22, 22), Image.LANCZOS))
big, d, s = disc(22, hexc("c4483f"), RED_DARK, 2, hi=hexc("d65a50"))
check(d, s, 22, CREAM, 2.6)
save("stamp_on_hover", big.resize((22, 22), Image.LANCZOS))
save("stamp_off", dashed_ring(22, hexc("a08672"), 2, 10))
save("stamp_off_hover", dashed_ring(22, RED, 2, 10))
# Griff des Schiebereglers
big, d, s = disc(14, RED, RED_DARK, 2)
save("knob", big.resize((14, 14), Image.LANCZOS))
big, d, s = disc(14, hexc("c4483f"), RED_DARK, 2)
save("knob_hover", big.resize((14, 14), Image.LANCZOS))

# Großer Mods-Stempel: rote Scheibe, gestrichelter Innenring, Play-Dreieck (24 GUI = 48 px)
for name, base in (("stamp_play", RED), ("stamp_play_hover", hexc("c4483f"))):
    size = 48
    big, d, s = disc(size, base, RED_DARK, 3, hi=None)
    inset = size * s * 0.17
    for i in range(12):
        a = i * 30 - 90
        d.arc([inset, inset, size * s - inset - 1, size * s - inset - 1], a, a + 17, fill=CREAM, width=int(1.8 * s))
    tri = [(0.42, 0.33), (0.42, 0.67), (0.68, 0.5)]
    d.polygon([(x * size * s, y * size * s) for x, y in tri], fill=CREAM)
    # Schatten unten
    img = big.resize((size, size), Image.LANCZOS)
    save(name, img)

print("ok", OUT)
