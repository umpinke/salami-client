package dev.salamiclient.theme;

/**
 * Farbschema für ClickGUI und HUD (alle Werte ARGB).
 *
 * @param panel       Hintergrund der Panels
 * @param header      Kopfzeile der Panels
 * @param headerText  Text in der Kopfzeile
 * @param text        Modulname (aus)
 * @param accent      Modulname (an), Slider, Akzente
 * @param enabledRow  Hintergrund aktiver Module
 * @param hover       Hover-Overlay
 * @param textDim     Einstellungs-Beschriftungen, Hinweise
 * @param outline     Panel-Rahmen
 * @param warning     Warnfarbe (Utility-Kategorie)
 */
public record Theme(
	String id,
	int panel,
	int header,
	int headerText,
	int text,
	int accent,
	int enabledRow,
	int hover,
	int textDim,
	int outline,
	int warning
) {
	/** Neutrales Dunkel-Theme mit Salami-Rot als Akzent. */
	public static final Theme DEFAULT = new Theme(
		"default",
		0xE0141418,
		0xFF1E1E24,
		0xFFFFFFFF,
		0xFFDADADF,
		0xFFE0454A,
		0x40E0454A,
		0x18FFFFFF,
		0xFF8A8A95,
		0xFF2C2C35,
		0xFFFFB020
	);

	/** Der Salami-Look: Pfeffer-Schwarz, Salami-Rot, Fett-Weiß, Pelle-Braun. */
	public static final Theme SALAMI = new Theme(
		"salami",
		0xEE2B1D1A,
		0xFFB23A3A,
		0xFFF3E3D3,
		0xFFD9C3B0,
		0xFFF3E3D3,
		0x80B23A3A,
		0x22F3E3D3,
		0xFFA88C7A,
		0xFF7A4A35,
		0xFFFFC857
	);
}
