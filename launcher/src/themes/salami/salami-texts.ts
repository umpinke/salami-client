import i18n from "../../i18n/i18n";

/**
 * Texte des Salami-Modus.
 *
 * - `SALAMI_UI_STRINGS`: neue Schlüssel für die Salami-Oberfläche (immer geladen).
 * - `SALAMI_OVERLAY`: ersetzt vorhandene Texte, solange der Salami-Modus an ist.
 *
 * Bewusst getrennt von den Upstream-Übersetzungsdateien, damit Updates des
 * NoRisk-Launchers ohne Merge-Konflikte übernommen werden können.
 */

type Strings = Record<string, string>;

const SALAMI_UI_STRINGS: Record<string, Strings> = {
  de: {
    "salami.mode.title": "Salami-Modus",
    "salami.mode.description":
      "Der komplette Client im Salami-Look: Farben, Hintergrund, Texte – und im Spiel ClickGUI, HUD und Menüs.",
    "salami.mode.enabled": "Frisch angeschnitten!",
    "salami.mode.disabled": "Salami-Modus ausgeschaltet.",
    "salami.mode.game_hint": "Gilt für das Spiel ab dem nächsten Start.",
    "salami.look.title": "Wie soll dein Launcher aussehen?",
    "salami.look.subtitle": "Such dir einen Look aus. Du kannst ihn jederzeit in den Einstellungen ändern.",
    "salami.look.choose": "Diesen Look nehmen",
    "salami.look.current": "Aktiv",
    "salami.look.selected": "Ausgewählt",
    "salami.look.settings_title": "Look",
    "salami.look.settings_description": "Das Erscheinungsbild des ganzen Launchers – Navigation, Startseite und alle Seiten.",
    "salami.look.salumeria.name": "Salumeria",
    "salami.look.salumeria.tagline": "Edel wie das Etikett: Schwarz, Creme und feine Serifenschrift.",
    "salami.look.werkbank.name": "Pixel-Werkbank",
    "salami.look.werkbank.tagline": "Ganz im Minecraft-Stil: Pixel-Rahmen, Inventar-Felder und eine Hotbar als Navigation.",
    "salami.look.theke.name": "Feinkost-Theke",
    "salami.look.theke.tagline": "Verspielt: Markise, Schilder, Preisschildchen und ein Stempel zum Starten.",
    "salami.play.greeting": "Hallo, {{name}}",
    "salami.play.greeting_anon": "Willkommen",
    "salami.play.no_profile": "Kein Profil",
    "salami.play.no_profile_hint": "Leg ein Profil an, dann kann es losgehen.",
    "salami.play.create_profile": "Profil erstellen",
    "salami.play.version": "Minecraft {{version}} · {{loader}}",
    "salami.play.recent": "Zuletzt gespielt",
    "salami.play.online_one": "{{count}} Spieler online",
    "salami.play.online_other": "{{count}} Spieler online",
    "salami.play.online_hint": "So viele Salami-Launcher sind gerade offen",
    "salami.worlds.batch_delete_partial": "{{deleted}} von {{count}} Welten gelöscht. Nicht gelöscht: {{failed}}",
  },
  en: {
    "salami.mode.title": "Salami Mode",
    "salami.mode.description":
      "The whole client in salami style: colors, background, texts – and in-game ClickGUI, HUD and menus.",
    "salami.mode.enabled": "Freshly sliced!",
    "salami.mode.disabled": "Salami mode turned off.",
    "salami.mode.game_hint": "Applies to the game from the next launch.",
    "salami.look.title": "How should your launcher look?",
    "salami.look.subtitle": "Pick a look. You can change it any time in the settings.",
    "salami.look.choose": "Use this look",
    "salami.look.current": "Active",
    "salami.look.selected": "Selected",
    "salami.look.settings_title": "Look",
    "salami.look.settings_description": "The appearance of the whole launcher – navigation, start page and every page.",
    "salami.look.salumeria.name": "Salumeria",
    "salami.look.salumeria.tagline": "Elegant like the label: black, cream and a fine serif typeface.",
    "salami.look.werkbank.name": "Pixel Workbench",
    "salami.look.werkbank.tagline": "Pure Minecraft: pixel frames, inventory slots and a hotbar for navigation.",
    "salami.look.theke.name": "Deli Counter",
    "salami.look.theke.tagline": "Playful: awning, signs, price tags and a stamp to start the game.",
    "salami.play.greeting": "Hi, {{name}}",
    "salami.play.greeting_anon": "Welcome",
    "salami.play.no_profile": "No profile",
    "salami.play.no_profile_hint": "Create a profile to start playing.",
    "salami.play.create_profile": "Create profile",
    "salami.play.version": "Minecraft {{version}} · {{loader}}",
    "salami.play.recent": "Recently played",
    "salami.play.online_one": "{{count}} player online",
    "salami.play.online_other": "{{count}} players online",
    "salami.play.online_hint": "Salami launchers open right now",
    "salami.worlds.batch_delete_partial": "Deleted {{deleted}} of {{count}} worlds. Not deleted: {{failed}}",
  },
};

const SALAMI_OVERLAY: Record<string, Strings> = {
  de: {
    "nav.play": "Anschneiden",
    "nav.profiles": "Wurstsorten",
    "nav.mods": "Belag",
    "nav.skins": "Pellen",
    "nav.settings": "Gewürzmischung",
    "profiles.play": "Anschneiden",
    "launch.button": "Salami anschneiden",
    "launch.action.play": "Anschneiden",
    "launch.launching": "Wird angeschnitten...",
    "launch.starting": "Angeschnitten!",
    "launch.launched": "Angeschnitten!",
    "launch.minecraft_started": "✓ Die Salami ist angeschnitten – Minecraft läuft!",
    "launch.stopping": "Wird eingepackt...",
    "launch.error": "✗ Da ist die Wurst geplatzt: {{error}}",
    "common.loading": "Reift noch...",
    "modrinth.install": "Drauflegen",
    "modrinth.installing": "Wird belegt...",
  },
  en: {
    "nav.play": "Slice",
    "nav.profiles": "Varieties",
    "nav.mods": "Toppings",
    "nav.skins": "Casings",
    "nav.settings": "Spice Rack",
    "profiles.play": "Slice",
    "launch.button": "Slice the salami",
    "launch.action.play": "Slice",
    "launch.launching": "Slicing...",
    "launch.starting": "Sliced!",
    "launch.launched": "Sliced!",
    "launch.minecraft_started": "✓ Salami sliced – Minecraft is running!",
    "launch.stopping": "Wrapping up...",
    "launch.error": "✗ The sausage burst: {{error}}",
    "common.loading": "Still curing...",
    "modrinth.install": "Add topping",
    "modrinth.installing": "Adding topping...",
  },
};

/** Originalwerte der überschriebenen Schlüssel, je Sprache – für das Zurücksetzen. */
const originals = new Map<string, Strings>();
let uiStringsRegistered = false;

/** Registriert die `salami.*`-Schlüssel (einmalig; andere Sprachen fallen auf Englisch zurück). */
export function registerSalamiStrings(): void {
  if (uiStringsRegistered) return;
  for (const [lng, strings] of Object.entries(SALAMI_UI_STRINGS)) {
    i18n.addResourceBundle(lng, "translation", strings, true, false);
  }
  uiStringsRegistered = true;
}

/** Schaltet die Salami-Texte an/aus und lässt React neu rendern. */
export function applySalamiTexts(enabled: boolean): void {
  let changed = false;
  for (const [lng, overlay] of Object.entries(SALAMI_OVERLAY)) {
    if (!i18n.hasResourceBundle(lng, "translation")) continue;

    if (enabled && !originals.has(lng)) {
      const saved: Strings = {};
      for (const key of Object.keys(overlay)) {
        const value = i18n.getResource(lng, "translation", key);
        if (typeof value === "string") saved[key] = value;
      }
      originals.set(lng, saved);
      i18n.addResourceBundle(lng, "translation", overlay, true, true);
      changed = true;
    } else if (!enabled && originals.has(lng)) {
      i18n.addResourceBundle(lng, "translation", originals.get(lng)!, true, true);
      originals.delete(lng);
      changed = true;
    }
  }
  if (changed) {
    // react-i18next rendert nur bei Sprachwechsel neu – gleiche Sprache neu setzen.
    void i18n.changeLanguage(i18n.language);
  }
}
