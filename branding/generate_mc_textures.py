"""Pixel-Art-Texturen für den Salami-Modus im Spiel (salami-core).

Erzeugt:
  textures/gui/sprites/widget/button{,_highlighted,_disabled}.png   (200x20, Nine-Slice)
  textures/gui/title/salami.png    (256x64, Titelbild-Logo "SALAMI")
  textures/gui/title/edition.png   (128x16, Untertitel "CLIENT")

Deterministisch (fester Seed). Aufruf: python -I branding/generate_mc_textures.py
"""

from __future__ import annotations

import json
import random
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "mod/src/main/resources/assets/salami/textures/gui"

RED = (178, 58, 58, 255)
RED_LIGHT = (208, 96, 96, 255)
RED_DARK = (122, 36, 37, 255)
FAT = (243, 227, 211, 255)
FAT_DIM = (222, 196, 180, 255)
PEPPER = (43, 29, 26, 255)
CASING = (122, 74, 53, 255)
CASING_DARK = (74, 40, 30, 255)

# 5x7-Pixelschrift, nur die benötigten Buchstaben
GLYPHS = {
    "S": [".####", "#....", "#....", ".###.", "....#", "....#", "####."],
    "A": [".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"],
    "L": ["#....", "#....", "#....", "#....", "#....", "#....", "#####"],
    "M": ["#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#"],
    "I": ["#####", "..#..", "..#..", "..#..", "..#..", "..#..", "#####"],
    "C": [".####", "#....", "#....", "#....", "#....", "#....", ".####"],
    "E": ["#####", "#....", "#....", "####.", "#....", "#....", "#####"],
    "N": ["#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#", "#...#"],
    "T": ["#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."],
}


def button(fill, light, dark, outline, spots, seed) -> Image.Image:
    """200x20 im Vanilla-Aufbau: 1px Rahmen, 1px Fase, Fläche mit Fettaugen."""
    rnd = random.Random(seed)
    img = Image.new("RGBA", (200, 20), fill)
    d = ImageDraw.Draw(img)
    # Fettaugen und Pfeffer nur oben/unten – die Textzeile in der Mitte bleibt ruhig und lesbar
    edge_rows = [3, 4, 14, 15]
    for _ in range(40):
        x, y = rnd.randint(4, 194), rnd.choice(edge_rows)
        w = rnd.choice([1, 2, 2, 3])
        d.rectangle([x, y, x + w - 1, y], fill=spots)
    for _ in range(10):
        d.point((rnd.randint(4, 195), rnd.choice(edge_rows)), fill=PEPPER)
    # Fase: oben/links hell, unten/rechts dunkel
    d.line([(1, 1), (198, 1)], fill=light)
    d.line([(1, 1), (1, 18)], fill=light)
    d.line([(1, 18), (198, 18)], fill=dark)
    d.line([(198, 1), (198, 18)], fill=dark)
    d.rectangle([0, 0, 199, 19], outline=outline)
    return img


def draw_text(img: Image.Image, text: str, x: int, y: int, scale: int, depth: int) -> None:
    """Pixeltext mit Salami-Fläche, schwarzem Rand und brauner 3D-Extrusion."""
    d = ImageDraw.Draw(img)
    rnd = random.Random(7)
    cells: list[tuple[int, int]] = []
    cx = x
    for ch in text:
        glyph = GLYPHS[ch]
        for gy, row in enumerate(glyph):
            for gx, bit in enumerate(row):
                if bit == "#":
                    cells.append((cx + gx * scale, y + gy * scale))
        cx += (len(glyph[0]) + 1) * scale

    for k in range(depth, 0, -1):  # Extrusion
        shade = CASING if k == 1 else CASING_DARK
        for px, py in cells:
            d.rectangle([px + k, py + k, px + k + scale - 1, py + k + scale - 1], fill=shade)
    for px, py in cells:  # Kontur
        d.rectangle([px - 1, py - 1, px + scale, py + scale], fill=PEPPER)
    filled = set(cells)
    for px, py in cells:  # Fläche
        d.rectangle([px, py, px + scale - 1, py + scale - 1], fill=RED)
    for px, py in cells:  # Lichtkante nur an der Oberkante der Buchstaben, Schatten unten
        if (px, py - scale) not in filled:
            d.line([(px, py), (px + scale - 1, py)], fill=RED_LIGHT)
        if (px, py + scale) not in filled:
            d.line([(px, py + scale - 1), (px + scale - 1, py + scale - 1)], fill=RED_DARK)
    for px, py in cells:  # vereinzelte Fettaugen
        if scale >= 4 and rnd.random() < 0.3:
            fx, fy = px + rnd.randint(1, scale - 3), py + rnd.randint(1, scale - 3)
            d.rectangle([fx, fy, fx + 1, fy], fill=FAT)


def text_width(text: str, scale: int) -> int:
    return sum((len(GLYPHS[c][0]) + 1) * scale for c in text) - scale


def pixel_slice(size: int) -> Image.Image:
    """Kleine Pixel-Salamischeibe für das Logo."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse([0, 0, size - 1, size - 1], fill=PEPPER)
    d.ellipse([1, 1, size - 2, size - 2], fill=CASING)
    d.ellipse([3, 3, size - 4, size - 4], fill=RED)
    rnd = random.Random(size)
    for _ in range(size // 3):
        x, y = rnd.randint(6, size - 8), rnd.randint(6, size - 8)
        d.rectangle([x, y, x + 1, y + 1], fill=FAT)
    return img


def main() -> None:
    sprites = ASSETS / "sprites/widget"
    sprites.mkdir(parents=True, exist_ok=True)
    meta = {"gui": {"scaling": {"type": "nine_slice", "width": 200, "height": 20, "border": 3}}}
    variants = {
        "button": button(RED, RED_LIGHT, RED_DARK, PEPPER, FAT_DIM, 1),
        "button_highlighted": button((194, 72, 72, 255), (226, 120, 120, 255), RED_DARK, FAT, FAT, 1),
        "button_disabled": button((92, 58, 51, 255), (112, 74, 66, 255), (64, 40, 35, 255), PEPPER, (130, 104, 92, 255), 1),
    }
    for name, image in variants.items():
        image.save(sprites / f"{name}.png")
        (sprites / f"{name}.png.mcmeta").write_text(json.dumps(meta, indent=2) + "\n", encoding="utf-8", newline="\n")

    title_dir = ASSETS / "title"
    title_dir.mkdir(parents=True, exist_ok=True)

    logo = Image.new("RGBA", (256, 64), (0, 0, 0, 0))
    scale, depth = 5, 4
    width = text_width("SALAMI", scale)
    x0 = (256 - width) // 2
    draw_text(logo, "SALAMI", x0, 3, scale, depth)
    s = pixel_slice(30)
    logo.alpha_composite(s, (x0 - 38, 6))
    logo.alpha_composite(s, (x0 + width + 8, 6))
    logo.save(title_dir / "salami.png")

    edition = Image.new("RGBA", (128, 16), (0, 0, 0, 0))
    width = text_width("CLIENT", 2)
    draw_text(edition, "CLIENT", (128 - width) // 2, 1, 2, 1)
    edition.save(title_dir / "edition.png")

    for p in sorted(ASSETS.rglob("*.png")):
        print("->", p.relative_to(ROOT))


if __name__ == "__main__":
    main()
