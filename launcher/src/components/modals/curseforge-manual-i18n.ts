import i18n from "../../i18n/i18n";

/** Texte des Fensters „Dateien selbst herunterladen“ (andere Sprachen fallen auf Englisch zurück). */
const STRINGS: Record<string, Record<string, string>> = {
  de: {
    "curseforge_manual.title": "Ein paar Dateien musst du selbst laden",
    "curseforge_manual.intro":
      "Die Autoren dieser Dateien erlauben Downloads nur direkt auf der CurseForge-Webseite – der Launcher darf sie nicht selbst laden. Öffne jede Seite und klick dort auf „Download“. Die Datei landet in deinem Download-Ordner.",
    "curseforge_manual.mods": "Mods",
    "curseforge_manual.mods_hint":
      "Danach einfach nochmal auf Spielen klicken: Der Launcher findet die Dateien im Download-Ordner, prüft sie und übernimmt sie selbst.",
    "curseforge_manual.content": "Ressourcenpakete & Co.",
    "curseforge_manual.content_hint": "Diese Dateien nach dem Herunterladen in den angegebenen Ordner des Profils legen.",
    "curseforge_manual.folder": "Ordner: {{folder}}",
    "curseforge_manual.open": "Auf CurseForge öffnen",
    "curseforge_manual.open_folder": "Ordner öffnen",
    "curseforge_manual.open_all": "Alle Seiten öffnen",
    "curseforge_manual.close": "Schließen",
    "curseforge_manual.open_failed": "Konnte nicht geöffnet werden: {{error}}",
  },
  en: {
    "curseforge_manual.title": "A few files need a manual download",
    "curseforge_manual.intro":
      "The authors of these files only allow downloads directly on the CurseForge website, so the launcher may not download them itself. Open each page and click “Download” there. The file ends up in your downloads folder.",
    "curseforge_manual.mods": "Mods",
    "curseforge_manual.mods_hint":
      "Then just click Play again: the launcher finds the files in your downloads folder, checks them and takes them over.",
    "curseforge_manual.content": "Resource packs & more",
    "curseforge_manual.content_hint": "Put these files into the profile folder shown after downloading them.",
    "curseforge_manual.folder": "Folder: {{folder}}",
    "curseforge_manual.open": "Open on CurseForge",
    "curseforge_manual.open_folder": "Open folder",
    "curseforge_manual.open_all": "Open all pages",
    "curseforge_manual.close": "Close",
    "curseforge_manual.open_failed": "Could not open: {{error}}",
  },
};

for (const [lng, strings] of Object.entries(STRINGS)) {
  i18n.addResourceBundle(lng, "translation", strings, true, false);
}
