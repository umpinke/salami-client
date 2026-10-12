"""Erzeugt das Salami-Client-Logo (eine Salamischeibe) als PNG.

Deterministisch (fester Seed), damit das Logo jederzeit reproduzierbar ist.
Aufruf:  python branding/generate_logo.py
Ausgabe: branding/out/salami-logo-<size>.png  (+ Kopien in Launcher und Mod)
"""

from __future__ import annotations

import math
import random
import shutil
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "branding" / "out"

# Salami-Palette (identisch mit Launcher-Theme und In-Game-Theme)
CASING = (90, 35, 32, 255)       # Pelle
CASING_EDGE = (60, 22, 20, 255)
MEAT = (178, 58, 58, 255)         # Salami-Rot
MEAT_DARK = (140, 40, 42, 255)
FAT = (243, 227, 211, 255)        # Fett-Weiß
PEPPER = (43, 29, 26, 255)        # Pfeffer-Schwarz

SS = 4  # Supersampling-Faktor für glatte Kanten


def draw_slice(size: int, seed: int = 1337) -> Image.Image:
    big = size * SS
    rnd = random.Random(seed)
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    c = big / 2
    r_outer = big * 0.47
    r_meat = big * 0.415

    # Pelle mit dunklem Rand
    d.ellipse([c - r_outer, c - r_outer, c + r_outer, c + r_outer], fill=CASING_EDGE)
    r_casing = r_outer * 0.97
    d.ellipse([c - r_casing, c - r_casing, c + r_casing, c + r_casing], fill=CASING)

    # Fleisch mit leichtem radialen Verlauf (dunkler nach außen)
    steps = 24
    for i in range(steps):
        t = i / (steps - 1)
        rr = r_meat * (1 - 0.35 * t)
        col = tuple(int(MEAT_DARK[k] + (MEAT[k] - MEAT_DARK[k]) * t) for k in range(3)) + (255,)
        d.ellipse([c - rr, c - rr, c + rr, c + rr], fill=col)

    # Fettaugen: unregelmäßige Ellipsen, nicht überlappend platziert
    placed: list[tuple[float, float, float]] = []
    attempts = 0
    while len(placed) < 26 and attempts < 4000:
        attempts += 1
        fr = big * rnd.uniform(0.022, 0.05)
        ang = rnd.uniform(0, 2 * math.pi)
        dist = math.sqrt(rnd.random()) * (r_meat - fr * 1.6)
        fx, fy = c + math.cos(ang) * dist, c + math.sin(ang) * dist
        if any(math.hypot(fx - px, fy - py) < (fr + pr) * 1.25 for px, py, pr in placed):
            continue
        placed.append((fx, fy, fr))
        rx, ry = fr * rnd.uniform(0.8, 1.25), fr * rnd.uniform(0.7, 1.1)
        d.ellipse([fx - rx, fy - ry, fx + rx, fy + ry], fill=FAT)

    # Pfefferkörner
    for _ in range(14):
        pr = big * rnd.uniform(0.008, 0.013)
        ang = rnd.uniform(0, 2 * math.pi)
        dist = math.sqrt(rnd.random()) * (r_meat - pr * 3)
        px, py = c + math.cos(ang) * dist, c + math.sin(ang) * dist
        d.ellipse([px - pr, py - pr, px + pr, py + pr], fill=PEPPER)

    # Glanzlicht oben links
    gloss = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    gd = ImageDraw.Draw(gloss)
    gr = r_meat * 0.75
    gd.ellipse([c - gr * 1.05, c - gr * 1.1, c + gr * 0.35, c + gr * 0.2], fill=(255, 255, 255, 38))
    gloss = gloss.filter(ImageFilter.GaussianBlur(big * 0.03))
    img = Image.alpha_composite(img, gloss)

    return img.resize((size, size), Image.LANCZOS)


def draw_board_background(width: int = 1920, height: int = 1080, seed: int = 42) -> Image.Image:
    """Hintergrund für den Salami-Modus: dunkles Holzbrett mit Salamischeiben am Rand.

    Die Mitte bleibt frei (dort stehen Spielerfigur und Start-Knopf)."""
    rnd = random.Random(seed)
    img = Image.new("RGB", (width, height), (58, 36, 22))
    d = ImageDraw.Draw(img)

    # Holzmaserung: viele leicht gewellte, horizontale Linien in Brauntönen
    for y in range(0, height, 3):
        shade = rnd.randint(-14, 14)
        base = (74 + shade, 46 + shade // 2, 28 + shade // 3)
        amp = rnd.uniform(1.5, 6)
        freq = rnd.uniform(0.002, 0.006)
        phase = rnd.uniform(0, math.tau)
        pts = [(x, y + amp * math.sin(x * freq + phase)) for x in range(0, width + 40, 40)]
        d.line(pts, fill=base, width=3)
    # Bretter-Fugen
    for x in (width // 3, 2 * width // 3):
        d.line([(x, 0), (x, height)], fill=(36, 22, 13), width=4)
    img = img.filter(ImageFilter.GaussianBlur(1.2))

    # Salamischeiben: links, rechts und unten, nicht in der Mitte
    slices = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    shadows = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadows)
    placed: list[tuple[int, int, int]] = []
    attempts = 0
    while len(placed) < 22 and attempts < 5000:
        attempts += 1
        size = rnd.randint(130, 250)
        x = rnd.randint(-size // 3, width - size * 2 // 3)
        y = rnd.randint(-size // 3, height - size * 2 // 3)
        cx, cy = x + size / 2, y + size / 2
        # Mittelbereich freihalten (Ellipse um die Bildmitte)
        if ((cx - width / 2) / (width * 0.30)) ** 2 + ((cy - height * 0.45) / (height * 0.40)) ** 2 < 1:
            continue
        if any(math.hypot(cx - px, cy - py) < (size + ps) * 0.42 for px, py, ps in placed):
            continue
        placed.append((int(cx), int(cy), size))
        piece = draw_slice(size, seed=rnd.randint(0, 10_000)).rotate(rnd.uniform(0, 360), resample=Image.BICUBIC)
        sd.ellipse([x + 8, y + 14, x + size + 8, y + size + 14], fill=(0, 0, 0, 120))
        slices.alpha_composite(piece, (x, y))
    shadows = shadows.filter(ImageFilter.GaussianBlur(10))

    out = img.convert("RGBA")
    out.alpha_composite(shadows)
    out.alpha_composite(slices)

    # Vignette + leichte Abdunklung, damit Text und Knöpfe lesbar bleiben
    vignette = Image.new("L", (width, height), 0)
    vd = ImageDraw.Draw(vignette)
    for i in range(60):
        t = i / 59
        inset = int(t * min(width, height) * 0.45)
        vd.rectangle([inset, inset, width - inset, height - inset], fill=int(70 + 110 * t))
    vignette = vignette.filter(ImageFilter.GaussianBlur(80))
    dark = Image.new("RGBA", (width, height), (20, 10, 6, 255))
    out = Image.composite(out, dark, vignette)
    return out.convert("RGB")


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    board = draw_board_background()
    board_path = ROOT / "launcher/public/themes/salami_theme.jpg"
    board.save(board_path, quality=85, optimize=True)
    print("->", board_path.relative_to(ROOT))

    master = draw_slice(1024)
    master.save(OUT / "salami-logo-1024.png")
    for s in (512, 128, 64):
        master.resize((s, s), Image.LANCZOS).save(OUT / f"salami-logo-{s}.png")

    # Verteilen
    targets = {
        ROOT / "mod/src/main/resources/assets/salami/icon.png": OUT / "salami-logo-128.png",
        ROOT / "mod/src/main/resources/assets/salami/textures/gui/logo.png": OUT / "salami-logo-64.png",
        ROOT / "launcher/public/logo.png": OUT / "salami-logo-512.png",
        ROOT / "launcher/public/salami-logo-64.png": OUT / "salami-logo-64.png",
    }
    for dst, src in targets.items():
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(src, dst)
        print("->", dst.relative_to(ROOT))


if __name__ == "__main__":
    main()
