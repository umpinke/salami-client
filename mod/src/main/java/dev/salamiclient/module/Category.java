package dev.salamiclient.module;

/**
 * Modul-Kategorien. Jede Kategorie bekommt in der ClickGUI ein eigenes Panel.
 */
public enum Category {
	CLIENT("client"),
	VISUAL("visual"),
	/** Verschiebbare Anzeigen (Overlay-System, HUD-Editor). */
	HUD("hud"),
	MISC("misc"),
	/** Freunde und Chat über den Salami-Server. */
	SOCIAL("social");

	private final String id;

	Category(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	public String translationKey() {
		return "salami.category." + id;
	}
}
