package dev.salamiclient.gui.skin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Aufbau des Startmenüs (Menü-Taste) für einen Look. Der Bildschirm ({@code SalamiMenuScreen})
 * hält Logik und Eingabe; der View rechnet die Plätze aus und zeichnet. Zeichnen und Klicken
 * müssen dieselbe Rechnung nutzen.
 *
 * <p>Pflicht sind nur {@link #render} und {@link #hit}. Schnellschalter ({@link #hitQuick}), ein eigener
 * Schließen-Knopf ({@link #hitClose}) und Tooltips ({@link #tooltip}) sind freiwillig – ohne sie bleibt
 * alles wie bisher (Theke).
 */
public interface MenuView {
	/** Zeichnet das ganze Menü (ohne {@link Skin#backdrop}, den zeichnet der Bildschirm vorher). */
	void render(GuiGraphics g, Font font, Model model, int uiWidth, int uiHeight, int mouseX, int mouseY);

	/** Welcher Eintrag liegt unter (x, y)? {@code null} = keiner. */
	Entry hit(Model model, int uiWidth, int uiHeight, double x, double y);

	/**
	 * Welcher {@linkplain Model#quick() Schnellschalter} liegt unter (x, y)? Index in {@link Model#quick()} oder -1.
	 * Linksklick schaltet das Modul um, Rechtsklick öffnet seine Einstellungen. Standard: der Look zeigt keine.
	 */
	default int hitQuick(Model model, int uiWidth, int uiHeight, double x, double y) {
		return -1;
	}

	/** Liegt unter (x, y) ein eigener Schließen-Knopf des Looks (Werkbank: „Fertig“)? Standard: nein. */
	default boolean hitClose(Model model, int uiWidth, int uiHeight, double x, double y) {
		return false;
	}

	/**
	 * Tooltip für die Stelle (x, y) oder {@code null}. Der Bildschirm zeigt ihn mit Minecrafts eigenem
	 * Tooltip an (wie {@link ModsView#tooltip}). Standard: keiner.
	 */
	default List<Component> tooltip(Font font, Model model, int uiWidth, int uiHeight, int mouseX, int mouseY) {
		return null;
	}

	/**
	 * Alles, was das Menü zeigt.
	 *
	 * @param title   „Salami“
	 * @param status  z. B. „10 von 69 Mods an“
	 * @param entries Einträge in dieser Reihenfolge: zuerst {@link Kind#MODS}, danach die übrigen
	 * @param hint    z. B. „Esc: schließen · Enter: Mods“
	 * @param quick   häufig genutzte Module zum direkten An-/Ausschalten (Werkbank: Hotbar); ein Look darf sie weglassen
	 */
	record Model(String title, String status, List<Entry> entries, String hint, List<Quick> quick) {
		/** Ohne Schnellschalter. */
		public Model(String title, String status, List<Entry> entries, String hint) {
			this(title, status, entries, hint, List.of());
		}

		public Entry entry(Kind kind) {
			for (Entry entry : entries) {
				if (entry.kind() == kind) {
					return entry;
				}
			}
			return null;
		}
	}

	/**
	 * Ein Eintrag.
	 *
	 * @param kind   was er öffnet
	 * @param label  Beschriftung („Mods“, „HUD bearbeiten“ …)
	 * @param detail kurze Zusatzzeile oder {@code null} (z. B. „10 / 69“, „3 online“)
	 * @param badge  Zahl als Marke (ungelesene Nachrichten, Anfragen); 0 = keine
	 */
	record Entry(Kind kind, String label, String detail, int badge) {}

	/**
	 * Ein Schnellschalter: ein Modul, das man im Startmenü direkt an- und ausschaltet.
	 *
	 * @param id          Modul-ID (z. B. „zoom“) – für das Symbol des Looks
	 * @param label       Name des Moduls
	 * @param description Beschreibung oder {@code null}
	 * @param on          eingeschaltet
	 * @param key         kurzer Tastenname (z. B. „C“) oder {@code null}
	 * @param hasSettings hat Einstellungen (Rechtsklick öffnet sie)
	 */
	record Quick(String id, String label, Component description, boolean on, String key, boolean hasSettings) {}

	/** Was ein Eintrag öffnet; jeder Look zeigt dafür sein eigenes Symbol. */
	enum Kind {
		MODS,
		HUD,
		CONFIGS,
		PANELS,
		FRIENDS,
		COSMETICS,
		MINIGAMES
	}
}
