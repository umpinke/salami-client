package dev.salamiclient.util;

/** Von {@code OverlayTextureMixin} implementiert: Treffer-Farbe der Entities setzen. */
public interface HurtOverlay {
	/** Vanilla-Farbe (ARGB): halbdurchsichtiges Rot. */
	int VANILLA_COLOR = 0xB2FF0000;

	void salami$setHurtColor(int argb);
}
