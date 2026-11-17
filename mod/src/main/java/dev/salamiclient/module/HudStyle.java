package dev.salamiclient.module;

/**
 * Aufgelöste Darstellung eines HUD-Elements für einen Frame: Hintergrund (ARGB, 0 = keiner),
 * Eckenradius, feiner Rand (ARGB, 0 = keiner), Textschatten, Bezeichnungen in Akzentfarbe und
 * Innenabstand um den Inhalt.
 *
 * <p>Kommt aus dem HUD-Stil des Moduls „Oberfläche“ oder – mit „Eigener Stil“ – aus den
 * Einstellungen des Elements selbst (siehe {@link HudModule#style()}).
 */
public record HudStyle(int background, int radius, int border, boolean shadow, boolean accentLabels, int padding) {
	/** Hintergrund-Grundfarbe ohne Deckkraft: fast schwarz mit einem Hauch Blau, wirkt ruhiger als reines Schwarz. */
	public static final int BASE = 0x0C0D12;

	public boolean hasBackground() {
		return (background >>> 24) != 0;
	}
}
