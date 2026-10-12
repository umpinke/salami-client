"""Ersetzt NoRisk-Branding in den Launcher-Übersetzungen durch "Salami".

Nur WERTE werden geändert, Schlüssel bleiben gleich (der Code referenziert sie).
Texte zu NoRisk-eigenen Diensten (AGB, Crash-Upload, Werbung, Socials …) bleiben
unverändert – diese Funktionen sind im Salami Client ohnehin ausgeblendet.

Wiederholbar: nach einem Upstream-Merge einfach erneut ausführen.
    python -I scripts/rebrand_translations.py
"""

from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TRANSLATIONS = ROOT / "launcher" / "src" / "i18n" / "translations"

# Schlüssel, deren Texte sich auf NoRisk selbst beziehen (Team, Website, Dienste).
SKIP_PREFIXES = (
    "tos.",
    "crash_modal.",
    "applixir.",
    "socials.",
    "rollout.",
    "deep_link.",
    "analytics.",
    "designer.",
    "syncPacks.presets.noriskclient",
    "crash_analysis.status.new_nrc_bug",
)

RULES: list[tuple[re.Pattern[str], str]] = [
    (re.compile(r"NoRisk ?Client Launcher"), "Salami Client"),
    (re.compile(r"NoRisk Launcher"), "Salami Client"),
    (re.compile(r"NoRisk ?Client"), "Salami Client"),
    (re.compile(r"norisk client"), "salami client"),
    (re.compile(r"NoRiskMod\b"), "Salami-Mod"),
    (re.compile(r"\bNRC\b(?!\+)"), "Salami"),
    (re.compile(r"\bNoRisk\b"), "Salami"),
]


def rebrand(value: str) -> str:
    for pattern, replacement in RULES:
        value = pattern.sub(replacement, value)
    return value


def main() -> None:
    for path in sorted(TRANSLATIONS.glob("*.json")):
        raw = path.read_text(encoding="utf-8")
        data: dict[str, str] = json.loads(raw)
        changed = 0
        for key, value in data.items():
            if not isinstance(value, str) or key.startswith(SKIP_PREFIXES):
                continue
            new_value = rebrand(value)
            if new_value != value:
                data[key] = new_value
                changed += 1
        indent = 2 if raw.startswith("{\n  \"") else 4 if raw.startswith("{\n    \"") else 2
        text = json.dumps(data, ensure_ascii=False, indent=indent)
        if raw.endswith("\n"):
            text += "\n"
        path.write_text(text, encoding="utf-8", newline="\n")  # LF wie im Upstream
        print(f"{path.name}: {changed} Werte angepasst")


if __name__ == "__main__":
    main()
