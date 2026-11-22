package dev.salamiclient.gui.skin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Aufbau der Mod-Liste für einen Look. Der Bildschirm ({@code ModsScreen}) hält Logik, Filter,
 * Suche, Scrollstand und Eingabe; der View rechnet die Plätze aus und zeichnet. Zeichnen,
 * Klicken und Scrollen müssen dieselbe Rechnung nutzen.
 *
 * <p>Beschreibungen nie mitten im Satz abschneiden: was nicht ganz in eine Karte passt, zeigt der
 * View vollständig an anderer Stelle (Hinweiskasten, Tooltip über {@link #tooltip}).
 */
public interface ModsView {
	void render(GuiGraphics g, Font font, Model model, int uiWidth, int uiHeight, int mouseX, int mouseY);

	/** Was liegt unter (x, y)? */
	Hit hit(Font font, Model model, int uiWidth, int uiHeight, double x, double y);

	/**
	 * Tooltip für die Stelle (x, y) oder {@code null}. Der Bildschirm zeigt ihn mit Minecrafts
	 * eigenem Tooltip an.
	 */
	List<Component> tooltip(Font font, Model model, int uiWidth, int uiHeight, int mouseX, int mouseY);

	/** Bereich, in dem die Einträge scrollen {x0, y0, x1, y1} – das Mausrad wirkt nur dort. */
	int[] scrollArea(Model model, int uiWidth, int uiHeight);

	/** Höhe aller Einträge zusammen (für den größten Scrollstand). */
	int contentHeight(Font font, Model model, int uiWidth, int uiHeight);

	/**
	 * Alles, was die Mod-Liste zeigt.
	 *
	 * @param title         Fenstertitel („Mods“); ein Look darf stattdessen einen eigenen Titel mit dem Namen der
	 *                      gewählten Kategorie zeigen (Theke: „Zutatenliste · Optik“)
	 * @param tabs          Kategorien; Index 0 ist „Alle“
	 * @param activeTab     gewählte Kategorie
	 * @param tools         Werkzeuge (HUD bearbeiten, Configs, Panel-Ansicht)
	 * @param items         sichtbare Einträge (schon gefiltert und sortiert)
	 * @param query         Suchtext
	 * @param searchHint    Platzhalter im Suchfeld
	 * @param emptyText     Text, wenn nichts passt
	 * @param scroll        Scrollstand in UI-Pixeln
	 * @param closeLabel    Beschriftung des Schließen-Knopfs (z. B. „Esc“)
	 */
	record Model(String title, List<Tab> tabs, int activeTab, List<Tool> tools, List<Item> items, String query,
		String searchHint, String emptyText, double scroll, String closeLabel) {}

	/** Kategorie mit Zähler „an / alle“; {@code enabled < 0}: nur die Anzahl zeigen (mitgelieferte Mods). */
	record Tab(String label, int enabled, int total) {
		/** „4/34“ bzw. „5“. */
		public String count() {
			return enabled < 0 ? String.valueOf(total) : enabled + "/" + total;
		}
	}

	/**
	 * Werkzeug unten in der Seitenleiste bzw. im Menü des Looks.
	 *
	 * @param kind was es öffnet ({@link MenuView.Kind#HUD}, {@link MenuView.Kind#CONFIGS}, {@link MenuView.Kind#PANELS}) –
	 *             für das Symbol (z. B. {@link Icons#draw})
	 */
	record Tool(String label, MenuView.Kind kind) {}

	/**
	 * Ein Eintrag: ein Salami-Modul oder eine mitgelieferte Mod.
	 *
	 * @param name        Name
	 * @param description Beschreibung (kann leer sein)
	 * @param on          eingeschaltet
	 * @param toggleable  lässt sich hier an-/ausschalten (mitgelieferte Mods nicht)
	 * @param hasSettings hat Einstellungen (Zahnrad)
	 * @param tag         kleine Marke, z. B. „mitgeliefert“, oder {@code null}
	 */
	record Item(String name, Component description, boolean on, boolean toggleable, boolean hasSettings, String tag) {}

	/** Ergebnis von {@link #hit}. */
	sealed interface Hit {
		record None() implements Hit {}

		record TabHit(int index) implements Hit {}

		record ToolHit(int index) implements Hit {}

		/** Klick auf den Eintrag selbst (an/aus umschalten). */
		record ItemHit(int index) implements Hit {}

		/** Klick auf das Zahnrad des Eintrags (Einstellungen öffnen). */
		record SettingsHit(int index) implements Hit {}

		record SearchHit() implements Hit {}

		record CloseHit() implements Hit {}

		Hit NONE = new None();
	}
}
