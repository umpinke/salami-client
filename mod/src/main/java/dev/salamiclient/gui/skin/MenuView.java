package dev.salamiclient.gui.skin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * Aufbau des Startmenüs (Menü-Taste) für einen Look. Der Bildschirm ({@code SalamiMenuScreen})
 * hält Logik und Eingabe; der View rechnet die Plätze aus und zeichnet. Zeichnen und Klicken
 * müssen dieselbe Rechnung nutzen.
 */
public interface MenuView {
	/** Zeichnet das ganze Menü (ohne {@link Skin#backdrop}, den zeichnet der Bildschirm vorher). */
	void render(GuiGraphics g, Font font, Model model, int uiWidth, int uiHeight, int mouseX, int mouseY);

	/** Welcher Eintrag liegt unter (x, y)? {@code null} = keiner. */
	Entry hit(Model model, int uiWidth, int uiHeight, double x, double y);

	/**
	 * Alles, was das Menü zeigt.
	 *
	 * @param title   „Salami“
	 * @param status  z. B. „10 von 69 Mods an“
	 * @param entries Einträge in dieser Reihenfolge: zuerst {@link Kind#MODS}, danach die übrigen
	 * @param hint    z. B. „Esc: schließen · Enter: Mods“
	 */
	record Model(String title, String status, List<Entry> entries, String hint) {
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
