package dev.salamiclient.gui.skin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Ein Look der Salami-Menüs im Spiel. Er folgt dem Look, den der Spieler im Launcher gewählt hat
 * (Launcher → {@code -Dsalami.look}, siehe {@link Skins}): „theke“ (Feinkost-Theke), „werkbank“
 * (Pixel-Werkbank) oder „salumeria“.
 *
 * <p>Alle Salami-Bildschirme zeichnen ihre Flächen, Knöpfe, Schalter, Reiter, Zeilen und Felder nur
 * über diese Bausteine – so sehen Mods, Einstellungen, Cosmetics, Freunde, Configs, Panel-Ansicht,
 * HUD-Editor und Minispiele im selben Look gleich aus. Startmenü und Mod-Liste haben pro Look einen
 * eigenen Aufbau ({@link #menuView()}, {@link #modsView()}).
 *
 * <p>Koordinaten sind die skalierten UI-Koordinaten der Bildschirme (Modul „Oberfläche“). Farben
 * sind ARGB. Texte kommen fertig übersetzt.
 *
 * <p>Wo welcher Baustein benutzt wird und wie man Screenshots pro Look macht, steht in
 * {@code gui/skin/SKINS.md}. Gemeinsame Helfer für alle Looks: {@link Icons} (Pixel-Symbole) und
 * {@link TextFit} (Texte einpassen statt mitten im Wort abschneiden).
 */
public interface Skin {
	/** „theke“, „werkbank“ oder „salumeria“. */
	String id();

	// ------------------------------------------------------------------ Flächen

	/**
	 * Hinter jedem Salami-Bildschirm, über der Welt (die Bildschirme rufen vorher Minecrafts eigenen
	 * Hintergrund auf: Unschärfe in der Welt, Panorama ohne Welt). Deckt Chat, Hotbar und HUD so weit
	 * ab, dass nichts mehr durch das Menü scheint. Darf oben Deko zeichnen (Theke: Markise), höchstens
	 * {@link #backdropTop()} hoch.
	 *
	 * <p>Ausnahmen ohne Hintergrund: HUD-Editor und Emote-Rad – dort muss man das Spiel sehen.
	 */
	void backdrop(GuiGraphics g, int width, int height);

	/**
	 * Höhe der Deko, die {@link #backdrop} oben zeichnet (0 = keine). Fenster, Leisten und Panels der
	 * Bildschirme beginnen erst darunter.
	 */
	int backdropTop();

	/** Eine Fläche der Art {@code kind}. */
	void surface(GuiGraphics g, Surface kind, int x, int y, int width, int height);

	/** Text- und Linienfarben, die auf einer Fläche der Art {@code kind} lesbar sind. */
	Ink ink(Surface kind);

	// ------------------------------------------------------------------ Bedienelemente

	/**
	 * Knopf mit Beschriftung (mittig, bei Platzmangel erst verkleinert, dann mit „…“ gekürzt).
	 * {@link ButtonKind#GHOST} nur auf hellen bzw. Fenster-Flächen ({@link Surface#WINDOW}, {@link Surface#CARD},
	 * {@link Surface#POPUP}); auf dem Hintergrund und auf {@link Surface#SIDE} {@link ButtonKind#SECONDARY} nehmen.
	 */
	void button(GuiGraphics g, Font font, int x, int y, int width, int height, String label, ButtonKind kind, UiState state);

	/** Knopf nur mit Symbol: der Look zeichnet Fläche und Rahmen, der Bildschirm danach das Symbol in {@link #buttonInk}. */
	void iconButton(GuiGraphics g, int x, int y, int size, ButtonKind kind, UiState state);

	/** Farbe für Text/Symbol auf einem Knopf. */
	int buttonInk(ButtonKind kind, UiState state);

	/** Größe eines Schalters (an/aus) in UI-Pixeln: {breite, höhe}. */
	int[] toggleSize();

	/** Schalter an/aus; (x, y) ist die linke obere Ecke, Größe {@link #toggleSize()}. */
	void toggle(GuiGraphics g, int x, int y, boolean on, UiState state);

	/** Kontrollkästchen (Auswahl, Filter); quadratisch mit Seitenlänge {@code size}. */
	void checkbox(GuiGraphics g, int x, int y, int size, boolean checked, UiState state);

	/**
	 * Reiter einer Reiterleiste. {@code count} (z. B. „4/34“) darf {@code null} sein.
	 * {@code vertical}: Eintrag einer senkrechten Liste auf {@link Surface#SIDE} (Seitenleiste) statt
	 * eines Reiters oben auf {@link Surface#WINDOW}.
	 */
	void tab(GuiGraphics g, Font font, int x, int y, int width, int height, String label, String count, boolean active,
		boolean vertical, UiState state);

	/**
	 * Hintergrund einer Listenzeile oder Karte (Mods, Einstellungen, Freunde, Configs …) auf einer hellen
	 * Fläche. Bis 24 Pixel Höhe eine Zeile (ohne Hover und Auswahl unsichtbar), ab 25 Pixel eine Karte mit
	 * eigener Fläche (Cosmetics, Minispiele, Configs). {@code selected}: angelegt, gewählt, offen.
	 */
	void row(GuiGraphics g, int x, int y, int width, int height, boolean selected, UiState state);

	/** Eingabefeld (Suche, Namen, Zahlen); den Text zeichnet der Bildschirm in {@link Ink#text()} von {@link Surface#INPUT}. */
	void input(GuiGraphics g, int x, int y, int width, int height, boolean focused);

	/** Schieberegler: Spur über die ganze Breite, Griff bei {@code value} (0..1). */
	void slider(GuiGraphics g, int x, int y, int width, int height, float value, UiState state);

	/** Senkrechte Bildlaufleiste: Spur und Griff; {@code thumbY} ist der Abstand des Griffs von der Oberkante der Spur. */
	void scrollbar(GuiGraphics g, int x, int y, int width, int height, int thumbY, int thumbHeight, UiState state);

	/** Kleine Zahl/Marke (ungelesene Nachrichten, „neu“); (x, y) ist die rechte obere Ecke. */
	void badge(GuiGraphics g, Font font, int x, int y, String text);

	/**
	 * Schild mit kurzem Text, das auf jeder Fläche und direkt auf dem Hintergrund lesbar ist: Tasten-
	 * Hinweise unten am Bildschirm, „Esc“, Name und Größe im HUD-Editor, „Eigenes“ an einer Karte.
	 * Größe {@link #tagWidth} × {@link #tagHeight}; (x, y) ist die linke obere Ecke.
	 */
	void tag(GuiGraphics g, Font font, int x, int y, String text);

	int tagWidth(Font font, String text);

	int tagHeight();

	/** Trennlinie zwischen Abschnitten. */
	void divider(GuiGraphics g, int x, int y, int width, Surface on);

	// ------------------------------------------------------------------ Schrift

	/**
	 * Überschrift (Fenstertitel, Abschnitt). Der Look wählt Schrift und Stil (Salumeria z. B.
	 * Serifenschrift), {@code scale} vergrößert (1 = normale Textgröße).
	 */
	void heading(GuiGraphics g, Font font, String text, int x, int y, int color, float scale);

	/** Breite einer Überschrift wie {@link #heading} sie zeichnet. */
	int headingWidth(Font font, String text, float scale);

	// ------------------------------------------------------------------ Bildschirme mit eigenem Aufbau

	/** Startmenü (Menü-Taste, Knöpfe im Titelbildschirm und Pausemenü). */
	MenuView menuView();

	/** Mod-Liste. */
	ModsView modsView();

	// ------------------------------------------------------------------ Typen

	/** Arten von Flächen. */
	enum Surface {
		/** Hauptfenster eines Bildschirms. */
		WINDOW,
		/** Seitenleiste oder zweite Spalte neben dem Hauptfenster. */
		SIDE,
		/** Karte oder Kasten innerhalb eines Fensters (Mod-Karte, Einstellungsgruppe, Freund). */
		CARD,
		/** Aufklappmenü, Dialog, Farbwähler – liegt über allem. */
		POPUP,
		/** Inneres eines Eingabefelds. */
		INPUT
	}

	/**
	 * Farben auf einer Fläche.
	 *
	 * @param text     normaler Text
	 * @param dim      Nebentext, Hinweise, Beschreibungen
	 * @param accent   Hervorhebung (gewählt, an, Links)
	 * @param onAccent Text auf einer Fläche in der Akzentfarbe
	 * @param line     Linien, Rahmen, Trenner
	 * @param warning  Warnung, Löschen, Fehler
	 */
	record Ink(int text, int dim, int accent, int onAccent, int line, int warning) {}

	/** Arten von Knöpfen. */
	enum ButtonKind {
		/** Hauptaktion (Speichern, Öffnen, Installieren). */
		PRIMARY,
		/** Normale Aktion. */
		SECONDARY,
		/** Löschen, Entfernen, Beenden. */
		DANGER,
		/** Flach, ohne Fläche (Zurück, Schließen, Werkzeuge). */
		GHOST
	}

	/** Zustand eines Bedienelements. */
	enum UiState {
		NORMAL,
		HOVER,
		PRESSED,
		DISABLED;

		public static UiState of(boolean hovered) {
			return hovered ? HOVER : NORMAL;
		}

		public static UiState of(boolean hovered, boolean enabled) {
			return !enabled ? DISABLED : hovered ? HOVER : NORMAL;
		}
	}
}
