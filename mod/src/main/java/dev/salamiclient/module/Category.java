package dev.salamiclient.module;

/**
 * Modul-Kategorien. Jede Kategorie bekommt in der ClickGUI ein eigenes Panel.
 */
public enum Category {
	CLIENT("client", false),
	VISUAL("visual", false),
	/** Verschiebbare Anzeigen (Overlay-System, HUD-Editor). */
	HUD("hud", false),
	MISC("misc", false),
	/**
	 * Kampf-Automatisierung (Auto Totem, Auto Crystal, Anchors …).
	 * Auf den meisten Servern verboten. Safe Mode und Easy Leave schalten
	 * alle Module dieser Kategorie hart ab.
	 */
	UTILITY("utility", true);

	private final String id;
	private final boolean restricted;

	Category(String id, boolean restricted) {
		this.id = id;
		this.restricted = restricted;
	}

	public String id() {
		return id;
	}

	/** {@code true}, wenn Safe Mode Module dieser Kategorie blockiert. */
	public boolean isRestricted() {
		return restricted;
	}

	public String translationKey() {
		return "salami.category." + id;
	}
}
