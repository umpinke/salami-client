"""Hüte für salami-core (Cosmetics → Hüte): Textur und Symbol der Weihnachtsmütze.

Die Mütze ist ein kleines 3D-Modell, das auf der Schulter sitzt (render/SantaHatLayer). Die
Textur ist absichtlich einfach aufgeteilt: Jeder Würfel des Modells holt seine Fläche aus
einem einfarbigen Bereich, so passt jede Seite ohne Abwicklung.

    Textur 64x32:  Fell (weiß)  Zeile 0–7,  Spalte 0–31   → Krempe (0,0), Bommel (20,0)
                   Stoff (rot)  Zeile 8–23, Spalte 0–39   → Stufen (0,8), (0,16), Spitze (16,16)

Das Symbol (32x32) zeigt die Mütze im Cosmetics-Menü.

    python branding/generate_hats.py
"""

from __future__ import annotations

import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "mod/src/main/resources/assets/salami/textures/cosmetics"

FUR = (244, 241, 236)
FUR_SHADE = (222, 218, 212)
RED = (196, 32, 42)
RED_DARK = (160, 22, 32)
RED_LIGHT = (222, 58, 62)
OUTLINE = (92, 14, 20)


def jitter(color: tuple[int, int, int], rng: random.Random, amount: int) -> tuple[int, int, int, int]:
    d = rng.randint(-amount, amount)
    return tuple(max(0, min(255, c + d)) for c in color) + (255,)


def texture() -> Image.Image:
    rng = random.Random(1225)
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    px = img.load()
    # Fell: hell mit flauschigen Schatten
    for y in range(0, 8):
        for x in range(0, 32):
            base = FUR_SHADE if rng.random() < 0.22 else FUR
            px[x, y] = jitter(base, rng, 6)
    # Stoff: rot mit dunkleren Fäden und einzelnen Glanzpunkten
    for y in range(8, 24):
        for x in range(0, 40):
            roll = rng.random()
            base = RED_DARK if roll < 0.18 else RED_LIGHT if roll > 0.94 else RED
            px[x, y] = jitter(base, rng, 8)
    return img


# Symbol im Pixel-Stil: Krempe unten, Spitze nach rechts gebogen, Bommel oben rechts
ICON = [
    "................................",
    "................................",
    "................................",
    "......................WWWW......",
    ".....................WWWWWW.....",
    ".....................WWWWWW.....",
    "..............OOO....WWWWWW.....",
    "............OORRRO....WWWW......",
    "...........ORRRRRRO..OO.........",
    "..........ORRRRRRRROORO.........",
    ".........ORRRRRRRRRRRRO.........",
    ".........ORRRRRRRRRRRO..........",
    "........ORRRRRRRRRRRO...........",
    "........ORRRRRRRRRRO............",
    ".......ORRRRRRRRRRRO............",
    ".......ORRRRRRRRRRRRO...........",
    "......ORRRRRRRRRRRRRO...........",
    "......ORRRRRRRRRRRRRRO..........",
    ".....ORRRRRRRRRRRRRRRO..........",
    ".....ORRRRRRRRRRRRRRRRO.........",
    "....WWWWWWWWWWWWWWWWWWWW........",
    "...WWWWWWWWWWWWWWWWWWWWWW.......",
    "...WWWWWWWWWWWWWWWWWWWWWW.......",
    "...WWWWWWWWWWWWWWWWWWWWWW.......",
    "....WWWWWWWWWWWWWWWWWWWW........",
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
]


def icon() -> Image.Image:
    rng = random.Random(42)
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(ICON):
        for x, ch in enumerate(row):
            if ch == "W":
                px[x, y] = jitter(FUR_SHADE if rng.random() < 0.25 else FUR, rng, 5)
            elif ch == "R":
                # Licht von links oben
                base = RED_LIGHT if x + y < 22 else RED_DARK if x - y > 4 else RED
                px[x, y] = jitter(base, rng, 6)
            elif ch == "O":
                px[x, y] = OUTLINE + (255,)
    return img


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    texture().save(OUT / "santa_hat.png")
    icon().save(OUT / "santa_hat_icon.png")
    print(f"geschrieben: {OUT / 'santa_hat.png'}, {OUT / 'santa_hat_icon.png'}")


if __name__ == "__main__":
    main()
