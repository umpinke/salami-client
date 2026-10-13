"""Salami-Capes für salami-core (CapesModule) und den Launcher (Cape-Galerie, Vorlage).

Erzeugt Cape-Texturen im Minecraft-Layout (64x32, hier 4-fach = 256x128):
Cape-Box bei (0,0) mit 10x16x1, Elytra-Box bei (22,0) mit 10x20x2.

    python branding/generate_capes.py
"""

from __future__ import annotations

import math
import random
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "mod/src/main/resources/assets/salami/textures/cape"

S = 4  # Skalierung gegenüber 64x32
RED = (178, 58, 58, 255)
RED_LIGHT = (208, 96, 96, 255)
RED_DARK = (122, 36, 37, 255)
FAT = (243, 227, 211, 255)
FAT_DIM = (222, 196, 180, 255)
PEPPER = (43, 29, 26, 255)
CASING = (122, 74, 53, 255)
CASING_DARK = (74, 40, 30, 255)
PEPPERONI = (196, 52, 38, 255)
PEPPERONI_DARK = (150, 30, 24, 255)
WOOD = (196, 150, 98, 255)
WOOD_DARK = (164, 118, 72, 255)
WOOD_LIGHT = (214, 172, 120, 255)

# Flächen im 64x32-Raster: (x, y, breite, höhe)
CAPE_FRONT = (1, 1, 10, 16)   # Außenseite, von hinten sichtbar
CAPE_BACK = (12, 1, 10, 16)   # Innenseite
ELYTRA_FRONT = (24, 2, 10, 20)
ELYTRA_BACK = (36, 2, 10, 20)


def box(area):
    x, y, w, h = area
    return (x * S, y * S, (x + w) * S - 1, (y + h) * S - 1)


def meat(draw: ImageDraw.ImageDraw, area, rng: random.Random, base=RED, spots=FAT, spot_dim=FAT_DIM, pepper=PEPPER) -> None:
    x0, y0, x1, y1 = box(area)
    draw.rectangle((x0, y0, x1, y1), fill=base)
    w, h = x1 - x0, y1 - y0
    for _ in range(w * h // 55):
        cx, cy = rng.randint(x0, x1), rng.randint(y0, y1)
        r = rng.choice((1, 1, 2, 2, 3))
        draw.ellipse((cx - r, cy - r, cx + r, cy + r), fill=rng.choice((spots, spots, spot_dim)))
    for _ in range(w * h // 160):
        cx, cy = rng.randint(x0, x1), rng.randint(y0, y1)
        draw.rectangle((cx, cy, cx + 1, cy + 1), fill=pepper)


def border(draw: ImageDraw.ImageDraw, area, outer, inner, width=S) -> None:
    x0, y0, x1, y1 = box(area)
    draw.rectangle((x0, y0, x1, y1), outline=outer, width=width)
    draw.rectangle((x0 + width, y0 + width, x1 - width, y1 - width), outline=inner, width=max(1, width // 2))


def slice_disc(draw: ImageDraw.ImageDraw, cx: int, cy: int, r: int, rng: random.Random) -> None:
    """Salamischeibe: Pelle, Fleisch, Fettpunkte."""
    draw.ellipse((cx - r, cy - r, cx + r, cy + r), fill=CASING_DARK)
    draw.ellipse((cx - r + 2, cy - r + 2, cx + r - 2, cy + r - 2), fill=RED)
    inner = r - 4
    for _ in range(r + 4):
        a = rng.uniform(0, 6.283)
        d = (inner - 2) * math.sqrt(rng.random())  # gleichmäßig über die Fläche
        px, py = cx + int(math.cos(a) * d), cy + int(math.sin(a) * d)
        s = rng.choice((0, 0, 1))
        draw.ellipse((px - s, py - s, px + s, py + s), fill=FAT)


def canvas() -> tuple[Image.Image, ImageDraw.ImageDraw]:
    img = Image.new("RGBA", (64 * S, 32 * S), (0, 0, 0, 0))
    return img, ImageDraw.Draw(img)


def salami() -> Image.Image:
    rng = random.Random(7)
    img, draw = canvas()
    draw.rectangle((0, 0, 22 * S - 1, 17 * S - 1), fill=CASING)       # Kanten der Cape-Box
    draw.rectangle((22 * S, 0, 46 * S - 1, 22 * S - 1), fill=CASING)  # Kanten der Elytra-Box
    for area in (CAPE_FRONT, ELYTRA_FRONT, ELYTRA_BACK):
        meat(draw, area, rng)
        border(draw, area, CASING_DARK, CASING)
    meat(draw, CAPE_BACK, rng, base=RED_DARK, spots=FAT_DIM)
    return img


def pepperoni() -> Image.Image:
    rng = random.Random(11)
    img, draw = canvas()
    draw.rectangle((0, 0, 22 * S - 1, 17 * S - 1), fill=PEPPERONI_DARK)
    draw.rectangle((22 * S, 0, 46 * S - 1, 22 * S - 1), fill=PEPPERONI_DARK)
    for area in (CAPE_FRONT, CAPE_BACK, ELYTRA_FRONT, ELYTRA_BACK):
        x0, y0, x1, y1 = box(area)
        draw.rectangle((x0, y0, x1, y1), fill=FAT if area is not CAPE_BACK else FAT_DIM)
        step = 5 * S
        for row, cy in enumerate(range(y0 + step // 2, y1, step)):
            offset = step // 2 if row % 2 else 0
            for cx in range(x0 + step // 2 - offset, x1 + step, step):
                if x0 <= cx <= x1:
                    r = 2 * S
                    draw.ellipse((cx - r, cy - r, cx + r, cy + r), fill=PEPPERONI)
                    draw.ellipse((cx - r + 2, cy - r + 2, cx - 1, cy - 1), fill=RED_LIGHT)
                    for _ in range(3):
                        px, py = cx + rng.randint(-r + 3, r - 3), cy + rng.randint(-r + 3, r - 3)
                        draw.rectangle((px, py, px + 1, py + 1), fill=PEPPERONI_DARK)
        border(draw, area, PEPPERONI_DARK, PEPPERONI, width=S // 2)
    return img


def board() -> Image.Image:
    rng = random.Random(3)
    img, draw = canvas()
    draw.rectangle((0, 0, 22 * S - 1, 17 * S - 1), fill=WOOD_DARK)
    draw.rectangle((22 * S, 0, 46 * S - 1, 22 * S - 1), fill=WOOD_DARK)
    for area in (CAPE_FRONT, CAPE_BACK, ELYTRA_FRONT, ELYTRA_BACK):
        x0, y0, x1, y1 = box(area)
        draw.rectangle((x0, y0, x1, y1), fill=WOOD)
        for y in range(y0, y1, 3):  # Maserung
            if rng.random() < 0.55:
                start = rng.randint(x0, x1 - 6)
                draw.line((start, y, min(x1, start + rng.randint(6, 24)), y), fill=rng.choice((WOOD_DARK, WOOD_LIGHT)))
        border(draw, area, WOOD_DARK, WOOD_LIGHT, width=S // 2)
    for area, radius in ((CAPE_FRONT, 4 * S), (ELYTRA_FRONT, 4 * S), (ELYTRA_BACK, 4 * S)):
        x0, y0, x1, y1 = box(area)
        slice_disc(draw, (x0 + x1) // 2, (y0 + y1) // 2, radius, rng)
    return img


TEMPLATE_SCALE = 8  # 512x256: groß genug für Beschriftungen, Verhältnis wie 64x32
TEMPLATE_OUT = ROOT / "launcher/src-tauri/assets/salami_cape_template.png"
LAUNCHER_CAPES = ROOT / "launcher/public/capes"
FONT = ROOT / "launcher/public/fonts/fredoka.ttf"

# (Bereich im 64x32-Raster, Farbe, Beschriftung)
TEMPLATE_AREAS = [
    ((1, 1, 10, 16), (178, 58, 58, 255), "AUSSEN\nsichtbar"),
    ((12, 1, 10, 16), (122, 36, 37, 255), "INNEN"),
    ((1, 0, 10, 1), (243, 227, 211, 255), ""),     # oben
    ((11, 0, 10, 1), (222, 196, 180, 255), ""),    # unten
    ((0, 1, 1, 16), (122, 74, 53, 255), ""),       # Rand links
    ((11, 1, 1, 16), (122, 74, 53, 255), ""),      # Rand rechts
    ((24, 2, 10, 20), (196, 150, 98, 255), "ELYTRA\naußen"),
    ((36, 2, 10, 20), (164, 118, 72, 255), "ELYTRA\ninnen"),
    ((24, 0, 10, 2), (243, 227, 211, 255), ""),
    ((34, 0, 10, 2), (222, 196, 180, 255), ""),
    ((22, 2, 2, 20), (74, 40, 30, 255), ""),
    ((34, 2, 2, 20), (74, 40, 30, 255), ""),
]


def template() -> Image.Image:
    """Vorlage zum Selbermalen: jede Fläche eigene Farbe + Beschriftung, Rest durchsichtig."""
    from PIL import ImageFont

    s = TEMPLATE_SCALE
    img = Image.new("RGBA", (64 * s, 32 * s), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    font = ImageFont.truetype(str(FONT), 15)
    small = ImageFont.truetype(str(FONT), 16)
    for (x, y, w, h), color, label in TEMPLATE_AREAS:
        box_ = (x * s, y * s, (x + w) * s - 1, (y + h) * s - 1)
        draw.rectangle(box_, fill=color)
        if label:
            cx, cy = (box_[0] + box_[2]) / 2, (box_[1] + box_[3]) / 2
            dark = sum(color[:3]) < 450
            draw.multiline_text((cx, cy), label, font=font, fill=(255, 255, 255, 230) if dark else (43, 29, 26, 230),
                                anchor="mm", align="center", spacing=4)
    draw.multiline_text((50 * s, 26 * s), "Salami Client\nCape-Vorlage", font=small, fill=(178, 58, 58, 255),
                        anchor="mm", align="center")
    return img


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    LAUNCHER_CAPES.mkdir(parents=True, exist_ok=True)
    for name, make in (("salami", salami), ("pepperoni", pepperoni), ("board", board)):
        image = make()
        for target in (OUT / f"{name}.png", LAUNCHER_CAPES / f"{name}.png"):
            image.save(target)
            print(target.relative_to(ROOT))
    TEMPLATE_OUT.parent.mkdir(parents=True, exist_ok=True)
    template().save(TEMPLATE_OUT)
    print(TEMPLATE_OUT.relative_to(ROOT))


if __name__ == "__main__":
    main()
