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

YELLOW = (255, 216, 0, 255)
CYAN = (0, 255, 255, 255)
BLUE = (0, 148, 255, 255)
TEMPLATE_RED = (255, 0, 0, 255)
ORANGE = (255, 120, 0, 255)
GREEN = (40, 162, 0, 255)
PURPLE = (216, 0, 255, 255)

# Cape-Box 10x16x1 bei (0,0): (Bereich im 64x32-Raster, Farbe, Beschriftung, gedreht)
TEMPLATE_CAPE = [
    ((1, 0, 10, 1), YELLOW, "Oben", False),
    ((11, 0, 10, 1), CYAN, "Unten", False),
    ((0, 1, 1, 16), YELLOW, "Links", True),
    ((1, 1, 10, 16), BLUE, "Vorne", False),
    ((11, 1, 1, 16), YELLOW, "Rechts", True),
    ((12, 1, 10, 16), TEMPLATE_RED, "Hinten", False),
]

# Elytra (Box 10x20x2 bei (22,0)): Minecraft zeichnet nur diese Pixel – dieselbe Form wie
# die Original-Elytra (textures/entity/equipment/wings/elytra.png). Zeile -> Spaltenbereiche.
ELYTRA_ROWS = {
    0: [(31, 39)], 1: [(32, 33)], 2: [(34, 41)], 3: [(34, 42)],
    4: [(35, 43)], 5: [(35, 43)], 6: [(35, 43)],
    7: [(35, 44)], 8: [(35, 44)], 9: [(35, 44)], 10: [(35, 44)],
    **{y: [(22, 22), (36, 45)] for y in range(11, 16)},
    **{y: [(22, 22), (37, 45)] for y in range(16, 19)},
    19: [(22, 22), (38, 45)], 20: [(22, 22), (38, 45)], 21: [(22, 22), (39, 45)],
}


def elytra_part(x: int, y: int) -> str | None:
    """Seite eines benutzten Elytra-Pixels (wie in der Vorlage beschriftet)."""
    if not any(a <= x <= b for a, b in ELYTRA_ROWS.get(y, [])):
        return None
    if y == 0:
        return "top" if x <= 33 else "bottom"
    if y == 1:
        return "top"
    if x == 22:
        return "left"
    if x <= 35:
        return "right"
    return "front"


ELYTRA_STYLE = {"top": ORANGE, "bottom": CYAN, "left": GREEN, "right": GREEN, "front": PURPLE}
# Beschriftung je Elytra-Seite: (Mitte im 64x32-Raster, Text, gedreht)
ELYTRA_LABELS = [
    ((32.9, 0.9), "Oben", False),
    ((37.0, 0.5), "Unten", False),
    ((35.0, 6.5), "Rechts", True),
    ((40.8, 9.0), "Vorne", False),
    ((22.5, 16.5), "Links", True),
]


def _label(img: Image.Image, center, text: str, size: int, rotated: bool) -> None:
    """Schwarze Beschriftung, bei schmalen Streifen um 90° gedreht."""
    from PIL import ImageFont

    font = ImageFont.truetype(str(FONT), size)
    probe = ImageDraw.Draw(img).textbbox((0, 0), text, font=font, anchor="lt")
    w, h = probe[2] - probe[0] + 2, probe[3] - probe[1] + 2
    layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ImageDraw.Draw(layer).text((1, 1), text, font=font, fill=(0, 0, 0, 255), anchor="lt")
    if rotated:
        layer = layer.rotate(-90, expand=True)
    cx, cy = center
    img.alpha_composite(layer, (round(cx - layer.width / 2), round(cy - layer.height / 2)))


def template() -> Image.Image:
    """Vorlage zum Selbermalen: Cape + Elytra, jede Seite eigene Farbe + Beschriftung, Rest durchsichtig."""
    s = TEMPLATE_SCALE
    img = Image.new("RGBA", (64 * s, 32 * s), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    for (x, y, w, h), color, _, _ in TEMPLATE_CAPE:
        draw.rectangle((x * s, y * s, (x + w) * s - 1, (y + h) * s - 1), fill=color)
    for y, ranges in ELYTRA_ROWS.items():
        for a, b in ranges:
            for x in range(a, b + 1):
                draw.rectangle((x * s, y * s, (x + 1) * s - 1, (y + 1) * s - 1), fill=ELYTRA_STYLE[elytra_part(x, y)])
    for (x, y, w, h), _, label, rotated in TEMPLATE_CAPE:
        big = w >= 10 and h >= 10
        _label(img, ((x + w / 2) * s, (y + h / 2) * s - (3 * s if big else 0)), label, 16 if big else 9, rotated)
    for (cx, cy), label, rotated in ELYTRA_LABELS:
        _label(img, (cx * s, cy * s), label, 14 if label == "Vorne" else 9, rotated)
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
