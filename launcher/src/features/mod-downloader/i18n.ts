import i18n from "../../i18n/i18n";

/**
 * Texte des Mod-Downloaders. Getrennt von den Upstream-Übersetzungsdateien,
 * damit Updates des NoRisk-Launchers ohne Merge-Konflikte übernommen werden.
 * Andere Sprachen fallen auf Englisch zurück.
 */
const STRINGS: Record<string, Record<string, string>> = {
  de: {
    "salami.downloader.plan.title": "Abhängigkeiten von {{name}}",
    "salami.downloader.plan.required": "Wird mitinstalliert",
    "salami.downloader.plan.already": "Schon vorhanden",
    "salami.downloader.plan.optional": "Optional – anhaken zum Mitinstallieren",
    "salami.downloader.plan.conflicts": "Konflikt mit installierten Mods",
    "salami.downloader.plan.conflict_hint":
      "Laut Autor sind diese Mods nicht mit {{name}} kompatibel. Du kannst trotzdem installieren, das Spiel kann dann aber abstürzen.",
    "salami.downloader.plan.install": "Installieren",
    "salami.downloader.plan.install_anyway": "Trotzdem installieren",
    "salami.downloader.plan.cancel": "Abbrechen",
    "salami.downloader.plan.dont_ask": "Nicht mehr fragen (Konflikte werden immer angezeigt)",
    "salami.downloader.plan.unknown_project": "{{platform}}-Projekt {{id}}",
    "salami.downloader.cancelled": "Installation abgebrochen",
    "salami.downloader.optional_installed": "{{count}} optionale Abhängigkeit(en) installiert",
    "salami.downloader.optional_failed": "{{name}} konnte nicht installiert werden: {{error}}",
    "salami.downloader.no_compatible": "{{name}}: keine Version für {{game}} / {{loader}}",

    "salami.downloader.cart.title": "Mod-Warenkorb",
    "salami.downloader.cart.add": "In den Warenkorb",
    "salami.downloader.cart.in_cart": "Im Warenkorb",
    "salami.downloader.cart.added": "{{name}} liegt im Warenkorb",
    "salami.downloader.cart.empty": "Noch leer – leg Mods mit dem Warenkorb-Knopf auf der Karte hinein.",
    "salami.downloader.cart.profile": "Ziel-Profil",
    "salami.downloader.cart.no_profile": "Bitte ein Profil wählen",
    "salami.downloader.cart.install_all": "Alle installieren ({{count}})",
    "salami.downloader.cart.installing": "Installiere {{current}}/{{total}}: {{name}}",
    "salami.downloader.cart.clear": "Leeren",
    "salami.downloader.cart.result": "{{ok}} installiert, {{already}} schon vorhanden, {{skipped}} ohne passende Version, {{failed}} fehlgeschlagen",
    "salami.downloader.cart.open": "Warenkorb ({{count}})",
  },
  en: {
    "salami.downloader.plan.title": "Dependencies of {{name}}",
    "salami.downloader.plan.required": "Will be installed too",
    "salami.downloader.plan.already": "Already installed",
    "salami.downloader.plan.optional": "Optional – tick to install too",
    "salami.downloader.plan.conflicts": "Conflicts with installed mods",
    "salami.downloader.plan.conflict_hint":
      "According to the author these mods are incompatible with {{name}}. You can still install it, but the game may crash.",
    "salami.downloader.plan.install": "Install",
    "salami.downloader.plan.install_anyway": "Install anyway",
    "salami.downloader.plan.cancel": "Cancel",
    "salami.downloader.plan.dont_ask": "Don't ask again (conflicts are always shown)",
    "salami.downloader.plan.unknown_project": "{{platform}} project {{id}}",
    "salami.downloader.cancelled": "Installation cancelled",
    "salami.downloader.optional_installed": "{{count}} optional dependency(ies) installed",
    "salami.downloader.optional_failed": "{{name}} could not be installed: {{error}}",
    "salami.downloader.no_compatible": "{{name}}: no version for {{game}} / {{loader}}",

    "salami.downloader.cart.title": "Mod cart",
    "salami.downloader.cart.add": "Add to cart",
    "salami.downloader.cart.in_cart": "In cart",
    "salami.downloader.cart.added": "{{name}} added to cart",
    "salami.downloader.cart.empty": "Still empty – add mods with the cart button on a card.",
    "salami.downloader.cart.profile": "Target profile",
    "salami.downloader.cart.no_profile": "Please choose a profile",
    "salami.downloader.cart.install_all": "Install all ({{count}})",
    "salami.downloader.cart.installing": "Installing {{current}}/{{total}}: {{name}}",
    "salami.downloader.cart.clear": "Clear",
    "salami.downloader.cart.result": "{{ok}} installed, {{already}} already present, {{skipped}} without a matching version, {{failed}} failed",
    "salami.downloader.cart.open": "Cart ({{count}})",
  },
};

let registered = false;

export function registerDownloaderStrings(): void {
  if (registered) return;
  for (const [lng, strings] of Object.entries(STRINGS)) {
    i18n.addResourceBundle(lng, "translation", strings, true, false);
  }
  registered = true;
}

registerDownloaderStrings();
